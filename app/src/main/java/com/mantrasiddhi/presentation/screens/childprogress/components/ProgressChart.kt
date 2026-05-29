/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.childprogress.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.presentation.components.MantraCard
import com.mantrasiddhi.presentation.theme.ChartBarShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ProgressChart(
    sessions: List<RecitationSession>,
    modifier: Modifier = Modifier
) {
    val last7Days = remember(sessions) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val days = mutableListOf<Pair<String, Int>>()
        val df = SimpleDateFormat("EEE", Locale.getDefault())

        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val startOfDay = targetCal.timeInMillis
            val endOfDay = startOfDay + 24 * 60 * 60 * 1000L
            
            val count = sessions.filter { 
                it.isCorrect && it.createdAt >= startOfDay && it.createdAt < endOfDay 
            }.size

            val dayName = df.format(targetCal.time)
            days.add(dayName to count)
        }
        days
    }

    val maxCount = remember(last7Days) {
        val max = last7Days.maxOf { it.second }
        if (max == 0) 1 else max
    }

    var animateTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(key1 = sessions) {
        animateTrigger = true
    }

    MantraCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = stringResource(id = R.string.progress_chart_recitations_per_day),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                last7Days.forEach { (dayName, count) ->
                    val targetProgress = if (animateTrigger) count.toFloat() / maxCount else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = targetProgress,
                        animationSpec = tween(durationMillis = 800),
                        label = "BarHeightAnimation"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Count Value above bar
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (count > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Animated Bar
                        val gradient = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxHeight(fraction = animatedProgress.coerceIn(0.01f, 1f))
                                .clip(ChartBarShape)
                                .background(
                                    if (count > 0) {
                                        gradient
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.Gray.copy(alpha = 0.15f),
                                                Color.Gray.copy(alpha = 0.15f)
                                            )
                                        )
                                    }
                                )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Day Label
                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
