/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.signal

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.evaluation.acoustic.AcousticFeatures
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes the bundled benchmark recitation once and caches its **per-word** MFCC
 * features (keyed to the [GayatriMantra] word timestamps) for reuse by every
 * pronunciation comparison.
 *
 * Decoding (MP3 → 16 kHz mono PCM) is Android-specific so it lives in the data
 * layer; the cached result is the Android-free [AcousticFeatures] the domain scorer
 * consumes. All work is lazy and computed at most once; any failure yields empty
 * features so the engine degrades to the text-only path.
 */
@Singleton
class BenchmarkFeatureCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mfccExtractor: MfccExtractor
) {

    @Volatile
    private var cached: List<AcousticFeatures>? = null

    @Volatile
    private var cachedPcm: List<FloatArray>? = null
    private val lock = Any()

    /** Sample rate of the per-word PCM returned by [pcmPerWord]. */
    fun sampleRate(): Int = TARGET_SAMPLE_RATE

    /**
     * Returns one [AcousticFeatures] per reference word, in word order.
     *
     * Computed lazily on first call and cached thereafter. On any decode/extract
     * failure every entry is [AcousticFeatures.EMPTY].
     */
    fun featuresPerWord(): List<AcousticFeatures> {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val computed = runCatching { computeFeatures() }
                .getOrElse { GayatriMantra.words.map { AcousticFeatures.EMPTY } }
            cached = computed
            return computed
        }
    }

    /**
     * Returns the decoded benchmark PCM (16 kHz mono, `-1.0..1.0`) sliced per reference
     * word, in word order. Reused by the optional swara/pitch layer so the benchmark
     * audio is decoded at most once. On any failure each entry is an empty array.
     */
    fun pcmPerWord(): List<FloatArray> {
        cachedPcm?.let { return it }
        synchronized(lock) {
            cachedPcm?.let { return it }
            val computed = runCatching { computeWordPcm() }
                .getOrElse { GayatriMantra.words.map { FloatArray(0) } }
            cachedPcm = computed
            return computed
        }
    }

    private fun computeFeatures(): List<AcousticFeatures> {
        val pcm = decodeToMonoPcm() ?: return GayatriMantra.words.map { AcousticFeatures.EMPTY }
        return GayatriMantra.words.map { word ->
            val startSample = msToSample(word.benchmarkStartMs)
            val endSample = msToSample(word.benchmarkEndMs).coerceAtMost(pcm.size)
            if (endSample <= startSample) {
                AcousticFeatures.EMPTY
            } else {
                mfccExtractor.extract(pcm.copyOfRange(startSample, endSample), TARGET_SAMPLE_RATE)
            }
        }
    }

    private fun computeWordPcm(): List<FloatArray> {
        val pcm = decodeToMonoPcm() ?: return GayatriMantra.words.map { FloatArray(0) }
        return GayatriMantra.words.map { word ->
            val startSample = msToSample(word.benchmarkStartMs)
            val endSample = msToSample(word.benchmarkEndMs).coerceAtMost(pcm.size)
            if (endSample <= startSample) FloatArray(0) else pcm.copyOfRange(startSample, endSample)
        }
    }

    private fun msToSample(ms: Long): Int = (ms * TARGET_SAMPLE_RATE / MILLIS_PER_SECOND).toInt()

    /**
     * Decodes the benchmark resource to normalised mono PCM at [TARGET_SAMPLE_RATE].
     * Returns null on any failure.
     */
    private fun decodeToMonoPcm(): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.resources.openRawResourceFd(R.raw.gayatri_mantra_benchmark).use { afd ->
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
            val mono = decodeStream(extractor, decoder, channels)
            return resampleTo16k(mono, sourceRate)
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
