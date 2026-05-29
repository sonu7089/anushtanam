/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.mantrasiddhi.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles playbacks of sound effects and device haptic vibrations.
 */
@Singleton
class SoundEffectsPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Plays the soft bell sound effect for correct recitation and triggers a medium haptic vibration.
     */
    fun playCorrectSound() {
        playSound(R.raw.bell_correct)
        vibrate(100L, 120) // Subtle/medium vibration
    }

    /**
     * Plays the achievement sound effect for target completion and triggers a strong haptic vibration.
     */
    fun playAchievementSound() {
        playSound(R.raw.achievement)
        vibrate(400L, 255) // Strong vibration
    }

    /**
     * Triggers a light haptic vibration for button taps.
     */
    fun triggerLightHaptic() {
        vibrate(30L, 80) // Light vibration
    }

    private fun playSound(resId: Int) {
        try {
            MediaPlayer.create(context, resId)?.apply {
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) {
            // Safe fallback for tests or headless environments
        }
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        try {
            vibrator?.let {
                if (it.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createOneShot(
                            durationMs,
                            amplitude.coerceIn(1, 255)
                        )
                        it.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(durationMs)
                    }
                }
            }
        } catch (e: Exception) {
            // Fail silently if permission is missing or on emulator
        }
    }
}
