package com.iroid.savvy.rd

import android.content.Context
import com.iroid.savvy.core.Commitment
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.OfflineEvent
import com.iroid.savvy.core.OfflinePolicy
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.data.BackendClient
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.data.UsageCollector
import com.iroid.savvy.rd.service.TamperMonitor
import com.iroid.savvy.rd.service.UsageMonitorService
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Android equivalent of iOS AppModel: every R&D action in one place so the test
 * lab and the background service share the same logic. All methods block on the
 * network: call them from a background thread.
 */
class SavvyActions(private val context: Context) {
    private val repo get() = context.savvy.repo
    private val backend get() = context.savvy.backend
    private val unlock by lazy { UnlockCoordinator(context) }

    /**
     * Child phone linked to a parent: role, account and app list are the parent's to change.
     * Only after the link: picking "Child's phone" alone must not lock the user out.
     */
    val managedByParent get() = repo.role == "child" && repo.controlMode == com.iroid.savvy.core.ControlMode.PARENT && repo.parentLinked

    fun register(email: String): String {
        // Re-registering would give the child a fresh, unlinked device token.
        if (managedByParent && repo.deviceToken != null) return "managed_by_parent"
        val r = backend.register(email, repo.role)
        repo.deviceId = r.getLong("device_id"); repo.deviceToken = r.getString("device_token")
        repo.parentLinked = false
        val k = backend.keys()
        repo.grantKeyHex = spkiRawHex(k.getString("grant_public_key")); repo.cardKeyHex = spkiRawHex(k.getString("card_public_key"))
        com.iroid.savvy.rd.service.SyncJobService.schedule(context)
        return "registered device ${repo.deviceId} as ${repo.role}; ${restore()}"
    }

    /**
     * Binds the physical card (NFC URL or the same URL as QR) to this account. The card
     * code is also kept locally: offline unlock accepts only the bound signed card.
     */
    fun registerCard(raw: String, source: String): String {
        val payload = com.iroid.savvy.core.CardPayload.parse(raw, BuildConfig.CARD_DOMAIN) ?: return "not a Savvy card"
        val r = try {
            backend.registerCard(raw, source)
        } catch (e: BackendClient.ApiError) {
            // Re-registering the card this account already owns is fine.
            if (e.code != "account_already_has_card" || repo.boundCardCode != payload.cardCode) return "card refused: ${e.code}"
            null
        }
        repo.boundCardCode = payload.cardCode
        SavvyLog.event("Card", "registered ${payload.cardCode} via $source format=${r?.optString("format") ?: "existing"}")
        return "card ${payload.cardCode} registered (${payload::class.simpleName})"
    }

    /** Debug builds: create a signed test card on the dev backend and bind it. */
    fun devCard(): String {
        val url = backend.devCard().getString("url")
        repo.devCardUrl = url
        return "${registerCard(url, "dev")}\n$url"
    }

    /** FREE policy only: the user ends the session in the app (backend method=user). */
    fun endFree(): String {
        val c = repo.commitment ?: return "no commitment"
        if (c.unlockPolicy != UnlockPolicy.FREE) return "this session needs the card to end early"
        if (c.serverId == null) { unlock.releaseLocally("user_ended_offline"); return "ended (offline)" }
        return try {
            unlock.applyGrant(backend.release(c.serverId!!, "user", null, "app").optString("grant"), c.serverId!!).toString()
        } catch (e: BackendClient.ApiError) {
            "end refused: ${e.code}"
        } catch (e: IOException) {
            unlock.releaseLocally("user_ended_offline"); "ended (offline)"
        }
    }

    /** Self mode screen-time summary from UsageStats. Stays on the device. */
    fun screenTimeToday(): String {
        val today = UsageCollector.dailyTotals(context, days = 1).entries.lastOrNull()?.value ?: return "no usage data (grant Usage access)"
        val pm = context.packageManager
        fun label(p: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
        return "Screen time today: ${today.values.sum() / 60} min\n" +
            today.entries.sortedByDescending { it.value }.take(8).joinToString("\n") { "  ${label(it.key)}: ${it.value / 60} min" }
    }

    /** Launch / reboot / reinstall restore. Offline events are flushed first. */
    fun restore(): String {
        if (repo.deviceToken == null) return "not registered"
        flushOffline()
        val r = backend.active()
        repo.recordServerTime(Instant.parse(r.getString("server_time")).toEpochMilli())
        val c = r.optJSONObject("commitment")
        if (c == null) {
            if (repo.commitment?.serverId != null) unlock.releaseLocally("server_says_not_active")
            return "no active commitment"
        }
        val local = repo.commitment
        if (local == null || r.optBoolean("restored_from_previous_install")) {
            val ends = Instant.parse(c.getString("ends_at")).toEpochMilli()
            val serverNow = System.currentTimeMillis() + repo.serverOffsetMs!!
            // Reinstall / cleared data: the blocked apps come back from the server with the commitment.
            val pkgs = c.optString("selection_ref").takeIf { it.startsWith("[") }
                ?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map(a::getString).toSet() } }
            if (pkgs != null && repo.selectedPackages.isEmpty()) repo.selectedPackages = pkgs
            activate(c.getLong("id"), UUID.randomUUID().toString(), Mode.valueOf(c.getString("mode").uppercase()),
                UnlockPolicy.fromWire(c.getString("unlock_policy")), ends - serverNow, ends, c.optString("task_ref").ifEmpty { null },
                pkgs)
            return "restored commitment ${c.getLong("id")} (${pkgs?.size ?: 0} apps from server)"
        }
        if (local.serverId == c.getLong("id")) {
            repo.commitment = local.copy(serverEndsAtMs = Instant.parse(c.getString("ends_at")).toEpochMilli())
        }
        return "commitment ${c.getLong("id")} active"
    }

    fun start(mode: Mode, minutes: Int, policy: UnlockPolicy, taskRef: String? = null): String {
        if (repo.selectedPackages.isEmpty()) return "choose apps first"
        val localId = UUID.randomUUID().toString()
        val selectionRef = JSONArray(repo.selectedPackages.toList()).toString()
        return try {
            val r = backend.startCommitment(mode.name.lowercase(), minutes, policy.wire, taskRef, selectionRef)
            activate(r.getLong("id"), localId, mode, policy, minutes * 60_000L, Instant.parse(r.getString("ends_at")).toEpochMilli(), taskRef)
            "started ${r.getLong("id")} for $minutes min"
        } catch (e: IOException) {
            repo.enqueue(OfflineEvent.CommitmentStarted(localId, mode.name.lowercase(), minutes, policy.wire, taskRef,
                System.currentTimeMillis(), selectionRef))
            activate(null, localId, mode, policy, minutes * 60_000L, null, taskRef)
            "started offline for $minutes min (will sync)"
        }.also {
            // Marked only once the restriction is really on (a refused start leaves the task pending).
            if (taskRef != null && repo.commitment?.taskRef == taskRef) setTaskStatus(taskRef, "active")
        }
    }

    private fun activate(serverId: Long?, localId: String, mode: Mode, policy: UnlockPolicy, durationMs: Long,
                         serverEndsAt: Long?, taskRef: String?, packages: Set<String>? = null) {
        repo.commitment = Commitment(serverId, mode, policy, repo.controlMode, packages ?: repo.selectedPackages, durationMs, repo.now(),
            serverEndsAt, taskRef = taskRef, localId = localId)
        repo.emergencyPending = null
        UsageMonitorService.start(context)
        com.iroid.savvy.rd.admin.DeviceOwnerController.reconcile(context)
        SavvyLog.event("Actions", "activated $serverId $mode $policy ${durationMs / 60000}min task=$taskRef")
    }

    /** R&D 12: to-do completion. Online: backend decides (card may be required). Offline: core OfflinePolicy. */
    fun completeTask(taskId: String, cardPayload: String? = null): String {
        val c = repo.commitment
        if (c == null || c.taskRef != taskId) { setTaskStatus(taskId, "completed"); return "task done (no active restriction)" }
        if (c.serverId != null) {
            try {
                val r = backend.release(c.serverId!!, "task_complete", cardPayload, "nfc")
                val o = unlock.applyGrant(r.optString("grant"), c.serverId!!)
                if (o is UnlockCoordinator.Outcome.Released) setTaskStatus(taskId, "completed")
                return "task: $o"
            } catch (e: BackendClient.ApiError) {
                return "task completion refused: ${e.code}"
            } catch (e: IOException) {
                SavvyLog.event("Actions", "task offline rule: $e")
            }
        }
        return when (val d = OfflinePolicy.taskCompletion(c, taskId)) {
            is OfflinePolicy.CardDecision.Release -> {
                repo.enqueue(OfflineEvent.TaskCompleted(c.localId, c.serverId, System.currentTimeMillis()))
                unlock.releaseLocally("task_complete_offline")
                setTaskStatus(taskId, "completed")
                "task done offline (will sync)"
            }
            is OfflinePolicy.CardDecision.Reject -> "offline: ${d.reason}"
        }
    }

    fun emergency(reason: String): String {
        val c = repo.commitment ?: return "no commitment"
        if (c.serverId != null) {
            try {
                val r = backend.emergencyExit(c.serverId!!, reason)
                if (r.optString("status") == "pending") {
                    // Backend cooling-off: calling again after available_at confirms the same request.
                    repo.emergencyPending = "${c.serverId}|${r.optString("available_at")}"
                    return "pending until ${r.optString("available_at")}"
                }
                return unlock.applyGrant(r.optString("grant"), c.serverId!!).toString()
            } catch (e: BackendClient.ApiError) {
                return "emergency refused: ${e.code}"
            } catch (e: IOException) {
                SavvyLog.event("Actions", "emergency offline rule: $e")
            }
        }
        val now = System.currentTimeMillis()
        if (!OfflinePolicy.emergencyAllowed(repo.localEmergencyExits, now)) return "offline emergency exit already used this week"
        repo.localEmergencyExits = repo.localEmergencyExits + now
        repo.enqueue(OfflineEvent.EmergencyExit(c.localId, c.serverId, now))
        unlock.releaseLocally("emergency_offline")
        return "emergency exit offline (will sync)"
    }

    /** Emergency exits used in the backend window: (used, limit, windowDays). */
    fun emergencyUsage(): Triple<Int, Int, Int> = backend.emergencyUsage().let {
        Triple(it.getInt("used"), it.getInt("limit"), it.getInt("window_days"))
    }

    // ---- Always blocked (the user's own list, independent of focus sessions) ----------

    /** Adds an app to the always-blocked list. Blocking is instant and needs no card. */
    fun blockAlways(pkg: String): String {
        if (managedByParent) return "managed_by_parent"
        if (pkg in com.iroid.savvy.core.RestrictionPolicy.ALWAYS_ALLOWED || pkg == context.packageName) return "cannot_block_app"
        repo.selfBlockedPackages = repo.selfBlockedPackages + pkg
        UsageMonitorService.start(context)
        SavvyLog.event("Always", "blocked $pkg")
        return "always blocked $pkg"
    }

    /**
     * Card tap on an always-blocked app: the same card that ends a focus session early, checked
     * on the device (core OfflinePolicy.alwaysBlockedUnblock) as there is no commitment to release.
     * @param presenceVerified a SUN card's live proof already passed during the tap.
     */
    fun unblockAlways(pkg: String, raw: String, presenceVerified: Boolean = false): String {
        if (pkg !in repo.selfBlockedPackages) return "unblocked $pkg"
        val d = OfflinePolicy.alwaysBlockedUnblock(raw, BuildConfig.CARD_DOMAIN,
            repo.cardKeyHex?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray() ?: ByteArray(0),
            repo.boundCardCode, presenceVerified)
        if (d is OfflinePolicy.CardDecision.Reject) return d.reason
        repo.selfBlockedPackages = repo.selfBlockedPackages - pkg
        if (repo.commitment == null && !repo.hasAlwaysOnBlocks) UsageMonitorService.stop(context)
        SavvyLog.event("Always", "unblocked $pkg with card")
        return "unblocked $pkg"
    }

    fun flushOffline(): String {
        val queue = repo.offlineQueue()
        if (queue.length() == 0) return "nothing to sync"
        return try {
            val r = backend.sync(queue)
            val results = r.getJSONArray("results")
            for (i in 0 until results.length()) {
                val res = results.getJSONObject(i)
                val ev = queue.getJSONObject(i)
                val c = repo.commitment
                if (ev.getString("type") == "commitment_started" && c != null && c.localId == ev.getString("local_id") && res.has("server_id")) {
                    repo.commitment = c.copy(serverId = res.getLong("server_id"))
                }
                res.optString("violation").takeIf { it.isNotEmpty() && it != "null" }?.let { SavvyLog.event("Sync", "violation $it") }
            }
            repo.dropSynced(queue.length())
            "synced ${queue.length()} offline events"
        } catch (e: IOException) {
            "sync failed, kept ${queue.length()}"
        }
    }

    // ---- Child device ---------------------------------------------------------

    fun linkAsChild(code: String): String {
        backend.redeemLink(code)
        repo.role = "child"
        repo.controlMode = com.iroid.savvy.core.ControlMode.PARENT
        repo.parentLinked = true
        uploadInventory()
        // Report in now: the parent sees the phone as reporting and any rules already waiting apply.
        housekeeping()
        return "linked as child"
    }

    fun uploadInventory(): String = backend.putInventory(UsageCollector.launchableApps(context)).toString()

    /** Applies parent rules: real package names (no opaque tokens on Android). */
    fun syncRules(): String {
        val r = backend.pullRules(repo.appliedRuleVersion)
        if (!r.getBoolean("changed")) return "rules up to date v${r.getInt("version")}"
        val rules = r.getJSONObject("rules")
        val pkgs = rules.optJSONArray("packages")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet()
        repo.selectedPackages = pkgs
        repo.parentBlockedPackages = if (rules.optBoolean("always_on")) pkgs else emptySet()
        repo.parentLeavePinHash = rules.optString("leave_pin_sha256").ifEmpty { null }
        val focus = rules.optJSONObject("focus")
        val running = repo.commitment
        if (focus != null && running == null) {
            start(Mode.valueOf(focus.getString("mode").uppercase()), focus.getInt("duration_minutes"), UnlockPolicy.fromWire(focus.getString("unlock_policy")))
        } else if (focus != null && running != null && running.controlMode == com.iroid.savvy.core.ControlMode.PARENT) {
            // A parent session already running takes the parent's new app list right away.
            repo.commitment = running.copy(blockedPackages = pkgs)
        } else if (focus == null && running != null) {
            unlock.releaseLocally("parent_rules_cleared")
        }
        if (repo.parentBlockedPackages.isNotEmpty()) UsageMonitorService.start(context)
        com.iroid.savvy.rd.admin.DeviceOwnerController.reconcile(context)
        repo.appliedRuleVersion = r.getInt("version")
        backend.ackRules(r.getInt("version"))
        return "applied rules v${r.getInt("version")} (${pkgs.size} apps, always_on=${rules.optBoolean("always_on")})"
    }

    /**
     * Child phone leaves parent mode with the parent's PIN (checked offline against the hash in
     * the rules). Always-on blocks and the tamper guard stop; a focus session already running
     * continues until it ends (the backend would restore it anyway). The family link stays on
     * the backend (no unlink endpoint in the POC backend).
     */
    fun leaveParentMode(pin: String): String {
        if (!managedByParent) return "not in parent mode"
        if (repo.parentLeavePinHash == null) return "no_leave_pin"
        if (!com.iroid.savvy.core.ParentPin.matches(repo.deviceId, pin, repo.parentLeavePinHash)) {
            SavvyLog.event("Family", "leave parent mode: wrong PIN")
            return "wrong_pin"
        }
        repo.role = "self"
        repo.controlMode = com.iroid.savvy.core.ControlMode.SELF
        repo.parentLinked = false
        repo.parentBlockedPackages = emptySet()
        repo.parentLeavePinHash = null
        com.iroid.savvy.rd.admin.DeviceOwnerController.reconcile(context)
        SavvyLog.event("Family", "left parent mode with parent PIN")
        return "left parent mode"
    }

    fun uploadUsage(): String {
        val totals = UsageCollector.dailyTotals(context)
        totals.forEach { (date, perApp) -> backend.putDailyUsage(date.toString(), UsageCollector.toJson(context, perApp)) }
        return "uploaded ${totals.size} day(s)"
    }

    /** Called every 15 min by UsageMonitorService and on app open. */
    fun housekeeping(): String {
        if (repo.deviceToken == null) return "not registered"
        val out = mutableListOf<String>()
        out += runCatching { flushOffline() }.getOrElse { "sync: $it" }
        // Rules before the heartbeat, so the parent sees the version applied in this same pass.
        if (repo.role == "child") out += runCatching { syncRules() }.getOrElse { "rules: $it" }
        out += runCatching {
            backend.heartbeat(TamperMonitor.read(context).toJson().put("applied_rule_version", repo.appliedRuleVersion)
                .put("shield_active", repo.commitment != null)); "heartbeat ok"
        }.getOrElse { "heartbeat: $it" }
        if (repo.role == "child") out += runCatching { uploadUsage() }.getOrElse { "usage: $it" }
        SavvyLog.event("Housekeeping", out.joinToString("; "))
        return out.joinToString("\n")
    }

    // ---- Parent phone ---------------------------------------------------------

    fun createLinkCode(): String = backend.createLinkCode().getString("code")

    fun children(): List<Long> = backend.children().getJSONArray("children").let { a ->
        (0 until a.length()).map { a.getJSONObject(it).getLong("child_device_id") }
    }

    /**
     * @param leavePinHash core ParentPin.hash for the child, or null for "no way to leave parent mode".
     * The backend stores the rules JSON as sent, so the extra fields need no backend change.
     */
    fun sendRules(childId: Long, packages: Set<String>, focusMinutes: Int?, policy: UnlockPolicy, alwaysOn: Boolean,
                  mode: Mode = Mode.STUDY, leavePinHash: String? = null): String {
        val rules = JSONObject().put("packages", JSONArray(packages.toList())).put("always_on", alwaysOn)
            .put("focus", focusMinutes?.let { JSONObject().put("mode", mode.name.lowercase()).put("duration_minutes", it).put("unlock_policy", policy.wire) } ?: JSONObject.NULL)
            .putOpt("leave_pin_sha256", leavePinHash)
        return "rules v${backend.putRules(childId, rules).getInt("version")} sent"
    }

    fun childReport(childId: Long): String {
        val s = backend.childStatus(childId)
        val u = backend.childUsage(childId).getJSONArray("days")
        val today = if (u.length() > 0) u.getJSONObject(0) else null
        return "flags=${s.getJSONArray("flags")} rules v${s.optInt("applied_rule_version")}/${s.getInt("latest_rule_version")}\n" +
            (today?.let { d -> "usage ${d.getString("date")}: ${d.getInt("total_seconds") / 60} min, top: " +
                (0 until minOf(3, d.getJSONArray("apps").length())).joinToString { i ->
                    d.getJSONArray("apps").getJSONObject(i).let { a -> "${a.getString("label")} ${a.getInt("seconds") / 60}m" } } } ?: "no usage yet")
    }

    fun insights(): String {
        val i = backend.insights(java.util.TimeZone.getDefault().id)
        return "streak ${i.getInt("streak_days")} days, today ${i.getInt("focus_seconds_today") / 60} min, total ${i.getInt("focus_seconds_total") / 60} min"
    }

    // ---- Tasks ----------------------------------------------------------------

    fun addTask(title: String, minutes: Int): String {
        val a = repo.tasksJson
        a.put(JSONObject().put("id", UUID.randomUUID().toString()).put("title", title).put("minutes", minutes).put("status", "pending"))
        repo.tasksJson = a
        return "task added"
    }

    fun setTaskStatus(id: String, status: String) {
        val a = repo.tasksJson
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") == id) a.getJSONObject(i).put("status", status)
        repo.tasksJson = a
    }

    private fun spkiRawHex(b64: String) = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        .takeLast(32).joinToString("") { "%02x".format(it) }
}
