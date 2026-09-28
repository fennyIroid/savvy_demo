import Foundation

/// Mirrors backend/src/crypto/cardToken.js and Android core CardPayload.kt.
/// The same URL is on the NFC chip (NDEF URI record) and printed as the QR code.
public enum CardPayload: Equatable {
    case staticId(cardCode: String)
    case signed(cardCode: String, signature: String)
    case sun(cardCode: String, picc: String, mac: String)

    public var cardCode: String {
        switch self {
        case .staticId(let c), .signed(let c, _), .sun(let c, _, _): return c
        }
    }

    /// Only NTAG 424 DNA SUN messages prove the physical chip was present.
    public var provesPhysicalPresence: Bool { if case .sun = self { return true } else { return false } }

    static let alphabet = Set("0123456789ABCDEFGHJKMNPQRSTVWXYZ")

    static func validCode(_ s: String) -> Bool { s.count == 12 && s.allSatisfy { alphabet.contains($0) } }

    public static func parse(_ raw: String, domain: String) -> CardPayload? {
        guard let comps = URLComponents(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              comps.scheme == "https", comps.host == domain else { return nil }
        let parts = comps.path.split(separator: "/").map(String.init)
        guard parts.first == "c" else { return nil }

        if parts.count == 3, parts[1] == "s", validCode(parts[2]) {
            let q = comps.queryItems ?? []
            guard let e = q.first(where: { $0.name == "e" })?.value, e.count == 32, Data(hex: e) != nil,
                  let c = q.first(where: { $0.name == "c" })?.value, c.count == 16, Data(hex: c) != nil
            else { return nil }
            return .sun(cardCode: parts[2], picc: e, mac: c)
        }
        if parts.count == 2 {
            let seg = parts[1].split(separator: ".", omittingEmptySubsequences: false).map(String.init)
            if seg.count == 3, seg[0] == "1", validCode(seg[1]), !seg[2].isEmpty {
                return .signed(cardCode: seg[1], signature: seg[2])
            }
            if validCode(parts[1]) { return .staticId(cardCode: parts[1]) }
        }
        return nil
    }

    /// Offline check: genuine Savvy signed card AND the one bound to this account.
    /// Proves identity, not possession: a copy of the URL passes too.
    public static func verifyOffline(_ payload: CardPayload, cardPublicKeyRaw: Data, boundCardCode: String?) -> Bool {
        guard case let .signed(code, sig) = payload, code == boundCardCode,
              let sigData = Data(base64URL: sig) else { return false }
        return Ed25519.verify(publicKeyRaw: cardPublicKeyRaw, message: Data("savvy-card:v1:\(code)".utf8), signature: sigData)
    }
}
