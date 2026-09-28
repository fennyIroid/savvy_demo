import CoreNFC
import Foundation
import SavvyCore

/// R&D 6 and 9. Foreground card reading.
///
/// Two session types so testers can compare them on the real card:
///   - tag session: returns the chip UID and chip family plus the NDEF URL
///   - NDEF session: returns the NDEF URL only (simplest, what background reading gives)
/// Core NFC works only in the foregrounded main app, never in an extension.
final class NFCReader: NSObject {
    struct Result {
        var url: String?
        var uidHex: String?
        var chipFamily: String
        var elapsed: TimeInterval
        /// Set when a live proof ran (NTAG 424 DNA): single-use backend token.
        var presenceToken: String? = nil
    }

    enum Failure: Error { case unavailable, cancelled, noNDEF, other(String) }

    private var tagSession: NFCTagReaderSession?
    private var ndefSession: NFCNDEFReaderSession?
    private var startedAt = Date()
    private var completion: ((Swift.Result<Result, Failure>) -> Void)?
    /// When set, ISO 7816 cards with a SUN URL also run the live AES proof (LiveProofRelay).
    private var liveBackend: LiveProofBackend?

    static var isAvailable: Bool { NFCTagReaderSession.readingAvailable }

    func scanWithTagSession(prompt: String, liveProof: LiveProofBackend? = nil,
                            completion: @escaping (Swift.Result<Result, Failure>) -> Void) {
        guard NFCTagReaderSession.readingAvailable else { return completion(.failure(.unavailable)) }
        self.completion = completion
        self.liveBackend = liveProof
        startedAt = Date()
        tagSession = NFCTagReaderSession(pollingOption: [.iso14443, .iso15693], delegate: self, queue: nil)
        tagSession?.alertMessage = prompt
        tagSession?.begin()
        SavvyLog.event("NFC", "tag session begin")
    }

    func scanWithNDEFSession(prompt: String, completion: @escaping (Swift.Result<Result, Failure>) -> Void) {
        guard NFCNDEFReaderSession.readingAvailable else { return completion(.failure(.unavailable)) }
        self.completion = completion
        startedAt = Date()
        ndefSession = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: true)
        ndefSession?.alertMessage = prompt
        ndefSession?.begin()
        SavvyLog.event("NFC", "ndef session begin")
    }

    fileprivate func finish(_ result: Swift.Result<Result, Failure>) {
        let done = completion
        completion = nil
        DispatchQueue.main.async { done?(result) }
    }

    static func firstURI(in message: NFCNDEFMessage?) -> String? {
        message?.records.lazy.compactMap { $0.wellKnownTypeURIPayload()?.absoluteString }.first
    }
}

extension NFCReader: NFCTagReaderSessionDelegate {
    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession) {}

    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error) {
        let code = (error as? NFCReaderError)?.code
        SavvyLog.event("NFC", "tag session invalidated \(error.localizedDescription)")
        if code == .readerSessionInvalidationErrorUserCanceled { finish(.failure(.cancelled)) }
        else if code != .readerSessionInvalidationErrorFirstNDEFTagRead { finish(.failure(.other(error.localizedDescription))) }
    }

    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag]) {
        guard let tag = tags.first else { return }
        session.connect(to: tag) { [weak self] error in
            guard let self else { return }
            if let error {
                session.invalidate(errorMessage: "Could not connect. Try again.")
                return self.finish(.failure(.other(error.localizedDescription)))
            }
            let (ndefTag, uid, family): (NFCNDEFTag, Data?, String) = {
                switch tag {
                case .miFare(let t): return (t, t.identifier, "mifare/\(t.mifareFamily.rawValue)")
                case .iso7816(let t): return (t, t.identifier, "iso7816")
                case .iso15693(let t): return (t, t.identifier, "iso15693")
                case .feliCa(let t): return (t, t.currentIDm, "felica")
                @unknown default: fatalError("unknown tag type")
                }
            }()
            ndefTag.readNDEF { message, readError in
                let url = NFCReader.firstURI(in: message)
                let result = Result(url: url, uidHex: uid?.map { String(format: "%02X", $0) }.joined(),
                                    chipFamily: family, elapsed: Date().timeIntervalSince(self.startedAt))
                SavvyLog.event("NFC", "tag read family=\(family) uid=\(result.uidHex ?? "-") url=\(url ?? "-") err=\(readError?.localizedDescription ?? "none") t=\(String(format: "%.2f", result.elapsed))s")
                if url == nil {
                    session.invalidate(errorMessage: "This is not a Savvy card.")
                    self.finish(.failure(.noNDEF))
                    return
                }
                // NTAG 424 DNA + live proof requested: keep the session open and relay.
                if let backend = self.liveBackend, case .iso7816(let isoTag) = tag,
                   let url, case .sun(let code, _, _)? = CardPayload.parse(url, domain: SavvyLinks.cardDomain) {
                    session.alertMessage = "Keep holding your card..."
                    Task {
                        do {
                            let token = try await LiveProofRelay.run(tag: isoTag, cardCode: code, backend: backend)
                            var live = result
                            live.presenceToken = token
                            live.elapsed = Date().timeIntervalSince(self.startedAt)
                            SavvyLog.event("NFC", "live proof ok t=\(String(format: "%.2f", live.elapsed))s")
                            session.alertMessage = "Card verified"
                            session.invalidate()
                            self.finish(.success(live))
                        } catch {
                            SavvyLog.event("NFC", "live proof failed \(error)")
                            session.invalidate(errorMessage: "Card check failed. Try again.")
                            self.finish(.failure(.other("live_proof_\(error)")))
                        }
                    }
                    return
                }
                session.alertMessage = "Card read"
                session.invalidate()
                self.finish(.success(result))
            }
        }
    }
}

extension NFCReader: NFCNDEFReaderSessionDelegate {
    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        let code = (error as? NFCReaderError)?.code
        SavvyLog.event("NFC", "ndef session invalidated \(error.localizedDescription)")
        if code == .readerSessionInvalidationErrorUserCanceled { finish(.failure(.cancelled)) }
        else if code != .readerSessionInvalidationErrorFirstNDEFTagRead { finish(.failure(.other(error.localizedDescription))) }
    }

    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
        let url = NFCReader.firstURI(in: messages.first)
        let result = Result(url: url, uidHex: nil, chipFamily: "ndef-session", elapsed: Date().timeIntervalSince(startedAt))
        SavvyLog.event("NFC", "ndef read url=\(url ?? "-") t=\(String(format: "%.2f", result.elapsed))s")
        finish(url == nil ? .failure(.noNDEF) : .success(result))
    }
}
