// Harness FAKE of FamilyControls (documented API shape). Not Apple code.
import Foundation
import Combine
@_exported import ManagedSettings

public struct FamilyActivitySelection: Codable, Equatable {
    public init() {}
    public var applicationTokens: Set<ApplicationToken> = []
    public var categoryTokens: Set<ActivityCategoryToken> = []
    public var webDomainTokens: Set<WebDomainToken> = []
}
public enum AuthorizationStatus { case notDetermined, denied, approved }
public enum FamilyControlsMember { case individual, child }
public final class AuthorizationCenter {
    public static let shared = AuthorizationCenter()
    @Published public var authorizationStatus: AuthorizationStatus = .notDetermined
    public func requestAuthorization(for member: FamilyControlsMember) async throws { authorizationStatus = .approved }
    public func revokeAuthorization(completionHandler: @escaping (Result<Void, Error>) -> Void) {
        authorizationStatus = .denied
        ManagedSettingsFake.reset() // Apple: revoking removes all restrictions
        completionHandler(.success(()))
    }
}
