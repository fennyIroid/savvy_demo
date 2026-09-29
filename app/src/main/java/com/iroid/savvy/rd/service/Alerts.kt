package com.iroid.savvy.rd.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * User-facing alerts outside the focus notification. Framework Notification.Builder (minSdk 26)
 * so the Robolectric compile check needs no extra AndroidX stubs.
 */
object Alerts {
    const val EXTRA_OPEN = "open"
    const val OPEN_PERMISSIONS = "permissions"
    private const val CHANNEL = "alerts"
    private const val ID_A11Y_OFF = 20

    /**
     * Accessibility turned off during enforcement: by the user, a force stop, an OEM battery
     * killer (Oppo / Vivo turn services off on screen-off) or Android 17 Advanced Protection.
     * Blocking continues through UsageStats if granted; the tap leads back to the Permissions
     * screen, where the prominent disclosure is shown again before Settings opens.
     */
    fun accessibilityOff(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Protection alerts", NotificationManager.IMPORTANCE_HIGH))
        val open = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .putExtra(EXTRA_OPEN, OPEN_PERMISSIONS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, ID_A11Y_OFF, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Savvy can't see blocked apps")
            .setContentText("The blocking service was turned off. Tap to turn it back on.")
            .setStyle(Notification.BigTextStyle().bigText(
                "The blocking service was turned off, so blocked apps may open for a moment before Savvy closes them. Tap to turn it back on."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID_A11Y_OFF, n) }
    }

    fun clearAccessibilityOff(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID_A11Y_OFF)
}
