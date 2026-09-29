package com.iroid.savvy.rd.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.iroid.savvy.core.UsageAggregator
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Android R&D 13. Reads raw UsageEvents (kept by Android for only a few days) and
 * computes per-app daily totals with core UsageAggregator, for upload to
 * PUT /v1/usage/daily. Needs the Usage access grant. Returns empty before the
 * first unlock after boot (Android 11+).
 */
object UsageCollector {
    fun dailyTotals(context: Context, days: Int = 2): Map<LocalDate, Map<String, Long>> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val zone = ZoneId.systemDefault()
        val end = System.currentTimeMillis()
        val begin = LocalDate.now(zone).minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = mutableListOf<UsageAggregator.Event>()
        val it = usm.queryEvents(begin, end) ?: return emptyMap()
        val e = UsageEvents.Event()
        while (it.hasNextEvent()) {
            it.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageAggregator.Kind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageAggregator.Kind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN -> UsageAggregator.Kind.SCREEN_OFF
                else -> null
            } ?: continue
            events += UsageAggregator.Event(e.timeStamp, e.packageName, kind)
        }
        return UsageAggregator.dailyTotals(events, end, zone)
    }

    fun toJson(context: Context, perApp: Map<String, Long>): JSONArray {
        val pm = context.packageManager
        val out = JSONArray()
        perApp.entries.sortedByDescending { it.value }.forEach { (pkg, seconds) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString() }
                .getOrDefault(pkg)
            out.put(JSONObject().put("package", pkg).put("label", label).put("seconds", seconds))
        }
        return out
    }

    /** Launchable apps (through the <queries> launcher intent) for the parent's picker. */
    fun launchableApps(context: Context): JSONArray {
        val pm = context.packageManager
        val out = JSONArray()
        pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.filter { it.first != context.packageName }
            .forEach { (pkg, label) -> out.put(JSONObject().put("package", pkg).put("label", label)) }
        return out
    }
}
