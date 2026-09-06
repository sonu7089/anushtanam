/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAnalyzer
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticAssessment
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticPronunciationScorer
import com.mantrasiddhi.domain.evaluation.acoustic.AudioQualityGate
import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio
import com.mantrasiddhi.domain.evaluation.acoustic.WordSegmenter
import com.mantrasiddhi.domain.mantra.Mantra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-layer implementation of the domain [AcousticAnalyzer] port.
 *
 * It wires the DSP front-end ([MfccExtractor] + [BenchmarkFeatureCache]) to the
 * pure-domain comparison ([WordSegmenter], [AcousticPronunciationScorer],
 * [AudioQualityGate]). All work runs on [Dispatchers.Default] (off the main thread)
 * and any failure degrades to [AcousticAssessment.notAssessed] so the text path is
 * always preserved.
 */
@Singleton
class AcousticAnalyzerImpl @Inject constructor(
    private val mfccExtractor: MfccExtractor,
    private val benchmarkFeatureCache: BenchmarkFeatureCache
) : AcousticAnalyzer {

    override suspend fun analyze(
        mantra: Mantra,
        audio: RecordedAudio,
        sttConfidence: Float,
        config: EvaluationConfig
    ): AcousticAssessment {
        // Without per-word windows in a reference recording there is nothing to compare
        // the learner's audio against, so the text-only path stands.
        if (audio.isEmpty || !mantra.hasBenchmarkTimings) return AcousticAssessment.notAssessed()
        return withContext(Dispatchers.Default) {
            runCatching { analyzeInternal(mantra, audio, sttConfidence, config) }
                .getOrElse { AcousticAssessment.notAssessed() }
        }
    }

    private fun analyzeInternal(
        mantra: Mantra,
        audio: RecordedAudio,
        sttConfidence: Float,
        config: EvaluationConfig
    ): AcousticAssessment {
        val learner = mfccExtractor.extract(audio.samples, audio.sampleRate)
        if (learner.isEmpty) return AcousticAssessment.notAssessed()

        val snrDb = AudioQualityGate.estimateSnrDb(learner.energies)
        val assessable = AudioQualityGate.isAssessable(
            energies = learner.energies,
            sttConfidence = sttConfidence,
            minSnrDb = config.minSnrDb,
            minConfidence = config.minConfidence,
            minVoicedFrames = config.acousticMinVoicedFrames
        )
        if (!assessable) return AcousticAssessment.notAssessed(snrDb)

        val segments = WordSegmenter.segment(
            energies = learner.energies,
            benchmarkWordDurationsMs = mantra.benchmarkWordDurationsMs,
            energyThresholdRatio = config.acousticEnergyThresholdRatio,
            minVoicedFrames = config.acousticMinVoicedFrames
        )
        return AcousticPronunciationScorer.score(
            learner = learner,
            segments = segments,
            benchmark = benchmarkFeatureCache.featuresPerWord(mantra),
            distanceScale = config.acousticDistanceScale.toDouble(),
            assessed = true,
            snrDb = snrDb
        )
    }
}
