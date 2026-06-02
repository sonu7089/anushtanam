/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset

/**
 * Premium empty state screen with custom styled lotus illustration.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Lotus Flower Canvas Illustration
        LotusIllustration(
            modifier = Modifier
                .size(120.dp)
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (actionButton != null) {
            Spacer(modifier = Modifier.height(24.dp))
            actionButton()
        }
    }
}

/**
 * Draws the brand lotus emblem — matching the adaptive launcher icon.
 *
 * A solid saffron disc holds two radial rings of cream/gold petals (a larger outer
 * ring and a smaller inner ring rotated by half a step) and a gold seed-pod centre.
 * All fills are flat (no gradients) so the petals read cleanly without looking
 * shadowed. Colours default to the devotional palette but can be overridden.
 *
 * @param discColor the saffron backing disc behind the bloom.
 * @param outerPetalColor colour of the outer ring of petals.
 * @param innerPetalColor colour of the inner ring of petals.
 * @param centerColor colour of the seed-pod centre.
 */
@Composable
fun LotusIllustration(
    modifier: Modifier = Modifier,
    discColor: Color = MaterialTheme.colorScheme.primary,
    outerPetalColor: Color = Color(0xFFFFE0B2),
    innerPetalColor: Color = Color(0xFFFFF3E0),
    centerColor: Color = Color(0xFFFFCA62)
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val disc = minOf(size.width, size.height) * 0.47f
        val outerLen = disc * 0.80f
        val innerLen = disc * 0.50f

        // Saffron backing disc.
        drawCircle(color = discColor, radius = disc, center = Offset(cx, cy))

        // Outer ring — 8 large petals.
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = Offset(cx, cy)) {
                drawPath(
                    path = lotusPetal(cx, cy, length = outerLen, halfWidth = outerLen * 0.34f),
                    color = outerPetalColor,
                    style = Fill
                )
            }
        }

        // Inner ring — 8 smaller petals, offset by half a step, sitting on top.
        repeat(8) { i ->
            rotate(degrees = i * 45f + 22.5f, pivot = Offset(cx, cy)) {
                drawPath(
                    path = lotusPetal(cx, cy, length = innerLen, halfWidth = innerLen * 0.42f),
                    color = innerPetalColor,
                    style = Fill
                )
            }
        }

        // Seed-pod centre with a white highlight.
        drawCircle(color = centerColor, radius = disc * 0.17f, center = Offset(cx, cy))
        drawCircle(color = Color.White, radius = disc * 0.085f, center = Offset(cx, cy))
    }
}

/**
 * Builds a single almond-shaped lotus petal pointing straight up from the centre.
 */
private fun lotusPetal(cx: Float, cy: Float, length: Float, halfWidth: Float): Path =
    Path().apply {
        moveTo(cx, cy)
        cubicTo(
            cx - halfWidth, cy - length * 0.45f,
            cx - halfWidth * 0.5f, cy - length * 0.92f,
            cx, cy - length
        )
        cubicTo(
            cx + halfWidth * 0.5f, cy - length * 0.92f,
            cx + halfWidth, cy - length * 0.45f,
            cx, cy
        )
        close()
    }
