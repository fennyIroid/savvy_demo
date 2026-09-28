package com.iroid.savvy.rd.picker

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.iroid.savvy.rd.savvy

/**
 * Android R&D 1. Lists launchable apps through the <queries> launcher intent
 * (no QUERY_ALL_PACKAGES). Unlike iOS, Savvy sees real package names and labels,
 * so the backend can store the selection and a parent can choose from a list
 * reported by the child device.
 */
class AppPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.lowercase() }
        val selected = savvy.repo.selectedPackages.toMutableSet()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        launchable.forEach { (pkg, label) ->
            list.addView(CheckBox(this).apply {
                text = "$label\n$pkg"
                isChecked = pkg in selected
                setOnCheckedChangeListener { _, on -> if (on) selected += pkg else selected -= pkg; savvy.repo.selectedPackages = selected }
            })
        }
        setContentView(ScrollView(this).apply { addView(list) })
    }
}
