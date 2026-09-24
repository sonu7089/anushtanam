package com.mantrasiddhi.data.sync

import com.mantrasiddhi.domain.util.SandhyaDateKey
import java.time.ZoneId

/** The shipped, unversioned wire format is v1. Never coerce missing/fractional fields to zero. */
internal object BackupRecordValidator {
    private const val LAST_MILLIS = 253402300799999L // End of year 9999.
    private val periods = setOf("PRATAH", "MADHYAHNA", "SAYAM")

    fun validate(type: String, documentId: String, value: Map<String, Any?>): String? = try {
        require(!value.containsKey("schemaVersion") || value.integer("schemaVersion") == 1L) { "Unsupported schema version" }
        value.identifier("id")
        when (type) {
            "account" -> require(AccountRecordPolicy.validate(documentId, value) == null) { "Invalid account record" }
            "entry" -> entry(value)
            "goal" -> goal(value)
            "practice" -> practice(value)
            "conflict" -> conflict(value)
            else -> error("Unsupported record type")
        }
        val expected = if (type == "practice") "${value.text("type")}_${value.text("id")}" else value.text("id")
        require(documentId == expected) { "Document identity mismatch" }
        null
    } catch (invalid: IllegalArgumentException) {
        invalid.message ?: "Invalid record"
    } catch (invalid: IllegalStateException) {
        invalid.message ?: "Invalid record"
    } catch (_: java.time.DateTimeException) {
        "Invalid calendar date or timezone"
    }

    private fun entry(d: Map<String, Any?>) {
        d.fields("id mantraId dateKey zoneId period quantity revision createdAtMillis targetId vowId source updatedAtMillis operationId", "sessionStableId")
        d.identifier("mantraId"); d.identifier("operationId")
        d.date(); d.zone(); d.count("quantity"); d.revision()
        d.millis("createdAtMillis"); d.millis("updatedAtMillis")
        require(d.integer("updatedAtMillis") >= d.integer("createdAtMillis")) { "Update precedes creation" }
        d.nullableId("targetId"); d.nullableId("vowId"); d.nullableId("sessionStableId")
        require((d["source"] == "SANDHYA" && d["period"] in periods && d["sessionStableId"] == null) ||
            (d["source"] == "AUDIO_ACCEPTED" && d["period"] == null && d.integer("quantity") == 1L)) { "Invalid source/period/quantity" }
    }

    private fun goal(d: Map<String, Any?>) {
        val common = "id kind mantraId startedAtMillis endedAtMillis baselineCount targetCount"
        when (d["kind"]) {
            "target" -> {
                d.fields("$common rewardType rewardDescription")
                d.string("rewardType", 100); d.string("rewardDescription", 200000)
                d.count("targetCount", 1, Int.MAX_VALUE.toLong())
            }
            "vow" -> {
                d.fields("$common aksharas completedAtMillis")
                d.count("targetCount", 1); d.count("aksharas", 1, Int.MAX_VALUE.toLong())
                d.nullableMillis("completedAtMillis")
            }
            else -> error("Invalid goal kind")
        }
        d.identifier("mantraId"); d.count("baselineCount")
        d.millis("startedAtMillis"); d.nullableMillis("endedAtMillis")
        // End dates can precede starts in legacy competing-goal reconciliation.
    }

    private fun practice(d: Map<String, Any?>) {
        when (d["type"]) {
            "session" -> {
                d.fields("type id entryId mantraId mode overallScore wordScoresJson transcription isCorrect durationMs createdAt correctness completeness pronunciation",
                    "projectionDate projectionZoneId childGoalEligible assessmentJson")
                if ("assessmentJson" in d) {
                    d.string("assessmentJson", 199999)
                    val detail = runCatching { org.json.JSONObject(d.text("assessmentJson")) }.getOrNull()
                    require(detail?.optInt("schemaVersion") == 1) { "Invalid assessment detail version" }
                }
                if (d.keys.any { it in setOf("projectionDate", "projectionZoneId", "childGoalEligible") }) {
                    d.string("projectionZoneId", 200); d.bool("childGoalEligible")
                    val zone = ZoneId.of(d.text("projectionZoneId"))
                    require(d.text("projectionDate") == java.time.Instant.ofEpochMilli(d.integer("createdAt"))
                        .atZone(zone).toLocalDate().toString()) { "Invalid occurrence date" }
                    require(d["childGoalEligible"] != true || (d["mode"] == "NORMAL" && d["isCorrect"] == true)) { "Ineligible reward session" }
                }
                d.nullableId("entryId"); d.identifier("mantraId")
                require(d["mode"] in setOf("PRACTICE", "NORMAL")) { "Invalid session mode" }
                for (field in listOf("overallScore", "correctness", "completeness", "pronunciation")) {
                    val score = (d[field] as? Number)?.toDouble()
                    require(score != null && score.isFinite() && score in 0.0..1.0) { "Invalid $field" }
                }
                d.bool("isCorrect"); d.count("durationMs"); d.millis("createdAt")
                d.string("wordScoresJson", 199999); d.string("transcription", 199999)
                if (d.text("wordScoresJson").isNotBlank()) for (word in d.text("wordScoresJson").split(',')) {
                    val score = word.substringAfter(':', "").toDoubleOrNull()
                    require(word.substringBefore(':', "").isNotBlank() && score != null && score.isFinite() && score in 0.0..1.0) { "Invalid word score" }
                }
            }
            "sandhya" -> {
                d.fields("type id dateKey period scheduledEpochMillis status observedEpochMillis")
                d.date(); d.period(); d.millis("scheduledEpochMillis"); d.nullableMillis("observedEpochMillis")
                require(d["status"] in setOf("NOTIFIED", "MISSED", "OBSERVED")) { "Invalid observance status" }
                require(d["id"] == "${d.text("dateKey")}_${d.text("period")}") { "Invalid observance identity" }
            }
            "change" -> {
                d.fields("type id dateKey period zoneId mode previousQuantity quantity revision createdAtMillis targetCompleted vowCompleted completionReversed milestonesCrossed")
                d.date(); d.period(); d.zone(); d.count("quantity"); d.revision(); d.millis("createdAtMillis")
                if (d["previousQuantity"] != null) d.count("previousQuantity")
                require(d["mode"] in setOf("RECORD", "ADD", "EDIT", "COUNTER")) { "Invalid change mode" }
                for (key in listOf("targetCompleted", "vowCompleted", "completionReversed")) d.bool(key)
                d.count("milestonesCrossed", 0, Int.MAX_VALUE.toLong())
            }
            else -> error("Unsupported practice type")
        }
    }

    private fun conflict(d: Map<String, Any?>) {
        d.fields("id entryId first second resolved")
        d.identifier("entryId"); d.bool("resolved")
        for (key in listOf("first", "second")) {
            @Suppress("UNCHECKED_CAST")
            val revision = d[key] as? Map<String, Any?> ?: error("Invalid conflict revision")
            require(validate("entry", d.text("entryId"), revision) == null) { "Invalid conflict revision" }
        }
    }

    private fun Map<String, Any?>.fields(required: String, optional: String = "") {
        val needed = required.split(' ').toSet()
        require(keys.containsAll(needed) && (keys - needed - optional.split(' ').toSet() - "schemaVersion").isEmpty()) { "Missing or unknown fields" }
    }
    private fun Map<String, Any?>.integer(key: String): Long {
        val n = this[key]
        require(n is Long || n is Int || n is Short || n is Byte) { "Invalid integer: $key" }
        return (n as Number).toLong()
    }
    private fun Map<String, Any?>.count(key: String, min: Long = 0, max: Long = Long.MAX_VALUE) {
        require(integer(key) in min..max) { "Out of range: $key" }
    }
    private fun Map<String, Any?>.revision() = count("revision", 1, Long.MAX_VALUE - 1)
    private fun Map<String, Any?>.millis(key: String) = count(key, 0, LAST_MILLIS)
    private fun Map<String, Any?>.nullableMillis(key: String) { if (this[key] != null) millis(key) }
    private fun Map<String, Any?>.string(key: String, max: Int) {
        require(this[key] is String && (this[key] as String).length <= max) { "Invalid text: $key" }
    }
    private fun Map<String, Any?>.identifier(key: String) {
        string(key, 1400)
        require(text(key).isNotBlank() && '/' !in text(key) && text(key) !in setOf(".", "..")) { "Invalid identity: $key" }
    }
    private fun Map<String, Any?>.nullableId(key: String) { if (this[key] != null) identifier(key) }
    private fun Map<String, Any?>.bool(key: String) { require(this[key] is Boolean) { "Invalid boolean: $key" } }
    private fun Map<String, Any?>.date() {
        require(text("dateKey").matches(Regex("[0-9]{8}"))) { "Invalid date" }
        require(SandhyaDateKey.of(SandhyaDateKey.parse(text("dateKey"))) == text("dateKey") && !text("dateKey").startsWith("0000")) { "Invalid calendar date" }
    }
    private fun Map<String, Any?>.zone() { string("zoneId", 200); ZoneId.of(text("zoneId")) }
    private fun Map<String, Any?>.period() { require(this["period"] in periods) { "Invalid period" } }
}
