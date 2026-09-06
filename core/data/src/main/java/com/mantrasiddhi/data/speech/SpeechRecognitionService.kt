/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.speech

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import com.mantrasiddhi.resources.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that wraps Android's SpeechRecognizer to provide a Flow-based API
 * for transcribing Gayatri Mantra recitations offline.
 */
@Singleton
class SpeechRecognitionService @Inject constructor(
    private val voiceModelManager: VoiceModelManager
) {

    /**
     * Starts listening to audio input and emits transcription results.
     *
     * The SpeechRecognizer runs on the Main thread.
     */
    fun startListening(): Flow<SpeechResult> = callbackFlow {
        val mainDispatcher = Dispatchers.Main
        var recognizer: SpeechRecognizer? = null

        fun initRecognizer() {
            val rec = try {
                voiceModelManager.createForListening()
            } catch (_: Exception) {
                trySend(SpeechResult.Error(SpeechRecognizer.ERROR_CLIENT, R.string.speech_error_client))
                close()
                return
            }
            if (rec == null) {
                trySend(SpeechResult.Error(ERROR_MODEL_UNAVAILABLE, R.string.speech_error_model_missing))
                close()
                return
            }
            val intent = voiceModelManager.recognitionIntent()

            recognizer = rec

            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { trySend(SpeechResult.EndOfSpeech) }

                override fun onError(error: Int) {
                    voiceModelManager.reportRecognitionError(error)
                    val messageResId = errorMessageResId(error)
                    trySend(SpeechResult.Error(error, messageResId))
                    close()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val alternatives = buildAlternatives(matches, confidences)
                    val best = alternatives.firstOrNull() ?: ("" to DEFAULT_CONFIDENCE)
                    trySend(SpeechResult.Final(best.first, best.second, alternatives))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    trySend(SpeechResult.Partial(text))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                rec.startListening(intent)
            } catch (_: Exception) {
                trySend(SpeechResult.Error(SpeechRecognizer.ERROR_CLIENT, R.string.speech_error_client))
                close()
            }
        }

        launch(mainDispatcher) {
            initRecognizer()
        }

        awaitClose {
            // The main executor outlives this cancelled producer scope, so teardown
            // still runs when the user stops recording or changes tabs.
            val toRelease = recognizer
            recognizer = null
            toRelease?.let(voiceModelManager::releaseAfterListening)
        }
    }

    /**
     * Combines the recognizer's n-best transcriptions with their confidence scores
     * into an ordered, best-first list. When confidences are missing (common on
     * device), [DEFAULT_CONFIDENCE] is used so downstream scoring still works.
     */
    private fun buildAlternatives(
        matches: List<String>?,
        confidences: FloatArray?
    ): List<Pair<String, Float>> {
        if (matches.isNullOrEmpty()) return emptyList()
        return matches.mapIndexed { index, text ->
            text to (confidences?.getOrNull(index) ?: DEFAULT_CONFIDENCE)
        }
    }

    /**
     * Maps a recognizer error code to the sentence the sadhak sees. Resource ids, not text:
     * the Abhyas screen renders this verbatim, so it has to come from `strings.xml`.
     */
    @StringRes
    internal fun errorMessageResId(errorCode: Int): Int {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> R.string.speech_error_audio
            SpeechRecognizer.ERROR_CLIENT -> R.string.speech_error_client
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.speech_error_permission
            SpeechRecognizer.ERROR_NETWORK -> R.string.speech_error_network
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.speech_error_network_timeout
            SpeechRecognizer.ERROR_NO_MATCH -> R.string.speech_error_no_match
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.speech_error_busy
            SpeechRecognizer.ERROR_SERVER -> R.string.speech_error_server
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.speech_error_speech_timeout
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.speech_error_model_missing // ERROR_LANGUAGE_UNAVAILABLE
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> R.string.speech_error_language_not_supported // ERROR_LANGUAGE_NOT_SUPPORTED
            else -> R.string.speech_error_unknown
        }
    }

    private companion object {
        /** Confidence assumed when the recognizer does not report a score. */
        const val DEFAULT_CONFIDENCE = 1.0f

        /** Error code surfaced when the on-device Hindi model is unavailable at capture time. */
        const val ERROR_MODEL_UNAVAILABLE = SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
    }
}
