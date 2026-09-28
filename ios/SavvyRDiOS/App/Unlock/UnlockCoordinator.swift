import Foundation
import SavvyCore

/// R&D 8, 10, 17. The full journey from "card payload received" to "shield removed".
///
/// Card payload can arrive from:
///   .nfcForeground   in-app NFCTagReaderSession / NFCNDEFReaderSession
///   .nfcBackground   iOS background tag read -> notification -> universal link
///   .qr              in-app camera scanner
///   .link            universal link opened some other way (system Camera QR, pasted link)
///
/// Decision order:
///   1. Parse. Reject non-Savvy payloads immediately (no network).
///   2. Online: backend verifies card ownership + commitment policy and returns a
///      signed grant. App verifies the grant (SavvyCore.GrantVerifier), then clears.
///   3. Offline: SavvyCore.OfflinePolicy decides (signed + bound card only), the
///      release is queued for /v1/sync.
@MainActor
final class UnlockCoordinator {
    enum Source: String { case nfcForeground = "nfc", nfcBackground = "nfc_background", qr, link }

    enum Outcome: Equatable {
        case released(offline: Bool)
        case paused(until: Date)
        case pending(availableAt: String)
        case rejected(String)
    }

    var allowOfflineSignedCardUnlock = true
    private let backend: BackendClient
    private let deviceId: () -> Int?

    init(backend: BackendClient, deviceId: @escaping () -> Int?) {
        self.backend = backend
        self.deviceId = deviceId
    }

    /// NTAG 424 DNA live proof path: the presence token already proves the real card
    /// was at the phone just now. Needs network by design (no offline path).
    func handleLiveProof(url: String, presenceToken: String) async -> Outcome {
        defer { SharedState.pendingUnlockRequest = nil }
        guard let state = SharedState.commitment else { return .rejected("no_active_commitment") }
        guard let serverId = state.serverId, let deviceId = deviceId() else { return .rejected("live_proof_needs_synced_commitment") }
        do {
            let r = try await backend.release(id: serverId, method: "card", payload: url, source: "nfc_live",
                                              presenceToken: presenceToken)
            return try applyGrant(r.grant, deviceId: deviceId, commitmentId: serverId)
        } catch let e as BackendClient.APIError {
            return .rejected(e.code)
        } catch {
            return .rejected("live_proof_\(error)")
        }
    }

    func handleCard(raw: String, source: Source) async -> Outcome {
        let started = SharedState.pendingUnlockRequest ?? Date()
        defer { SharedState.pendingUnlockRequest = nil }

        guard CardPayload.parse(raw, domain: SavvyLinks.cardDomain) != nil else {
            SavvyLog.event("Unlock", "rejected not_a_savvy_card source=\(source.rawValue)")
            return .rejected("not_a_savvy_card")
        }
        guard let state = SharedState.commitment else { return .rejected("no_active_commitment") }
        if state.unlockPolicy == .locked { return .rejected("locked_commitment_no_early_unlock") }

        if let serverId = state.serverId, let deviceId = deviceId() {
            do {
                let r = try await backend.release(id: serverId, method: "card", payload: raw, source: source.rawValue)
                let outcome = try applyGrant(r.grant, deviceId: deviceId, commitmentId: serverId)
                SavvyLog.event("Unlock", "online \(outcome) source=\(source.rawValue) total=\(String(format: "%.1f", Date().timeIntervalSince(started)))s")
                return outcome
            } catch let e as BackendClient.APIError {
                SavvyLog.event("Unlock", "backend rejected \(e.code)")
                return .rejected(e.code)
            } catch let e as GrantVerifier.Failure {
                SavvyLog.event("Unlock", "grant failed \(e)")
                return .rejected("grant_\(e)")
            } catch {
                SavvyLog.event("Unlock", "network error \(error), trying offline path")
            }
        }

        let decision = OfflinePolicy.cardUnlock(raw: raw, domain: SavvyLinks.cardDomain, state: state,
                                                allowOffline: allowOfflineSignedCardUnlock,
                                                cardPublicKeyRaw: SavvyKeys.cardPublicKeyRaw,
                                                boundCardCode: SharedState.boundCardCode)
        guard decision == .release else {
            if case .reject(let reason) = decision { return .rejected(reason) }
            return .rejected("offline_card_check_failed")
        }
        SharedState.enqueue(.cardRelease(localId: state.localId, serverId: state.serverId, payload: raw,
                                         source: source.rawValue, at: Date()))
        releaseLocally(reason: "card_offline_\(source.rawValue)")
        return .released(offline: true)
    }

    /// Verifies the grant and applies it. Never clears a shield without a valid grant
    /// (the offline path above is the only exception, and it is queued for sync).
    func applyGrant(_ grant: String?, deviceId: Int, commitmentId: Int) throws -> Outcome {
        guard let grant else { throw GrantVerifier.Failure.malformed }
        let g = try GrantVerifier.verify(grant, publicKeyRaw: SavvyKeys.grantPublicKeyRaw,
                                         deviceId: deviceId, commitmentId: commitmentId)
        if g.action == "pause", let until = g.pauseUntil {
            ShieldEngine.clear(store: .commitment)
            try? ScheduleEngine.startPauseWindow(until: until)
            if var s = SharedState.commitment { s.pausedUntil = until; SharedState.commitment = s }
            return .paused(until: until)
        }
        releaseLocally(reason: g.reason)
        return .released(offline: false)
    }

    func releaseLocally(reason: String) {
        ShieldEngine.clear(store: .commitment)
        ShieldEngine.setRemovalGuard(false)
        ScheduleEngine.stop([.commitment, .pause])
        SharedState.commitment = nil
        SavvyLog.event("Unlock", "released locally reason=\(reason)")
    }
}
