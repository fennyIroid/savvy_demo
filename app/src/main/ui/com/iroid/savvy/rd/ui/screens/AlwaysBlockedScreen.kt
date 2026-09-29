package com.iroid.savvy.rd.ui.screens

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.iroid.savvy.core.RestrictionPolicy
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.Callout
import com.iroid.savvy.rd.ui.openCardScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The user's own always-blocked apps: blocked all the time, focus session or not.
 * Ticking an app blocks it at once; unticking opens the card screen, and only the
 * Savvy card (the same one that ends a focus session early) unblocks it.
 */
@Composable
fun AlwaysBlockedScreen(vm: SavvyViewModel, onLinkCard: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var confirm by remember { mutableStateOf<AppEntry?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }
                .filter { it.pkg != context.packageName && it.pkg !in RestrictionPolicy.ALWAYS_ALLOWED }
                .sortedBy { it.label.lowercase() }
        }
    }
    val s = vm.snapshot
    val managed = s.managedByParent
    AppChecklist(
        title = "Always blocked", apps = apps, selected = s.selfBlocked,
        tags = s.selected.associateWith { "Blocked during focus sessions" } + s.selfBlocked.associateWith { "Always blocked" },
        note = if (managed) "Your parent manages this phone, so you can't change always blocked apps."
            else "These apps stay blocked even when no focus session is running. Tap a blocked app and hold your Savvy card to unblock it.",
        header = if (!managed && s.boundCard == null) ({
            Callout("No Savvy card is linked yet. Link one so you can unblock these apps later.", action = "Link card", onAction = onLinkCard)
        }) else null,
        onToggle = { pkg ->
            when {
                managed -> Unit
                pkg in s.selfBlocked -> context.openCardScreen(BlockActivity.MODE_UNBLOCK_APP, blockedPackage = pkg)
                else -> confirm = apps?.firstOrNull { it.pkg == pkg }
            }
        },
        bottomLabel = "Done", onBottom = onDone, onBack = onDone,
    )

    confirm?.let { app ->
        ConfirmDialog(
            title = "Always block ${app.label}?",
            body = "It stays blocked even when no focus session is running. Only your Savvy card can unblock it." +
                if (s.boundCard == null) " No card is linked yet, so link one before you need to unblock it." else "",
            confirm = "Block", dismiss = "Cancel",
            onConfirm = { confirm = null; vm.blockAlways(app.pkg) },
            onDismiss = { confirm = null },
        )
    }
}
