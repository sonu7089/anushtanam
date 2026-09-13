package com.mantrasiddhi.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Wall-time deadlines, restarted by resume or system clock changes. No work without a collector. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarTime @Inject constructor() {
    private val revision = MutableStateFlow(0L)
    fun invalidate() { revision.value++ }

    fun deadlines(next: (Long) -> Long?, now: () -> Long = System::currentTimeMillis): Flow<Long> =
        revision.flatMapLatest {
            flow {
                while (true) {
                    val current = now()
                    emit(current)
                    val deadline = next(current) ?: awaitCancellation()
                    require(deadline > current) { "Deadline must be in the future" }
                    delay((deadline - now()).coerceAtLeast(1))
                }
            }
        }

    fun days(zone: () -> ZoneId = ZoneId::systemDefault): Flow<Pair<LocalDate, ZoneId>> =
        deadlines(next = { now -> nextMidnight(now, zone()) }).map { now ->
            val currentZone = zone()
            Instant.ofEpochMilli(now).atZone(currentZone).toLocalDate() to currentZone
        }.distinctUntilChanged()

    companion object {
        fun nextMidnight(now: Long, zone: ZoneId): Long = Instant.ofEpochMilli(now).atZone(zone)
            .toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
