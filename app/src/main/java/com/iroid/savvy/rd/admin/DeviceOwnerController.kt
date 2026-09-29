package com.iroid.savvy.rd.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.iroid.savvy.core.RestrictionPolicy
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * Approach F (R&D alternative, NOT a production feature). Proves what Device Owner adds,
 * so the brief's "technically possible under special device management" line is backed by code.
 *
 * Active only when Savvy is Device Owner, which needs provisioning on a device with no
 * accounts (factory reset, or on a test phone:
 * `adb shell dpm set-device-owner com.iroid.savvy.rd/.admin.SavvyDeviceAdminReceiver`).
 * On every other install every call here is a no-op.
 *
 * While a commitment or parent always-on rule is active it:
 *  - suspends the blocked apps (setPackagesSuspended): the OS itself refuses to open them,
 *    from every launch path, with no Accessibility or UsageStats and no visible flash;
 *  - blocks Savvy's uninstall (setUninstallBlocked);
 *  - blocks safe mode (DISALLOW_SAFE_BOOT), the bypass no consumer-mode approach closes.
 * All three are lifted when the restriction ends or pauses. Force stop and Clear data are
 * also refused by the system for a Device Owner.
 */
object DeviceOwnerController {
    private const val PREFS = "savvy_do"

    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)
    private fun admin(context: Context) = ComponentName(context, SavvyDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context) = runCatching { dpm(context).isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    /** Brings the device policy in line with the current Savvy state. Cheap when nothing changed. */
    @Synchronized
    fun reconcile(context: Context) {
        if (!isDeviceOwner(context)) return
        val repo = context.savvy.repo
        val c = repo.commitment
        val now = repo.now()
        val paused = c?.pausedUntilElapsedMs != null && c.pauseBootCount == now.bootCount && now.elapsedRealtimeMs < c.pausedUntilElapsedMs!!
        val focus = if (c != null && !paused && repo.remainingMs(c, now) > 0) c.blockedPackages else emptySet()
        val want = (focus + repo.parentBlockedPackages) - RestrictionPolicy.ALWAYS_ALLOWED - context.packageName
        val protect = c != null || repo.parentBlockedPackages.isNotEmpty()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val had = prefs.getStringSet("suspended", emptySet())!!.toSet()
        val hadProtect = prefs.getBoolean("protect", false)
        if (want == had && protect == hadProtect) return

        val dpm = dpm(context)
        val admin = admin(context)
        runCatching {
            val lift = (had - want).toTypedArray()
            if (lift.isNotEmpty()) dpm.setPackagesSuspended(admin, lift, false)
            val add = (want - had).toTypedArray()
            // Returns the packages that could not be suspended (system apps such as the dialer).
            val failed = if (add.isNotEmpty()) dpm.setPackagesSuspended(admin, add, true).toSet() else emptySet()
            val suspended = want - failed
            dpm.setUninstallBlocked(admin, context.packageName, protect)
            if (protect) dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            else dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            prefs.edit().putStringSet("suspended", suspended).putBoolean("protect", protect).apply()
            SavvyLog.event("DeviceOwner", "suspended=${suspended.size} failed=$failed protect=$protect")
        }.onFailure { SavvyLog.event("DeviceOwner", "reconcile failed $it") }
    }

    /** Diagnostics: lift everything and give up Device Owner so the test phone is normal again. */
    fun removeDeviceOwner(context: Context): String {
        if (!isDeviceOwner(context)) return "Savvy is not Device Owner"
        val dpm = dpm(context)
        val admin = admin(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return runCatching {
            val had = prefs.getStringSet("suspended", emptySet())!!.toTypedArray()
            if (had.isNotEmpty()) dpm.setPackagesSuspended(admin, had, false)
            dpm.setUninstallBlocked(admin, context.packageName, false)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            prefs.edit().clear().apply()
            @Suppress("DEPRECATION") dpm.clearDeviceOwnerApp(context.packageName)
            SavvyLog.event("DeviceOwner", "removed by tester")
            "Device Owner removed; ${had.size} app(s) unsuspended"
        }.getOrElse { "remove failed: $it" }
    }

    fun describe(context: Context): String {
        if (!isDeviceOwner(context)) return "Device Owner: off.\n" +
            "R&D only. On a test phone with no accounts:\n" +
            "adb shell dpm set-device-owner ${context.packageName}/.admin.SavvyDeviceAdminReceiver\n" +
            "Then start a session: blocked apps are suspended by the OS, uninstall and safe mode are blocked."
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return "Device Owner: ON\nsuspended=${prefs.getStringSet("suspended", emptySet())}\n" +
            "uninstall+safe-mode blocked=${prefs.getBoolean("protect", false)}"
    }
}
