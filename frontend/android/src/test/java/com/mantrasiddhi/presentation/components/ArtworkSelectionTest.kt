package com.mantrasiddhi.presentation.components

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ArtworkSelectionTest {
    private val squares = listOf(128, 192, 256, 320, 448, 640, 896).map { ArtVariant(it, it, it) }

    @Test fun `card density selects the smallest covering tier`() {
        listOf(1 to 128, 3 to 256, 4 to 320).forEach { (density, expected) ->
            assertEquals(expected, selectArtwork(squares, 76*density, 76*density, false)?.width)
        }
    }

    @Test fun `unmeasured and unbounded slots do not load anything`() {
        assertNull(selectArtwork(squares, 0, 200, false))
        assertNull(selectArtwork(squares, 200, Int.MAX_VALUE, false))
        assertNull(selectArtwork(squares, -1, 200, true))
    }

    @Test fun `fit respects both bounds while crop covers the whole slot`() {
        val portrait = listOf(ArtVariant(1, 128, 192), ArtVariant(2, 256, 384), ArtVariant(3, 512, 768))
        assertEquals(1, selectArtwork(portrait, 320, 120, false)?.resource)
        assertEquals(3, selectArtwork(portrait, 320, 120, true)?.resource)
    }

    @Test fun `source limit returns largest derivative without an original fallback`() {
        assertEquals(896, selectArtwork(squares, 4000, 4000, true)?.width)
    }

    @Test fun `bleeding hero width and detail dimensions are covered`() {
        val heroes = listOf(640, 960, 1280, 1536).map { ArtVariant(it, it, it*2/3) }
        assertEquals(1280, selectArtwork(heroes, 360*3, 248*3, true)?.width)
        assertEquals(896, selectArtwork(squares, 208*4, 208*4, false)?.width)
    }
}
