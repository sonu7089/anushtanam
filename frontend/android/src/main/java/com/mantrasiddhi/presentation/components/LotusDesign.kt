package com.mantrasiddhi.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.R

enum class LotusArt { LOTUS, RITUAL, EMBLEM, RIVER, DIYA, JASMINE, FAMILY, MESSAGES }

/** Decorative bitmap art: excluded from accessibility announcements. */
@Composable
fun LotusArtwork(art: LotusArt = LotusArt.LOTUS, modifier: Modifier = Modifier,
                 usage: ArtUsage = ArtUsage.Image) {
    AppArtwork(when (art) {
        LotusArt.LOTUS -> AppArt.LOTUS
        LotusArt.RITUAL -> AppArt.RITUAL
        LotusArt.EMBLEM -> AppArt.EMBLEM
        LotusArt.RIVER -> AppArt.RIVER
        LotusArt.DIYA -> AppArt.DIYA
        LotusArt.JASMINE -> AppArt.JASMINE
        LotusArt.FAMILY -> AppArt.FAMILY
        LotusArt.MESSAGES -> AppArt.MESSAGES
    }, modifier = modifier, usage = usage,
        contentScale = if (art in listOf(LotusArt.EMBLEM, LotusArt.DIYA, LotusArt.JASMINE)) ContentScale.Fit else ContentScale.Crop,
        alignment = Alignment.CenterEnd)
}

/** Extends only the artwork into the page gutters. Reading and touch bounds stay inset. */
private fun Modifier.artworkBleed() = layout { measurable, constraints ->
    val gutter = 24.dp.roundToPx()
    val expanded = constraints.copy(minWidth = constraints.maxWidth + gutter * 2,
        maxWidth = constraints.maxWidth + gutter * 2)
    val placeable = measurable.measure(expanded)
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-gutter, 0) }
}

/** A scene that fades into the actual theme background, without a photo-card border. */
@Composable
fun AmbientArtwork(art: LotusArt, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier.artworkBleed()) {
        LotusArtwork(art, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to background, 0.18f to background.copy(alpha = 0.05f),
            0.53f to Color.Transparent, 0.83f to background.copy(alpha = 0.9f), 1f to background)))
    }
}

/** The scene and heading share one canvas; the reading area ends on an opaque surface. */
@Composable
fun PageIntro(title: String, description: String, art: LotusArt = LotusArt.LOTUS,
              modifier: Modifier = Modifier, eyebrow: String? = null,
              titleAction: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxWidth()) {
      AmbientArtwork(art, Modifier.fillMaxWidth().height(248.dp))
      Column(Modifier.fillMaxWidth().padding(top = 218.dp)) {
        if (eyebrow != null) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface)
            titleAction()
        }
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
      }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

/** Keeps paired cards readable on compact screens and with larger accessibility text. */
@Composable
fun AdaptivePair(
    modifier: Modifier = Modifier,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 312.dp || LocalDensity.current.fontScale > 1.2f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Shared M3 cards, with a restrained outline and no colored elevation wash. */
@Composable
fun LotusCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    content: @Composable ColumnScope.() -> Unit
) = Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = border, content = content)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f,
        spring(dampingRatio = 0.85f, stiffness = 550f), label = "cardPress")
    Card(onClick = onClick, modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled, shape = shape, colors = colors, elevation = elevation, border = border,
        interactionSource = interactionSource, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotusTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(title = { ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() } },
        modifier = modifier, navigationIcon = navigationIcon, actions = actions,
        colors = colors, scrollBehavior = scrollBehavior)
}

@Composable
fun PreferenceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(selected = selected, onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp), label = {
            Text(text, style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }, colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer))
}
