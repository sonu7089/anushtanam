/*
 * Anushtanam - Gayatri Mantra Recitation App
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
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
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
        audio: RecordedAudio,
        sttConfidence: Float,
        config: EvaluationConfig
    ): AcousticAssessment {
        if (audio.isEmpty) return AcousticAssessment.notAssessed()
        return withContext(Dispatchers.Default) {
            runCatching { analyzeInternal(audio, sttConfidence, config) }
                .getOrElse { AcousticAssessment.notAssessed() }
        }
    }

    private fun analyzeInternal(
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

        val benchmark = benchmarkFeatureCache.featuresPerWord()
        val durations = GayatriMantra.words.map {
            (it.benchmarkEndMs - it.benchmarkStartMs).toDouble()
        }
        val segments = WordSegmenter.segment(
            energies = learner.energies,
            benchmarkWordDurationsMs = durations,
            energyThresholdRatio = config.acousticEnergyThresholdRatio,
            minVoicedFrames = config.acousticMinVoicedFrames
        )
        return AcousticPronunciationScorer.score(
            learner = learner,
            segments = segments,
            benchmark = benchmark,
            distanceScale = config.acousticDistanceScale.toDouble(),
            assessed = true,
            snrDb = snrDb
        )
    }
}
