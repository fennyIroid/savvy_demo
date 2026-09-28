import Foundation
import ManagedSettings
import FamilyControls

/// Applies and clears shields. Used by the app AND by the DeviceActivityMonitor
/// extension, so shields can be restored or released while the app is not running.
///
/// ManagedSettings values persist in the system after the process exits; the app
/// does not need to run for a shield to stay up (to be proven by T-SHIELD-3..5).
enum ShieldEngine {
    static func apply(_ selection: FamilyActivitySelection, store name: ManagedSettingsStore.Name = .commitment) {
        let store = ManagedSettingsStore(named: name)
        store.shield.applications = selection.applicationTokens.isEmpty ? nil : selection.applicationTokens
        store.shield.applicationCategories = selection.categoryTokens.isEmpty
            ? nil
            : ShieldSettings.ActivityCategoryPolicy.specific(selection.categoryTokens)
        store.shield.webDomains = selection.webDomainTokens.isEmpty ? nil : selection.webDomainTokens
        store.shield.webDomainCategories = selection.categoryTokens.isEmpty
            ? nil
            : ShieldSettings.ActivityCategoryPolicy.specific(selection.categoryTokens)
        SavvyLog.event("ShieldEngine", "apply store=\(name.rawValue) apps=\(selection.applicationTokens.count) cats=\(selection.categoryTokens.count) web=\(selection.webDomainTokens.count)")
    }

    static func clear(store name: ManagedSettingsStore.Name = .commitment) {
        ManagedSettingsStore(named: name).clearAllSettings()
        SavvyLog.event("ShieldEngine", "clear store=\(name.rawValue)")
    }

    static func isShielding(store name: ManagedSettingsStore.Name = .commitment) -> Bool {
        let store = ManagedSettingsStore(named: name)
        return !(store.shield.applications?.isEmpty ?? true) || store.shield.applicationCategories != nil
    }

    /// Commitment guard (R&D 11/12), applied only while a commitment runs.
    ///
    /// denyAppRemoval: blocks deleting ALL apps (not only Savvy) while set.
    ///   Apple Frameworks Engineer: "isn't guaranteed to prevent your app from being
    ///   deleted with .individual authorization, since .individual authorizations
    ///   can be revoked at any time via Settings". So with .individual this is
    ///   friction, not prevention. With .child the system already prevents deletion.
    /// requireAutomaticDateAndTime: stops the user moving the clock to end a
    ///   DeviceActivity window early. Whether it is honoured under .individual is
    ///   not documented: test T-TIME-2.
    static func setRemovalGuard(_ enabled: Bool) {
        let store = ManagedSettingsStore(named: .removalGuard)
        store.application.denyAppRemoval = enabled ? true : nil
        store.dateAndTime.requireAutomaticDateAndTime = enabled ? true : nil
        SavvyLog.event("ShieldEngine", "commitment guard (denyAppRemoval, requireAutomaticDateAndTime)=\(enabled)")
    }
}
