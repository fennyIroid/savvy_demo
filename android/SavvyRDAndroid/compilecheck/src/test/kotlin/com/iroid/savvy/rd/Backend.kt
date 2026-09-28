package com.iroid.savvy.rd

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

/** Starts the real Node backend (SAVVY_DEV=1) on a free port for end-to-end tests. */
class Backend : AutoCloseable {
    val port = ServerSocket(0).use { it.localPort }
    val url = "http://127.0.0.1:$port"
    private val process: Process

    init {
        val dir = File(System.getProperty("savvy.backendDir"))
        process = ProcessBuilder("node", "src/server.js").directory(dir).redirectErrorStream(true).apply {
            environment()["PORT"] = port.toString()
            environment()["SAVVY_DEV"] = "1"
            environment()["SAVVY_CARD_DOMAINS"] = "go.savvy.test"
            // stdin stays a pipe owned by this JVM: if the JVM dies the backend exits.
            environment()["SAVVY_EXIT_ON_STDIN_CLOSE"] = "1"
        }.redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        val deadline = System.currentTimeMillis() + 15_000
        while (true) {
            if (runCatching { call("GET", "/v1/time", null, null) }.isSuccess) break
            check(System.currentTimeMillis() < deadline) { "backend did not start" }
            Thread.sleep(100)
        }
    }

    fun call(method: String, path: String, body: JSONObject?, token: String?): JSONObject {
        val c = URL(url + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Content-Type", "application/json")
        token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val text = (if (c.responseCode < 300) c.inputStream else c.errorStream).bufferedReader().readText()
        return JSONObject(text)
    }

    fun newCard(): String = call("POST", "/v1/dev/cards", JSONObject(), null).getString("url")

    override fun close() { process.destroy(); process.waitFor() }
}
