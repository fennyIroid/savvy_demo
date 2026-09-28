import Foundation
import FamilyControls
import ManagedSettings

/// iOS 26.5+: Screen Time tokens can expire (Apple added ManagedSettingsStore
/// TokenExpiryMessage and refresh(_:)). TokenExpiryMessage is an
/// NotificationCenter.AsyncMessage whose members are not documented yet, so the
/// POC does not depend on it: on every launch it calls the documented
/// refresh(_:) on the saved selections and saves the result. Test T-SEL-7.
enum TokenRefresher {
    static func refreshSavedSelections() {
        guard #available(iOS 26.5, *) else { return }
        if let s = SharedState.selection { SharedState.selection = refreshed(s) }
        if let s = SharedState.parentSelection { SharedState.parentSelection = refreshed(s) }
    }

    @available(iOS 26.5, *)
    private static func refreshed(_ selection: FamilyActivitySelection) -> FamilyActivitySelection {
        var result = selection
        var apps = Array(selection.applicationTokens)
        var categories = Array(selection.categoryTokens)
        var domains = Array(selection.webDomainTokens)
        do {
            try ManagedSettingsStore.refresh(&apps)
            try ManagedSettingsStore.refresh(&categories)
            try ManagedSettingsStore.refresh(&domains)
            result.applicationTokens = Set(apps)
            result.categoryTokens = Set(categories)
            result.webDomainTokens = Set(domains)
            if result != selection { SavvyLog.event("Tokens", "refreshed expired tokens") }
        } catch {
            SavvyLog.event("Tokens", "refresh failed \(error)")
        }
        return result
    }
}
