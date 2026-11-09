package com.callbook.journal.domain

import java.time.LocalDate
import java.time.LocalTime

enum class BuyOutcome {
    TARGET,
    STOP,
    OPEN,
}

data class BuyScore(
    val id: Long,
    val epochDay: Long,
    val symbol: String,
    val entryPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val outcome: BuyOutcome?,
)

/** The weekday after the buy was written down. */
fun nextSessionDay(epochDay: Long): LocalDate =
    shiftTradingDay(LocalDate.ofEpochDay(epochDay), 1)

/**
 * What the next session did with the stop and the target.
 * A session that is still open, and has not touched either price, stays unscored.
 * When both prices print, the stop is the result. The risk was hit.
 */
fun scoreSession(
    stop: Double,
    target: Double,
    low: Double,
    high: Double,
    sessionDay: Long,
    today: Long,
    now: LocalTime,
): BuyOutcome? {
    if (low <= 0.0 || high <= 0.0 || high + 0.009 < low) return null
    val hitStop = low <= stop + 0.009
    val hitTarget = high + 0.009 >= target
    if (hitStop) return BuyOutcome.STOP
    if (hitTarget) return BuyOutcome.TARGET
    val finished = today > sessionDay || (today == sessionDay && !now.isBefore(LocalTime.of(15, 30)))
    return if (finished) BuyOutcome.OPEN else null
}
