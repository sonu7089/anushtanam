/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import com.mantrasiddhi.domain.evaluation.EvaluationConfig
import com.mantrasiddhi.domain.evaluation.acoustic.RecordedAudio
import com.mantrasiddhi.domain.evaluation.acoustic.SelfAudioSegmenter
import com.mantrasiddhi.domain.evaluation.acoustic.SwaraAnalyzer
import com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment
import com.mantrasiddhi.domain.evaluation.acoustic.SwaraScorer
import com.mantrasiddhi.domain.mantra.Mantra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data-layer implementation of the [SwaraAnalyzer] port (§5.5).
 *
 * Segments the learner's recording into words (reusing [SelfAudioSegmenter]), extracts
 * each word's f0 contour with [PitchExtractor], and compares it against the cached
 * benchmark contour via the pure-domain [SwaraScorer]. All work runs off the main
 * thread and any failure degrades to [SwaraAssessment.notAssessed] — swara is an
 * opt-in extra and must never break a recitation.
 */
@Singleton
class SwaraAnalyzerImpl @Inject constructor(
    private val pitchExtractor: PitchExtractor,
    private val benchmarkPitchCache: BenchmarkPitchCache
) : SwaraAnalyzer {

    override suspend fun analyze(
        mantra: Mantra,
        audio: RecordedAudio,
        config: EvaluationConfig
    ): SwaraAssessment {
        if (audio.isEmpty || !mantra.hasBenchmarkTimings) return SwaraAssessment.notAssessed()
        return withContext(Dispatchers.Default) {
            runCatching { analyzeInternal(mantra, audio, config) }
                .getOrElse { SwaraAssessment.notAssessed() }
        }
    }

    private fun analyzeInternal(
        mantra: Mantra,
        audio: RecordedAudio,
        config: EvaluationConfig
    ): SwaraAssessment {
        val benchmark = benchmarkPitchCache.contoursPerWord(mantra)
        if (benchmark.all { it.isEmpty() }) return SwaraAssessment.notAssessed()

        val durations = mantra.benchmarkWordDurationsMs
        val learnerContours = mantra.words.map { word ->
            val range = SelfAudioSegmenter.sampleRangeFor(audio, durations, word.index)
                ?: return@map null
            val slice = audio.samples.copyOfRange(range.first, range.last + 1)
            pitchExtractor.extract(slice, audio.sampleRate)
        }

        return SwaraScorer.score(
            learnerContours = learnerContours,
            benchmarkContours = benchmark,
            distanceScale = config.swaraDistanceScale.toDouble(),
            minVoicedFrames = config.swaraMinVoicedFrames,
            flatThresholdSemitones = config.swaraFlatThresholdSemitones
        )
    }
}
