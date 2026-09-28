package com.iroid.savvy.core

import kotlin.math.abs

/**
 * Android equivalent of the iOS TimeIntegrity. elapsedRealtime is monotonic and
 * unaffected by the user changing date/time; BOOT_COUNT tells us whether we are
 * still in the same boot. After a reboot we cannot measure elapsed time locally
 * and must use server time (online) or a conservative fallback (offline).
 */
object TimeIntegrity {
    const val TOLERANCE_MS = 120_000L

    sealed class Verdict {
        data class Trusted(val elapsedMs: Long) : Verdict()
        data class ClockChanged(val trueElapsedMs: Long, val wallElapsedMs: Long) : Verdict()
        data class RebootedOrUnknown(val wallElapsedMs: Long) : Verdict()
    }

    fun check(anchor: TimeAnchor, now: TimeAnchor): Verdict {
        val wallElapsed = now.wallClockMs - anchor.wallClockMs
        if (now.bootCount != anchor.bootCount || now.elapsedRealtimeMs < anchor.elapsedRealtimeMs) {
            return Verdict.RebootedOrUnknown(wallElapsed)
        }
        val trueElapsed = now.elapsedRealtimeMs - anchor.elapsedRealtimeMs
        return if (abs(wallElapsed - trueElapsed) > TOLERANCE_MS) {
            Verdict.ClockChanged(trueElapsed, wallElapsed)
        } else {
            Verdict.Trusted(trueElapsed)
        }
    }

    /**
     * Remaining commitment time.
     *  - same boot: monotonic elapsed time (clock changes ignored)
     *  - after reboot: server end time if known (serverNowMs from last sync), else wall clock
     * Offline + reboot + changed clock is the one gap; see ANDROID_BYPASS_MATRIX.md.
     */
    fun remainingMs(c: Commitment, now: TimeAnchor, serverNowMs: Long? = null): Long =
        when (val v = check(c.anchor, now)) {
            is Verdict.Trusted -> c.durationMs - v.elapsedMs
            is Verdict.ClockChanged -> c.durationMs - v.trueElapsedMs
            is Verdict.RebootedOrUnknown -> {
                val end = c.serverEndsAtMs ?: (c.anchor.wallClockMs + c.durationMs)
                end - (serverNowMs ?: now.wallClockMs)
            }
        }.coerceAtLeast(0)
}
