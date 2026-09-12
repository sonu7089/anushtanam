package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.domain.repository.JapaHistoryRepository
import com.mantrasiddhi.domain.repository.PerformedJapa
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class JapaHistoryRepositoryImpl @Inject constructor(private val database: MantraSiddhiDatabase) : JapaHistoryRepository {
    override fun observe(userId: Long) = com.mantrasiddhi.domain.diagnostics.WorkProbe.observe("japa.history",
        database.japaDao().observePerformed(userId).map { rows ->
            rows.map { row -> PerformedJapa(row.dateKey?.let(SandhyaDateKey::parse)
                ?: Instant.ofEpochMilli(requireNotNull(row.createdAt)).atZone(ZoneId.systemDefault()).toLocalDate(), row.quantity) }
        }.flowOn(kotlinx.coroutines.Dispatchers.Default))
}
