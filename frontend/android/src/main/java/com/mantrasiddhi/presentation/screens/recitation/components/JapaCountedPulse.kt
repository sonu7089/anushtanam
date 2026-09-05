/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.theme.EmphasizedDecelerateEasing
import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import kotlinx.coroutines.delay
import kotlin.math.max

/** Total length of one confirmation. Long enough to register, short enough to stay out of the way. */
private const val PULSE_DURATION_MS = 820

/**
 * Where each ring starts within the overall progress. Staggering them makes one japa read
 * as a single ripple rather than three separate flashes.
 */
private val RING_STARTS = listOf(0f, 0.12f, 0.24f)

/** Opening opacity per ring; they fade to fully transparent as they expand. */
private val RING_PEAK_ALPHAS = listOf(0.55f, 0.38f, 0.24f)

/** Thinner rings trail the leading one, which keeps the ripple from looking heavy. */
private val RING_STROKES = listOf(5.dp, 3.dp, 2.dp)

/**
 * Peak opacity of the background wash. Deliberately low: the mantra text and the
 * transcript sit underneath, and a bright full-screen flash on every japa would strobe.
 */
private const val WASH_PEAK_ALPHA = 0.18f

/** The wash is gone by this point in the animation — it is a blink, not a tint. */
private const val WASH_FADE_END = 0.35f

/** Rings are drawn slightly past the box corner so they leave the screen rather than stop on it. */
private const val MAX_RADIUS_FACTOR = 1.12f

/** Frozen progress used when the platform has animations switched off. */
private const val STATIC_RING_PROGRESS = 0.55f

/** How long the static ring is held when animations are switched off. */
private const val STATIC_RING_HOLD_MS = 450L

/**
 * Silent, full-screen confirmation that one japa was heard and counted.
 *
 * Until now a counted japa was acknowledged only by the bell and the progress number
 * ticking up — useless when the phone is muted, face-down on a mat, or when the sadhak
 * is mid-mantra and cannot look away long enough to read a number. This draws concentric
 * green rings expanding from the exact centre of the screen so the confirmation is
 * readable from the corner of the eye without breaking the flow of recitation.
 *
 * Two decisions worth knowing about:
 *
 * - It is driven by [pulseId], a monotonically increasing counter, not a boolean. The
 *   recitation state is re-emitted on every partial speech result, so a flag would either
 *   re-fire continuously or be cleared before the animation could be seen. A new id means
 *   "one more japa was counted"; the same id repeated means "nothing new happened".
 * - It has NO input modifiers. It is drawn on top of the whole screen, including the mic
 *   button, and Compose only routes pointer events to nodes that declare pointer input —
 *   so a bare [Box] + [Canvas] cannot swallow the tap that stops the recitation.
 *
 * @param pulseId Id of the most recently counted japa. `0` means "none yet" and draws nothing.
 */
@Composable
fun JapaCountedPulse(
    pulseId: Long,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    val ringColor = extendedColors.success
    val washColor = extendedColors.successContainer
    val announcement = stringResource(id = R.string.recitation_japa_counted)
    val context = LocalContext.current

    // "Remove animations" (accessibility settings / developer options) zeroes the animator
    // duration scale. Read once — it is a global setting and this composable is entered on
    // every japa. A failure to read it must never cost the sadhak their confirmation, so
    // the fallback is to animate normally.
    val animationsRemoved = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    val progress = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    // Whatever id was already current when this overlay entered composition belongs to a
    // japa that happened before the sadhak was looking at this screen. Leaving and
    // returning to the Japa tab must not replay it.
    val idAtEntry = remember { pulseId }

    LaunchedEffect(pulseId) {
        // Id 0 additionally means "no japa counted yet" in this ViewModel's lifetime.
        if (pulseId == 0L || pulseId == idAtEntry) return@LaunchedEffect
        visible = true
        if (animationsRemoved) {
            progress.snapTo(STATIC_RING_PROGRESS)
            delay(STATIC_RING_HOLD_MS)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = PULSE_DURATION_MS,
                    easing = EmphasizedDecelerateEasing
                )
            )
        }
        visible = false
    }

    // At rest the overlay is not in the tree at all, so it costs nothing between japa.
    // A back-to-back japa cancels this effect before `visible` is cleared, so the ripple
    // restarts from the centre without flickering out first.
    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            // Colour alone is not a confirmation. Announce the count politely so it never
            // interrupts what TalkBack is already reading; the phrasing is terse because
            // this fires once per mantra.
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = announcement
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Reading the animation value inside the draw lambda keeps each frame in the
            // draw phase — no recomposition of the recitation screen while the ripple runs.
            val animationProgress = progress.value
            val centre = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = max(size.width, size.height) / 2f * MAX_RADIUS_FACTOR

            val washAlpha =
                WASH_PEAK_ALPHA * (1f - (animationProgress / WASH_FADE_END)).coerceIn(0f, 1f)
            if (washAlpha > 0f) {
                drawCircle(
                    color = washColor,
                    radius = maxRadius,
                    center = centre,
                    alpha = washAlpha
                )
            }

            RING_STARTS.forEachIndexed { index, start ->
                val ringProgress = ((animationProgress - start) / (1f - start)).coerceIn(0f, 1f)
                if (ringProgress <= 0f) return@forEachIndexed
                val alpha = RING_PEAK_ALPHAS[index] * (1f - ringProgress)
                if (alpha <= 0f) return@forEachIndexed
                drawCircle(
                    color = ringColor,
                    radius = maxRadius * ringProgress,
                    center = centre,
                    alpha = alpha,
                    style = Stroke(width = RING_STROKES[index].toPx())
                )
            }
        }
    }
}
