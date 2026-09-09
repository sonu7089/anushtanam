package com.mantrasiddhi.presentation.navigation

import android.content.Intent
import com.mantrasiddhi.data.sandhya.SandhyaContract
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaReminderStage
import com.mantrasiddhi.domain.util.SandhyaDateKey
import java.time.LocalDate

data class SandhyaReminderTarget(val userId: Long, val date: LocalDate, val period: SandhyaPeriod, val stage: SandhyaReminderStage) {
    companion object {
        fun from(intent: Intent?): SandhyaReminderTarget? {
            if (intent?.action != SandhyaContract.ACTION_OPEN) return null
            return runCatching {
                val userId = intent.getLongExtra(SandhyaContract.EXTRA_USER_ID, 0)
                require(userId > 0)
                SandhyaReminderTarget(userId,
                    SandhyaDateKey.parse(requireNotNull(intent.getStringExtra(SandhyaContract.EXTRA_DATE_KEY))),
                    SandhyaPeriod.valueOf(requireNotNull(intent.getStringExtra(SandhyaContract.EXTRA_PERIOD))),
                    SandhyaReminderStage.valueOf(requireNotNull(intent.getStringExtra(SandhyaContract.EXTRA_STAGE))))
            }.getOrNull()
        }
    }
}
