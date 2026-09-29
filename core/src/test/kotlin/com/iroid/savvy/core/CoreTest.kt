package com.iroid.savvy.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MIN = 60_000L
private const val HOUR = 60 * MIN

class TimeIntegrityTest {
    private val start = TimeAnchor(wallClockMs = 1_000_000_000_000, elapsedRealtimeMs = 5 * HOUR, bootCount = 12)
    private val sixHours = commitment(durationMs = 6 * HOUR)

    @Test fun trustedWhenClocksAgree() {
        val now = start.copy(wallClockMs = start.wallClockMs + HOUR, elapsedRealtimeMs = start.elapsedRealtimeMs + HOUR)
        assertEquals(TimeIntegrity.Verdict.Trusted(HOUR), TimeIntegrity.check(start, now))
        assertEquals(5 * HOUR, TimeIntegrity.remainingMs(sixHours, now))
    }

    @Test fun clockMovedForwardIsIgnoredInSameBoot() {
        // User moves the clock +7 h after 1 h of real time.
        val now = start.copy(wallClockMs = start.wallClockMs + 8 * HOUR, elapsedRealtimeMs = start.elapsedRealtimeMs + HOUR)
        assertIs<TimeIntegrity.Verdict.ClockChanged>(TimeIntegrity.check(start, now))
        assertEquals(5 * HOUR, TimeIntegrity.remainingMs(sixHours, now))
    }

    @Test fun afterRebootUsesServerEndTimeWhenKnown() {
        val c = sixHours.copy(serverEndsAtMs = start.wallClockMs + 6 * HOUR)
        val now = TimeAnchor(start.wallClockMs + 9 * HOUR, elapsedRealtimeMs = 10 * MIN, bootCount = 13) // clock moved
        val serverNow = start.wallClockMs + 2 * HOUR
        assertEquals(4 * HOUR, TimeIntegrity.remainingMs(c, now, serverNowMs = serverNow))
    }

    @Test fun afterRebootOfflineWithoutBootRecordFallsBackToWallClock() {
        val now = TimeAnchor(start.wallClockMs + 9 * HOUR, elapsedRealtimeMs = 10 * MIN, bootCount = 13)
        // No LOCKED_BOOT_COMPLETED record (receiver never ran): the old gap remains.
        assertEquals(0, TimeIntegrity.remainingMs(sixHours, now))
    }

    @Test fun rebootThenClockForwardOfflineNoLongerEndsCommitment() {
        // 1 h in, reboot takes 5 min; boot wall clock recorded before unlock. Then the user moves the clock +7 h.
        val bootWall = start.wallClockMs + HOUR + 5 * MIN
        val now = TimeAnchor(bootWall + 7 * HOUR + 10 * MIN, elapsedRealtimeMs = 10 * MIN, bootCount = 13)
        val cp = TimeIntegrity.checkpoint(sixHours, start.copy(wallClockMs = start.wallClockMs + HOUR, elapsedRealtimeMs = start.elapsedRealtimeMs + HOUR))
        assertEquals(HOUR, cp.creditedMs)
        // credited 1 h + gap 5 min + 10 min since boot = 1 h 15 min elapsed.
        assertEquals(4 * HOUR + 45 * MIN, TimeIntegrity.remainingMs(sixHours, now, checkpoint = cp, bootWallMs = bootWall))
    }

    @Test fun powerOffTimeStillCounts() {
        // Honest case: phone off for 3 h, no clock change. Off time is part of the commitment.
        val cp = TimeIntegrity.checkpoint(sixHours, start.copy(wallClockMs = start.wallClockMs + HOUR, elapsedRealtimeMs = start.elapsedRealtimeMs + HOUR))
        val bootWall = start.wallClockMs + 4 * HOUR
        val now = TimeAnchor(bootWall + 30 * MIN, elapsedRealtimeMs = 30 * MIN, bootCount = 13)
        assertEquals(90 * MIN, TimeIntegrity.remainingMs(sixHours, now, checkpoint = cp, bootWallMs = bootWall))
    }

    @Test fun clockChangeBeforeRebootIsNotCountedWhenCheckpointedOnTimeChange() {
        // 1 h in the user moves the clock +8 h (ACTION_TIME_CHANGED -> checkpoint), then reboots at once.
        val changed = start.copy(wallClockMs = start.wallClockMs + 9 * HOUR, elapsedRealtimeMs = start.elapsedRealtimeMs + HOUR)
        val cp = TimeIntegrity.checkpoint(sixHours, changed)
        assertEquals(HOUR, cp.creditedMs) // monotonic, the +8 h is ignored
        val bootWall = changed.wallClockMs + MIN // the RTC keeps the changed clock
        val now = TimeAnchor(bootWall + 2 * MIN, elapsedRealtimeMs = 2 * MIN, bootCount = 13)
        assertEquals(6 * HOUR - HOUR - 3 * MIN, TimeIntegrity.remainingMs(sixHours, now, checkpoint = cp, bootWallMs = bootWall))
    }

    @Test fun checkpointInCurrentBootContinuesMonotonically() {
        val cp = Checkpoint(bootCount = 13, elapsedRealtimeMs = 20 * MIN, wallClockMs = 0, creditedMs = 2 * HOUR)
        val now = TimeAnchor(start.wallClockMs + 30 * HOUR, elapsedRealtimeMs = 50 * MIN, bootCount = 13) // wall clock nonsense
        assertEquals(6 * HOUR - 2 * HOUR - 30 * MIN, TimeIntegrity.remainingMs(sixHours, now, checkpoint = cp))
    }

    private fun commitment(durationMs: Long) = Commitment(
        serverId = 1, mode = Mode.STUDY, unlockPolicy = UnlockPolicy.CARD_REQUIRED, controlMode = ControlMode.SELF,
        blockedPackages = setOf("com.instagram.android"), durationMs = durationMs, anchor = start, serverEndsAtMs = null,
    )
}

class RestrictionPolicyTest {
    private val anchor = TimeAnchor(1_000_000_000_000, 5 * HOUR, 3)
    private val launchers = setOf("com.google.android.apps.nexuslauncher", "com.sec.android.app.launcher")
    private fun commitment(mode: ControlMode = ControlMode.SELF) = Commitment(
        serverId = 1, mode = Mode.STUDY, unlockPolicy = UnlockPolicy.CARD_REQUIRED, controlMode = mode,
        blockedPackages = setOf("com.instagram.android"), durationMs = 6 * HOUR, anchor = anchor, serverEndsAtMs = null,
    )
    private fun at(minutes: Long) = anchor.copy(wallClockMs = anchor.wallClockMs + minutes * MIN, elapsedRealtimeMs = anchor.elapsedRealtimeMs + minutes * MIN)

    @Test fun noCommitmentAllowsEverything() {
        assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide("com.instagram.android", null, null, at(1), launchers))
    }

    @Test fun blocksSelectedAppDuringCommitment() {
        assertEquals(RestrictionPolicy.Decision.BlockApp("com.instagram.android"),
            RestrictionPolicy.decide("com.instagram.android", "MainActivity", commitment(), at(10), launchers))
    }

    @Test fun neverBlocksSavvyLauncherOrDialer() {
        val c = commitment().copy(blockedPackages = setOf("com.iroid.savvy.rd", "com.google.android.dialer", "com.sec.android.app.launcher"))
        for (pkg in c.blockedPackages) {
            assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide(pkg, null, c, at(10), launchers))
        }
    }

    @Test fun expiresByMonotonicTimeNotWallClock() {
        val movedClock = at(60).copy(wallClockMs = anchor.wallClockMs + 10 * HOUR)
        assertIs<RestrictionPolicy.Decision.BlockApp>(RestrictionPolicy.decide("com.instagram.android", null, commitment(), movedClock, launchers))
        assertEquals(RestrictionPolicy.Decision.CommitmentExpired, RestrictionPolicy.decide("com.instagram.android", null, commitment(), at(361), launchers))
    }

    @Test fun pauseAllowsUntilItEndsAndNotAcrossReboot() {
        val c = commitment().copy(pausedUntilElapsedMs = anchor.elapsedRealtimeMs + 30 * MIN, pauseBootCount = 3)
        assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide("com.instagram.android", null, c, at(20), launchers))
        assertIs<RestrictionPolicy.Decision.BlockApp>(RestrictionPolicy.decide("com.instagram.android", null, c, at(31), launchers))
        val rebooted = TimeAnchor(anchor.wallClockMs + 20 * MIN, 5 * MIN, 4)
        assertIs<RestrictionPolicy.Decision.BlockApp>(RestrictionPolicy.decide("com.instagram.android", null, c, rebooted, launchers))
    }

    @Test fun tamperScreensBlockedOnlyInParentMode() {
        val cls = "com.android.settings.DeviceAdminAdd"
        assertEquals(RestrictionPolicy.Decision.Allow,
            RestrictionPolicy.decide("com.android.settings", cls, commitment(ControlMode.SELF), at(5), launchers))
        assertIs<RestrictionPolicy.Decision.BlockTamperScreen>(
            RestrictionPolicy.decide("com.android.settings", cls, commitment(ControlMode.PARENT), at(5), launchers))
        assertIs<RestrictionPolicy.Decision.BlockTamperScreen>(
            RestrictionPolicy.decide("com.google.android.packageinstaller", "com.android.packageinstaller.UninstallerActivity", commitment(ControlMode.PARENT), at(5), launchers))
    }
}

/** Verifies payloads and grants produced by the Node backend (scripts/make-fixtures.js). */
class CrossImplementationTest {
    private val f: Map<String, String> = FlatJson.parse(javaClass.getResource("/fixtures.json")!!.readText())!!
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val cardKey = hex(f.getValue("card_public_key_raw_hex"))
    private val grantKey = hex(f.getValue("grant_public_key_raw_hex"))
    private val domain = "go.savvy.test"

    @Test fun parsesAllFormats() {
        assertIs<CardPayload.Signed>(CardPayload.parse(f.getValue("signed_card_url"), domain))
        assertIs<CardPayload.Static>(CardPayload.parse(f.getValue("static_card_url"), domain))
        val sun = CardPayload.parse(f.getValue("sun_card_url"), domain)
        assertIs<CardPayload.Sun>(sun)
        assertTrue(sun.provesPhysicalPresence)
        assertNull(CardPayload.parse("https://evil.test/c/8V1QFQWTY6VG", domain))
        assertNull(CardPayload.parse("http://go.savvy.test/c/8V1QFQWTY6VG", domain))
        assertNull(CardPayload.parse("not a url", domain))
    }

    @Test fun backendSignedCardVerifiesOfflineOnlyForBoundCard() {
        val p = CardPayload.parse(f.getValue("signed_card_url"), domain)!!
        assertTrue(CardPayload.verifyOffline(p, cardKey, f.getValue("bound_card_code")))
        assertFalse(CardPayload.verifyOffline(p, cardKey, "AAAAAAAAAAAA"))
        val forged = CardPayload.parse(f.getValue("forged_card_url"), domain)!!
        assertFalse(CardPayload.verifyOffline(forged, cardKey, "ZZZZZZZZZZZZ"))
        val sun = CardPayload.parse(f.getValue("sun_card_url"), domain)!!
        assertFalse(CardPayload.verifyOffline(sun, cardKey, "8V1QFQWTY6VG")) // SUN needs the backend
    }

    @Test fun backendGrantVerifies() {
        val r = GrantVerifier.verify(f.getValue("grant"), grantKey, deviceId = 7, commitmentId = 42, now = Instant.parse(f.getValue("grant_now_valid")))
        assertIs<GrantVerifier.Result.Valid>(r)
        assertEquals("release", r.grant.action)
        val p = GrantVerifier.verify(f.getValue("pause_grant"), grantKey, 7, 42, Instant.parse(f.getValue("grant_now_valid")))
        assertIs<GrantVerifier.Result.Valid>(p)
        assertEquals(Instant.parse("2026-09-25T09:15:00Z"), p.grant.pauseUntil)
    }

    @Test fun grantRejectsExpiryWrongDeviceWrongCommitmentAndTampering() {
        val g = f.getValue("grant")
        val now = Instant.parse(f.getValue("grant_now_valid"))
        assertEquals(GrantVerifier.Result.Invalid("expired"), GrantVerifier.verify(g, grantKey, 7, 42, Instant.parse(f.getValue("grant_now_expired"))))
        assertEquals(GrantVerifier.Result.Invalid("wrong_device"), GrantVerifier.verify(g, grantKey, 8, 42, now))
        assertEquals(GrantVerifier.Result.Invalid("wrong_commitment"), GrantVerifier.verify(g, grantKey, 7, 43, now))
        val tampered = g.replaceFirst(g.substring(10, 12), if (g.substring(10, 12) == "AA") "BB" else "AA")
        assertEquals(GrantVerifier.Result.Invalid("bad_signature"), GrantVerifier.verify(tampered, grantKey, 7, 42, now))
        assertEquals(GrantVerifier.Result.Invalid("bad_signature"), GrantVerifier.verify(g, cardKey, 7, 42, now))
    }
}
