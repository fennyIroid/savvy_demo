import Foundation
import os

/// Lifecycle log shared by the app and extensions (App Group file) so a tester can
/// see, for example, whether intervalDidEnd ran while the app was killed.
enum SavvyLog {
    private static let logger = Logger(subsystem: "com.iroid.savvy.rd", category: "lifecycle")
    private static let queue = DispatchQueue(label: "savvy.log")
    private static var fileURL: URL { AppGroup.containerURL.appendingPathComponent("savvy-rd.log") }
    private static let maxBytes = 256 * 1024

    static func event(_ source: String, _ message: String) {
        logger.log("[\(source, privacy: .public)] \(message, privacy: .public)")
        let line = "\(ISO8601DateFormatter().string(from: Date())) up=\(Int(ProcessInfo.processInfo.systemUptime)) [\(source)] \(message)\n"
        queue.async {
            guard let data = line.data(using: .utf8) else { return }
            if let handle = try? FileHandle(forWritingTo: fileURL) {
                defer { try? handle.close() }
                if (try? handle.seekToEnd()) ?? 0 > UInt64(maxBytes) {
                    try? handle.truncate(atOffset: 0)
                }
                try? handle.write(contentsOf: data)
            } else {
                try? data.write(to: fileURL)
            }
        }
    }

    static func read() -> String {
        (try? String(contentsOf: fileURL, encoding: .utf8)) ?? "(empty)"
    }

    static func clear() {
        try? FileManager.default.removeItem(at: fileURL)
    }
}
