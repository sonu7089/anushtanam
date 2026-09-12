package com.mantrasiddhi.presentation

import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import coil.EventListener
import coil.ImageLoader
import coil.decode.Decoder
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult
import com.mantrasiddhi.presentation.components.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ArtworkDeliveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun everyDerivativeDecodesOffMainAtItsDeclaredSizeAndReusesCache() = runBlocking {
        val decodes = AtomicInteger()
        val mainDecodes = AtomicInteger()
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener {
            override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
                decodes.incrementAndGet()
                if (Looper.myLooper() == Looper.getMainLooper()) mainDecodes.incrementAndGet()
            }
        }).build()
        try {
            val variants = AppArt.entries.flatMap { art ->
                ArtUsage.entries.flatMap { artworkVariants(art, it) }
            }.distinctBy { it.resource }
            variants.forEach { variant ->
                val request = artworkRequest(context, variant, false).newBuilder().allowHardware(false).build()
                val result = loader.execute(request)
                assertTrue("${context.resources.getResourceEntryName(variant.resource)}: $result", result is SuccessResult)
                val bitmap = (result.drawable as BitmapDrawable).bitmap
                assertEquals(variant.width, bitmap.width)
                assertEquals(variant.height, bitmap.height)
                val cached = loader.execute(request) as SuccessResult
                assertEquals(DataSource.MEMORY_CACHE, cached.dataSource)
            }
            assertEquals(variants.size, decodes.get())
            assertEquals(0, mainDecodes.get())
        } finally { loader.shutdown() }
    }

    @Test fun placementMatrixUsesFiniteRequestsAndSeparatesThumbsFromHeroes() {
        val slots = listOf(20 to 20, 48 to 48, 56 to 56, 72 to 72, 76 to 76,
            96 to 96, 104 to 104, 140 to 140, 160 to 160, 208 to 208,
            88 to 120, 320 to 124, 360 to 248, 600 to 260)
        for (density in listOf(1, 3, 4)) for (art in AppArt.entries) {
            for ((w, h) in slots) for (crop in listOf(false, true)) {
                val selected = selectArtwork(artworkVariants(art, ArtUsage.Image), w*density, h*density, crop)!!
                assertTrue(selected.width > 0 && selected.height > 0)
                assertTrue(context.resources.getResourceEntryName(selected.resource).startsWith("art_"))
            }
        }
        for (art in listOf(AppArt.LOTUS, AppArt.FAMILY, AppArt.MESSAGES, AppArt.RITUAL, AppArt.RIVER)) {
            val thumbs = artworkVariants(art, ArtUsage.Thumbnail)
            assertTrue(thumbs.all { it.width == it.height && it.width <= 320 })
            assertTrue(thumbs.map { it.resource }.intersect(artworkVariants(art, ArtUsage.Image).map { it.resource }.toSet()).isEmpty())
        }
    }
}
