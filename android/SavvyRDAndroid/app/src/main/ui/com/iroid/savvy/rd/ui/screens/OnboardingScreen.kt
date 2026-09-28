package com.iroid.savvy.rd.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iroid.savvy.rd.BuildConfig
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.ui.SavvyViewModel
import com.iroid.savvy.rd.ui.components.CardLook
import com.iroid.savvy.rd.ui.components.PillButton
import com.iroid.savvy.rd.ui.components.PillStyle
import com.iroid.savvy.rd.ui.components.SavvyCardHero
import com.iroid.savvy.rd.ui.openCardScreen
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors

private enum class Step { WELCOME, ROLE, ACCOUNT, LINK, PERMISSIONS, CARD }

/** First run: role, account (device registration), parent link for a child, permissions, card. */
@Composable
fun OnboardingScreen(vm: SavvyViewModel, onFinished: () -> Unit) {
    val c = savvyColors
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var email by rememberSaveable { mutableStateOf(vm.prefs.email) }
    var code by rememberSaveable { mutableStateOf("") }
    val s = vm.snapshot
    val steps = remember(s.role) {
        when (s.role) {
            "child" -> listOf(Step.WELCOME, Step.ROLE, Step.ACCOUNT, Step.LINK, Step.PERMISSIONS)
            "parent" -> listOf(Step.WELCOME, Step.ROLE, Step.ACCOUNT)
            else -> listOf(Step.WELCOME, Step.ROLE, Step.ACCOUNT, Step.PERMISSIONS, Step.CARD)
        }
    }
    fun next() {
        val i = steps.indexOf(step)
        if (i == steps.lastIndex) onFinished() else step = steps[i + 1]
    }
    BackHandler(step != Step.WELCOME && step != Step.PERMISSIONS && step != Step.CARD) { step = steps[(steps.indexOf(step) - 1).coerceAtLeast(0)] }

    Column(Modifier.fillMaxSize().background(c.sheet).statusBarsPadding().navigationBarsPadding()) {
        // Progress dashes.
        Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, _ ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= steps.indexOf(step)) c.ink else c.cardStrong))
            }
        }
        AnimatedContent(step, transitionSpec = {
            (slideInHorizontally(tween(320)) { it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(200)))
        }, label = "step", modifier = Modifier.weight(1f)) { st ->
            when (st) {
                Step.WELCOME -> Page(bottom = { PillButton("Get started", ::next) }) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SavvyCardHero(CardLook.ACTIVE) }
                    Spacer(Modifier.height(20.dp))
                    Title("Put the phone down,\npick it up on purpose.")
                    Body("Savvy closes the apps that pull you in. Your Savvy card is the key to open them early.")
                    Spacer(Modifier.weight(1f))
                }
                Step.ROLE -> Page(bottom = { PillButton("Continue", ::next) }) {
                    Title("Who uses this phone?")
                    Body("You can change this later in Settings.")
                    Spacer(Modifier.height(8.dp))
                    RoleOptions(s.role) { vm.setRole(it) }
                }
                Step.ACCOUNT -> Page(bottom = {
                    PillButton(if (s.registered) "Continue" else "Create account", busy = vm.busy == "Register", enabled = email.contains('@'), onClick = {
                        if (s.registered) next() else {
                            vm.prefs.email = email.trim()
                            vm.run("Register", onDone = { r -> if (r.startsWith("registered")) next() }) { register(email.trim()) }
                        }
                    })
                }) {
                    Title("Your account")
                    Body("Sessions and your card are saved to your account, so they come back if you reinstall.")
                    Spacer(Modifier.height(8.dp))
                    EmailField(email) { email = it }
                }
                Step.LINK -> Page(bottom = {
                    PillButton("Link this phone", enabled = code.isNotBlank(), busy = vm.busy == "Link", onClick = {
                        vm.run("Link", onDone = { r -> if (r.startsWith("linked")) next() }) { linkAsChild(code) }
                    })
                    PillButton("Later", ::next, style = PillStyle.SOFT, height = 52.dp)
                }) {
                    Title("Link to your parent")
                    Body("On the parent's phone open Settings › Family and create a link code. Type it here.")
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(c.card).padding(horizontal = 24.dp, vertical = 22.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (code.isEmpty()) Text("Link code", style = SavvyType.title, color = c.inkFaint)
                            BasicTextField(code, { code = it.uppercase().trim() }, singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                textStyle = SavvyType.headline.copy(color = c.ink, textAlign = TextAlign.Center, letterSpacing = 6.sp),
                                cursorBrush = SolidColor(c.ink), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                Step.PERMISSIONS -> Page(bottom = { PillButton("Continue", ::next) }) {
                    Title("Let Savvy step in")
                    Body("Turn these on so Savvy can close blocked apps, even in the background.")
                    Spacer(Modifier.height(4.dp))
                    PermissionList(vm, compact = true)
                }
                Step.CARD -> Page(bottom = {
                    if (s.boundCard == null) {
                        PillButton("Link my card", { context.openCardScreen(BlockActivity.MODE_REGISTER) }, icon = Icons.Rounded.Contactless)
                        if (BuildConfig.DEBUG) PillButton("Create a test card", { vm.run("Test card") { devCard() } },
                            style = PillStyle.SOFT, icon = Icons.Rounded.Science, busy = vm.busy == "Test card", height = 52.dp)
                        PillButton("I'll do this later", onFinished, style = PillStyle.SOFT, height = 52.dp)
                    } else PillButton("Start using Savvy", onFinished)
                }) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SavvyCardHero(if (s.boundCard != null) CardLook.SUCCESS else CardLook.LISTENING)
                    }
                    Title(if (s.boundCard != null) "Card linked" else "Link your Savvy card")
                    Body(if (s.boundCard != null) "Card ${s.boundCard} is yours. Only this card can end your sessions early."
                        else "Hold the card to the back of your phone, or scan the QR code on it.")
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Page(bottom: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(12.dp))
            content()
        }
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { bottom() }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = SavvyType.headline, color = savvyColors.ink)

@Composable
private fun Body(text: String) = Text(text, style = SavvyType.body, color = savvyColors.inkSoft)
