package com.iroid.savvy.core

import java.time.Instant
import java.util.Base64

/**
 * Verifies backend unlock grants (backend/src/crypto/grants.js):
 * base64url(json) + "." + base64url(Ed25519 signature over the first part).
 * JSON is parsed with a tiny flat-object reader so core has no dependencies.
 */
object GrantVerifier {
    data class Grant(
        val commitmentId: Long,
        val deviceId: Long,
        val action: String,
        val reason: String,
        val expiresAt: Instant,
        val pauseUntil: Instant?,
    )

    sealed class Result {
        data class Valid(val grant: Grant) : Result()
        data class Invalid(val reason: String) : Result()
    }

    fun verify(grant: String, publicKeyRaw: ByteArray, deviceId: Long, commitmentId: Long, now: Instant): Result {
        val parts = grant.split('.')
        if (parts.size != 2) return Result.Invalid("malformed")
        val sig = runCatching { Base64.getUrlDecoder().decode(parts[1]) }.getOrNull() ?: return Result.Invalid("malformed")
        if (!Ed25519.verify(publicKeyRaw, parts[0].toByteArray(Charsets.US_ASCII), sig)) return Result.Invalid("bad_signature")
        val json = runCatching { String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8) }.getOrNull()
            ?: return Result.Invalid("malformed")
        val f = FlatJson.parse(json) ?: return Result.Invalid("malformed")
        val g = runCatching {
            Grant(
                commitmentId = f.getValue("commitment_id").toLong(),
                deviceId = f.getValue("device_id").toLong(),
                action = f.getValue("action"),
                reason = f.getValue("reason"),
                expiresAt = Instant.parse(f.getValue("expires_at")),
                pauseUntil = f["pause_until"]?.let(Instant::parse),
            )
        }.getOrNull() ?: return Result.Invalid("malformed")
        if (g.expiresAt.isBefore(now)) return Result.Invalid("expired")
        if (g.deviceId != deviceId) return Result.Invalid("wrong_device")
        if (g.commitmentId != commitmentId) return Result.Invalid("wrong_commitment")
        return Result.Valid(g)
    }
}

/** Reads a flat JSON object of string / number / boolean values. Enough for grants. */
internal object FlatJson {
    private val pair = Regex("\"([^\"]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|-?[0-9.]+|true|false|null)")

    fun parse(json: String): Map<String, String>? {
        val t = json.trim()
        if (!t.startsWith("{") || !t.endsWith("}")) return null
        return pair.findAll(t).associate { m ->
            val raw = m.groupValues[2]
            m.groupValues[1] to (if (raw.startsWith("\"")) m.groupValues[3] else raw)
        }.filterValues { it != "null" }
    }
}
