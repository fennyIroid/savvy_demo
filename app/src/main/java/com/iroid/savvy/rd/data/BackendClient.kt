package com.iroid.savvy.rd.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal blocking client for backend/ (call from a background thread). */
class BackendClient(private val baseUrl: () -> String, private val token: () -> String?) {
    class ApiError(val status: Int, val code: String) : Exception("$status $code")

    fun keys() = send("GET", "/v1/keys", null)
    fun sync(events: org.json.JSONArray) = send("POST", "/v1/sync", JSONObject().put("events", events))
    fun insights(tz: String) = send("GET", "/v1/insights/summary?tz=${java.net.URLEncoder.encode(tz, "UTF-8")}", null)
    fun createLinkCode() = send("POST", "/v1/family/link-codes", JSONObject())
    fun children() = send("GET", "/v1/family/children", null)
    fun putRules(childDeviceId: Long, rules: JSONObject) = send("PUT", "/v1/family/children/$childDeviceId/rules", JSONObject().put("rules", rules))
    fun childStatus(childDeviceId: Long) = send("GET", "/v1/family/children/$childDeviceId/status", null)
    fun childInventory(childDeviceId: Long) = send("GET", "/v1/family/children/$childDeviceId/inventory", null)
    fun childUsage(childDeviceId: Long) = send("GET", "/v1/family/children/$childDeviceId/usage?days=7", null)
    fun putInventory(apps: org.json.JSONArray) = send("PUT", "/v1/family/inventory", JSONObject().put("apps", apps))
    fun putDailyUsage(date: String, apps: org.json.JSONArray) = send("PUT", "/v1/usage/daily", JSONObject().put("date", date).put("apps", apps))
    fun register(email: String, role: String) = send("POST", "/v1/devices/register",
        JSONObject().put("email", email).put("platform", "android").put("role", role))
    fun registerCard(payload: String, source: String) = send("POST", "/v1/cards/register",
        JSONObject().put("payload", payload).put("source", source))
    fun startCommitment(mode: String, minutes: Int, policy: String, taskRef: String?, selectionRef: String? = null) = send("POST", "/v1/commitments",
        JSONObject().put("mode", mode).put("duration_minutes", minutes).put("unlock_policy", policy).putOpt("task_ref", taskRef)
            .putOpt("selection_ref", selectionRef))
    fun active() = send("GET", "/v1/commitments/active", null)
    fun release(id: Long, method: String, payload: String?, source: String, presenceToken: String? = null) =
        send("POST", "/v1/commitments/$id/release",
            JSONObject().put("method", method).putOpt("payload", payload).put("source", source).putOpt("presence_token", presenceToken))
    /** Dev-only card factory (backend SAVVY_DEV=1): a new signed card URL to write to a tag or show as QR. */
    fun devCard() = send("POST", "/v1/dev/cards", JSONObject().put("format", "signed"))
    fun liveStart(cardCode: String) = send("POST", "/v1/cards/live/start", JSONObject().put("card_code", cardCode))
    fun liveStep(sessionId: String, response: String) = send("POST", "/v1/cards/live/step",
        JSONObject().put("session_id", sessionId).put("response", response))
    fun liveFinish(sessionId: String, response: String) = send("POST", "/v1/cards/live/finish",
        JSONObject().put("session_id", sessionId).put("response", response))
    fun emergencyUsage() = send("GET", "/v1/emergency-exits/usage", null)
    fun emergencyExit(id: Long, reason: String) = send("POST", "/v1/commitments/$id/emergency-exit", JSONObject().put("reason", reason))
    fun heartbeat(status: JSONObject) = send("POST", "/v1/devices/heartbeat", status)
    fun redeemLink(code: String) = send("POST", "/v1/family/link", JSONObject().put("code", code))
    fun pullRules(since: Int) = send("GET", "/v1/family/rules?since_version=$since", null)
    fun ackRules(version: Int) = send("POST", "/v1/family/rules/ack", JSONObject().put("version", version))

    private fun send(method: String, path: String, body: JSONObject?): JSONObject {
        val conn = (URL(baseUrl() + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5000; readTimeout = 8000
            setRequestProperty("Content-Type", "application/json")
            token()?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; outputStream.use { it.write(body.toString().toByteArray()) } }
        }
        val status = conn.responseCode
        val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: "{}"
        val json = JSONObject(text)
        if (status !in 200..299) throw ApiError(status, json.optString("error", "http_$status"))
        return json
    }
}
