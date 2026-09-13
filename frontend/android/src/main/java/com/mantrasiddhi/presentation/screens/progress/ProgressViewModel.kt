package com.mantrasiddhi.presentation.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.*
import com.mantrasiddhi.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.mantrasiddhi.presentation.state.profileStateWhileSubscribed

sealed interface ProgressUiState {
    data object Loading : ProgressUiState
    data class Success(
        val overviewStats: RecitationOverviewStats = RecitationOverviewStats(0, 0, 0.0, 0, 0.0),
        val dailyCounts: List<DailyCount> = emptyList(),
        val accuracyTrend: List<DailyAccuracy> = emptyList(),
        val sessions: List<RecitationSession> = emptyList(),
        val axisTrends: List<DailyAxisScores> = emptyList(),
        val hasMore: Boolean = false, val appending: Boolean = false,
        val refreshing: Boolean = false, val error: Boolean = false
    ) : ProgressUiState
    data class Error(val message: String) : ProgressUiState
}

/** Only the selected tab owns upstream reads. Cached sections are scoped to profile + account. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    currentUser: GetCurrentUserUseCase,
    private val stats: GetRecitationStatsUseCase,
    private val sessions: RecitationRepository,
    private val cloud: ProgressSyncRepository,
    private val japa: GetJapaSummaryUseCase,
    private val calendarTime: com.mantrasiddhi.domain.util.CalendarTime = com.mantrasiddhi.domain.util.CalendarTime()
) : ViewModel() {
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()
    private val retryRequest = MutableStateFlow(0)
    private val appendRequest = MutableStateFlow(0)
    private val cache = mutableMapOf<Int, ProgressUiState.Success>()
    fun selectTab(tab: Int) { require(tab in 0..3); _selectedTab.value = tab }
    fun retry() { retryRequest.value++ }
    fun loadMore() {
        val current = uiState.value as? ProgressUiState.Success ?: return
        if (_selectedTab.value == 3 && current.hasMore && !current.appending) appendRequest.value++
    }

    val uiState: StateFlow<ProgressUiState> = currentUser().distinctUntilChangedBy { it?.let { profile -> profile.id to profile.firebaseUid } }
        .profileStateWhileSubscribed(viewModelScope, ProgressUiState.Loading,
            reset = { cache.clear(); ProgressUiState.Loading }) { user ->
            combine(_selectedTab, retryRequest) { tab, _ -> tab }.flatMapLatest { tab ->
                flow<ProgressUiState> {
                    emit(cache[tab]?.copy(refreshing = true, error = false) ?: ProgressUiState.Loading)
                    emitAll(section(user.id, tab).onEach { cache[tab] = it })
                }.catch { error ->
                    if (error is CancellationException) throw error
                    emit(cache[tab]?.copy(refreshing = false, appending = false, error = true)
                        ?: ProgressUiState.Error("Unable to load this section."))
                }
            }
    }

    private fun section(userId: Long, tab: Int): Flow<ProgressUiState.Success> = when (tab) {
        0 -> {
            val overview = combine(cloud.summaryFlow().distinctUntilChanged(), calendarTime.days()) { summary, calendar -> summary to calendar.first }.flatMapLatest { (summary, today) ->
                if (summary == null) stats(userId) else flowOf(RecitationOverviewStats(
                    summary.totalSessions.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    summary.correctSessions.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    summary.averageAccuracy, summary.currentStreak(today.toString()), summary.bestScore))
            }
            combine(overview, japa(userId)) { value, count ->
                ProgressUiState.Success(overviewStats = value.copy(performedJapa = count.lifetime.toString()))
            }
        }
        1 -> combine(japa(userId), calendarTime.days()) { count, calendar ->
            ProgressUiState.Success(dailyCounts = dates(calendar.first).map { day ->
                val amount = count.on(day)
                DailyCount(label(day), millis(day), amount.min(java.math.BigInteger.valueOf(Int.MAX_VALUE.toLong())).toInt(), amount.toString())
            })
        }
        2 -> calendarTime.days().flatMapLatest { calendar ->
            val dates = dates(calendar.first)
            // Accuracy and all three axes share this one bounded aggregate query.
            combine(sessions.observeScoreDays(userId, millis(dates.first()), millis(dates.last().plusDays(1))),
                cloud.summaryFlow()) { days, summary ->
                val byDate = days.associateBy { it.dateKey }
                ProgressUiState.Success(accuracyTrend = dates.map { date ->
                    val remote = summary?.daily?.get(date.toString())
                    val average = if (summary != null) remote?.let { if (it.n > 0) it.s / it.n else 0.0 } ?: 0.0
                        else byDate[date.toString()]?.average ?: 0.0
                    DailyAccuracy(label(date), millis(date), average)
                }, axisTrends = dates.map { date ->
                    val local = byDate[date.toString()]
                    DailyAxisScores(label(date), millis(date), local?.correctness ?: 0.0,
                        local?.completeness ?: 0.0, local?.pronunciation ?: 0.0)
                })
            }
        }
        else -> history(userId)
    }

    private fun history(userId: Long): Flow<ProgressUiState.Success> = channelFlow {
        sessions.observeHistoryPage(userId, Long.MAX_VALUE, Long.MAX_VALUE, PAGE_SIZE + 1).collectLatest { first ->
            val head = first.take(PAGE_SIZE).map { it.toSession(userId) }
            val saved = cache[3]
            var current = if (saved != null && saved.sessions.take(PAGE_SIZE) == head)
                saved.copy(refreshing = false, appending = false, error = false)
                else ProgressUiState.Success(sessions = head, hasMore = first.size > PAGE_SIZE)
            send(current)
            appendRequest.drop(1).collect {
                if (current.hasMore) {
                    current = current.copy(appending = true, error = false)
                    send(current)
                    try {
                        val last = current.sessions.last()
                        val page = sessions.observeHistoryPage(userId, last.createdAt, last.id, PAGE_SIZE + 1).first()
                        current = current.copy(sessions = (current.sessions + page.take(PAGE_SIZE).map { it.toSession(userId) }).distinctBy { it.id },
                            hasMore = page.size > PAGE_SIZE, appending = false)
                    } catch (error: CancellationException) { throw error
                    } catch (_: Exception) { current = current.copy(appending = false, error = true) }
                    send(current)
                }
            }
        }
    }

    private fun SessionHistoryRow.toSession(userId: Long) = RecitationSession(id, userId, "",
        RecitationMode.valueOf(mode), overallScore, emptyMap(), "", isCorrect, durationMs,
        createdAt = createdAt, stableId = "history:$id")
    private fun dates(today: LocalDate) = (6L downTo 0L).map { today.minusDays(it) }
    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun label(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("EEE"))
    companion object { const val PAGE_SIZE = 30 }
}
