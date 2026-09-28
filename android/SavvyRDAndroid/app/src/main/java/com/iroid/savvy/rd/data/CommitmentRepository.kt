package com.iroid.savvy.rd.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.iroid.savvy.core.Commitment
import com.iroid.savvy.core.ControlMode
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.OfflineEvent
import com.iroid.savvy.core.TimeAnchor
import com.iroid.savvy.core.UnlockPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local enforcement state. Everything the blocking engine needs is here so it
 * keeps working offline and after reboot. POC: plain SharedPreferences.
 * Production: EncryptedSharedPreferences / Keystore for the device token.
 */
class CommitmentRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("savvy_rd", Context.MODE_PRIVATE)

    fun now(): TimeAnchor = TimeAnchor(
        wallClockMs = System.currentTimeMillis(),
        elapsedRealtimeMs = SystemClock.elapsedRealtime(),
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
    )

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

    var localEmergencyExits: List<Long>
        get() = prefs.getString("localEmergency", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        set(v) = prefs.edit().putString("localEmergency", v.joinToString(",")).apply()

    /** Minimal to-do list (R&D 12). JSON array of {id, title, minutes, status}. */
    var tasksJson: JSONArray
        get() = JSONArray(prefs.getString("tasks", "[]"))
        set(v) = prefs.edit().putString("tasks", v.toString()).apply()

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
