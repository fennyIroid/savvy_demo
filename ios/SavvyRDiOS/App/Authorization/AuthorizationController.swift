import FamilyControls
import Combine
import Foundation

/// R&D 1. Wraps AuthorizationCenter and records every status change so testers
/// can see what happens when access is revoked in Settings.
@MainActor
final class AuthorizationController: ObservableObject {
    @Published private(set) var status: AuthorizationStatus = AuthorizationCenter.shared.authorizationStatus
    @Published private(set) var lastError: String?
    private var cancellable: AnyCancellable?

    init() {
        cancellable = AuthorizationCenter.shared.$authorizationStatus
            .receive(on: RunLoop.main)
            .sink { [weak self] newValue in
                self?.status = newValue
                SavvyLog.event("Auth", "status -> \(newValue.label)")
            }
    }

    /// .individual = self-use (iOS 16+). .child = parent-authorized on a child's
    /// device in Family Sharing (parent's Apple ID credentials are requested).
    func request(_ member: FamilyControlsMember) async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: member)
            lastError = nil
            SavvyLog.event("Auth", "request \(member) succeeded")
        } catch {
            lastError = String(describing: error)
            SavvyLog.event("Auth", "request \(member) failed \(error)")
        }
    }

    func revoke() {
        AuthorizationCenter.shared.revokeAuthorization { result in
            SavvyLog.event("Auth", "revoke result \(result)")
        }
    }
}

extension AuthorizationStatus {
    var label: String {
        switch self {
        case .notDetermined: return "notDetermined"
        case .denied: return "denied"
        case .approved: return "approved"
        @unknown default: return "unknown"
        }
    }
}
