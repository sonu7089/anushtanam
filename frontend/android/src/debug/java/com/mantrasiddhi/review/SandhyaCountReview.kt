package com.mantrasiddhi.review

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.presentation.screens.sandhya.*
import com.mantrasiddhi.resources.R
import java.time.LocalDate
import java.time.LocalTime

@Composable
internal fun SandhyaCountReview(page: String) {
    val date = LocalDate.of(2026, 9, 8)
    val draft = SandhyaCountDraft("preview", 1, "20260908", SandhyaPeriod.SAYAM, "Asia/Kolkata",
        if (page == "sandhya-counter") SandhyaCountMode.COUNTER else SandhyaCountMode.EDIT,
        if (page == "sandhya-counter") "57" else "500", 600, 1, 1)
    var editor by remember { mutableStateOf(if (page in listOf("sandhya-entry", "sandhya-counter"))
        SandhyaCountEditor(draft, paused = page == "sandhya-counter") else null) }
    val quantities = if (page == "sandhya-empty") listOf(null, null, null) else listOf(108L, 32L, 600L)
    var observed by remember { mutableStateOf(setOf(SandhyaPeriod.PRATAH, SandhyaPeriod.MADHYAHNA)) }
    if (page == "sandhya-draft") {
        SandhyaDraftCard(draft.copy(mode = SandhyaCountMode.COUNTER, input = "57")) {
            editor = SandhyaCountEditor(draft.copy(mode = SandhyaCountMode.COUNTER, input = "57"), paused = true)
        }
        Spacer(Modifier.height(16.dp))
    }
    TodayCard(SandhyaPeriod.entries.mapIndexed { index, period -> SandhyaTodayItem(period,
        listOf(R.string.sandhya_event_sunrise, R.string.sandhya_event_noon, R.string.sandhya_event_sunset)[index],
        LocalTime.of(listOf(6, 12, 18)[index], 10), null, period in observed, quantities[index]) },
        { period -> observed = observed + period }, date,
        onRecord = { period, mode -> editor = SandhyaCountEditor(draft.copy(period = period, mode = mode,
            input = if (mode == SandhyaCountMode.EDIT) (quantities[period.ordinal] ?: 0).toString() else "0",
            baseQuantity = quantities[period.ordinal])) })
    editor?.let { state ->
        SandhyaCountSheet(state, { editor = state.copy(draft = state.draft.copy(input = it)) },
            { amount -> editor = state.copy(draft = state.draft.copy(input =
                ((state.draft.quantity ?: 0) + amount).coerceAtLeast(0).toString())) },
            { editor = state.copy(paused = !state.paused) }, { editor = null }, { editor = null }, { editor = null })
    }
}
