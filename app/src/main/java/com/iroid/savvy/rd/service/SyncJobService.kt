package com.iroid.savvy.rd.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.iroid.savvy.rd.SavvyActions
import com.iroid.savvy.rd.data.SavvyLog
import com.iroid.savvy.rd.savvy
import kotlin.concurrent.thread

/**
 * Periodic housekeeping (every 15 min, the platform minimum; persisted across reboots) that
 * does not depend on the focus FGS being alive:
 *  - a child phone with NO active rule still pulls new parent rules (before: only on app open,
 *    because the FGS runs only while something is blocked);
 *  - heartbeat and offline sync continue after an OEM killed the FGS;
 *  - enforcement is re-armed when a commitment or parent rule exists but the FGS was killed.
 * Framework JobScheduler, not WorkManager, so no new dependency or compile-check stub.
 * Push (FCM data message from the backend outbox) would make rules instant; needs a Firebase project.
 */
class SyncJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            val repo = savvy.repo
            if (repo.commitment != null || repo.hasAlwaysOnBlocks) {
                // May be refused from the background on Android 12+ (FGS start limits); logged, not fatal.
                runCatching { UsageMonitorService.start(this) }.onFailure { SavvyLog.event("Job", "FGS re-arm refused $it") }
            }
            val out = runCatching { SavvyActions(this).housekeeping() }.getOrElse { "failed $it" }
            SavvyLog.event("Job", "periodic sync: ${out.replace('\n', ';')}")
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters) = true

    companion object {
        private const val JOB_ID = 7101

        /** Idempotent: keeps an existing schedule (re-scheduling would restart its period). */
        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, SyncJobService::class.java))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build()
            val r = runCatching { js.schedule(job) }.getOrDefault(JobScheduler.RESULT_FAILURE)
            SavvyLog.event("Job", "periodic sync scheduled result=$r")
        }
    }
}
