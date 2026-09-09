package com.mantrasiddhi.data.sync

import androidx.room.withTransaction
import com.mantrasiddhi.data.local.MantraSiddhiDatabase
import com.mantrasiddhi.data.local.entity.*

internal class JapaReplicaMerger(private val database: MantraSiddhiDatabase) {
    suspend fun merge(userId: Long, goals: List<Map<String, Any?>>, entries: List<Map<String, Any?>>,
        ownerStillActive: () -> Boolean): Boolean {
        return database.withTransaction {
            check(ownerStillActive())
            val dao = database.japaDao()
            for (goal in goals) {
                val id = goal.text("id")
                val end = goal.numberOrNull("endedAtMillis")
                if (goal.text("kind") == "target") {
                    val old = dao.targets(userId).firstOrNull { it.stableId == id }
                    val effectiveEnd = listOfNotNull(end, old?.endedAtMillis).minOrNull()
                    val row = TargetEntity(id = old?.id ?: 0, userId = userId, setByUserId = userId,
                        targetRecitationCount = goal.number("targetCount").toInt(), currentRecitationCount = old?.currentRecitationCount ?: 0,
                        rewardType = goal.text("rewardType"), rewardDescription = goal.text("rewardDescription"), isCompleted = old?.isCompleted ?: false,
                        stableId = id, mantraId = goal.text("mantraId"), startedAtMillis = goal.number("startedAtMillis"),
                        endedAtMillis = effectiveEnd, isActive = effectiveEnd == null, baselineCount = goal.number("baselineCount"))
                    if (old == null) database.targetDao().insert(row) else dao.putTarget(row)
                } else {
                    val old = dao.vows(userId).firstOrNull { it.stableId == id }
                    val effectiveEnd = listOfNotNull(end, old?.endedAtMillis).minOrNull()
                    val row = PurashcharanaEntity(id = old?.id ?: 0, userId = userId, mantraId = goal.text("mantraId"),
                        mantraAksharaCount = goal.number("aksharas").toInt(), targetJapaCount = goal.number("targetCount"),
                        currentJapaCount = old?.currentJapaCount ?: 0, startedAtMillis = goal.number("startedAtMillis"),
                        completedAtMillis = old?.completedAtMillis, isActive = effectiveEnd == null,
                        stableId = id, endedAtMillis = effectiveEnd, baselineCount = goal.number("baselineCount"))
                    if (old == null) database.purashcharanaDao().insert(row) else dao.putVow(row)
                }
            }
            // Two devices may enroll while offline. Keep only the newest goal active.
            val targets = dao.targets(userId).filter { it.isActive }
            for (old in targets.drop(1)) dao.putTarget(old.copy(isActive = false, endedAtMillis = targets.first().startedAtMillis))
            val vows = dao.vows(userId).filter { it.isActive }
            for (old in vows.drop(1)) dao.putVow(old.copy(isActive = false, endedAtMillis = vows.first().startedAtMillis))
            for (value in entries) {
                val old = dao.entry(value.text("id"))
                if (JapaSyncPolicy.newer(value, old?.payload())) dao.putEntry(JapaEntryEntity(
                    id = value.text("id"), userId = userId, mantraId = value.text("mantraId"), dateKey = value.text("dateKey"),
                    zoneId = value.text("zoneId"), period = value["period"] as? String, quantity = value.number("quantity"),
                    revision = value.number("revision"), createdAtMillis = value.number("createdAtMillis"),
                    targetId = value["targetId"] as? String, vowId = value["vowId"] as? String, source = value.text("source"),
                    sessionId = old?.sessionId, updatedAtMillis = value.number("updatedAtMillis"), operationId = value.text("operationId")))
            }
            database.sandhyaCountDao().rebuildPeriods(userId)
            val beforeProjection = dao.entries(userId)
            dao.project(userId, System.currentTimeMillis())
            beforeProjection != dao.entries(userId)
        }
    }
}
