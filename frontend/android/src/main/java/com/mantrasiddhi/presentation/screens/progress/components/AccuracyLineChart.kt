/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.domain.usecase.DailyAccuracy

/**
 * Animated line chart with smooth curves and gradient fill using Compose Canvas.
 */
@Composable
fun AccuracyLineChart(
    data: List<DailyAccuracy>,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing)
        )
    }

    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    val labelStyle = TextStyle(
        color = onSurfaceVariant,
        fontSize = 11.sp, fontFamily = com.mantrasiddhi.presentation.theme.ReadingFont
    )
    val scoreStyle = TextStyle(
        color = accentColor,
        fontSize = 11.sp, fontFamily = com.mantrasiddhi.presentation.theme.ReadingFont
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (data.isEmpty()) return@Canvas

            val width = size.width
            val height = size.height

            val paddingLeft = 40.dp.toPx()
            val paddingBottom = 32.dp.toPx()
            val paddingTop = 28.dp.toPx()
            val paddingRight = 20.dp.toPx()

            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom

            // Draw Y Grid lines (0%, 25%, 50%, 75%, 100%)
            val yLines = 4
            for (i in 0..yLines) {
                val ratio = i.toFloat() / yLines
                val y = paddingTop + chartHeight * (1 - ratio)
                val percentage = (ratio * 100).toInt()

                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.08f),
                    start = Offset(paddingLeft, y),
                    end = Offset(width - paddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )

                val textLayout = textMeasurer.measure(
                    text = "$percentage%",
                    style = labelStyle
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "$percentage%",
                    topLeft = Offset(paddingLeft - textLayout.size.width - 10f, y - textLayout.size.height / 2),
                    style = labelStyle
                )
            }

            val pointsCount = data.size
            if (pointsCount == 0) return@Canvas

            val xSpacing = if (pointsCount > 1) chartWidth / (pointsCount - 1) else chartWidth

            val points = data.mapIndexed { index, item ->
                val x = paddingLeft + index * xSpacing
                val ratio = item.averageScore.coerceIn(0.0, 1.0).toFloat()
                val y = paddingTop + chartHeight * (1 - ratio * animatedProgress.value)
                Offset(x, y)
            }

            // Draw area gradient fill
            if (points.isNotEmpty()) {
                val fillPath = Path().apply {
                    moveTo(paddingLeft, paddingTop + chartHeight)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, paddingTop + chartHeight)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
            }

            // Draw smooth line
            if (points.size > 1) {
                val strokePath = Path().apply {
                    val first = points.first()
                    moveTo(first.x, first.y)
                    
                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        
                        // Control points for cubic bezier to make it smooth
                        val controlX = (p0.x + p1.x) / 2f
                        cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                    }
                }

                drawPath(
                    path = strokePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx())
                )
            } else if (points.size == 1) {
                // If only 1 point, draw a line across
                drawLine(
                    color = primaryColor,
                    start = Offset(paddingLeft, points[0].y),
                    end = Offset(width - paddingRight, points[0].y),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Draw points and labels
            data.forEachIndexed { index, item ->
                val p = points[index]

                // Draw circle marker
                drawCircle(
                    color = accentColor,
                    radius = 4.dp.toPx(),
                    center = p
                )
                drawCircle(
                    color = surfaceColor,
                    radius = 2.dp.toPx(),
                    center = p
                )


                // Draw score text above point if score > 0
                if (item.averageScore > 0) {
                    val percentStr = "${(item.averageScore * 100).toInt()}%"
                    val scoreLayout = textMeasurer.measure(
                        text = percentStr,
                        style = scoreStyle
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = percentStr,
                        topLeft = Offset(p.x - scoreLayout.size.width / 2, p.y - scoreLayout.size.height - 6f),
                        style = scoreStyle
                    )
                }

                // Draw X Label
                val labelLayout = textMeasurer.measure(
                    text = item.dayLabel,
                    style = labelStyle
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = item.dayLabel,
                    topLeft = Offset(p.x - labelLayout.size.width / 2, paddingTop + chartHeight + 10f),
                    style = labelStyle
                )
            }
        }
    }
}
