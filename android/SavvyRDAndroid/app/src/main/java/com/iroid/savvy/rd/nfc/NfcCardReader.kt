package com.iroid.savvy.rd.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.SystemClock
import com.iroid.savvy.core.CardPayload
import com.iroid.savvy.rd.BuildConfig
import com.iroid.savvy.rd.data.BackendClient
import com.iroid.savvy.rd.data.SavvyLog

/**
 * Foreground card reading with reader mode, used while the Savvy block screen is
 * visible: the user just holds the card, no system chooser, no extra tap.
 * Works for NTAG21x (Type 2) and NTAG 424 DNA (Type 4) through the Ndef tech.
 * Background taps are handled by the NDEF_DISCOVERED / App Link intent filter.
 */
class NfcCardReader(
    private val activity: Activity,
    /** When set, NTAG 424 DNA cards also run the live AES proof; the token is passed to onUrl. */
    private val liveBackend: BackendClient? = null,
    private val onUrl: (url: String, uidHex: String, readMs: Long, presenceToken: String?) -> Unit,
) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val state: String
        get() = when {
            adapter == null -> "no_nfc_hardware"
            !adapter.isEnabled -> "nfc_disabled"
            else -> "ready"
        }

    fun enable() {
        val a = adapter ?: return
        val started = SystemClock.elapsedRealtime()
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        a.enableReaderMode(activity, { tag -> onTag(tag, started) }, flags, Bundle())
        SavvyLog.event("NFC", "reader mode on")
    }

    fun disable() { adapter?.disableReaderMode(activity) }

    private fun onTag(tag: Tag, started: Long) {
        val uid = tag.id?.joinToString("") { "%02X".format(it) } ?: ""
        val url = runCatching {
            Ndef.get(tag)?.use { ndef ->
                ndef.connect()
                firstUri(ndef.ndefMessage ?: ndef.cachedNdefMessage)
            }
        }.getOrNull()
        // NTAG 424 DNA: Ndef is closed (use {}) before IsoDep connects for the live proof.
        val sun = url?.let { CardPayload.parse(it, BuildConfig.CARD_DOMAIN) } as? CardPayload.Sun
        val backend = liveBackend
        val token = if (backend != null && sun != null) {
            runCatching { LiveProofRelay.run(tag, sun.cardCode, backend) }
                .onFailure { SavvyLog.event("NFC", "live proof failed $it") }.getOrNull()
        } else null
        val ms = SystemClock.elapsedRealtime() - started
        SavvyLog.event("NFC", "tag uid=$uid techs=${tag.techList.joinToString { it.substringAfterLast('.') }} url=${url ?: "-"} live=${token != null} ${ms}ms")
        activity.runOnUiThread { onUrl(url ?: "", uid, ms, token) }
    }

    companion object {
        fun firstUri(message: NdefMessage?): String? = message?.records?.firstNotNullOfOrNull { it.toUri()?.toString() }
    }
}
