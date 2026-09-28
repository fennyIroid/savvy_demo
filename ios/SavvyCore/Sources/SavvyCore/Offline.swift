import Foundation

/// Actions taken without network, replayed to POST /v1/sync when it returns.
public enum OfflineEvent: Codable, Equatable {
    case commitmentStarted(localId: String, mode: String, minutes: Int, policy: String, taskRef: String?, at: Date)
    case cardRelease(localId: String, serverId: Int?, payload: String, source: String, at: Date)
    case emergencyExit(localId: String, serverId: Int?, at: Date)
    case taskCompleted(localId: String, serverId: Int?, at: Date)
}

/// Pure offline decisions, kept here so they are unit-tested.
public enum OfflinePolicy {
    /// At most `maxPerWindow` offline emergency exits per rolling window. The
    /// server applies the real (stricter) limit when the event syncs.
    /// Entries dated in the future (user moved the clock back) still count, so
    /// winding the clock back cannot free up another exit.
    public static func emergencyAllowed(history: [Date], now: Date, maxPerWindow: Int = 1,
                                        window: TimeInterval = 7 * 86_400) -> Bool {
        history.filter { now.timeIntervalSince($0) < window }.count < maxPerWindow
    }

    public enum CardDecision: Equatable {
        case release
        case reject(String)
    }

    /// What the app may do with a card scan when the backend cannot be reached.
    public static func cardUnlock(raw: String, domain: String, state: CommitmentState?, allowOffline: Bool,
                                  cardPublicKeyRaw: Data, boundCardCode: String?) -> CardDecision {
        guard let payload = CardPayload.parse(raw, domain: domain) else { return .reject("not_a_savvy_card") }
        guard let state else { return .reject("no_active_commitment") }
        if state.unlockPolicy == .locked { return .reject("locked_commitment_no_early_unlock") }
        guard allowOffline else { return .reject("offline_unlock_disabled") }
        if payload.provesPhysicalPresence { return .reject("sun_card_needs_internet") }
        guard CardPayload.verifyOffline(payload, cardPublicKeyRaw: cardPublicKeyRaw, boundCardCode: boundCardCode) else {
            return .reject("offline_card_check_failed")
        }
        return .release
    }

    /// Completing a task offline is only a free unlock when the policy is free.
    public static func taskCompletion(state: CommitmentState, taskId: String) -> CardDecision {
        guard state.mode == .task, state.taskRef == taskId else { return .reject("not_this_task") }
        return state.unlockPolicy == .free ? .release : .reject("card_or_server_required")
    }
}
