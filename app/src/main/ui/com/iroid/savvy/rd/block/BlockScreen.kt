package com.iroid.savvy.rd.block

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iroid.savvy.core.TimeIntegrity
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.savvy
import com.iroid.savvy.rd.ui.Friendly
import com.iroid.savvy.rd.ui.UiPrefs
import com.iroid.savvy.rd.ui.components.AppIcon
import com.iroid.savvy.rd.ui.components.CardLook
import com.iroid.savvy.rd.ui.components.CurvedSheetFrame
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.PillStyle
import com.iroid.savvy.rd.ui.components.SavvyCardHero
import com.iroid.savvy.rd.ui.components.TopPill
import com.iroid.savvy.rd.ui.components.appLabel
import com.iroid.savvy.rd.ui.screens.ConfirmDialog
import com.iroid.savvy.rd.ui.theme.SavvyTheme
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors
import kotlinx.coroutines.delay

/** Compose view of [BlockActivity]. The activity owns the logic and pushes [BlockUiState] here. */
class BlockScreen(private val activity: BlockActivity) {
    private val state = mutableStateOf(BlockUiState())
    private var attached = false

    fun render(s: BlockUiState) {
        state.value = s
        if (attached) return
        attached = true
        activity.enableEdgeToEdge()
        val appearance = UiPrefs(activity).appearance
        activity.setContent { SavvyTheme(appearance) { BlockContent(state.value, activity) } }
    }

    /** Let the success state show for a moment, then close. */
    fun onSuccess(done: () -> Unit) { Handler(Looper.getMainLooper()).postDelayed(done, 900) }
}

@Composable
private fun BlockContent(s: BlockUiState, a: BlockActivity) {
    val c = savvyColors
    val context = LocalContext.current
    val repo = context.savvy.repo
    val commitment = repo.commitment
    var confirmEmergency by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val unlockMode = s.mode == BlockActivity.MODE_UNLOCK

    val look = when (s.phase) {
        BlockUiState.Phase.SUCCESS -> CardLook.SUCCESS
        BlockUiState.Phase.FAILED -> CardLook.ERROR
        BlockUiState.Phase.CHECKING -> CardLook.ACTIVE
        BlockUiState.Phase.WAITING -> if (s.nfc == "ready") CardLook.LISTENING else CardLook.IDLE
    }

    CurvedSheetFrame(bottomBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            if (unlockMode && commitment != null && !s.tamper) BottomLink("Emergency exit") { confirmEmergency = true } else Spacer(Modifier.width(1.dp))
            BottomLink(if (unlockMode) "Go home" else "Cancel", strong = true) { a.goHome() }
        }
    }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            // What is blocked and for how long.
            if (unlockMode && commitment != null && !s.tamper) {
                val left = tick.let { TimeIntegrity.remainingMs(commitment, repo.now()) }
                TopPill {
                    Text(Friendly.mode(commitment.mode), style = SavvyType.label, color = c.ink)
                    Text("  ·  ${Friendly.clock(left)} left", style = SavvyType.label, color = c.inkSoft)
                }
            }
            Spacer(Modifier.weight(0.6f))
            when {
                s.tamper -> Header(icon = { Icon(Icons.Rounded.Shield, null, tint = c.ink, modifier = Modifier.size(34.dp)) },
                    title = "This setting is locked", body = "Your parent's Savvy rules protect this screen.")
                s.mode == BlockActivity.MODE_REGISTER -> Header(title = "Link your Savvy card", body = "Hold your card to the back of your phone.")
                s.mode == BlockActivity.MODE_TASK -> Header(title = "Finish your task", body = "Hold your Savvy card to the back of your phone to mark it done.")
                else -> {
                    val pkg = s.blockedPackage
                    Header(
                        icon = if (pkg != null) ({ AppIcon(pkg, size = 56.dp) }) else null,
                        title = "${pkg?.let { appLabel(context, it) } ?: "This app"} is " +
                            if (s.phase == BlockUiState.Phase.SUCCESS) "unlocked" else "paused",
                        body = when {
                            commitment == null && repo.parentBlockedPackages.isNotEmpty() -> "Your parent has blocked this app."
                            commitment?.unlockPolicy == UnlockPolicy.LOCKED -> "This session is locked until the timer runs out."
                            commitment?.unlockPolicy == UnlockPolicy.FREE -> "You chose to take a break from it. End the session in Savvy if you need it."
                            else -> "Hold your Savvy card to the back of your phone to unlock."
                        },
                    )
                }
            }
            Spacer(Modifier.weight(0.4f))
            SavvyCardHero(look, width = 230.dp)
            Spacer(Modifier.height(4.dp))

            AnimatedContent(s.phase to s.message, label = "status") { (phase, msg) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val text = when (phase) {
                        BlockUiState.Phase.CHECKING -> "Checking your card…"
                        BlockUiState.Phase.SUCCESS -> when (s.mode) {
                            BlockActivity.MODE_REGISTER -> "Card linked"
                            BlockActivity.MODE_TASK -> "Task done"
                            else -> if (msg?.contains("Paused") == true) "Unlocked for a short break" else "Unlocked"
                        }
                        BlockUiState.Phase.FAILED -> Friendly.text(msg ?: "")
                        BlockUiState.Phase.WAITING -> when (s.nfc) {
                            "ready" -> "Ready. Hold your card near the camera."
                            "nfc_disabled" -> "NFC is off. Turn it on, or scan the card's QR code."
                            else -> "This phone has no NFC. Scan the card's QR code."
                        }
                    }
                    Text(text, style = SavvyType.bodyMedium, textAlign = TextAlign.Center,
                        color = when (phase) { BlockUiState.Phase.FAILED -> c.danger; BlockUiState.Phase.SUCCESS -> c.success; else -> c.inkSoft })
                    if (phase == BlockUiState.Phase.FAILED) Text("Try again", style = SavvyType.label, color = c.ink,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable { a.retry() }.padding(8.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            if (s.nfc == "nfc_disabled") {
                PillButton("Turn on NFC", { a.openNfcSettings() }, style = PillStyle.SOFT, height = 52.dp)
                Spacer(Modifier.height(10.dp))
            }
            PillButton("Scan QR instead", { a.scanQr() }, style = PillStyle.OUTLINE, icon = Icons.Rounded.QrCodeScanner, height = 60.dp,
                enabled = s.phase != BlockUiState.Phase.CHECKING)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmEmergency) ConfirmDialog(
        title = "Use an emergency exit?",
        body = "This ends the session now and uses one of your 2 emergency exits for this week." +
            if (commitment?.unlockPolicy == UnlockPolicy.LOCKED) " It works even on a locked session." else "",
        confirm = "End session", dismiss = "Keep going", destructive = true,
        onConfirm = { confirmEmergency = false; a.emergency() },
        onDismiss = { confirmEmergency = false },
    )
}

@Composable
private fun Header(title: String, body: String, icon: (@Composable () -> Unit)? = null) {
    val c = savvyColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(c.card), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.height(18.dp))
        }
        Text(title, style = SavvyType.headline, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = SavvyType.body, color = c.inkSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun BottomLink(text: String, strong: Boolean = false, onClick: () -> Unit) {
    val c = savvyColors
    Row(Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!strong) { Icon(Icons.Rounded.Lock, null, tint = c.inkSoft, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = SavvyType.nav, color = if (strong) c.ink else c.inkSoft)
    }
}
