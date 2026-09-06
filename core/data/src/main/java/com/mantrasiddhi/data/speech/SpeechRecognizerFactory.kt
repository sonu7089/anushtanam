package com.mantrasiddhi.data.speech

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal enum class RecognitionBackend { ON_DEVICE, DEFAULT_SERVICE }

/** Shared service selection for the model check, download, and microphone session. */
@Singleton
class SpeechRecognizerFactory @Inject constructor(@ApplicationContext private val context: Context) {
    internal val mainExecutor: Executor get() = ContextCompat.getMainExecutor(context)

    internal fun intent(language: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
    }

    internal fun availableBackends(): List<RecognitionBackend> = buildList {
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) add(RecognitionBackend.ON_DEVICE)
        if (SpeechRecognizer.isRecognitionAvailable(context)) add(RecognitionBackend.DEFAULT_SERVICE)
    }

    internal fun create(backend: RecognitionBackend): SpeechRecognizer = when (backend) {
        RecognitionBackend.ON_DEVICE -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        RecognitionBackend.DEFAULT_SERVICE -> SpeechRecognizer.createSpeechRecognizer(context)
    }
}
