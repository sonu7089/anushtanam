/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.RecitationRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Domain model for combined dashboard statistics.
 */
data class DashboardStats(
    val user: UserProfile,
    val todayRecitationCount: Int,
    val streakDays: Int,
    val activeTarget: Target?,
    val childrenProgress: List<ChildProgress> = emptyList()
)

/**
 * Aggregates everything the home dashboard shows for one user: today's verified count, the
 * practice streak, the active target, and — for a parent — each linked child's progress.
 */
class GetDashboardStatsUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val targetRepository: TargetRepository,
    private val recitationRepository: RecitationRepository,
    private val getChildProgressUseCase: GetChildProgressUseCase
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(userId: Long): Flow<DashboardStats?> = combine(
        targetRepository.getAllForUser(userId),
        recitationRepository.getSessionsForUser(userId),
        userRepository.getChildrenOf(userId),
        ::Triple
    ).flatMapLatest { (targets, sessions, children) ->
        flow { emit(userRepository.getById(userId)) }.flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                val stats = DashboardStats(
                    user = user,
                    todayRecitationCount = countToday(sessions),
                    streakDays = streakDays(sessions),
                    activeTarget = targets.filter { it.isActive }.maxWithOrNull(
                        compareBy<Target> { it.startedAtMillis }.thenBy { it.stableId }),
                    childrenProgress = emptyList()
                )
                childrenProgress(user, children).map { stats.copy(childrenProgress = it) }
            }
        }
    }

    /**
     * Streams each linked child's progress for a parent, or a single empty list for anyone
     * else. [combine] never emits for an empty list of flows, so the no-children case is
     * short-circuited rather than left to hang.
     */
    private fun childrenProgress(
        user: UserProfile,
        children: List<UserProfile>
    ): Flow<List<ChildProgress>> {
        if (user.profileType != ProfileType.PARENT || children.isEmpty()) return flowOf(emptyList())
        return combine(children.map { getChildProgressUseCase(it.id) }) { it.filterNotNull() }
    }

    private fun countToday(sessions: List<RecitationSession>): Int {
        val today = LocalDate.now(zone)
        return sessions.count { it.isCorrect && it.createdAt.toLocalDate() == today }
    }

    /**
     * Consecutive days up to today on which at least one recitation was verified.
     *
     * Counted in calendar days rather than 24-hour spans: a day is a day whatever the clock
     * does to it, so a streak survives a daylight-saving change or a flight across
     * timezones. A streak whose most recent day is neither today nor yesterday has been
     * broken and reads as zero — today itself is not required, so the count does not
     * collapse to nothing before the sadhak has sat down.
     */
    private fun streakDays(sessions: List<RecitationSession>): Int {
        val days = sessions.filter { it.isCorrect }
            .map { it.createdAt.toLocalDate() }
            .distinct()
            .sortedDescending()
        val today = LocalDate.now(zone)
        val mostRecent = days.firstOrNull() ?: return 0
        if (mostRecent != today && mostRecent != today.minusDays(1)) return 0

        var streak = 1
        for ((previous, day) in days.zipWithNext()) {
            if (previous.minusDays(1) != day) break
            streak++
        }
        return streak
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
