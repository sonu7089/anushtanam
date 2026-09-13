package com.mantrasiddhi.domain.diagnostics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Opt-in benchmark counters. Labels describe work, never account IDs or payloads. */
object WorkProbe {
    @Volatile var enabled = false
    private val counts = sortedMapOf<String, Long>()
    private val active = sortedMapOf<String, Long>()

    fun increment(label: String, count: Long = 1) {
        if (!enabled) return
        synchronized(this) { counts[label] = (counts[label] ?: 0) + count }
    }

    @Synchronized private fun subscription(label: String, delta: Long) {
        active[label] = (active[label] ?: 0) + delta
        if (delta > 0) counts["subscriptions.$label"] = (counts["subscriptions.$label"] ?: 0) + 1
    }

    @Synchronized fun snapshot(): Pair<Map<String, Long>, Map<String, Long>> =
        counts.toMap() to active.toMap()

    fun <T> observe(label: String, source: Flow<T>): Flow<T> = if (!enabled) source else flow {
        subscription(label, 1)
        try { emitAll(source) } finally { subscription(label, -1) }
    }
}
