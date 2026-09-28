import XCTest
@testable import SavvyCore

private let domain = "go.savvy.test"

final class CrossImplementationTests: XCTestCase {
    /// Fixtures produced by backend/scripts/make-fixtures.js (same file as the Android core tests).
    var f: [String: String] = [:]

    override func setUpWithError() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "fixtures", withExtension: "json"))
        f = try JSONDecoder().decode([String: String].self, from: Data(contentsOf: url))
    }

    var cardKey: Data { Data(hex: f["card_public_key_raw_hex"]!)! }
    var grantKey: Data { Data(hex: f["grant_public_key_raw_hex"]!)! }
    func date(_ k: String) -> Date { ISO8601DateFormatter.savvy.date(from: f[k]!)! }

    func testParsesAllFormats() {
        XCTAssertEqual(CardPayload.parse(f["static_card_url"]!, domain: domain), .staticId(cardCode: "8V1QFQWTY6VG"))
        guard case .signed(let code, _)? = CardPayload.parse(f["signed_card_url"]!, domain: domain) else { return XCTFail() }
        XCTAssertEqual(code, "8V1QFQWTY6VG")
        let sun = CardPayload.parse(f["sun_card_url"]!, domain: domain)
        XCTAssertEqual(sun, .sun(cardCode: "8V1QFQWTY6VG", picc: "EF963FF7828658A599F3041510671E88", mac: "94EED9EE65337086"))
        XCTAssertTrue(sun!.provesPhysicalPresence)
        XCTAssertNil(CardPayload.parse("https://evil.test/c/8V1QFQWTY6VG", domain: domain))
        XCTAssertNil(CardPayload.parse("http://go.savvy.test/c/8V1QFQWTY6VG", domain: domain))
        XCTAssertNil(CardPayload.parse("https://go.savvy.test/c/lowercase1234", domain: domain))
        XCTAssertNil(CardPayload.parse("https://go.savvy.test/c/s/8V1QFQWTY6VG?e=ZZ&c=11", domain: domain))
        XCTAssertNil(CardPayload.parse("hello", domain: domain))
    }

    func testBackendSignedCardVerifiesOfflineOnlyForBoundCard() {
        let p = CardPayload.parse(f["signed_card_url"]!, domain: domain)!
        XCTAssertTrue(CardPayload.verifyOffline(p, cardPublicKeyRaw: cardKey, boundCardCode: "8V1QFQWTY6VG"))
        XCTAssertFalse(CardPayload.verifyOffline(p, cardPublicKeyRaw: cardKey, boundCardCode: "AAAAAAAAAAAA"))
        XCTAssertFalse(CardPayload.verifyOffline(p, cardPublicKeyRaw: grantKey, boundCardCode: "8V1QFQWTY6VG"))
        let forged = CardPayload.parse(f["forged_card_url"]!, domain: domain)!
        XCTAssertFalse(CardPayload.verifyOffline(forged, cardPublicKeyRaw: cardKey, boundCardCode: "ZZZZZZZZZZZZ"))
    }

    func testBackendGrantVerifies() throws {
        let g = try GrantVerifier.verify(f["grant"]!, publicKeyRaw: grantKey, deviceId: 7, commitmentId: 42, now: date("grant_now_valid"))
        XCTAssertEqual(g.action, "release")
        let p = try GrantVerifier.verify(f["pause_grant"]!, publicKeyRaw: grantKey, deviceId: 7, commitmentId: 42, now: date("grant_now_valid"))
        XCTAssertEqual(p.pauseUntil, ISO8601DateFormatter.savvy.date(from: "2026-09-25T09:15:00.000Z"))
    }

    func testGrantRejections() {
        let g = f["grant"]!
        let now = date("grant_now_valid")
        func failure(_ grant: String, key: Data? = nil, device: Int = 7, commitment: Int = 42, at: Date? = nil) -> GrantVerifier.Failure? {
            do { _ = try GrantVerifier.verify(grant, publicKeyRaw: key ?? grantKey, deviceId: device, commitmentId: commitment, now: at ?? now); return nil }
            catch { return error as? GrantVerifier.Failure }
        }
        XCTAssertEqual(failure(g, at: date("grant_now_expired")), .expired)
        XCTAssertEqual(failure(g, device: 8), .wrongDevice)
        XCTAssertEqual(failure(g, commitment: 43), .wrongCommitment)
        XCTAssertEqual(failure(g, key: cardKey), .badSignature)
        var chars = Array(g)
        chars[10] = chars[10] == "A" ? "B" : "A"
        XCTAssertEqual(failure(String(chars)), .badSignature)
        XCTAssertEqual(failure("garbage"), .malformed)
    }
}

final class TimeIntegrityTests: XCTestCase {
    let start = TimeAnchor(wallClock: Date(timeIntervalSince1970: 1_800_000_000), uptime: 5 * 3600, bootSession: "boot-A")
    func state(_ hours: Double = 6) -> CommitmentState {
        CommitmentState(serverId: 1, mode: .study, unlockPolicy: .cardRequired, taskRef: nil, startedAt: start.wallClock,
                        endsAt: start.wallClock.addingTimeInterval(hours * 3600), durationSeconds: hours * 3600,
                        timeAnchor: start, serverEndsAtKnown: true)
    }
    func later(wall: TimeInterval, up: TimeInterval, boot: String? = "boot-A") -> TimeAnchor {
        TimeAnchor(wallClock: start.wallClock.addingTimeInterval(wall), uptime: start.uptime + up, bootSession: boot)
    }

    func testTrusted() {
        XCTAssertEqual(TimeIntegrity.check(since: start, now: later(wall: 3600, up: 3600)), .trusted(elapsed: 3600))
        XCTAssertEqual(TimeIntegrity.remaining(for: state(), now: later(wall: 3600, up: 3600)), 5 * 3600)
    }

    func testClockMovedForwardIgnoredInSameBoot() {
        let now = later(wall: 8 * 3600, up: 3600)
        XCTAssertEqual(TimeIntegrity.check(since: start, now: now), .clockChanged(trueElapsed: 3600, wallElapsed: 8 * 3600))
        XCTAssertEqual(TimeIntegrity.remaining(for: state(), now: now), 5 * 3600)
    }

    func testRebootUsesServerTimeWhenKnown() {
        let now = TimeAnchor(wallClock: start.wallClock.addingTimeInterval(9 * 3600), uptime: 600, bootSession: "boot-B")
        XCTAssertEqual(TimeIntegrity.remaining(for: state(), now: now, serverNow: start.wallClock.addingTimeInterval(2 * 3600)), 4 * 3600)
        XCTAssertEqual(TimeIntegrity.remaining(for: state(), now: now), 0) // documented gap: reboot + offline + clock change
    }

    func testMissingBootSessionIsTreatedAsUnknown() {
        let noSession = TimeAnchor(wallClock: start.wallClock, uptime: start.uptime, bootSession: nil)
        XCTAssertEqual(TimeIntegrity.check(since: noSession, now: later(wall: 60, up: 60)), .rebootedOrUnknown(wallElapsed: 60))
    }
}

final class OfflinePolicyTests: XCTestCase {
    var f: [String: String] = [:]
    override func setUpWithError() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "fixtures", withExtension: "json"))
        f = try JSONDecoder().decode([String: String].self, from: Data(contentsOf: url))
    }
    func state(_ policy: CommitmentState.UnlockPolicy, mode: CommitmentState.Mode = .study, task: String? = nil) -> CommitmentState {
        let a = TimeAnchor(wallClock: Date(), uptime: 0, bootSession: "b")
        return CommitmentState(serverId: nil, mode: mode, unlockPolicy: policy, taskRef: task, startedAt: Date(),
                               endsAt: Date().addingTimeInterval(3600), durationSeconds: 3600, timeAnchor: a, serverEndsAtKnown: false)
    }

    func testOfflineCardDecisions() {
        let key = Data(hex: f["card_public_key_raw_hex"]!)!
        let signed = f["signed_card_url"]!
        func decide(_ raw: String, _ s: CommitmentState?, allow: Bool = true, bound: String? = "8V1QFQWTY6VG") -> OfflinePolicy.CardDecision {
            OfflinePolicy.cardUnlock(raw: raw, domain: domain, state: s, allowOffline: allow, cardPublicKeyRaw: key, boundCardCode: bound)
        }
        XCTAssertEqual(decide(signed, state(.cardRequired)), .release)
        XCTAssertEqual(decide(signed, state(.locked)), .reject("locked_commitment_no_early_unlock"))
        XCTAssertEqual(decide(signed, nil), .reject("no_active_commitment"))
        XCTAssertEqual(decide(signed, state(.cardRequired), allow: false), .reject("offline_unlock_disabled"))
        XCTAssertEqual(decide(signed, state(.cardRequired), bound: "AAAAAAAAAAAA"), .reject("offline_card_check_failed"))
        XCTAssertEqual(decide(f["sun_card_url"]!, state(.cardRequired)), .reject("sun_card_needs_internet"))
        XCTAssertEqual(decide(f["static_card_url"]!, state(.cardRequired)), .reject("offline_card_check_failed"))
        XCTAssertEqual(decide("https://example.com", state(.cardRequired)), .reject("not_a_savvy_card"))
    }

    func testOfflineEmergencyLimit() {
        let now = Date()
        XCTAssertTrue(OfflinePolicy.emergencyAllowed(history: [], now: now))
        XCTAssertFalse(OfflinePolicy.emergencyAllowed(history: [now.addingTimeInterval(-86_400)], now: now))
        XCTAssertTrue(OfflinePolicy.emergencyAllowed(history: [now.addingTimeInterval(-8 * 86_400)], now: now))
        // Clock moved back after using the exit: the entry is in the "future" and must still count.
        XCTAssertFalse(OfflinePolicy.emergencyAllowed(history: [now.addingTimeInterval(86_400)], now: now))
    }

    func testOfflineTaskCompletion() {
        XCTAssertEqual(OfflinePolicy.taskCompletion(state: state(.free, mode: .task, task: "t1"), taskId: "t1"), .release)
        XCTAssertEqual(OfflinePolicy.taskCompletion(state: state(.cardRequired, mode: .task, task: "t1"), taskId: "t1"), .reject("card_or_server_required"))
        XCTAssertEqual(OfflinePolicy.taskCompletion(state: state(.free, mode: .task, task: "t1"), taskId: "t2"), .reject("not_this_task"))
    }

    func testOfflineEventsRoundTrip() throws {
        let events: [OfflineEvent] = [
            .commitmentStarted(localId: "L1", mode: "study", minutes: 60, policy: "card_required", taskRef: nil, at: Date(timeIntervalSince1970: 1)),
            .cardRelease(localId: "L1", serverId: nil, payload: "x", source: "nfc", at: Date(timeIntervalSince1970: 2)),
            .emergencyExit(localId: "L1", serverId: 5, at: Date(timeIntervalSince1970: 3)),
        ]
        XCTAssertEqual(try JSONDecoder().decode([OfflineEvent].self, from: JSONEncoder().encode(events)), events)
    }
}
