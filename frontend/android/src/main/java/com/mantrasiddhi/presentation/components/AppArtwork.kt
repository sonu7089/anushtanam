package com.mantrasiddhi.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal fun artworkRequest(context: android.content.Context, variant: ArtVariant, crop: Boolean) =
    ImageRequest.Builder(context).data(variant.resource)
        .size(variant.width, variant.height)
        .scale(if (crop) Scale.FILL else Scale.FIT)
        .precision(Precision.EXACT).crossfade(false).build()

/** The measured slot is in physical pixels, including bleed, padding and density. */
internal fun selectArtwork(variants: List<ArtVariant>, width: Int, height: Int, crop: Boolean): ArtVariant? {
    if (width <= 0 || height <= 0 || width == Int.MAX_VALUE || height == Int.MAX_VALUE) return null
    val source = variants.last()
    val scale = if (crop) max(width.toDouble() / source.width, height.toDouble() / source.height)
                else min(width.toDouble() / source.width, height.toDouble() / source.height)
    val neededWidth = ceil(source.width * scale).toInt()
    val neededHeight = ceil(source.height * scale).toInt()
    return variants.firstOrNull { it.width >= neededWidth && it.height >= neededHeight } ?: source
}

/** Decorative art never determines layout or blocks text. Coil uses its shared bounded cache. */
@Composable
fun AppArtwork(
    art: AppArt,
    modifier: Modifier = Modifier,
    usage: ArtUsage = ArtUsage.Image,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center
) {
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    val crop = contentScale == ContentScale.Crop
    val variant = remember(art, usage, bounds, crop) {
        selectArtwork(artworkVariants(art, usage), bounds.width, bounds.height, crop)
    }
    Box(modifier.onSizeChanged { bounds = it }) {
        if (variant != null) {
            // Use a finite tier-sized decode, independent of single-pixel layout changes.
            // The resource ID keeps thumbnails and heroes in distinct cache entries.
            val request = remember(context, variant, crop) {
                artworkRequest(context, variant, crop)
            }
            AsyncImage(request, contentDescription = null, modifier = Modifier.matchParentSize(),
                contentScale = contentScale, alignment = alignment)
        }
    }
}
