// Linux-only stand-ins for Apple-only system functions used by the app sources.
// Not part of the iOS app. Compiled only in ios/AppHarness.
@_exported import Foundation
#if canImport(FoundationNetworking)
@_exported import FoundationNetworking
#endif
#if canImport(Glibc)
import Glibc
#endif

extension FileManager {
    /// App Group container: a temp directory on Linux.
    public func containerURL(forSecurityApplicationGroupIdentifier groupIdentifier: String) -> URL? {
        let url = temporaryDirectory.appendingPathComponent("savvy-harness-\(groupIdentifier)")
        try? createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
}

/// Darwin's clock_gettime_nsec_np, implemented with Linux clock_gettime.
public func clock_gettime_nsec_np(_ clockId: clockid_t) -> UInt64 {
    var ts = timespec()
    clock_gettime(clockId, &ts)
    return UInt64(ts.tv_sec) * 1_000_000_000 + UInt64(ts.tv_nsec)
}

/// Only kern.bootsessionuuid is supported: Linux boot_id plays the same role.
public func sysctlbyname(_ name: UnsafePointer<CChar>!, _ oldp: UnsafeMutableRawPointer!, _ oldlenp: UnsafeMutablePointer<Int>!,
                         _ newp: UnsafeMutableRawPointer!, _ newlen: Int) -> Int32 {
    guard String(cString: name) == "kern.bootsessionuuid",
          let id = try? String(contentsOfFile: "/proc/sys/kernel/random/boot_id", encoding: .utf8)
              .trimmingCharacters(in: .whitespacesAndNewlines) else { return -1 }
    let bytes = Array(id.utf8CString)
    if oldp == nil { oldlenp.pointee = bytes.count; return 0 }
    guard oldlenp.pointee >= bytes.count else { return -1 }
    bytes.withUnsafeBytes { oldp.copyMemory(from: $0.baseAddress!, byteCount: bytes.count) }
    oldlenp.pointee = bytes.count
    return 0
}
