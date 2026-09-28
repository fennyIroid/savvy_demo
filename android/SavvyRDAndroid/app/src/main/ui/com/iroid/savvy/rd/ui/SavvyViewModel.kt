package com.iroid.savvy.rd.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iroid.savvy.core.Commitment
import com.iroid.savvy.core.ControlMode
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.TimeIntegrity
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.SavvyActions
import com.iroid.savvy.rd.data.UsageCollector
import com.iroid.savvy.rd.savvy
import com.iroid.savvy.rd.service.TamperMonitor
import com.iroid.savvy.rd.service.UsageMonitorService
import com.iroid.savvy.rd.ui.theme.Appearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Screen-only preferences (the enforcement state stays in CommitmentRepository). */
class UiPrefs(context: Context) {
    private val p = context.getSharedPreferences("savvy_ui", Context.MODE_PRIVATE)
    var onboarded: Boolean get() = p.getBoolean("onboarded", false); set(v) = p.edit().putBoolean("onboarded", v).apply()
    var email: String get() = p.getString("email", "")!!; set(v) = p.edit().putString("email", v).apply()
    var mode: Mode get() = Mode.valueOf(p.getString("mode", Mode.STUDY.name)!!); set(v) = p.edit().putString("mode", v.name).apply()
    var minutes: Int get() = p.getInt("minutes", 60); set(v) = p.edit().putInt("minutes", v).apply()
    var policy: UnlockPolicy get() = UnlockPolicy.valueOf(p.getString("policy", UnlockPolicy.CARD_REQUIRED.name)!!)
        set(v) = p.edit().putString("policy", v.name).apply()
    var appearance: Appearance get() = Appearance.valueOf(p.getString("appearance", Appearance.AUTO.name)!!)
        set(v) = p.edit().putString("appearance", v.name).apply()
}

data class SessionSetup(val mode: Mode, val minutes: Int, val policy: UnlockPolicy)

data class TaskItem(val id: String, val title: String, val minutes: Int, val status: String)

/** Everything the screens read, refreshed from CommitmentRepository every second while visible. */
data class Snapshot(
    val registered: Boolean = false,
    val role: String = "self",
    val controlMode: ControlMode = ControlMode.SELF,
    val deviceId: Long = 0,
    val boundCard: String? = null,
    val devCardUrl: String? = null,
    val selected: Set<String> = emptySet(),
    val parentBlocked: Set<String> = emptySet(),
    val commitment: Commitment? = null,
    val remainingMs: Long = 0,
    val pausedForMs: Long = 0,
    val tasks: List<TaskItem> = emptyList(),
    val offlineQueue: Int = 0,
    val emergencyUsedLocal: Int = 0,
) {
    val active get() = commitment != null && remainingMs > 0
    val paused get() = active && pausedForMs > 0
}

data class Usage(val todaySec: Long, val averageSec: Long, val days: Int, val todayApps: List<Pair<String, Long>>, val weekDaily: List<Pair<java.time.LocalDate, Long>>)
data class Insights(val streakDays: Int, val focusTodaySec: Int, val focusTotalSec: Int)

class SavvyViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val repo get() = ctx.savvy.repo
    val actions = SavvyActions(app)
    val prefs = UiPrefs(app)

    var snapshot by mutableStateOf(Snapshot()); private set
    var tamper by mutableStateOf<TamperMonitor.Status?>(null); private set
    var usage by mutableStateOf<Usage?>(null); private set
    var insights by mutableStateOf<Insights?>(null); private set
    var insightsError by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf<String?>(null); private set
    var appearance by mutableStateOf(prefs.appearance); private set
    var session by mutableStateOf(SessionSetup(prefs.mode, prefs.minutes, prefs.policy)); private set

    /** One-shot message for the snackbar. */
    var message by mutableStateOf<String?>(null)

    init { refresh() }

    fun refresh() {
        val c = repo.commitment
        val now = repo.now()
        val tasks = repo.tasksJson.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).toTask() } }
        snapshot = Snapshot(
            registered = repo.deviceToken != null,
            role = repo.role,
            controlMode = repo.controlMode,
            deviceId = repo.deviceId,
            boundCard = repo.boundCardCode,
            devCardUrl = repo.devCardUrl,
            selected = repo.selectedPackages,
            parentBlocked = repo.parentBlockedPackages,
            commitment = c,
            remainingMs = c?.let { TimeIntegrity.remainingMs(it, now) } ?: 0,
            pausedForMs = c?.let { cm ->
                val until = cm.pausedUntilElapsedMs
                if (until != null && cm.pauseBootCount == now.bootCount) (until - now.elapsedRealtimeMs).coerceAtLeast(0) else 0
            } ?: 0,
            tasks = tasks,
            offlineQueue = repo.offlineQueue().length(),
            emergencyUsedLocal = repo.localEmergencyExits.count { System.currentTimeMillis() - it < 7 * 86_400_000L },
        )
    }

    fun refreshSlow() {
        viewModelScope.launch {
            tamper = withContext(Dispatchers.IO) { runCatching { TamperMonitor.read(ctx) }.getOrNull() }
            usage = withContext(Dispatchers.IO) { runCatching { readUsage() }.getOrNull() }
        }
    }

    /** App opened: same as the old MainActivity.onResume (re-arm enforcement, server restore). */
    fun onAppResumed() {
        if (repo.commitment != null || repo.parentBlockedPackages.isNotEmpty()) UsageMonitorService.start(ctx)
        refresh(); refreshSlow()
        if (repo.deviceToken != null) viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { actions.restore() } }
            refresh()
        }
    }

    fun loadInsights() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { ctx.savvy.backend.insights(java.util.TimeZone.getDefault().id) }
            }
            r.onSuccess { i ->
                insights = Insights(i.optInt("streak_days"), i.optInt("focus_seconds_today"), i.optInt("focus_seconds_total")); insightsError = null
            }.onFailure { insightsError = Friendly.text("error: $it") }
        }
    }

    private fun readUsage(): Usage? {
        if (tamper?.usageAccess == false) return null
        val totals = UsageCollector.dailyTotals(ctx, days = 7)
        if (totals.isEmpty()) return null
        val today = java.time.LocalDate.now()
        val todayMap = totals[today] ?: emptyMap()
        val daysWithData = totals.filterValues { it.values.sum() > 0 }
        val avg = if (daysWithData.isEmpty()) 0 else daysWithData.values.sumOf { it.values.sum() } / daysWithData.size
        val week = (6 downTo 0).map { d -> today.minusDays(d.toLong()).let { it to (totals[it]?.values?.sum() ?: 0L) } }
        return Usage(todayMap.values.sum(), avg, daysWithData.size,
            todayMap.entries.sortedByDescending { it.value }.map { it.key to it.value }, week)
    }

    /**
     * Runs one of the existing SavvyActions off the main thread, then refreshes and shows
     * the result in plain words. [label] is shown on the busy button.
     */
    fun run(label: String, quiet: Boolean = false, onDone: (String) -> Unit = {}, block: SavvyActions.() -> String) {
        if (busy != null) return
        busy = label
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) { runCatching { actions.block() }.getOrElse { "error: $it" } }
            busy = null
            refresh(); refreshSlow()
            if (!quiet) message = Friendly.text(raw)
            onDone(raw)
        }
    }

    fun changeAppearance(a: Appearance) { prefs.appearance = a; appearance = a }

    fun setRole(role: String) {
        repo.role = role
        when (role) {
            "self" -> repo.controlMode = ControlMode.SELF
            "child" -> repo.controlMode = ControlMode.PARENT
        }
        refresh()
    }

    fun setSelected(pkgs: Set<String>) { repo.selectedPackages = pkgs; refresh() }

    fun saveSession(s: SessionSetup) {
        prefs.mode = s.mode; prefs.minutes = s.minutes; prefs.policy = s.policy
        session = s
    }

    fun startSession() {
        val (mode, minutes, policy) = session
        run("Starting") { start(mode, minutes, policy) }
    }

    fun addTask(title: String, minutes: Int) { actions.addTask(title, minutes); refresh() }

    fun deleteTask(id: String) {
        val a = repo.tasksJson
        val out = org.json.JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") != id) out.put(a.getJSONObject(i))
        repo.tasksJson = out
        refresh()
    }

    fun startTask(t: TaskItem) {
        actions.setTaskStatus(t.id, "active"); refresh()
        run("Starting") { start(Mode.TASK, t.minutes, UnlockPolicy.CARD_REQUIRED, taskRef = t.id) }
    }

    private fun JSONObject.toTask() = TaskItem(getString("id"), getString("title"), getInt("minutes"), getString("status"))
}
