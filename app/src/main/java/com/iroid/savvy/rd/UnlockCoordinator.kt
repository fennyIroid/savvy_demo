package com.iroid.savvy.rd

import android.content.Context
import com.iroid.savvy.core.CardPayload
import com.iroid.savvy.core.GrantVerifier
import com.iroid.savvy.core.OfflineEvent
import com.iroid.savvy.core.OfflinePolicy
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.data.BackendClient
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.service.UsageMonitorService
import java.io.IOException
import java.time.Instant

/**
 * Same decision order as iOS UnlockCoordinator:
 *  1. parse (reject non-Savvy payloads without network)
 *  2. online: backend verifies card + policy, returns signed grant; verify grant; release
 *  3. offline: signed cards only, bound card only, if allowed; queue for sync
 * Call from a background thread.
 */
class UnlockCoordinator(private val context: Context) {
    sealed class Outcome {
        data class Released(val offline: Boolean) : Outcome()
        data class Paused(val until: Instant) : Outcome()
        data class Pending(val availableAt: String) : Outcome()
        data class Rejected(val reason: String) : Outcome()
    }

    var allowOfflineSignedCardUnlock = true

    private val repo get() = context.savvy.repo
    private val grantPublicKeyRaw get() = hex(repo.grantKeyHex)
    private val cardPublicKeyRaw get() = hex(repo.cardKeyHex)

    private fun hex(s: String?) = s?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray() ?: ByteArray(0)

    fun handleCard(raw: String, source: String): Outcome {
        val payload = CardPayload.parse(raw, BuildConfig.CARD_DOMAIN) ?: return Outcome.Rejected("not_a_savvy_card")
        val c = repo.commitment ?: return Outcome.Rejected("no_active_commitment")
        if (c.unlockPolicy == UnlockPolicy.LOCKED) return Outcome.Rejected("locked_commitment_no_early_unlock")

        if (c.serverId != null) {
            try {
                val r = context.savvy.backend.release(c.serverId!!, "card", raw, source)
                return applyGrant(r.optString("grant"), c.serverId!!)
            } catch (e: BackendClient.ApiError) {
                return Outcome.Rejected(e.code)
            } catch (e: IOException) {
                SavvyLog.event("Unlock", "network error, offline path: $e")
            }
        }
        val decision = OfflinePolicy.cardUnlock(raw, BuildConfig.CARD_DOMAIN, c, allowOfflineSignedCardUnlock,
            cardPublicKeyRaw, repo.boundCardCode)
        if (decision is OfflinePolicy.CardDecision.Reject) return Outcome.Rejected(decision.reason)
        repo.enqueue(OfflineEvent.CardRelease(c.localId, c.serverId, raw, source, System.currentTimeMillis()))
        releaseLocally("card_offline_$source")
        return Outcome.Released(offline = true)
    }

    /** NTAG 424 DNA live proof: token proves the real card was at the phone just now. Online only. */
    fun handleLiveProof(url: String, presenceToken: String): Outcome {
        val c = repo.commitment ?: return Outcome.Rejected("no_active_commitment")
        val id = c.serverId ?: return Outcome.Rejected("live_proof_needs_synced_commitment")
        return try {
            applyGrant(context.savvy.backend.release(id, "card", url, "nfc_live", presenceToken).optString("grant"), id)
        } catch (e: BackendClient.ApiError) {
            Outcome.Rejected(e.code)
        } catch (e: IOException) {
            Outcome.Rejected("live_proof_needs_internet")
        }
    }

    fun applyGrant(grant: String?, commitmentId: Long): Outcome {
        val r = GrantVerifier.verify(grant ?: "", grantPublicKeyRaw, repo.deviceId, commitmentId, Instant.now())
        if (r is GrantVerifier.Result.Invalid) return Outcome.Rejected("grant_${r.reason}")
        val g = (r as GrantVerifier.Result.Valid).grant
        if (g.action == "pause" && g.pauseUntil != null) {
            val now = repo.now()
            val pauseMs = g.pauseUntil!!.toEpochMilli() - now.wallClockMs
            repo.commitment = repo.commitment?.copy(pausedUntilElapsedMs = now.elapsedRealtimeMs + pauseMs, pauseBootCount = now.bootCount)
            repo.emergencyPending = null
            com.iroid.savvy.rd.admin.DeviceOwnerController.reconcile(context)
            return Outcome.Paused(g.pauseUntil!!)
        }
        releaseLocally(g.reason)
        return Outcome.Released(offline = false)
    }

    /** Every way a restriction ends (card, grant, emergency, expiry, offline) goes through here. */
    fun releaseLocally(reason: String) {
        repo.commitment = null
        repo.emergencyPending = null
        repo.reconcileTasks()
        if (!repo.hasAlwaysOnBlocks) UsageMonitorService.stop(context)
        com.iroid.savvy.rd.admin.DeviceOwnerController.reconcile(context)
        SavvyLog.event("Unlock", "released locally reason=$reason")
    }
}
