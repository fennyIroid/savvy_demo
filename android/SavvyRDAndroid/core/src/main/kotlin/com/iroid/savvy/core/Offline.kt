package com.iroid.savvy.core

import java.util.UUID

/** Actions taken without network, replayed to POST /v1/sync (backend syncService.js). */
sealed class OfflineEvent {
    abstract val eventId: String
    abstract val localId: String
    abstract val atMs: Long

    data class CommitmentStarted(override val localId: String, val mode: String, val minutes: Int, val policy: String,
                                 val taskRef: String?, override val atMs: Long,
                                 override val eventId: String = UUID.randomUUID().toString()) : OfflineEvent()
    data class CardRelease(override val localId: String, val serverId: Long?, val payload: String, val source: String,
                           override val atMs: Long, override val eventId: String = UUID.randomUUID().toString()) : OfflineEvent()
    data class EmergencyExit(override val localId: String, val serverId: Long?, override val atMs: Long,
                             override val eventId: String = UUID.randomUUID().toString()) : OfflineEvent()
    data class TaskCompleted(override val localId: String, val serverId: Long?, override val atMs: Long,
                             override val eventId: String = UUID.randomUUID().toString()) : OfflineEvent()
}

/** Same rules as iOS SavvyCore OfflinePolicy (kept identical on purpose). */
object OfflinePolicy {
    const val WEEK_MS = 7 * 86_400_000L

    /**
     * At most [maxPerWindow] offline emergency exits per rolling window. Entries dated
     * in the future (clock moved back after using one) still count.
     */
    fun emergencyAllowed(historyMs: List<Long>, nowMs: Long, maxPerWindow: Int = 1, windowMs: Long = WEEK_MS): Boolean =
        historyMs.count { nowMs - it < windowMs } < maxPerWindow

    sealed class CardDecision {
        object Release : CardDecision()
        data class Reject(val reason: String) : CardDecision()
    }

    fun cardUnlock(raw: String, domain: String, commitment: Commitment?, allowOffline: Boolean,
                   cardPublicKeyRaw: ByteArray, boundCardCode: String?): CardDecision {
        val payload = CardPayload.parse(raw, domain) ?: return CardDecision.Reject("not_a_savvy_card")
        val c = commitment ?: return CardDecision.Reject("no_active_commitment")
        if (c.unlockPolicy == UnlockPolicy.LOCKED) return CardDecision.Reject("locked_commitment_no_early_unlock")
        if (!allowOffline) return CardDecision.Reject("offline_unlock_disabled")
        if (payload.provesPhysicalPresence) return CardDecision.Reject("sun_card_needs_internet")
        if (!CardPayload.verifyOffline(payload, cardPublicKeyRaw, boundCardCode)) return CardDecision.Reject("offline_card_check_failed")
        return CardDecision.Release
    }

    fun taskCompletion(c: Commitment, taskId: String): CardDecision = when {
        c.mode != Mode.TASK || c.taskRef != taskId -> CardDecision.Reject("not_this_task")
        c.unlockPolicy == UnlockPolicy.FREE -> CardDecision.Release
        else -> CardDecision.Reject("card_or_server_required")
    }
}
