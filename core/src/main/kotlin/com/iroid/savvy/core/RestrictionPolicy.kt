package com.iroid.savvy.core

/**
 * The single decision the Android blocking engine makes on every foreground
 * change (AccessibilityService window event or UsageStats ACTIVITY_RESUMED):
 * allow, block the app, or (parent mode only) block a tamper screen.
 *
 * Kept free of Android classes so it is unit-tested on the JVM.
 */
object RestrictionPolicy {

    sealed class Decision {
        object Allow : Decision()
        data class BlockApp(val packageName: String) : Decision()
        /** Parent mode: child opened a screen that would disable or uninstall Savvy. */
        data class BlockTamperScreen(val packageName: String, val className: String?) : Decision()
        object CommitmentExpired : Decision()
    }

    /** Never blocked, whatever the user selects (dialer so emergency calls always work). */
    val ALWAYS_ALLOWED = setOf(
        "com.iroid.savvy.rd",
        "com.android.systemui",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
    )

    /**
     * Screens that disable or remove Savvy. Blocking them via Accessibility is only
     * allowed by Google Play when "authorized by a parent or guardian through a
     * parental control app", so this is used in PARENT mode only.
     * Class names differ by OEM: the list must be extended from device tests (A-OEM-*).
     */
    val TAMPER_SCREENS = listOf(
        "com.android.settings" to "DeviceAdminAdd",
        "com.android.settings" to "DeviceAdminSettings",
        "com.android.packageinstaller" to "UninstallerActivity",
        "com.google.android.packageinstaller" to "UninstallerActivity",
        "com.android.settings" to "AccessibilitySettings",
        "com.android.settings" to "ToggleAccessibilityServicePreferenceFragment",
        "com.android.settings" to "InstalledAppDetails",
        "com.android.settings" to "AppInfoDashboardFragment",
        // Pixel 4 / Android 13 (28 Sep 2026): Usage access list, where the UsageStats fallback can be revoked.
        "com.android.settings" to "UsageAccessSettingsActivity",
    )

    private fun isTamperScreen(pkg: String, cls: String?) =
        cls != null && TAMPER_SCREENS.any { (p, c) -> pkg == p && cls.contains(c) }

    fun decide(
        foregroundPackage: String,
        foregroundClass: String?,
        commitment: Commitment?,
        now: TimeAnchor,
        launcherPackages: Set<String>,
        serverNowMs: Long? = null,
        /** Parent "always-on" rule (child device): blocked even without a focus commitment. */
        alwaysBlocked: Set<String> = emptySet(),
        /**
         * The user's own "always blocked" apps: blocked with or without a focus session, like
         * [alwaysBlocked], but never turns on the parent tamper guard.
         */
        selfBlocked: Set<String> = emptySet(),
        /**
         * Child device under parent rules. The tamper guard must hold whenever parent rules
         * are active, not only during a focus commitment: a Pixel 4 test (28 Sep 2026) showed
         * the uninstall and Accessibility screens open freely under an always-on rule.
         */
        parentControlled: Boolean = false,
        /** Offline reboot protection, see [TimeIntegrity.remainingMs]. */
        checkpoint: Checkpoint? = null,
        bootWallMs: Long? = null,
    ): Decision {
        val always = (foregroundPackage in alwaysBlocked || foregroundPackage in selfBlocked) &&
            foregroundPackage !in ALWAYS_ALLOWED && foregroundPackage !in launcherPackages
        if (parentControlled && alwaysBlocked.isNotEmpty() && isTamperScreen(foregroundPackage, foregroundClass)) {
            return Decision.BlockTamperScreen(foregroundPackage, foregroundClass)
        }
        if (commitment == null) return if (always) Decision.BlockApp(foregroundPackage) else Decision.Allow

        if (TimeIntegrity.remainingMs(commitment, now, serverNowMs, checkpoint, bootWallMs) <= 0) return Decision.CommitmentExpired
        // Note: after expiry the caller clears the commitment and calls decide() again,
        // so parent always-on rules still apply on the next event.

        if (commitment.controlMode == ControlMode.PARENT && isTamperScreen(foregroundPackage, foregroundClass)) {
            return Decision.BlockTamperScreen(foregroundPackage, foregroundClass)
        }

        if (foregroundPackage in ALWAYS_ALLOWED || foregroundPackage in launcherPackages) return Decision.Allow

        val paused = commitment.pausedUntilElapsedMs != null &&
            commitment.pauseBootCount == now.bootCount &&
            now.elapsedRealtimeMs < commitment.pausedUntilElapsedMs
        if (paused) return if (always) Decision.BlockApp(foregroundPackage) else Decision.Allow

        return if (always || foregroundPackage in commitment.blockedPackages) Decision.BlockApp(foregroundPackage) else Decision.Allow
    }
}
