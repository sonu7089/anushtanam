package com.mantrasiddhi.domain.model

/** Each stage has its own alarm and delivery history. */
enum class SandhyaReminderStage { PREPARATION, EVENT }

data class SandhyaReminderTime(val eventMillis: Long, val triggerMillis: Long)

object SandhyaReminderPolicy {
    const val LEAD_MINUTES = 15L
    const val LEAD_MILLIS = LEAD_MINUTES * 60_000L
    fun trigger(event: Long, stage: SandhyaReminderStage, now: Long): Long? = when {
        event <= now -> null
        stage == SandhyaReminderStage.EVENT -> event
        event - LEAD_MILLIS > now -> event - LEAD_MILLIS
        event - now > 120_000L -> now + 1_000L
        else -> null
    }
    fun delivery(stage: SandhyaReminderStage, event: Long, now: Long, eventEnabled: Boolean): SandhyaReminderStage? = when {
        now > event + 60 * 60_000L -> null
        stage == SandhyaReminderStage.PREPARATION && now >= event ->
            if (eventEnabled) SandhyaReminderStage.EVENT else null
        else -> stage
    }
}
