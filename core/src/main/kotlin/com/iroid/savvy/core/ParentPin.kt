package com.iroid.savvy.core

import java.security.MessageDigest

/**
 * Parent PIN that a child phone needs to leave parent mode (brief section 25: "child attempts
 * to change Savvy settings"). The parent phone sends only the hash inside the rules JSON
 * (`leave_pin_sha256`); the child compares locally, so it also works offline.
 *
 * POC strength: a short PIN hash can be brute-forced by someone who can read the app's
 * private storage (root / adb backup of a debuggable build). Production: verify on the backend
 * with rate limiting, or require the parent's card.
 */
object ParentPin {
    fun valid(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() }

    fun hash(childDeviceId: Long, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("savvy-leave:v1:$childDeviceId:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun matches(childDeviceId: Long, pin: String, expectedHash: String?): Boolean =
        expectedHash != null && valid(pin) && MessageDigest.isEqual(hash(childDeviceId, pin).toByteArray(), expectedHash.toByteArray())
}
