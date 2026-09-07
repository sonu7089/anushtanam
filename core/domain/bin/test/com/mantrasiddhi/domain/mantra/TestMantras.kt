/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.mantra

/**
 * Mantras used across the domain test suite.
 *
 * [gayatri] is the real bundled definition rather than a fixture, so every evaluation test
 * doubles as a check that the shipped catalog still parses and still says what the engine
 * expects. [twoWord] is a deliberately tiny mantra for tests that need a *second*, unrelated
 * mantra — proof that nothing in the engine is wired to one particular text.
 */
object TestMantras {

    /** The Gayatri, exactly as shipped in `resources/mantras/gayatri.json`. */
    val gayatri: Mantra = BundledMantraCatalog.require("gayatri")

    /** A minimal two-word, one-pada mantra with no benchmark recording. */
    val twoWord: Mantra = MantraCatalogReader.readMantra(
        """
        {
          "id": "test-two-word",
          "name": "Two Word Test Mantra",
          "padaCount": 1,
          "aksharaCount": 3,
          "words": [
            { "devanagari": "ॐ", "transliteration": "om", "syllables": ["om"], "pada": 1 },
            { "devanagari": "नमः", "transliteration": "namah", "syllables": ["na", "mah"], "pada": 1 }
          ]
        }
        """.trimIndent(),
        source = "TestMantras.twoWord"
    )
}
