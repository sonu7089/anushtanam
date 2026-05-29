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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
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
 * Draws a beautiful, realistic symbolic lotus flower with a glowing flame core and aura using Canvas paths and gradients.
 */
@Composable
fun LotusIllustration(modifier: Modifier = Modifier) {
    val saffron = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.colorScheme.secondary
    val purple = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.72f

        // 1. Draw soft background radial aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(gold.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(cx, cy - h * 0.2f),
                radius = w * 0.45f
            ),
            center = Offset(cx, cy - h * 0.2f),
            radius = w * 0.45f
        )

        // 2. Draw base supportive leaves (magenta/purple seat)
        val baseSeat = Path().apply {
            moveTo(cx - w * 0.3f, cy + h * 0.05f)
            cubicTo(cx - w * 0.15f, cy + h * 0.12f, cx + w * 0.15f, cy + h * 0.12f, cx + w * 0.3f, cy + h * 0.05f)
            cubicTo(cx + w * 0.15f, cy + h * 0.02f, cx - w * 0.15f, cy + h * 0.02f, cx - w * 0.3f, cy + h * 0.05f)
            close()
        }
        drawPath(
            path = baseSeat,
            brush = Brush.horizontalGradient(
                colors = listOf(purple, purple.copy(alpha = 0.6f), purple)
            ),
            style = Fill
        )

        // 3. Draw outermost side petals
        val outerLeft = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.45f, cy, cx - w * 0.45f, cy - h * 0.25f, cx - w * 0.32f, cy - h * 0.35f)
            cubicTo(cx - w * 0.2f, cy - h * 0.3f, cx - w * 0.1f, cy - h * 0.1f, cx, cy)
            close()
        }
        drawPath(
            path = outerLeft,
            brush = Brush.verticalGradient(
                colors = listOf(purple, saffron),
                startY = cy - h * 0.35f,
                endY = cy
            ),
            style = Fill
        )

        val outerRight = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx + w * 0.45f, cy, cx + w * 0.45f, cy - h * 0.25f, cx + w * 0.32f, cy - h * 0.35f)
            cubicTo(cx + w * 0.2f, cy - h * 0.3f, cx + w * 0.1f, cy - h * 0.1f, cx, cy)
            close()
        }
        drawPath(
            path = outerRight,
            brush = Brush.verticalGradient(
                colors = listOf(purple, saffron),
                startY = cy - h * 0.35f,
                endY = cy
            ),
            style = Fill
        )

        // 4. Draw middle side petals
        val midLeft = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.32f, cy - h * 0.15f, cx - w * 0.4f, cy - h * 0.45f, cx - w * 0.22f, cy - h * 0.55f)
            cubicTo(cx - w * 0.1f, cy - h * 0.45f, cx - w * 0.05f, cy - h * 0.2f, cx, cy)
            close()
        }
        drawPath(
            path = midLeft,
            brush = Brush.verticalGradient(
                colors = listOf(saffron, gold),
                startY = cy - h * 0.55f,
                endY = cy
            ),
            style = Fill
        )

        val midRight = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx + w * 0.32f, cy - h * 0.15f, cx + w * 0.4f, cy - h * 0.45f, cx + w * 0.22f, cy - h * 0.55f)
            cubicTo(cx + w * 0.1f, cy - h * 0.45f, cx + w * 0.05f, cy - h * 0.2f, cx, cy)
            close()
        }
        drawPath(
            path = midRight,
            brush = Brush.verticalGradient(
                colors = listOf(saffron, gold),
                startY = cy - h * 0.55f,
                endY = cy
            ),
            style = Fill
        )

        // 5. Draw inner side petals
        val innerLeft = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.18f, cy - h * 0.25f, cx - w * 0.22f, cy - h * 0.55f, cx - w * 0.12f, cy - h * 0.65f)
            cubicTo(cx - w * 0.05f, cy - h * 0.5f, cx - w * 0.02f, cy - h * 0.25f, cx, cy)
            close()
        }
        drawPath(
            path = innerLeft,
            brush = Brush.verticalGradient(
                colors = listOf(gold, gold.copy(alpha = 0.8f)),
                startY = cy - h * 0.65f,
                endY = cy
            ),
            style = Fill
        )

        val innerRight = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx + w * 0.18f, cy - h * 0.25f, cx + w * 0.22f, cy - h * 0.55f, cx + w * 0.12f, cy - h * 0.65f)
            cubicTo(cx + w * 0.05f, cy - h * 0.5f, cx + w * 0.02f, cy - h * 0.25f, cx, cy)
            close()
        }
        drawPath(
            path = innerRight,
            brush = Brush.verticalGradient(
                colors = listOf(gold, gold.copy(alpha = 0.8f)),
                startY = cy - h * 0.65f,
                endY = cy
            ),
            style = Fill
        )

        // 6. Draw center petal
        val centerPetal = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.12f, cy - h * 0.3f, cx - w * 0.12f, cy - h * 0.65f, cx, cy - h * 0.72f)
            cubicTo(cx + w * 0.12f, cy - h * 0.65f, cx + w * 0.12f, cy - h * 0.3f, cx, cy)
            close()
        }
        drawPath(
            path = centerPetal,
            brush = Brush.verticalGradient(
                colors = listOf(saffron, gold),
                startY = cy - h * 0.72f,
                endY = cy
            ),
            style = Fill
        )

        // 7. Draw glowing inner flame core
        val flameCore = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.06f, cy - h * 0.25f, cx - w * 0.06f, cy - h * 0.45f, cx, cy - h * 0.52f)
            cubicTo(cx + w * 0.06f, cy - h * 0.45f, cx + w * 0.06f, cy - h * 0.25f, cx, cy)
            close()
        }
        drawPath(
            path = flameCore,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White, gold),
                startY = cy - h * 0.52f,
                endY = cy
            ),
            style = Fill
        )

        // 8. Draw top bindu (sacred dot)
        drawCircle(
            color = saffron,
            radius = w * 0.035f,
            center = Offset(cx, cy - h * 0.82f)
        )
        drawCircle(
            color = Color.White,
            radius = w * 0.018f,
            center = Offset(cx, cy - h * 0.82f)
        )
    }
}
