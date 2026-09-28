package com.iroid.savvy.rd.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.iroid.savvy.rd.BuildConfig
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.Routes
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.CardLook
import com.iroid.savvy.rd.ui.components.GroupCard
import com.iroid.savvy.rd.ui.components.RowDivider
import com.iroid.savvy.rd.ui.components.SavvyCardHero
import com.iroid.savvy.rd.ui.components.SettingRow
import com.iroid.savvy.rd.ui.theme.FrozenLake
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors

fun roleLabel(role: String) = when (role) { "parent" -> "Parent's phone"; "child" -> "Child's phone"; else -> "Just me" }

@Composable
fun SettingsTab(vm: SavvyViewModel, nav: NavHostController) {
    val c = savvyColors
    val context = LocalContext.current
    val s = vm.snapshot
    val t = vm.tamper
    val missing = t?.let { listOf(it.accessibility || it.usageAccess, it.overlay, it.ignoringBatteryOptimizations).count { ok -> !ok } } ?: 0

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        GroupCard {
            SettingRow("Account", Icons.Outlined.Person,
                subtitle = if (s.registered) "${vm.prefs.email.ifBlank { "Device ${s.deviceId}" }} · ${roleLabel(s.role)}" else "Not set up",
                onClick = { nav.navigate(Routes.ACCOUNT) })
        }

        // Card banner, in the place of Brick's "Share with a friend".
        Box(
            Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(FrozenLake.Slate, FrozenLake.Icy)))
                .clickable { nav.navigate(Routes.CARD) },
        ) {
            Column(Modifier.padding(start = 22.dp, top = 20.dp).fillMaxWidth(0.6f)) {
                Text(if (s.boundCard != null) "Your Savvy card is linked" else "Link your Savvy card", style = SavvyType.title, color = FrozenLake.Snow)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.clip(CircleShape).background(FrozenLake.Navy).padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Text(if (s.boundCard != null) "Card ${s.boundCard.takeLast(4)}" else "Link card now", style = SavvyType.label, color = FrozenLake.Snow)
                }
            }
            Box(Modifier.align(Alignment.CenterEnd).offset(x = 70.dp, y = 10.dp)) {
                SavvyCardHero(if (s.boundCard != null) CardLook.ACTIVE else CardLook.IDLE, width = 150.dp)
            }
        }

        GroupCard {
            SettingRow("Blocked apps", Icons.Outlined.Apps, value = "${s.selected.size}", onClick = { nav.navigate(Routes.APPS) })
            RowDivider()
            SettingRow("Focus session", Icons.Outlined.Timer,
                value = "${Friendly.mode(vm.session.mode)} · ${Friendly.duration(vm.session.minutes)}", onClick = { nav.navigate(Routes.SESSION) })
            RowDivider()
            SettingRow("Emergency exit", Icons.Outlined.Lock, value = "2 a week", onClick = { nav.navigate(Routes.EMERGENCY) })
        }

        GroupCard {
            SettingRow("Permissions", Icons.Outlined.Shield,
                value = if (t == null) null else if (missing == 0) "All on" else "$missing off",
                valueColor = if (missing > 0) c.danger else null,
                onClick = { nav.navigate(Routes.PERMISSIONS) })
            RowDivider()
            SettingRow("Notifications", Icons.Outlined.Notifications, onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
            RowDivider()
            SettingRow("Appearance", Icons.Outlined.DarkMode, value = vm.appearance.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { nav.navigate(Routes.APPEARANCE) })
        }

        GroupCard {
            SettingRow("Family", Icons.Outlined.FamilyRestroom, subtitle = when (s.role) {
                "parent" -> "Link and manage your child's phone"
                "child" -> "This phone follows a parent's rules"
                else -> "Set up Savvy for a parent or child"
            }, onClick = { nav.navigate(Routes.FAMILY) })
            RowDivider()
            SettingRow("Diagnostics", Icons.Outlined.BugReport, subtitle = "Status, sync, logs for testing",
                value = if (s.offlineQueue > 0) "${s.offlineQueue} to sync" else null, onClick = { nav.navigate(Routes.DIAGNOSTICS) })
        }
        Text("Savvy ${BuildConfig.VERSION_NAME}", style = SavvyType.caption, color = c.inkFaint,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
    }
}
