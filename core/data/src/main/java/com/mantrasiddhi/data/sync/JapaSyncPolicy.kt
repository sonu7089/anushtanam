package com.mantrasiddhi.data.sync

/** Revisions win first. Concurrent revisions use timestamp, then operation ID as a stable tie break. */
object JapaSyncPolicy {
    fun legacyBaseline(goal: Map<String, Any?>, mirror: Map<String, Any?>?): Long {
        val local = (goal["baselineCount"] as? Number)?.toLong() ?: 0
        if (mirror == null) return local
        fun number(map: Map<String, Any?>, key: String) = (map[key] as? Number)?.toLong()
        val target = goal["kind"] == "target"
        val same = if (mirror["stableId"] != null) mirror["stableId"] == goal["id"] else {
            val sameMantra = (mirror["mantraId"] ?: "gayatri") == goal["mantraId"]
            sameMantra && if (target) {
                goal["id"] == "legacy-target" && number(mirror, "targetRecitationCount") == number(goal, "targetCount")
            } else {
                number(mirror, "startedAtMillis") == number(goal, "startedAtMillis") &&
                    number(mirror, "targetJapaCount") == number(goal, "targetCount")
            }
        }
        return if (same) maxOf(local, number(mirror, if (target) "currentRecitationCount" else "currentJapaCount") ?: 0) else local
    }

    fun newer(incoming: Map<String, Any?>, saved: Map<String, Any?>?): Boolean {
        if (saved == null) return true
        for (key in listOf("revision", "updatedAtMillis")) {
            val comparison = ((incoming[key] as? Number)?.toLong() ?: 0)
                .compareTo((saved[key] as? Number)?.toLong() ?: 0)
            if (comparison != 0) return comparison > 0
        }
        return incoming["operationId"].toString() > saved["operationId"].toString()
    }
}
