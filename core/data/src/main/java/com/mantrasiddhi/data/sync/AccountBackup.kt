package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.SandhyaCountDraftEntity
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.domain.repository.SessionRepository
import javax.inject.Inject

/** Materializes account records without enqueueing them again. */
class AccountBackup @Inject constructor(
    private val database: MantraSiddhiDatabase,
    private val records: AccountRecordStore,
    private val preferences: UserPreferencesDataStore,
    private val sandhya: SandhyaPreferences,
    private val session: SessionRepository
) {
    suspend fun capture(userId: Long, uid: String) {
        val user = database.userDao().getById(userId) ?: return
        require(user.firebaseUid == uid)
        records.importLocal(userId, uid)
        preferences.seedCloud(uid)
        sandhya.seedCloud(uid, userId)
        records.put(uid, "profile", mapOf("name" to user.name, "avatarIndex" to user.avatarIndex,
            "useGooglePhoto" to user.useGooglePhoto), seed = true)
        database.sandhyaCountDao().getDraft(userId)?.let { draft ->
            records.put(uid, "draft_${draft.id}", draft.payload(), seed = true)
        }
    }

    suspend fun merge(uid: String, userId: Long, value: Map<String, Any?>) {
        require(database.userDao().getById(userId)?.firebaseUid == uid)
        records.merge(uid, value)
        // Reapply even after a replay: profile creation/sign-in may have refreshed its row.
        val current = records.get(uid, value.text("id")) ?: return
        val data = AccountRecordStore.data(current)
        when {
            value.text("id") == "profile" -> {
                val old = database.userDao().getById(userId) ?: return
                val updated = old.copy(name = data.text("name"), avatarIndex = data.number("avatarIndex").toInt(),
                    useGooglePhoto = data["useGooglePhoto"] == true)
                if (old != updated) database.userDao().insert(updated)
                if (records.currentUid == uid && session.currentUser.value?.id == userId) session.login(updated.toDomain())
            }
            value.text("id").startsWith("draft_") -> {
                val dao = database.sandhyaCountDao()
                val id = value.text("id").removePrefix("draft_")
                if (current["deleted"] == true || dao.getChange(id) != null) {
                    dao.discardDraft(userId, id)
                    return
                }
                val old = dao.getDraft(userId)
                if (old != null && old.id != id && old.updatedAtMillis >= data.number("updatedAtMillis")) return
                val count = dao.getCount(userId, data.text("dateKey"), data.text("period"))
                val draft = SandhyaCountDraftEntity(id, userId, data.text("dateKey"), data.text("period"),
                    data.text("zoneId"), data.text("mode"), data.text("input"), data.numberOrNull("baseQuantity"),
                    // Revisions are device-local projections; stale quantities must still fail on save.
                    if (count?.quantity == data.numberOrNull("baseQuantity")) count?.revision ?: 0 else -1,
                    data.number("updatedAtMillis"))
                if (old != draft) {
                    if (old != null) dao.discardDraft(userId, old.id)
                    dao.saveDraft(draft)
                }
            }
        }
    }

    suspend fun reconcileDraft(userId: Long) {
        val dao = database.sandhyaCountDao()
        val draft = dao.getDraft(userId) ?: return
        if (dao.getChange(draft.id) != null) {
            dao.discardDraft(userId, draft.id)
            return
        }
        val count = dao.getCount(userId, draft.dateKey, draft.period)
        val revision = if (count?.quantity == draft.baseQuantity) count?.revision ?: 0 else -1
        if (draft.baseRevision != revision) dao.saveDraft(draft.copy(baseRevision = revision))
    }
}

internal fun SandhyaCountDraftEntity.payload(): Map<String, Any?> = mapOf(
    "id" to id, "dateKey" to dateKey, "period" to period, "zoneId" to zoneId,
    "mode" to mode, "input" to input, "baseQuantity" to baseQuantity,
    "baseRevision" to baseRevision.coerceAtLeast(0), "updatedAtMillis" to updatedAtMillis)
