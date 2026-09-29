package com.iroid.savvy.rd.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.iroid.savvy.core.UnlockPolicy
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy

/**
 * The block layer drawn straight over the blocked app, so blocking never opens a Savvy
 * activity (and a second Savvy entry in Recents). Two actions:
 *  - Unlock using card or QR: opens BlockActivity (NFC reader mode needs a resumed activity)
 *    on top of the blocked app, so a successful unlock lands the user back in it;
 *  - Close: leaves the blocked app for the home screen.
 *
 * Window, in order of preference:
 *  1. TYPE_ACCESSIBILITY_OVERLAY from the connected SavvyAccessibilityService (no extra permission);
 *  2. TYPE_APPLICATION_OVERLAY when the user granted "Display over other apps" (UsageStats path).
 * [show] returns false when neither is possible; the caller then opens BlockActivity instead.
 *
 * Main thread only (accessibility events and the UsageStats poll both run there).
 */
object BlockOverlay {
    private var view: View? = null
    private var host: Context? = null
    /** Package the layer currently covers, or null when hidden. */
    var shownFor: String? = null; private set
    private var shownTamper = false

    fun show(context: Context, pkg: String, tamper: Boolean): Boolean {
        if (view != null && shownFor == pkg && shownTamper == tamper) return true
        val a11y = SavvyAccessibilityService.instance
        val (h, type) = when {
            a11y != null -> a11y to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Settings.canDrawOverlays(context) -> context.applicationContext to WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> return false
        }
        hide()
        val v = build(h, pkg, tamper)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        return runCatching { h.getSystemService(WindowManager::class.java).addView(v, params) }
            .onSuccess { view = v; host = h; shownFor = pkg; shownTamper = tamper; SavvyLog.event("Overlay", "shown over $pkg") }
            .onFailure { SavvyLog.event("Overlay", "failed $it") }
            .isSuccess
    }

    fun hide() {
        val v = view ?: return
        runCatching { host?.getSystemService(WindowManager::class.java)?.removeView(v) }
        view = null; host = null; shownFor = null
    }

    /**
     * Another app is in front and allowed: the layer no longer belongs there. System UI (shade,
     * volume panel) also reports window changes over the blocked app, so it keeps the layer.
     */
    fun onAllowed(pkg: String) {
        if (view != null && pkg != shownFor && pkg != "com.android.systemui") hide()
    }

    private fun goHome(h: Context) {
        val a11y = h as? SavvyAccessibilityService
        if (a11y != null) a11y.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        else runCatching { h.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Plain views (no Compose outside an activity); same frozen-lake look as the block screen. */
    private fun build(h: Context, pkg: String, tamper: Boolean): View {
        val dp = h.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val ink = 0xFF0B1233.toInt(); val inkSoft = 0xFF6D8196.toInt()
        val snow = 0xFFFFFAFA.toInt(); val navy = 0xFF000080.toInt(); val card = 0xFFEEF2F6.toInt()
        val pm = h.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val repo = h.savvy.repo
        val commitment = repo.commitment

        fun pill(text: String, solid: Boolean, onClick: () -> Unit) = TextView(h).apply {
            this.text = text; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(if (solid) snow else ink)
            background = GradientDrawable().apply { cornerRadius = 30f * dp; setColor(if (solid) navy else card) }
            setPadding(0, px(18), 0, px(18))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = px(12) }
        }

        val content = LinearLayout(h).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(px(28), 0, px(28), 0)
            addView(TextView(h).apply {
                text = "SAVVY"; textSize = 13f; letterSpacing = 0.2f; setTextColor(inkSoft); gravity = Gravity.CENTER
            })
            addView(FrameLayout(h).apply {
                background = GradientDrawable().apply { cornerRadius = 24f * dp; setColor(card) }
                layoutParams = LinearLayout.LayoutParams(px(84), px(84)).apply { topMargin = px(24); bottomMargin = px(20) }
                addView(ImageView(h).apply {
                    runCatching { setImageDrawable(pm.getApplicationIcon(pkg)) }
                }, FrameLayout.LayoutParams(px(56), px(56), Gravity.CENTER))
            })
            addView(TextView(h).apply {
                textSize = 24f; setTextColor(ink); gravity = Gravity.CENTER
                text = if (tamper) "This setting is locked" else "$label has been blocked by Savvy"
            })
            addView(TextView(h).apply {
                textSize = 16f; setTextColor(inkSoft); gravity = Gravity.CENTER
                setPadding(0, px(10), 0, px(32))
                text = when {
                    tamper -> "Your parent's Savvy rules protect this screen."
                    repo.blockedOnlyBySelf(pkg) -> "You keep this app always blocked. Unblock it with your Savvy card or its QR code."
                    commitment == null -> "Your parent has blocked this app."
                    commitment.unlockPolicy == UnlockPolicy.LOCKED -> "This session is locked until the timer runs out."
                    else -> "Unlock it with your Savvy card or by scanning its QR code."
                }
            })
            addView(pill("Unlock using card or QR", solid = true) {
                hide()
                runCatching { h.startActivity(h.savvy.engine.blockIntent(pkg, tamper)) }
                    .onFailure { SavvyLog.event("Overlay", "card screen launch failed $it") }
            })
            addView(pill("Close", solid = false) { hide(); goHome(h) })
        }

        return FrameLayout(h).apply {
            setBackgroundColor(snow)
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            // Back must not reveal the blocked app: treat it as Close.
            isFocusableInTouchMode = true
            setOnKeyListener { _, code, e ->
                if (code == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { hide(); goHome(h) }
                code == KeyEvent.KEYCODE_BACK
            }
            post { requestFocus() }
        }
    }
}
