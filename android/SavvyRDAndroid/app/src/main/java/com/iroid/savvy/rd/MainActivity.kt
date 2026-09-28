package com.iroid.savvy.rd

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.iroid.savvy.core.ControlMode
import com.iroid.savvy.core.Mode
import com.iroid.savvy.core.TimeIntegrity
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.admin.SavvyDeviceAdminReceiver
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.picker.AppPickerActivity
import com.iroid.savvy.rd.picker.ChildAppPickerActivity
import com.iroid.savvy.rd.service.TamperMonitor
import com.iroid.savvy.rd.service.UsageMonitorService
import kotlin.concurrent.thread

/** Android test lab. Each button maps to a test in docs/ANDROID_POC_RESULTS.md. */
class MainActivity : AppCompatActivity() {
    private lateinit var out: TextView
    private lateinit var tasksBox: LinearLayout
    private val repo get() = savvy.repo
    private val actions by lazy { SavvyActions(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        val email = EditText(this).apply { setText("tester@savvy.test") }
        val minutes = EditText(this).apply { setText("360"); hint = "minutes" }
        val linkCode = EditText(this).apply { hint = "parent link code" }
        val taskTitle = EditText(this).apply { hint = "task title" }
        tasksBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        fun header(t: String) = col.addView(TextView(this).apply { text = t; textSize = 16f; setPadding(0, 24, 0, 4) })
        fun button(label: String, action: () -> Unit) = col.addView(Button(this).apply { text = label; setOnClickListener { action() } })

        col.addView(out)
        header("Role and account")
        button("Role: SELF") { repo.role = "self"; repo.controlMode = ControlMode.SELF; show("role self") }
        button("Role: PARENT (this is the parent's phone)") { repo.role = "parent"; show("role parent") }
        button("Role: CHILD (parent sets up this phone)") { repo.role = "child"; repo.controlMode = ControlMode.PARENT; show("role child") }
        col.addView(email)
        button("Register this install") { bg { actions.register(email.text.toString()) } }

        header("Savvy card")
        button("Register my card (tap NFC or scan QR)") {
            startActivity(Intent(this, BlockActivity::class.java).putExtra(BlockActivity.EXTRA_MODE, BlockActivity.MODE_REGISTER))
        }
        if (BuildConfig.DEBUG) button("DEV: create + register a test card") { bg { actions.devCard() } }

        header("Permissions")
        button("Choose apps to block") { startActivity(Intent(this, AppPickerActivity::class.java)) }
        button("Enable Accessibility (disclosure first)") { showDisclosure() }
        button("Grant Usage access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        button("Grant overlay (display over other apps)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("Battery: unrestricted") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button("CHILD: activate device admin") {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, SavvyDeviceAdminReceiver::class.java))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets your parent's Savvy rules stay on this phone."))
        }

        header("Focus / commitment")
        col.addView(minutes)
        fun mins() = minutes.text.toString().toIntOrNull() ?: 60
        button("Start Study, card required") { bg { actions.start(Mode.STUDY, mins(), UnlockPolicy.CARD_REQUIRED) } }
        button("Start Work, locked") { bg { actions.start(Mode.WORK, mins(), UnlockPolicy.LOCKED) } }
        button("Start Sleep, free") { bg { actions.start(Mode.SLEEP, mins(), UnlockPolicy.FREE) } }
        button("Open block screen (scan card / QR)") { startActivity(Intent(this, BlockActivity::class.java)) }
        button("End session (free sessions only)") { bg { actions.endFree() } }
        button("Emergency exit") { bg { actions.emergency("test") } }

        header("To-do restriction")
        col.addView(taskTitle)
        button("Add task (uses minutes above)") { show(actions.addTask(taskTitle.text.toString().ifBlank { "Task" }, mins())); renderTasks() }
        col.addView(tasksBox)

        header("Child phone")
        col.addView(linkCode)
        button("Link as child") { bg { actions.linkAsChild(linkCode.text.toString()) } }
        button("Sync parent rules / upload app list / usage") { bg { actions.housekeeping() } }

        header("Parent phone")
        button("Create link code") { bg { "link code: ${actions.createLinkCode()} (15 min)" } }
        button("Choose apps for my child") { thread {
            val ids = runCatching { actions.children() }.getOrDefault(emptyList())
            runOnUiThread {
                if (ids.isEmpty()) show("no linked child") else startActivity(
                    Intent(this, ChildAppPickerActivity::class.java).putExtra(ChildAppPickerActivity.EXTRA_CHILD, ids.first()))
            }
        } }
        button("Child status and usage") { bg { actions.children().joinToString("\n\n") { "child $it: ${actions.childReport(it)}" } } }

        header("Insights and diagnostics")
        button("Screen time today (this phone)") { bg { actions.screenTimeToday() } }
        button("Focus time and streak") { bg { actions.insights() } }
        button("Sync offline events now") { bg { actions.flushOffline() } }
        button("Status / heartbeat") { bg {
            val c = repo.commitment
            "${actions.housekeeping()}\n${TamperMonitor.read(this)}\ncommitment=${c?.let { "${it.mode} ${it.unlockPolicy} " +
                "remaining=${TimeIntegrity.remainingMs(it, repo.now()) / 60000}min verdict=${TimeIntegrity.check(it.anchor, repo.now())}" }}" +
                "\nparent always-on=${repo.parentBlockedPackages.size} offline queue=${repo.offlineQueue().length()}"
        } }
        button("Show log") { show(SavvyLog.read().takeLast(6000)) }
        button("Share log (test evidence)") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, SavvyLog.read().takeLast(90_000)), "Savvy R&D log"))
        }
        setContentView(ScrollView(this).apply { addView(col) })
        // Android 13+: without this the "focus is on" FGS notification is hidden, and Play's
        // monitoring rules expect a visible notification in parent mode.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        // Force stop kills the enforcement service and nothing restarts it (Pixel 4 test,
        // 28 Sep 2026): re-arm it whenever Savvy is opened with a commitment or parent rule.
        if (repo.commitment != null || repo.parentBlockedPackages.isNotEmpty()) UsageMonitorService.start(this)
        renderTasks()
        bg { actions.restore() }
    }

    private fun renderTasks() {
        tasksBox.removeAllViews()
        val a = repo.tasksJson
        for (i in 0 until a.length()) {
            val t = a.getJSONObject(i)
            val id = t.getString("id")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = "${t.getString("title")} (${t.getInt("minutes")} min) [${t.getString("status")}]  " })
            when (t.getString("status")) {
                "pending" -> row.addView(Button(this).apply { text = "Start"; setOnClickListener {
                    actions.setTaskStatus(id, "active"); renderTasks()
                    bg { actions.start(Mode.TASK, t.getInt("minutes"), UnlockPolicy.CARD_REQUIRED, taskRef = id) }
                } })
                "active" -> row.addView(Button(this).apply { text = "Done"; setOnClickListener {
                    val c = repo.commitment
                    if (c?.taskRef == id && c.unlockPolicy == UnlockPolicy.CARD_REQUIRED) {
                        // Card-protected task: finishing it needs the card too (backend rule, OPEN_ITEMS C3).
                        startActivity(Intent(this@MainActivity, BlockActivity::class.java)
                            .putExtra(BlockActivity.EXTRA_MODE, BlockActivity.MODE_TASK).putExtra(BlockActivity.EXTRA_TASK_ID, id))
                    } else bg { actions.completeTask(id).also { runOnUiThread { renderTasks() } } }
                } })
            }
            tasksBox.addView(row)
        }
    }

    /** Play policy: prominent disclosure and affirmative consent BEFORE sending the user to Accessibility settings. */
    private fun showDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Allow Savvy to detect the app on screen?")
            .setMessage(getString(R.string.prominent_disclosure))
            .setPositiveButton("Agree") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("No thanks", null)
            .show()
    }

    private fun bg(block: () -> String) = thread {
        val text = runCatching(block).getOrElse { "error: $it" }
        runOnUiThread { show(text) }
    }

    private fun show(text: String) { out.text = text }
}
