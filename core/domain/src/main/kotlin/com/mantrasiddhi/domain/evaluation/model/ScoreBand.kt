/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.model

/**
 * Qualitative band a score falls into, shared across the three evaluation axes.
 *
 * Bands give the learner a coarse, human-readable verdict ("Excellent", "Good",
 * "Needs work", "Poor") independent of the exact numeric score.
 */
enum class ScoreBand {
    EXCELLENT,
    GOOD,
    NEEDS_WORK,
    POOR
}
