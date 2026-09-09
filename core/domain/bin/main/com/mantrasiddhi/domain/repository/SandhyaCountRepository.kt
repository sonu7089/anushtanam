package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.flow.Flow

interface SandhyaCountRepository {
    fun observeCounts(userId: Long): Flow<List<SandhyaCount>>
    fun observeDraft(userId: Long): Flow<SandhyaCountDraft?>
    suspend fun getCount(userId: Long, dateKey: String, period: SandhyaPeriod): SandhyaCount?
    suspend fun getDraft(userId: Long): SandhyaCountDraft?
    suspend fun saveDraft(draft: SandhyaCountDraft)
    suspend fun discardDraft(userId: Long, id: String)
    suspend fun save(draft: SandhyaCountDraft): SandhyaCountChange
    suspend fun history(userId: Long, dateKey: String, period: SandhyaPeriod): List<SandhyaCountChange>
}
