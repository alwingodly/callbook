package com.callbook.journal.ui

import com.callbook.journal.domain.Direction
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.Segment
import com.callbook.journal.domain.fieldPrice
import com.callbook.journal.domain.normalizeSymbol
import com.callbook.journal.domain.parseAmount
import com.callbook.journal.domain.planWarning

data class CallDraft(
    val epochDay: Long,
    val symbol: String,
    val direction: Direction,
    val segment: Segment,
    val entry: String,
    val target: String,
    val stop: String,
    val capital: String,
    val note: String,
) {
    fun toPrediction(existing: Prediction?): DraftResult {
        val cleanSymbol = normalizeSymbol(symbol)
        if (cleanSymbol.isBlank()) return DraftResult.Invalid("Enter the stock symbol from Groww.")
        val entryPrice = parseAmount(entry) ?: return DraftResult.Invalid("Enter the price Groww is showing.")
        val targetPrice = parseAmount(target) ?: return DraftResult.Invalid("Enter your target.")
        val stopPrice = parseAmount(stop) ?: return DraftResult.Invalid("Enter your stop loss.")
        val amount = parseAmount(capital) ?: return DraftResult.Invalid("Enter how much money you would put in.")
        return DraftResult.Valid(
            prediction = Prediction(
                id = existing?.id ?: 0,
                epochDay = epochDay,
                symbol = cleanSymbol,
                direction = direction,
                segment = segment,
                entryPrice = entryPrice,
                targetPrice = targetPrice,
                stopPrice = stopPrice,
                capital = amount,
                actualPrice = existing?.actualPrice,
                note = note.trim().take(400),
                mark = existing?.mark,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                manualPnl = existing?.manualPnl,
            ),
            warning = planWarning(direction, entryPrice, targetPrice, stopPrice),
        )
    }

    companion object {
        fun fromDip(epochDay: Long, dip: com.callbook.journal.domain.DipCheck, freeCash: Double) = CallDraft(
            epochDay = epochDay,
            symbol = dip.symbol,
            direction = Direction.GAIN,
            segment = Segment.DELIVERY,
            entry = fieldPrice(dip.limitPrice),
            target = fieldPrice(dip.targetPrice),
            stop = fieldPrice(dip.stopPrice),
            capital = run {
                val spend = minOf(100_000.0, freeCash.coerceAtLeast(0.0))
                if (spend < 1.0) "" else fieldPrice(spend)
            },
            note = "Strategy swing. Stop is under today's low.",
        )

        fun empty(epochDay: Long) = CallDraft(
            epochDay = epochDay,
            symbol = "",
            direction = Direction.GAIN,
            segment = Segment.DELIVERY,
            entry = "",
            target = "",
            stop = "",
            capital = "",
            note = "",
        )

        fun from(prediction: Prediction) = CallDraft(
            epochDay = prediction.epochDay,
            symbol = prediction.symbol,
            direction = prediction.direction,
            segment = prediction.segment,
            entry = fieldPrice(prediction.entryPrice),
            target = fieldPrice(prediction.targetPrice),
            stop = fieldPrice(prediction.stopPrice),
            capital = fieldPrice(prediction.capital),
            note = prediction.note,
        )
    }
}

sealed interface DraftResult {
    data class Valid(val prediction: Prediction, val warning: String?) : DraftResult
    data class Invalid(val message: String) : DraftResult
}
