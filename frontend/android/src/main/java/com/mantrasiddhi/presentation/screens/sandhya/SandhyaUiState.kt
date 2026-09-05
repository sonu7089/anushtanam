/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.sandhya

import com.mantrasiddhi.domain.model.SandhyaAdherence
import com.mantrasiddhi.domain.model.SandhyaPeriod
import com.mantrasiddhi.domain.model.SandhyaRegion
import com.mantrasiddhi.domain.model.SandhyaRegions
import java.time.LocalTime

/** One of today's three junctures, as shown on the Sandhya screen. */
data class SandhyaTodayItem(
    val period: SandhyaPeriod,
    val eventLabelRes: Int,
    val eventTime: LocalTime?,     // null on a polar day
    val reminderTime: LocalTime?,  // eventTime minus the lead time
    val observedToday: Boolean
)

data class SandhyaUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val region: SandhyaRegion = SandhyaRegions.DEFAULT,
    val today: List<SandhyaTodayItem> = emptyList(),
    val adherence: SandhyaAdherence = SandhyaAdherence.EMPTY
)
