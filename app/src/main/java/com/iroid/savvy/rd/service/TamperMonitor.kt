package com.iroid.savvy.rd.service

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import com.iroid.savvy.rd.admin.SavvyDeviceAdminReceiver
import org.json.JSONObject

/**
 * Reports which Savvy protections are currently on. Sent in the heartbeat so the
 * backend (and a parent) can see when a service was disabled, force-stopped or
 * revoked. Detection only: in self mode Savvy must not prevent these actions.
 */
object TamperMonitor {
    data class Status(
        val accessibility: Boolean,
        val usageAccess: Boolean,
        val overlay: Boolean,
        val deviceAdmin: Boolean,
        val ignoringBatteryOptimizations: Boolean,
        val nfcEnabled: Boolean?,
        val tagIntentsAllowed: Boolean?,
        val advancedProtection: Boolean?,
        val adbEnabled: Boolean,
    ) {
        fun toJson() = JSONObject().apply {
            put("authorization_status", if (accessibility || usageAccess) "approved" else "denied")
            put("accessibility", accessibility); put("usage_access", usageAccess); put("overlay", overlay)
            put("device_admin", deviceAdmin); put("battery_unrestricted", ignoringBatteryOptimizations)
            put("nfc_enabled", nfcEnabled); put("tag_intents_allowed", tagIntentsAllowed)
            put("advanced_protection", advancedProtection); put("adb_enabled", adbEnabled)
        }
    }

    /** Cheap check, polled by the FGS to alert when the service is switched off. */
    fun accessibilityEnabled(context: Context): Boolean {
        val a11yId = ComponentName(context, SavvyAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.split(':').any { it.equals(a11yId, ignoreCase = true) }
    }

    fun read(context: Context): Status {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        // unsafeCheckOpNoThrow (API 29) is itself marked deprecated in the API 36 SDK; both work.
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        val usage = mode == AppOpsManager.MODE_ALLOWED
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val nfc = NfcAdapter.getDefaultAdapter(context)
        return Status(
            accessibility = accessibilityEnabled(context),
            usageAccess = usage,
            overlay = Settings.canDrawOverlays(context),
            deviceAdmin = dpm.isAdminActive(ComponentName(context, SavvyDeviceAdminReceiver::class.java)),
            ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            nfcEnabled = nfc?.isEnabled,
            // Android 16: user can block tag intents per app.
            tagIntentsAllowed = if (Build.VERSION.SDK_INT >= 36 && nfc != null) nfc.isTagIntentAllowed else null,
            advancedProtection = advancedProtection(context),
            // USB debugging lets a technical child remove Savvy with adb; parent mode flags it.
            adbEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1,
        )
    }

    /** Android 16+: AdvancedProtectionManager. On Android 17 AAPM revokes non-tool accessibility services. */
    private fun advancedProtection(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < 36) return null
        return runCatching {
            val mgr = context.getSystemService(android.security.advancedprotection.AdvancedProtectionManager::class.java)
            mgr?.isAdvancedProtectionEnabled
        }.getOrNull()
    }
}
