package com.iroid.savvy.rd

import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import com.iroid.savvy.core.Commitment
import com.iroid.savvy.core.ControlMode
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.OfflineEvent
import com.iroid.savvy.core.RestrictionPolicy.Decision
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.data.UsageCollector
import com.iroid.savvy.rd.service.BlockingEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import android.net.Uri
import com.iroid.savvy.rd.service.BootReceiver
import com.iroid.savvy.rd.service.UsageMonitorService
import java.time.LocalDate
import java.time.ZoneId

private const val INSTAGRAM = "com.instagram.android"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = SavvyApp::class)
class AndroidFlowTest {
    private lateinit var app: SavvyApp
    private lateinit var backend: Backend

    @Before fun setUp() {
        backend = Backend()
        System.setProperty("savvy.backendUrl", backend.url)
        app = RuntimeEnvironment.getApplication() as SavvyApp
    }

    @After fun tearDown() {
        System.clearProperty("savvy.backendUrl")
        backend.close()
    }

    private fun goOffline() = System.setProperty("savvy.backendUrl", "http://127.0.0.1:1")
    private fun goOnline() = System.setProperty("savvy.backendUrl", backend.url)

    private fun nextStarted(): Intent? = shadowOf(app as Application).nextStartedActivity

    @Test fun repositoryRoundTripsCommitmentAndOfflineQueue() {
        val repo = app.repo
        val c = Commitment(5, Mode.TASK, UnlockPolicy.CARD_REQUIRED, ControlMode.SELF, setOf(INSTAGRAM), 60_000, repo.now(), 123L,
            taskRef = "t1", localId = "L1")
        repo.commitment = c
        assertEquals(c, repo.commitment)
        repo.enqueue(OfflineEvent.EmergencyExit("L1", 5, 1_000))
        repo.enqueue(OfflineEvent.TaskCompleted("L1", 5, 2_000))
        assertEquals("emergency_exit", repo.offlineQueue().getJSONObject(0).getString("type"))
        repo.dropSynced(1)
        assertEquals(1, repo.offlineQueue().length())
        assertEquals("task_completed", repo.offlineQueue().getJSONObject(0).getString("type"))
    }

    @Test fun onlineFlowBlockThenCardUnlock() {
        val actions = SavvyActions(app)
        assertTrue(actions.register("alice@test").startsWith("registered"))
        val card = backend.newCard()
        app.backend.registerCard(card, "nfc")
        app.repo.boundCardCode = card.substringAfter("/c/1.").substringBefore('.')
        app.repo.selectedPackages = setOf(INSTAGRAM)
        assertTrue(actions.start(Mode.STUDY, 60, UnlockPolicy.CARD_REQUIRED).startsWith("started"))

        // Instagram comes to the front -> engine blocks and launches the Savvy block screen.
        assertEquals(Decision.BlockApp(INSTAGRAM), app.engine.onForeground(INSTAGRAM, "MainActivity", BlockingEngine.Source.ACCESSIBILITY, null))
        val started = nextStarted()
        assertEquals(BlockActivity::class.java.name, started?.component?.className)
        assertEquals(INSTAGRAM, started?.getStringExtra(BlockActivity.EXTRA_BLOCKED))

        // Wrong card is refused, the right card releases through a verified backend grant.
        assertEquals(UnlockCoordinator.Outcome.Rejected("card_not_owned_by_user"), UnlockCoordinator(app).handleCard(backend.newCard(), "nfc"))
        assertEquals(UnlockCoordinator.Outcome.Released(offline = false), UnlockCoordinator(app).handleCard(card, "nfc"))
        assertEquals(Decision.Allow, app.engine.onForeground(INSTAGRAM, null, BlockingEngine.Source.ACCESSIBILITY, null))
        assertTrue(actions.insights().startsWith("streak"))
    }

    @Test fun offlineStartOfflineCardUnlockThenSync() {
        val actions = SavvyActions(app)
        actions.register("bob@test")
        val card = backend.newCard()
        app.backend.registerCard(card, "nfc")
        app.repo.boundCardCode = card.substringAfter("/c/1.").substringBefore('.')
        app.repo.selectedPackages = setOf(INSTAGRAM)

        goOffline()
        assertTrue(actions.start(Mode.WORK, 120, UnlockPolicy.CARD_REQUIRED).startsWith("started offline"))
        assertEquals(Decision.BlockApp(INSTAGRAM), app.engine.onForeground(INSTAGRAM, null, BlockingEngine.Source.USAGE_STATS, null))
        // Offline: the signed card is checked with the public key and the bound card code.
        assertEquals(UnlockCoordinator.Outcome.Released(offline = true), UnlockCoordinator(app).handleCard(card, "nfc"))
        assertEquals(2, app.repo.offlineQueue().length())

        goOnline()
        assertEquals("synced 2 offline events", actions.flushOffline())
        assertEquals(0, app.repo.offlineQueue().length())
        val summary = app.backend.insights("UTC")
        assertEquals(1, summary.getJSONObject("sessions_by_outcome").getInt("released_by_card"))
    }

    @Test fun offlineEmergencyExitLimitedToOnePerWeek() {
        val actions = SavvyActions(app)
        actions.register("carol@test")
        app.repo.selectedPackages = setOf(INSTAGRAM)
        goOffline()
        actions.start(Mode.WORK, 60, UnlockPolicy.LOCKED)
        assertEquals("emergency exit offline (will sync)", actions.emergency("test"))
        actions.start(Mode.WORK, 60, UnlockPolicy.LOCKED)
        assertEquals("offline emergency exit already used this week", actions.emergency("test"))
        goOnline()
        assertEquals("synced 3 offline events", actions.flushOffline())
    }

    @Test fun parentAlwaysOnRuleAndUsageReachParent() {
        // Parent (another device, plain HTTP client) creates a link code.
        val parent = backend.call("POST", "/v1/devices/register", JSONObject().put("email", "parent@test").put("platform", "android"), null)
        val parentToken = parent.getString("device_token")
        val code = backend.call("POST", "/v1/family/link-codes", JSONObject(), parentToken).getString("code")

        // Child phone (the app under test).
        val actions = SavvyActions(app)
        app.repo.role = "child"
        actions.register("child@test")
        assertEquals("linked as child", actions.linkAsChild(code))
        val childId = app.repo.deviceId

        // Parent chooses Instagram as always-on.
        backend.call("PUT", "/v1/family/children/$childId/rules",
            JSONObject().put("rules", JSONObject().put("packages", JSONArray(listOf(INSTAGRAM))).put("always_on", true).put("focus", JSONObject.NULL)), parentToken)
        assertTrue(actions.syncRules().startsWith("applied rules v1"))
        assertEquals(setOf(INSTAGRAM), app.repo.parentBlockedPackages)
        // Blocked with no focus session running.
        assertEquals(Decision.BlockApp(INSTAGRAM), app.engine.onForeground(INSTAGRAM, null, BlockingEngine.Source.ACCESSIBILITY, null))

        // Usage events recorded by Android -> daily totals -> parent sees them.
        val usm = app.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        shadowOf(usm).addEvent(INSTAGRAM, now - 30 * 60_000, UsageEvents.Event.ACTIVITY_RESUMED)
        shadowOf(usm).addEvent(INSTAGRAM, now - 10 * 60_000, UsageEvents.Event.ACTIVITY_PAUSED)
        val today = UsageCollector.dailyTotals(app)[LocalDate.now(ZoneId.systemDefault())]
        assertNotNull(today)
        assertEquals(1200L, today!![INSTAGRAM])
        actions.uploadUsage()
        val usage = backend.call("GET", "/v1/family/children/$childId/usage", null, parentToken)
        assertEquals(1200, usage.getJSONArray("days").getJSONObject(0).getInt("total_seconds"))
        val status = backend.call("GET", "/v1/family/children/$childId/status", null, parentToken)
        assertEquals(1, status.getInt("latest_rule_version"))
    }

    private fun waitUntil(timeoutMs: Long = 8_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            ShadowLooper.idleMainLooper()
            check(System.currentTimeMillis() < end) { "condition not met in time" }
            Thread.sleep(50)
        }
    }

    @Test fun cardAppLinkOpensBlockScreenAndUnlocks() {
        val actions = SavvyActions(app)
        actions.register("dave@test")
        val card = backend.newCard()
        app.backend.registerCard(card, "nfc")
        app.repo.selectedPackages = setOf(INSTAGRAM)
        actions.start(Mode.STUDY, 60, UnlockPolicy.CARD_REQUIRED)
        // Tag dispatch / App Link: card URL delivered to BlockActivity while Savvy was in background.
        val controller = Robolectric.buildActivity(BlockActivity::class.java, Intent(Intent.ACTION_VIEW, Uri.parse(card))).setup()
        waitUntil { app.repo.commitment == null }
        waitUntil { controller.get().isFinishing }
    }

    @Test fun rebootRestartsEnforcementService() {
        app.repo.selectedPackages = setOf(INSTAGRAM)
        app.repo.commitment = Commitment(null, Mode.SLEEP, UnlockPolicy.LOCKED, ControlMode.SELF, setOf(INSTAGRAM), 3_600_000, app.repo.now(), null)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(UsageMonitorService::class.java.name, shadowOf(app as Application).nextStartedService?.component?.className)
    }

    @Test fun tamperFlagsReachParent() {
        val parent = backend.call("POST", "/v1/devices/register", JSONObject().put("email", "p2@test").put("platform", "android"), null)
        val parentToken = parent.getString("device_token")
        val code = backend.call("POST", "/v1/family/link-codes", JSONObject(), parentToken).getString("code")
        val actions = SavvyActions(app)
        app.repo.role = "child"
        actions.register("c2@test")
        actions.linkAsChild(code)
        // Nothing enabled on this simulated phone: no accessibility service, no device admin.
        actions.housekeeping()
        val flags = backend.call("GET", "/v1/family/children/${app.repo.deviceId}/status", null, parentToken).getJSONArray("flags")
        val list = (0 until flags.length()).map(flags::getString)
        assertTrue(list.toString(), "accessibility_off" in list && "device_admin_off" in list)
    }

    @Test fun parentModeGuardsDeviceAdminScreenSelfModeDoesNot() {
        val settings = "com.android.settings"
        val adminScreen = "com.android.settings.DeviceAdminAdd"
        app.repo.commitment = Commitment(1, Mode.STUDY, UnlockPolicy.CARD_REQUIRED, ControlMode.SELF, setOf(INSTAGRAM), 3_600_000, app.repo.now(), null)
        assertEquals(Decision.Allow, app.engine.onForeground(settings, adminScreen, BlockingEngine.Source.ACCESSIBILITY, null))
        app.repo.commitment = app.repo.commitment!!.copy(controlMode = ControlMode.PARENT)
        assertEquals(Decision.BlockTamperScreen(settings, adminScreen), app.engine.onForeground(settings, adminScreen, BlockingEngine.Source.ACCESSIBILITY, null))
        assertEquals(true, nextStarted()?.getBooleanExtra(BlockActivity.EXTRA_TAMPER, false))
    }
}
