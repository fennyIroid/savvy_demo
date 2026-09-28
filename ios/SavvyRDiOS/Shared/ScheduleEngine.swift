import Foundation
import DeviceActivity

/// Commitment timing through DeviceActivity, so the end of a commitment is
/// handled by the system (monitor extension) and not by a Timer in the app.
///
/// Apple limits: interval 15 minutes to 1 week, max 20 monitored activities per
/// app. Callbacks run only when the device is in use (late if the phone is idle).
/// Developers report intervalDidEnd is unreliable when year/month/day components
/// are included, so both component strategies are kept for device testing
/// (T-SCHED-1/2). Remove the losing strategy after the test.
enum ScheduleEngine {
    static let minimumInterval: TimeInterval = 15 * 60
    static let maximumInterval: TimeInterval = 7 * 24 * 3600

    enum ComponentStrategy: String { case fullDate, timeOfDay }
    static var strategy: ComponentStrategy {
        ComponentStrategy(rawValue: AppGroup.defaults.string(forKey: "schedule.strategy") ?? "") ?? .timeOfDay
    }

    /// Starts monitoring [now, now + duration]. Shields are applied by the app
    /// immediately; intervalDidEnd in the extension releases them.
    static func startCommitmentWindow(duration: TimeInterval, now: Date = Date()) throws {
        try startMonitoring(.commitment, from: now, to: now.addingTimeInterval(duration))
    }

    /// Temporary unlock (card "pause" or emergency pause). For pauses shorter than
    /// 15 minutes the window is back-dated so the interval is still 15 minutes long
    /// but ends at the requested time. Needs device validation (T-PAUSE-2).
    static func startPauseWindow(until end: Date, now: Date = Date()) throws {
        let windowStart = min(now, end.addingTimeInterval(-minimumInterval))
        try startMonitoring(.pause, from: windowStart, to: end)
    }

    static func stop(_ names: [DeviceActivityName]) {
        DeviceActivityCenter().stopMonitoring(names)
        SavvyLog.event("ScheduleEngine", "stop \(names.map(\.rawValue))")
    }

    static var monitored: [DeviceActivityName] { DeviceActivityCenter().activities }

    private static func startMonitoring(_ name: DeviceActivityName, from: Date, to: Date) throws {
        let calendar = Calendar.current
        // timeOfDay only works for windows under 24 h; longer windows need fullDate.
        let useFullDate = strategy == .fullDate || to.timeIntervalSince(from) >= 24 * 3600
        let parts: Set<Calendar.Component> = useFullDate
            ? [.era, .year, .month, .day, .hour, .minute, .second]
            : [.hour, .minute, .second]
        let schedule = DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(parts, from: from),
            intervalEnd: calendar.dateComponents(parts, from: to),
            repeats: false,
            warningTime: nil
        )
        let center = DeviceActivityCenter()
        center.stopMonitoring([name])
        try center.startMonitoring(name, during: schedule)
        SavvyLog.event("ScheduleEngine", "start \(name.rawValue) \(from) -> \(to) fullDate=\(useFullDate)")
    }
}
