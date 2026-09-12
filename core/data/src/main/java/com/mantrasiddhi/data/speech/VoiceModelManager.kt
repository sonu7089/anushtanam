/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.speech

import android.content.Intent
import android.os.Build
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import com.mantrasiddhi.domain.model.VoiceModelDownload
import com.mantrasiddhi.domain.model.VoiceModelState
import com.mantrasiddhi.domain.model.VoiceModelStatus
import com.mantrasiddhi.domain.model.VoiceModelSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Resolves offline Hindi support on the same service that will handle the recording. */
@Singleton
class VoiceModelManager @Inject constructor(
    private val factory: SpeechRecognizerFactory
) {
    private data class Selection(val backend: RecognitionBackend, val status: VoiceModelStatus, val language: String)
    private var selection: Selection? = null
    private var approvedTrial: RecognitionBackend? = null
    private val checkMutex = Mutex()

    /** Uncertain metadata is not evidence that the pack is missing. */
    suspend fun checkStatus(): VoiceModelStatus = withContext(Dispatchers.Main) {
        checkMutex.withLock {
            com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("voice.status.check")
            val candidates = mutableListOf<Selection>()
            for (backend in factory.availableBackends()) {
                val status = querySupport(backend)
                val language = status.installedLanguages.firstOrNull(VoiceModelSupport::matchesRequired)
                    ?.replace('_', '-') ?: VoiceModelSupport.REQUIRED_LANGUAGE
                val previous = selection
                val candidate = if (status.state == VoiceModelState.CHECK_UNAVAILABLE &&
                    previous?.backend == backend && previous.status.state == VoiceModelState.READY) {
                    previous // A transient metadata error does not revoke confirmed readiness.
                } else Selection(backend, status, language)
                candidates += candidate
                if (candidate.status.state == VoiceModelState.READY) break
            }
            // Prefer a confirmed installed pack, then a known downloadable one. A service
            // with unknown metadata is more useful than one explicitly rejecting Hindi.
            val preferred = candidates.minByOrNull { priority(it.status.state) }
            selection = preferred
            if (preferred?.status?.state != VoiceModelState.CHECK_UNAVAILABLE ||
                preferred.backend != approvedTrial) approvedTrial = null
            preferred?.status ?: VoiceModelStatus(VoiceModelState.RECOGNIZER_UNAVAILABLE)
        }
    }

    val trialAllowed: Boolean
        get() = selection?.let { it.status.state == VoiceModelState.CHECK_UNAVAILABLE && it.backend == approvedTrial } == true

    /** Only a deliberate action on the unknown-support screen enables a trial. */
    fun allowUnverifiedTrial(): Boolean {
        val current = selection ?: return false
        if (current.status.state != VoiceModelState.CHECK_UNAVAILABLE) return false
        approvedTrial = current.backend
        return true
    }

    /** Called on Main only. Never quietly switches services during a recording. */
    fun createForListening(): SpeechRecognizer? {
        val current = selection ?: return null
        if (current.status.state != VoiceModelState.READY && !trialAllowed) return null
        return factory.create(current.backend)
    }

    /** The support query and microphone use the same language and request options. */
    fun recognitionIntent(language: String = selection?.language ?: VoiceModelSupport.REQUIRED_LANGUAGE): Intent =
        factory.intent(language)

    private suspend fun querySupport(backend: RecognitionBackend): VoiceModelStatus {
        var recognizer: SpeechRecognizer? = null
        return try {
            val rec = factory.create(backend)
            recognizer = rec
            com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("voice.support.query")
            withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    rec.checkRecognitionSupport(recognitionIntent(VoiceModelSupport.REQUIRED_LANGUAGE),
                        factory.mainExecutor, object : RecognitionSupportCallback {
                            override fun onSupportResult(support: RecognitionSupport) {
                                if (!continuation.isActive) return
                                // Online languages, keyboard languages, and TTS voices are
                                // intentionally not evidence of an installed offline pack.
                                val hasMetadata = support.installedOnDeviceLanguages.isNotEmpty() ||
                                    support.pendingOnDeviceLanguages.isNotEmpty() ||
                                    support.supportedOnDeviceLanguages.isNotEmpty() || support.onlineLanguages.isNotEmpty()
                                val status = VoiceModelStatus(
                                    if (!hasMetadata) VoiceModelState.CHECK_UNAVAILABLE else
                                        VoiceModelSupport.resolveState(support.installedOnDeviceLanguages,
                                            support.pendingOnDeviceLanguages, support.supportedOnDeviceLanguages),
                                    support.installedOnDeviceLanguages, support.pendingOnDeviceLanguages,
                                    support.supportedOnDeviceLanguages)
                                continuation.resume(status)
                            }
                            override fun onError(error: Int) {
                                if (continuation.isActive) continuation.resume(VoiceModelStatus(when (error) {
                                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceModelState.NOT_INSTALLED
                                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> VoiceModelState.UNSUPPORTED
                                    else -> VoiceModelState.CHECK_UNAVAILABLE
                                }))
                            }
                        })
                }
            } ?: VoiceModelStatus(VoiceModelState.CHECK_UNAVAILABLE)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            VoiceModelStatus(VoiceModelState.CHECK_UNAVAILABLE)
        } finally {
            recognizer?.let(::destroyOnMain)
        }
    }

    fun triggerDownload(): Flow<VoiceModelDownload> = callbackFlow {
        var recognizer: SpeechRecognizer? = null
        val job = launch(Dispatchers.Main.immediate) {
            val backend = selection?.backend ?: factory.availableBackends().firstOrNull()
            if (backend == null) {
                trySend(VoiceModelDownload.Failed(-1)); close(); return@launch
            }
            try {
                val rec = factory.create(backend)
                recognizer = rec
                if (Build.VERSION.SDK_INT >= 34) {
                    rec.triggerModelDownload(recognitionIntent(), factory.mainExecutor,
                        object : ModelDownloadListener {
                            override fun onProgress(completedPercent: Int) {
                                trySend(VoiceModelDownload.Progress(completedPercent.coerceIn(0, 100)))
                            }
                            override fun onScheduled() {
                                trySend(VoiceModelDownload.Scheduled); close()
                            }
                            override fun onSuccess() {
                                trySend(VoiceModelDownload.Completed); close()
                            }
                            override fun onError(error: Int) {
                                if (error == SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS) {
                                    // Some services support downloading without progress callbacks.
                                    try {
                                        rec.triggerModelDownload(recognitionIntent())
                                        trySend(VoiceModelDownload.Scheduled)
                                    } catch (_: Exception) { trySend(VoiceModelDownload.Failed(error)) }
                                } else trySend(VoiceModelDownload.Failed(error))
                                close()
                            }
                        })
                } else {
                    rec.triggerModelDownload(recognitionIntent())
                    trySend(VoiceModelDownload.Scheduled); close()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                trySend(VoiceModelDownload.Failed(SpeechRecognizer.ERROR_CLIENT)); close()
            }
        }
        awaitClose { job.cancel(); recognizer?.let(::destroyOnMain) }
    }

    internal fun reportRecognitionError(error: Int) {
        val state = when (error) {
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceModelState.NOT_INSTALLED
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> VoiceModelState.UNSUPPORTED
            else -> return
        }
        selection = selection?.let { it.copy(status = it.status.copy(state = state)) }
        approvedTrial = null
    }

    internal fun releaseAfterListening(recognizer: SpeechRecognizer) {
        factory.mainExecutor.execute {
            runCatching { recognizer.stopListening() }
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
    }

    private fun destroyOnMain(recognizer: SpeechRecognizer) {
        factory.mainExecutor.execute { runCatching { recognizer.destroy() } }
    }

    private fun priority(state: VoiceModelState) = when (state) {
        VoiceModelState.READY -> 0
        VoiceModelState.DOWNLOADING -> 1
        VoiceModelState.NOT_INSTALLED -> 2
        VoiceModelState.CHECK_UNAVAILABLE -> 3
        VoiceModelState.UNSUPPORTED -> 4
        VoiceModelState.RECOGNIZER_UNAVAILABLE -> 5
    }

    private companion object { const val CHECK_TIMEOUT_MS = 5_000L }
}
