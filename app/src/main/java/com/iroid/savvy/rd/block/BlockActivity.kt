package com.iroid.savvy.rd.block

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.iroid.savvy.rd.SavvyActions
import com.iroid.savvy.rd.UnlockCoordinator
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.nfc.NfcCardReader
import com.iroid.savvy.rd.nfc.NfcTagWriter
import com.iroid.savvy.rd.savvy
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.concurrent.thread

/**
 * Savvy card screen. Opened from the block layer's Unlock button (service/BlockOverlay), or
 * by the engine when no overlay window is available, and also the
 * target of card taps (NDEF_DISCOVERED / App Link) when Savvy is in the
 * background or not running. While visible, NFC reader mode is on, so holding
 * the card is the only action needed to unlock.
 *
 * The same card input (reader mode, QR, card link) also serves two other flows,
 * selected by [EXTRA_MODE]: binding a card to the account, and completing a
 * card-protected to-do task.
 */
class BlockActivity : AppCompatActivity() {
    private lateinit var reader: NfcCardReader
    /** Compose screen in the app (src/main/ui); a no-op stub in the Robolectric suite. */
    private lateinit var ui: BlockScreen
    private val unlock by lazy { UnlockCoordinator(this) }
    private val actions by lazy { SavvyActions(this) }
    private var mode = MODE_UNLOCK
    private var taskId: String? = null
    private var writeUrl: String? = null
    /** Opened over a blocked app: after unlocking, step back to it instead of showing Savvy. */
    private var returnToBlocked = false
    var state = BlockUiState(); private set

    private val qr = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleCard(it, "qr") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readMode(intent)
        returnToBlocked = intent.getBooleanExtra(EXTRA_RETURN_TO_BLOCKED, false)
        reader = NfcCardReader(this, liveBackend = savvy.backend) { url, uid, ms, token ->
            if (token != null) handleLiveProof(url, token, "uid=$uid read=${ms}ms") else handleCard(url, "nfc", "uid=$uid read=${ms}ms")
        }
        armWriter()
        ui = BlockScreen(this)
        update { BlockUiState(mode = mode, blockedPackage = intent.getStringExtra(EXTRA_BLOCKED),
            tamper = intent.getBooleanExtra(EXTRA_TAMPER, false), nfc = reader.state) }
        // Back must not return to the blocked app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = goHome() })
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A card link arriving while registering / finishing a task keeps that mode.
        if (intent.hasExtra(EXTRA_MODE)) readMode(intent)
        // The engine blocked another app while this screen was open (e.g. over "Unblock app"): show that app.
        if (intent.hasExtra(EXTRA_BLOCKED)) {
            if (!intent.hasExtra(EXTRA_MODE)) readMode(intent)
            returnToBlocked = intent.getBooleanExtra(EXTRA_RETURN_TO_BLOCKED, false)
            update { it.copy(blockedPackage = intent.getStringExtra(EXTRA_BLOCKED), tamper = intent.getBooleanExtra(EXTRA_TAMPER, false),
                phase = BlockUiState.Phase.WAITING, message = null) }
        }
        update { it.copy(mode = mode) }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        reader.enable()
        update { it.copy(nfc = reader.state) }
    }

    override fun onPause() {
        reader.disable()
        super.onPause()
    }

    private fun update(f: (BlockUiState) -> BlockUiState) {
        state = f(state)
        ui.render(state)
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
        writeUrl = i.getStringExtra(EXTRA_URL)
        if (::reader.isInitialized) armWriter()
    }

    /** MODE_WRITE (debug): the next tag held to the phone gets [writeUrl] instead of being read. */
    private fun armWriter() {
        val url = writeUrl
        reader.onRawTag = if (mode == MODE_WRITE && url != null) { tag ->
            runOnUiThread { checking() }
            val msg = runCatching { NfcTagWriter.write(tag, url, packageName) }.getOrElse { "error: $it" }
            SavvyLog.event("NFC", "write -> $msg")
            runOnUiThread { if (msg.startsWith("written") || msg.startsWith("formatted")) succeeded(msg) else failed(msg) }
        } else null
    }

    private fun checking() = update { it.copy(phase = BlockUiState.Phase.CHECKING, message = null) }
    private fun failed(msg: String) = update { it.copy(phase = BlockUiState.Phase.FAILED, message = msg) }
    private fun succeeded(msg: String) {
        update { it.copy(phase = BlockUiState.Phase.SUCCESS, message = msg) }
        ui.onSuccess {
            // This screen shares Savvy's task, so finishing alone would show Savvy's home;
            // sending the task back brings the now unlocked app to the front again.
            if (returnToBlocked && mode == MODE_UNLOCK) moveTaskToBack(true)
            finish()
        }
    }

    /**
     * The app on this screen is blocked only by the user's always-blocked list (or this is the
     * unblock-one-app mode): the card removes it from that list instead of ending the session.
     */
    private fun unblocksAlwaysBlocked(): Boolean {
        val pkg = state.blockedPackage ?: return false
        return mode == MODE_UNBLOCK_APP || (mode == MODE_UNLOCK && !state.tamper && savvy.repo.blockedOnlyBySelf(pkg))
    }

    private fun handleAlwaysBlocked(raw: String, source: String, note: String, presenceVerified: Boolean) {
        val pkg = state.blockedPackage ?: return
        checking()
        thread {
            val msg = runCatching { actions.unblockAlways(pkg, raw, presenceVerified) }.getOrElse { "error: $it" }
            SavvyLog.event("Block", "always-blocked card source=$source $note -> $msg")
            runOnUiThread { if (pkg !in savvy.repo.selfBlockedPackages) succeeded(msg) else failed(msg) }
        }
    }

    private fun handleCard(raw: String, source: String, note: String = "") {
        if (mode == MODE_WRITE) return // writing a test tag, not reading cards
        if (unblocksAlwaysBlocked()) { handleAlwaysBlocked(raw, source, note, presenceVerified = false); return }
        checking()
        if (mode != MODE_UNLOCK) { handleOtherMode(raw, source, note); return }
        thread {
            val outcome = unlock.handleCard(raw, source)
            SavvyLog.event("Block", "card source=$source $note -> $outcome")
            runOnUiThread {
                when (outcome) {
                    is UnlockCoordinator.Outcome.Released, is UnlockCoordinator.Outcome.Paused -> succeeded(outcome.toString())
                    is UnlockCoordinator.Outcome.Rejected -> failed(outcome.reason)
                    else -> failed(outcome.toString())
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
            val repo = savvy.repo
            val done = if (mode == MODE_REGISTER) repo.boundCardCode != null && msg.contains("registered")
                else repo.commitment?.taskRef != taskId
            if (done) succeeded(msg) else failed(msg)
        }
    }

    /** Called by the screen after the user confirmed. */
    fun emergency() {
        checking()
        thread {
            val msg = runCatching { actions.emergency("block_screen") }.getOrElse { "error: $it" }
            SavvyLog.event("Block", "emergency -> $msg")
            runOnUiThread {
                val c = savvy.repo.commitment
                if (c == null || (c.pausedUntilElapsedMs ?: 0) > savvy.repo.now().elapsedRealtimeMs) succeeded(msg) else failed(msg)
            }
        }
    }

    fun scanQr() {
        qr.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(true))
    }

    fun openNfcSettings() = startActivity(Intent(Settings.ACTION_NFC_SETTINGS))

    /** Leaves the finished card state so the user can try again. */
    fun retry() = update { it.copy(phase = BlockUiState.Phase.WAITING, message = null) }

    private fun handleLiveProof(url: String, token: String, note: String) {
        if (unblocksAlwaysBlocked()) { handleAlwaysBlocked(url, "nfc_live", note, presenceVerified = true); return }
        checking()
        thread {
            val outcome = unlock.handleLiveProof(url, token)
            SavvyLog.event("Block", "live proof $note -> $outcome")
            runOnUiThread {
                if (outcome is UnlockCoordinator.Outcome.Released || outcome is UnlockCoordinator.Outcome.Paused) succeeded(outcome.toString())
                else failed((outcome as? UnlockCoordinator.Outcome.Rejected)?.reason ?: outcome.toString())
            }
        }
    }

    /**
     * Back and "Go home": never back into the blocked app; open Savvy's home screen instead.
     * Other modes just close. Uses the launch intent because MainActivity lives in src/main/ui,
     * which the Robolectric compilecheck doesn't build.
     */
    fun goHome() {
        if (mode == MODE_UNLOCK) {
            val appHome = packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            startActivity(appHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED = "blocked"
        const val EXTRA_TAMPER = "tamper"
        /** Set by the blocking engine: the screen was opened over [EXTRA_BLOCKED], which is still behind Savvy. */
        const val EXTRA_RETURN_TO_BLOCKED = "return_to_blocked"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TASK_ID = "task_id"
        const val MODE_UNLOCK = "unlock"
        const val MODE_REGISTER = "register"
        const val MODE_TASK = "task"
        /** Unblock the always-blocked app in [EXTRA_BLOCKED] with the card (from the Always blocked screen). */
        const val MODE_UNBLOCK_APP = "unblock_app"
        /** Debug: write [EXTRA_URL] to a blank NFC tag. */
        const val MODE_WRITE = "write"
        const val EXTRA_URL = "url"
    }
}

/** What the block screen shows. [message] is the raw result (reason code or action text) for the screen to phrase. */
data class BlockUiState(
    val mode: String = BlockActivity.MODE_UNLOCK,
    val blockedPackage: String? = null,
    val tamper: Boolean = false,
    /** NfcCardReader.state: ready, nfc_disabled or no_nfc_hardware. */
    val nfc: String = "ready",
    val phase: Phase = Phase.WAITING,
    val message: String? = null,
) {
    enum class Phase { WAITING, CHECKING, SUCCESS, FAILED }
}
