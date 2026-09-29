package com.iroid.savvy.rd.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.iroid.savvy.core.RestrictionPolicy.Decision
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * Approach B (primary). Receives TYPE_WINDOW_STATE_CHANGED within milliseconds of
 * an app coming to the front, from any launch path (launcher, notification,
 * recents, deep link, split screen). It does not read window content.
 *
 * Block delivery: [BlockOverlay] as a TYPE_ACCESSIBILITY_OVERLAY window straight over the
 * blocked app (no SYSTEM_ALERT_WINDOW needed). Opening BlockActivity for every block put a
 * second Savvy task in Recents; the card screen now opens only from the layer's Unlock button.
 * Fallback, only if the layer cannot be added: go Home, THEN start BlockActivity. Order
 * matters: on a Pixel 4 (Android 13) starting BlockActivity first and sending Home after it
 * left the launcher on top of the block screen (device test 28 Sep 2026).
 *
 * Play policy: allowed as a non-accessibility-tool with declaration, prominent
 * disclosure, consent and video. Using it to stop disabling / uninstall is only
 * allowed in parent-authorised mode (RestrictionPolicy handles that split).
 */
class SavvyAccessibilityService : AccessibilityService() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        SavvyLog.event("A11y", "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) {
            // Our own overlay window also reports our package (class android.widget.*),
            // so only a Savvy Activity coming to the front removes it.
            if (event.className?.toString()?.startsWith("com.iroid.savvy.rd.") == true) BlockOverlay.hide()
            return
        }
        val engine = savvy.engine
        val decision = engine.onForeground(pkg, event.className?.toString(),
            BlockingEngine.Source.ACCESSIBILITY, event.eventTime, launch = false)
        if (decision !is Decision.BlockApp && decision !is Decision.BlockTamperScreen) return
        val tamper = decision is Decision.BlockTamperScreen
        // Idempotent: the blocked app emits several window events per launch.
        if (BlockOverlay.show(this, pkg, tamper) || !engine.shouldLaunch(pkg)) return
        SavvyLog.event("A11y", "overlay unavailable, opening block screen")
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            runCatching { startActivity(engine.blockIntent(pkg, tamper)) }
                .onFailure { SavvyLog.event("A11y", "block screen launch failed $it") }
        }, HOME_SETTLE_MS)
    }

    override fun onInterrupt() {}

    companion object {
        const val HOME_SETTLE_MS = 250L
        /** The connected service, for [BlockOverlay]'s window; null when Accessibility is off. */
        @Volatile var instance: SavvyAccessibilityService? = null; private set
    }

    override fun onDestroy() {
        BlockOverlay.hide()
        instance = null
        SavvyLog.event("A11y", "destroyed (disabled, force-stopped or killed)")
        super.onDestroy()
    }
}
