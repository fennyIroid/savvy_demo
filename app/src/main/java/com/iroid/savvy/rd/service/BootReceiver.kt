package com.iroid.savvy.rd.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iroid.savvy.rd.admin.DeviceOwnerController
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * Restarts enforcement after reboot. The AccessibilityService is re-bound by the
 * system on its own if it is still enabled. The specialUse FGS may be started
 * from BOOT_COMPLETED (Android 15 blocks only dataSync, camera, mediaPlayback,
 * phoneCall, mediaProjection and microphone types).
 *
 * directBootAware: LOCKED_BOOT_COMPLETED arrives before the user unlocks, so the wall clock
 * at boot is recorded before anyone can open Settings and change it (core TimeIntegrity,
 * offline reboot protection). Nothing else may run in that state: credential-encrypted
 * storage (the commitment, the log file) is not readable until the first unlock.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = context.savvy.repo
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            repo.recordBootWall(overwrite = true)
            return
        }
        // Devices without direct boot (or a missed locked broadcast): best effort, keeps an earlier record.
        repo.recordBootWall(overwrite = false)
        val c = repo.commitment
        SavvyLog.event("Boot", "${intent.action} commitment=${c?.serverId ?: if (c == null) "none" else "local"} bootWall=${repo.bootWallMs() != null}")
        if (c != null || repo.hasAlwaysOnBlocks) UsageMonitorService.start(context)
        DeviceOwnerController.reconcile(context)
        SyncJobService.schedule(context)
    }
}

/**
 * ACTION_TIME_CHANGED (exempt from the implicit-broadcast limits, so it wakes Savvy even when
 * not running): checkpoint the commitment with the monotonic clock BEFORE the new wall clock
 * can be carried across a reboot.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = context.savvy.repo
        if (repo.commitment == null) return
        repo.saveCheckpoint("clock changed (${intent.action?.substringAfterLast('.')})")
    }
}
