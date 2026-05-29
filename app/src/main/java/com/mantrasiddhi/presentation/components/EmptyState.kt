/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
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
 * Draws a beautiful symbolic lotus flower using Canvas paths and gradients.
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
        val cy = h * 0.7f

        // Draw central petal
        val centerPetal = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.15f, cy - h * 0.3f, cx - w * 0.15f, cy - h * 0.65f, cx, cy - h * 0.7f)
            cubicTo(cx + w * 0.15f, cy - h * 0.65f, cx + w * 0.15f, cy - h * 0.3f, cx, cy)
            close()
        }
        drawPath(
            path = centerPetal,
            brush = Brush.verticalGradient(
                colors = listOf(saffron, gold),
                startY = cy - h * 0.7f,
                endY = cy
            ),
            style = Fill
        )

        // Draw left petal
        val leftPetal = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.35f, cy - h * 0.15f, cx - w * 0.45f, cy - h * 0.45f, cx - w * 0.25f, cy - h * 0.55f)
            cubicTo(cx - w * 0.1f, cy - h * 0.45f, cx - w * 0.05f, cy - h * 0.2f, cx, cy)
            close()
        }
        drawPath(
            path = leftPetal,
            brush = Brush.verticalGradient(
                colors = listOf(saffron.copy(alpha = 0.85f), purple.copy(alpha = 0.85f)),
                startY = cy - h * 0.55f,
                endY = cy
            ),
            style = Fill
        )

        // Draw right petal
        val rightPetal = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx + w * 0.35f, cy - h * 0.15f, cx + w * 0.45f, cy - h * 0.45f, cx + w * 0.25f, cy - h * 0.55f)
            cubicTo(cx + w * 0.1f, cy - h * 0.45f, cx + w * 0.05f, cy - h * 0.2f, cx, cy)
            close()
        }
        drawPath(
            path = rightPetal,
            brush = Brush.verticalGradient(
                colors = listOf(saffron.copy(alpha = 0.85f), purple.copy(alpha = 0.85f)),
                startY = cy - h * 0.55f,
                endY = cy
            ),
            style = Fill
        )

        // Draw outer left petal
        val outerLeft = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx - w * 0.5f, cy, cx - w * 0.5f, cy - h * 0.25f, cx - w * 0.35f, cy - h * 0.35f)
            cubicTo(cx - w * 0.2f, cy - h * 0.3f, cx - w * 0.1f, cy - h * 0.1f, cx, cy)
            close()
        }
        drawPath(
            path = outerLeft,
            brush = Brush.horizontalGradient(
                colors = listOf(purple.copy(alpha = 0.7f), saffron.copy(alpha = 0.7f))
            ),
            style = Fill
        )

        // Draw outer right petal
        val outerRight = Path().apply {
            moveTo(cx, cy)
            cubicTo(cx + w * 0.5f, cy, cx + w * 0.5f, cy - h * 0.25f, cx + w * 0.35f, cy - h * 0.35f)
            cubicTo(cx + w * 0.2f, cy - h * 0.3f, cx + w * 0.1f, cy - h * 0.1f, cx, cy)
            close()
        }
        drawPath(
            path = outerRight,
            brush = Brush.horizontalGradient(
                colors = listOf(saffron.copy(alpha = 0.7f), purple.copy(alpha = 0.7f))
            ),
            style = Fill
        )
    }
}
