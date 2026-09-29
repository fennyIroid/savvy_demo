package com.iroid.savvy.rd.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * Restarts enforcement after reboot. The AccessibilityService is re-bound by the
 * system on its own if it is still enabled. The specialUse FGS may be started
 * from BOOT_COMPLETED (Android 15 blocks only dataSync, camera, mediaPlayback,
 * phoneCall, mediaProjection and microphone types).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.savvy.repo.commitment
        SavvyLog.event("Boot", "${intent.action} commitment=${c?.serverId ?: if (c == null) "none" else "local"}")
        if (c != null || context.savvy.repo.parentBlockedPackages.isNotEmpty()) UsageMonitorService.start(context)
    }
}
