package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.remote.FirebaseContract as F
import com.mantrasiddhi.domain.model.ProgressSummary
import org.json.JSONObject

/** Pure transformations shared by upload and the pending-progress overlay. */
object CloudSyncPolicy {
    const val CHECKPOINTS = "syncCheckpoints"

    fun decode(json: String): Map<String, Any?> = objectMap(JSONObject(json))

    private fun objectMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
        when (val item = value.get(key)) {
            JSONObject.NULL -> null
            is JSONObject -> objectMap(item)
            else -> item
        }
    }

    fun acknowledged(checkpoints: Map<String, Any?>, event: CloudSyncEntity): Boolean =
        (checkpoints[event.streamId] as? Number)?.toLong()?.let { it >= event.id } == true

    @Suppress("UNCHECKED_CAST")
    fun progress(remote: Map<String, Any?>, payload: Map<String, Any?>): Map<String, Any?> {
        if (remote.isEmpty()) return payload.getValue("seed") as Map<String, Any?>
        val score = (payload.getValue("score") as Number).toDouble()
        val correct = if (payload["correct"] == true) 1L else 0L
        val key = payload.getValue("date") as String
        val daily = (remote[F.FIELD_DAILY] as? Map<String, Any?>).orEmpty().toMutableMap()
        val day = (daily[key] as? Map<String, Any?>).orEmpty()
        daily[key] = mapOf(
            F.FIELD_DAY_SESSIONS to day.long(F.FIELD_DAY_SESSIONS) + 1,
            F.FIELD_DAY_CORRECT to day.long(F.FIELD_DAY_CORRECT) + correct,
            F.FIELD_DAY_SCORE_SUM to day.double(F.FIELD_DAY_SCORE_SUM) + score
        )
        val keep = daily.keys.sortedDescending().take(ProgressSummary.DAILY_KEY_CAP).toSet()
        return remote + mapOf(
            F.FIELD_TOTAL_SESSIONS to remote.long(F.FIELD_TOTAL_SESSIONS) + 1,
            F.FIELD_CORRECT_SESSIONS to remote.long(F.FIELD_CORRECT_SESSIONS) + correct,
            F.FIELD_SCORE_SUM to remote.double(F.FIELD_SCORE_SUM) + score,
            F.FIELD_BEST_SCORE to maxOf(remote.double(F.FIELD_BEST_SCORE), score),
            F.FIELD_DAILY to daily.filterKeys { it in keep }
        )
    }

    fun purashcharana(remote: Map<String, Any?>, local: Map<String, Any?>): Map<String, Any?> {
        val same = remote.isNotEmpty() && remote[F.FIELD_IS_ACTIVE] != false &&
            (remote["stableId"] == null || local["stableId"] == null || remote["stableId"] == local["stableId"]) &&
            (remote[F.FIELD_MANTRA_ID] ?: "gayatri") == local[F.FIELD_MANTRA_ID] &&
            remote.long(F.FIELD_AKSHARA_COUNT) == local.long(F.FIELD_AKSHARA_COUNT) &&
            remote.long(F.FIELD_TARGET_JAPA_COUNT) == local.long(F.FIELD_TARGET_JAPA_COUNT)
        if (!same) return remote + local
        val count = maxOf(remote.long(F.FIELD_CURRENT_JAPA_COUNT), local.long(F.FIELD_CURRENT_JAPA_COUNT))
        val start = remote.long(F.FIELD_STARTED_AT).takeIf { it in 1L until local.long(F.FIELD_STARTED_AT) }
            ?: local.long(F.FIELD_STARTED_AT)
        val completed = listOfNotNull(remote[F.FIELD_COMPLETED_AT] as? Number, local[F.FIELD_COMPLETED_AT] as? Number)
            .minOfOrNull { it.toLong() }?.takeIf { count >= local.long(F.FIELD_TARGET_JAPA_COUNT) }
        return remote + local + mapOf(F.FIELD_CURRENT_JAPA_COUNT to count, F.FIELD_STARTED_AT to start,
            F.FIELD_COMPLETED_AT to completed, F.FIELD_IS_COMPLETED to (count >= local.long(F.FIELD_TARGET_JAPA_COUNT)))
    }

    fun Map<String, Any?>.long(key: String): Long = (get(key) as? Number)?.toLong() ?: 0L
    fun Map<String, Any?>.double(key: String): Double = (get(key) as? Number)?.toDouble() ?: 0.0
}
