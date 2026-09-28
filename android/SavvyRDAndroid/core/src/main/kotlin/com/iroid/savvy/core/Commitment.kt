package com.iroid.savvy.core

/**
 * Platform-independent commitment state. Android stores it in SharedPreferences
 * as JSON. Times are captured in three clocks so a changed wall clock can be
 * detected without the network:
 *  - wallClockMs:     System.currentTimeMillis() (user can change it)
 *  - elapsedRealtimeMs: SystemClock.elapsedRealtime() (monotonic, counts deep sleep, resets on boot)
 *  - bootCount:       Settings.Global.BOOT_COUNT (increments every boot)
 */
data class TimeAnchor(val wallClockMs: Long, val elapsedRealtimeMs: Long, val bootCount: Int)

enum class Mode { STUDY, WORK, SLEEP, CUSTOM, TASK }

/** Mirrors backend unlock_policy. */
enum class UnlockPolicy(val wire: String) {
    CARD_REQUIRED("card_required"), FREE("free"), LOCKED("locked");

    companion object {
        fun fromWire(v: String) = entries.first { it.wire == v }
    }
}

/** SELF = user restricts own device. PARENT = parent-authorised child device. */
enum class ControlMode { SELF, PARENT }

data class Commitment(
    val serverId: Long?,
    val mode: Mode,
    val unlockPolicy: UnlockPolicy,
    val controlMode: ControlMode,
    val blockedPackages: Set<String>,
    val durationMs: Long,
    val anchor: TimeAnchor,
    val serverEndsAtMs: Long?,
    val taskRef: String? = null,
    /** Temporary unlock (card pause or emergency pause), in elapsedRealtime of the same boot. */
    val pausedUntilElapsedMs: Long? = null,
    val pauseBootCount: Int? = null,
    /** App-generated id used to reconcile offline events with the server. */
    val localId: String = java.util.UUID.randomUUID().toString(),
)
