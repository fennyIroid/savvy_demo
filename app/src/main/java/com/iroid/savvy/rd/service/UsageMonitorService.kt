package com.iroid.savvy.rd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * Approach A + C + D (fallback when Accessibility is not allowed or is revoked,
 * for example Android 17 Advanced Protection Mode). Polls UsageEvents for
 * ACTIVITY_RESUMED and launches the block screen. Background activity launch is
 * allowed because the user granted SYSTEM_ALERT_WINDOW (documented BAL exemption).
 * Also shows the persistent "focus session active" notification.
 */
class UsageMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastQuery = System.currentTimeMillis() - 5_000
    private var lastPackage: String? = null
    private var lastHousekeeping = 0L

    private val poll = object : Runnable {
        override fun run() {
            val usm = getSystemService(UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(lastQuery, now)
            val e = UsageEvents.Event()
            var latest: Pair<String, String?>? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = e.packageName to e.className
            }
            lastQuery = now
            latest?.let { (pkg, cls) ->
                if (pkg != lastPackage) {
                    lastPackage = pkg
                    savvy.engine.onForeground(pkg, cls, BlockingEngine.Source.USAGE_STATS, null)
                }
            }
            val repo = savvy.repo
            if (repo.commitment != null || repo.parentBlockedPackages.isNotEmpty()) handler.postDelayed(this, POLL_MS) else stopSelf()
            // Every 15 minutes: heartbeat, offline sync, usage upload, parent rules (off the main thread).
            if (android.os.SystemClock.elapsedRealtime() - lastHousekeeping > HOUSEKEEPING_MS) {
                lastHousekeeping = android.os.SystemClock.elapsedRealtime()
                kotlin.concurrent.thread { runCatching { com.iroid.savvy.rd.SavvyActions(this@UsageMonitorService).housekeeping() } }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, notification(), type)
        handler.removeCallbacks(poll)
        handler.post(poll)
        SavvyLog.event("UsageSvc", "started")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        SavvyLog.event("UsageSvc", "destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("focus", "Focus session", NotificationManager.IMPORTANCE_LOW))
        val c = savvy.repo.commitment
        return NotificationCompat.Builder(this, "focus")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Savvy focus is on")
            .setContentText(c?.let { "${it.mode.name.lowercase()} session active" } ?: "Starting")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val POLL_MS = 750L
        const val HOUSEKEEPING_MS = 15 * 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
