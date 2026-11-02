package com.callbook.journal.domain

import java.time.DayOfWeek
import java.time.LocalDate
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
)

enum class CapSize(val label: String, val index: String) {
    LARGE("Large", "NIFTY 100"),
    MID("Mid", "NIFTY MIDCAP 150"),
    SMALL("Small", "NIFTY SMALLCAP 250"),
}

data class CapDips(
    val marketPercent: Double,
    val dips: List<DipCheck>,
    val pauses: List<DipCheck> = emptyList(),
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

fun inDipWindow(time: LocalTime): Boolean =
    !time.isBefore(LocalTime.of(9, 15)) && time.isBefore(LocalTime.of(9, 45))

/** Cash market is open. The strategy list can refresh through this whole session. */
fun inMarketHours(time: LocalTime): Boolean =
    !time.isBefore(LocalTime.of(9, 15)) && time.isBefore(LocalTime.of(15, 30))

/** A strong negative headline can account for about a 3% fall. One negative word, about 1%. */
fun newsCoversLoss(headlines: List<HeadlineRead>): Double {
    val worst = headlines.filter { it.tone == HeadlineTone.NEGATIVE }.maxByOrNull { it.percent } ?: return 0.0
    return if (worst.percent >= 90) 3.0 else 1.0
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
): DipCheck {
    val intense = headlines.any { it.tone == HeadlineTone.NEGATIVE && it.percent >= 90 }
    val mild = headlines.any { it.tone == HeadlineTone.NEGATIVE }
    val covers = newsCoversLoss(headlines)
    val unexplained = ((-changePercent) - covers).coerceAtLeast(0.0)
    val newsNote = newsWorthNote(changePercent, headlines)
    val marketDrag = marketPercent <= -0.8
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
    )
}
