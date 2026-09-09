package com.mantrasiddhi.domain.model

import com.mantrasiddhi.domain.util.SandhyaDateKey
import java.time.LocalDate
import java.time.ZoneId

val SandhyaPeriod.minimumJapa: Long
    get() = when (this) {
        SandhyaPeriod.PRATAH -> 108
        SandhyaPeriod.MADHYAHNA -> 32
        SandhyaPeriod.SAYAM -> 64
    }

data class SandhyaMinimums(
    val pratah: Long = SandhyaPeriod.PRATAH.minimumJapa,
    val madhyahna: Long = SandhyaPeriod.MADHYAHNA.minimumJapa,
    val sayam: Long = SandhyaPeriod.SAYAM.minimumJapa
) {
    init { require(pratah >= 0 && madhyahna >= 0 && sayam >= 0) }

    operator fun get(period: SandhyaPeriod): Long = when (period) {
        SandhyaPeriod.PRATAH -> pratah
        SandhyaPeriod.MADHYAHNA -> madhyahna
        SandhyaPeriod.SAYAM -> sayam
    }
}

/** Manual Gayatri quantity, independent of ritual observance and audio assessment. */
data class SandhyaCount(
    val userId: Long,
    val dateKey: String,
    val period: SandhyaPeriod,
    val quantity: Long,
    val revision: Long,
    val zoneId: String
)

enum class SandhyaCountMode { RECORD, ADD, EDIT, COUNTER }

data class SandhyaCountDraft(
    val id: String,
    val userId: Long,
    val dateKey: String,
    val period: SandhyaPeriod,
    val zoneId: String,
    val mode: SandhyaCountMode,
    val input: String,
    val baseQuantity: Long?,
    val baseRevision: Long,
    val updatedAtMillis: Long
) {
    val quantity: Long? get() = SandhyaCountMath.parse(input)
    val newTotal: Long? get() = quantity?.let {
        runCatching { SandhyaCountMath.total(baseQuantity, it, mode) }.getOrNull()
    }
}

/** Stable operation identity also makes a retried save safe. Previous values support corrections. */
data class SandhyaCountChange(
    val id: String,
    val userId: Long,
    val dateKey: String,
    val period: SandhyaPeriod,
    val zoneId: String,
    val mode: SandhyaCountMode,
    val previousQuantity: Long?,
    val quantity: Long,
    val revision: Long,
    val createdAtMillis: Long
)

class SandhyaCountConflict : IllegalStateException("The saved count has changed")

object SandhyaCountMath {
    fun parse(input: String): Long? = input.takeIf {
        it.isNotEmpty() && it.all { char -> char in '0'..'9' }
    }?.toLongOrNull()

    fun total(previous: Long?, quantity: Long, mode: SandhyaCountMode): Long {
        require(quantity >= 0 && (previous == null || previous >= 0))
        return when (mode) {
            SandhyaCountMode.ADD, SandhyaCountMode.COUNTER -> Math.addExact(previous ?: 0, quantity)
            SandhyaCountMode.RECORD, SandhyaCountMode.EDIT -> quantity
        }
    }

    fun validateDate(dateKey: String, zoneId: String, today: LocalDate = LocalDate.now(ZoneId.of(zoneId))) {
        val date = SandhyaDateKey.parse(dateKey)
        require(SandhyaDateKey.of(date) == dateKey && !date.isAfter(today))
    }
}
