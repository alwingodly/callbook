package com.callbook.journal.domain

/**
 * Approximate NSE charges for a Groww equity order, from Groww's pricing page.
 *
 * Brokerage, on delivery and intraday, is the lower of Rs 20 or 0.1% of each executed
 * order, and at least Rs 5 unless that would pass 2.5% of the order. GST is 18% on
 * brokerage, DP, exchange, SEBI, and IPFT. A delivery sell also pays the DP charge:
 * Rs 20, or Rs 3.50 when the sell value is under Rs 100. A contract note can differ
 * by a few rupees, and the DP charge is a little lower for some demat accounts.
 */
object GrowwMath {
    private const val BROKERAGE_RATE = 0.001
    private const val BROKERAGE_CAP = 20.0
    private const val BROKERAGE_FLOOR = 5.0
    private const val BROKERAGE_REGULATORY_CAP = 0.025
    private const val NSE_TRANSACTION_RATE = 0.0000297
    private const val SEBI_RATE = 0.000001
    private const val IPFT_RATE = 0.000001
    private const val GST = 0.18
    private const val DELIVERY_STT = 0.001
    private const val DELIVERY_STAMP = 0.00015
    private const val INTRADAY_STT_SELL = 0.00025
    private const val INTRADAY_STAMP_BUY = 0.00003
    private const val DP_CHARGE = 20.0
    private const val DP_CHARGE_SMALL_SELL = 3.50

    fun quantity(capital: Double, entryPrice: Double): Int {
        if (capital <= 0.0 || entryPrice <= 0.0) return 0
        return (capital / entryPrice).toInt()
    }

    /** Groww's fee for one executed order, before GST. */
    fun orderBrokerage(orderValue: Double): Double {
        if (orderValue <= 0.0) return 0.0
        val standard = minOf(BROKERAGE_CAP, orderValue * BROKERAGE_RATE)
        val floor = minOf(BROKERAGE_FLOOR, orderValue * BROKERAGE_REGULATORY_CAP)
        return maxOf(standard, floor)
    }

    fun quote(segment: Segment, buyValue: Double, sellValue: Double): ChargeBreakdown {
        val turnover = buyValue + sellValue
        val brokerage = orderBrokerage(buyValue) + orderBrokerage(sellValue)
        val exchange = NSE_TRANSACTION_RATE * turnover
        val sebi = SEBI_RATE * turnover
        val ipft = IPFT_RATE * turnover
        val dp = dpCharge(segment, sellValue)
        val stt = when (segment) {
            Segment.DELIVERY -> DELIVERY_STT * turnover
            Segment.INTRADAY -> INTRADAY_STT_SELL * sellValue
        }
        val stamp = when (segment) {
            Segment.DELIVERY -> DELIVERY_STAMP * buyValue
            Segment.INTRADAY -> INTRADAY_STAMP_BUY * buyValue
        }
        val brokerGst = GST * (brokerage + dp)
        val feeGst = GST * (exchange + sebi + ipft)
        val statutory = stt + stamp + exchange + sebi + ipft + feeGst
        return ChargeBreakdown(
            brokerage = brokerage,
            dp = dp,
            brokerGst = brokerGst,
            statutory = statutory,
        )
    }

    fun atPrice(prediction: Prediction, exitPrice: Double, side: TradeSide): PositionResult {
        val entry = prediction.entryPrice
        val shares = quantity(prediction.capital, entry)
        val stockMovePercent = if (entry == 0.0) 0.0 else (exitPrice - entry) / entry * 100.0
        if (side == TradeSide.STAY_OUT || shares == 0) {
            return PositionResult(
                side = side,
                quantity = shares,
                deployed = if (side == TradeSide.STAY_OUT) 0.0 else shares * entry,
                idleCash = if (side == TradeSide.STAY_OUT) prediction.capital else (prediction.capital - shares * entry).coerceAtLeast(0.0),
                grossPnl = 0.0,
                brokerCost = 0.0,
                charges = 0.0,
                netPnl = 0.0,
                stockMovePercent = stockMovePercent,
            )
        }
        val entryValue = shares * entry
        val exitValue = shares * exitPrice
        val buyValue: Double
        val sellValue: Double
        val gross: Double
        when (side) {
            TradeSide.LONG -> {
                buyValue = entryValue
                sellValue = exitValue
                gross = exitValue - entryValue
            }
            TradeSide.SHORT -> {
                buyValue = exitValue
                sellValue = entryValue
                gross = entryValue - exitValue
            }
            TradeSide.STAY_OUT -> error("Stay out is handled above")
        }
        val fees = quote(prediction.segment, buyValue, sellValue)
        return PositionResult(
            side = side,
            quantity = shares,
            deployed = entryValue,
            idleCash = (prediction.capital - entryValue).coerceAtLeast(0.0),
            grossPnl = gross,
            brokerCost = fees.brokerCost,
            charges = fees.total,
            netPnl = gross - fees.total,
            stockMovePercent = stockMovePercent,
        )
    }

    private fun dpCharge(segment: Segment, sellValue: Double): Double {
        if (segment != Segment.DELIVERY || sellValue <= 0.0) return 0.0
        return if (sellValue < 100.0) DP_CHARGE_SMALL_SELL else DP_CHARGE
    }
}

data class ChargeBreakdown(
    val brokerage: Double,
    val dp: Double,
    val brokerGst: Double,
    val statutory: Double,
) {
    /** Brokerage, the DP charge, and GST on both. */
    val brokerCost: Double get() = brokerage + dp + brokerGst
    val total: Double get() = brokerCost + statutory
}

data class PositionResult(
    val side: TradeSide,
    val quantity: Int,
    val deployed: Double,
    val idleCash: Double,
    val grossPnl: Double,
    val brokerCost: Double,
    val charges: Double,
    val netPnl: Double,
    val stockMovePercent: Double,
)
