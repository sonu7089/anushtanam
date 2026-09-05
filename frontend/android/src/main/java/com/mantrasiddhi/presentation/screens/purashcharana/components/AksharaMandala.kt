/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.purashcharana.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.presentation.components.LotusArtwork
import com.mantrasiddhi.presentation.components.LotusArt
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * The emblem of the Purashcharana: one mark per akshara of the mantra, arranged in a ring
 * around a lamp flame.
 *
 * The ring says what the vow is made of — 24 syllables, each carrying one lakh japa — and
 * fills clockwise as the sankalpa advances, so a glance at it answers "how far along am I"
 * without a single digit. Marks are lit by *proportion*, and any verified japa at all lights
 * the first mark: at this scale a strictly rounded fill would sit empty for the best part of
 * a year, which would be both discouraging and untrue.
 *
 * Everything is flat fills in the devotional palette — no gradients, no bitmaps — and the
 * whole thing is a single Canvas pass, cheap enough to sit inside a Home card.
 *
 * @param aksharaCount how many marks the ring carries; one per syllable under sankalpa.
 * @param progress fraction of the sankalpa completed, `0f..1f`.
 * @param litColor fill for aksharas already covered by verified japa.
 * @param unlitColor outline for aksharas still ahead.
 * @param discColor the solid disc the flame sits on.
 * @param flameColor the lamp flame at the centre.
 */
@Composable
fun AksharaMandala(
    modifier: Modifier = Modifier,
    aksharaCount: Int = GayatriMantra.AKSHARA_COUNT,
    progress: Float = 0f,
    litColor: Color = MaterialTheme.colorScheme.primary,
    unlitColor: Color = MaterialTheme.colorScheme.outlineVariant,
    discColor: Color = MaterialTheme.colorScheme.primaryContainer,
    flameColor: Color = MaterialTheme.colorScheme.secondary
) {
    val marks = aksharaCount.coerceAtLeast(1)
    val safeProgress = progress.coerceIn(0f, 1f)
    val litMarks = if (safeProgress <= 0f) {
        0
    } else {
        ceil(safeProgress * marks).toInt().coerceIn(1, marks)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        if (radius <= 0f) return@Canvas

        val ringRadius = radius * 0.80f
        val markRadius = radius * 0.085f
        val hairline = (radius * 0.022f).coerceAtLeast(1f)

        // Faint guide ring: the path the marks sit on, so an empty mandala still reads as
        // a circle waiting to be filled rather than as scattered dots.
        drawCircle(
            color = unlitColor,
            radius = ringRadius,
            center = centre,
            style = Stroke(width = hairline)
        )

        // Marks run clockwise from the top, the direction a mala is told.
        repeat(marks) { index ->
            val angleRad = Math.toRadians((-90f + index * 360f / marks).toDouble())
            val markCentre = Offset(
                x = centre.x + ringRadius * cos(angleRad).toFloat(),
                y = centre.y + ringRadius * sin(angleRad).toFloat()
            )
            if (index < litMarks) {
                drawCircle(color = litColor, radius = markRadius, center = markCentre)
            } else {
                drawCircle(
                    color = unlitColor,
                    radius = markRadius * 0.82f,
                    center = markCentre,
                    style = Stroke(width = hairline)
                )
            }
        }

    }
    LotusArtwork(LotusArt.EMBLEM, Modifier.fillMaxSize(0.64f))
    }
}
