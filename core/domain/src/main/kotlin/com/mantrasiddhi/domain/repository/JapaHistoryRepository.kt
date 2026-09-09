package com.mantrasiddhi.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class PerformedJapa(val date: LocalDate, val quantity: Long)

interface JapaHistoryRepository {
    fun observe(userId: Long): Flow<List<PerformedJapa>>
}
