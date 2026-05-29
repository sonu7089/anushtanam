/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
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
    @ApplicationContext private val context: Context
) {

    /**
     * Starts listening to audio input and emits transcription results.
     *
     * The SpeechRecognizer runs on the Main thread.
     */
    fun startListening(): Flow<SpeechResult> = callbackFlow {
        val mainDispatcher = Dispatchers.Main
        var recognizer: SpeechRecognizer? = null

        fun initRecognizer(onDevice: Boolean) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                trySend(SpeechResult.Error(-1, "Speech recognition is not available on this device"))
                close()
                return
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (onDevice) {
                    putExtra("android.speech.extra.PREFER_OFFLINE", true)
                }
            }

            val rec = if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            
            recognizer = rec
            
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    // Error 13 is SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE (Android 12+)
                    // Error 14 is SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                    // Error 5 is SpeechRecognizer.ERROR_CLIENT (generic connection/binding issues)
                    if (onDevice && (error == 13 || error == 14 || error == 5)) {
                        if (error == 13 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                val downloadIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                }
                                val triggerRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                                triggerRecognizer.triggerModelDownload(downloadIntent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                        launch(mainDispatcher) {
                            try {
                                rec.destroy()
                            } catch (e: Exception) {
                                // ignore
                            }
                            initRecognizer(onDevice = false)
                        }
                    } else {
                        val message = getErrorMessage(error)
                        trySend(SpeechResult.Error(error, message))
                        close()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    val text = matches?.firstOrNull() ?: ""
                    val confidence = confidences?.firstOrNull() ?: 1.0f
                    trySend(SpeechResult.Final(text, confidence))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    trySend(SpeechResult.Partial(text))
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            rec.startListening(intent)
        }

        launch(mainDispatcher) {
            val initialOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            initRecognizer(onDevice = initialOnDevice)
        }

        awaitClose {
            launch(mainDispatcher) {
                recognizer?.apply {
                    try {
                        stopListening()
                        cancel()
                        destroy()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            13 -> "Hindi offline speech model not downloaded" // ERROR_LANGUAGE_UNAVAILABLE
            14 -> "Language not supported" // ERROR_LANGUAGE_NOT_SUPPORTED
            else -> "Unknown error ($errorCode)"
        }
    }
}
