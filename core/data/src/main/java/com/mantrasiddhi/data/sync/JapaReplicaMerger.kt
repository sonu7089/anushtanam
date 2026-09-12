package com.mantrasiddhi.data.sync

import androidx.room.withTransaction
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.*

internal class JapaReplicaMerger(private val database: MantraSiddhiDatabase) {
    suspend fun merge(userId: Long, goals: List<Map<String, Any?>>, entries: List<Map<String, Any?>>,
        ownerStillActive: () -> Boolean): Boolean = merge(userId, goals, entries, ownerStillActive, project = true)

    suspend fun merge(userId: Long, goals: List<Map<String, Any?>>, entries: List<Map<String, Any?>>,
        ownerStillActive: () -> Boolean, project: Boolean): Boolean {
        return database.withTransaction {
            check(ownerStillActive())
            val dao = database.japaDao()
            for (goal in goals) {
                val id = goal.text("id")
                val end = goal.numberOrNull("endedAtMillis")
                if (goal.text("kind") == "target") {
                    val old = dao.target(userId, id)
                    val effectiveEnd = listOfNotNull(end, old?.endedAtMillis).minOrNull()
                    val row = TargetEntity(id = old?.id ?: 0, userId = userId, setByUserId = userId,
                        targetRecitationCount = goal.number("targetCount").toInt(), currentRecitationCount = old?.currentRecitationCount ?: 0,
                        rewardType = goal.text("rewardType"), rewardDescription = goal.text("rewardDescription"), isCompleted = old?.isCompleted ?: false,
                        stableId = id, mantraId = goal.text("mantraId"), startedAtMillis = goal.number("startedAtMillis"),
                        endedAtMillis = effectiveEnd, isActive = effectiveEnd == null, baselineCount = goal.number("baselineCount"))
                    if (old == null) database.targetDao().insert(row) else if (row != old) dao.putTarget(row)
                } else {
                    val old = dao.vow(userId, id)
                    val effectiveEnd = listOfNotNull(end, old?.endedAtMillis).minOrNull()
                    val row = PurashcharanaEntity(id = old?.id ?: 0, userId = userId, mantraId = goal.text("mantraId"),
                        mantraAksharaCount = goal.number("aksharas").toInt(), targetJapaCount = goal.number("targetCount"),
                        currentJapaCount = old?.currentJapaCount ?: 0, startedAtMillis = goal.number("startedAtMillis"),
                        completedAtMillis = listOfNotNull(old?.completedAtMillis, goal.numberOrNull("completedAtMillis")).maxOrNull(), isActive = effectiveEnd == null,
                        stableId = id, endedAtMillis = effectiveEnd, baselineCount = goal.number("baselineCount"))
                    if (old == null) database.purashcharanaDao().insert(row) else if (row != old) dao.putVow(row)
                }
            }
            // Two devices may enroll while offline. Keep only the newest goal active.
            val targets = if (project) dao.targets(userId).filter { it.isActive } else emptyList()
            for (old in targets.drop(1)) dao.putTarget(old.copy(isActive = false, endedAtMillis = targets.first().startedAtMillis))
            val vows = if (project) dao.vows(userId).filter { it.isActive } else emptyList()
            for (old in vows.drop(1)) dao.putVow(old.copy(isActive = false, endedAtMillis = vows.first().startedAtMillis))
            for (value in entries) {
                val old = dao.entry(value.text("id"))
                check(old == null || old.userId == userId)
                val sessionId = (database.backupDao().session(userId, value.text("sessionStableId"))
                    ?: database.backupDao().sessionForEntry(userId, value.text("id")))?.id ?: old?.sessionId
                if (JapaSyncPolicy.newer(value, old?.payload())) dao.putEntry(JapaEntryEntity(
                    id = value.text("id"), userId = userId, mantraId = value.text("mantraId"), dateKey = value.text("dateKey"),
                    zoneId = value.text("zoneId"), period = value["period"] as? String, quantity = value.number("quantity"),
                    revision = value.number("revision"), createdAtMillis = value.number("createdAtMillis"),
                    targetId = value["targetId"] as? String, vowId = value["vowId"] as? String, source = value.text("source"),
                    sessionId = sessionId, updatedAtMillis = value.number("updatedAtMillis"), operationId = value.text("operationId"),
                    sessionStableId = value["sessionStableId"] as? String ?: old?.sessionStableId))
                else if (old != null && old.sessionId == null && sessionId != null) dao.putEntry(old.copy(sessionId = sessionId))
            }
            if (!project) return@withTransaction false
            database.sandhyaCountDao().rebuildPeriods(userId, recordHistory = false)
            val repaired = dao.creditPendingTargetEntries(userId, System.currentTimeMillis())
            dao.project(userId, System.currentTimeMillis(), repair = false)
            repaired
        }
    }
}
