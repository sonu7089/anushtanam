package com.mantrasiddhi.data.speech

import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.domain.model.VoiceModelState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SpeechPlatformTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun requestContainsHindiAndOfflineOptionsWithoutLanguageDetailsExtras() {
        val intent = SpeechRecognizerFactory(context).intent("hi-IN")
        assertEquals("hi-IN", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        assertEquals(5, intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0))
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES))
        assertFalse(intent.hasExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE))
    }

    @Test fun visibleDefaultServiceIsNotMisreportedAsNoRecognizer() = runBlocking {
        val defaultAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        assumeTrue("This device must have a speech service for the compatibility check", defaultAvailable)
        val nativeAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val result = VoiceModelManager(SpeechRecognizerFactory(context)).checkStatus()
        Log.i("SpeechPlatformTest", "native=$nativeAvailable default=$defaultAvailable state=${result.state} installed=${result.installedLanguages}")
        assertNotEquals(VoiceModelState.RECOGNIZER_UNAVAILABLE, result.state)
    }
}
