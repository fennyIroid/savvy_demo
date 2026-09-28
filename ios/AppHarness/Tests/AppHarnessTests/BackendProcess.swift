import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Starts backend/src/server.js (SAVVY_DEV=1) on a free port for the test.
final class BackendProcess {
    let port: Int
    let url: URL
    private let process = Process()
    private let stdinPipe = Pipe()

    init() throws {
        port = Int.random(in: 20_000...40_000)
        url = URL(string: "http://127.0.0.1:\(port)")!
        let backendDir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
            .appendingPathComponent("../../../../backend").standardizedFileURL
        process.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        process.arguments = ["node", "src/server.js"]
        process.currentDirectoryURL = backendDir
        var env = ProcessInfo.processInfo.environment
        env["PORT"] = String(port); env["SAVVY_DEV"] = "1"; env["SAVVY_CARD_DOMAINS"] = "go.savvy.test"
        env["SAVVY_EXIT_ON_STDIN_CLOSE"] = "1"
        process.environment = env
        process.standardInput = stdinPipe   // closes if this test process dies -> backend exits
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        try process.run()
        let deadline = Date().addingTimeInterval(15)
        while (try? call("GET", "/v1/time")) == nil {
            guard Date() < deadline else { throw NSError(domain: "backend", code: 1) }
            Thread.sleep(forTimeInterval: 0.1)
        }
    }

    deinit { process.terminate(); process.waitUntilExit() }

    /// Small synchronous JSON call used by the "other device" (for example the parent).
    @discardableResult
    func call(_ method: String, _ path: String, _ body: [String: Any]? = nil, token: String? = nil) throws -> [String: Any] {
        var req = URLRequest(url: URL(string: path, relativeTo: url)!)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { req.httpBody = try JSONSerialization.data(withJSONObject: body) }
        let sem = DispatchSemaphore(value: 0)
        var result: Result<Data, Error> = .failure(NSError(domain: "http", code: 0))
        URLSession.shared.dataTask(with: req) { data, _, error in
            result = error.map { .failure($0) } ?? .success(data ?? Data())
            sem.signal()
        }.resume()
        sem.wait()
        return (try JSONSerialization.jsonObject(with: result.get()) as? [String: Any]) ?? [:]
    }

    func newCard() throws -> String { try call("POST", "/v1/dev/cards", [:])["url"] as! String }
}
