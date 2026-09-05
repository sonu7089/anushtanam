/*
 * Anushtanam - Gayatri Mantra Recitation App
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
 * Average score of each evaluation axis on a specific day.
 *
 * Lets the Progress screen draw three independent trend lines so the learner can see
 * *which* dimension is improving (§4.4).
 *
 * @property dayLabel Short, localized day label.
 * @property dateMillis Start-of-day timestamp.
 * @property correctness Mean correctness axis for the day (`0.0..1.0`).
 * @property completeness Mean completeness axis for the day (`0.0..1.0`).
 * @property pronunciation Mean pronunciation axis for the day (`0.0..1.0`).
 */
data class DailyAxisScores(
    val dayLabel: String,
    val dateMillis: Long,
    val correctness: Double,
    val completeness: Double,
    val pronunciation: Double
)

/**
 * Computes the per-day average of each of the three evaluation axes, so progress can
 * be tracked separately for correctness, completeness and pronunciation.
 */
class GetAxisTrendsUseCase @Inject constructor(
    private val recitationRepository: RecitationRepository
) {

    /**
     * @param userId The learner whose sessions to aggregate.
     * @param daysLimit Number of trailing days to include (ending today).
     */
    operator fun invoke(userId: Long, daysLimit: Int = 7): Flow<List<DailyAxisScores>> {
        return recitationRepository.getSessionsForUser(userId).map { sessions ->
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dateLabelFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            (daysLimit - 1 downTo 0).map { offset ->
                val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
                val startOfDay = dayCal.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val endOfDay = startOfDay + DAY_MS - 1L
                val daySessions = sessions.filter { it.createdAt in startOfDay..endOfDay }
                val label = if (daysLimit > 7) dateLabelFormat.format(dayCal.time) else dayFormat.format(dayCal.time)
                DailyAxisScores(
                    dayLabel = label,
                    dateMillis = startOfDay,
                    correctness = daySessions.meanOf { it.correctness },
                    completeness = daySessions.meanOf { it.completeness },
                    pronunciation = daySessions.meanOf { it.pronunciation }
                )
            }
        }
    }

    private fun <T> List<T>.meanOf(selector: (T) -> Double): Double =
        if (isEmpty()) 0.0 else sumOf(selector) / size

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
