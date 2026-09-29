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

    /**
     * A-USAGE-2: Android keeps raw UsageEvents for only a few days, so Savvy keeps its own
     * per-app daily totals (35 days, on the phone). A day recomputed from fewer surviving
     * events never lowers a stored figure.
     */
    @Synchronized
    fun recordHistory(context: Context, totals: Map<LocalDate, Map<String, Long>>) {
        val prefs = context.getSharedPreferences("savvy_usage", Context.MODE_PRIVATE)
        val all = JSONObject(prefs.getString("days", "{}")!!)
        totals.forEach { (date, perApp) ->
            val day = all.optJSONObject(date.toString()) ?: JSONObject()
            perApp.forEach { (pkg, sec) -> if (sec > day.optLong(pkg)) day.put(pkg, sec) }
            all.put(date.toString(), day)
        }
        val oldest = LocalDate.now().minusDays(35)
        all.keys().asSequence().toList().filter { LocalDate.parse(it).isBefore(oldest) }.forEach(all::remove)
        prefs.edit().putString("days", all.toString()).apply()
    }

    /** Stored daily totals for the last [days] days (including today), oldest first. */
    fun history(context: Context, days: Int): Map<LocalDate, Map<String, Long>> {
        val all = JSONObject(context.getSharedPreferences("savvy_usage", Context.MODE_PRIVATE).getString("days", "{}")!!)
        val today = LocalDate.now()
        return (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.associateWith { d ->
            all.optJSONObject(d.toString())?.let { o -> o.keys().asSequence().associateWith { o.getLong(it) } } ?: emptyMap()
        }.filterValues { it.isNotEmpty() }
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
