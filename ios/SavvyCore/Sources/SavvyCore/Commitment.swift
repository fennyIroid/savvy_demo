import Foundation

/// Monotonic time reference captured at commitment start. The app fills it from
/// Darwin CLOCK_MONOTONIC and kern.bootsessionuuid (see app Shared/DeviceClock.swift).
public struct TimeAnchor: Codable, Equatable {
    public var wallClock: Date
    public var uptime: TimeInterval          // CLOCK_MONOTONIC seconds: counts sleep, ignores clock changes
    public var bootSession: String?          // changes on every boot

    public init(wallClock: Date, uptime: TimeInterval, bootSession: String?) {
        self.wallClock = wallClock
        self.uptime = uptime
        self.bootSession = bootSession
    }
}

/// Everything an extension needs to enforce a commitment without the app running.
public struct CommitmentState: Codable, Equatable {
    public enum UnlockPolicy: String, Codable, CaseIterable { case cardRequired = "card_required", free, locked }
    public enum Mode: String, Codable, CaseIterable { case study, work, sleep, custom, task }

    public var serverId: Int?          // nil while offline-created and not yet synced
    public var localId: String         // stable id used to reconcile offline events
    public var mode: Mode
    public var unlockPolicy: UnlockPolicy
    public var taskRef: String?
    public var startedAt: Date
    public var endsAt: Date            // server ends_at when known, otherwise device estimate
    public var durationSeconds: TimeInterval
    public var timeAnchor: TimeAnchor
    public var serverEndsAtKnown: Bool
    public var pausedUntil: Date?

    public init(serverId: Int?, localId: String = UUID().uuidString, mode: Mode, unlockPolicy: UnlockPolicy,
                taskRef: String?, startedAt: Date, endsAt: Date, durationSeconds: TimeInterval,
                timeAnchor: TimeAnchor, serverEndsAtKnown: Bool, pausedUntil: Date? = nil) {
        self.serverId = serverId
        self.localId = localId
        self.mode = mode
        self.unlockPolicy = unlockPolicy
        self.taskRef = taskRef
        self.startedAt = startedAt
        self.endsAt = endsAt
        self.durationSeconds = durationSeconds
        self.timeAnchor = timeAnchor
        self.serverEndsAtKnown = serverEndsAtKnown
        self.pausedUntil = pausedUntil
    }
}

/// Detects manual clock changes without the network. Same rules as Android core TimeIntegrity.
public enum TimeIntegrity {
    public static let tolerance: TimeInterval = 120

    public enum Verdict: Equatable {
        case trusted(elapsed: TimeInterval)
        case clockChanged(trueElapsed: TimeInterval, wallElapsed: TimeInterval)
        case rebootedOrUnknown(wallElapsed: TimeInterval)
    }

    public static func check(since anchor: TimeAnchor, now: TimeAnchor) -> Verdict {
        let wallElapsed = now.wallClock.timeIntervalSince(anchor.wallClock)
        guard let a = anchor.bootSession, let b = now.bootSession, a == b, now.uptime >= anchor.uptime else {
            return .rebootedOrUnknown(wallElapsed: wallElapsed)
        }
        let trueElapsed = now.uptime - anchor.uptime
        if abs(wallElapsed - trueElapsed) > tolerance {
            return .clockChanged(trueElapsed: trueElapsed, wallElapsed: wallElapsed)
        }
        return .trusted(elapsed: trueElapsed)
    }

    /// Remaining time. Same boot: monotonic. After reboot: server end time judged
    /// against server time if known, else wall clock (documented offline gap).
    public static func remaining(for state: CommitmentState, now: TimeAnchor, serverNow: Date? = nil) -> TimeInterval {
        let value: TimeInterval
        switch check(since: state.timeAnchor, now: now) {
        case .trusted(let elapsed), .clockChanged(let elapsed, _):
            value = state.durationSeconds - elapsed
        case .rebootedOrUnknown:
            value = state.endsAt.timeIntervalSince(serverNow ?? now.wallClock)
        }
        return max(0, value)
    }
}
