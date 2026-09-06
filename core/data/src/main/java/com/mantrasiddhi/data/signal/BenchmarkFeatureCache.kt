/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.mantrasiddhi.data.mantra.MantraBenchmarkResources
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticFeatures
import com.mantrasiddhi.domain.mantra.Mantra
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes a mantra's benchmark recitation once and caches its **per-word** MFCC features
 * for reuse by every pronunciation comparison.
 *
 * Decoding (compressed audio to 16 kHz mono PCM) is Android-specific so it lives in the
 * data layer; what it caches is the Android-free [AcousticFeatures] the domain scorer
 * consumes. Results are memoised per mantra id, so an app carrying several mantras decodes
 * each recording at most once and never confuses one mantra's windows with another's.
 *
 * Any failure yields empty features so the engine degrades to the text-only path.
 */
@Singleton
class BenchmarkFeatureCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mfccExtractor: MfccExtractor,
    private val benchmarkResources: MantraBenchmarkResources
) {

    private val featuresByMantra = ConcurrentHashMap<String, List<AcousticFeatures>>()
    private val pcmByMantra = ConcurrentHashMap<String, List<FloatArray>>()

    /** Sample rate of the per-word PCM returned by [pcmPerWord]. */
    fun sampleRate(): Int = TARGET_SAMPLE_RATE

    /**
     * Returns one [AcousticFeatures] per word of [mantra], in word order.
     *
     * Computed on first call and cached thereafter. On any decode or extract failure every
     * entry is [AcousticFeatures.EMPTY].
     */
    fun featuresPerWord(mantra: Mantra): List<AcousticFeatures> =
        // computeIfAbsent, not getOrPut: decoding a recording is expensive enough that two
        // recitations settling at once must not both do it.
        featuresByMantra.computeIfAbsent(mantra.id) {
            sliceBenchmark(mantra, empty = AcousticFeatures.EMPTY) { pcm ->
                mfccExtractor.extract(pcm, TARGET_SAMPLE_RATE)
            }
        }

    /**
     * Returns the decoded benchmark PCM (16 kHz mono, `-1.0..1.0`) sliced per word of
     * [mantra], in word order. Reused by the optional swara/pitch layer so a recording is
     * never decoded twice. On any failure each entry is an empty array.
     */
    fun pcmPerWord(mantra: Mantra): List<FloatArray> =
        pcmByMantra.computeIfAbsent(mantra.id) {
            sliceBenchmark(mantra, empty = FloatArray(0)) { pcm -> pcm }
        }

    /**
     * Decodes [mantra]'s benchmark and maps each word's window through [transform],
     * substituting [empty] wherever a window is missing, out of range, or the whole
     * recording could not be decoded.
     */
    private fun <T> sliceBenchmark(mantra: Mantra, empty: T, transform: (FloatArray) -> T): List<T> {
        val pcm = runCatching { decodeToMonoPcm(mantra) }.getOrNull()
            ?: return mantra.words.map { empty }
        return mantra.words.map { word ->
            val startSample = msToSample(word.benchmarkStartMs)
            val endSample = msToSample(word.benchmarkEndMs).coerceAtMost(pcm.size)
            if (endSample <= startSample) empty else transform(pcm.copyOfRange(startSample, endSample))
        }
    }

    private fun msToSample(ms: Long): Int = (ms * TARGET_SAMPLE_RATE / MILLIS_PER_SECOND).toInt()

    /**
     * Decodes [mantra]'s benchmark resource to normalised mono PCM at [TARGET_SAMPLE_RATE].
     * Returns `null` when the mantra has no recording or decoding fails.
     */
    private fun decodeToMonoPcm(mantra: Mantra): FloatArray? {
        val resourceId = benchmarkResources.audioResourceId(mantra) ?: return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.resources.openRawResourceFd(resourceId).use { afd ->
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            val trackIndex = selectAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            val decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            codec = decoder
            return resampleTo16k(decodeStream(extractor, decoder, channels), sourceRate)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return null
    }

    /** Drives the codec to completion, returning down-mixed mono float PCM. */
    private fun decodeStream(extractor: MediaExtractor, codec: MediaCodec, channels: Int): FloatArray {
        val out = ArrayList<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(inBuffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outIndex >= 0) {
                val outBuffer = codec.getOutputBuffer(outIndex)!!
                appendMonoSamples(outBuffer, bufferInfo, channels, out)
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEos = true
                }
            }
        }
        return out.toFloatArray()
    }

    /** Reads 16-bit PCM from [buffer], down-mixes channels and appends normalised floats. */
    private fun appendMonoSamples(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channels: Int,
        out: ArrayList<Float>
    ) {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frameChannels = channels.coerceAtLeast(1)
        val total = shorts.remaining()
        var i = 0
        while (i + frameChannels <= total) {
            var sum = 0
            for (c in 0 until frameChannels) sum += shorts.get(i + c).toInt()
            out.add((sum.toFloat() / frameChannels) / Short.MAX_VALUE)
            i += frameChannels
        }
    }

    /** Linear-interpolation resample of [mono] from [sourceRate] to [TARGET_SAMPLE_RATE]. */
    private fun resampleTo16k(mono: FloatArray, sourceRate: Int): FloatArray {
        if (mono.isEmpty() || sourceRate <= 0 || sourceRate == TARGET_SAMPLE_RATE) return mono
        val ratio = TARGET_SAMPLE_RATE.toDouble() / sourceRate
        val outLength = (mono.size * ratio).toInt()
        val out = FloatArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i / ratio
            val idx = srcPos.toInt()
            val frac = (srcPos - idx).toFloat()
            val a = mono[idx.coerceIn(0, mono.size - 1)]
            val b = mono[(idx + 1).coerceIn(0, mono.size - 1)]
            out[i] = a + (b - a) * frac
        }
        return out
    }

    private companion object {
        const val TARGET_SAMPLE_RATE = 16_000
        const val MILLIS_PER_SECOND = 1000L
        const val TIMEOUT_US = 10_000L
    }
}
