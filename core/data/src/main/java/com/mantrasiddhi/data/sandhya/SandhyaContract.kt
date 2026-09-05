/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.sandhya

/** Shared constants for the Sandhya reminder intents and notification channel. */
object SandhyaContract {
    const val CHANNEL_ID = "sandhya_reminders"

    /** Action for the notification's "Mark as done" button. */
    const val ACTION_MARK_OBSERVED = "com.mantrasiddhi.action.SANDHYA_MARK_OBSERVED"

    const val EXTRA_PERIOD = "com.mantrasiddhi.extra.PERIOD"
    const val EXTRA_DATE_KEY = "com.mantrasiddhi.extra.DATE_KEY"

    /** The solar-event time (epoch ms) the reminder is for; the alarm fires earlier. */
    const val EXTRA_EVENT_MILLIS = "com.mantrasiddhi.extra.EVENT_MILLIS"

    /** Lead time before the solar event that the reminder fires. */
    const val LEAD_MINUTES = 5L
    const val LEAD_MILLIS = LEAD_MINUTES * 60_000L

    /** Request-code offset so "mark done" PendingIntents don't collide with alarms. */
    const val ACTION_REQUEST_OFFSET = 100
}
