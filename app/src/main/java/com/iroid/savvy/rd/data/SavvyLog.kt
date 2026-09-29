package com.iroid.savvy.rd.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.time.Instant

/** Lifecycle log with wall and monotonic time, so device tests can be checked afterwards. */
object SavvyLog {
    private lateinit var file: File

    fun init(context: Context) { file = File(context.filesDir, "savvy-rd.log") }

    @Synchronized
    fun event(source: String, message: String) {
        Log.i("SavvyRD", "[$source] $message")
        if (!::file.isInitialized) return
        if (file.length() > 512 * 1024) file.writeText("")
        file.appendText("${Instant.now()} rt=${SystemClock.elapsedRealtime()} [$source] $message\n")
    }

    fun read(): String = if (::file.isInitialized && file.exists()) file.readText() else "(empty)"
    fun clear() { if (::file.isInitialized) file.writeText("") }
}
