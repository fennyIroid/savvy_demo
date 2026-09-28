// Harness FAKE of DeviceActivity. The "system" schedule table is DeviceActivityFake;
// tests call the monitor extension callbacks themselves to simulate interval ends.
import Foundation

public struct DeviceActivityName: Hashable, RawRepresentable {
    public var rawValue: String
    public init(rawValue: String) { self.rawValue = rawValue }
    public init(_ rawValue: String) { self.rawValue = rawValue }
}
public struct DeviceActivitySchedule {
    public let intervalStart: DateComponents
    public let intervalEnd: DateComponents
    public let repeats: Bool
    public init(intervalStart: DateComponents, intervalEnd: DateComponents, repeats: Bool, warningTime: DateComponents? = nil) {
        self.intervalStart = intervalStart; self.intervalEnd = intervalEnd; self.repeats = repeats
    }
}
public enum DeviceActivityFake {
    public static var monitored: [DeviceActivityName: DeviceActivitySchedule] = [:]
    public static func reset() { monitored = [:] }
}
public struct MonitoringError: Error { public let reason: String }
public final class DeviceActivityCenter {
    public init() {}
    public var activities: [DeviceActivityName] { Array(DeviceActivityFake.monitored.keys) }
    public func startMonitoring(_ activity: DeviceActivityName, during schedule: DeviceActivitySchedule) throws {
        // Apple limits: interval 15 minutes to 1 week (checked on full-date schedules).
        let cal = Calendar.current
        if let s = cal.date(from: schedule.intervalStart), let e = cal.date(from: schedule.intervalEnd),
           schedule.intervalStart.day != nil {
            let len = e.timeIntervalSince(s)
            if len < 15 * 60 - 1 { throw MonitoringError(reason: "intervalTooShort") }
            if len > 7 * 86_400 { throw MonitoringError(reason: "intervalTooLong") }
        }
        DeviceActivityFake.monitored[activity] = schedule
    }
    public func stopMonitoring(_ activities: [DeviceActivityName] = []) {
        if activities.isEmpty { DeviceActivityFake.monitored = [:] }
        for a in activities { DeviceActivityFake.monitored[a] = nil }
    }
}
open class DeviceActivityMonitor: NSObject {
    public override init() {}
    open func intervalDidStart(for activity: DeviceActivityName) {}
    open func intervalDidEnd(for activity: DeviceActivityName) {}
    open func intervalWillEndWarning(for activity: DeviceActivityName) {}
}
