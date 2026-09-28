import Foundation
import ManagedSettings
import DeviceActivity

/// Values shared by the app and every extension. Change the identifiers to the
/// real team prefix / bundle IDs before building (see README.md).
enum AppGroup {
    static let identifier = "group.com.iroid.savvy.rd"

    static var defaults: UserDefaults {
        // Force-unwrap is intentional: a missing App Group is a provisioning bug.
        UserDefaults(suiteName: identifier)!
    }

    static var containerURL: URL {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)!
    }
}

/// Deep links. The card URL domain must match the Associated Domains entitlement
/// and the apple-app-site-association file in Web/.
enum SavvyLinks {
    static let cardDomain = "go.savvy.test"
    static let unlockURL = URL(string: "savvyrd://unlock")!
}

extension ManagedSettingsStore.Name {
    /// Focus modes, to-do restrictions and commitments. One active at a time in the POC.
    static let commitment = Self("savvy.commitment")
    /// Separate store so uninstall friction can be switched off without touching shields.
    static let removalGuard = Self("savvy.removalGuard")
    /// Parent-managed rules on a child device.
    static let parentRules = Self("savvy.parentRules")
}

extension DeviceActivityName {
    /// The commitment window. intervalDidEnd releases the commitment store.
    static let commitment = Self("savvy.commitment")
    /// Temporary unlock (card or emergency pause). intervalDidEnd re-applies shields.
    static let pause = Self("savvy.pause")
}
