import XCTest
import ManagedSettings
import FamilyControls
import DeviceActivity
import UserNotifications
import SavvyCore
@testable import SavvyApp

/// End-to-end runs of the iOS app logic (AppModel, UnlockCoordinator, Monitor and
/// ShieldAction extensions) against the real backend, with framework FAKES standing
/// in for iOS. See Package.swift for what this does and does not prove.
final class IOSAppFlowTests: XCTestCase {
    var backend: BackendProcess!
    let instagram = ApplicationToken("instagram")

    override func setUpWithError() throws {
        backend = try BackendProcess()
        for key in AppGroup.defaults.dictionaryRepresentation().keys { AppGroup.defaults.removeObject(forKey: key) }
        ManagedSettingsFake.reset()
        DeviceActivityFake.reset()
        UNUserNotificationCenter.delivered = []
        AuthorizationCenter.shared.authorizationStatus = .approved
    }

    override func tearDown() { backend = nil }

    @MainActor func makeModel(email: String, role: AppModel.Role = .selfUse) async -> AppModel {
        let m = AppModel()
        m.backend.baseURL = backend.url
        m.role = role
        await m.register(email: email)
        var s = FamilyActivitySelection()
        s.applicationTokens = [instagram]
        m.selection = s
        return m
    }

    @MainActor func registerCard(_ m: AppModel) async throws -> String {
        let card = try backend.newCard()
        await m.handleCard(raw: card, source: .qr) // no commitment -> registration
        XCTAssertTrue(m.lastOutcome.hasPrefix("Card registered"), m.lastOutcome)
        return card
    }

    @MainActor func testOnlineCardUnlockClearsShield() async throws {
        let m = await makeModel(email: "alice@test")
        let card = try await registerCard(m)
        await m.start(mode: .study, minutes: 360, policy: .cardRequired)
        XCTAssertEqual(ManagedSettingsFake.shieldedApps, [instagram])
        XCTAssertTrue(ManagedSettingsFake.appRemovalDenied, "commitment guard on")
        XCTAssertNotNil(DeviceActivityFake.monitored[.commitment])
        XCTAssertNotNil(SharedState.commitment?.serverId)

        await m.handleCard(raw: try backend.newCard(), source: .nfcForeground)
        XCTAssertTrue(m.lastOutcome.contains("card_not_owned_by_user"), m.lastOutcome)
        XCTAssertEqual(ManagedSettingsFake.shieldedApps, [instagram])

        await m.handleCard(raw: card, source: .nfcForeground)
        XCTAssertTrue(m.lastOutcome.hasPrefix("released(offline: false)"), m.lastOutcome)
        XCTAssertTrue(ManagedSettingsFake.shieldedApps.isEmpty)
        XCTAssertFalse(ManagedSettingsFake.appRemovalDenied)
        XCTAssertNil(DeviceActivityFake.monitored[.commitment])
        XCTAssertNil(SharedState.commitment)
    }

    @MainActor func testOfflineStartOfflineCardThenSync() async throws {
        let m = await makeModel(email: "bob@test")
        let card = try await registerCard(m)
        m.backend.baseURL = URL(string: "http://127.0.0.1:1")!          // network lost
        await m.start(mode: .work, minutes: 120, policy: .cardRequired)
        XCTAssertNil(SharedState.commitment?.serverId)
        XCTAssertEqual(ManagedSettingsFake.shieldedApps, [instagram])
        await m.handleCard(raw: card, source: .nfcForeground)
        XCTAssertTrue(m.lastOutcome.hasPrefix("released(offline: true)"), m.lastOutcome)
        XCTAssertTrue(ManagedSettingsFake.shieldedApps.isEmpty)
        XCTAssertEqual(SharedState.offlineQueue.count, 2)

        m.backend.baseURL = backend.url                                  // network back
        await m.flushOfflineQueue()
        XCTAssertEqual(SharedState.offlineQueue.count, 0)
        await m.loadInsights()
        XCTAssertEqual(m.insights?.sessions_by_outcome["released_by_card"], 1)
    }

    @MainActor func testLockedCommitmentRefusesCardButEmergencyExitWorks() async throws {
        let m = await makeModel(email: "carol@test")
        let card = try await registerCard(m)
        await m.start(mode: .sleep, minutes: 480, policy: .locked)
        await m.handleCard(raw: card, source: .nfcForeground)
        XCTAssertTrue(m.lastOutcome.contains("locked_commitment_no_early_unlock"), m.lastOutcome)
        XCTAssertEqual(ManagedSettingsFake.shieldedApps, [instagram])
        await m.emergencyExit(reason: "test")
        XCTAssertTrue(ManagedSettingsFake.shieldedApps.isEmpty, m.lastOutcome)
    }

    @MainActor func testOfflineEmergencyLimitAndTaskRules() async throws {
        let m = await makeModel(email: "dan@test")
        m.backend.baseURL = URL(string: "http://127.0.0.1:1")!
        await m.start(mode: .work, minutes: 60, policy: .locked)
        await m.emergencyExit(reason: "x")
        XCTAssertEqual(m.lastOutcome, "Emergency exit (offline, will sync)")
        await m.start(mode: .work, minutes: 60, policy: .locked)
        await m.emergencyExit(reason: "x")
        XCTAssertEqual(m.lastOutcome, "Offline emergency exit already used this week")
        XCTAssertEqual(ManagedSettingsFake.shieldedApps, [instagram])

        // Task: offline completion is a free unlock only for "free" tasks.
        m.tasks.add(title: "Read", minutes: 30)
        let task = m.tasks.tasks[0]
        m.backend.baseURL = backend.url
        await m.emergencyExit(reason: "clear") // end the locked one (server has no record: still offline-created)
        SharedState.commitment = nil
        ManagedSettingsFake.reset()
        await m.start(mode: .task, minutes: 30, policy: .free, taskRef: task.id)
        await m.completeTask(task)
        XCTAssertEqual(m.lastOutcome, "Task done, apps unlocked")
        XCTAssertTrue(ManagedSettingsFake.shieldedApps.isEmpty)
    }

    @MainActor func testMonitorReArmsOnClockTamperAndReleasesWhenReallyOver() async throws {
        var s = FamilyActivitySelection(); s.applicationTokens = [instagram]
        SharedState.selection = s
        let now = TimeAnchor.now()
        XCTAssertNotNil(now.bootSession, "boot session id available")
        // 1 minute of real time passed, but the user moved the clock 7 hours forward.
        let tampered = TimeAnchor(wallClock: now.wallClock.addingTimeInterval(-7 * 3600), uptime: now.uptime - 60, bootSession: now.bootSession)
        SharedState.commitment = CommitmentState(serverId: 1, mode: .study, unlockPolicy: .cardRequired, taskRef: nil,
            startedAt: tampered.wallClock, endsAt: tampered.wallClock.addingTimeInterval(6 * 3600), durationSeconds: 6 * 3600,
            timeAnchor: tampered, serverEndsAtKnown: true)
        ShieldEngine.apply(s)
        SavvyMonitorExtension().intervalDidEnd(for: .commitment)
        XCTAssertEqual(ManagedSettingsFake.shieldedApps, [instagram], "shield kept after clock tamper")
        XCTAssertNotNil(DeviceActivityFake.monitored[.commitment], "re-armed for the real remaining time")

        // Really over: 7 hours of monotonic time too.
        var real = try XCTUnwrap(SharedState.commitment, "commitment was cleared although clock was tampered")
        real.timeAnchor = TimeAnchor(wallClock: now.wallClock.addingTimeInterval(-7 * 3600), uptime: now.uptime - 7 * 3600, bootSession: now.bootSession)
        SharedState.commitment = real
        SavvyMonitorExtension().intervalDidEnd(for: .commitment)
        XCTAssertTrue(ManagedSettingsFake.shieldedApps.isEmpty)
        XCTAssertNil(SharedState.commitment)
    }

    func testShieldActionHandsOffToAppAndClosesLockedApps() throws {
        let anchor = TimeAnchor(wallClock: Date(), uptime: 0, bootSession: "b")
        func state(_ p: CommitmentState.UnlockPolicy) -> CommitmentState {
            CommitmentState(serverId: 1, mode: .study, unlockPolicy: p, taskRef: nil, startedAt: Date(),
                            endsAt: Date().addingTimeInterval(3600), durationSeconds: 3600, timeAnchor: anchor, serverEndsAtKnown: true)
        }
        SharedState.commitment = state(.cardRequired)
        var response: ShieldActionResponse?
        SavvyShieldAction().handle(action: .primaryButtonPressed, for: instagram) { response = $0 }
        XCTAssertEqual(response, .openParentalControlsApp) // iOS 26.5+ path
        XCTAssertNotNil(SharedState.pendingUnlockRequest)

        SharedState.commitment = state(.locked)
        SavvyShieldAction().handle(action: .primaryButtonPressed, for: instagram) { response = $0 }
        XCTAssertEqual(response, .close)

        SavvyShieldAction().handle(action: .secondaryButtonPressed, for: instagram) { response = $0 }
        XCTAssertNotNil(SharedState.pendingEmergencyRequest)
    }

    @MainActor func testParentSelectionBlobAppliedOnChild() async throws {
        let parent = try backend.call("POST", "/v1/devices/register", ["email": "parent@test", "platform": "ios"])
        let parentToken = parent["device_token"] as! String
        let code = try backend.call("POST", "/v1/family/link-codes", [:], token: parentToken)["code"] as! String

        let child = await makeModel(email: "child@test", role: .child)
        child.selection = FamilyActivitySelection() // child has chosen nothing itself
        await child.linkChild(code: code)
        XCTAssertEqual(child.lastOutcome, "Linked to parent")

        // Parent's picker selection (tokens valid within the family) sent as a rule.
        var parentPick = FamilyActivitySelection(); parentPick.applicationTokens = [instagram]
        let blob = try JSONEncoder().encode(parentPick).base64EncodedString()
        let childId = try XCTUnwrap(child.deviceId)
        try backend.call("PUT", "/v1/family/children/\(childId)/rules",
                         ["rules": ["selection_blob": blob, "always_on": true,
                                    "focus": ["mode": "study", "duration_minutes": 30, "unlock_policy": "card_required"]]],
                         token: parentToken)
        await child.syncParentRules()
        XCTAssertEqual(child.lastOutcome, "Applied parent rules v1")
        XCTAssertEqual(ManagedSettingsFake.stores["savvy.parentRules"]?.shield.applications, [instagram])
        XCTAssertEqual(SharedState.commitment?.mode, .study)
        XCTAssertEqual(ManagedSettingsFake.stores["savvy.commitment"]?.shield.applications, [instagram])
        let status = try backend.call("GET", "/v1/family/children/\(childId)/status", token: parentToken)
        XCTAssertEqual(status["latest_rule_version"] as? Int, 1)
    }

    @MainActor func testReinstallRestoresCommitmentWithSameEndTime() async throws {
        let first = await makeModel(email: "erin@test")
        await first.start(mode: .study, minutes: 360, policy: .cardRequired)
        let endsAt = try XCTUnwrap(SharedState.commitment).endsAt
        // Delete Savvy: app data gone, iOS removes its restrictions (Apple: unenrolled on deletion).
        for key in AppGroup.defaults.dictionaryRepresentation().keys { AppGroup.defaults.removeObject(forKey: key) }
        ManagedSettingsFake.reset()
        DeviceActivityFake.reset()

        let second = AppModel()
        second.backend.baseURL = backend.url
        await second.register(email: "erin@test")
        XCTAssertEqual(second.lastOutcome, "Active commitment restored. Choose apps again (selection is lost on reinstall).")
        var s = FamilyActivitySelection(); s.applicationTokens = [instagram]
        second.selection = s
        await second.restoreOnLaunch()
        XCTAssertEqual(ManagedSettingsFake.shieldedApps, [instagram])
        XCTAssertEqual(try XCTUnwrap(SharedState.commitment).endsAt.timeIntervalSince1970, endsAt.timeIntervalSince1970, accuracy: 1)
    }
}
