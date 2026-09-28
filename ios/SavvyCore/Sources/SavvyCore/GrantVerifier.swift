import Foundation

/// Backend unlock grant (backend/src/crypto/grants.js):
/// base64url(json) + "." + base64url(Ed25519 signature over the ASCII first part).
public struct UnlockGrant: Decodable, Equatable {
    public let grant_id: String
    public let commitment_id: Int
    public let device_id: Int
    public let action: String          // "release" | "pause"
    public let reason: String
    public let issued_at: String
    public let expires_at: String
    public let pause_until: String?

    public var pauseUntil: Date? { pause_until.flatMap(ISO8601DateFormatter.savvy.date(from:)) }
}

public enum GrantVerifier {
    public enum Failure: Error, Equatable { case malformed, badSignature, expired, wrongDevice, wrongCommitment }

    public static func verify(_ grant: String, publicKeyRaw: Data, deviceId: Int, commitmentId: Int,
                              now: Date = Date()) throws -> UnlockGrant {
        let parts = grant.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 2, let body = Data(base64URL: String(parts[0])),
              let sig = Data(base64URL: String(parts[1])) else { throw Failure.malformed }
        guard Ed25519.verify(publicKeyRaw: publicKeyRaw, message: Data(parts[0].utf8), signature: sig) else {
            throw Failure.badSignature
        }
        guard let g = try? JSONDecoder().decode(UnlockGrant.self, from: body),
              let exp = ISO8601DateFormatter.savvy.date(from: g.expires_at) else { throw Failure.malformed }
        if exp < now { throw Failure.expired }
        guard g.device_id == deviceId else { throw Failure.wrongDevice }
        guard g.commitment_id == commitmentId else { throw Failure.wrongCommitment }
        return g
    }
}
