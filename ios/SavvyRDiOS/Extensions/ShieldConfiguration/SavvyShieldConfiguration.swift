import ManagedSettings
import ManagedSettingsUI
import UIKit
import SavvyCore

/// Savvy-branded shield. Apple allows: background blur style and colour, icon,
/// title, subtitle, primary button label/colours, optional secondary button.
/// No custom views, no text input, no NFC, no network-driven content at render time.
final class SavvyShieldConfiguration: ShieldConfigurationDataSource {

    private func savvyShield(appName: String?) -> ShieldConfiguration {
        let state = SharedState.commitment
        let mode = state?.mode.rawValue.capitalized ?? "Focus"
        let endText: String = {
            guard let end = state?.endsAt else { return "" }
            let f = DateFormatter(); f.timeStyle = .short
            return " until \(f.string(from: end))"
        }()
        let subtitle: String
        switch state?.unlockPolicy {
        case .cardRequired?: subtitle = "\(mode) is on\(endText). Tap your Savvy card to unlock."
        case .locked?: subtitle = "\(mode) commitment is locked\(endText)."
        default: subtitle = "\(mode) is on\(endText)."
        }
        return ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialDark,
            backgroundColor: UIColor(red: 0.07, green: 0.09, blue: 0.16, alpha: 1),
            icon: UIImage(named: "ShieldIcon"),
            title: ShieldConfiguration.Label(text: "\(appName ?? "This app") is paused by Savvy", color: .white),
            subtitle: ShieldConfiguration.Label(text: subtitle, color: .lightGray),
            primaryButtonLabel: ShieldConfiguration.Label(
                text: state?.unlockPolicy == .locked ? "Close" : "Unlock with Savvy card", color: .black),
            primaryButtonBackgroundColor: UIColor(red: 0.55, green: 0.87, blue: 0.62, alpha: 1),
            secondaryButtonLabel: ShieldConfiguration.Label(text: "Emergency exit", color: .lightGray)
        )
    }

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        savvyShield(appName: application.localizedDisplayName)
    }

    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration {
        savvyShield(appName: application.localizedDisplayName)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        savvyShield(appName: webDomain.domain)
    }

    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration {
        savvyShield(appName: webDomain.domain)
    }
}
