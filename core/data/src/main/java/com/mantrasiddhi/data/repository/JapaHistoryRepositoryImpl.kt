package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.domain.repository.JapaHistoryRepository
import com.mantrasiddhi.domain.repository.PerformedJapa
import com.mantrasiddhi.domain.util.SandhyaDateKey
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class JapaHistoryRepositoryImpl @Inject constructor(private val database: MantraSiddhiDatabase) : JapaHistoryRepository {
    override fun observe(userId: Long) = com.mantrasiddhi.domain.diagnostics.WorkProbe.observe("japa.history", combine(database.japaDao().observeEntries(userId),
        database.recitationDao().getSessionsForUser(userId)) { entries, sessions ->
        val represented = entries.mapNotNull { it.sessionId }.toSet()
        entries.map { PerformedJapa(SandhyaDateKey.parse(it.dateKey), it.quantity) } +
            sessions.filter { it.id !in represented && it.isCorrect && it.mode == "NORMAL" }.map {
                PerformedJapa(Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate(), 1)
            }
    })
}
