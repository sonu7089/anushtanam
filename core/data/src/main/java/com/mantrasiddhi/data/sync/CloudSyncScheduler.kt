package com.mantrasiddhi.data.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot persistent jobs, rearmed only while work remains. Android may defer background jobs. */
@Singleton
class CloudSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val jobs get() = context.getSystemService(JobScheduler::class.java)
    private var queued = jobs.getPendingJob(JOB_ID) != null
    private var running = false
    private var requestedDuringRun = false

    @Synchronized fun schedule() {
        if (running) { requestedDuringRun = true; return }
        if (!queued) enqueueJob()
    }

    private fun enqueueJob() {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, CloudSyncJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(CloudSyncRunner.INTERVAL_MS)
            .setBackoffCriteria(CloudSyncRunner.INTERVAL_MS, JobInfo.BACKOFF_POLICY_LINEAR)
            .setPersisted(true)
            .build()
        queued = jobs.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    @Synchronized fun started() { queued = false; running = true; requestedDuringRun = false }

    @Synchronized fun finished(more: Boolean, finish: () -> Unit) {
        finish()
        running = false
        if (more || requestedDuringRun) enqueueJob()
        requestedDuringRun = false
    }

    @Synchronized fun stopped() { running = false; queued = true }

    @Synchronized fun cancelIdle() {
        if (!running) { jobs.cancel(JOB_ID); queued = false }
    }

    companion object { const val JOB_ID = 7301 }
}

@AndroidEntryPoint
class CloudSyncJobService : JobService() {
    @Inject lateinit var runner: CloudSyncRunner
    @Inject lateinit var scheduler: CloudSyncScheduler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        scheduler.started()
        job = scope.launch {
            val more = try { runner.runPending() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { true }
            scheduler.finished(more) { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        scheduler.stopped()
        return true
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
