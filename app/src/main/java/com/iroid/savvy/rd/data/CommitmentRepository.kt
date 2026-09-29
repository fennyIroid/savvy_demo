package com.iroid.savvy.rd.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.iroid.savvy.core.Checkpoint
import com.iroid.savvy.core.Commitment
import com.iroid.savvy.core.ControlMode
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.OfflineEvent
import com.iroid.savvy.core.TimeAnchor
import com.iroid.savvy.core.TimeIntegrity
import com.iroid.savvy.core.UnlockPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local enforcement state. Everything the blocking engine needs is here so it
 * keeps working offline and after reboot. POC: plain SharedPreferences.
 * Production: EncryptedSharedPreferences / Keystore for the device token.
 */
class CommitmentRepository(private val context: Context) {
    // Lazy: the app process can start before the first unlock (direct-boot BootReceiver), when
    // credential-encrypted SharedPreferences throw. Only [recordBootWall] runs in that state.
    private val prefs by lazy { context.getSharedPreferences("savvy_rd", Context.MODE_PRIVATE) }
    /** Device-protected storage: readable before the first unlock after boot. */
    private val bootPrefs by lazy {
        context.createDeviceProtectedStorageContext().getSharedPreferences("savvy_boot", Context.MODE_PRIVATE)
    }

    fun now(): TimeAnchor = TimeAnchor(
        wallClockMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        bootCount = bootCount(),
    )

    fun bootCount() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    // ---- Time integrity (see core TimeIntegrity) ----------------------------------------

    /**
     * Raw wall clock at the start of this boot. Recorded at LOCKED_BOOT_COMPLETED, before the
     * user can unlock and change the clock. [overwrite] false: a later BOOT_COMPLETED keeps it.
     */
    fun recordBootWall(overwrite: Boolean) {
        val boot = bootCount()
        if (!overwrite && bootPrefs.getInt("bootCount", -2) == boot) return
        bootPrefs.edit().putInt("bootCount", boot)
            .putLong("bootWall", System.currentTimeMillis() - SystemClock.elapsedRealtime()).commit()
    }

    fun bootWallMs(now: TimeAnchor = now()): Long? =
        if (bootPrefs.getInt("bootCount", -2) == now.bootCount) bootPrefs.getLong("bootWall", 0) else null

    /** Last checkpoint of the CURRENT commitment (ignored if it belongs to an older one). */
    fun checkpointFor(c: Commitment): Checkpoint? {
        if (prefs.getString("cpLocalId", null) != c.localId) return null
        return Checkpoint(prefs.getInt("cpBoot", 0), prefs.getLong("cpRt", 0), prefs.getLong("cpWall", 0), prefs.getLong("cpCredited", 0))
    }

    /** Called every minute by the FGS and on ACTION_TIME_CHANGED. */
    @Synchronized
    fun saveCheckpoint(reason: String) {
        val c = commitment ?: return
        val now = now()
        val cp = TimeIntegrity.checkpoint(c, now, checkpointFor(c), bootWallMs(now))
        prefs.edit().putString("cpLocalId", c.localId).putInt("cpBoot", cp.bootCount).putLong("cpRt", cp.elapsedRealtimeMs)
            .putLong("cpWall", cp.wallClockMs).putLong("cpCredited", cp.creditedMs).commit()
        if (reason != "tick") SavvyLog.event("Time", "checkpoint $reason credited=${cp.creditedMs / 60000}min")
    }

    /** Server clock read in this boot, advanced by elapsedRealtime: immune to wall-clock changes. */
    fun recordServerTime(serverMs: Long) {
        val now = now()
        serverOffsetMs = serverMs - now.wallClockMs
        prefs.edit().putLong("srvMs", serverMs).putLong("srvRt", now.elapsedRealtimeMs).putInt("srvBoot", now.bootCount).apply()
    }

    fun trustedServerNow(now: TimeAnchor = now()): Long? {
        if (!prefs.contains("srvMs") || prefs.getInt("srvBoot", -2) != now.bootCount) return null
        val rt = prefs.getLong("srvRt", 0)
        if (now.elapsedRealtimeMs < rt) return null
        return prefs.getLong("srvMs", 0) + (now.elapsedRealtimeMs - rt)
    }

    /** The one remaining-time calculation used by the engine, the FGS and the screens. */
    fun remainingMs(c: Commitment, now: TimeAnchor = now()): Long =
        TimeIntegrity.remainingMs(c, now, trustedServerNow(now), checkpointFor(c), bootWallMs(now))

    var selectedPackages: Set<String>
        get() = prefs.getStringSet("selected", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("selected", v).apply()

    /** "self", "parent" (the parent's own phone) or "child". */
    var role: String
        get() = prefs.getString("role", "self")!!
        set(v) = prefs.edit().putString("role", v).apply()

    /** Parent always-on rule on a child device: blocked even without a focus session. */
    var parentBlockedPackages: Set<String>
        get() = prefs.getStringSet("parentBlocked", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("parentBlocked", v).apply()

    /** The user's own "always blocked" apps: blocked even without a focus session, unblocked with the card. */
    var selfBlockedPackages: Set<String>
        get() = prefs.getStringSet("selfBlocked", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("selfBlocked", v).apply()

    /** Something is blocked even without a focus session, so enforcement must keep running. */
    val hasAlwaysOnBlocks: Boolean get() = parentBlockedPackages.isNotEmpty() || selfBlockedPackages.isNotEmpty()

    /**
     * [pkg] is blocked right now only because the user put it on their always-blocked list
     * (not by the parent, not by the running, unpaused focus session). The card then unblocks
     * the app instead of ending the session.
     */
    fun blockedOnlyBySelf(pkg: String): Boolean {
        if (pkg !in selfBlockedPackages || pkg in parentBlockedPackages) return false
        val c = commitment ?: return true
        if (pkg !in c.blockedPackages) return true
        val now = now()
        return c.pausedUntilElapsedMs != null && c.pauseBootCount == now.bootCount && now.elapsedRealtimeMs < c.pausedUntilElapsedMs!!
    }

    var localEmergencyExits: List<Long>
        get() = prefs.getString("localEmergency", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        set(v) = prefs.edit().putString("localEmergency", v.joinToString(",")).apply()

    /** Minimal to-do list (R&D 12). JSON array of {id, title, minutes, status}. */
    var tasksJson: JSONArray
        get() = JSONArray(prefs.getString("tasks", "[]"))
        set(v) = prefs.edit().putString("tasks", v.toString()).apply()

    /**
     * A task left "active" when its restriction ended some other way (card unlock, emergency
     * exit, expiry) becomes "ended": not done, no longer blocking. Device run 1 open item.
     */
    @Synchronized
    fun reconcileTasks() {
        val active = commitment?.taskRef
        val a = tasksJson
        var changed = false
        for (i in 0 until a.length()) {
            val t = a.getJSONObject(i)
            if (t.getString("status") == "active" && t.getString("id") != active) { t.put("status", "ended"); changed = true }
        }
        if (changed) { tasksJson = a; SavvyLog.event("Tasks", "restriction ended, task(s) marked ended") }
    }

    /** This device token was linked to a parent with a link code (a fresh registration is unlinked). */
    var parentLinked: Boolean
        get() = prefs.getBoolean("parentLinked", false)
        set(v) = prefs.edit().putBoolean("parentLinked", v).apply()

    /** Parent mode: hash of the PIN that lets this child phone leave parent mode (core ParentPin). */
    var parentLeavePinHash: String?
        get() = prefs.getString("leavePin", null)
        set(v) = prefs.edit().putString("leavePin", v).apply()

    /** Emergency exit waiting for the backend cooling-off period: "serverId|available_at". */
    var emergencyPending: String?
        get() = prefs.getString("emergencyPending", null)
        set(v) = prefs.edit().putString("emergencyPending", v).apply()

    @Synchronized
    fun enqueue(e: OfflineEvent) {
        val a = JSONArray(prefs.getString("offlineQueue", "[]"))
        a.put(encodeEvent(e))
        prefs.edit().putString("offlineQueue", a.toString()).commit()
        SavvyLog.event("Offline", "queued ${e::class.simpleName}")
    }

    @Synchronized
    fun offlineQueue(): JSONArray = JSONArray(prefs.getString("offlineQueue", "[]"))

    /** Removes the first [count] events after a successful sync (new ones may have been added meanwhile). */
    @Synchronized
    fun dropSynced(count: Int) {
        val a = offlineQueue()
        val rest = JSONArray()
        for (i in count until a.length()) rest.put(a.get(i))
        prefs.edit().putString("offlineQueue", rest.toString()).commit()
    }

    /** Field names match backend/src/services/syncService.js. */
    private fun encodeEvent(e: OfflineEvent): JSONObject {
        val o = JSONObject().put("event_id", e.eventId).put("local_id", e.localId)
            .put("at", java.time.Instant.ofEpochMilli(e.atMs).toString())
        when (e) {
            is OfflineEvent.CommitmentStarted -> o.put("type", "commitment_started").put("mode", e.mode)
                .put("minutes", e.minutes).put("policy", e.policy).put("task_ref", e.taskRef ?: JSONObject.NULL)
                .put("selection_ref", e.selectionRef ?: JSONObject.NULL)
            is OfflineEvent.CardRelease -> o.put("type", "card_release").put("server_id", e.serverId ?: JSONObject.NULL)
                .put("payload", e.payload).put("source", e.source)
            is OfflineEvent.EmergencyExit -> o.put("type", "emergency_exit").put("server_id", e.serverId ?: JSONObject.NULL)
            is OfflineEvent.TaskCompleted -> o.put("type", "task_completed").put("server_id", e.serverId ?: JSONObject.NULL)
        }
        return o
    }

    var controlMode: ControlMode
        get() = ControlMode.valueOf(prefs.getString("controlMode", ControlMode.SELF.name)!!)
        set(v) = prefs.edit().putString("controlMode", v.name).apply()

    var deviceId: Long
        get() = prefs.getLong("deviceId", 0)
        set(v) = prefs.edit().putLong("deviceId", v).apply()

    var deviceToken: String?
        get() = prefs.getString("deviceToken", null)
        set(v) = prefs.edit().putString("deviceToken", v).apply()

    var boundCardCode: String?
        get() = prefs.getString("boundCard", null)
        set(v) = prefs.edit().putString("boundCard", v).apply()

    /** Debug: last dev card URL, so a tester can present it again (adb / QR). */
    var devCardUrl: String?
        get() = prefs.getString("devCardUrl", null)
        set(v) = prefs.edit().putString("devCardUrl", v).apply()

    var appliedRuleVersion: Int
        get() = prefs.getInt("ruleVersion", 0)
        set(v) = prefs.edit().putInt("ruleVersion", v).apply()

    /** Raw Ed25519 public keys fetched from GET /v1/keys at registration (POC; production embeds them). */
    var grantKeyHex: String?
        get() = prefs.getString("grantKey", null)
        set(v) = prefs.edit().putString("grantKey", v).apply()
    var cardKeyHex: String?
        get() = prefs.getString("cardKey", null)
        set(v) = prefs.edit().putString("cardKey", v).apply()

    /** Last known server clock offset (server - wall), used after reboot when online state is stale. */
    var serverOffsetMs: Long?
        get() = if (prefs.contains("serverOffset")) prefs.getLong("serverOffset", 0) else null
        set(v) = prefs.edit().apply { if (v == null) remove("serverOffset") else putLong("serverOffset", v) }.apply()

    var commitment: Commitment?
        get() = prefs.getString("commitment", null)?.let { decode(JSONObject(it)) }
        set(v) = prefs.edit().apply { if (v == null) remove("commitment") else putString("commitment", encode(v).toString()) }.apply()

    private fun encode(c: Commitment) = JSONObject().apply {
        put("serverId", c.serverId ?: JSONObject.NULL)
        put("mode", c.mode.name); put("policy", c.unlockPolicy.name); put("control", c.controlMode.name)
        put("blocked", JSONArray(c.blockedPackages.toList())); put("duration", c.durationMs)
        put("wall", c.anchor.wallClockMs); put("rt", c.anchor.elapsedRealtimeMs); put("boot", c.anchor.bootCount)
        put("serverEnds", c.serverEndsAtMs ?: JSONObject.NULL); put("task", c.taskRef ?: JSONObject.NULL)
        put("pausedUntil", c.pausedUntilElapsedMs ?: JSONObject.NULL); put("pauseBoot", c.pauseBootCount ?: JSONObject.NULL)
        put("localId", c.localId)
    }

    private fun decode(o: JSONObject) = Commitment(
        serverId = o.optLongOrNull("serverId"),
        mode = Mode.valueOf(o.getString("mode")),
        unlockPolicy = UnlockPolicy.valueOf(o.getString("policy")),
        controlMode = ControlMode.valueOf(o.getString("control")),
        blockedPackages = o.getJSONArray("blocked").let { a -> (0 until a.length()).map(a::getString).toSet() },
        durationMs = o.getLong("duration"),
        anchor = TimeAnchor(o.getLong("wall"), o.getLong("rt"), o.getInt("boot")),
        serverEndsAtMs = o.optLongOrNull("serverEnds"),
        taskRef = if (o.isNull("task")) null else o.getString("task"),
        pausedUntilElapsedMs = o.optLongOrNull("pausedUntil"),
        pauseBootCount = if (o.isNull("pauseBoot")) null else o.getInt("pauseBoot"),
        localId = o.optString("localId").ifEmpty { java.util.UUID.randomUUID().toString() },
    )

    private fun JSONObject.optLongOrNull(k: String) = if (isNull(k)) null else getLong(k)
}
