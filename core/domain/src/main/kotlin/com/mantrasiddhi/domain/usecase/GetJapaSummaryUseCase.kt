package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.repository.SandhyaCountRepository
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.flow.map
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class JapaSummary(val byDate: Map<LocalDate, BigInteger> = emptyMap()) {
    val lifetime: BigInteger get() = byDate.values.fold(BigInteger.ZERO, BigInteger::add)
    fun on(date: LocalDate): BigInteger = byDate[date] ?: BigInteger.ZERO
}

/** Count performed japa once. Audio assessment statistics remain on their own path. */
class GetJapaSummaryUseCase @Inject constructor(private val history: com.mantrasiddhi.domain.repository.JapaHistoryRepository) {
    operator fun invoke(userId: Long) = history.observe(userId).map { records ->
        JapaSummary(records.groupBy { it.date }.mapValues { (_, entries) ->
            entries.fold(BigInteger.ZERO) { sum, entry -> sum + BigInteger.valueOf(entry.quantity) }
        })
    }
}
