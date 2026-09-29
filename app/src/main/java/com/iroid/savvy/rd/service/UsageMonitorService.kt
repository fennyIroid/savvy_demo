package com.iroid.savvy.rd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.iroid.savvy.rd.UnlockCoordinator
import com.iroid.savvy.rd.admin.DeviceOwnerController
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * Approach A + C + D (fallback when Accessibility is not allowed or is revoked,
 * for example Android 17 Advanced Protection Mode). Polls UsageEvents for
 * ACTIVITY_RESUMED and shows the block layer (BlockOverlay, TYPE_APPLICATION_OVERLAY with
 * SYSTEM_ALERT_WINDOW), or launches the block screen if the layer can't be added. Background
 * activity launch is allowed because the user granted SYSTEM_ALERT_WINDOW (documented BAL exemption).
 * Also shows the persistent "focus session active" notification with a live countdown.
 *
 * The same loop keeps the commitment honest while it runs:
 *  - ends it the moment the time is up (to-do Flow B), not at the next app switch;
 *  - saves a time checkpoint every minute (offline reboot protection, core TimeIntegrity);
 *  - alerts when the accessibility service is switched off (user, force stop, OEM killer);
 *  - keeps Device Owner suspensions in step with pauses (R&D Approach F only).
 */
class UsageMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastQuery = System.currentTimeMillis() - 5_000
    private var lastPackage: String? = null
    private var lastHousekeeping = 0L
    private var lastCheckpoint = 0L
    private var lastSlowCheck = 0L

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
            val rt = SystemClock.elapsedRealtime()
            repo.commitment?.let { c ->
                if (repo.remainingMs(c) <= 0) {
                    SavvyLog.event("UsageSvc", "commitment time is up")
                    UnlockCoordinator(this@UsageMonitorService).releaseLocally("expired")
                } else if (rt - lastCheckpoint > CHECKPOINT_MS) {
                    lastCheckpoint = rt
                    repo.saveCheckpoint("tick")
                }
            }
            if (rt - lastSlowCheck > SLOW_CHECK_MS) {
                lastSlowCheck = rt
                watchAccessibility()
                DeviceOwnerController.reconcile(this@UsageMonitorService)
            }
            if (repo.commitment != null || repo.hasAlwaysOnBlocks) handler.postDelayed(this, POLL_MS) else stopSelf()
            // Every 15 minutes: heartbeat, offline sync, usage upload, parent rules (off the main thread).
            if (rt - lastHousekeeping > HOUSEKEEPING_MS) {
                lastHousekeeping = rt
                kotlin.concurrent.thread { runCatching { com.iroid.savvy.rd.SavvyActions(this@UsageMonitorService).housekeeping() } }
            }
        }
    }

    /**
     * On -> off transition while enforcing: alert the user and tell the backend (parent) now, not
     * in 15 min. The last state is persisted, so a force stop (which kills this service AND turns
     * the accessibility service off) is caught when Savvy is next opened and the service restarts.
     */
    private fun watchAccessibility() {
        val on = TamperMonitor.accessibilityEnabled(this)
        val prefs = getSharedPreferences("savvy_svc", Context.MODE_PRIVATE)
        val was = if (prefs.contains("a11yOn")) prefs.getBoolean("a11yOn", false) else null
        if (was != on) prefs.edit().putBoolean("a11yOn", on).apply()
        if (was == true && !on) {
            SavvyLog.event("UsageSvc", "accessibility service turned off during enforcement")
            Alerts.accessibilityOff(this)
            kotlin.concurrent.thread { runCatching { com.iroid.savvy.rd.SavvyActions(this).housekeeping() } }
        } else if (on && was == false) {
            Alerts.clearAccessibilityOff(this)
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
        val repo = savvy.repo
        val c = repo.commitment
        val b = Notification.Builder(this, "focus")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Savvy focus is on")
            .setOngoing(true)
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            b.setContentIntent(PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE))
        }
        if (c != null) {
            // Live countdown in the shade, computed from the tamper-resistant remaining time.
            val endsAt = System.currentTimeMillis() + repo.remainingMs(c)
            b.setContentText("${c.mode.name.lowercase().replaceFirstChar { it.uppercase() }} session · ${c.blockedPackages.size} app${if (c.blockedPackages.size == 1) "" else "s"} blocked")
                .setWhen(endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            val self = repo.selfBlockedPackages.size
            b.setContentText(when {
                repo.parentBlockedPackages.isNotEmpty() -> "Your parent's rules are on"
                self > 0 -> "$self app${if (self == 1) "" else "s"} always blocked"
                else -> "Starting"
            })
        }
        return b.build()
    }

    companion object {
        const val POLL_MS = 750L
        const val HOUSEKEEPING_MS = 15 * 60_000L
        const val CHECKPOINT_MS = 60_000L
        const val SLOW_CHECK_MS = 5_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageMonitorService::class.java))
        }
    }
}
