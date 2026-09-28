import SwiftUI
import FamilyControls
import DeviceActivity
import SavvyCore

/// Test lab, not product UI. Each section maps to an R&D item and a test ID in
/// docs/IOS_POC_RESULTS.md.
struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @State private var showPicker = false
    @State private var showChildPicker = false
    @State private var mode: CommitmentState.Mode = .study
    @State private var minutes = 360
    @State private var policy: CommitmentState.UnlockPolicy = .cardRequired
    @State private var email = "tester@savvy.test"
    @State private var linkCode = ""
    @State private var newTask = ""
    @State private var taskMinutes = 30
    @State private var emergencyReason = ""
    @State private var showLog = false
    @State private var childFocusMinutes = 120
    @State private var childAlwaysOn = false

    var body: some View {
        NavigationView {
            Form {
                Section("Result") { Text(model.lastOutcome).font(.footnote).textSelection(.enabled) }

                Section("Role") {
                    Picker("This iPhone is", selection: $model.role) {
                        Text("Self-use").tag(AppModel.Role.selfUse)
                        Text("Parent").tag(AppModel.Role.parent)
                        Text("Child").tag(AppModel.Role.child)
                    }.pickerStyle(.segmented)
                }

                Section("R&D 1 Authorization") {
                    Text("Status: \(model.auth.status.label)")
                    if model.role == .child {
                        Button("Request .child (parent approves on this device)") { Task { await model.auth.request(.child) } }
                    } else {
                        Button("Request .individual") { Task { await model.auth.request(.individual) } }
                    }
                    Button("Revoke authorization", role: .destructive) { model.auth.revoke() }
                    if let e = model.auth.lastError { Text(e).font(.caption).foregroundColor(.red) }
                }

                Section("Account") {
                    TextField("email", text: $email).textInputAutocapitalization(.never)
                    Button("Register this install") { Task { await model.register(email: email) } }
                    Text("Device id: \(model.deviceId.map(String.init) ?? "-")")
                    Text("Offline events waiting: \(SharedState.offlineQueue.count)").font(.caption)
                    Button("Sync offline events now") { Task { await model.flushOfflineQueue() } }
                }

                if model.role != .parent { selfOrChildSections }
                if model.role == .parent { parentSections }

                Section("R&D 19 Insights") {
                    Button("Load focus time and streak") { Task { await model.loadInsights() } }
                    if let i = model.insights {
                        Text("Streak: \(i.streak_days) days")
                        Text("Focus today: \(i.focus_seconds_today / 60) min, total: \(i.focus_seconds_total / 60) min")
                        Text("Outcomes: \(i.sessions_by_outcome.map { "\($0.key)=\($0.value)" }.sorted().joined(separator: ", "))").font(.caption)
                    }
                }

                Section("Diagnostics") {
                    Text("Monitored: \(ScheduleEngine.monitored.map(\.rawValue).joined(separator: ", "))").font(.caption)
                    Text("Shielding: \(ShieldEngine.isShielding() ? "yes" : "no")").font(.caption)
                    HStack {
                        Button("Schedule: timeOfDay") { model.setScheduleStrategy(.timeOfDay) }
                        Spacer()
                        Button("Schedule: fullDate") { model.setScheduleStrategy(.fullDate) }
                    }.buttonStyle(.bordered).font(.caption)
                    Button("Show lifecycle log") { showLog = true }
                    Button("Clear log") { SavvyLog.clear() }
                }
            }
            .navigationTitle("Savvy R&D iOS")
        }
        .familyActivityPicker(isPresented: $showPicker, selection: $model.selection)
        .sheet(isPresented: $model.showScanner) {
            QRScannerView { code in
                model.showScanner = false
                Task { await model.handleCard(raw: code, source: .qr) }
            }
        }
        .sheet(isPresented: $showLog) { ScrollView { Text(SavvyLog.read()).font(.caption2.monospaced()).padding() } }
        .alert("Emergency exit", isPresented: $model.showEmergency) {
            TextField("Reason", text: $emergencyReason)
            Button("Use emergency exit", role: .destructive) { Task { await model.emergencyExit(reason: emergencyReason) } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Emergency exits are limited and recorded.")
        }
    }

    @ViewBuilder private var selfOrChildSections: some View {
        Section("R&D 2 App selection") {
            Button("Choose distracting apps") { showPicker = true }
            Text("apps \(model.selection.applicationTokens.count), categories \(model.selection.categoryTokens.count), web \(model.selection.webDomainTokens.count)")
            ForEach(Array(model.selection.applicationTokens), id: \.self) { token in
                Label(token) // name and icon rendered by the system; Savvy cannot read them
            }
        }

        Section("R&D 3/5/15 Focus and commitment") {
            Picker("Mode", selection: $mode) { ForEach(CommitmentState.Mode.allCases, id: \.self) { Text($0.rawValue) } }
            Stepper("Minutes: \(minutes)", value: $minutes, in: 15...1440, step: 15)
            HStack { Button("6 h") { minutes = 360 }; Spacer(); Button("24 h") { minutes = 1440 } }.buttonStyle(.bordered)
            Picker("Unlock policy", selection: $policy) {
                Text("Card required").tag(CommitmentState.UnlockPolicy.cardRequired)
                Text("Free").tag(CommitmentState.UnlockPolicy.free)
                Text("Locked").tag(CommitmentState.UnlockPolicy.locked)
            }
            Toggle("Commitment guard (denyAppRemoval + auto time)", isOn: $model.useRemovalGuard)
            Button("Start") { Task { await model.start(mode: mode, minutes: minutes, policy: policy) } }
            if let c = model.commitment { CommitmentStatusView(state: c) }
        }

        Section("R&D 6-10 Card") {
            Button("Scan card (tag session, shows UID)") { model.scanCard(tagSession: true) }
            Button("Scan card (NDEF session)") { model.scanCard(tagSession: false) }
            Button("Scan card with live proof (NTAG 424 DNA)") { model.scanCard(tagSession: true, liveProof: true) }
            Button("Scan card QR") { model.showScanner = true }
            Text("Bound card: \(SharedState.boundCardCode ?? "none")").font(.caption)
        }

        Section("R&D 16 To-do restriction") {
            TextField("Task title", text: $newTask)
            Stepper("Minutes: \(taskMinutes)", value: $taskMinutes, in: 15...1440, step: 15)
            Button("Add task") { model.tasks.add(title: newTask, minutes: taskMinutes); newTask = "" }
            ForEach(model.tasks.tasks) { task in
                HStack {
                    Text("\(task.title) [\(task.status.rawValue)]")
                    Spacer()
                    if task.status == .pending {
                        Button("Start") {
                            model.tasks.set(task.id, .active)
                            Task { await model.start(mode: .task, minutes: task.durationMinutes ?? 1440, policy: policy, taskRef: task.id) }
                        }
                    } else if task.status == .active {
                        Button("Done") { Task { await model.completeTask(task) } }
                    }
                }
            }
        }

        Section("R&D 17 Emergency exit") {
            Button("Emergency exit", role: .destructive) { model.showEmergency = true }
        }

        if model.role == .child {
            Section("R&D 13-15 Child device") {
                TextField("Link code from parent", text: $linkCode).keyboardType(.numberPad)
                Button("Link this device as child") { Task { await model.linkChild(code: linkCode) } }
                Button("Sync parent rules now") { Task { await model.syncParentRules() } }
                Text("Parent always-on apps: \(SharedState.parentSelection?.applicationTokens.count ?? 0)").font(.caption)
            }
        }

        Section("R&D 18 Screen-time summary (rendered by extension)") {
            DeviceActivityReport(.totalActivity, filter: DeviceActivityFilter(
                segment: .daily(during: Calendar.current.dateInterval(of: .day, for: Date())!),
                users: .all, devices: .init([.iPhone])))
                .frame(height: 260)
        }
    }

    @ViewBuilder private var parentSections: some View {
        Section("R&D 13-14 Parent device") {
            Button("Create link code for child") { Task { await model.createLinkCode() } }
            if let code = model.linkCode { Text("Code: \(code) (valid 15 min)").font(.title3.monospaced()) }
            Button("Load my children") { Task { await model.loadChildren() } }
            Button("Choose child's apps (parent-device picker)") { showChildPicker = true }
            Text("Chosen for child: apps \(model.childSelection.applicationTokens.count), categories \(model.childSelection.categoryTokens.count)")
            Stepper("Child focus minutes: \(childFocusMinutes)", value: $childFocusMinutes, in: 15...1440, step: 15)
            Toggle("Always-on block (not only during focus)", isOn: $childAlwaysOn)
            ForEach(model.children, id: \.child_device_id) { child in
                VStack(alignment: .leading) {
                    Text("Child device \(child.child_device_id) (\(child.platform))")
                    HStack {
                        Button("Send focus rule") {
                            Task { await model.sendRules(to: child.child_device_id, focusMinutes: childFocusMinutes, policy: policy, alwaysOn: childAlwaysOn) }
                        }
                        Button("Clear focus") {
                            Task { await model.sendRules(to: child.child_device_id, focusMinutes: nil, policy: policy, alwaysOn: childAlwaysOn) }
                        }
                        Button("Status") { Task { await model.loadChildStatus(child.child_device_id) } }
                    }.buttonStyle(.bordered).font(.caption)
                }
            }
            if let s = model.childStatus {
                Text("Child \(s.child_device_id): rules v\(s.applied_rule_version ?? 0)/\(s.latest_rule_version), auth \(s.authorization_status ?? "-"), flags \(s.flags.joined(separator: ", "))")
                    .font(.caption)
            }
        }
        // Separate from the self-use picker so the two sheets do not compete.
        .familyActivityPicker(isPresented: $showChildPicker, selection: $model.childSelection)
    }
}

extension DeviceActivityReport.Context {
    static let totalActivity = Self("Total Activity")
}

struct CommitmentStatusView: View {
    let state: CommitmentState
    var body: some View {
        let now = TimeAnchor.now()
        let offset = AppGroup.defaults.double(forKey: "serverOffset")
        let remaining = TimeIntegrity.remaining(for: state, now: now, serverNow: Date().addingTimeInterval(offset))
        VStack(alignment: .leading) {
            Text("Active: \(state.mode.rawValue), policy \(state.unlockPolicy.rawValue), server id \(state.serverId.map(String.init) ?? "offline")")
            Text("Ends: \(state.endsAt.formatted(date: .abbreviated, time: .shortened))")
            Text("Remaining: \(Int(remaining / 60)) min")
            Text("Clock check: \(String(describing: TimeIntegrity.check(since: state.timeAnchor, now: now)))").font(.caption2)
        }.font(.caption)
    }
}
