package com.iroid.savvy.rd.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contactless
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.Routes
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.TaskItem
import com.iroid.savvy.rd.ui.components.Callout
import com.iroid.savvy.rd.ui.components.Chip
import com.iroid.savvy.rd.ui.components.EmptyState
import com.iroid.savvy.rd.ui.components.ModalPage
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.PillStyle
import com.iroid.savvy.rd.ui.components.SectionLabel
import com.iroid.savvy.rd.ui.openCardScreen
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors

/** R&D 12, to-do restriction: a task locks the chosen apps until it is done (with the card). */
@Composable
fun TasksTab(vm: SavvyViewModel, nav: NavHostController) {
    val c = savvyColors
    val context = LocalContext.current
    val s = vm.snapshot
    val tasks = s.tasks.sortedBy { when (it.status) { "active" -> 0; "pending" -> 1; else -> 2 } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tasks", style = SavvyType.headline, color = c.ink)
                Text("Apps stay blocked until the task is done", style = SavvyType.caption, color = c.inkSoft)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.primary).clickable { nav.navigate(Routes.ADD_TASK) },
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "Add task", tint = c.onPrimary) }
        }
        if (tasks.isEmpty()) {
            Spacer(Modifier.weight(1f))
            EmptyState("No tasks yet", "Add something you need to get done. Savvy keeps your distracting apps closed until you finish it.")
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                PillButton("Add a task", onClick = { nav.navigate(Routes.ADD_TASK) }, style = PillStyle.OUTLINE)
            }
            Spacer(Modifier.weight(1.3f))
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tasks, key = { it.id }) { t ->
                TaskCard(t, busy = vm.busy != null,
                    onStart = {
                        if (s.selected.isEmpty()) nav.navigate(Routes.APPS) else vm.startTask(t)
                    },
                    onDone = {
                        val cm = s.commitment
                        if (cm?.taskRef == t.id && cm.unlockPolicy == UnlockPolicy.CARD_REQUIRED) {
                            // Card-protected task: finishing it needs the card too (backend rule, OPEN_ITEMS C3).
                            context.openCardScreen(BlockActivity.MODE_TASK, t.id)
                        } else vm.run("Finishing") { completeTask(t.id) }
                    },
                    onDelete = { vm.deleteTask(t.id) },
                    otherActive = s.active && s.commitment?.taskRef != t.id,
                )
            }
        }
    }
}

@Composable
private fun TaskCard(t: TaskItem, busy: Boolean, onStart: () -> Unit, onDone: () -> Unit, onDelete: () -> Unit, otherActive: Boolean) {
    val c = savvyColors
    val done = t.status == "completed"
    val active = t.status == "active"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (active) c.icySoft else c.card).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (done) c.success else c.cardStrong),
                contentAlignment = Alignment.Center,
            ) { if (done) Icon(Icons.Rounded.Check, null, tint = c.sheet, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = SavvyType.bodyMedium, color = if (done) c.inkSoft else c.ink,
                    textDecoration = if (done) TextDecoration.LineThrough else null)
                Text(
                    when (t.status) { "active" -> "In progress · ${Friendly.duration(t.minutes)}"; "completed" -> "Done"; else -> "${Friendly.duration(t.minutes)} · card to finish" },
                    style = SavvyType.caption, color = c.inkSoft,
                )
            }
            if (!active) Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, "Delete", tint = c.inkFaint, modifier = Modifier.size(18.dp))
            }
        }
        if (!done) {
            Spacer(Modifier.height(14.dp))
            if (active) PillButton("I'm done", onDone, style = PillStyle.SOLID, icon = Icons.Rounded.Contactless, height = 50.dp, enabled = !busy)
            else PillButton(if (otherActive) "Another session is running" else "Start task", onStart, style = PillStyle.SOFT,
                height = 50.dp, enabled = !busy && !otherActive)
        }
    }
}

private val taskPresets = listOf(15, 25, 45, 60, 90, 120)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskScreen(vm: SavvyViewModel, onDone: () -> Unit) {
    val c = savvyColors
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(45) }
    ModalPage("Add task", onBack = onDone, bottom = {
        PillButton("Save task", onClick = { vm.addTask(title.trim().ifBlank { "Task" }, minutes); vm.message = "Task added."; onDone() })
    }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Name", style = SavvyType.body, color = c.inkSoft)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (title.isEmpty()) Text("Essay, revision, tax return", style = SavvyType.body, color = c.inkFaint)
                BasicTextField(title, { title = it }, singleLine = true,
                    textStyle = SavvyType.bodyMedium.copy(color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                    cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth())
            }
        }
        SectionLabel("Time box", trailing = Friendly.duration(minutes))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            taskPresets.forEach { m -> Chip(Friendly.duration(m), minutes == m, onClick = { minutes = m }) }
        }
        Spacer(Modifier.height(4.dp))
        Callout("When you start this task your blocked apps close. To finish it, tap \"I'm done\" and hold your Savvy card to your phone.",
            icon = Icons.Rounded.Contactless)
    }
}
