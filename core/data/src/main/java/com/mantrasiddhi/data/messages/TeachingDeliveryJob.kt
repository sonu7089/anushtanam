package com.mantrasiddhi.data.messages

import android.app.job.JobParameters
import android.app.job.JobService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class TeachingDeliveryJob : JobService() {
    @Inject lateinit var notifications: TeachingNotifications
    @Inject lateinit var repository: TeachingRepository
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!notifications.practicing) {
                    for ((id, raw) in notifications.pending()) {
                        if (!notifications.isEnabledNow()) { notifications.discard(id); continue }
                        if (notifications.practicing) break
                        val data = JSONObject(raw)
                        val detail = repository.detail(data.getString("contentId"))
                        if (detail.message != null) notifications.display(id, data.getLong("expiresAt"), detail.message)
                        else if (!detail.offline) notifications.discard(id)
                    }
                }
            } finally {
                jobFinished(params, notifications.pending().isNotEmpty())
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
}
