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
 */
class BlockActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var reader: NfcCardReader
    private val unlock by lazy { UnlockCoordinator(this) }

    private val qr = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleCard(it, "qr") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blocked = intent.getStringExtra(EXTRA_BLOCKED)
        val tamper = intent.getBooleanExtra(EXTRA_TAMPER, false)
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
                text = if (tamper) "This setting is locked by your parent's Savvy rules" else "${blocked ?: "This app"} is paused by Savvy"
            })
            addView(status)
            addView(Button(context).apply { text = "Scan card QR instead"; setOnClickListener {
                qr.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(true))
            } })
            addView(Button(context).apply { text = "Turn on NFC"; setOnClickListener { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) } })
            addView(Button(context).apply { text = "Go to Home"; setOnClickListener { goHome() } })
        })
        // Back must not return to the blocked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = goHome() })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        reader.enable()
        status.text = when (reader.state) {
            "ready" -> "Hold your Savvy card to the back of your phone to unlock."
            "nfc_disabled" -> "NFC is off. Turn it on, or scan the card QR."
            else -> "This phone has no NFC. Scan the card QR."
        }
    }

    override fun onPause() {
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

    private fun handleCard(raw: String, source: String, note: String = "") {
        status.text = "Checking card..."
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
    }
}
