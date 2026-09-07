/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.repository.RecitationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * Domain model representing a count of recitations for a specific day.
 */
data class DailyCount(
    val dayLabel: String,
    val dateMillis: Long,
    val count: Int
)

/**
 * UseCase to aggregate recitation counts per day for a user.
 */
class GetDailyRecitationCountsUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(userId: Long, daysLimit: Int = 7): Flow<List<DailyCount>> {
        return recitationRepository.getSessionsForUser(userId).map { sessions ->
            val dailyCounts = mutableListOf<DailyCount>()
            val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dateLabelFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

            for (i in (daysLimit - 1) downTo 0) {
                val dayCal = Calendar.getInstance()
                dayCal.add(Calendar.DAY_OF_YEAR, -i)

                val startOfDay = dayCal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endOfDay = startOfDay + 24 * 60 * 60 * 1000L - 1L

                // Only count sessions that were correct (recitations that count towards goal)
                val count = sessions.count { it.createdAt in startOfDay..endOfDay && it.isCorrect }

                val label = if (daysLimit > 7) {
                    dateLabelFormat.format(dayCal.time)
                } else {
                    dateFormat.format(dayCal.time)
                }

                dailyCounts.add(DailyCount(label, startOfDay, count))
            }
            dailyCounts
        }
    }
}
