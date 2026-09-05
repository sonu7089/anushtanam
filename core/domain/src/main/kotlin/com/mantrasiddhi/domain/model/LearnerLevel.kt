/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.model

/**
 * The adaptive difficulty tier a learner is currently practising at.
 *
 * Drives how strict the evaluation gate is (lenient for [BEGINNER], demanding for
 * [ADVANCED]) so the bar rises as the learner improves and progress feels earned
 * without ever hiding what still needs work.
 */
enum class LearnerLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}
