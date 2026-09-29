package com.iroid.savvy.rd.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.ui.components.CurvedSheetFrame
import com.iroid.savvy.rd.ui.components.TextTabBar
import com.iroid.savvy.rd.ui.screens.AccountScreen
import com.iroid.savvy.rd.ui.screens.ActivityTab
import com.iroid.savvy.rd.ui.screens.AddTaskScreen
import com.iroid.savvy.rd.ui.screens.AppPickerScreen
import com.iroid.savvy.rd.ui.screens.AppearanceScreen
import com.iroid.savvy.rd.ui.screens.CardScreen
import com.iroid.savvy.rd.ui.screens.ChildRulesScreen
import com.iroid.savvy.rd.ui.screens.DiagnosticsScreen
import com.iroid.savvy.rd.ui.screens.EmergencyScreen
import com.iroid.savvy.rd.ui.screens.FamilyScreen
import com.iroid.savvy.rd.ui.screens.HomeTab
import com.iroid.savvy.rd.ui.screens.OnboardingScreen
import com.iroid.savvy.rd.ui.screens.PermissionsScreen
import com.iroid.savvy.rd.ui.screens.SessionScreen
import com.iroid.savvy.rd.ui.screens.SettingsTab
import com.iroid.savvy.rd.ui.screens.TasksTab
import com.iroid.savvy.rd.ui.theme.SavvyTheme
import com.iroid.savvy.rd.ui.theme.SavvyType
import com.iroid.savvy.rd.ui.theme.savvyColors
import kotlinx.coroutines.delay

object Routes {
    const val ONBOARDING = "onboarding"
    const val TABS = "tabs"
    const val SESSION = "session"
    const val APPS = "apps"
    const val ADD_TASK = "addTask"
    const val CARD = "card"
    const val PERMISSIONS = "permissions"
    const val FAMILY = "family"
    const val CHILD_RULES = "childRules"
    const val ACCOUNT = "account"
    const val APPEARANCE = "appearance"
    const val DIAGNOSTICS = "diagnostics"
    const val EMERGENCY = "emergency"
}

/** Opens the Savvy card screen (NFC reader mode + QR) in one of its modes. */
fun Context.openCardScreen(mode: String = BlockActivity.MODE_UNLOCK, taskId: String? = null) {
    startActivity(Intent(this, BlockActivity::class.java).putExtra(BlockActivity.EXTRA_MODE, mode)
        .apply { if (taskId != null) putExtra(BlockActivity.EXTRA_TASK_ID, taskId) })
}

@Composable
fun SavvyRoot(vm: SavvyViewModel = viewModel()) {
    SavvyTheme(vm.appearance) {
        val nav = rememberNavController()
        val snackbar = remember { SnackbarHostState() }
        val lifecycle = LocalLifecycleOwner.current.lifecycle

        LifecycleResumeEffect(Unit) { vm.onAppResumed(); onPauseOrDispose { } }
        // Countdown and state from other screens (block screen, services): re-read every second.
        LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { vm.refresh(); delay(1000) } } }
        LaunchedEffect(vm.message) {
            vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
        }

        val start = remember { if (vm.prefs.onboarded || vm.snapshot.registered) Routes.TABS else Routes.ONBOARDING }
        Box(Modifier.fillMaxSize().background(savvyColors.canvas)) {
            NavHost(
                nav, start,
                enterTransition = { slideInVertically(tween(320)) { it / 8 } + fadeIn(tween(240)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { slideOutVertically(tween(280)) { it / 8 } + fadeOut(tween(220)) },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(vm) {
                        vm.prefs.onboarded = true
                        nav.navigate(Routes.TABS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    }
                }
                composable(Routes.TABS) { Tabs(vm, nav) }
                composable(Routes.SESSION) { SessionScreen(vm, nav) }
                composable(Routes.APPS) { AppPickerScreen(vm) { nav.popBackStack() } }
                composable(Routes.ADD_TASK) { AddTaskScreen(vm) { nav.popBackStack() } }
                composable(Routes.CARD) { CardScreen(vm) { nav.popBackStack() } }
                composable(Routes.PERMISSIONS) { PermissionsScreen(vm) { nav.popBackStack() } }
                composable(Routes.FAMILY) { FamilyScreen(vm, nav) }
                composable("${Routes.CHILD_RULES}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    ChildRulesScreen(vm, it.arguments!!.getLong("id")) { nav.popBackStack() }
                }
                composable(Routes.ACCOUNT) { AccountScreen(vm) { nav.popBackStack() } }
                composable(Routes.APPEARANCE) { AppearanceScreen(vm) { nav.popBackStack() } }
                composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(vm) { nav.popBackStack() } }
                composable(Routes.EMERGENCY) { EmergencyScreen(vm) { nav.popBackStack() } }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)) { data ->
                val c = savvyColors
                Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp), containerColor = c.ink, contentColor = c.sheet,
                ) { Text(data.visuals.message, style = SavvyType.body) }
            }
        }
    }
}

private val tabNames = listOf("Focus", "Tasks", "Activity", "Settings")

@Composable
private fun Tabs(vm: SavvyViewModel, nav: NavHostController) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    CurvedSheetFrame(bottomBar = { TextTabBar(tabNames, tab) { tab = it } }) {
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "tabs") { t ->
            when (t) {
                0 -> HomeTab(vm, nav, onUnlock = { context.openCardScreen() })
                1 -> TasksTab(vm, nav)
                2 -> ActivityTab(vm, nav)
                else -> SettingsTab(vm, nav)
            }
        }
    }
}
