package com.mantrasiddhi.data.repository

import com.mantrasiddhi.data.local.dao.SandhyaCountDao
import com.mantrasiddhi.data.local.entity.SandhyaCountDraftEntity
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.SandhyaCountRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SandhyaCountRepositoryImpl @Inject constructor(private val dao: SandhyaCountDao) : SandhyaCountRepository {
    override fun observeCounts(userId: Long) = dao.observeCounts(userId).map { rows -> rows.map { it.toDomain() } }
    override fun observeDraft(userId: Long) = dao.observeDraft(userId).map { it?.toDomain() }
    override suspend fun getCount(userId: Long, dateKey: String, period: SandhyaPeriod) =
        dao.getCount(userId, dateKey, period.name)?.toDomain()
    override suspend fun getDraft(userId: Long) = dao.getDraft(userId)?.toDomain()
    override suspend fun saveDraft(draft: SandhyaCountDraft) = dao.saveDraft(SandhyaCountDraftEntity.from(draft))
    override suspend fun discardDraft(userId: Long, id: String) = dao.discardDraft(userId, id)
    override suspend fun save(draft: SandhyaCountDraft) = dao.save(draft, System.currentTimeMillis()).toDomain()
    override suspend fun history(userId: Long, dateKey: String, period: SandhyaPeriod) =
        dao.history(userId, dateKey, period.name).map { it.toDomain() }
}
