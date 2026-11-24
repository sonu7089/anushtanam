package com.mantrasiddhi.domain.evaluation

import com.mantrasiddhi.domain.evaluation.acoustic.*
import com.mantrasiddhi.domain.evaluation.model.RecitationAssessment
import kotlinx.serialization.json.*

/** Versioned, ordered detail; repeated reference words keep separate positions. */
object AssessmentSnapshot {
    fun encode(assessment: RecitationAssessment, hypotheses: List<Pair<String, Float>>,
        confidence: Float, acoustic: AcousticAssessment?, swara: SwaraAssessment?,
        audioCaptured: Boolean, config: EvaluationConfig): String = json(mapOf(
        "schemaVersion" to 1, "confidence" to confidence,
        "hypotheses" to hypotheses.map { mapOf("text" to it.first, "confidence" to it.second) },
        "lowConfidence" to assessment.lowConfidence, "acousticAssessed" to assessment.acousticAssessed,
        "audioCaptured" to audioCaptured, "snrDb" to acoustic?.snrDb,
        "bands" to mapOf("overall" to assessment.overall.band.name, "correctness" to assessment.correctness.band.name,
            "completeness" to assessment.completeness.band.name, "pronunciation" to assessment.pronunciation.band.name),
        "words" to assessment.wordResults.map(::word),
        "padas" to assessment.padaResults.map { mapOf("pada" to it.pada, "coverage" to it.coverage,
            "averageScore" to it.averageScore, "words" to it.wordResults.map(::word)) },
        "missingWords" to assessment.missingWords, "extraWords" to assessment.extraWords,
        "acousticWords" to acoustic?.wordScores?.map { mapOf("wordIndex" to it.wordIndex,
            "score" to it.score, "pacingRatio" to it.pacingRatio, "pacing" to it.pacing.name) },
        "swara" to swara?.let { mapOf("wordScores" to it.wordScores, "overallScore" to it.overallScore,
            "hint" to it.hint.name, "assessed" to it.assessed) },
        "config" to configSnapshot(config)
    )).toString()

    private fun word(w: WordResult) = mapOf("referenceWord" to w.referenceWord, "spokenWord" to w.spokenWord,
        "score" to w.score, "feedback" to w.feedback?.name, "acousticScore" to w.acousticScore, "pacingRatio" to w.pacingRatio)

    private fun configSnapshot(c: EvaluationConfig) = mapOf(
        "passThreshold" to c.passThreshold, "goodBand" to c.goodBand, "needsWorkBand" to c.needsWorkBand,
        "completenessPassThreshold" to c.completenessPassThreshold, "correctnessPassThreshold" to c.correctnessPassThreshold,
        "feedbackThreshold" to c.feedbackThreshold, "swapThreshold" to c.swapThreshold,
        "partialThreshold" to c.partialThreshold, "matchThreshold" to c.matchThreshold, "minConfidence" to c.minConfidence,
        "correctnessWeight" to c.correctnessWeight, "completenessWeight" to c.completenessWeight,
        "pronunciationWeight" to c.pronunciationWeight, "wrongWordPenalty" to c.wrongWordPenalty,
        "swappedWordPenalty" to c.swappedWordPenalty, "extraWordPenalty" to c.extraWordPenalty,
        "inversionPenalty" to c.inversionPenalty, "wholePadaPenalty" to c.wholePadaPenalty,
        "acousticPronunciationWeight" to c.acousticPronunciationWeight, "acousticDistanceScale" to c.acousticDistanceScale,
        "acousticEnergyThresholdRatio" to c.acousticEnergyThresholdRatio, "acousticMinVoicedFrames" to c.acousticMinVoicedFrames,
        "minSnrDb" to c.minSnrDb, "swaraDistanceScale" to c.swaraDistanceScale,
        "swaraMinVoicedFrames" to c.swaraMinVoicedFrames, "swaraFlatThresholdSemitones" to c.swaraFlatThresholdSemitones)

    private fun json(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Float -> if (v.isFinite()) JsonPrimitive(v) else JsonNull
        is Double -> if (v.isFinite()) JsonPrimitive(v) else JsonNull
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is String -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to json(it.value) })
        is List<*> -> JsonArray(v.map(::json))
        else -> error("Unsupported assessment field")
    }
}
