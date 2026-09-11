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
                deliverPendingTeachings(notifications, repository)
            } finally {
                jobFinished(params, notifications.pending().isNotEmpty())
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
}

internal suspend fun deliverPendingTeachings(notifications: TeachingNotifications, repository: TeachingRepository) {
    if (notifications.practicing) return
    for ((id, raw) in notifications.pending()) {
        if (!notifications.isEnabledNow()) { notifications.discard(id); continue }
        if (notifications.practicing) break
        val data = JSONObject(raw)
        if (data.optString("kind") == "nudge") {
            val nudge = com.mantrasiddhi.domain.model.NotificationNudge.fromPayload(
                data.keys().asSequence().associateWith { data.optString(it) }, System.currentTimeMillis())
            if (nudge != null && nudge.campaignId == id) notifications.displayNudge(nudge) else notifications.discard(id)
            continue
        }
        val detail = repository.detail(data.getString("contentId"))
        if (detail.message != null) notifications.display(id, data.getLong("expiresAt"), detail.message)
        else if (!detail.offline) notifications.discard(id)
    }
}
