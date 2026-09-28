import Foundation
import Combine
import CoreNFC
import FamilyControls
import SavvyCore

/// Orchestrates the POC. Kept in one place so each R&D test maps to one method.
@MainActor
final class AppModel: ObservableObject {
    enum Role: String, CaseIterable { case selfUse = "self", parent, child }

    @Published var selection = SharedState.selection ?? FamilyActivitySelection() {
        didSet { SharedState.selection = selection }
    }
    /// Parent device: apps chosen for the child in the parent-device picker.
    @Published var childSelection = FamilyActivitySelection()
    @Published private(set) var commitment = SharedState.commitment
    @Published var lastOutcome = ""
    @Published var showScanner = false
    @Published var showEmergency = false
    @Published var useRemovalGuard = true
    @Published var insights: BackendClient.Insights?
    @Published var children: [BackendClient.Child] = []
    @Published var childStatus: BackendClient.ChildStatus?
    @Published var linkCode: String?
    @Published var role: Role = Role(rawValue: AppGroup.defaults.string(forKey: "role") ?? "") ?? .selfUse {
        didSet { AppGroup.defaults.set(role.rawValue, forKey: "role") }
    }

    let auth = AuthorizationController()
    let tasks = TaskStore()
    let backend = BackendClient()
    private let nfc = NFCReader()
    private(set) lazy var unlock = UnlockCoordinator(backend: backend) { [weak self] in self?.deviceId }

    // POC storage. Production: device token in the Keychain.
    private var storedDeviceId: Int {
        get { AppGroup.defaults.integer(forKey: "deviceId") }
        set { AppGroup.defaults.set(newValue, forKey: "deviceId") }
    }
    private var storedToken: String {
        get { AppGroup.defaults.string(forKey: "deviceToken") ?? "" }
        set { AppGroup.defaults.set(newValue, forKey: "deviceToken") }
    }
    private var appliedRuleVersion: Int {
        get { AppGroup.defaults.integer(forKey: "appliedRuleVersion") }
        set { AppGroup.defaults.set(newValue, forKey: "appliedRuleVersion") }
    }

    var deviceId: Int? { storedDeviceId == 0 ? nil : storedDeviceId }

    init() {
        backend.deviceToken = storedToken.isEmpty ? nil : storedToken
    }

    // MARK: Account

    func register(email: String) async {
        do {
            let r = try await backend.register(email: email, role: role.rawValue)
            storedDeviceId = r.device_id
            storedToken = r.device_token
            backend.deviceToken = r.device_token
            // POC: fetch verification keys. Production: embed them in Info.plist at build time.
            let keys = try await backend.keys()
            AppGroup.defaults.set(Self.rawKeyHex(fromSPKIBase64: keys.grant_public_key), forKey: "SavvyGrantPublicKey")
            AppGroup.defaults.set(Self.rawKeyHex(fromSPKIBase64: keys.card_public_key), forKey: "SavvyCardPublicKey")
            lastOutcome = "Registered device \(r.device_id) as \(role.rawValue)"
            await restoreOnLaunch()
        } catch { lastOutcome = "Register failed \(error)" }
    }

    static func rawKeyHex(fromSPKIBase64 b64: String) -> String? {
        guard let der = Data(base64Encoded: b64), der.count >= 32 else { return nil }
        return der.suffix(32).map { String(format: "%02x", $0) }.joined()
    }

    // MARK: Launch, reboot and reinstall restore (R&D 11, 53)

    /// Called on every foreground and background refresh. Order matters:
    /// flush offline events first so the server knows about offline starts/releases.
    func restoreOnLaunch() async {
        TokenRefresher.refreshSavedSelections()
        refresh()
        consumePendingRequests()
        guard backend.deviceToken != nil else { return }
        await flushOfflineQueue()
        do {
            let r = try await backend.active()
            AppGroup.defaults.set(serverNow(r.server_time).timeIntervalSince(Date()), forKey: "serverOffset")
            guard let c = r.commitment else {
                if commitment?.serverId != nil { unlock.releaseLocally(reason: "server_says_not_active") }
                refresh()
                return
            }
            if commitment == nil || r.restored_from_previous_install {
                guard auth.status == .approved else {
                    lastOutcome = "Active commitment on server but Screen Time access is off. Re-authorize to restore."
                    SavvyLog.event("Restore", "commitment \(c.id) active on server, authorization=\(auth.status.label)")
                    return
                }
                guard !selection.applicationTokens.isEmpty || !selection.categoryTokens.isEmpty else {
                    lastOutcome = "Active commitment restored. Choose apps again (selection is lost on reinstall)."
                    return
                }
                let ends = ISO8601DateFormatter.savvy.date(from: c.ends_at) ?? Date()
                let remaining = ends.timeIntervalSince(serverNow(r.server_time))
                activateLocally(serverId: c.id, mode: .init(rawValue: c.mode) ?? .custom,
                                policy: .init(rawValue: c.unlock_policy) ?? .cardRequired,
                                taskRef: c.task_ref, duration: remaining, endsAt: ends, serverKnown: true)
                lastOutcome = "Restored commitment \(c.id) (\(Int(remaining / 60)) min left)"
                SavvyLog.event("Restore", "restored commitment \(c.id) reinstall=\(r.restored_from_previous_install)")
            } else if var local = commitment, local.serverId == c.id {
                // Server end time is authoritative (clock changes, reboot).
                local.endsAt = ISO8601DateFormatter.savvy.date(from: c.ends_at) ?? local.endsAt
                local.serverEndsAtKnown = true
                SharedState.commitment = local
            }
            if role == .child { await syncParentRules() }
            _ = try? await backend.heartbeat(authorization: auth.status.label,
                                             appliedRuleVersion: appliedRuleVersion, shieldActive: ShieldEngine.isShielding())
        } catch { SavvyLog.event("Restore", "offline, local state kept: \(error)") }
        refresh()
    }

    private func serverNow(_ iso: String) -> Date { ISO8601DateFormatter.savvy.date(from: iso) ?? Date() }

    /// POST /v1/sync with everything done offline. Maps offline-created commitments
    /// to their new server ids. Events stay queued if the network fails again.
    func flushOfflineQueue() async {
        let queue = SharedState.offlineQueue
        guard !queue.isEmpty else { return }
        do {
            let r = try await backend.sync(queue)
            for (item, result) in zip(queue, r.results) {
                if case let .commitmentStarted(localId, _, _, _, _, _) = item.event, let sid = result.server_id,
                   var c = SharedState.commitment, c.localId == localId {
                    c.serverId = sid
                    SharedState.commitment = c
                }
                if let v = result.violation { SavvyLog.event("Sync", "server recorded violation \(v)") }
            }
            SharedState.offlineQueue.removeFirst(min(queue.count, SharedState.offlineQueue.count))
            SavvyLog.event("Sync", "flushed \(queue.count) offline events")
        } catch { SavvyLog.event("Sync", "flush failed, kept \(queue.count): \(error)") }
    }

    /// Shield button handoff: the extension recorded a request just before opening
    /// Savvy (iOS 26.5+) or posting the notification (older iOS).
    func consumePendingRequests() {
        if let t = SharedState.pendingUnlockRequest, Date().timeIntervalSince(t) < 120 {
            SavvyLog.event("Unlock", "pending unlock request from shield, starting scan")
            scanCard(tagSession: true)
        }
        if let t = SharedState.pendingEmergencyRequest, Date().timeIntervalSince(t) < 120 {
            SharedState.pendingEmergencyRequest = nil
            showEmergency = true
        }
    }

    func refresh() { commitment = SharedState.commitment }

    // MARK: Focus / commitment / task start (R&D 3, 5, 16)

    func start(mode: CommitmentState.Mode, minutes: Int, policy: CommitmentState.UnlockPolicy,
               taskRef: String? = nil, selectionOverride: FamilyActivitySelection? = nil) async {
        guard auth.status == .approved else { lastOutcome = "Authorize Screen Time first"; return }
        let chosen = selectionOverride ?? selection
        guard !chosen.applicationTokens.isEmpty || !chosen.categoryTokens.isEmpty else {
            lastOutcome = "Select apps first"; return
        }
        if selectionOverride != nil { selection = chosen }
        let minutes = max(minutes, 15) // DeviceActivity minimum interval
        let localId = UUID().uuidString
        do {
            let c = try await backend.startCommitment(mode: mode.rawValue, minutes: minutes, policy: policy.rawValue, taskRef: taskRef)
            let ends = ISO8601DateFormatter.savvy.date(from: c.ends_at) ?? Date().addingTimeInterval(TimeInterval(minutes * 60))
            activateLocally(serverId: c.id, localId: localId, mode: mode, policy: policy, taskRef: taskRef,
                            duration: TimeInterval(minutes * 60), endsAt: ends, serverKnown: true)
        } catch {
            SavvyLog.event("Focus", "backend unavailable, starting offline: \(error)")
            SharedState.enqueue(.commitmentStarted(localId: localId, mode: mode.rawValue, minutes: minutes,
                                                   policy: policy.rawValue, taskRef: taskRef, at: Date()))
            activateLocally(serverId: nil, localId: localId, mode: mode, policy: policy, taskRef: taskRef,
                            duration: TimeInterval(minutes * 60), endsAt: Date().addingTimeInterval(TimeInterval(minutes * 60)),
                            serverKnown: false)
        }
    }

    private func activateLocally(serverId: Int?, localId: String = UUID().uuidString, mode: CommitmentState.Mode,
                                 policy: CommitmentState.UnlockPolicy, taskRef: String?, duration: TimeInterval,
                                 endsAt: Date, serverKnown: Bool) {
        SharedState.commitment = CommitmentState(serverId: serverId, localId: localId, mode: mode, unlockPolicy: policy,
            taskRef: taskRef, startedAt: Date(), endsAt: endsAt, durationSeconds: duration, timeAnchor: .now(),
            serverEndsAtKnown: serverKnown)
        ShieldEngine.apply(selection)
        if useRemovalGuard && policy != .free { ShieldEngine.setRemovalGuard(true) }
        do {
            try ScheduleEngine.startCommitmentWindow(duration: max(duration, ScheduleEngine.minimumInterval))
            lastOutcome = "Started \(mode.rawValue) until \(endsAt.formatted(date: .omitted, time: .shortened))"
        } catch {
            lastOutcome = "Shield applied but schedule failed: \(error). App must release manually."
            SavvyLog.event("Focus", "schedule error \(error)")
        }
        refresh()
    }

    // MARK: Unlock paths

    func scanCard(tagSession: Bool, liveProof: Bool = false) {
        let prompt = "Hold your Savvy card near the top of your iPhone."
        let handler: (Result<NFCReader.Result, NFCReader.Failure>) -> Void = { result in
            Task { @MainActor [weak self] in
                guard let self else { return }
                switch result {
                case .success(let r):
                    let note = "uid=\(r.uidHex ?? "n/a") read=\(String(format: "%.2f", r.elapsed))s"
                    if let token = r.presenceToken, self.commitment != nil {
                        let outcome = await self.unlock.handleLiveProof(url: r.url ?? "", presenceToken: token)
                        self.lastOutcome = "\(outcome) live \(note)"
                        self.refresh()
                    } else {
                        await self.handleCard(raw: r.url ?? "", source: .nfcForeground, note: note)
                    }
                case .failure(let f):
                    self.lastOutcome = "NFC: \(f)"
                }
            }
        }
        if tagSession { nfc.scanWithTagSession(prompt: prompt, liveProof: liveProof ? backend : nil, completion: handler) }
        else { nfc.scanWithNDEFSession(prompt: prompt, completion: handler) }
    }

    func handleCard(raw: String, source: UnlockCoordinator.Source, note: String = "") async {
        if commitment == nil {
            // No active commitment: treat the scan as card registration.
            do {
                _ = try await backend.registerCard(payload: raw, source: source.rawValue)
                SharedState.boundCardCode = CardPayload.parse(raw, domain: SavvyLinks.cardDomain)?.cardCode
                lastOutcome = "Card registered \(note)"
            } catch { lastOutcome = "Card registration failed \(error) \(note)" }
            return
        }
        let outcome = await unlock.handleCard(raw: raw, source: source)
        lastOutcome = "\(outcome) \(note)"
        refresh()
    }

    /// Universal link: background NFC read (after the user taps the iOS
    /// notification) or any other opening of the card URL.
    func handle(userActivity: NSUserActivity) {
        guard let url = userActivity.webpageURL else { return }
        let fromTag = userActivity.ndefMessagePayload.records.first.map { $0.typeNameFormat != .empty } ?? false
        SavvyLog.event("Link", "universal link fromTag=\(fromTag) \(url.absoluteString)")
        Task { await handleCard(raw: url.absoluteString, source: fromTag ? .nfcBackground : .link) }
    }

    func handle(url: URL) {
        SavvyLog.event("Link", "custom URL \(url)")
        if url.host == "unlock" { scanCard(tagSession: true) }
    }

    // MARK: Task completion (R&D 16)

    func completeTask(_ task: FocusTask, cardPayload: String? = nil) async {
        guard let c = commitment, c.taskRef == task.id else { tasks.set(task.id, .completed); return }
        if let serverId = c.serverId, let deviceId {
            do {
                let r = try await backend.release(id: serverId, method: "task_complete", payload: cardPayload, source: "nfc")
                _ = try unlock.applyGrant(r.grant, deviceId: deviceId, commitmentId: serverId)
                tasks.set(task.id, .completed)
                lastOutcome = "Task done, apps unlocked"
                refresh()
                return
            } catch let e as BackendClient.APIError {
                lastOutcome = "Task completion refused: \(e.code)"
                return
            } catch {
                SavvyLog.event("Task", "network error, offline rule: \(error)")
            }
        }
        switch OfflinePolicy.taskCompletion(state: c, taskId: task.id) {
        case .release:
            SharedState.enqueue(.taskCompleted(localId: c.localId, serverId: c.serverId, at: Date()))
            unlock.releaseLocally(reason: "task_complete_offline")
            tasks.set(task.id, .completed)
            lastOutcome = "Task done offline (will sync)"
        case .reject(let reason):
            lastOutcome = "Offline: \(reason)"
        }
        refresh()
    }

    // MARK: Emergency exit (R&D 17)

    func emergencyExit(reason: String) async {
        guard let c = commitment else { return }
        if let serverId = c.serverId, let deviceId {
            do {
                let r = try await backend.emergencyExit(id: serverId, reason: reason)
                if r.status == "pending" { lastOutcome = "Emergency exit available at \(r.available_at ?? "?")"; return }
                lastOutcome = "\(try unlock.applyGrant(r.grant, deviceId: deviceId, commitmentId: serverId))"
                refresh()
                return
            } catch let e as BackendClient.APIError {
                lastOutcome = "Emergency exit refused: \(e.code)"
                return
            } catch {
                SavvyLog.event("Emergency", "network error, offline rule: \(error)")
            }
        }
        guard OfflinePolicy.emergencyAllowed(history: SharedState.localEmergencyExits, now: Date()) else {
            lastOutcome = "Offline emergency exit already used this week"
            return
        }
        SharedState.localEmergencyExits.append(Date())
        SharedState.enqueue(.emergencyExit(localId: c.localId, serverId: c.serverId, at: Date()))
        unlock.releaseLocally(reason: "emergency_offline")
        lastOutcome = "Emergency exit (offline, will sync)"
        refresh()
    }

    // MARK: Insights (R&D 19)

    func loadInsights() async {
        do { insights = try await backend.insights(timeZone: TimeZone.current.identifier) }
        catch { lastOutcome = "Insights failed \(error)" }
    }

    // MARK: Parent device (R&D 13, 14)

    func createLinkCode() async {
        do { linkCode = try await backend.createLinkCode().code }
        catch { lastOutcome = "Link code failed \(error)" }
    }

    func loadChildren() async {
        do { children = try await backend.children().children }
        catch { lastOutcome = "Children failed \(error)" }
    }

    /// Sends the apps chosen in the parent-device picker to the child. Apple:
    /// "A FamilyActivityPicker shown on a parent device only displays applications
    /// and websites from authorized child devices". Test T-PC-2 / T-PC-3.
    func sendRules(to childId: Int, focusMinutes: Int?, policy: CommitmentState.UnlockPolicy, alwaysOn: Bool) async {
        do {
            let blob = try JSONEncoder().encode(childSelection).base64EncodedString()
            let focus = focusMinutes.map { BackendClient.ChildRules.Focus(mode: "study", duration_minutes: $0, unlock_policy: policy.rawValue) }
            _ = try await backend.putRules(childDeviceId: childId,
                                           rules: .init(selection_blob: blob, always_on: alwaysOn, focus: focus))
            lastOutcome = "Rules sent to child \(childId) (\(childSelection.applicationTokens.count) apps)"
        } catch { lastOutcome = "Send rules failed \(error)" }
    }

    func loadChildStatus(_ childId: Int) async {
        do { childStatus = try await backend.childStatus(childDeviceId: childId) }
        catch { lastOutcome = "Child status failed \(error)" }
    }

    // MARK: Child device (R&D 13-15)

    func linkChild(code: String) async {
        do { _ = try await backend.redeemLink(code: code); role = .child; lastOutcome = "Linked to parent" }
        catch { lastOutcome = "Link failed \(error)" }
    }

    /// Child device: pull parent rules and apply them locally. Triggered on every
    /// foreground, by BGAppRefreshTask, and by a silent push (production).
    func syncParentRules() async {
        do {
            let r = try await backend.pullRules(since: appliedRuleVersion)
            guard r.changed, let rules = r.rules else { lastOutcome = "Rules up to date (v\(r.version))"; return }
            var parentSelection: FamilyActivitySelection?
            if let blob = rules.selection_blob, let data = Data(base64Encoded: blob) {
                parentSelection = try JSONDecoder().decode(FamilyActivitySelection.self, from: data)
            }
            if rules.always_on == true, let sel = parentSelection {
                SharedState.parentSelection = sel
                ShieldEngine.apply(sel, store: .parentRules)
            } else {
                SharedState.parentSelection = nil
                ShieldEngine.clear(store: .parentRules)
            }
            if let focus = rules.focus {
                if commitment == nil {
                    await start(mode: .init(rawValue: focus.mode) ?? .custom, minutes: focus.duration_minutes,
                                policy: .init(rawValue: focus.unlock_policy) ?? .cardRequired,
                                selectionOverride: parentSelection)
                }
            } else if commitment != nil {
                unlock.releaseLocally(reason: "parent_rules_cleared")
            }
            appliedRuleVersion = r.version
            _ = try await backend.ackRules(version: r.version)
            lastOutcome = "Applied parent rules v\(r.version)"
        } catch { lastOutcome = "Rule sync failed \(error)" }
        refresh()
    }

    // MARK: Test helpers

    func setScheduleStrategy(_ s: ScheduleEngine.ComponentStrategy) {
        AppGroup.defaults.set(s.rawValue, forKey: "schedule.strategy")
        lastOutcome = "Schedule strategy \(s.rawValue)"
    }
}
