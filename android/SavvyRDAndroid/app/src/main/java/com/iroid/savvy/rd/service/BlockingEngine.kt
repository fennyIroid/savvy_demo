package com.iroid.savvy.rd.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.iroid.savvy.core.RestrictionPolicy
import com.iroid.savvy.core.RestrictionPolicy.Decision
import com.iroid.savvy.rd.block.BlockActivity
import com.iroid.savvy.rd.data.CommitmentRepository
import com.iroid.savvy.rd.data.SavvyLog

/**
 * Shared by both detection paths (Accessibility and UsageStats), so the two
 * approaches can be compared on the same device with the same policy.
 */
class BlockingEngine(private val context: Context, private val repo: CommitmentRepository) {
    enum class Source { ACCESSIBILITY, USAGE_STATS }

    private val launchers: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(home, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
    }

    private var lastBlockPkg: String? = null
    private var lastBlockAt = 0L

    /**
     * One app launch produces several foreground events (activity + its windows), so a
     * block for the same package within [DEBOUNCE_MS] is reported but not re-launched.
     */
    fun shouldLaunch(pkg: String): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val fresh = pkg != lastBlockPkg || now - lastBlockAt > DEBOUNCE_MS
        lastBlockPkg = pkg; lastBlockAt = now
        return fresh
    }

    fun blockIntent(pkg: String, tamper: Boolean): Intent = Intent(context, BlockActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(BlockActivity.EXTRA_BLOCKED, pkg)
        .putExtra(BlockActivity.EXTRA_TAMPER, tamper)

    /**
     * @param launch false when the caller delivers the block screen itself (Accessibility
     *   path: it must go Home first, then open the block screen; see SavvyAccessibilityService).
     * @param eventTimeMs uptime/elapsed time of the event, used to log reaction latency.
     * @return true when the engine blocked something (caller may also go HOME).
     */
    fun onForeground(pkg: String, cls: String?, source: Source, eventTimeMs: Long?, launch: Boolean = true): Decision {
        val now = repo.now()
        val serverNow = repo.serverOffsetMs?.let { now.wallClockMs + it }
        val decision = RestrictionPolicy.decide(pkg, cls, repo.commitment, now, launchers, serverNow, repo.parentBlockedPackages)
        when (decision) {
            is Decision.BlockApp, is Decision.BlockTamperScreen -> {
                val latency = eventTimeMs?.let { android.os.SystemClock.uptimeMillis() - it }
                SavvyLog.event("Engine", "$source block $pkg/$cls latency=${latency ?: "?"}ms")
                if (launch && shouldLaunch(pkg)) context.startActivity(blockIntent(pkg, decision is Decision.BlockTamperScreen))
            }
            Decision.CommitmentExpired -> {
                SavvyLog.event("Engine", "commitment expired, releasing")
                repo.commitment = null
                if (repo.parentBlockedPackages.isEmpty()) UsageMonitorService.stop(context)
                // Parent always-on rules still apply to this same app switch.
                return onForeground(pkg, cls, source, eventTimeMs, launch)
            }
            Decision.Allow -> Unit
        }
        return decision
    }

    companion object { const val DEBOUNCE_MS = 1500L }
}
