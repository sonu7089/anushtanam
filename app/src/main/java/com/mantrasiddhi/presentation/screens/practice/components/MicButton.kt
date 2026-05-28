/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun MicButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha2"
    )

    Box(
        modifier = modifier.size(130.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording && !isProcessing) {
            // Pulsing rings
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(pulseScale1)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha1), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(pulseScale2)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha2), CircleShape)
            )
        }

        // Inner Mic Button
        val buttonGradient = Brush.verticalGradient(
            if (isRecording) {
                listOf(Color(0xFFE53935), Color(0xFFC62828)) // Recording red
            } else {
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
            }
        )

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(buttonGradient)
                .clickable(enabled = !isProcessing, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            } else {
                MicIcon(
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun MicIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Mic body (rounded rectangle in the center)
        val bodyWidth = w * 0.35f
        val bodyHeight = h * 0.5f
        val bodyLeft = (w - bodyWidth) / 2
        val bodyTop = h * 0.15f
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyWidth / 2, bodyWidth / 2)
        )

        // 2. Mic stand (U-shape arc/line around bottom of body)
        val standRadius = bodyWidth * 0.8f
        val strokeWidth = w * 0.08f
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset((w - standRadius * 2) / 2, bodyTop + bodyHeight / 2 - standRadius * 0.2f),
            size = androidx.compose.ui.geometry.Size(standRadius * 2, standRadius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // 3. Vertical stand pole
        val poleTop = bodyTop + bodyHeight / 2 + standRadius * 0.8f
        val poleLength = h * 0.15f
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w / 2, poleTop),
            end = androidx.compose.ui.geometry.Offset(w / 2, poleTop + poleLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // 4. Base horizontal line
        val baseWidth = w * 0.4f
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset((w - baseWidth) / 2, poleTop + poleLength),
            end = androidx.compose.ui.geometry.Offset((w + baseWidth) / 2, poleTop + poleLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
