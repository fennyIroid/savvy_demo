import SwiftUI
import UserNotifications
import BackgroundTasks

@main
struct SavvyRDApp: App {
    static let refreshTaskId = "com.iroid.savvy.rd.refresh"

    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var model = AppModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .onOpenURL { model.handle(url: $0) }
                // Background NFC tag read and card QR opened via the system Camera
                // both arrive here as a universal link.
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { model.handle(userActivity: $0) }
                .onReceive(NotificationCenter.default.publisher(for: .savvyNotificationRoute)) { note in
                    if (note.object as? String) == "unlock" { model.scanCard(tagSession: true) }
                    if (note.object as? String) == "emergency" { model.showEmergency = true }
                }
                .onReceive(NotificationCenter.default.publisher(for: .savvySilentPush)) { _ in
                    Task { await model.restoreOnLaunch() }
                }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { Task { await model.restoreOnLaunch() } }
            if phase == .background { Self.scheduleRefresh() }
        }
        // Child devices pick up parent rules and flush offline events even when
        // Savvy is not opened (system decides timing; not guaranteed). T-PC-7.
        .backgroundTask(.appRefresh(Self.refreshTaskId)) {
            SavvyLog.event("App", "background refresh")
            await model.restoreOnLaunch()
            Self.scheduleRefresh()
        }
    }

    static func scheduleRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do { try BGTaskScheduler.shared.submit(request) }
        catch { SavvyLog.event("App", "refresh schedule failed \(error)") }
    }
}

extension Notification.Name {
    static let savvyNotificationRoute = Notification.Name("savvyNotificationRoute")
    static let savvySilentPush = Notification.Name("savvySilentPush")
}

/// Routes taps on the notifications posted by the ShieldAction extension (iOS < 26.5),
/// and silent pushes the backend sends when a parent changes the child's rules.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .timeSensitive]) { granted, _ in
            SavvyLog.event("App", "notification permission \(granted)")
        }
        application.registerForRemoteNotifications()
        SavvyLog.event("App", "didFinishLaunching")
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        AppGroup.defaults.set(hex, forKey: "apnsToken")   // sent with the next heartbeat
        SavvyLog.event("App", "APNs token received")
    }

    func application(_ application: UIApplication, didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                     fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        SavvyLog.event("App", "silent push \(userInfo["reason"] ?? "-")")
        NotificationCenter.default.post(name: .savvySilentPush, object: nil)
        // Give the async restore a moment; POC keeps it simple.
        DispatchQueue.main.asyncAfter(deadline: .now() + 20) { completionHandler(.newData) }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let route = response.notification.request.content.userInfo["route"] as? String
        SavvyLog.event("App", "notification tapped route=\(route ?? "-")")
        // Clear the pending flag so restoreOnLaunch does not start a second scan.
        SharedState.pendingUnlockRequest = nil
        NotificationCenter.default.post(name: .savvyNotificationRoute, object: route)
        completionHandler()
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}
