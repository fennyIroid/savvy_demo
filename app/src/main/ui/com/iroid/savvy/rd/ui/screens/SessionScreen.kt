package com.iroid.savvy.rd.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.Routes
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.SessionSetup
import com.iroid.savvy.rd.ui.components.Chip
import com.iroid.savvy.rd.ui.components.FieldPill
import com.iroid.savvy.rd.ui.components.ModalPage
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.RoundIconButton
import com.iroid.savvy.rd.ui.components.SectionLabel
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors

val durationPresets = listOf(25, 60, 120, 360, 1440)

/** Session setup: mode, duration, how it can end, and the apps. Saved for the next "Start focus". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionScreen(vm: SavvyViewModel, nav: NavHostController) {
    val c = savvyColors
    var mode by remember { mutableStateOf(vm.session.mode) }
    var minutes by remember { mutableIntStateOf(vm.session.minutes) }
    var policy by remember { mutableStateOf(vm.session.policy) }
    fun save() = vm.saveSession(SessionSetup(mode, minutes, policy))

    ModalPage("Focus session", onBack = { save(); nav.popBackStack() }, bottom = {
        PillButton("Save session", onClick = { save(); nav.popBackStack() })
    }) {
        SectionLabel("Mode")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Mode.STUDY, Mode.WORK, Mode.SLEEP, Mode.CUSTOM).forEach { m ->
                Chip(Friendly.mode(m), mode == m, onClick = {
                    mode = m
                    // The old test-lab pairings as a starting point: Study = card, Work = locked, Sleep = free.
                    policy = when (m) { Mode.WORK -> UnlockPolicy.LOCKED; Mode.SLEEP -> UnlockPolicy.FREE; else -> UnlockPolicy.CARD_REQUIRED }
                })
            }
        }

        SectionLabel("Duration")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.Rounded.Remove, "Shorter", { minutes = (minutes - if (minutes > 60) 30 else 5).coerceAtLeast(5) })
            Text(Friendly.duration(minutes), style = SavvyType.headline, color = c.ink, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            RoundIconButton(Icons.Rounded.Add, "Longer", { minutes = (minutes + if (minutes >= 60) 30 else 5).coerceAtMost(24 * 60) })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            durationPresets.forEach { m -> Chip(Friendly.duration(m), minutes == m, onClick = { minutes = m }) }
        }

        SectionLabel("Ending early")
        PolicyOption(Icons.Rounded.Contactless, "Card to unlock", Friendly.policyLong(UnlockPolicy.CARD_REQUIRED), policy == UnlockPolicy.CARD_REQUIRED) { policy = UnlockPolicy.CARD_REQUIRED }
        PolicyOption(Icons.Rounded.LockClock, "Locked", Friendly.policyLong(UnlockPolicy.LOCKED), policy == UnlockPolicy.LOCKED) { policy = UnlockPolicy.LOCKED }
        PolicyOption(Icons.Rounded.TouchApp, "Free", Friendly.policyLong(UnlockPolicy.FREE), policy == UnlockPolicy.FREE) { policy = UnlockPolicy.FREE }

        Spacer(Modifier.padding(2.dp))
        FieldPill("Blocked apps", onClick = { save(); nav.navigate(Routes.APPS) }) {
            Text("${vm.snapshot.selected.size} apps", style = SavvyType.bodyMedium, color = c.ink)
        }
    }
}

@Composable
fun PolicyOption(icon: ImageVector, title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val c = savvyColors
    val border by animateColorAsState(if (selected) c.primary else c.card, label = "border")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).border(1.5.dp, border, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (selected) c.icySoft else c.cardStrong), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SavvyType.bodyMedium, color = c.ink)
            Text(body, style = SavvyType.caption, color = c.inkSoft)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(24.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
