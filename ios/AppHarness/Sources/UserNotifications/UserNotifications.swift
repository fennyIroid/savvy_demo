// Harness FAKE of UserNotifications: delivered requests are recorded.
import Foundation

public enum UNNotificationInterruptionLevel { case passive, active, timeSensitive, critical }
open class UNMutableNotificationContent {
    public init() {}
    open var title = ""
    open var body = ""
    open var userInfo: [AnyHashable: Any] = [:]
    open var interruptionLevel: UNNotificationInterruptionLevel = .active
}
open class UNNotificationTrigger {}
open class UNNotificationRequest {
    public let identifier: String
    public let content: UNMutableNotificationContent
    public init(identifier: String, content: UNMutableNotificationContent, trigger: UNNotificationTrigger?) {
        self.identifier = identifier; self.content = content
    }
}
open class UNUserNotificationCenter {
    public static var delivered: [UNNotificationRequest] = []
    public static func current() -> UNUserNotificationCenter { UNUserNotificationCenter() }
    open func add(_ request: UNNotificationRequest, withCompletionHandler: ((Error?) -> Void)? = nil) {
        UNUserNotificationCenter.delivered.append(request)
        withCompletionHandler?(nil)
    }
}
