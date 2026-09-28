import ManagedSettings
import UserNotifications
import Foundation
import SavvyCore

/// Handles shield button taps. Core NFC is not available in extensions, so the
/// card must be read by the main app. Handoff to the main app:
///
///   iOS 26.5+   respond .openParentalControlsApp -> system opens Savvy directly.
///               (Apple doc: "open your parental controls app that is responsible
///               for shielding the application".) Works with .individual is
///               likely but must be proven: T-SHIELD-ACTION-1.
///   iOS 16-26.4 there is no supported way to open the app from the extension
///               (Frameworks Engineer, forums 719905). Fallback: post a local
///               notification, close the blocked app, user taps the notification.
///
/// In both cases the unlock request is recorded in the App Group first, so the
/// app knows to start the card scan as soon as it becomes active.
final class SavvyShieldAction: ShieldActionDelegate {

    override func handle(action: ShieldAction, for application: ApplicationToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        handle(action, completionHandler)
    }

    override func handle(action: ShieldAction, for webDomain: WebDomainToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        handle(action, completionHandler)
    }

    override func handle(action: ShieldAction, for category: ActivityCategoryToken,
                         completionHandler: @escaping (ShieldActionResponse) -> Void) {
        handle(action, completionHandler)
    }

    private func handle(_ action: ShieldAction, _ done: @escaping (ShieldActionResponse) -> Void) {
        let policy = SharedState.commitment?.unlockPolicy
        switch action {
        case .primaryButtonPressed:
            guard policy == .cardRequired || policy == .free else {
                SavvyLog.event("ShieldAction", "primary on locked commitment -> close")
                done(.close)
                return
            }
            SharedState.pendingUnlockRequest = Date()
            SavvyLog.event("ShieldAction", "unlock requested")
            if #available(iOS 26.5, *) {
                SavvyLog.event("ShieldAction", "responding openParentalControlsApp")
                done(.openParentalControlsApp)
                return
            }
            // Respond only after the notification is queued: the extension may be
            // suspended as soon as the completion handler is called.
            postNotification(title: "Unlock with your Savvy card",
                             body: "Tap here, then hold your card near the top of your iPhone.",
                             route: "unlock") { done(.close) }
        case .secondaryButtonPressed:
            SavvyLog.event("ShieldAction", "emergency requested")
            SharedState.pendingEmergencyRequest = Date()
            if #available(iOS 26.5, *) {
                done(.openParentalControlsApp)
                return
            }
            postNotification(title: "Emergency exit",
                             body: "Tap to open Savvy. Emergency exits are limited and recorded.",
                             route: "emergency") { done(.close) }
        @unknown default:
            done(.none)
        }
    }

    private func postNotification(title: String, body: String, route: String, then: @escaping () -> Void) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.userInfo = ["route": route]
        content.interruptionLevel = .timeSensitive
        let request = UNNotificationRequest(identifier: "savvy.\(route)", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request) { error in
            SavvyLog.event("ShieldAction", error.map { "notification error \($0)" } ?? "notification queued")
            then()
        }
    }
}
