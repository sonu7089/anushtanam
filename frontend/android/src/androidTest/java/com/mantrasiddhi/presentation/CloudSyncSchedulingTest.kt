package com.mantrasiddhi.presentation

import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.data.sync.CloudSyncJobService
import com.mantrasiddhi.data.sync.CloudSyncScheduler
import org.junit.Assert.*
import org.junit.Test

class CloudSyncSchedulingTest {
    @Test fun backgroundJobIsPersistedNetworkConstrainedAndCancelableWhenIdle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val jobs = context.getSystemService(JobScheduler::class.java)
        val scheduler = CloudSyncScheduler(context)
        scheduler.cancelIdle()
        try {
            scheduler.schedule()
            val job = jobs.getPendingJob(CloudSyncScheduler.JOB_ID)
            assertNotNull(job)
            assertEquals(60_000L, job!!.minLatencyMillis)
            assertNotNull(job.requiredNetwork)
            assertTrue(job.isPersisted)
            assertEquals(ComponentName(context, CloudSyncJobService::class.java), job.service)
            val service = context.packageManager.getServiceInfo(job.service, PackageManager.ComponentInfoFlags.of(0))
            assertEquals("android.permission.BIND_JOB_SERVICE", service.permission)
            assertTrue(service.exported)
            scheduler.schedule()
            assertEquals(1, jobs.allPendingJobs.count { it.id == CloudSyncScheduler.JOB_ID })
        } finally { scheduler.cancelIdle() }
        assertNull(jobs.getPendingJob(CloudSyncScheduler.JOB_ID))
    }
}
