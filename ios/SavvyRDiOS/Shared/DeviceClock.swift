import Foundation
import SavvyCore

/// Darwin clock readers for the platform-neutral TimeIntegrity in SavvyCore.
///
/// CLOCK_MONOTONIC on Darwin keeps counting while the device sleeps (unlike
/// ProcessInfo.systemUptime / CLOCK_UPTIME_RAW) and does not move when the user
/// changes date and time. kern.bootsessionuuid changes on every boot. If the
/// sandbox does not return it, TimeIntegrity treats the check as "unknown"
/// rather than guessing. Device test: T-TIME-1..3.
enum DeviceClock {
    static func monotonicSeconds() -> TimeInterval {
        TimeInterval(clock_gettime_nsec_np(CLOCK_MONOTONIC)) / 1_000_000_000
    }

    static func bootSessionID() -> String? {
        var size = 0
        guard sysctlbyname("kern.bootsessionuuid", nil, &size, nil, 0) == 0, size > 0 else { return nil }
        var buffer = [CChar](repeating: 0, count: size)
        guard sysctlbyname("kern.bootsessionuuid", &buffer, &size, nil, 0) == 0 else { return nil }
        return String(cString: buffer)
    }
}

extension TimeAnchor {
    static func now() -> TimeAnchor {
        TimeAnchor(wallClock: Date(), uptime: DeviceClock.monotonicSeconds(), bootSession: DeviceClock.bootSessionID())
    }
}

/// Public keys used to verify backend grants and signed cards offline.
/// Raw 32-byte Ed25519 keys (last 32 bytes of the SPKI DER from GET /v1/keys),
/// hex encoded in Info.plist keys SavvyGrantPublicKey / SavvyCardPublicKey.
/// Readable from extensions too because Info.plist values are copied per target.
enum SavvyKeys {
    static var grantPublicKeyRaw: Data { key("SavvyGrantPublicKey") }
    static var cardPublicKeyRaw: Data { key("SavvyCardPublicKey") }

    private static func key(_ name: String) -> Data {
        let fromDefaults = AppGroup.defaults.string(forKey: name)   // POC: fetched at registration
        let fromPlist = Bundle.main.object(forInfoDictionaryKey: name) as? String
        return Data(hex: fromDefaults ?? fromPlist ?? "") ?? Data()
    }
}
