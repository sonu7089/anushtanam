/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.evaluation.model

/**
 * A single evaluation axis result: a normalized [score] plus the qualitative
 * [band] it maps to.
 *
 * @property score Normalized score in the range 0.0..1.0.
 * @property band Qualitative band the [score] falls into for the active configuration.
 */
data class AxisScore(
    val score: Float,
    val band: ScoreBand
)
