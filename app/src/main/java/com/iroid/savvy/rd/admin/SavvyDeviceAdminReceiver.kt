package com.iroid.savvy.rd.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.iroid.savvy.rd.data.SavvyLog

/**
 * Approach E, PARENT mode only. Being an active device admin means:
 *  - Savvy cannot be uninstalled until deactivated (uninstall is redirected to deactivation);
 *  - AOSP Settings disables Force stop and Clear storage for active admins (OEMs may differ).
 * It does not block uninstall by itself: the child can deactivate in Settings,
 * which the parent-mode AccessibilityService guards (RestrictionPolicy.TAMPER_SCREENS).
 * Safe mode still defeats it (only Device Owner can set DISALLOW_SAFE_BOOT).
 */
class SavvyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) = SavvyLog.event("Admin", "enabled")

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        SavvyLog.event("Admin", "disable requested")
        return "Turning this off removes Savvy's parental protection. Your parent will be notified."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // Tamper event: next heartbeat reports device_admin=false to the parent.
        SavvyLog.event("Admin", "DISABLED")
    }
}
