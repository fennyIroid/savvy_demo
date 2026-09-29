package com.iroid.savvy.core

import kotlin.math.abs

/**
 * Android equivalent of the iOS TimeIntegrity. elapsedRealtime is monotonic and
 * unaffected by the user changing date/time; BOOT_COUNT tells us whether we are
 * still in the same boot.
 *
 * After a reboot the monotonic clock restarts, so elapsed time is rebuilt from:
 *  - a trusted server clock read in this boot (online), else
 *  - the last [Checkpoint] plus the gap to this boot plus time since boot (offline).
 * The gap is measured on the raw wall clock between the checkpoint and the boot. A clock
 * change before the reboot triggers a new checkpoint (ACTION_TIME_CHANGED), and the boot
 * wall clock is recorded at LOCKED_BOOT_COMPLETED, before the user can unlock and change it.
 * So "reboot, go offline, move the clock forward" no longer ends a commitment early.
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
     * Commitment time that has provably passed, without the network.
     * @param bootWallMs raw wall clock at the start of the current boot (wall - elapsedRealtime,
     *   recorded at LOCKED_BOOT_COMPLETED). When unknown it is read now, which is the old,
     *   manipulable behaviour.
     */
    fun elapsedMs(c: Commitment, now: TimeAnchor, checkpoint: Checkpoint? = null, bootWallMs: Long? = null): Long {
        val base = checkpoint ?: Checkpoint(c.anchor.bootCount, c.anchor.elapsedRealtimeMs, c.anchor.wallClockMs, 0)
        if (base.bootCount == now.bootCount && now.elapsedRealtimeMs >= base.elapsedRealtimeMs) {
            return base.creditedMs + (now.elapsedRealtimeMs - base.elapsedRealtimeMs)
        }
        val bootWall = bootWallMs ?: (now.wallClockMs - now.elapsedRealtimeMs)
        // Rest of the old boot plus power-off time. A clock moved back gives a negative gap: count none.
        val gap = (bootWall - base.wallClockMs).coerceAtLeast(0)
        return base.creditedMs + gap + now.elapsedRealtimeMs
    }

    /** New checkpoint for [c] at [now]. Store it and pass it to the next [remainingMs] call. */
    fun checkpoint(c: Commitment, now: TimeAnchor, previous: Checkpoint? = null, bootWallMs: Long? = null) =
        Checkpoint(now.bootCount, now.elapsedRealtimeMs, now.wallClockMs, elapsedMs(c, now, previous, bootWallMs))

    /**
     * Remaining commitment time.
     *  - same boot: monotonic elapsed time (clock changes ignored)
     *  - after reboot, online: [serverNowMs] must be a server clock read in THIS boot and
     *    advanced with elapsedRealtime, never "wall clock + old offset" (that is manipulable)
     *  - after reboot, offline: checkpoint + boot gap + time since boot (see class comment)
     */
    fun remainingMs(
        c: Commitment,
        now: TimeAnchor,
        serverNowMs: Long? = null,
        checkpoint: Checkpoint? = null,
        bootWallMs: Long? = null,
    ): Long =
        when (val v = check(c.anchor, now)) {
            is Verdict.Trusted -> c.durationMs - v.elapsedMs
            is Verdict.ClockChanged -> c.durationMs - v.trueElapsedMs
            is Verdict.RebootedOrUnknown -> {
                if (serverNowMs != null) {
                    (c.serverEndsAtMs ?: (c.anchor.wallClockMs + c.durationMs)) - serverNowMs
                } else {
                    c.durationMs - elapsedMs(c, now, checkpoint, bootWallMs)
                }
            }
        }.coerceAtLeast(0)
}
