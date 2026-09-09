package com.mantrasiddhi.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Keep the reading column wide on small screens and with larger text. */
@Composable
fun IllustratedCardContent(
    artwork: @Composable (Modifier) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
        if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                artwork(Modifier.size(56.dp))
                Column(Modifier.fillMaxWidth(), content = content)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                artwork(Modifier.size(76.dp))
                Column(Modifier.weight(1f), content = content)
            }
        }
    }
}
