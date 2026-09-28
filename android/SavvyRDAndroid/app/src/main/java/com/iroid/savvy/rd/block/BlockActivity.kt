package com.iroid.savvy.rd.block

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.iroid.savvy.rd.SavvyActions
import com.iroid.savvy.rd.UnlockCoordinator
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.nfc.NfcCardReader
import com.iroid.savvy.rd.savvy
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.concurrent.thread

/**
 * Savvy block screen. Shown over the blocked app by the engine, and also the
 * target of card taps (NDEF_DISCOVERED / App Link) when Savvy is in the
 * background or not running. While visible, NFC reader mode is on, so holding
 * the card is the only action needed to unlock.
 *
 * The same card input (reader mode, QR, card link) also serves two other flows,
 * selected by [EXTRA_MODE]: binding a card to the account, and completing a
 * card-protected to-do task.
 */
class BlockActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var reader: NfcCardReader
    private val unlock by lazy { UnlockCoordinator(this) }
    private val actions by lazy { SavvyActions(this) }
    private var mode = MODE_UNLOCK
    private var taskId: String? = null

    private val qr = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleCard(it, "qr") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blocked = intent.getStringExtra(EXTRA_BLOCKED)
        val tamper = intent.getBooleanExtra(EXTRA_TAMPER, false)
        readMode(intent)
        status = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER }
        reader = NfcCardReader(this, liveBackend = savvy.backend) { url, uid, ms, token ->
            if (token != null) handleLiveProof(url, token, "uid=$uid read=${ms}ms") else handleCard(url, "nfc", "uid=$uid read=${ms}ms")
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply {
                textSize = 24f; gravity = Gravity.CENTER
                text = when {
                    mode == MODE_REGISTER -> "Register your Savvy card"
                    mode == MODE_TASK -> "Scan your Savvy card to finish the task"
                    tamper -> "This setting is locked by your parent's Savvy rules"
                    else -> "${blocked ?: "This app"} is paused by Savvy"
                }
            })
            addView(status)
            addView(Button(context).apply { text = "Scan card QR instead"; setOnClickListener {
                qr.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(true))
            } })
            addView(Button(context).apply { text = "Turn on NFC"; setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } })
            if (mode == MODE_UNLOCK) addView(Button(context).apply { text = "Emergency exit"; setOnClickListener { emergency() } })
            addView(Button(context).apply { text = "Go to Home"; setOnClickListener { goHome() } })
        })
        // Back must not return to the blocked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = goHome() })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A card link arriving while registering / finishing a task keeps that mode.
        if (intent.hasExtra(EXTRA_MODE)) readMode(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        reader.enable()
        status.text = when (reader.state) {
            "ready" -> "Hold your Savvy card to the back of your phone${if (mode == MODE_UNLOCK) " to unlock" else ""}."
            "nfc_disabled" -> "NFC is off. Turn it on, or scan the card QR."
            else -> "This phone has no NFC. Scan the card QR."
        }
    }

    override fun onPause() {
        resumed = false
        reader.disable()
        super.onPause()
    }

    /** Card tapped while Savvy was in background / not running: tag dispatch or App Link. */
    private fun handleIntent(intent: Intent) {
        val url = when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED -> intent.data?.toString()
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        } ?: return
        handleCard(url, if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) "nfc_background" else "link")
    }

    private fun readMode(i: Intent) {
        mode = i.getStringExtra(EXTRA_MODE) ?: MODE_UNLOCK
        taskId = i.getStringExtra(EXTRA_TASK_ID)
    }

    private fun handleCard(raw: String, source: String, note: String = "") {
        status.text = "Checking card..."
        if (mode != MODE_UNLOCK) { handleOtherMode(raw, source, note); return }
        thread {
            val outcome = unlock.handleCard(raw, source)
            SavvyLog.event("Block", "card source=$source $note -> $outcome")
            runOnUiThread {
                when (outcome) {
                    is UnlockCoordinator.Outcome.Released, is UnlockCoordinator.Outcome.Paused -> {
                        status.text = "Unlocked"
                        finish()
                    }
                    else -> status.text = "Not unlocked: $outcome"
                }
            }
        }
    }

    private fun handleOtherMode(raw: String, source: String, note: String) = thread {
        val msg = runCatching {
            if (mode == MODE_REGISTER) actions.registerCard(raw, source) else actions.completeTask(taskId ?: "", raw)
        }.getOrElse { "error: $it" }
        SavvyLog.event("Block", "$mode card source=$source $note -> $msg")
        runOnUiThread {
            status.text = msg
            val repo = savvy.repo
            val done = if (mode == MODE_REGISTER) repo.boundCardCode != null && msg.contains("registered")
                else repo.commitment?.taskRef != taskId
            if (done) finish()
        }
    }

    private fun emergency() {
        status.text = "Requesting emergency exit..."
        thread {
            val msg = runCatching { actions.emergency("block_screen") }.getOrElse { "error: $it" }
            SavvyLog.event("Block", "emergency -> $msg")
            runOnUiThread {
                status.text = msg
                val c = savvy.repo.commitment
                if (c == null || (c.pausedUntilElapsedMs ?: 0) > savvy.repo.now().elapsedRealtimeMs) finish()
            }
        }
    }

    private fun handleLiveProof(url: String, token: String, note: String) {
        status.text = "Checking card..."
        thread {
            val outcome = unlock.handleLiveProof(url, token)
            SavvyLog.event("Block", "live proof $note -> $outcome")
            runOnUiThread {
                if (outcome is UnlockCoordinator.Outcome.Released || outcome is UnlockCoordinator.Outcome.Paused) finish()
                else status.text = "Not unlocked: $outcome"
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED = "blocked"
        const val EXTRA_TAMPER = "tamper"
        /** True while the block screen is in front; the accessibility service's overlay fallback checks it. */
        @Volatile var resumed = false
        const val EXTRA_MODE = "mode"
        const val EXTRA_TASK_ID = "task_id"
        const val MODE_UNLOCK = "unlock"
        const val MODE_REGISTER = "register"
        const val MODE_TASK = "task"
    }
}
