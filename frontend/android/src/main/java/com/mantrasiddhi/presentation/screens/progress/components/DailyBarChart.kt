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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.domain.usecase.DailyCount

/**
 * Animated bar chart using Compose Canvas.
 */
@Composable
fun DailyBarChart(
    data: List<DailyCount>,
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
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val labelStyle = TextStyle(
        color = onSurfaceVariant,
        fontSize = 11.sp, fontFamily = com.mantrasiddhi.presentation.theme.ReadingFont
    )
    val countStyle = TextStyle(
        color = primaryColor,
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

            val paddingLeft = 36.dp.toPx()
            val paddingBottom = 32.dp.toPx()
            val paddingTop = 28.dp.toPx()
            val paddingRight = 20.dp.toPx()

            val chartWidth = width - paddingLeft - paddingRight
            val chartHeight = height - paddingTop - paddingBottom

            val maxVal = maxOf(5, data.maxOf { it.count })

            // Draw Y Grid lines & Labels
            val yLines = 4
            for (i in 0..yLines) {
                val ratio = i.toFloat() / yLines
                val y = paddingTop + chartHeight * (1 - ratio)
                val gridVal = (maxVal * ratio).toInt()

                // Draw grid line
                drawLine(
                    color = onSurfaceVariant.copy(alpha = 0.1f),
                    start = Offset(paddingLeft, y),
                    end = Offset(width - paddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )

                // Draw Y label
                val textLayout = textMeasurer.measure(
                    text = gridVal.toString(),
                    style = labelStyle
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = gridVal.toString(),
                    topLeft = Offset(paddingLeft - textLayout.size.width - 10f, y - textLayout.size.height / 2),
                    style = labelStyle
                )
            }

            // Draw Bars
            val barCount = data.size
            val barSpacing = chartWidth / (barCount * 1.5f)
            val barWidth = (chartWidth - (barSpacing * (barCount - 1))) / barCount

            data.forEachIndexed { index, item ->
                val ratio = item.count.toFloat() / maxVal
                val animRatio = ratio * animatedProgress.value
                val barHeight = chartHeight * animRatio

                val x = paddingLeft + index * (barWidth + barSpacing)
                val y = paddingTop + chartHeight - barHeight

                // Quiet, solid bars; the most recent day uses the lotus accent.
                if (barHeight > 0) {
                    drawRoundRect(
                        color = if (index == data.lastIndex) primaryColor else secondaryColor.copy(alpha = 0.72f),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Draw count above bar
                if (item.count > 0) {
                    val countLayout = textMeasurer.measure(
                        text = item.count.toString(),
                        style = countStyle
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = item.count.toString(),
                        topLeft = Offset(x + (barWidth - countLayout.size.width) / 2, y - countLayout.size.height - 4f),
                        style = countStyle
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
                    topLeft = Offset(x + (barWidth - labelLayout.size.width) / 2, paddingTop + chartHeight + 10f),
                    style = labelStyle
                )
            }
        }
    }
}
