package com.iroid.savvy.rd.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.Routes
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.CardLook
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.PillStyle
import com.iroid.savvy.rd.ui.components.SavvyCardHero
import com.iroid.savvy.rd.ui.components.TopPill
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HomeTab(vm: SavvyViewModel, nav: NavHostController, onUnlock: () -> Unit) {
    val c = savvyColors
    val s = vm.snapshot
    val t = vm.tamper
    val blockingOff = t != null && !t.accessibility && !t.usageAccess
    var confirm by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        // Top pill: live countdown, today's screen time, or a nudge to finish setup.
        when {
            s.active -> TopPill {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.paused) c.inkSoft else c.success))
                Spacer(Modifier.width(10.dp))
                Text(Friendly.clock(if (s.paused) s.pausedForMs else s.remainingMs), style = SavvyType.title, color = c.ink)
                Spacer(Modifier.width(8.dp))
                Text(if (s.paused) "break" else "left", style = SavvyType.caption, color = c.inkSoft)
            }
            blockingOff -> TopPill(onClick = { nav.navigate(Routes.PERMISSIONS) }) {
                Icon(Icons.Rounded.WarningAmber, null, tint = c.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Blocking is off. Finish setup", style = SavvyType.label, color = c.ink)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            else -> TopPill(onClick = { nav.navigate(Routes.PERMISSIONS) }.takeIf { vm.usage == null }) {
                val (h, m) = Friendly.hm(vm.usage?.todaySec ?: 0)
                Text("$h $m", style = SavvyType.title, color = c.ink)
                Spacer(Modifier.width(8.dp))
                Text("today", style = SavvyType.caption, color = c.inkSoft)
            }
        }

        Spacer(Modifier.weight(1f))
        val look = when {
            s.paused -> CardLook.IDLE
            s.active -> CardLook.ACTIVE
            else -> CardLook.IDLE
        }
        SavvyCardHero(look)
        Spacer(Modifier.height(8.dp))

        val c0 = s.commitment
        AnimatedContent(s.active, label = "state") { active ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (active && c0 != null) {
                    Text(Friendly.mode(c0.mode), style = SavvyType.headline, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    val ends = remember(c0.localId) {
                        Instant.now().plusMillis(s.remainingMs).atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                    }
                    val n0 = c0.blockedPackages.size
                    Text("$n0 app${if (n0 == 1) "" else "s"} blocked until $ends", style = SavvyType.body, color = c.inkSoft)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (c0.unlockPolicy == UnlockPolicy.LOCKED) Icon(Icons.Rounded.Lock, null, tint = c.ink, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                        Text(Friendly.policy(c0.unlockPolicy), style = SavvyType.label, color = c.ink)
                        if (c0.taskRef != null) Text("  ·  ${s.tasks.firstOrNull { it.id == c0.taskRef }?.title ?: "Task"}", style = SavvyType.label, color = c.ink)
                    }
                } else {
                    Text(Friendly.mode(vm.session.mode), style = SavvyType.headline, color = c.ink)
                    Spacer(Modifier.height(6.dp))
                    val n = (s.selected + s.selfBlocked).size
                    Text(
                        if (n == 0) "No apps chosen yet" else "Blocks $n app${if (n == 1) "" else "s"} for ${Friendly.duration(vm.session.minutes)}",
                        style = SavvyType.body, color = c.inkSoft,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(Routes.SESSION) }.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Edit session", style = SavvyType.label, color = c.ink)
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.ink, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (s.parentBlocked.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Your parent keeps ${s.parentBlocked.size} apps blocked", style = SavvyType.caption, color = c.inkSoft, textAlign = TextAlign.Center)
        }
        // Secondary entry to the user's own always-blocked apps (outside focus sessions).
        if (!s.managedByParent) {
            Spacer(Modifier.height(10.dp))
            val n = s.selfBlocked.size
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { nav.navigate(Routes.ALWAYS_BLOCKED) }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = c.inkSoft, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (n == 0) "Block an app anytime" else "$n app${if (n == 1) "" else "s"} always blocked",
                    style = SavvyType.caption, color = c.inkSoft,
                )
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.inkSoft, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.weight(1f))

        // Primary action, Brick's "Tap or hold" pill.
        val c1 = s.commitment
        when {
            !s.active -> PillButton(
                if (s.selected.isEmpty()) "Choose apps to block" else "Start focus",
                onClick = {
                    when {
                        s.selected.isEmpty() -> nav.navigate(Routes.APPS)
                        vm.session.policy == UnlockPolicy.CARD_REQUIRED && s.boundCard == null -> confirm = "card"
                        vm.session.policy == UnlockPolicy.LOCKED -> confirm = "locked"
                        else -> vm.startSession()
                    }
                },
                style = PillStyle.OUTLINE, busy = vm.busy == "Starting", height = 64.dp,
            )
            s.paused -> PillButton("On a break", onClick = {}, style = PillStyle.OUTLINE, enabled = false, height = 64.dp)
            c1?.unlockPolicy == UnlockPolicy.CARD_REQUIRED -> PillButton(
                if (c1.mode == Mode.TASK) "Tap card to finish early" else "Tap card to unlock", onClick = onUnlock,
                style = PillStyle.OUTLINE, icon = Icons.Rounded.Contactless, height = 64.dp,
            )
            c1?.unlockPolicy == UnlockPolicy.FREE -> PillButton(
                "End session", onClick = { vm.run("Ending") { endFree() } }, style = PillStyle.OUTLINE, busy = vm.busy == "Ending", height = 64.dp,
            )
            else -> PillButton("Locked until the timer ends", onClick = {}, style = PillStyle.OUTLINE, enabled = false, icon = Icons.Rounded.Lock, height = 64.dp)
        }
        if (s.active && !s.paused) {
            Text(
                "Emergency exit", style = SavvyType.caption, color = c.inkSoft,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).clickable { nav.navigate(Routes.EMERGENCY) }.padding(8.dp),
            )
        } else Spacer(Modifier.height(38.dp))
        Spacer(Modifier.height(12.dp))
    }

    when (confirm) {
        "card" -> ConfirmDialog(
            title = "Link your card first?",
            body = "This session ends early only with your Savvy card, and no card is linked to your account yet.",
            confirm = "Link card", dismiss = "Start anyway",
            onConfirm = { confirm = null; nav.navigate(Routes.CARD) },
            onDismiss = { confirm = null; vm.startSession() },
            onCancel = { confirm = null },
        )
        "locked" -> ConfirmDialog(
            title = "Lock for ${Friendly.duration(vm.session.minutes)}?",
            body = "A locked session can't be ended early, not even with your card. Only the emergency exit works.",
            confirm = "Lock it", dismiss = "Cancel",
            onConfirm = { confirm = null; vm.startSession() },
            onDismiss = { confirm = null },
            onCancel = { confirm = null },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String, body: String, confirm: String, dismiss: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit, onCancel: () -> Unit = onDismiss, destructive: Boolean = false,
) {
    val c = savvyColors
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = c.sheet,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = SavvyType.title, color = c.ink) },
        text = { Text(body, style = SavvyType.body, color = c.inkSoft) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, style = SavvyType.bodyMedium, color = if (destructive) c.danger else c.ink) } },
        dismissButton = { TextButton(onDismiss) { Text(dismiss, style = SavvyType.body, color = c.inkSoft) } },
    )
}
