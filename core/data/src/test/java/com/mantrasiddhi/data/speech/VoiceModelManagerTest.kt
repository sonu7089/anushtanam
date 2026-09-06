package com.mantrasiddhi.data.speech

import android.content.Intent
import android.speech.RecognitionListener
import kotlinx.coroutines.flow.toList
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import com.mantrasiddhi.domain.model.VoiceModelState
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceModelManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val factory = mockk<SpeechRecognizerFactory>()
    private val native = mockk<SpeechRecognizer>(relaxed = true)
    private val default = mockk<SpeechRecognizer>(relaxed = true)
    private val intent = mockk<Intent>()
    private lateinit var manager: VoiceModelManager

    @BeforeEach fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { factory.mainExecutor } returns Executor { it.run() }
        every { factory.intent(any()) } returns intent
        every { factory.availableBackends() } returns listOf(RecognitionBackend.ON_DEVICE, RecognitionBackend.DEFAULT_SERVICE)
        every { factory.create(RecognitionBackend.ON_DEVICE) } returns native
        every { factory.create(RecognitionBackend.DEFAULT_SERVICE) } returns default
        manager = VoiceModelManager(factory)
    }

    @AfterEach fun tearDown() { Dispatchers.resetMain() }

    private fun support(rec: SpeechRecognizer, installed: List<String> = emptyList(),
        pending: List<String> = emptyList(), supported: List<String> = emptyList(), online: List<String> = emptyList()) {
        val result = mockk<RecognitionSupport>()
        every { result.installedOnDeviceLanguages } returns installed
        every { result.pendingOnDeviceLanguages } returns pending
        every { result.supportedOnDeviceLanguages } returns supported
        every { result.onlineLanguages } returns online
        every { rec.checkRecognitionSupport(any(), any(), any()) } answers {
            thirdArg<RecognitionSupportCallback>().onSupportResult(result)
        }
    }

    private fun error(rec: SpeechRecognizer, code: Int) {
        every { rec.checkRecognitionSupport(any(), any(), any()) } answers {
            thirdArg<RecognitionSupportCallback>().onError(code)
        }
    }

    @Test fun `confirmed native pack wins without opening another service`() = runTest(dispatcher) {
        support(native, installed = listOf("en-US", "hi-IN"))
        assertEquals(VoiceModelState.READY, manager.checkStatus().state)
        assertSame(native, manager.createForListening())
        verify(exactly = 0) { factory.create(RecognitionBackend.DEFAULT_SERVICE) }
        verify(exactly = 1) { native.destroy() }
    }

    @Test fun `default service with installed offline Hindi works without a dedicated recognizer`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns listOf(RecognitionBackend.DEFAULT_SERVICE)
        support(default, installed = listOf("hi_IN"))
        assertEquals(VoiceModelState.READY, manager.checkStatus().state)
        assertSame(default, manager.createForListening())
        manager.recognitionIntent()
        verify { factory.intent("hi-IN") }
    }

    @Test fun `installed default pack wins when dedicated service does not support Hindi`() = runTest(dispatcher) {
        error(native, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        support(default, installed = listOf("hi"))
        assertEquals(VoiceModelState.READY, manager.checkStatus().state)
        assertSame(default, manager.createForListening())
        manager.recognitionIntent()
        verify { factory.intent("hi") }
    }

    @Test fun `unsupported metadata check does not pretend the model is missing`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns listOf(RecognitionBackend.ON_DEVICE)
        error(native, SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
        assertEquals(VoiceModelState.CHECK_UNAVAILABLE, manager.checkStatus().state)
        assertNull(manager.createForListening())
        assertTrue(manager.allowUnverifiedTrial())
        assertSame(native, manager.createForListening())
        manager.checkStatus()
        assertTrue(manager.trialAllowed)
    }

    @Test fun `a confirmed missing model revokes an earlier unverified trial`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns listOf(RecognitionBackend.ON_DEVICE)
        error(native, SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
        manager.checkStatus(); manager.allowUnverifiedTrial()
        error(native, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(VoiceModelState.NOT_INSTALLED, manager.checkStatus().state)
        assertFalse(manager.trialAllowed)
        assertFalse(manager.allowUnverifiedTrial())
        assertNull(manager.createForListening())
    }

    @Test fun `non Hindi support does not unlock an offline session`() = runTest(dispatcher) {
        support(native, installed = listOf("en-US"), supported = listOf("en-US"))
        support(default, online = listOf("hi-IN"))
        assertEquals(VoiceModelState.UNSUPPORTED, manager.checkStatus().state)
        assertNull(manager.createForListening())
    }

    @Test fun `no service is distinct from an uncheckable service`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns emptyList()
        assertEquals(VoiceModelState.RECOGNIZER_UNAVAILABLE, manager.checkStatus().state)
        assertFalse(manager.allowUnverifiedTrial())
    }

    @Test fun `pending download is preserved until the pack is installed`() = runTest(dispatcher) {
        support(native, pending = listOf("hi-IN"), supported = listOf("hi-IN"))
        support(default)
        assertEquals(VoiceModelState.DOWNLOADING, manager.checkStatus().state)
        support(native, installed = listOf("hi-IN"), pending = listOf("hi-IN"))
        assertEquals(VoiceModelState.READY, manager.checkStatus().state)
    }

    @Test fun `timeout releases the recognizer and allows a deliberate trial`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns listOf(RecognitionBackend.ON_DEVICE)
        every { native.checkRecognitionSupport(any(), any(), any()) } just Runs
        val result = async { manager.checkStatus() }
        advanceUntilIdle()
        assertEquals(VoiceModelState.CHECK_UNAVAILABLE, result.await().state)
        verify(exactly = 1) { native.destroy() }
    }

    @Test fun `cancelled checks release the recognizer and ignore late callbacks`() = runTest(dispatcher) {
        val callback = slot<RecognitionSupportCallback>()
        every { native.checkRecognitionSupport(any(), any(), capture(callback)) } just Runs
        val job = launch { manager.checkStatus() }
        runCurrent(); job.cancelAndJoin()
        callback.captured.onError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        verify(exactly = 1) { native.destroy() }
        assertNull(manager.createForListening())
    }

    @Test fun `creation failure is retryable and does not crash the gate`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns listOf(RecognitionBackend.ON_DEVICE)
        every { factory.create(RecognitionBackend.ON_DEVICE) } throws UnsupportedOperationException()
        assertEquals(VoiceModelState.CHECK_UNAVAILABLE, manager.checkStatus().state)
    }

    @Test fun `speech errors use the Android language constants`() {
        val service = SpeechRecognitionService(manager)
        assertEquals(com.mantrasiddhi.resources.R.string.speech_error_language_not_supported,
            service.errorMessageResId(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertEquals(com.mantrasiddhi.resources.R.string.speech_error_model_missing,
            service.errorMessageResId(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
        assertEquals(com.mantrasiddhi.resources.R.string.speech_error_client,
            service.errorMessageResId(SpeechRecognizer.ERROR_CLIENT))
        assertEquals(com.mantrasiddhi.resources.R.string.speech_error_unknown,
            service.errorMessageResId(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT))
    }

    @Test fun `empty metadata permits a trial but never claims the pack is installed`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns listOf(RecognitionBackend.DEFAULT_SERVICE)
        support(default)
        assertEquals(VoiceModelState.CHECK_UNAVAILABLE, manager.checkStatus().state)
        assertNull(manager.createForListening())
    }

    @Test fun `the selected default service handles recording and is released after an error`() = runTest(dispatcher) {
        every { factory.availableBackends() } returns listOf(RecognitionBackend.DEFAULT_SERVICE)
        support(default, installed = listOf("hi-IN"))
        manager.checkStatus()
        val listener = slot<RecognitionListener>()
        every { default.setRecognitionListener(capture(listener)) } just Runs
        every { default.startListening(intent) } answers { listener.captured.onError(SpeechRecognizer.ERROR_CLIENT) }
        val events = SpeechRecognitionService(manager).startListening().toList()
        assertEquals(1, events.size)
        assertEquals(com.mantrasiddhi.resources.R.string.speech_error_client, (events.single() as SpeechResult.Error).messageResId)
        verify(exactly = 1) { default.startListening(intent); default.stopListening(); default.cancel() }
        verify(exactly = 2) { default.destroy() } // Support query and recording session.
    }

    @Test fun `cancelling a microphone session always tears down the selected recognizer`() = runTest(dispatcher) {
        support(native, installed = listOf("hi-IN"))
        manager.checkStatus()
        val job = launch { SpeechRecognitionService(manager).startListening().collect {} }
        runCurrent(); job.cancelAndJoin()
        verify(exactly = 1) { native.stopListening(); native.cancel() }
        verify(exactly = 2) { native.destroy() }
    }

    @Test fun `temporary metadata failures keep a previously confirmed service ready`() = runTest(dispatcher) {
        support(native, installed = listOf("hi-IN"))
        manager.checkStatus()
        error(native, SpeechRecognizer.ERROR_CLIENT)
        assertEquals(VoiceModelState.READY, manager.checkStatus().state)
        assertSame(native, manager.createForListening())
        manager.reportRecognitionError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertNull(manager.createForListening())
    }
}
