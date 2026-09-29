package com.iroid.savvy.rd.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.savvy
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.Routes
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.Callout
import com.iroid.savvy.rd.ui.components.Chip
import com.iroid.savvy.rd.ui.components.GroupCard
import com.iroid.savvy.rd.ui.components.ModalPage
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.PillStyle
import com.iroid.savvy.rd.ui.components.RowDivider
import com.iroid.savvy.rd.ui.components.SavvySwitch
import com.iroid.savvy.rd.ui.components.SectionLabel
import com.iroid.savvy.rd.ui.components.SettingRow
import com.iroid.savvy.rd.ui.theme.Outfit
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val flagText = mapOf(
    "accessibility_off" to "Blocking service off",
    "usage_access_off" to "Usage access off",
    "device_admin_off" to "Device admin off",
    "adb_enabled_on" to "USB debugging on",
    "overlay_off" to "Overlay off",
    "battery_unrestricted_off" to "Battery restricted",
    "nfc_enabled_off" to "NFC off",
    "authorization_not_approved" to "Blocking not allowed",
    "device_not_reporting" to "Not reporting",
    "latest_rules_not_applied" to "Rules not applied yet",
    "advanced_protection_on" to "Advanced Protection on",
)

@Composable
fun FamilyScreen(vm: SavvyViewModel, nav: NavHostController) {
    ModalPage("Family", onBack = { nav.popBackStack() }) {
        when (vm.snapshot.role) {
            "parent" -> ParentFamily(vm, nav)
            "child" -> ChildFamily(vm)
            else -> {
                Callout("Savvy can run on a child's phone with the rules set from a parent's phone. Choose a role and register again in Account.")
                RoleOptions(vm.snapshot.role) { vm.setRole(it) }
                PillButton("Open Account", onClick = { nav.navigate(Routes.ACCOUNT) }, style = PillStyle.SOFT)
            }
        }
    }
}

private data class ChildInfo(val id: Long, val flags: List<String>, val applied: Int, val latest: Int, val todayMin: Int?, val top: List<String>)

@Composable
private fun ParentFamily(vm: SavvyViewModel, nav: NavHostController) {
    val c = savvyColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var children by remember { mutableStateOf<List<ChildInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun load() = scope.launch {
        val r = withContext(Dispatchers.IO) { runCatching { loadChildren(context, vm) } }
        r.onSuccess { children = it; error = null }.onFailure { error = Friendly.text("error: $it"); children = emptyList() }
    }
    LaunchedEffect(Unit) { load() }

    SectionLabel("Link a child's phone")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (code != null) {
            Text(code!!, fontFamily = Outfit, fontSize = 40.sp, letterSpacing = 6.sp, color = c.ink)
            Text("Enter this on the child's phone within 15 minutes", style = SavvyType.caption, color = c.inkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
        } else {
            Text("Create a one-time code, then enter it on your child's phone in Settings › Family.", style = SavvyType.body,
                color = c.inkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
        }
        PillButton(if (code == null) "Create link code" else "New code", onClick = {
            vm.run("Code", quiet = true, onDone = { r -> if (r.startsWith("error")) vm.message = Friendly.text(r) else code = r }) { createLinkCode() }
        }, style = PillStyle.SOLID, busy = vm.busy == "Code", height = 52.dp)
    }

    SectionLabel("Children", trailing = children?.size?.toString())
    when {
        children == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.ink) }
        children!!.isEmpty() -> Callout(error ?: "No child phone linked yet.")
        else -> children!!.forEach { ch -> ChildCard(ch, onRules = { nav.navigate("${Routes.CHILD_RULES}/${ch.id}") }) }
    }
    PillButton("Refresh", onClick = { children = null; load() }, style = PillStyle.SOFT, icon = Icons.Rounded.Sync, height = 50.dp)
}

private fun loadChildren(context: Context, vm: SavvyViewModel): List<ChildInfo> {
    val backend = context.savvy.backend
    return vm.actions.children().map { id ->
        val s = backend.childStatus(id)
        val days = runCatching { backend.childUsage(id).getJSONArray("days") }.getOrNull()
        val today: JSONObject? = if (days != null && days.length() > 0) days.getJSONObject(0) else null
        val flags = s.getJSONArray("flags").let { a -> (0 until a.length()).map(a::getString) }
        ChildInfo(
            id, flags, s.optInt("applied_rule_version"), s.optInt("latest_rule_version"),
            today?.getInt("total_seconds")?.div(60),
            today?.getJSONArray("apps")?.let { a -> (0 until minOf(3, a.length())).map { i -> a.getJSONObject(i).let { "${it.getString("label")} ${it.getInt("seconds") / 60}m" } } } ?: emptyList(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildCard(ch: ChildInfo, onRules: () -> Unit) {
    val c = savvyColors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Child phone #${ch.id}", style = SavvyType.bodyMedium, color = c.ink)
                Text(if (ch.latest == 0) "No rules yet" else "Rules v${ch.applied} of v${ch.latest}", style = SavvyType.caption, color = c.inkSoft)
            }
            Text(ch.todayMin?.let { "${it / 60}h ${it % 60}m today" } ?: "No usage yet", style = SavvyType.label, color = c.ink)
        }
        if (ch.top.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Top: ${ch.top.joinToString(" · ")}", style = SavvyType.caption, color = c.inkSoft)
        }
        if (ch.flags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ch.flags.forEach { f ->
                    Box(Modifier.clip(CircleShape).background(c.dangerSoft).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(flagText[f] ?: f, style = SavvyType.caption, color = c.danger)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton("Set rules", onRules, style = PillStyle.SOLID, height = 48.dp)
    }
}

@Composable
private fun ChildFamily(vm: SavvyViewModel) {
    val c = savvyColors
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    val s = vm.snapshot
    Callout("This phone follows rules set on a parent's phone. Rules arrive when Savvy opens and about every 15 minutes.")
    // Already linked: a second code would only fail (one parent per child phone), so no code entry.
    if (!s.managedByParent) {
        SectionLabel("Link to a parent")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Code", style = SavvyType.body, color = c.inkSoft)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (code.isEmpty()) Text("From the parent's phone", style = SavvyType.body, color = c.inkFaint)
                // Link codes are 8 digits (backend familyService.createLinkCode).
                BasicTextField(code, { v -> code = v.filter { it.isDigit() }.take(8) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    textStyle = SavvyType.bodyMedium.copy(color = c.ink, textAlign = TextAlign.End, letterSpacing = 3.sp),
                    cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth())
            }
        }
        PillButton("Link this phone", onClick = { vm.run("Link") { linkAsChild(code) } }, enabled = code.length == 8, busy = vm.busy == "Link")
    }
    SectionLabel("Status")
    GroupCard {
        SettingRow("Always blocked by parent", value = "${s.parentBlocked.size} app${if (s.parentBlocked.size == 1) "" else "s"}", chevron = false)
        RowDivider()
        SettingRow("Apps in parent's list", value = "${s.selected.size}", chevron = false)
        RowDivider()
        SettingRow("Sync with parent now", onClick = { vm.run("Sync", quiet = true, onDone = { vm.message = "Synced with your parent." }) { housekeeping() } })
        RowDivider()
        SettingRow("Device admin", value = if (vm.tamper?.deviceAdmin == true) "On" else "Off",
            onClick = if (vm.tamper?.deviceAdmin == true) null else ({ context.startActivity(deviceAdminIntent(context)) }))
    }
    if (s.managedByParent) LeaveParentMode(vm)
}

/** Brief section 25: leaving parent mode needs the parent's PIN (hash sent in the rules, checked offline). */
@Composable
private fun LeaveParentMode(vm: SavvyViewModel) {
    val c = savvyColors
    var pin by remember { mutableStateOf("") }
    SectionLabel("Leave parent mode")
    if (!vm.snapshot.hasLeavePin) {
        Callout("Only your parent can turn parent mode off. They set a PIN for this in their Savvy app under your phone's rules.")
        return
    }
    PinField(pin, "Parent's PIN") { pin = it }
    PillButton("Leave parent mode", onClick = {
        // Read the PIN now: the block runs later on a background thread, after the field is cleared.
        val entered = pin
        vm.run("Leave") { leaveParentMode(entered) }; pin = ""
    },
        style = PillStyle.DANGER, enabled = com.iroid.savvy.core.ParentPin.valid(pin), busy = vm.busy == "Leave", height = 52.dp)
    Text("Stops your parent's always-on blocks and settings protection. A focus session already running continues until it ends.",
        style = SavvyType.caption, color = c.inkSoft, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun PinField(pin: String, placeholder: String, onChange: (String) -> Unit) {
    val c = savvyColors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("PIN", style = SavvyType.body, color = c.inkSoft)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (pin.isEmpty()) Text(placeholder, style = SavvyType.body, color = c.inkFaint)
            BasicTextField(pin, { v -> onChange(v.filter { it.isDigit() }.take(8)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                textStyle = SavvyType.bodyMedium.copy(color = c.ink, textAlign = TextAlign.End, letterSpacing = 4.sp),
                cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Parent phone: the child's reported app list (package names), a focus length, and the always-on switch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildRulesScreen(vm: SavvyViewModel, childId: Long, onBack: () -> Unit) {
    val c = savvyColors
    val context = LocalContext.current
    // No GET endpoint for a child's current rules: prefill from what this phone sent last time.
    val last = remember(childId) { vm.prefs.lastRules(childId) }
    val lastFocus = last?.optJSONObject("focus")
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var selected by remember { mutableStateOf(last?.optJSONArray("packages")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet()) }
    var minutes by remember { mutableIntStateOf(if (last == null) 120 else lastFocus?.optInt("duration_minutes") ?: 0) }
    var alwaysOn by remember { mutableStateOf(last?.optBoolean("always_on") ?: false) }
    var mode by remember { mutableStateOf(lastFocus?.optString("mode")?.let { m -> Mode.entries.firstOrNull { it.name.equals(m, true) } } ?: Mode.STUDY) }
    var policy by remember { mutableStateOf(lastFocus?.optString("unlock_policy")?.let { p -> UnlockPolicy.entries.firstOrNull { it.wire == p } } ?: UnlockPolicy.CARD_REQUIRED) }
    var pin by remember { mutableStateOf("") }
    val oldPinHash = last?.optString("leave_pin_sha256")?.ifEmpty { null }
    LaunchedEffect(childId) {
        apps = withContext(Dispatchers.IO) {
            runCatching {
                val a = context.savvy.backend.childInventory(childId).getJSONArray("apps")
                (0 until a.length()).map { i -> a.getJSONObject(i).let { AppEntry(it.getString("package"), it.getString("label")) } }.sortedBy { it.label.lowercase() }
            }.getOrElse { vm.message = "Couldn't load the child's apps."; emptyList() }
        }
    }
    AppChecklist(
        title = "Child's rules", apps = apps, selected = selected, showIcons = false,
        onToggle = { p -> selected = if (p in selected) selected - p else selected + p },
        bottomLabel = "Send rules", bottomBusy = vm.busy == "Rules",
        onBottom = {
            if (pin.isNotEmpty() && !com.iroid.savvy.core.ParentPin.valid(pin)) { vm.message = "Use 4 to 8 digits for the PIN."; return@AppChecklist }
            val pinHash = if (pin.isNotEmpty()) com.iroid.savvy.core.ParentPin.hash(childId, pin) else oldPinHash
            vm.run("Rules", onDone = { r ->
                if (r.endsWith("sent")) {
                    vm.prefs.saveRules(childId, JSONObject().put("packages", org.json.JSONArray(selected.toList())).put("always_on", alwaysOn)
                        .put("focus", if (minutes > 0) JSONObject().put("mode", mode.name.lowercase()).put("duration_minutes", minutes).put("unlock_policy", policy.wire) else JSONObject.NULL)
                        .putOpt("leave_pin_sha256", pinHash))
                    onBack()
                }
            }) {
                sendRules(childId, selected, minutes.takeIf { it > 0 }, policy, alwaysOn, mode, pinHash)
            }
        },
        onBack = onBack,
        header = {
            SectionLabel("Focus session")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 30, 60, 120, 240, 360, 1440).forEach { m -> Chip(if (m == 0) "None" else Friendly.duration(m), minutes == m, onClick = { minutes = m }) }
            }
            if (minutes > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Mode.STUDY, Mode.WORK, Mode.SLEEP).forEach { m -> Chip(Friendly.mode(m), mode == m, onClick = { mode = m }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UnlockPolicy.CARD_REQUIRED, UnlockPolicy.LOCKED, UnlockPolicy.FREE).forEach { p ->
                        Chip(Friendly.policy(p), policy == p, onClick = { policy = p })
                    }
                }
                Text(Friendly.policyLong(policy), style = SavvyType.caption, color = c.inkSoft, modifier = Modifier.padding(start = 4.dp))
            }
            GroupCard {
                SettingRow("Always-on block", subtitle = "Blocked all the time, not only during focus", chevron = false) {
                    SavvySwitch(alwaysOn) { alwaysOn = it }
                }
            }
            SectionLabel("PIN to leave parent mode", trailing = if (oldPinHash != null) "Set" else "Not set")
            PinField(pin, if (oldPinHash != null) "Keep current PIN" else "4 to 8 digits") { pin = it }
            if (pin.isNotEmpty() && !com.iroid.savvy.core.ParentPin.valid(pin)) Text("Use 4 to 8 digits.", style = SavvyType.caption, color = c.danger)
            Text("Apps on #$childId", style = SavvyType.caption, color = c.inkSoft, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        },
    )
}
