import DeviceActivity
import ManagedSettings
import Foundation
import SavvyCore

/// Runs in the system's process space when a monitored interval starts or ends,
/// even if Savvy is terminated. This is what keeps commitments working without
/// an app Timer. Memory budget is small (reported around 6 MB): no heavy work here.
final class SavvyMonitorExtension: DeviceActivityMonitor {

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        SavvyLog.event("Monitor", "intervalDidStart \(activity.rawValue)")
        switch activity {
        case .commitment:
            // Belt and braces: the app already applied the shield at start. Re-apply
            // from shared state in case the app was killed between the two steps.
            if let selection = SharedState.selection, SharedState.commitment != nil {
                ShieldEngine.apply(selection)
            }
        case .pause:
            // Temporary unlock window started: lift the commitment shield.
            ShieldEngine.clear(store: .commitment)
        default:
            break
        }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        SavvyLog.event("Monitor", "intervalDidEnd \(activity.rawValue)")
        switch activity {
        case .commitment:
            endCommitmentIfReallyOver()
        case .pause:
            // Pause over: shield again if the commitment is still running.
            if var state = SharedState.commitment, let selection = SharedState.selection {
                state.pausedUntil = nil
                SharedState.commitment = state
                ShieldEngine.apply(selection)
            }
        default:
            break
        }
    }

    override func intervalWillEndWarning(for activity: DeviceActivityName) {
        SavvyLog.event("Monitor", "intervalWillEndWarning \(activity.rawValue)")
    }

    /// If the user moved the clock forward, DeviceActivity may end the interval
    /// early (to be confirmed by T-TIME-1). The monotonic clock tells us the truth
    /// within the same boot, so we re-arm for the real remaining time.
    private func endCommitmentIfReallyOver() {
        guard let state = SharedState.commitment else {
            ShieldEngine.clear(store: .commitment)
            return
        }
        let verdict = TimeIntegrity.check(since: state.timeAnchor, now: .now())
        SavvyLog.event("Monitor", "time verdict \(verdict)")
        if case .clockChanged(let trueElapsed, _) = verdict, trueElapsed + 60 < state.durationSeconds {
            let remaining = state.durationSeconds - trueElapsed
            do {
                try ScheduleEngine.startCommitmentWindow(duration: max(remaining, ScheduleEngine.minimumInterval))
                SavvyLog.event("Monitor", "clock tamper detected, re-armed for \(Int(remaining))s, shield kept")
                return
            } catch {
                SavvyLog.event("Monitor", "re-arm failed \(error), keeping shield until app launch")
                return
            }
        }
        ShieldEngine.clear(store: .commitment)
        ShieldEngine.setRemovalGuard(false)
        SharedState.commitment = nil
        SavvyLog.event("Monitor", "commitment ended by schedule")
    }
}
