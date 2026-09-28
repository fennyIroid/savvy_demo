import CoreNFC
import Foundation
import SavvyCore

/// NTAG 424 DNA live proof of presence (docs/NFC_FINDINGS.md section 6).
/// While the card is held to the phone, the app relays the chip's AES
/// AuthenticateEV2First exchange between card and backend. The backend supplies
/// a fresh random value, so recorded taps and clones fail. Must run inside the
/// 60-second NFCTagReaderSession, so it needs a responsive network (about 2
/// round trips). Device test T-NFC-LIVE-1..4.
protocol LiveProofBackend {
    func liveStart(cardCode: String) async throws -> BackendClient.LiveStart
    func liveStep(sessionId: String, response: Data) async throws -> BackendClient.LiveStep
    func liveFinish(sessionId: String, response: Data) async throws -> BackendClient.LiveFinish
}

extension BackendClient: LiveProofBackend {}

enum LiveProofRelay {
    enum Failure: Error { case notA424Card, badAPDU, noNetwork(Error), card(Error) }

    /// Runs the relay on an already connected ISO 7816 tag. Returns the presence token.
    static func run(tag: NFCISO7816Tag, cardCode: String, backend: LiveProofBackend) async throws -> String {
        let start: BackendClient.LiveStart
        do { start = try await backend.liveStart(cardCode: cardCode) } catch { throw Failure.noNetwork(error) }
        var last = Data()
        for hex in start.apdus {
            guard let apdu = Data(hex: hex) else { throw Failure.badAPDU }
            last = try await send(tag, apdu)
        }
        let second: BackendClient.LiveStep
        do { second = try await backend.liveStep(sessionId: start.session_id, response: last) } catch { throw Failure.noNetwork(error) }
        guard let apdu2 = Data(hex: second.apdu) else { throw Failure.badAPDU }
        let final = try await send(tag, apdu2)
        do {
            return try await backend.liveFinish(sessionId: start.session_id, response: final).presence_token
        } catch { throw Failure.noNetwork(error) }
    }

    /// Sends one C-APDU and returns response data + SW1 SW2 (the format the backend expects).
    static func send(_ tag: NFCISO7816Tag, _ bytes: Data) async throws -> Data {
        guard let apdu = NFCISO7816APDU(data: bytes) else { throw Failure.badAPDU }
        return try await withCheckedThrowingContinuation { continuation in
            tag.sendCommand(apdu: apdu) { data, sw1, sw2, error in
                if let error { continuation.resume(throwing: Failure.card(error)) }
                else { continuation.resume(returning: data + Data([sw1, sw2])) }
            }
        }
    }
}
