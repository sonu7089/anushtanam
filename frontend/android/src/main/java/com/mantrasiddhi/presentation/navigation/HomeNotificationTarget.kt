package com.mantrasiddhi.presentation.navigation

import android.content.Intent
import com.mantrasiddhi.domain.model.TeachingDeliveryPolicy

/** Only the supported Home destination is accepted from notification taps. */
object HomeNotificationTarget {
    fun from(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "anushtanam" || uri.host != "home" || uri.pathSegments.size != 1 ||
            uri.query != null || uri.fragment != null || uri.port != -1 || uri.userInfo != null) return null
        return uri.lastPathSegment?.takeIf(TeachingDeliveryPolicy::validId)
    }
}
