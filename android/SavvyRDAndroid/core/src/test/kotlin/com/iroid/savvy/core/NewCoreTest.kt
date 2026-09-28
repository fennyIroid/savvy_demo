package com.iroid.savvy.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OfflinePolicyTest {
    private val f = FlatJson.parse(javaClass.getResource("/fixtures.json")!!.readText())!!
    private val key = f.getValue("card_public_key_raw_hex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun c(policy: UnlockPolicy, mode: Mode = Mode.STUDY, task: String? = null) = Commitment(
        null, mode, policy, ControlMode.SELF, setOf("x"), 3_600_000, TimeAnchor(0, 0, 1), null, taskRef = task)

    @Test fun emergencyLimitIncludingClockMovedBack() {
        val now = 10 * OfflinePolicy.WEEK_MS
        assertTrue(OfflinePolicy.emergencyAllowed(emptyList(), now))
        assertFalse(OfflinePolicy.emergencyAllowed(listOf(now - 86_400_000), now))
        assertTrue(OfflinePolicy.emergencyAllowed(listOf(now - 8 * 86_400_000L), now))
        assertFalse(OfflinePolicy.emergencyAllowed(listOf(now + 86_400_000), now)) // future-dated still counts
    }

    @Test fun offlineCardDecisionsMatchIos() {
        val signed = f.getValue("signed_card_url")
        fun d(raw: String, cm: Commitment?, allow: Boolean = true, bound: String? = "8V1QFQWTY6VG") =
            OfflinePolicy.cardUnlock(raw, "go.savvy.test", cm, allow, key, bound)
        assertEquals(OfflinePolicy.CardDecision.Release, d(signed, c(UnlockPolicy.CARD_REQUIRED)))
        assertEquals(OfflinePolicy.CardDecision.Reject("locked_commitment_no_early_unlock"), d(signed, c(UnlockPolicy.LOCKED)))
        assertEquals(OfflinePolicy.CardDecision.Reject("no_active_commitment"), d(signed, null))
        assertEquals(OfflinePolicy.CardDecision.Reject("offline_unlock_disabled"), d(signed, c(UnlockPolicy.CARD_REQUIRED), allow = false))
        assertEquals(OfflinePolicy.CardDecision.Reject("offline_card_check_failed"), d(signed, c(UnlockPolicy.CARD_REQUIRED), bound = "AAAAAAAAAAAA"))
        assertEquals(OfflinePolicy.CardDecision.Reject("sun_card_needs_internet"), d(f.getValue("sun_card_url"), c(UnlockPolicy.CARD_REQUIRED)))
        assertEquals(OfflinePolicy.CardDecision.Reject("not_a_savvy_card"), d("https://example.com", c(UnlockPolicy.CARD_REQUIRED)))
    }

    @Test fun offlineTaskCompletion() {
        assertEquals(OfflinePolicy.CardDecision.Release, OfflinePolicy.taskCompletion(c(UnlockPolicy.FREE, Mode.TASK, "t1"), "t1"))
        assertEquals(OfflinePolicy.CardDecision.Reject("card_or_server_required"), OfflinePolicy.taskCompletion(c(UnlockPolicy.CARD_REQUIRED, Mode.TASK, "t1"), "t1"))
        assertEquals(OfflinePolicy.CardDecision.Reject("not_this_task"), OfflinePolicy.taskCompletion(c(UnlockPolicy.FREE, Mode.TASK, "t1"), "t2"))
    }
}

class ParentAlwaysOnTest {
    private val anchor = TimeAnchor(1_000_000, 1_000, 1)

    @Test fun alwaysOnBlocksWithoutCommitmentButNeverSavvyOrLauncher() {
        val always = setOf("com.instagram.android", "com.iroid.savvy.rd", "launcher")
        assertIs<RestrictionPolicy.Decision.BlockApp>(RestrictionPolicy.decide("com.instagram.android", null, null, anchor, setOf("launcher"), alwaysBlocked = always))
        assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide("com.iroid.savvy.rd", null, null, anchor, setOf("launcher"), alwaysBlocked = always))
        assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide("launcher", null, null, anchor, setOf("launcher"), alwaysBlocked = always))
    }

    @Test fun tamperGuardHoldsUnderAlwaysOnRuleWithoutCommitment() {
        val now = TimeAnchor(0, 0, 1)
        val ig = setOf("com.instagram.android")
        val uninstall = RestrictionPolicy.decide("com.google.android.packageinstaller",
            "com.android.packageinstaller.UninstallerActivity", null, now, emptySet(), alwaysBlocked = ig, parentControlled = true)
        assertTrue(uninstall is RestrictionPolicy.Decision.BlockTamperScreen)
        // Self device, or a child with no parent rule: Settings stay open.
        assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide("com.google.android.packageinstaller",
            "com.android.packageinstaller.UninstallerActivity", null, now, emptySet(), alwaysBlocked = ig))
        assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide("com.android.settings",
            "com.android.settings.Settings\$AccessibilitySettingsActivity", null, now, emptySet(), parentControlled = true))
    }

    @Test fun pauseDoesNotLiftParentAlwaysOn() {
        val c = Commitment(1, Mode.STUDY, UnlockPolicy.CARD_REQUIRED, ControlMode.PARENT, setOf("com.game"), 3_600_000, anchor, null,
            pausedUntilElapsedMs = anchor.elapsedRealtimeMs + 600_000, pauseBootCount = 1)
        val now = anchor.copy(elapsedRealtimeMs = anchor.elapsedRealtimeMs + 60_000, wallClockMs = anchor.wallClockMs + 60_000)
        assertEquals(RestrictionPolicy.Decision.Allow, RestrictionPolicy.decide("com.game", null, c, now, emptySet()))
        assertIs<RestrictionPolicy.Decision.BlockApp>(RestrictionPolicy.decide("com.instagram.android", null, c, now, emptySet(), alwaysBlocked = setOf("com.instagram.android")))
    }
}

class UsageAggregatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 9, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun ev(ms: Long, pkg: String?, k: UsageAggregator.Kind) = UsageAggregator.Event(ms, pkg, k)

    @Test fun sumsForegroundTimePerAppAndSplitsAtMidnight() {
        val events = listOf(
            ev(t(24, 23, 50), "insta", UsageAggregator.Kind.RESUMED),
            ev(t(25, 0, 10), "insta", UsageAggregator.Kind.PAUSED),       // 20 min across midnight
            ev(t(25, 9, 0), "yt", UsageAggregator.Kind.RESUMED),
            ev(t(25, 9, 30), "insta", UsageAggregator.Kind.RESUMED),      // switch without pause event
            ev(t(25, 9, 45), null, UsageAggregator.Kind.SCREEN_OFF),
            ev(t(25, 10, 0), "insta", UsageAggregator.Kind.RESUMED),      // still open at end
        )
        val r = UsageAggregator.dailyTotals(events, endMs = t(25, 10, 5), zone = zone)
        assertEquals(mapOf("insta" to 600L), r[LocalDate.of(2026, 9, 24)])
        assertEquals(mapOf("insta" to 600L + 900 + 300, "yt" to 1800L), r[LocalDate.of(2026, 9, 25)])
    }

    @Test fun pauseOfAnotherAppDoesNotCloseCurrent() {
        val events = listOf(
            ev(t(25, 8, 0), "a", UsageAggregator.Kind.RESUMED),
            ev(t(25, 8, 1), "b", UsageAggregator.Kind.PAUSED),
        )
        assertEquals(mapOf("a" to 600L), UsageAggregator.dailyTotals(events, t(25, 8, 10), zone)[LocalDate.of(2026, 9, 25)])
    }
}
