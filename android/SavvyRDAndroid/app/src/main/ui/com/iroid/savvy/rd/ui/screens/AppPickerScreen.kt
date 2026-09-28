package com.iroid.savvy.rd.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.AppIcon
import com.iroid.savvy.rd.ui.components.Callout
import com.iroid.savvy.rd.ui.components.ModalPage
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.SectionLabel
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String)

/**
 * Android R&D 1. Launchable apps through the <queries> launcher intent (no
 * QUERY_ALL_PACKAGES). Selection is saved on every change, as before.
 */
@Composable
fun AppPickerScreen(vm: SavvyViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var selected by remember { mutableStateOf(vm.snapshot.selected) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }.filter { it.pkg != context.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    AppChecklist(
        title = "Blocked apps", apps = apps, selected = selected,
        note = if (vm.snapshot.active) "Changes apply from your next session." else null,
        onToggle = { pkg -> selected = if (pkg in selected) selected - pkg else selected + pkg; vm.setSelected(selected) },
        bottomLabel = "Done", onBottom = onDone, onBack = onDone,
    )
}

/** Searchable app list with round checks; shared by the self picker and the parent's child picker. */
@Composable
fun AppChecklist(
    title: String,
    apps: List<AppEntry>?,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    bottomLabel: String,
    onBottom: () -> Unit,
    onBack: () -> Unit,
    note: String? = null,
    bottomBusy: Boolean = false,
    showIcons: Boolean = true,
    header: (@Composable () -> Unit)? = null,
) {
    val c = savvyColors
    var query by remember { mutableStateOf("") }
    ModalPage(title, onBack = onBack, scroll = false, bottom = {
        PillButton(if (selected.isEmpty()) bottomLabel else "$bottomLabel · ${selected.size} selected", onBottom, busy = bottomBusy)
    }) {
        val shown = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
        val (on, off) = shown.partition { it.pkg in selected }
        // Lazy: a phone can have hundreds of launchable apps.
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    header?.invoke()
                    if (note != null) Callout(note)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = c.inkSoft, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search apps", style = SavvyType.body, color = c.inkSoft)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = SavvyType.body.copy(color = c.ink),
                                cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (apps == null) Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.ink) }
                }
            }
            if (on.isNotEmpty()) {
                item(key = "selHeader") { SectionLabel("Selected", Modifier.padding(top = 12.dp, bottom = 8.dp)) }
                appRows(on, selected, onToggle, showIcons, "s")
            }
            if (off.isNotEmpty()) {
                item(key = "allHeader") { SectionLabel(if (on.isEmpty()) "All apps" else "Other apps", Modifier.padding(top = 12.dp, bottom = 8.dp), trailing = "${off.size}") }
                appRows(off, selected, onToggle, showIcons, "o")
            }
        }
    }
}

private fun LazyListScope.appRows(list: List<AppEntry>, selected: Set<String>, onToggle: (String) -> Unit, showIcons: Boolean, prefix: String) {
    itemsIndexed(list, key = { _, a -> prefix + a.pkg }) { i, app ->
        val c = savvyColors
        val on = app.pkg in selected
        val top = if (i == 0) 28.dp else 0.dp
        val bottom = if (i == list.lastIndex) 28.dp else 0.dp
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, bottom, bottom)).background(c.card)
                .clickable { onToggle(app.pkg) }
                .padding(start = 18.dp, end = 18.dp, top = if (i == 0) 16.dp else 10.dp, bottom = if (i == list.lastIndex) 16.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcons) { AppIcon(app.pkg, size = 38.dp); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(app.label, style = SavvyType.bodyMedium, color = c.ink)
                Text(app.pkg, style = SavvyType.caption, color = c.inkFaint, maxLines = 1)
            }
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (on) c.primary else c.card)
                    .border(1.5.dp, if (on) c.primary else c.inkFaint, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Rounded.Check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp)) }
        }
    }
}
