package com.iroid.savvy.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns UsageEvents (ACTIVITY_RESUMED / ACTIVITY_PAUSED / ACTIVITY_STOPPED, screen
 * off / keyguard) into per-app foreground seconds per local day. Android keeps raw
 * events for only a few days, so Savvy computes and stores its own daily totals
 * (PUT /v1/usage/daily). Pure logic, unit-tested.
 */
object UsageAggregator {
    enum class Kind { RESUMED, PAUSED, SCREEN_OFF }

    data class Event(val timeMs: Long, val packageName: String?, val kind: Kind)

    /** @return date -> (package -> seconds). An app still in front at [endMs] counts until [endMs]. */
    fun dailyTotals(events: List<Event>, endMs: Long, zone: ZoneId): Map<LocalDate, Map<String, Long>> {
        val totals = sortedMapOf<LocalDate, MutableMap<String, Long>>()
        var current: String? = null
        var since = 0L

        fun close(untilMs: Long) {
            val pkg = current ?: return
            var from = since
            // Split the interval at local midnights so each day gets its own share.
            while (from < untilMs) {
                val day = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
                val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val to = minOf(untilMs, nextMidnight)
                val perApp = totals.getOrPut(day) { mutableMapOf() }
                perApp[pkg] = (perApp[pkg] ?: 0L) + (to - from)
                from = to
            }
            current = null
        }

        for (e in events.sortedBy { it.timeMs }) {
            when (e.kind) {
                Kind.RESUMED -> {
                    if (current != null && current != e.packageName) close(e.timeMs)
                    if (current == null) { current = e.packageName; since = e.timeMs }
                }
                Kind.PAUSED -> if (current != null && current == e.packageName) close(e.timeMs)
                Kind.SCREEN_OFF -> close(e.timeMs)
            }
        }
        close(endMs)
        return totals.mapValues { (_, m) -> m.mapValues { it.value / 1000 }.filterValues { it > 0 } }
    }
}
