package com.iroid.savvy.rd.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.iroid.savvy.rd.data.BackendClient
import com.iroid.savvy.rd.data.SavvyLog

/**
 * NTAG 424 DNA live proof of presence (same protocol as iOS LiveProofRelay and
 * backend src/crypto/ntag424Auth.js). Relays AuthenticateEV2First between the
 * card and the backend while the card is held to the phone. Blocking: call from
 * the reader-mode callback thread, never from the main thread.
 */
object LiveProofRelay {
    fun run(tag: Tag, cardCode: String, backend: BackendClient): String {
        val iso = IsoDep.get(tag) ?: error("not_iso_dep")
        iso.use {
            it.connect()
            it.timeout = 2000
            val start = backend.liveStart(cardCode)
            val apdus = start.getJSONArray("apdus")
            var last = ByteArray(0)
            for (i in 0 until apdus.length()) last = it.transceive(hex(apdus.getString(i)))
            val step = backend.liveStep(start.getString("session_id"), toHex(last))
            val final = it.transceive(hex(step.getString("apdu")))
            val token = backend.liveFinish(start.getString("session_id"), toHex(final)).getString("presence_token")
            SavvyLog.event("NFC", "live proof ok")
            return token
        }
    }

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun toHex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }
}
