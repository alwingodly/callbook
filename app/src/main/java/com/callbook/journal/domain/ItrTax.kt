package com.callbook.journal.domain

/** Listed equity sold within a year. The rate used on the ITR for these swings. */
const val STCG_RATE = 0.20

data class ItrFile(
    val deliveryNet: Double,
    val tax: Double,
    val afterTax: Double,
    val intradayNet: Double,
)

/** Tax on one delivery profit. A loss, or an intraday trade, is not this 20%. */
fun itrTaxOn(segment: Segment, net: Double): Double {
    if (segment != Segment.DELIVERY || net <= 0.0) return 0.0
    return rupee(net * STCG_RATE)
}

/**
 * Delivery profit and loss are netted, then 20% is the tax to file.
 * Intraday is kept apart because that is slab tax, not this rate.
 */
fun itrFile(predictions: List<Prediction>): ItrFile {
    var delivery = 0.0
    var intraday = 0.0
    predictions.forEach { call ->
        val net = call.boughtResult()?.netPnl ?: return@forEach
        if (call.segment == Segment.DELIVERY) delivery += net else intraday += net
    }
    delivery = rupee(delivery)
    intraday = rupee(intraday)
    val tax = if (delivery > 0.0) rupee(delivery * STCG_RATE) else 0.0
    return ItrFile(delivery, tax, rupee(delivery - tax), intraday)
}

private fun rupee(value: Double) = kotlin.math.round(value * 100.0) / 100.0
