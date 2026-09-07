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
 * Domain model representing accuracy score for a specific day.
 */
data class DailyAccuracy(
    val dayLabel: String,
    val dateMillis: Long,
    val averageScore: Double
)

/**
 * UseCase to compute the average recitation accuracy per day.
 */
class GetAccuracyTrendUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {
    operator fun invoke(userId: Long, daysLimit: Int = 7): Flow<List<DailyAccuracy>> {
        return recitationRepository.getSessionsForUser(userId).map { sessions ->
            val dailyAccuracies = mutableListOf<DailyAccuracy>()
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

                val daySessions = sessions.filter { it.createdAt in startOfDay..endOfDay }
                val averageScore = if (daySessions.isEmpty()) {
                    0.0
                } else {
                    val sum = daySessions.sumOf { it.overallScore }
                    sum / daySessions.size
                }

                val label = if (daysLimit > 7) {
                    dateLabelFormat.format(dayCal.time)
                } else {
                    dateFormat.format(dayCal.time)
                }

                dailyAccuracies.add(
                    DailyAccuracy(
                        dayLabel = label,
                        dateMillis = startOfDay,
                        averageScore = averageScore
                    )
                )
            }
            dailyAccuracies
        }
    }
}
