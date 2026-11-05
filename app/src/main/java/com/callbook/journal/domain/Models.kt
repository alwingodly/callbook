package com.callbook.journal.domain

enum class Direction {
    GAIN,
    LOSS,
}

enum class Segment {
    DELIVERY,
    INTRADAY,
}

/** A result the user picked when they did not type a Groww price. */
enum class Mark {
    CORRECT,
    OPPOSITE,
    HIT_TARGET,
    HIT_STOP,
    BEYOND,
    WITHDRAW_PROFIT,
    WITHDRAW_LOSS,
}

fun Mark.tookProfit(): Boolean = when (this) {
    Mark.WITHDRAW_PROFIT, Mark.CORRECT, Mark.HIT_TARGET, Mark.BEYOND -> true
    Mark.WITHDRAW_LOSS, Mark.OPPOSITE, Mark.HIT_STOP -> false
}

enum class TradeSide {
    LONG,
    SHORT,
    STAY_OUT,
}

enum class ResultKind {
    OPEN,
    SUCCESS,
    FAILED,
    SCRATCH,
}

data class Prediction(
    val id: Long = 0,
    val epochDay: Long,
    val symbol: String,
    val direction: Direction,
    val segment: Segment,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopPrice: Double,
    val capital: Double,
    val actualPrice: Double?,
    val note: String,
    val mark: Mark?,
    val createdAt: Long,
    val manualPnl: Double? = null,
    val notStarted: Boolean = false,
)

/** Buy a gain call. Short a loss call only when it is intraday. Stay out of a delivery loss call. */
fun Prediction.followSide(): TradeSide = when {
    direction == Direction.GAIN -> TradeSide.LONG
    segment == Segment.INTRADAY -> TradeSide.SHORT
    else -> TradeSide.STAY_OUT
}

fun opposite(direction: Direction): Direction = when (direction) {
    Direction.GAIN -> Direction.LOSS
    Direction.LOSS -> Direction.GAIN
}

/** The sell price that locks a profit on a buy. */
fun Prediction.profitPoint(): Double = maxOf(targetPrice, stopPrice)

/** The sell price that locks a loss on a buy. */
fun Prediction.lossPoint(): Double = minOf(targetPrice, stopPrice)

/**
 * Exit used for rupees. A Groww price is used when it agrees with the withdraw.
 * Otherwise a profit withdraw uses the profit point, and a loss withdraw uses the loss point.
 */
fun Prediction.moneyExit(): Double? {
    val chosen = mark
    if (chosen == null) return actualPrice
    val point = if (chosen.tookProfit()) profitPoint() else lossPoint()
    val price = actualPrice ?: return point
    val rose = price >= entryPrice
    return if (rose == chosen.tookProfit()) price else point
}

/** What the buy is worth at a public price, after Groww charges. */
fun Prediction.markToMarket(price: Double): PositionResult =
    GrowwMath.atPrice(this, price, TradeSide.LONG)

fun Prediction.boughtResult(): PositionResult? {
    val exit = moneyExit()
    val priced = exit?.let { GrowwMath.atPrice(this, it, TradeSide.LONG) }
    val typed = manualPnl
    if (typed != null) {
        val base = priced ?: GrowwMath.atPrice(this, entryPrice, TradeSide.LONG)
        return base.copy(netPnl = typed)
    }
    return priced
}

/** Tax cut on this sale. Delivery profit only. */
fun Prediction.taxCutting(): Double {
    val net = boughtResult()?.netPnl ?: return 0.0
    return itrTaxOn(segment, net)
}

/** Capital put into the buy, plus the profit or minus the loss, back in the account. */
fun Prediction.returnedCash(): Double? {
    val result = boughtResult() ?: return null
    return capital + result.netPnl
}
