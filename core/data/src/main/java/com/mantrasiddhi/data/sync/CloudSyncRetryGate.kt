package com.mantrasiddhi.data.sync

import com.mantrasiddhi.data.local.dao.CloudSyncDao
import com.mantrasiddhi.data.local.entity.BackupQueueState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** One durable gate for capture, upload and pull, shared by foreground/manual/background work. */
@Singleton
class CloudSyncRetryGate @Inject constructor(private val dao: CloudSyncDao, private val clock: CloudSyncClock) {
    private val lock = Mutex()
    // Also bounds failures reading/writing the schedule itself.
    private val emergencyDue = mutableMapOf<String, Long>()

    suspend fun remainingMs(uid: String, manual: Boolean = false): Long = lock.withLock {
        maxOf((emergencyDue[uid]?.minus(clock.elapsedMs()) ?: 0L).coerceAtLeast(0), remaining(uid, manual))
    }

    private suspend fun remaining(uid: String, manual: Boolean): Long {
        val timing = dao.retryTiming(uid)?.split(',')?.mapNotNull { it.toLongOrNull() }
        if (timing != null && timing.size == 4) {
            val (boot, elapsedDue, wallDue, failures) = timing
            val delta = if (boot == clock.bootCount()) elapsedDue - clock.elapsedMs() else wallDue - clock.nowMs()
            val remaining = delta.coerceIn(0, MAX_DELAY_MS)
            // Rebase once after a reboot, so a future wall timestamp cannot be clamped forever.
            if (boot != clock.bootCount() || delta > MAX_DELAY_MS) persist(uid, remaining, failures.toInt().coerceIn(0, 10))
            return remaining
        }
        val now = clock.nowMs()
        val last = dao.lastAttemptAt(uid)
        val due = dao.nextAttemptAt(uid) ?: now
        if ((last != null && last > now) || due > now + MIN_DELAY_MS) {
            persist(uid, MIN_DELAY_MS, 0)
            return MIN_DELAY_MS
        }
        val sinceAttempt = last?.let { (it + MIN_DELAY_MS - now).coerceIn(0, MIN_DELAY_MS) } ?: 0L
        return maxOf(sinceAttempt, if (manual) 0 else (due - now).coerceIn(0, MIN_DELAY_MS))
    }

    suspend fun claim(uid: String, manual: Boolean): Boolean = lock.withLock {
        if ((emergencyDue[uid] ?: 0) > clock.elapsedMs()) return@withLock false
        // Set before any Room access, including failures before a durable claim.
        emergencyDue[uid] = clock.elapsedMs() + MIN_DELAY_MS
        if (remaining(uid, manual) > 0) {
            emergencyDue.remove(uid)
            return@withLock false
        }
        persist(uid, MIN_DELAY_MS, failureCount(uid))
        emergencyDue.remove(uid)
        true
    }

    suspend fun failed(uid: String) = lock.withLock {
        emergencyDue[uid] = clock.elapsedMs() + MIN_DELAY_MS
        val failures = (failureCount(uid) + 1).coerceAtMost(10)
        val ceiling = (MIN_DELAY_MS * (1L shl (failures - 1))).coerceAtMost(MAX_DELAY_MS)
        val delay = if (ceiling == MIN_DELAY_MS) ceiling else Random.nextLong(maxOf(MIN_DELAY_MS, ceiling / 2), ceiling + 1)
        emergencyDue[uid] = clock.elapsedMs() + delay
        persist(uid, delay, failures)
        emergencyDue.remove(uid)
    }

    suspend fun succeeded(uid: String) = lock.withLock {
        val timing = dao.retryTiming(uid)?.split(',') ?: return@withLock
        dao.putRetryTiming(BackupQueueState(uid, KEY, (timing.take(3) + "0").joinToString(",")))
    }

    private suspend fun failureCount(uid: String) = dao.retryTiming(uid)?.substringAfterLast(',')?.toIntOrNull()?.coerceIn(0, 10) ?: 0

    private suspend fun persist(uid: String, delay: Long, failures: Int) {
        val now = clock.nowMs()
        dao.saveRetryTiming(uid, now, now + delay,
            listOf(clock.bootCount(), clock.elapsedMs() + delay, now + delay, failures).joinToString(","))
    }

    companion object {
        const val KEY = "sync:timing"
        const val MIN_DELAY_MS = 60_000L
        const val MAX_DELAY_MS = 15 * 60_000L
    }
}
