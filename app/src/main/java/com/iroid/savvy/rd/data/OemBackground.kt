package com.iroid.savvy.rd.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android R&D 4, OEM reliability: the brand-specific "keep running" screen that the standard
 * battery-optimisation setting does not cover (dontkillmyapp.com; component names vary by
 * ROM version, so each brand has several candidates and a final App info fallback).
 * Savvy cannot read these settings back, so the UI only records that the user opened them.
 *
 * Components are started directly (no resolveActivity), because package visibility on
 * Android 11+ would hide these system packages from a lookup.
 */
object OemBackground {
    data class Step(val brand: String, val title: String, val instructions: String, val candidates: List<ComponentName>)

    fun step(manufacturer: String = Build.MANUFACTURER): Step? = when (manufacturer.lowercase()) {
        "samsung" -> Step("Samsung", "Never sleeping apps",
            "Battery › Background usage limits › Never sleeping apps › add Savvy. Also turn off \"Put unused apps to sleep\".",
            listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ))
        "xiaomi", "redmi", "poco" -> Step("Xiaomi", "Autostart",
            "Turn Autostart on for Savvy, then in App info › Battery saver choose \"No restrictions\".",
            listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            ))
        "oppo", "realme" -> Step("Oppo", "Auto launch",
            "Allow Savvy to auto launch and run in the background. Oppo can turn accessibility services off on screen-off: Savvy alerts you if that happens.",
            listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ))
        "vivo", "iqoo" -> Step("Vivo", "Background start",
            "Allow Savvy to start in the background and keep running (High background power consumption).",
            listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ))
        "oneplus" -> Step("OnePlus", "Auto launch",
            "Allow auto launch, and lock Savvy in Recent apps (pull down on its card). OnePlus can reset battery optimisation.",
            listOf(
                ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ))
        "huawei", "honor" -> Step("Huawei", "App launch",
            "Set Savvy to \"Manage manually\" and allow auto-launch, secondary launch and run in background.",
            listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ))
        else -> null // Pixel / AOSP-like: the standard battery setting is enough.
    }

    /** Opens the first candidate that exists, else Savvy's App info. Returns what was opened (for the log). */
    fun open(context: Context, step: Step): String {
        for (cn in step.candidates) {
            val ok = runCatching {
                context.startActivity(Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
            }.recoverCatching { e -> if (e is ActivityNotFoundException || e is SecurityException) false else throw e }.getOrDefault(false)
            if (ok) { SavvyLog.event("OEM", "opened ${cn.flattenToShortString()}"); return cn.flattenToShortString() }
        }
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SavvyLog.event("OEM", "${step.brand}: no known screen, opened App info")
        return "app_info"
    }
}
