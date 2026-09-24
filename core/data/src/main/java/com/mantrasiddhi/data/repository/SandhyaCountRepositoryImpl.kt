package com.mantrasiddhi.data.repository

import androidx.room.withTransaction
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.sync.JapaCloudSync
import com.mantrasiddhi.data.local.dao.SandhyaCountDao
import com.mantrasiddhi.data.local.entity.SandhyaCountDraftEntity
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.SandhyaCountRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.mantrasiddhi.data.sync.AccountRecordStore
import com.mantrasiddhi.data.sync.payload

class SandhyaCountRepositoryImpl @Inject constructor(private val dao: SandhyaCountDao,
    private val database: MantraSiddhiDatabase, private val sync: JapaCloudSync,
    private val records: AccountRecordStore) : SandhyaCountRepository {
    override fun observeCounts(userId: Long) = dao.observeCounts(userId).map { rows -> rows.map { it.toDomain() } }
    override fun observeDraft(userId: Long) = dao.observeDraft(userId).map { it?.toDomain() }
    override suspend fun getCount(userId: Long, dateKey: String, period: SandhyaPeriod) =
        dao.getCount(userId, dateKey, period.name)?.toDomain()
    override suspend fun getDraft(userId: Long) = dao.getDraft(userId)?.toDomain()
    override suspend fun saveDraft(draft: SandhyaCountDraft) = database.withTransaction {
        if (dao.getChange(draft.id) != null || records.get(records.owner(draft.userId), "draft_${draft.id}")?.get("deleted") == true) return@withTransaction
        val row = SandhyaCountDraftEntity.from(draft)
        dao.saveDraft(row)
        records.put(records.owner(draft.userId), "draft_${draft.id}", row.payload())
    }
    override suspend fun discardDraft(userId: Long, id: String) = database.withTransaction {
        dao.discardDraft(userId, id)
        records.put(records.owner(userId), "draft_$id", mapOf("deleted" to true))
    }
    override suspend fun save(draft: SandhyaCountDraft): SandhyaCountChange {
        val result = database.withTransaction {
            require(database.userDao().getById(draft.userId) != null) { "Unknown count owner" }
            val replay = dao.getChange(draft.id)
            if (replay != null) return@withTransaction dao.save(draft, System.currentTimeMillis()).toDomain().copy(feedback = JapaGoalFeedback())

            check(records.get(records.owner(draft.userId), "draft_${draft.id}")?.get("deleted") != true) {
                "This entry was already saved or discarded on another device."
            }

            val change = dao.save(draft, System.currentTimeMillis())
            records.put(records.owner(draft.userId), "draft_${draft.id}", mapOf("deleted" to true))
            sync.enqueue(draft.userId)
            change.toDomain()
        }
        sync.schedule()
        return result
    }
    override suspend fun history(userId: Long, dateKey: String, period: SandhyaPeriod) =
        dao.history(userId, dateKey, period.name).map { it.toDomain() }
}
