/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import com.mantrasiddhi.domain.mantra.Mantra
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily extracts and caches a mantra's benchmark **per-word f0 contour** for the optional
 * swara/pitch layer (§5.5), reusing the PCM already decoded by [BenchmarkFeatureCache] so
 * the benchmark audio is never decoded twice.
 *
 * Memoised per mantra id and computed at most once; any failure yields empty contours so
 * the swara layer simply reports "not assessed".
 */
@Singleton
class BenchmarkPitchCache @Inject constructor(
    private val benchmarkFeatureCache: BenchmarkFeatureCache,
    private val pitchExtractor: PitchExtractor
) {

    private val contoursByMantra = ConcurrentHashMap<String, List<FloatArray>>()

    /** One f0 contour (Hz; `0` means unvoiced) per word of [mantra], in word order. */
    fun contoursPerWord(mantra: Mantra): List<FloatArray> =
        contoursByMantra.computeIfAbsent(mantra.id) {
            val sampleRate = benchmarkFeatureCache.sampleRate()
            runCatching {
                benchmarkFeatureCache.pcmPerWord(mantra).map { pitchExtractor.extract(it, sampleRate) }
            }.getOrElse { mantra.words.map { FloatArray(0) } }
        }
}
