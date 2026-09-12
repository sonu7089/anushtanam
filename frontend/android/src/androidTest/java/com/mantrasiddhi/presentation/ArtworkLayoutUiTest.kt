package com.mantrasiddhi.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import coil.imageLoader
import coil.intercept.Interceptor
import coil.request.SuccessResult
import com.mantrasiddhi.presentation.components.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

class ArtworkLayoutUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var previous: ImageLoader? = null
    private var loader: ImageLoader? = null

    @After fun restoreLoader() {
        previous?.let { Coil.setImageLoader(it) }
        loader?.shutdown()
    }

    @Test fun delayedArtworkKeepsLayoutAndActionsUsable() {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        previous = context.imageLoader
        loader = ImageLoader.Builder(context).components {
            add(Interceptor { chain -> entered.complete(Unit); release.await(); chain.proceed(chain.request) })
        }.build().also { Coil.setImageLoader(it) }
        var clicks = 0
        compose.setContent {
            Column {
                AppArtwork(AppArt.BOOK, Modifier.size(76.dp).testTag("art-slot"))
                Button(onClick = { clicks++ }) { Text("Open practice") }
            }
        }
        compose.waitUntil(5_000) { entered.isCompleted }
        compose.onNodeWithTag("art-slot").assertWidthIsEqualTo(76.dp).assertHeightIsEqualTo(76.dp)
        compose.onNodeWithText("Open practice").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        release.complete(Unit)
    }

    @Test fun changingDensityReloadsTheSmallestCoveringResource() {
        val loaded = CopyOnWriteArrayList<Int>()
        previous = context.imageLoader
        loader = ImageLoader.Builder(context).eventListener(object : coil.EventListener {
            override fun onSuccess(request: coil.request.ImageRequest, result: SuccessResult) {
                loaded += request.data as Int
            }
        }).build().also { Coil.setImageLoader(it) }
        val density = mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density.floatValue)) {
                AppArtwork(AppArt.BOOK, Modifier.size(76.dp))
            }
        }
        for (scale in listOf(1, 3, 4)) {
            compose.runOnIdle { density.floatValue = scale.toFloat() }
            val expected = selectArtwork(artworkVariants(AppArt.BOOK, ArtUsage.Image), 76*scale, 76*scale, false)!!
            compose.waitUntil(5_000) { loaded.lastOrNull() == expected.resource }
        }
        assertEquals(3, loaded.distinct().size)
    }
}
