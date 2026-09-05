package com.mantrasiddhi.presentation.screens.practice.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R

/** A stable recording control: spring press feedback, an explicit stop shape, and no idle animation. */
@Composable
fun MicButton(isRecording: Boolean, isProcessing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 500f), label = "micPress")
    val corner by animateFloatAsState(if (isRecording) 28f else 44f,
        spring(dampingRatio = 0.85f, stiffness = 380f), label = "micShape")
    val label = stringResource(if (isRecording) R.string.mic_stop_japa else R.string.mic_start_japa)
    val container by animateColorAsState(
        if (isRecording) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        label = "micColor")
    val foreground = if (isRecording) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
    Column(modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick, enabled = !isProcessing, interactionSource = source,
            modifier = Modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                .semantics { contentDescription = label },
            shape = RoundedCornerShape(corner.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = container, contentColor = foreground,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
            when {
                isProcessing -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                isRecording -> Box(Modifier.size(24.dp).background(foreground, RoundedCornerShape(6.dp)))
                else -> Icon(painterResource(com.mantrasiddhi.R.drawable.ic_mic), null, Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
