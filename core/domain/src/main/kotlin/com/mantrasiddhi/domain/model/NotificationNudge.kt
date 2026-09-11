package com.mantrasiddhi.domain.model

/** Standalone text carried by FCM. It never becomes a published teaching. */
data class NotificationNudge(val campaignId: String, val title: String, val body: String, val expiresAt: Long) {
    fun toPayload() = mapOf("kind" to "nudge", "schemaVersion" to "1", "campaignId" to campaignId,
        "title" to title, "body" to body, "destination" to "home", "expiresAt" to expiresAt.toString())

    companion object {
        fun fromPayload(data: Map<String, String>, now: Long): NotificationNudge? {
            if (data["kind"] != "nudge" || data["schemaVersion"] != "1" || data["destination"] != "home") return null
            val id = data["campaignId"]?.takeIf(TeachingDeliveryPolicy::validId) ?: return null
            val title = data["title"]?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return null
            val body = data["body"]?.takeIf { it.isNotBlank() && it.length <= 280 } ?: return null
            val expires = data["expiresAt"]?.toLongOrNull()?.takeIf { it > now && it <= 8640000000000000L } ?: return null
            return NotificationNudge(id, title.trim(), body.trim(), expires)
        }
    }
}
