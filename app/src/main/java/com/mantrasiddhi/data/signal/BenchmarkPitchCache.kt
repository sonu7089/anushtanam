/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lazily extracts and caches the benchmark recitation's **per-word f0 contour** for
 * the optional swara/pitch layer (§5.5), reusing the PCM already decoded by
 * [BenchmarkFeatureCache] so the benchmark audio is never decoded twice.
 *
 * Computed at most once; any failure yields empty contours so the swara layer simply
 * reports "not assessed".
 */
@Singleton
class BenchmarkPitchCache @Inject constructor(
    private val benchmarkFeatureCache: BenchmarkFeatureCache,
    private val pitchExtractor: PitchExtractor
) {

    @Volatile
    private var cached: List<FloatArray>? = null
    private val lock = Any()

    /** One f0 contour (Hz; `0` = unvoiced) per reference word, in word order. */
    fun contoursPerWord(): List<FloatArray> {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val sampleRate = benchmarkFeatureCache.sampleRate()
            val computed = runCatching {
                benchmarkFeatureCache.pcmPerWord().map { pitchExtractor.extract(it, sampleRate) }
            }.getOrElse { GayatriMantra.words.map { FloatArray(0) } }
            cached = computed
            return computed
        }
    }
}
