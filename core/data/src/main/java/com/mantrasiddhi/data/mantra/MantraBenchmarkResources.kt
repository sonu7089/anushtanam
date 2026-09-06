/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.mantra

import android.content.Context
import com.mantrasiddhi.data.audio.WordTimestamp
import com.mantrasiddhi.domain.mantra.Mantra
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the Android-side pieces of a mantra's benchmark recording.
 *
 * A [Mantra] only *names* its recording; turning that name into a bundled resource, and
 * reading the optional refinement of its word timings, is Android work and lives here.
 * Keeping it in one place means the player and the DSP caches agree on both the audio and
 * the windows, and that adding a mantra needs no new Kotlin.
 *
 * Nothing here throws. A missing recording, a missing timings asset or a malformed one all
 * degrade to "no audio" or "the timings in the definition file", because a benchmark is an
 * enhancement to scoring, never a prerequisite for it.
 */
@Singleton
class MantraBenchmarkResources @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Raw-resource id of [mantra]'s benchmark recording, or `null` when the mantra ships
     * without one or the named resource is not bundled.
     *
     * Resolved by name so a new mantra only has to drop its audio into
     * `core/resources/src/main/res/raw`. `raw/keep.xml` in that module stops the release
     * resource shrinker from removing recordings that are never referenced from code.
     */
    fun audioResourceId(mantra: Mantra): Int? {
        val name = mantra.benchmark?.audioResourceName ?: return null
        @Suppress("DiscouragedApi") // Resolved by name so the catalog stays data-only.
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        return id.takeIf { it != 0 }
    }

    /**
     * Per-word playback windows for [mantra], in word order.
     *
     * The definition file's timings are the baseline; when the mantra names a timings
     * asset, any word it covers is refined from that. Returns an empty list for a mantra
     * with no benchmark.
     */
    fun wordTimestamps(mantra: Mantra): List<WordTimestamp> {
        val benchmark = mantra.benchmark ?: return emptyList()
        val overrides = benchmark.timestampsAsset
            ?.let { readTimestampOverrides(it, benchmark.timestampsUnit.millisPerUnit) }
            .orEmpty()
        return mantra.words.map { word ->
            val override = overrides[word.index]
            WordTimestamp(
                wordIndex = word.index,
                word = word.transliteration,
                startMs = override?.startMs ?: word.benchmarkStartMs,
                endMs = override?.endMs ?: word.benchmarkEndMs
            )
        }
    }

    /**
     * Parses per-word timing overrides from a bundled asset, keyed by word index.
     * Any failure yields an empty map so the definition file's own timings apply.
     */
    private fun readTimestampOverrides(assetPath: String, millisPerUnit: Long): Map<Int, Window> =
        runCatching {
            val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val entries = JSONArray(text)
            buildMap {
                for (i in 0 until entries.length()) {
                    val entry = entries.getJSONObject(i)
                    put(
                        entry.getInt("wordIndex"),
                        Window(
                            startMs = entry.getLong("startMs") * millisPerUnit,
                            endMs = entry.getLong("endMs") * millisPerUnit
                        )
                    )
                }
            }
        }.getOrElse { emptyMap() }

    private data class Window(val startMs: Long, val endMs: Long)
}
