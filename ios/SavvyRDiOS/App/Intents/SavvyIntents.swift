import AppIntents
import Foundation
import SavvyCore

/// Shortcuts integration (R&D 7 / 8, alternative E).
///
/// A Shortcuts "NFC" personal automation identifies a tag by UID only and passes
/// no tag data to the intent, and any discoverable intent can be run by hand.
/// So intents must NEVER unlock directly. They only:
///   - start a focus session (locking is safe to automate), or
///   - open Savvy straight into the card scan (the scan still proves the card).

@available(iOS 16.0, *)
struct StartSavvyFocusIntent: AppIntent {
    static var title: LocalizedStringResource = "Start Savvy Focus"
    static var description = IntentDescription("Starts a Savvy focus session with your saved app selection.")
    static var openAppWhenRun = false

    @Parameter(title: "Minutes", default: 60) var minutes: Int

    func perform() async throws -> some IntentResult {
        // Writing ManagedSettings from an intent is likely supported but must be
        // proven on device (T-INTENT-1).
        guard let selection = SharedState.selection, SharedState.commitment == nil else { return .result() }
        let duration = TimeInterval(max(minutes, 15) * 60)
        let state = CommitmentState(serverId: nil, mode: .custom, unlockPolicy: .cardRequired, taskRef: nil,
            startedAt: Date(), endsAt: Date().addingTimeInterval(duration), durationSeconds: duration,
            timeAnchor: .now(), serverEndsAtKnown: false)
        SharedState.commitment = state
        // Queued like any offline start: the app syncs it to the backend on next launch.
        SharedState.enqueue(.commitmentStarted(localId: state.localId, mode: "custom", minutes: max(minutes, 15),
                                               policy: "card_required", taskRef: nil, at: Date()))
        ShieldEngine.apply(selection)
        try ScheduleEngine.startCommitmentWindow(duration: duration)
        SavvyLog.event("Intent", "StartSavvyFocusIntent \(minutes)m (offline-created, app syncs on next launch)")
        return .result()
    }
}

@available(iOS 16.0, *)
struct ScanSavvyCardIntent: AppIntent {
    static var title: LocalizedStringResource = "Unlock with Savvy Card"
    static var description = IntentDescription("Opens Savvy and starts the card scan.")
    static var openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        SharedState.pendingUnlockRequest = Date()
        SavvyLog.event("Intent", "ScanSavvyCardIntent -> app opens into scan")
        return .result()
    }
}

@available(iOS 16.0, *)
struct SavvyShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(intent: StartSavvyFocusIntent(), phrases: ["Start focus in \(.applicationName)"],
                    shortTitle: "Start Focus", systemImageName: "lock")
        AppShortcut(intent: ScanSavvyCardIntent(), phrases: ["Unlock with \(.applicationName) card"],
                    shortTitle: "Scan Card", systemImageName: "wave.3.right")
    }
}
