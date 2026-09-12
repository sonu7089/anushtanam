/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Shimmer effect modifier that creates a premium loading reflection using theme colors.
 */
private val LocalShimmerPhase = staticCompositionLocalOf<State<Float>?> { null }

/** One clock for a loading group; all placeholders read it only during drawing. */
@Composable
fun ShimmerGroup(content: @Composable () -> Unit) {
    val phase = rememberShimmerPhase()
    CompositionLocalProvider(LocalShimmerPhase provides phase, content = content)
}

@Composable
private fun rememberShimmerPhase(): State<Float> {
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    var visible by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            visible = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (!visible) return remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("render.shimmer.clock.start")
        onDispose { com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("render.shimmer.clock.stop") }
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

}

@Composable
private fun Modifier.shimmerEffect(phase: State<Float>): Modifier {
    androidx.compose.runtime.SideEffect { com.mantrasiddhi.domain.diagnostics.WorkProbe.increment("render.shimmer.composition") }
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.surfaceContainerLowest,
        MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    return drawBehind {
        val end = phase.value + 10f
        drawRect(Brush.linearGradient(shimmerColors, Offset(10f, 10f), Offset(end, end)))
    }
}

/**
 * Pre-defined skeleton loader for recitation session cards.
 */
@Composable
fun SkeletonSessionItem(modifier: Modifier = Modifier) {
    val phase = LocalShimmerPhase.current ?: rememberShimmerPhase()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circle score badge placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .shimmerEffect(phase)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Text details placeholder
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .shimmerEffect(phase)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .shimmerEffect(phase)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Duration placeholder
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(14.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .shimmerEffect(phase)
        )
    }
}

/**
 * Pre-defined skeleton loader for targets or stats grids.
 */
@Composable
fun SkeletonCardItem(modifier: Modifier = Modifier) {
    val phase = LocalShimmerPhase.current ?: rememberShimmerPhase()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(18.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .shimmerEffect(phase)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .shimmerEffect(phase)
        )
    }
}
