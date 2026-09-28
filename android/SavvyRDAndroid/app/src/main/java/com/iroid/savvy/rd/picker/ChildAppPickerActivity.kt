package com.iroid.savvy.rd.picker

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.SavvyActions
import com.iroid.savvy.rd.savvy
import kotlin.concurrent.thread

/**
 * Parent phone (Android R&D 8 / parent remote configuration). Shows the app list the
 * child device reported (PUT /v1/family/inventory) and sends the chosen package names
 * as the child's rule. Works whatever platform the parent uses; the parent here is Android.
 */
class ChildAppPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val childId = intent.getLongExtra(EXTRA_CHILD, 0)
        val status = TextView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        val minutes = EditText(this).apply { setText("120"); hint = "focus minutes (empty = no focus)" }
        val alwaysOn = CheckBox(this).apply { text = "Always-on block (not only during focus)" }
        val selected = mutableSetOf<String>()
        list.addView(status); list.addView(minutes); list.addView(alwaysOn)
        list.addView(Button(this).apply { text = "Send rule to child"; setOnClickListener {
            thread {
                val msg = runCatching {
                    SavvyActions(this@ChildAppPickerActivity).sendRules(childId, selected, minutes.text.toString().toIntOrNull(),
                        UnlockPolicy.CARD_REQUIRED, alwaysOn.isChecked)
                }.getOrElse { "error: $it" }
                runOnUiThread { status.text = msg }
            }
        } })
        setContentView(ScrollView(this).apply { addView(list) })
        thread {
            val apps = runCatching { savvy.backend.childInventory(childId).getJSONArray("apps") }.getOrNull()
            runOnUiThread {
                if (apps == null) { status.text = "Could not load the child's apps"; return@runOnUiThread }
                status.text = "Child $childId: ${apps.length()} apps"
                for (i in 0 until apps.length()) {
                    val a = apps.getJSONObject(i)
                    val pkg = a.getString("package")
                    list.addView(CheckBox(this).apply {
                        text = "${a.getString("label")}\n$pkg"
                        setOnCheckedChangeListener { _, on -> if (on) selected += pkg else selected -= pkg }
                    })
                }
            }
        }
    }

    companion object { const val EXTRA_CHILD = "child" }
}
