// Harness FAKE of ManagedSettings. Mirrors Apple's documented API shape; the state
// lives in ManagedSettingsFake so tests can see what the "system" is enforcing.
// Named stores with the same name share settings, like the real system-held stores.
import Foundation

public struct ApplicationToken: Hashable, Codable { public let id: String; public init(_ id: String) { self.id = id } }
public struct ActivityCategoryToken: Hashable, Codable { public let id: String; public init(_ id: String) { self.id = id } }
public struct WebDomainToken: Hashable, Codable { public let id: String; public init(_ id: String) { self.id = id } }
public struct Application { public var localizedDisplayName: String?; public var bundleIdentifier: String? }
public struct WebDomain { public var domain: String? }

public struct ShieldSettings {
    public enum ActivityCategoryPolicy<Category> {
        case all(except: Set<ApplicationToken> = [])
        case specific(Set<ActivityCategoryToken>, except: Set<ApplicationToken> = [])
        case none
    }
    public init() {}
    public var applications: Set<ApplicationToken>?
    public var applicationCategories: ActivityCategoryPolicy<Application>?
    public var webDomains: Set<WebDomainToken>?
    public var webDomainCategories: ActivityCategoryPolicy<WebDomain>?
}
public struct ApplicationSettings { public init() {}; public var denyAppRemoval: Bool? }
public struct DateAndTimeSettings { public init() {}; public var requireAutomaticDateAndTime: Bool? }

public enum ManagedSettingsFake {
    public struct State {
        public var shield = ShieldSettings()
        public var application = ApplicationSettings()
        public var dateAndTime = DateAndTimeSettings()
    }
    public static var stores: [String: State] = [:]
    public static func reset() { stores = [:] }
    /// Apps shielded by any store (the system applies the union).
    public static var shieldedApps: Set<ApplicationToken> {
        stores.values.reduce(into: Set<ApplicationToken>()) { $0.formUnion($1.shield.applications ?? []) }
    }
    public static var appRemovalDenied: Bool { stores.values.contains { $0.application.denyAppRemoval == true } }
}

open class ManagedSettingsStore {
    public struct Name: Hashable, RawRepresentable {
        public var rawValue: String
        public init(rawValue: String) { self.rawValue = rawValue }
        public init(_ rawValue: String) { self.rawValue = rawValue }
    }
    private let key: String
    public init() { key = "default" }
    public init(named name: Name) { key = name.rawValue }

    public var shield: ShieldSettings {
        get { ManagedSettingsFake.stores[key]?.shield ?? ShieldSettings() }
        set { ManagedSettingsFake.stores[key, default: .init()].shield = newValue }
    }
    public var application: ApplicationSettings {
        get { ManagedSettingsFake.stores[key]?.application ?? ApplicationSettings() }
        set { ManagedSettingsFake.stores[key, default: .init()].application = newValue }
    }
    public var dateAndTime: DateAndTimeSettings {
        get { ManagedSettingsFake.stores[key]?.dateAndTime ?? DateAndTimeSettings() }
        set { ManagedSettingsFake.stores[key, default: .init()].dateAndTime = newValue }
    }
    public func clearAllSettings() { ManagedSettingsFake.stores[key] = nil }
    public static func refresh(_ tokens: inout [ApplicationToken]) throws {}
    public static func refresh(_ tokens: inout [ActivityCategoryToken]) throws {}
    public static func refresh(_ tokens: inout [WebDomainToken]) throws {}
}

public enum ShieldAction { case primaryButtonPressed, secondaryButtonPressed }
public enum ShieldActionResponse: Equatable { case none, close, `defer`, openParentalControlsApp }
open class ShieldActionDelegate: NSObject {
    public override init() {}
    open func handle(action: ShieldAction, for application: ApplicationToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {}
    open func handle(action: ShieldAction, for webDomain: WebDomainToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {}
    open func handle(action: ShieldAction, for category: ActivityCategoryToken, completionHandler: @escaping (ShieldActionResponse) -> Void) {}
}
