package com.mantrasiddhi.presentation.screens.sandhya

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

/** Aligns labels by their text baseline and keeps actions at the same trailing edge when wrapping. */
@Composable
internal fun SandhyaHeaderRow(title: @Composable () -> Unit, action: @Composable () -> Unit) {
    Layout(content = { Box { title() }; Box { action() } }, modifier = Modifier.fillMaxWidth()) { children, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val label = children[0].measure(loose)
        val control = children[1].measure(loose)
        val width = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        if (label.width + gap + control.width <= width) {
            val labelBaseline = label[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: label.height / 2
            val controlBaseline = control[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: control.height / 2
            val baseline = maxOf(labelBaseline, controlBaseline)
            val labelY = baseline - labelBaseline
            val controlY = baseline - controlBaseline
            layout(width, maxOf(labelY + label.height, controlY + control.height)) {
                label.placeRelative(0, labelY)
                control.placeRelative(width - control.width, controlY)
            }
        } else {
            layout(width, label.height + control.height) {
                label.placeRelative(0, 0)
                control.placeRelative(width - control.width, label.height)
            }
        }
    }
}
