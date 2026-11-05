package com.callbook.journal.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A large cap down at least this much today counts as a deep dip. */
const val DEEP_DIP_PERCENT = -1.0

enum class WeekShape(val label: String) {
    STILL_FALLING("Still falling"),
    SLOWING("Fall is slowing"),
    STEADY("Not a week-long slide"),
    RISING("Rising week"),
    UNKNOWN("Week chart missing"),
}

enum class Danger(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low"),
}

enum class DipSuggestion(val label: String) {
    BUY("Buy the swing"),
    WAIT("Wait"),
    SKIP("Skip"),
}

data class DipCheck(
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val week: WeekShape,
    val newsNote: String,
    val marketPercent: Double,
    val danger: Danger,
    val suggestion: DipSuggestion,
    val reason: String,
    val limitPrice: Double = 0.0,
    val limitNote: String = "",
    val previousClose: Double = 0.0,
    val stopPrice: Double = 0.0,
    val targetPrice: Double = 0.0,
    val headlines: List<StockHeadline> = emptyList(),
    /** How many points of today's fall the headline words can account for. */
    val newsCovers: Double = 0.0,
    /** Checklist score from the week, the headlines, the market, and the 30-day move. Not a forecast. */
    val dangerPercent: Int = 0,
    val weekChange: Double? = null,
    val monthChange: Double? = null,
    val dayLow: Double = 0.0,
)

enum class CapSize(val label: String, val index: String) {
    LARGE("Large", "NIFTY 100"),
    MID("Mid", "NIFTY MIDCAP 150"),
    SMALL("Small", "NIFTY SMALLCAP 250"),
}

/** Where a name sits, and whether the latest check would buy, wait, or skip. */
data class ListedRead(
    val cap: CapSize,
    val suggestion: DipSuggestion,
    val dayLow: Double,
)

data class CapDips(
    val marketPercent: Double,
    val dips: List<DipCheck>,
    val pauses: List<DipCheck> = emptyList(),
    val dipsLoaded: Boolean = false,
    val pausesLoaded: Boolean = false,
)

sealed interface DipLoad {
    data object Idle : DipLoad
    data object Loading : DipLoad
    data class Ready(
        val asOf: String,
        val boards: Map<CapSize, CapDips>,
    ) : DipLoad
    data class Unavailable(val message: String) : DipLoad
}

/** Morning alerts. Ends at 10:00 so the first hour is not spent on the other lists. */
fun inDipWindow(time: LocalTime): Boolean =
    !time.isBefore(LocalTime.of(9, 15)) && time.isBefore(LocalTime.of(10, 0))

/** Cash market is open. The strategy list can refresh through this whole session. */
fun inMarketHours(time: LocalTime): Boolean =
    !time.isBefore(LocalTime.of(9, 15)) && time.isBefore(LocalTime.of(15, 30))

/** Buy alerts are urgent from the open until 3:15. */
fun inAlertHours(time: LocalTime): Boolean =
    !time.isBefore(LocalTime.of(9, 15)) && !time.isAfter(LocalTime.of(15, 15))

/** A buy alert waits this long after the last new low. */
const val FALL_QUIET_MINUTES = 3L

data class FallWatch(val low: Double, val quietSinceMillis: Long)

/**
 * A buy alert waits until today's low has stopped falling.
 * The first look only starts the wait. A cheaper low starts it again.
 */
fun fallHasPaused(
    dayLow: Double,
    price: Double,
    previous: FallWatch?,
    nowMillis: Long,
    pauseMillis: Long = FALL_QUIET_MINUTES * 60_000L,
): Pair<FallWatch, Boolean> {
    val low = when {
        dayLow > 0.0 -> dayLow
        price > 0.0 -> price
        else -> previous?.low ?: 0.0
    }
    if (low <= 0.0) return FallWatch(0.0, nowMillis) to false
    val madeNewLow = previous == null || previous.low <= 0.0 || low < previous.low - 0.009
    val watch = if (madeNewLow) FallWatch(low, nowMillis) else previous.copy(low = minOf(previous.low, low))
    val ready = !madeNewLow && nowMillis - watch.quietSinceMillis >= pauseMillis
    return watch to ready
}

/** Next large-dip check. During the session that is one minute ahead. Otherwise the next 9:15. */
fun nextAlertAt(now: LocalDateTime): LocalDateTime {
    val day = now.toLocalDate()
    val time = now.toLocalTime()
    if (isMarketWeekday(day.dayOfWeek) && time.isBefore(LocalTime.of(9, 15))) {
        return day.atTime(9, 15)
    }
    if (isMarketWeekday(day.dayOfWeek) && time.isBefore(LocalTime.of(15, 15)) && inAlertHours(time)) {
        return now.plusMinutes(1)
    }
    var cursor = day.plusDays(1)
    repeat(7) {
        if (isMarketWeekday(cursor.dayOfWeek)) return cursor.atTime(9, 15)
        cursor = cursor.plusDays(1)
    }
    return cursor.atTime(9, 15)
}

/** A strong negative headline can account for about a 3% fall. One negative word, about 1%. */
fun newsCoversLoss(headlines: List<HeadlineRead>): Double {
    val worst = headlines.filter { it.tone == HeadlineTone.NEGATIVE }.maxByOrNull { it.percent } ?: return 0.0
    return if (worst.percent >= 90) 3.0 else 1.0
}

/**
 * Highest check wins. A strong headline or a week that is still falling sits near 80.
 * This is not the chance the price falls.
 */
fun dangerPercent(
    week: WeekShape,
    intense: Boolean,
    mild: Boolean,
    unexplained: Double,
    marketDrag: Boolean,
    monthChange: Double,
): Int {
    val fromWeek = when (week) {
        WeekShape.STILL_FALLING -> 78
        WeekShape.UNKNOWN -> 50
        WeekShape.STEADY -> 30
        WeekShape.SLOWING -> 24
        WeekShape.RISING -> 18
    }
    val fromNews = when {
        intense -> 86
        mild && unexplained < 1.0 -> 58
        mild -> 32
        else -> 15
    }
    val fromMarket = if (marketDrag) 48 else 0
    val fromMonth = if (monthChange <= -8.0) 62 else 0
    return maxOf(fromWeek, fromNews, fromMarket, fromMonth).coerceAtMost(90)
}

/** Percent move from the first close to the last. */
fun spanChange(closes: List<Double>): Double? {
    if (closes.size < 2 || closes.first() <= 0.0) return null
    return (closes.last() - closes.first()) / closes.first() * 100.0
}

fun headlineDanger(read: HeadlineRead): String = when {
    read.tone == HeadlineTone.NEGATIVE && read.percent >= 90 -> "Makes danger high"
    read.tone == HeadlineTone.NEGATIVE -> "Makes danger medium"
    else -> "Keeps danger low"
}

/**
 * Compares today's fall with what the headline words can explain.
 * A fall larger than the news is the bounce case. This is not a price forecast.
 */
fun newsWorthNote(changePercent: Double, headlines: List<HeadlineRead>): String {
    val fall = signedPercent(changePercent)
    val covers = newsCoversLoss(headlines)
    if (headlines.isEmpty()) {
        return "No headline covers this $fall fall. It can rise over the next few days."
    }
    val intense = headlines.any { it.tone == HeadlineTone.NEGATIVE && it.percent >= 90 }
    if (intense) return "A strong headline can cover about 3% of this $fall fall. Leave it."
    if (covers <= 0.0) {
        return "No bad headline covers this $fall fall. It can rise over the next few days."
    }
    val extra = ((-changePercent) - covers).coerceAtLeast(0.0)
    if (extra < 1.0) return "News covers about 1% of this $fall fall. The drop matches the headline."
    val leftover = String.format(java.util.Locale.US, "%.1f%%", extra)
    return "News covers about 1% of this $fall fall. The other $leftover is not in the news, so it can rise over the next few days."
}

fun isMarketWeekday(day: DayOfWeek): Boolean =
    day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY

/** Saturday and Sunday fold back to Friday. */
fun tradingDay(date: LocalDate): LocalDate {
    var day = date
    while (!isMarketWeekday(day.dayOfWeek)) day = day.minusDays(1)
    return day
}

/** One price print. `atMillis` is when that low was traded. */
data class PricePrint(val atMillis: Long, val low: Double)

/**
 * The cheapest price traded at or after the plan was saved.
 * A low from earlier the same morning is left out.
 */
fun lowAfterAdded(addedAt: Long, prints: List<PricePrint>): Double? =
    prints.mapNotNull { print ->
        if (print.atMillis < addedAt || print.low <= 0.0) null else print.low
    }.minOrNull()

/** A wait is a trade-off. A skip is bad. Only a buy may purchase itself. */
fun allowsAutoBuy(suggestion: DipSuggestion?): Boolean = suggestion == DipSuggestion.BUY

/** A skip is a major trade-off, so the cap badge stays off that row. */
fun showsCapBadge(suggestion: DipSuggestion?): Boolean = suggestion != DipSuggestion.SKIP

/**
 * A plan buys itself once today's low reaches the buy price, the cash is free,
 * and the check is still a buy. A wait or a skip stays a plan.
 * Returns null when nothing about the plan changes.
 */
fun settleNotStarted(
    call: Prediction,
    today: LocalDate,
    now: LocalTime,
    dayLow: Double?,
    freeCash: Double,
    clearBuy: Boolean,
): Prediction? {
    if (!call.notStarted || call.mark != null) return null
    val day = LocalDate.ofEpochDay(call.epochDay)
    if (day > today) return null
    val reached = dayLow != null && dayLow > 0.0 && dayLow <= call.entryPrice + 0.009
    val affordable = call.capital >= 1.0 && call.capital <= freeCash + 0.5
    if (reached && affordable && clearBuy) {
        return call.copy(notStarted = false, epochDay = tradingDay(today).toEpochDay())
    }
    val next = carryIfNotStarted(call, today, now, dayLow) ?: return null
    return call.copy(epochDay = next)
}

/**
 * A plan that never traded. After the session, if the price stayed above the buy price,
 * the plan moves to the next weekday. A past plan moves to today while the market is open.
 */
fun carryIfNotStarted(
    call: Prediction,
    today: LocalDate,
    now: LocalTime,
    dayLow: Double?,
): Long? {
    if (!call.notStarted || call.mark != null) return null
    val day = LocalDate.ofEpochDay(call.epochDay)
    val afterClose = !now.isBefore(LocalTime.of(15, 30))
    if (day > today) return null
    if (day == today && !afterClose) return null
    val touched = day == today && dayLow != null && dayLow > 0.0 && dayLow <= call.entryPrice + 0.009
    if (touched) return null
    val next = if (day < today) {
        if (afterClose && tradingDay(today) == today) shiftTradingDay(today, 1) else tradingDay(today)
    } else {
        shiftTradingDay(day, 1)
    }
    return next.toEpochDay().takeIf { it != call.epochDay }
}

/** Steps across weekdays only. Monday minus one is Friday. */
fun shiftTradingDay(date: LocalDate, steps: Long): LocalDate {
    if (steps == 0L) return tradingDay(date)
    val direction = if (steps > 0) 1L else -1L
    var day = date
    var left = kotlin.math.abs(steps)
    while (left > 0) {
        day = day.plusDays(direction)
        if (isMarketWeekday(day.dayOfWeek)) left -= 1
    }
    return day
}

/** Last few daily closes, oldest first. A slide that is still stepping down is a skip. */
fun weekShape(closes: List<Double>): WeekShape {
    if (closes.size < 3) return WeekShape.UNKNOWN
    val last3 = closes.takeLast(3)
    if (last3[0] > last3[1] && last3[1] > last3[2]) return WeekShape.STILL_FALLING
    val steps = closes.zipWithNext { older, newer -> newer - older }
    val lastStep = steps.last()
    val previous = steps[steps.lastIndex - 1]
    if (lastStep < 0.0 && (previous >= 0.0 || lastStep > previous)) return WeekShape.SLOWING
    return WeekShape.STEADY
}

/**
 * Their swing rule: buy a deep large-cap dip when the week is not still sliding
 * and the latest headlines are not intensely bad. This is a checklist, not a forecast.
 */
fun adviseDip(
    symbol: String,
    price: Double,
    changePercent: Double,
    closes: List<Double>,
    headlines: List<HeadlineRead>,
    marketPercent: Double,
    dayLow: Double = 0.0,
    previousClose: Double = 0.0,
    stories: List<StockHeadline> = emptyList(),
    monthChange: Double = 0.0,
): DipCheck {
    val week = weekShape(closes)
    val intense = headlines.any { it.tone == HeadlineTone.NEGATIVE && it.percent >= 90 }
    val mild = headlines.any { it.tone == HeadlineTone.NEGATIVE }
    val covers = newsCoversLoss(headlines)
    val unexplained = ((-changePercent) - covers).coerceAtLeast(0.0)
    val newsNote = newsWorthNote(changePercent, headlines)
    val marketDrag = marketPercent <= -0.8
    val (danger, suggestion, reason) = when {
        intense -> Triple(
            Danger.HIGH,
            DipSuggestion.SKIP,
            "Bad news is out. Leave this one.",
        )
        week == WeekShape.STILL_FALLING -> Triple(
            Danger.HIGH,
            DipSuggestion.SKIP,
            "The last week is still falling. Leave this one.",
        )
        week == WeekShape.UNKNOWN -> Triple(
            Danger.MEDIUM,
            DipSuggestion.WAIT,
            "The week chart did not load. Wait.",
        )
        mild && unexplained < 1.0 -> Triple(
            Danger.MEDIUM,
            DipSuggestion.WAIT,
            "The headline already matches this fall. Wait.",
        )
        monthChange <= -8.0 -> Triple(
            Danger.MEDIUM,
            DipSuggestion.WAIT,
            "The last 30 days are down hard. Wait.",
        )
        marketDrag -> Triple(
            Danger.MEDIUM,
            DipSuggestion.WAIT,
            "Large caps are down as well. This may be the market.",
        )
        else -> Triple(
            Danger.LOW,
            DipSuggestion.BUY,
            "The fall is larger than the news, and the week is not still sliding. Swing, max ₹1 lakh.",
        )
    }
    val percent = dangerPercent(week, intense, mild, unexplained, marketDrag, monthChange)
    val limit = if (suggestion == DipSuggestion.SKIP) null else buyLimit(price, dayLow)
    val plan = limit?.let { swingPlan(it.price, dayLow, previousClose) }
    return DipCheck(
        symbol, price, changePercent, week, newsNote, marketPercent, danger, suggestion, reason,
        limitPrice = limit?.price ?: 0.0,
        limitNote = limit?.note.orEmpty(),
        previousClose = previousClose,
        stopPrice = plan?.stop ?: 0.0,
        targetPrice = plan?.target ?: 0.0,
        headlines = stories,
        newsCovers = covers,
        dangerPercent = percent,
        weekChange = spanChange(closes),
        monthChange = monthChange,
        dayLow = dayLow,
    )
}

/** A limit at today's low is the better bid once the price has lifted off that low. */
fun buyLimit(last: Double, dayLow: Double): BuyLimit {
    if (last <= 0.0) return BuyLimit(0.0, "")
    if (dayLow <= 0.0 || dayLow > last) return BuyLimit(last, "Limit at the last price.")
    val lifted = (last - dayLow) / last
    return if (lifted <= 0.0025) {
        BuyLimit(last, "Limit at the last price. It is still on today's low.")
    } else {
        BuyLimit(dayLow, "Better limit is today's low. The last price is already above it.")
    }
}

data class BuyLimit(val price: Double, val note: String)

data class SwingPlan(val entry: Double, val stop: Double, val target: Double)

/** Stop sits just under today's low. Profit aims back at yesterday's close when that pays for the risk. */
fun swingPlan(entry: Double, dayLow: Double, previousClose: Double): SwingPlan {
    val floor = if (dayLow > 0.0 && dayLow <= entry) dayLow else entry
    var stop = money(floor * 0.992)
    if (stop >= entry) stop = money(entry * 0.992)
    if (stop >= entry) stop = money(entry - 0.05)
    val risk = entry - stop
    val rewardToClose = previousClose - entry
    val target = when {
        previousClose > entry && rewardToClose >= risk -> money(previousClose)
        else -> money(entry + risk * 1.5)
    }
    return SwingPlan(entry, stop, target)
}

private fun money(value: Double) = kotlin.math.round(value * 100.0) / 100.0

/** A small dip in a name that is already up on the month. Deeper falls stay on the dip list. */
fun isMildPause(changePercent: Double, monthChange: Double): Boolean =
    changePercent <= -0.3 && changePercent > DEEP_DIP_PERCENT && monthChange >= 3.0

/** Higher low last week, the week was up, and today is down. */
fun risingPause(lows: List<Double>, closes: List<Double>): Boolean {
    if (lows.size < 4 || closes.size < 4) return false
    val priorLows = lows.dropLast(1)
    val priorCloses = closes.dropLast(1)
    val higherLow = priorLows.last() > priorLows[priorLows.lastIndex - 1]
    val weekUp = priorCloses.last() > priorCloses.first()
    val todayDown = closes.last() < priorCloses.last()
    return higherLow && weekUp && todayDown
}

fun pauseTarget(highs: List<Double>, entry: Double, stop: Double): Double {
    val priorHigh = highs.dropLast(1).maxOrNull() ?: 0.0
    val risk = (entry - stop).coerceAtLeast(0.05)
    val stretched = money(entry + risk * 1.5)
    return if (priorHigh > entry && priorHigh - entry >= risk) money(priorHigh) else stretched
}

/** Buy a pause in a name that is already rising. Same news and market gates as a dip. */
fun advisePause(
    symbol: String,
    price: Double,
    changePercent: Double,
    closes: List<Double>,
    highs: List<Double>,
    headlines: List<HeadlineRead>,
    marketPercent: Double,
    dayLow: Double = 0.0,
    previousClose: Double = 0.0,
    stories: List<StockHeadline> = emptyList(),
    monthChange: Double = 0.0,
): DipCheck {
    val intense = headlines.any { it.tone == HeadlineTone.NEGATIVE && it.percent >= 90 }
    val mild = headlines.any { it.tone == HeadlineTone.NEGATIVE }
    val covers = newsCoversLoss(headlines)
    val unexplained = ((-changePercent) - covers).coerceAtLeast(0.0)
    val newsNote = newsWorthNote(changePercent, headlines)
    val marketDrag = marketPercent <= -0.8
    val percent = dangerPercent(WeekShape.RISING, intense, mild, unexplained, marketDrag, monthChange)
    val (danger, suggestion, reason) = when {
        intense -> Triple(Danger.HIGH, DipSuggestion.SKIP, "Bad news is out. Leave this one.")
        mild && unexplained < 1.0 -> Triple(Danger.MEDIUM, DipSuggestion.WAIT, "The headline already matches this fall. Wait.")
        marketDrag -> Triple(Danger.MEDIUM, DipSuggestion.WAIT, "That cap is down as well. This may be the market.")
        else -> Triple(
            Danger.LOW,
            DipSuggestion.BUY,
            "The week is rising and today is only a pause. Swing, max ₹1 lakh.",
        )
    }
    val limit = if (suggestion == DipSuggestion.SKIP) null else buyLimit(price, dayLow)
    val plan = limit?.let { swingPlan(it.price, dayLow, previousClose) }
    val target = if (plan == null) 0.0 else pauseTarget(highs, plan.entry, plan.stop)
    return DipCheck(
        symbol, price, changePercent, WeekShape.RISING, newsNote, marketPercent, danger, suggestion, reason,
        limitPrice = limit?.price ?: 0.0,
        limitNote = limit?.note.orEmpty(),
        previousClose = previousClose,
        stopPrice = plan?.stop ?: 0.0,
        targetPrice = target,
        headlines = stories,
        newsCovers = covers,
        dangerPercent = percent,
        weekChange = spanChange(closes),
        monthChange = monthChange,
        dayLow = dayLow,
    )
}
