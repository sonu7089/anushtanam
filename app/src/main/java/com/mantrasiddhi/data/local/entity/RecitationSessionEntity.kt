/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession

@Entity(tableName = "recitation_sessions")
data class RecitationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val mode: String, // PRACTICE or NORMAL
    val overallScore: Double,
    val wordScoresJson: String, // format: "word1:0.95,word2:0.87"
    val transcription: String,
    val isCorrect: Boolean,
    val durationMs: Long,
    val createdAt: Long
) {
    fun toDomain(): RecitationSession {
        return RecitationSession(
            id = id,
            userId = userId,
            mode = RecitationMode.valueOf(mode),
            overallScore = overallScore,
            wordScores = deserializeWordScores(wordScoresJson),
            transcription = transcription,
            isCorrect = isCorrect,
            durationMs = durationMs,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(domain: RecitationSession): RecitationSessionEntity {
            return RecitationSessionEntity(
                id = domain.id,
                userId = domain.userId,
                mode = domain.mode.name,
                overallScore = domain.overallScore,
                wordScoresJson = serializeWordScores(domain.wordScores),
                transcription = domain.transcription,
                isCorrect = domain.isCorrect,
                durationMs = domain.durationMs,
                createdAt = domain.createdAt
            )
        }

        private fun serializeWordScores(map: Map<String, Double>): String {
            return map.entries.joinToString(",") { "${it.key}:${it.value}" }
        }

        private fun deserializeWordScores(str: String): Map<String, Double> {
            if (str.isBlank()) return emptyMap()
            return try {
                str.split(",").associate {
                    val parts = it.split(":")
                    val word = parts.getOrNull(0) ?: ""
                    val score = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                    word to score
                }
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}
