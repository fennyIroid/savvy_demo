package com.iroid.savvy.rd.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.iroid.savvy.rd.BuildConfig
import com.iroid.savvy.rd.R
import com.iroid.savvy.rd.admin.SavvyDeviceAdminReceiver
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.Callout
import com.iroid.savvy.rd.ui.components.CardLook
import com.iroid.savvy.rd.ui.components.GroupCard
import com.iroid.savvy.rd.ui.components.ModalPage
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.PillStyle
import com.iroid.savvy.rd.ui.components.RowDivider
import com.iroid.savvy.rd.ui.components.SavvyCardHero
import com.iroid.savvy.rd.ui.components.SectionLabel
import com.iroid.savvy.rd.ui.components.SettingRow
import com.iroid.savvy.rd.ui.openCardScreen
import com.iroid.savvy.rd.ui.theme.Appearance
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors
import com.iroid.savvy.rd.service.TamperMonitor
import com.iroid.savvy.core.TimeIntegrity
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.zxing.BarcodeFormat
import com.iroid.savvy.rd.savvy

// ---- Savvy card -----------------------------------------------------------------

@Composable
fun CardScreen(vm: SavvyViewModel, onBack: () -> Unit) {
    val c = savvyColors
    val context = LocalContext.current
    val s = vm.snapshot
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose { } }
    ModalPage("Savvy card", onBack = onBack, bottom = {
        PillButton(if (s.boundCard == null) "Link my card" else "Link a different card", onClick = {
            context.openCardScreen(BlockActivity.MODE_REGISTER)
        }, icon = Icons.Rounded.Contactless)
        if (BuildConfig.DEBUG) PillButton("Create a test card", onClick = { vm.run("Test card") { devCard() } },
            style = PillStyle.SOFT, icon = Icons.Rounded.Science, busy = vm.busy == "Test card")
        val testUrl = s.devCardUrl
        if (BuildConfig.DEBUG && testUrl != null) PillButton("Write test card to an NFC tag", onClick = {
            context.openCardScreen(BlockActivity.MODE_WRITE, url = testUrl)
        }, style = PillStyle.SOFT, icon = Icons.Rounded.Nfc)
    }) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SavvyCardHero(if (s.boundCard != null) CardLook.ACTIVE else CardLook.IDLE, width = 230.dp)
        }
        Text(
            if (s.boundCard != null) "Card ${s.boundCard} is linked" else "No card linked yet",
            style = SavvyType.title, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Your card is the key. Hold it to the back of your phone, near the camera, to end a session early or finish a task. " +
                "You can also scan the QR code printed on it.",
            style = SavvyType.body, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        val url = s.devCardUrl
        if (BuildConfig.DEBUG && url != null) {
            SectionLabel("Test card (debug build)")
            val qr = remember(url) { runCatching { BarcodeEncoder().encodeBitmap(url, BarcodeFormat.QR_CODE, 600, 600).asImageBitmap() }.getOrNull() }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(10.dp)) {
                    Image(qr, "Test card QR", Modifier.size(180.dp))
                }
                Spacer(Modifier.height(12.dp))
                SelectionContainer { Text(url, style = SavvyType.caption, color = c.inkSoft, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(8.dp))
                Text("Scan this from another phone's card screen, or write it to a blank NTAG213/215/216 tag with the button below.", style = SavvyType.caption, color = c.inkFaint,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

// ---- Permissions -------------------------------------------------------------------

@Composable
fun PermissionsScreen(vm: SavvyViewModel, onBack: () -> Unit) {
    ModalPage("Permissions", onBack = onBack) {
        Callout("Savvy needs these to notice when you open a blocked app and to keep your session running in the background.")
        PermissionList(vm)
    }
}

/** The checklist, also used in onboarding. */
@Composable
fun PermissionList(vm: SavvyViewModel, compact: Boolean = false) {
    val c = savvyColors
    val context = LocalContext.current
    val t = vm.tamper
    var disclosure by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshSlow() }
    LifecycleResumeEffect(Unit) { vm.refreshSlow(); onPauseOrDispose { } }
    LaunchedEffect(Unit) { while (true) { delay(2000); vm.refreshSlow() } }
    val notificationsOn = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    GroupCard {
        PermissionRow(Icons.Outlined.TouchApp, "Detect blocked apps", "Accessibility: sees which app is open, never what's on screen", t?.accessibility) {
            disclosure = true
        }
        RowDivider()
        PermissionRow(Icons.Outlined.QueryStats, "Usage access", "Backup detection and your screen-time summary", t?.usageAccess) {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        RowDivider()
        PermissionRow(Icons.Outlined.Layers, "Display over other apps", "Lets the block screen appear on top", t?.overlay) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        }
        RowDivider()
        PermissionRow(Icons.Outlined.BatteryChargingFull, "Run in background", "Battery: unrestricted, so sessions aren't stopped", t?.ignoringBatteryOptimizations) {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        val oem = remember { com.iroid.savvy.rd.data.OemBackground.step() }
        if (oem != null) {
            var opened by remember { mutableStateOf(vm.prefs.oemStepOpened) }
            RowDivider()
            // Brand screens are not readable back, so "on" here means the user opened it.
            PermissionRow(Icons.Outlined.PhonelinkSetup, "${oem.brand}: ${oem.title}", oem.instructions, opened) {
                com.iroid.savvy.rd.data.OemBackground.open(context, oem)
                vm.prefs.oemStepOpened = true; opened = true
            }
        }
        RowDivider()
        PermissionRow(Icons.Outlined.Notifications, "Notifications", "Shows when a session is on", notificationsOn) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!compact) {
            RowDivider()
            PermissionRow(Icons.Outlined.Contactless, "NFC", "Reads your Savvy card", t?.nfcEnabled ?: false, unavailable = t != null && t.nfcEnabled == null) {
                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
        }
        if (vm.snapshot.role == "child") {
            RowDivider()
            PermissionRow(Icons.Outlined.AdminPanelSettings, "Device admin", "Stops Savvy being removed without your parent", t?.deviceAdmin) {
                context.startActivity(deviceAdminIntent(context))
            }
            if (t?.adbEnabled == true) {
                RowDivider()
                SettingRow("USB debugging is on", Icons.Outlined.Usb, subtitle = "Your parent will see this", chevron = false, titleColor = c.danger)
            }
        }
    }
    if (disclosure) ConfirmDialog(
        title = "Allow Savvy to detect the app on screen?",
        body = context.getString(R.string.prominent_disclosure),
        confirm = "Agree", dismiss = "No thanks",
        // Play policy: prominent disclosure and affirmative consent BEFORE opening Accessibility settings.
        onConfirm = { disclosure = false; context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        onDismiss = { disclosure = false },
    )
}

fun deviceAdminIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, SavvyDeviceAdminReceiver::class.java))
    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets your parent's Savvy rules stay on this phone.")

@Composable
private fun PermissionRow(icon: ImageVector, title: String, subtitle: String, on: Boolean?, unavailable: Boolean = false, onClick: () -> Unit) {
    val c = savvyColors
    SettingRow(title, icon, subtitle = subtitle, chevron = false, onClick = if (on == true || unavailable) null else onClick) {
        Spacer(Modifier.width(10.dp))
        when {
            unavailable -> Text("No NFC", style = SavvyType.label, color = c.inkFaint)
            on == true -> Box(Modifier.size(26.dp).clip(CircleShape).background(c.success), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "On", tint = c.sheet, modifier = Modifier.size(16.dp))
            }
            else -> Box(Modifier.clip(CircleShape).background(c.primary).padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text("Turn on", style = SavvyType.label, color = c.onPrimary)
            }
        }
    }
}

// ---- Account -----------------------------------------------------------------------

@Composable
fun AccountScreen(vm: SavvyViewModel, onBack: () -> Unit) {
    val c = savvyColors
    val s = vm.snapshot
    var email by remember { mutableStateOf(vm.prefs.email.ifBlank { "tester@savvy.test" }) }
    ModalPage("Account", onBack = onBack, bottom = {
        PillButton(if (s.registered) "Register again" else "Create account", busy = vm.busy == "Register", enabled = !s.managedByParent, onClick = {
            vm.prefs.email = email.trim()
            vm.run("Register") { register(email.trim()) }
        })
    }) {
        EmailField(email) { email = it }
        SectionLabel("Who uses this phone?")
        if (s.managedByParent) {
            // Brief section 25: a child must not switch to "Just me" and drop the parent guard.
            Callout("Your parent manages this phone, so the account and role are locked. To leave parent mode, ask your parent for their PIN and use Settings › Family.",
                icon = Icons.Outlined.ChildCare)
        } else {
            RoleOptions(s.role) { vm.setRole(it) }
            Callout("Changing who uses this phone takes effect after you register again.")
        }
        if (s.registered) {
            GroupCard {
                SettingRow("Device", value = "#${s.deviceId}", chevron = false)
                RowDivider()
                SettingRow("Control", value = if (s.controlMode.name == "PARENT") "Parent" else "Self", chevron = false)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Server: ${BuildConfig.BACKEND_URL}", style = SavvyType.caption, color = c.inkFaint, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun EmailField(email: String, onChange: (String) -> Unit) {
    val c = savvyColors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Email", style = SavvyType.body, color = c.inkSoft)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (email.isEmpty()) Text("you@example.com", style = SavvyType.body, color = c.inkFaint)
            BasicTextField(email, onChange, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                textStyle = SavvyType.bodyMedium.copy(color = c.ink, textAlign = TextAlign.End), cursorBrush = SolidColor(c.ink),
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun RoleOptions(role: String, onSelect: (String) -> Unit) {
    PolicyOption(Icons.Outlined.Person, "Just me", "Block my own distractions", role == "self") { onSelect("self") }
    PolicyOption(Icons.Outlined.FamilyRestroom, "Parent's phone", "Manage a child's phone from here", role == "parent") { onSelect("parent") }
    PolicyOption(Icons.Outlined.ChildCare, "Child's phone", "A parent sets the rules on this phone", role == "child") { onSelect("child") }
}

// ---- Appearance --------------------------------------------------------------------

@Composable
fun AppearanceScreen(vm: SavvyViewModel, onBack: () -> Unit) {
    ModalPage("Appearance", onBack = onBack) {
        PolicyOption(Icons.Outlined.BrightnessAuto, "Automatic", "Follow the phone's light or dark setting", vm.appearance == Appearance.AUTO) { vm.changeAppearance(Appearance.AUTO) }
        PolicyOption(Icons.Outlined.LightMode, "Light", "Snow and icy blue", vm.appearance == Appearance.LIGHT) { vm.changeAppearance(Appearance.LIGHT) }
        PolicyOption(Icons.Outlined.DarkMode, "Dark", "Deep navy", vm.appearance == Appearance.DARK) { vm.changeAppearance(Appearance.DARK) }
    }
}

// ---- Emergency exit ----------------------------------------------------------------

@Composable
fun EmergencyScreen(vm: SavvyViewModel, onBack: () -> Unit) {
    val c = savvyColors
    val s = vm.snapshot
    val u = vm.emergencyUsage
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadEmergencyUsage() }
    // Backend cooling-off: the request is recorded; calling again after the wait confirms it.
    val pendingAt = s.emergencyAvailableAtMs
    val waiting = pendingAt != null && System.currentTimeMillis() < pendingAt
    ModalPage("Emergency exit", onBack = onBack, bottom = {
        PillButton(
            when {
                waiting -> "Available at ${Friendly.time(pendingAt!!)}"
                pendingAt != null -> "Confirm emergency exit"
                else -> "Use emergency exit"
            },
            onClick = { if (pendingAt != null) vm.run("Emergency", onDone = { vm.loadEmergencyUsage(); if (vm.snapshot.commitment == null) onBack() }) { emergency("app") } else confirm = true },
            style = PillStyle.DANGER, enabled = s.active && !waiting && (u == null || u.left > 0 || pendingAt != null),
            busy = vm.busy == "Emergency")
    }) {
        Text("For real emergencies only", style = SavvyType.title, color = c.ink)
        Text(
            "The emergency exit ends any session early, even a locked one. The weekly allowance keeps it special, and every use is recorded.",
            style = SavvyType.body, color = c.inkSoft,
        )
        if (pendingAt != null) Callout(if (waiting) "Requested. For a real emergency, wait until ${Friendly.time(pendingAt)} and confirm." else "Your wait is over. Confirm to end the session.")
        GroupCard {
            SettingRow("Allowance", value = u?.let { "${it.left} of ${it.limit} left · ${it.windowDays} days" } ?: "2 per 7 days", chevron = false)
            RowDivider()
            SettingRow("Used offline this week", value = "${s.emergencyUsedLocal}", chevron = false)
            RowDivider()
            SettingRow("Session", value = if (s.active) "${Friendly.mode(s.commitment!!.mode)} · ${Friendly.clock(s.remainingMs)} left" else "None running", chevron = false)
        }
        if (!s.active) Callout("There's no session to exit right now.")
    }
    if (confirm) ConfirmDialog(
        title = "End this session now?",
        body = "This uses one of your emergency exits for this week.",
        confirm = "End session", dismiss = "Keep going", destructive = true,
        onConfirm = { confirm = false; vm.run("Emergency", onDone = { vm.loadEmergencyUsage(); if (vm.snapshot.commitment == null) onBack() }) { emergency("app") } },
        onDismiss = { confirm = false },
    )
}

// ---- Diagnostics (the old test lab's tools) ----------------------------------------

@Composable
fun DiagnosticsScreen(vm: SavvyViewModel, onBack: () -> Unit) {
    val c = savvyColors
    val context = LocalContext.current
    var output by remember { mutableStateOf<String?>(null) }
    fun show(label: String, block: com.iroid.savvy.rd.SavvyActions.() -> String) = vm.run(label, quiet = true, onDone = { output = it }, block = block)

    ModalPage("Diagnostics", onBack = onBack) {
        Callout("Tools for device testing. Results match docs/ANDROID_POC_RESULTS.md.")
        // Result first, so it is visible without scrolling.
        if (vm.busy != null) Text("${vm.busy}…", style = SavvyType.caption, color = c.inkSoft)
        output?.let {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).horizontalScroll(rememberScrollState()).padding(16.dp)) {
                SelectionContainer { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = c.ink) }
            }
        }
        GroupCard {
            SettingRow("Status and heartbeat", onClick = {
                show("Status") {
                    val repo = context.savvy.repo
                    val cm = repo.commitment
                    val now = repo.now()
                    "${housekeeping()}\n${TamperMonitor.read(context)}\ncommitment=${cm?.let { "${it.mode} ${it.unlockPolicy} " +
                        "remaining=${repo.remainingMs(it, now) / 60000}min verdict=${TimeIntegrity.check(it.anchor, now)}\n" +
                        "checkpoint=${repo.checkpointFor(it)} bootWall=${repo.bootWallMs(now)} serverNow(trusted)=${repo.trustedServerNow(now)}" }}" +
                        "\nparent always-on=${repo.parentBlockedPackages.size} offline queue=${repo.offlineQueue().length()}"
                }
            })
            RowDivider()
            SettingRow("Sync offline events", value = "${vm.snapshot.offlineQueue} queued", onClick = { show("Sync") { flushOffline() } })
            RowDivider()
            SettingRow("Sync rules, apps and usage", onClick = { show("Housekeeping") { housekeeping() } })
            RowDivider()
            SettingRow("Restore from server", onClick = { show("Restore") { restore() } })
            RowDivider()
            SettingRow("Screen time today (text)", onClick = { show("Screen") { screenTimeToday() } })
            RowDivider()
            SettingRow("Focus time and streak (text)", onClick = { show("Insights") { insights() } })
        }
        GroupCard {
            // Approach F, R&D only: inert unless provisioned with adb (see DeviceOwnerController).
            val isDo = com.iroid.savvy.rd.admin.DeviceOwnerController.isDeviceOwner(context)
            SettingRow("Device Owner (R&D)", value = if (isDo) "On" else "Off",
                onClick = { output = com.iroid.savvy.rd.admin.DeviceOwnerController.describe(context) })
            if (isDo) {
                RowDivider()
                SettingRow("Remove Device Owner", titleColor = c.danger,
                    onClick = { output = com.iroid.savvy.rd.admin.DeviceOwnerController.removeDeviceOwner(context) })
            }
        }
        GroupCard {
            SettingRow("Open card screen", onClick = { context.openCardScreen() })
            RowDivider()
            SettingRow("Show log", onClick = { output = SavvyLog.read().takeLast(6000) })
            RowDivider()
            SettingRow("Share log (test evidence)", onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, SavvyLog.read().takeLast(90_000)), "Savvy R&D log"))
            })
        }
    }
}
