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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.usecase.DailyAxisScores

/**
 * Three-series line chart plotting the correctness, completeness and pronunciation
 * axes over time, so the learner sees *which* dimension is improving (§4.4).
 */
@Composable
fun AxisTrendChart(
    data: List<DailyAxisScores>,
    modifier: Modifier = Modifier
) {
    val correctnessColor = MaterialTheme.colorScheme.primary
    val completenessColor = MaterialTheme.colorScheme.tertiary
    val pronunciationColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, tween(durationMillis = 800, easing = LinearOutSlowInEasing))
    }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = gridColor, fontSize = 11.sp)

    AxisLegend(
        correctnessColor = correctnessColor,
        completenessColor = completenessColor,
        pronunciationColor = pronunciationColor
    )

    Spacer(modifier = Modifier.height(8.dp))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (data.isEmpty()) return@Canvas
            val paddingLeft = 45f
            val paddingBottom = 40f
            val paddingTop = 20f
            val paddingRight = 20f
            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            for (i in 0..4) {
                val ratio = i / 4f
                val y = paddingTop + chartHeight * (1 - ratio)
                drawLine(
                    color = gridColor.copy(alpha = 0.08f),
                    start = Offset(paddingLeft, y),
                    end = Offset(size.width - paddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )
                val label = "${(ratio * 100).toInt()}%"
                val layout = textMeasurer.measure(label, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(paddingLeft - layout.size.width - 10f, y - layout.size.height / 2),
                    style = labelStyle
                )
            }

            val xSpacing = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth

            fun pointsFor(selector: (DailyAxisScores) -> Double): List<Offset> =
                data.mapIndexed { index, item ->
                    val x = paddingLeft + index * xSpacing
                    val ratio = selector(item).coerceIn(0.0, 1.0).toFloat()
                    Offset(x, paddingTop + chartHeight * (1 - ratio * animatedProgress.value))
                }

            drawSeries(pointsFor { it.correctness }, correctnessColor)
            drawSeries(pointsFor { it.completeness }, completenessColor)
            drawSeries(pointsFor { it.pronunciation }, pronunciationColor)

            data.forEachIndexed { index, item ->
                val x = paddingLeft + index * xSpacing
                val layout = textMeasurer.measure(item.dayLabel, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = item.dayLabel,
                    topLeft = Offset(x - layout.size.width / 2, paddingTop + chartHeight + 10f),
                    style = labelStyle
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(points: List<Offset>, color: Color) {
    if (points.size > 1) {
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val controlX = (points[i].x + points[i + 1].x) / 2f
                cubicTo(controlX, points[i].y, controlX, points[i + 1].y, points[i + 1].x, points[i + 1].y)
            }
        }
        drawPath(path = path, color = color, style = Stroke(width = 3.dp.toPx()))
    }
    points.forEach { drawCircle(color = color, radius = 3.dp.toPx(), center = it) }
}

@Composable
private fun AxisLegend(
    correctnessColor: Color,
    completenessColor: Color,
    pronunciationColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendDot(correctnessColor, stringResource(R.string.progress_axis_correctness))
        LegendDot(completenessColor, stringResource(R.string.progress_axis_completeness))
        LegendDot(pronunciationColor, stringResource(R.string.progress_axis_pronunciation))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
