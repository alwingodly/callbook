package com.callbook.journal.domain

data class DaySummary(
    val invested: Double,
    val callGain: Double,
    val callLoss: Double,
    val gain: Double,
    val loss: Double,
    val matched: List<String>,
    val fell: List<String>,
    val priced: Int,
) {
    /** Profit and loss from this day's withdraws. This is how far capital moved. */
    val moved: Double get() = gain + loss
}

fun daySummary(calls: List<Prediction>): DaySummary {
    var invested = 0.0
    var callGain = 0.0
    var callLoss = 0.0
    var gain = 0.0
    var loss = 0.0
    var priced = 0
    val matched = mutableListOf<String>()
    val fell = mutableListOf<String>()
    calls.forEach { call ->
        invested += call.capital
        val atTarget = GrowwMath.atPrice(call, call.targetPrice, TradeSide.LONG).netPnl
        if (call.direction == Direction.GAIN) callGain += atTarget else callLoss += atTarget
        if (call.judgement().kind == ResultKind.SUCCESS) addName(matched, call.symbol)
        if (priceFell(call)) addName(fell, call.symbol)
        val net = call.boughtResult()?.netPnl ?: return@forEach
        priced += 1
        when {
            net > 0.005 -> gain += net
            net < -0.005 -> loss += net
        }
    }
    return DaySummary(invested, callGain, callLoss, gain, loss, matched, fell, priced)
}

/** Profit and loss, after charges, of the unsold buys that have a public price. */
fun holdingPnl(open: List<Prediction>, prices: Map<String, Double>): Double? {
    val values = open.mapNotNull { call ->
        if (call.judgement().kind != ResultKind.OPEN) return@mapNotNull null
        prices[call.symbol]?.let { call.markToMarket(it).netPnl }
    }
    return values.takeIf { it.isNotEmpty() }?.sum()
}

/** Open calls written before this day. Oldest first, so a stock held the longest stays at the top. */
fun stillHolding(predictions: List<Prediction>, day: Long): List<Prediction> =
    predictions
        .filter { it.epochDay < day && it.judgement().kind == ResultKind.OPEN }
        .sortedWith(compareBy<Prediction> { it.epochDay }.thenBy { it.createdAt })

private fun addName(names: MutableList<String>, symbol: String) {
    if (symbol !in names) names += symbol
}

private fun priceFell(call: Prediction): Boolean {
    val exit = call.moneyExit() ?: return false
    return exit < call.entryPrice - 0.009
}
