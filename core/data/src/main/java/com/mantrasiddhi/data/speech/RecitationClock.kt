package com.mantrasiddhi.data.speech

import javax.inject.Inject

/** Elapsed time must not jump when the user or network adjusts the wall clock. */
class RecitationClock @Inject constructor() {
    fun nowMs(): Long = System.nanoTime() / 1_000_000L
}
