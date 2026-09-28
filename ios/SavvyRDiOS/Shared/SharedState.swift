import Foundation
import FamilyControls
import SavvyCore

// CommitmentState, TimeAnchor and OfflineEvent live in the SavvyCore package (unit-tested).

struct QueuedEvent: Codable, Equatable {
    let id: String
    let event: OfflineEvent
}

enum SharedState {
    private static let commitmentKey = "commitment.v1"
    private static let selectionKey = "selection.v1"
    private static let boundCardKey = "boundCardCode.v1"
    private static let pendingUnlockKey = "pendingUnlockRequest.v1"
    private static let emergencyKey = "emergencyLocal.v1"

    static var commitment: CommitmentState? {
        get { decode(commitmentKey) }
        set { encode(newValue, commitmentKey) }
    }

    /// FamilyActivitySelection is Codable. The tokens inside are opaque and only
    /// meaningful on this device (see IOS_FEASIBILITY.md, app selection).
    static var selection: FamilyActivitySelection? {
        get { decode(selectionKey) }
        set { encode(newValue, selectionKey) }
    }

    /// Card code bound to this account, cached for offline verification.
    static var boundCardCode: String? {
        get { AppGroup.defaults.string(forKey: boundCardKey) }
        set { AppGroup.defaults.set(newValue, forKey: boundCardKey) }
    }

    /// Set by the ShieldAction extension when the user taps "Unlock with Savvy card"
    /// on the shield. The app reads it on launch and goes straight to scanning.
    static var pendingUnlockRequest: Date? {
        get { AppGroup.defaults.object(forKey: pendingUnlockKey) as? Date }
        set { AppGroup.defaults.set(newValue, forKey: pendingUnlockKey) }
    }

    static var pendingEmergencyRequest: Date? {
        get { AppGroup.defaults.object(forKey: "pendingEmergencyRequest.v1") as? Date }
        set { AppGroup.defaults.set(newValue, forKey: "pendingEmergencyRequest.v1") }
    }

    /// Local emergency-exit log used only when offline; synced to backend later.
    static var localEmergencyExits: [Date] {
        get { decode(emergencyKey) ?? [] }
        set { encode(newValue, emergencyKey) }
    }

    /// Offline actions waiting for POST /v1/sync, each with a stable event id.
    static var offlineQueue: [QueuedEvent] {
        get { decode("offlineQueue.v2") ?? [] }
        set { encode(newValue, "offlineQueue.v2") }
    }

    static func enqueue(_ event: OfflineEvent) {
        offlineQueue.append(QueuedEvent(id: UUID().uuidString, event: event))
        SavvyLog.event("Offline", "queued \(event)")
    }

    /// Always-on shield set by a parent (separate store from focus commitments).
    static var parentSelection: FamilyActivitySelection? {
        get { decode("parentSelection.v1") }
        set { encode(newValue, "parentSelection.v1") }
    }

    private static func decode<T: Decodable>(_ key: String) -> T? {
        guard let data = AppGroup.defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private static func encode<T: Encodable>(_ value: T?, _ key: String) {
        if let value, let data = try? JSONEncoder().encode(value) {
            AppGroup.defaults.set(data, forKey: key)
        } else {
            AppGroup.defaults.removeObject(forKey: key)
        }
    }
}
