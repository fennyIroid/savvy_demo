package com.iroid.savvy.rd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.Routes
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.AppIcon
import com.iroid.savvy.rd.ui.components.Callout
import com.iroid.savvy.rd.ui.components.SectionLabel
import com.iroid.savvy.rd.ui.components.appLabel
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors
import java.time.format.TextStyle
import java.util.Locale

/** Screen time from UsageStats (stays on the phone in self mode) and focus stats from the backend. */
@Composable
fun ActivityTab(vm: SavvyViewModel, nav: NavHostController) {
    val c = savvyColors
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.refreshSlow(); if (vm.snapshot.registered) vm.loadInsights() }
    val u = vm.usage

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(36.dp))
        // Brick's Today | Average header.
        Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.CenterVertically) {
            BigStat("Today", u?.todaySec ?: 0, Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight(0.45f).background(c.line))
            BigStat("Average", u?.averageSec ?: 0, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))

        if (u == null) {
            Callout(
                if (vm.tamper?.usageAccess == false) "Allow Usage access so Savvy can show your screen time. It stays on this phone."
                else "Activity appears after your first day using Savvy.",
                icon = Icons.Rounded.QueryStats,
                action = if (vm.tamper?.usageAccess == false) "Allow" else null,
                onAction = if (vm.tamper?.usageAccess == false) ({ nav.navigate(Routes.PERMISSIONS) }) else null,
            )
        } else {
            WeekChart(u.weekDaily)
            if (u.todayApps.isNotEmpty()) {
                SectionLabel("Most used today")
                Spacer(Modifier.height(8.dp))
                val max = u.todayApps.first().second.coerceAtLeast(1)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(vertical = 8.dp)) {
                    u.todayApps.take(8).forEach { (pkg, sec) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, size = 36.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(appLabel(context, pkg), style = SavvyType.bodyMedium, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
                                    Text(minutes(sec), style = SavvyType.label, color = c.inkSoft)
                                }
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.cardStrong)) {
                                    Box(Modifier.fillMaxWidth(sec.toFloat() / max).height(5.dp).clip(RoundedCornerShape(3.dp))
                                        .background(if (pkg in vm.snapshot.selected) c.primary else c.inkSoft.copy(alpha = 0.6f)))
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Focus")
        Spacer(Modifier.height(8.dp))
        val i = vm.insights
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallStat("Streak", i?.let { "${it.streakDays} d" } ?: "–", Modifier.weight(1f))
            SmallStat("Today", i?.let { minutes(it.focusTodaySec.toLong()) } ?: "–", Modifier.weight(1f))
            SmallStat("All time", i?.let { hours(it.focusTotalSec.toLong()) } ?: "–", Modifier.weight(1f))
        }
        vm.insightsError?.let { Text(it, style = SavvyType.caption, color = c.inkSoft, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
        Spacer(Modifier.height(32.dp))
    }
}

private fun minutes(sec: Long) = if (sec >= 3600) "${sec / 3600}h ${(sec % 3600) / 60}m" else "${sec / 60}m"
private fun hours(sec: Long) = if (sec >= 3600) "${sec / 3600}h" else "${sec / 60}m"

@Composable
private fun BigStat(label: String, seconds: Long, modifier: Modifier) {
    val c = savvyColors
    val (h, m) = Friendly.hm(seconds)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = SavvyType.label, color = c.inkSoft)
        Spacer(Modifier.height(4.dp))
        Text(h, style = SavvyType.display, color = c.ink)
        Text(m, style = SavvyType.display, color = c.ink)
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier) {
    val c = savvyColors
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(c.card).padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = SavvyType.title, color = c.ink)
        Text(label, style = SavvyType.caption, color = c.inkSoft)
    }
}

@Composable
private fun WeekChart(days: List<Pair<java.time.LocalDate, Long>>) {
    val c = savvyColors
    val max = days.maxOf { it.second }.coerceAtLeast(60)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(20.dp)) {
        Text("Last 7 days", style = SavvyType.label, color = c.inkSoft)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, (_, sec) ->
                val today = i == days.lastIndex
                Box(Modifier.width(26.dp).fillMaxHeight((sec.toFloat() / max).coerceIn(0.03f, 1f))
                    .clip(RoundedCornerShape(8.dp)).background(if (today) c.primary else c.icy.copy(alpha = if (c.isDark) 0.35f else 0.75f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { (d, _) ->
                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = SavvyType.caption, color = c.inkSoft,
                    modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
            }
        }
    }
}
