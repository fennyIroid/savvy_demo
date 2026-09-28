import Foundation
import SavvyCore

/// Thin client for backend/ (Node.js POC). Base URL comes from Info.plist
/// key SavvyBackendURL so no endpoint is hard-coded in code.
final class BackendClient {
    struct APIError: Error { let status: Int; let code: String }
    struct Registration: Decodable { let user_id: Int; let device_id: Int; let device_token: String }
    struct Commitment: Decodable {
        let id: Int; let mode: String; let unlock_policy: String; let status: String
        let started_at: String; let ends_at: String; let task_ref: String?
    }
    struct ActiveResponse: Decodable { let server_time: String; let commitment: Commitment?; let restored_from_previous_install: Bool }
    struct ReleaseResponse: Decodable { let status: String; let grant: String?; let proof_of_presence: Bool?; let available_at: String? }
    struct RulesResponse: Decodable { let changed: Bool; let version: Int; let rules: ChildRules? }
    /// Parent rule set. `selection_blob` is a base64 JSON FamilyActivitySelection chosen
    /// on the parent's iPhone (tokens are valid within the Family Sharing group).
    struct ChildRules: Codable {
        struct Focus: Codable { let mode: String; let duration_minutes: Int; let unlock_policy: String }
        var selection_blob: String?
        var always_on: Bool?
        var focus: Focus?
    }
    struct Keys: Decodable { let grant_public_key: String; let card_public_key: String }
    struct Insights: Decodable {
        let streak_days: Int; let focus_seconds_today: Int; let focus_seconds_total: Int
        let sessions_by_outcome: [String: Int]
    }
    struct Child: Decodable { let child_device_id: Int; let platform: String; let last_seen_at: String? }
    struct Children: Decodable { let children: [Child] }
    struct ChildStatus: Decodable {
        let child_device_id: Int; let last_seen_at: String?; let authorization_status: String?
        let latest_rule_version: Int; let applied_rule_version: Int?; let flags: [String]
    }
    struct LiveStart: Decodable { let session_id: String; let apdus: [String] }
    struct LiveStep: Decodable { let apdu: String }
    struct LiveFinish: Decodable { let presence_token: String; let expires_in: Int }
    struct LinkCode: Decodable { let code: String; let expires_at: String }
    struct SyncResult: Decodable {
        struct Item: Decodable { let event_id: String?; let server_id: Int?; let violation: String?; let error: String? }
        let server_time: String; let results: [Item]
    }

    // var so tests (ios/AppHarness) can simulate losing and regaining the network.
    var baseURL: URL
    var deviceToken: String?

    init(baseURL: URL = URL(string: Bundle.main.object(forInfoDictionaryKey: "SavvyBackendURL") as? String ?? "http://localhost:3000")!) {
        self.baseURL = baseURL
    }

    func register(email: String, role: String) async throws -> Registration {
        try await send("POST", "/v1/devices/register", ["email": email, "platform": "ios", "role": role])
    }
    func registerCard(payload: String, source: String) async throws -> [String: AnyDecodable] {
        try await send("POST", "/v1/cards/register", ["payload": payload, "source": source])
    }
    func startCommitment(mode: String, minutes: Int, policy: String, taskRef: String?) async throws -> Commitment {
        var body: [String: Any] = ["mode": mode, "duration_minutes": minutes, "unlock_policy": policy]
        if let taskRef { body["task_ref"] = taskRef }
        return try await send("POST", "/v1/commitments", body)
    }
    func active() async throws -> ActiveResponse { try await send("GET", "/v1/commitments/active", nil) }
    func release(id: Int, method: String, payload: String?, source: String,
                 presenceToken: String? = nil) async throws -> ReleaseResponse {
        var body: [String: Any] = ["method": method, "source": source]
        if let payload { body["payload"] = payload }
        if let presenceToken { body["presence_token"] = presenceToken }
        return try await send("POST", "/v1/commitments/\(id)/release", body)
    }
    func emergencyExit(id: Int, reason: String?) async throws -> ReleaseResponse {
        try await send("POST", "/v1/commitments/\(id)/emergency-exit", ["reason": reason ?? ""])
    }
    func heartbeat(authorization: String, appliedRuleVersion: Int?, shieldActive: Bool) async throws -> [String: AnyDecodable] {
        // NSNull, not `Optional as Any`: JSONSerialization raises an uncatchable exception on Optional.
        try await send("POST", "/v1/devices/heartbeat", ["authorization_status": authorization,
            "applied_rule_version": orNull(appliedRuleVersion), "shield_active": shieldActive,
            "push_token": orNull(AppGroup.defaults.string(forKey: "apnsToken"))])
    }
    func liveStart(cardCode: String) async throws -> LiveStart {
        try await send("POST", "/v1/cards/live/start", ["card_code": cardCode])
    }
    func liveStep(sessionId: String, response: Data) async throws -> LiveStep {
        try await send("POST", "/v1/cards/live/step", ["session_id": sessionId, "response": response.hexString])
    }
    func liveFinish(sessionId: String, response: Data) async throws -> LiveFinish {
        try await send("POST", "/v1/cards/live/finish", ["session_id": sessionId, "response": response.hexString])
    }
    func keys() async throws -> Keys { try await send("GET", "/v1/keys", nil) }
    func insights(timeZone: String) async throws -> Insights {
        try await send("GET", "/v1/insights/summary?tz=\(timeZone.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "UTC")", nil)
    }
    func createLinkCode() async throws -> LinkCode { try await send("POST", "/v1/family/link-codes", [:]) }
    func children() async throws -> Children { try await send("GET", "/v1/family/children", nil) }
    func putRules(childDeviceId: Int, rules: ChildRules) async throws -> [String: AnyDecodable] {
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(rules))
        return try await send("PUT", "/v1/family/children/\(childDeviceId)/rules", ["rules": json])
    }
    func childStatus(childDeviceId: Int) async throws -> ChildStatus {
        try await send("GET", "/v1/family/children/\(childDeviceId)/status", nil)
    }
    /// Replays offline actions. Field names match backend/src/services/syncService.js.
    func sync(_ queue: [QueuedEvent]) async throws -> SyncResult {
        let iso = ISO8601DateFormatter.savvy
        let events: [[String: Any]] = queue.map { item in
            var e: [String: Any] = ["event_id": item.id]
            switch item.event {
            case let .commitmentStarted(localId, mode, minutes, policy, taskRef, at):
                e.merge(["type": "commitment_started", "local_id": localId, "mode": mode, "minutes": minutes,
                         "policy": policy, "task_ref": orNull(taskRef), "at": iso.string(from: at)]) { $1 }
            case let .cardRelease(localId, serverId, payload, source, at):
                e.merge(["type": "card_release", "local_id": localId, "server_id": orNull(serverId),
                         "payload": payload, "source": source, "at": iso.string(from: at)]) { $1 }
            case let .emergencyExit(localId, serverId, at):
                e.merge(["type": "emergency_exit", "local_id": localId, "server_id": orNull(serverId),
                         "at": iso.string(from: at)]) { $1 }
            case let .taskCompleted(localId, serverId, at):
                e.merge(["type": "task_completed", "local_id": localId, "server_id": orNull(serverId),
                         "at": iso.string(from: at)]) { $1 }
            }
            return e
        }
        return try await send("POST", "/v1/sync", ["events": events])
    }

    /// JSONSerialization needs NSNull for missing values, never a Swift Optional.
    private func orNull<T>(_ value: T?) -> Any { value.map { $0 as Any } ?? NSNull() }
    func redeemLink(code: String) async throws -> [String: AnyDecodable] { try await send("POST", "/v1/family/link", ["code": code]) }
    func pullRules(since: Int) async throws -> RulesResponse { try await send("GET", "/v1/family/rules?since_version=\(since)", nil) }
    func ackRules(version: Int) async throws -> [String: AnyDecodable] { try await send("POST", "/v1/family/rules/ack", ["version": version]) }

    private func send<T: Decodable>(_ method: String, _ path: String, _ body: [String: Any]?) async throws -> T {
        var req = URLRequest(url: URL(string: path, relativeTo: baseURL)!)
        req.httpMethod = method
        req.timeoutInterval = 8
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let deviceToken { req.setValue("Bearer \(deviceToken)", forHTTPHeaderField: "Authorization") }
        if let body { req.httpBody = try JSONSerialization.data(withJSONObject: body) }
        let (data, response) = try await URLSession.shared.data(for: req)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let code = (try? JSONDecoder().decode([String: String].self, from: data))?["error"] ?? "http_\(status)"
            throw APIError(status: status, code: code)
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
}

extension Data {
    var hexString: String { map { String(format: "%02X", $0) }.joined() }
}

/// Minimal type-erased Decodable for responses the POC only logs.
struct AnyDecodable: Decodable, CustomStringConvertible {
    let description: String
    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if let v = try? c.decode(String.self) { description = v }
        else if let v = try? c.decode(Int.self) { description = String(v) }
        else if let v = try? c.decode(Bool.self) { description = String(v) }
        else { description = "…" }
    }
}
