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
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.mantrasiddhi.domain.model.VoiceModelDownload
import com.mantrasiddhi.domain.model.VoiceModelState
import com.mantrasiddhi.domain.model.VoiceModelStatus
import com.mantrasiddhi.domain.model.VoiceModelSupport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Inspects and manages the Android **on-device** speech-recognition model for the
 * required language ([VoiceModelSupport.REQUIRED_LANGUAGE]).
 *
 * The recitation features depend on this model being installed; this manager lets
 * the UI detect its state and trigger a system download/update. All
 * [SpeechRecognizer] interaction happens on the main thread, as the platform
 * requires, and every recognizer instance is destroyed once it is finished with.
 */
@Singleton
class VoiceModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Queries the platform for the current availability of the required voice model.
     *
     * Falls back to [VoiceModelState.NOT_INSTALLED] (the most actionable state, since
     * the user can still attempt a download) when the support check errors or times out.
     */
    suspend fun checkStatus(): VoiceModelStatus = withContext(Dispatchers.Main) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return@withContext VoiceModelStatus(VoiceModelState.RECOGNIZER_UNAVAILABLE)
        }
        withTimeoutOrNull(CHECK_TIMEOUT_MS) { querySupport() }
            ?: VoiceModelStatus(VoiceModelState.NOT_INSTALLED)
    }

    /** Suspends until [checkRecognitionSupport][SpeechRecognizer] reports back. */
    private suspend fun querySupport(): VoiceModelStatus =
        suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            continuation.invokeOnCancellation { destroyOnMain(recognizer) }

            recognizer.checkRecognitionSupport(
                recognitionIntent(),
                ContextCompat.getMainExecutor(context),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        destroyOnMain(recognizer)
                        if (!continuation.isActive) return
                        continuation.resume(
                            VoiceModelStatus(
                                state = VoiceModelSupport.resolveState(
                                    installed = support.installedOnDeviceLanguages,
                                    pending = support.pendingOnDeviceLanguages,
                                    supported = support.supportedOnDeviceLanguages
                                ),
                                installedLanguages = support.installedOnDeviceLanguages,
                                pendingLanguages = support.pendingOnDeviceLanguages,
                                supportedLanguages = support.supportedOnDeviceLanguages
                            )
                        )
                    }

                    override fun onError(error: Int) {
                        destroyOnMain(recognizer)
                        if (!continuation.isActive) return
                        continuation.resume(VoiceModelStatus(VoiceModelState.NOT_INSTALLED))
                    }
                }
            )
        }

    /**
     * Asks the system to download (or update to the latest) on-device model for the
     * required language and emits [VoiceModelDownload] progress events. On API 34+ the
     * stream reflects real download progress; on API 33 the request is fire-and-forget
     * and the caller should re-check [checkStatus] afterwards.
     */
    fun triggerDownload(): Flow<VoiceModelDownload> = callbackFlow {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            trySend(VoiceModelDownload.Failed(ERROR_RECOGNIZER_UNAVAILABLE))
            close()
            return@callbackFlow
        }

        val recognizer = withContext(Dispatchers.Main) {
            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val intent = recognitionIntent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                triggerWithListener(rec, intent, emit = { trySend(it) }, finish = { close() })
            } else {
                @Suppress("DEPRECATION")
                rec.triggerModelDownload(intent)
                trySend(VoiceModelDownload.Scheduled)
                close()
            }
            rec
        }

        awaitClose { destroyOnMain(recognizer) }
    }

    /** Registers an API 34+ [ModelDownloadListener] that forwards progress to [emit]. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun triggerWithListener(
        recognizer: SpeechRecognizer,
        intent: Intent,
        emit: (VoiceModelDownload) -> Unit,
        finish: () -> Unit
    ) {
        recognizer.triggerModelDownload(
            intent,
            ContextCompat.getMainExecutor(context),
            object : ModelDownloadListener {
                override fun onProgress(completedPercent: Int) {
                    emit(VoiceModelDownload.Progress(completedPercent))
                }

                override fun onScheduled() {
                    emit(VoiceModelDownload.Scheduled)
                }

                override fun onSuccess() {
                    emit(VoiceModelDownload.Completed)
                    finish()
                }

                override fun onError(error: Int) {
                    emit(VoiceModelDownload.Failed(error))
                    finish()
                }
            }
        )
    }

    /** Builds the recognition intent describing the required language. */
    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceModelSupport.REQUIRED_LANGUAGE)
        }

    /** Destroys [recognizer] on the main thread, swallowing teardown errors. */
    private fun destroyOnMain(recognizer: SpeechRecognizer) {
        ContextCompat.getMainExecutor(context).execute {
            try {
                recognizer.destroy()
            } catch (e: Exception) {
                // ignore – best-effort teardown
            }
        }
    }

    private companion object {
        /** Upper bound on how long to wait for the platform support callback. */
        const val CHECK_TIMEOUT_MS = 5_000L

        /** Sentinel error reported when no on-device recognizer exists to download into. */
        const val ERROR_RECOGNIZER_UNAVAILABLE = -1
    }
}
