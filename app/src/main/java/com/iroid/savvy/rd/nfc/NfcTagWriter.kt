package com.iroid.savvy.rd.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

/**
 * Debug tool: writes a Savvy card URL to a blank generic NDEF tag (NTAG213/215/216), so the
 * real-tag tests (A-NFC-1 block screen hold, A-NFC-2/3 background tap, A-NFC-6 read time)
 * can run before the vendor's cards arrive (brief section 57: "Generic NDEF card initially").
 *
 * Record 1: the https card URL (iOS reads only this). Record 2, if it fits: an Android
 * Application Record, so Android always routes the tap to Savvy, or to Play if not installed.
 * The tag is never made read-only (irreversible), so test tags can be rewritten.
 */
object NfcTagWriter {
    fun write(tag: Tag, url: String, packageName: String): String {
        val uri = NdefRecord.createUri(url)
        val withAar = NdefMessage(arrayOf(uri, NdefRecord.createApplicationRecord(packageName)))
        val uriOnly = NdefMessage(arrayOf(uri))
        val uid = tag.id?.joinToString("") { "%02X".format(it) } ?: "?"

        Ndef.get(tag)?.let { ndef ->
            ndef.use {
                it.connect()
                if (!it.isWritable) return "error: tag $uid is read-only"
                val msg = if (withAar.byteArrayLength <= it.maxSize) withAar else uriOnly
                if (msg.byteArrayLength > it.maxSize) return "error: tag $uid too small (${it.maxSize} bytes, need ${msg.byteArrayLength})"
                it.writeNdefMessage(msg)
                return "written ${msg.byteArrayLength}/${it.maxSize} bytes (${if (msg === withAar) "url+aar" else "url only"}) " +
                    "type=${it.type.substringAfterLast('.')} uid=$uid"
            }
        }
        NdefFormatable.get(tag)?.let { f ->
            f.use {
                it.connect()
                it.format(withAar)
                return "formatted and written ${withAar.byteArrayLength} bytes (url+aar) uid=$uid"
            }
        }
        return "error: tag $uid does not support NDEF (techs: ${tag.techList.joinToString { it.substringAfterLast('.') }})"
    }
}
