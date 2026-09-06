/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * Availability of the on-device Hindi speech-recognition model that the
 * recitation evaluator depends on.
 */
enum class VoiceModelState {
    /** The required language is installed on the device and ready to use. */
    READY,

    /** The language is supported but not yet downloaded; the user can install it. */
    NOT_INSTALLED,

    /** A download/update for the language is currently in progress. */
    DOWNLOADING,

    /** The on-device recognizer exists but does not offer the required language. */
    UNSUPPORTED,

    /** A service exists, but it cannot report whether its offline pack is installed. */
    CHECK_UNAVAILABLE,

    /** No on-device recognition service is available on this device at all. */
    RECOGNIZER_UNAVAILABLE
}

/**
 * Snapshot of on-device voice-model availability for the required language.
 *
 * @property state the resolved [VoiceModelState].
 * @property installedLanguages languages currently installed on the device.
 * @property pendingLanguages languages with a download in progress.
 * @property supportedLanguages languages the recognizer can install on request.
 */
data class VoiceModelStatus(
    val state: VoiceModelState,
    val installedLanguages: List<String> = emptyList(),
    val pendingLanguages: List<String> = emptyList(),
    val supportedLanguages: List<String> = emptyList()
)

/**
 * Progress events emitted while the system downloads or updates the voice model.
 */
sealed interface VoiceModelDownload {
    /** The system has accepted and queued the download. */
    data object Scheduled : VoiceModelDownload

    /** Download progress; [percent] is `null` when the system reports no number. */
    data class Progress(val percent: Int?) : VoiceModelDownload

    /** The model finished downloading and is now installed. */
    data object Completed : VoiceModelDownload

    /** The download failed with the recognizer [errorCode]. */
    data class Failed(val errorCode: Int) : VoiceModelDownload
}

/**
 * Pure, dependency-free helpers for resolving voice-model availability so the
 * matching logic can be unit-tested without the Android framework.
 */
object VoiceModelSupport {

    /** BCP-47 tag of the language the recitation evaluator requires. */
    const val REQUIRED_LANGUAGE = "hi-IN"

    /**
     * Returns true when [language] denotes Hindi, tolerating region suffixes and
     * `_`/`-` separators (e.g. `hi`, `hi-IN`, `hi_IN`, `hi-Latn-IN`).
     */
    fun matchesRequired(language: String): Boolean {
        val normalized = language.trim().replace('_', '-').lowercase()
        if (normalized.isEmpty()) return false
        return normalized == REQUIRED_LANGUAGE.lowercase() ||
            normalized.substringBefore('-') == "hi"
    }

    /**
     * Resolves the [VoiceModelState] from the three language lists reported by
     * the platform `RecognitionSupport`. Installed wins over pending, and pending
     * over merely supported.
     */
    fun resolveState(
        installed: List<String>,
        pending: List<String>,
        supported: List<String>
    ): VoiceModelState = when {
        installed.any(::matchesRequired) -> VoiceModelState.READY
        pending.any(::matchesRequired) -> VoiceModelState.DOWNLOADING
        supported.any(::matchesRequired) -> VoiceModelState.NOT_INSTALLED
        else -> VoiceModelState.UNSUPPORTED
    }
}
