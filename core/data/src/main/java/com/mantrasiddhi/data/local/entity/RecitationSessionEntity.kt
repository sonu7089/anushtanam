/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession

@Entity(tableName = "recitation_sessions", indices = [Index(value = ["userId", "createdAt", "id"]),
    Index(value = ["userId", "stableId"], unique = true), Index(value = ["userId", "acceptedEntryId"], unique = true)])
data class RecitationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    // Which mantra was recited. Rows written before the app carried a catalog predate the
    // column, so the v6 -> v7 migration backfills them with the mantra it shipped with;
    // the default is declared here too so the schema and the migration agree exactly.
    @ColumnInfo(defaultValue = LEGACY_MANTRA_ID) val mantraId: String,
    val mode: String, // PRACTICE or NORMAL
    val overallScore: Double,
    val wordScoresJson: String, // format: "word1:0.95,word2:0.87"
    val transcription: String,
    val isCorrect: Boolean,
    val durationMs: Long,
    val createdAt: Long,
    // Per-axis scores (Phase 4.4). Default to the overall score so rows written by
    // older code (and the v2→v3 migration backfill) remain coherent.
    val correctness: Double = overallScore,
    val completeness: Double = overallScore,
    val pronunciation: Double = overallScore,
    @ColumnInfo(defaultValue = "''") val stableId: String = java.util.UUID.randomUUID().toString(),
    /** Eventual backlink metadata, separate from the immutable assessment. */
    val acceptedEntryId: String? = null,
    /** Occurrence metadata for durable cloud counting; null denotes pre-v16 history. */
    val projectionDate: String? = null,
    val projectionZoneId: String? = null,
    val childGoalEligible: Boolean? = null,
    @ColumnInfo(defaultValue = "''") val assessmentJson: String = ""
) {
    fun toDomain(): RecitationSession {
        return RecitationSession(
            id = id,
            userId = userId,
            mantraId = mantraId,
            mode = RecitationMode.valueOf(mode),
            overallScore = overallScore,
            wordScores = deserializeWordScores(wordScoresJson),
            transcription = transcription,
            isCorrect = isCorrect,
            durationMs = durationMs,
            correctness = correctness,
            completeness = completeness,
            pronunciation = pronunciation,
            createdAt = createdAt, stableId = stableId, assessmentJson = assessmentJson
        )
    }

    companion object {
        /**
         * Mantra assumed for rows written before the app carried a mantra catalog. Kept as
         * a literal rather than read from the catalog: it records what those rows actually
         * were, and must not follow a later change of default mantra.
         */
        const val LEGACY_MANTRA_ID = "gayatri"

        fun fromDomain(domain: RecitationSession): RecitationSessionEntity {
            return RecitationSessionEntity(
                id = domain.id,
                userId = domain.userId,
                mantraId = domain.mantraId,
                mode = domain.mode.name,
                overallScore = domain.overallScore,
                wordScoresJson = serializeWordScores(domain.wordScores),
                transcription = domain.transcription,
                isCorrect = domain.isCorrect,
                durationMs = domain.durationMs,
                createdAt = domain.createdAt,
                correctness = domain.correctness,
                completeness = domain.completeness,
                pronunciation = domain.pronunciation, stableId = domain.stableId, assessmentJson = domain.assessmentJson
            )
        }

        /**
         * Encodes per-word scores as `word:score,word:score`.
         *
         * Despite the column name this has never been JSON, and changing it now would mean
         * rewriting every row of every sadhak's history for no gain. Transliterations are
         * plain Latin letters, so neither separator can occur inside a key.
         */
        private fun serializeWordScores(scores: Map<String, Double>): String =
            scores.entries.joinToString(ENTRY_SEPARATOR) { "${it.key}$KEY_SEPARATOR${it.value}" }

        /**
         * Decodes [serializeWordScores]. A malformed entry — a truncated row, a format from
         * some future version — is dropped rather than read as zero: a fabricated 0.0 would
         * count against a word the sadhak may well have recited perfectly.
         */
        private fun deserializeWordScores(encoded: String): Map<String, Double> {
            if (encoded.isBlank()) return emptyMap()
            return encoded.split(ENTRY_SEPARATOR).mapNotNull { entry ->
                val word = entry.substringBefore(KEY_SEPARATOR, missingDelimiterValue = "")
                val score = entry.substringAfter(KEY_SEPARATOR, missingDelimiterValue = "").toDoubleOrNull()
                if (word.isEmpty() || score == null) null else word to score
            }.toMap()
        }

        private const val ENTRY_SEPARATOR = ","
        private const val KEY_SEPARATOR = ":"
    }
}
