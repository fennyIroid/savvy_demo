package com.iroid.savvy.rd.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.iroid.savvy.core.RestrictionPolicy.Decision
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * Approach B (primary). Receives TYPE_WINDOW_STATE_CHANGED within milliseconds of
 * an app coming to the front, from any launch path (launcher, notification,
 * recents, deep link, split screen). It does not read window content.
 *
 * Block screen delivery, two layers:
 *  1. go Home + start BlockActivity (engine). Background activity launch from an
 *     accessibility service is expected to work but is no longer listed as an
 *     exemption in current docs (test A-BAL-1).
 *  2. fallback: a TYPE_ACCESSIBILITY_OVERLAY window, which needs no
 *     SYSTEM_ALERT_WINDOW. Its button opens BlockActivity from a user tap, which is
 *     always allowed. Removed when a Savvy window comes to the front or on Close.
 *
 * Play policy: allowed as a non-accessibility-tool with declaration, prominent
 * disclosure, consent and video. Using it to stop disabling / uninstall is only
 * allowed in parent-authorised mode (RestrictionPolicy handles that split).
 */
class SavvyAccessibilityService : AccessibilityService() {
    private var overlay: View? = null

    override fun onServiceConnected() {
        SavvyLog.event("A11y", "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            // Our own overlay window also reports our package (class android.widget.*),
            // so only a Savvy Activity coming to the front removes it.
            if (event.className?.toString()?.startsWith("com.iroid.savvy.rd.") == true) removeOverlay()
            return
        }
        val decision = savvy.engine.onForeground(pkg, event.className?.toString(),
            BlockingEngine.Source.ACCESSIBILITY, event.eventTime)
        if (decision is Decision.BlockApp || decision is Decision.BlockTamperScreen) {
            // Leave the blocked app first so it is not visible behind the block screen.
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay(pkg, decision is Decision.BlockTamperScreen)
        }
        // The overlay stays over the launcher until BlockActivity (our package) is in
        // front or the user taps Close; if BlockActivity started normally it is removed at once.
    }

    private fun showOverlay(blocked: String, tamper: Boolean) {
        if (overlay != null) return
        val wm = getSystemService(WindowManager::class.java)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF0111827.toInt())
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply {
                textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
                text = if (tamper) "This setting is locked by your parent's Savvy rules" else "$blocked is paused by Savvy"
            })
            addView(Button(context).apply {
                text = "Unlock with Savvy card"
                setOnClickListener {
                    removeOverlay()
                    startActivity(Intent(context, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(BlockActivity.EXTRA_BLOCKED, blocked))
                }
            })
            addView(Button(context).apply { text = "Close"; setOnClickListener { removeOverlay() } })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT,
        )
        runCatching { wm.addView(view, params); overlay = view }
            .onFailure { SavvyLog.event("A11y", "overlay failed $it") }
    }

    private fun removeOverlay() {
        overlay?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeOverlay()
        SavvyLog.event("A11y", "destroyed (disabled, force-stopped or killed)")
        super.onDestroy()
    }
}
