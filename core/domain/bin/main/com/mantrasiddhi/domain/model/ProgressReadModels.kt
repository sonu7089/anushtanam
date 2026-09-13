package com.mantrasiddhi.domain.model

/** Small read models: no transcription, word scores or backup payloads. */
data class SessionTotals(val total: Int, val correct: Int, val average: Double, val best: Double)
data class PracticeDay(val dateKey: String, val count: Int)
data class ScoreDay(val dateKey: String, val average: Double, val correctness: Double,
    val completeness: Double, val pronunciation: Double)
data class SessionHistoryRow(val id: Long, val createdAt: Long, val mode: String,
    val overallScore: Double, val isCorrect: Boolean, val durationMs: Long)

fun List<PracticeDay>.currentStreak(today: java.time.LocalDate = java.time.LocalDate.now()): Int {
    val dates = map { it.dateKey }.toHashSet()
    var day = if (today.toString() in dates) today else today.minusDays(1)
    var count = 0
    while (day.toString() in dates) { count++; day = day.minusDays(1) }
    return count
}
