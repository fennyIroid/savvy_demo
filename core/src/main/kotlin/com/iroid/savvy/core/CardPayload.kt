package com.iroid.savvy.core

import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Mirrors backend/src/crypto/cardToken.js and iOS Shared/CardPayload.swift. */
sealed class CardPayload {
    abstract val cardCode: String

    data class Static(override val cardCode: String) : CardPayload()
    data class Signed(override val cardCode: String, val signature: String) : CardPayload()
    data class Sun(override val cardCode: String, val picc: String, val mac: String) : CardPayload()

    /** Only NTAG 424 DNA SUN proves the physical chip was just tapped. */
    val provesPhysicalPresence: Boolean get() = this is Sun

    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private fun validCode(s: String) = s.length == 12 && s.all { it in ALPHABET }

        fun parse(raw: String, domain: String): CardPayload? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host != domain) return null
            val parts = (uri.rawPath ?: "").split('/').filter { it.isNotEmpty() }
            if (parts.firstOrNull() != "c") return null

            if (parts.size == 3 && parts[1] == "s" && validCode(parts[2])) {
                val q = (uri.rawQuery ?: "").split('&').mapNotNull {
                    val kv = it.split('=', limit = 2); if (kv.size == 2) kv[0] to kv[1] else null
                }.toMap()
                val e = q["e"] ?: return null
                val c = q["c"] ?: return null
                if (!e.matches(Regex("[0-9A-Fa-f]{32}")) || !c.matches(Regex("[0-9A-Fa-f]{16}"))) return null
                return Sun(parts[2], e, c)
            }
            if (parts.size == 2) {
                val seg = parts[1].split('.')
                if (seg.size == 3 && seg[0] == "1" && validCode(seg[1])) return Signed(seg[1], seg[2])
                if (validCode(parts[1])) return Static(parts[1])
            }
            return null
        }

        /**
         * Offline check: genuine Savvy signed card AND the card bound to this account.
         * Proves identity only; a copied URL passes too.
         * Ed25519 is in the JDK (15+) and in Android's platform provider from API 33;
         * for minSdk below 33 the app module plugs in Tink or BouncyCastle (see ANDROID_FEASIBILITY.md).
         */
        fun verifyOffline(p: CardPayload, cardPublicKeyRaw: ByteArray, boundCardCode: String?): Boolean {
            if (p !is Signed || p.cardCode != boundCardCode) return false
            val sig = runCatching { Base64.getUrlDecoder().decode(p.signature) }.getOrNull() ?: return false
            return Ed25519.verify(cardPublicKeyRaw, "savvy-card:v1:${p.cardCode}".toByteArray(), sig)
        }
    }
}

object Ed25519 {
    private val SPKI_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    fun verify(rawPublicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean = runCatching {
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(SPKI_PREFIX + rawPublicKey))
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(message)
            verify(signature)
        }
    }.getOrDefault(false)
}
