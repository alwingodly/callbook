package com.callbook.journal.domain

data class SymbolRecord(
    val symbol: String,
    val decided: Int,
    val successes: Int,
    val priced: Int,
    val netIfBought: Double,
)

data class FlipRecord(
    val id: Long,
    val symbol: String,
    val epochDay: Long,
    val called: Direction,
    val netIfBought: Double?,
)

data class Insights(
    val total: Int,
    val open: Int,
    val successes: Int,
    val failures: Int,
    val scratches: Int,
    val beyond: Int,
    val gainCallsDecided: Int,
    val gainCallSuccesses: Int,
    val lossCallsDecided: Int,
    val lossCallSuccesses: Int,
    val gainFlips: Int,
    val lossFlips: Int,
    val pricedTrades: Int,
    val inStocks: Double,
    val returned: Double,
    val profitCount: Int,
    val lossCount: Int,
    val netIfBought: Double,
    val gainsIfBought: Double,
    val lossesIfBought: Double,
    val netIfFollowed: Double,
    val symbols: List<SymbolRecord>,
    val recentFlips: List<FlipRecord>,
) {
    val decided: Int get() = successes + failures
    val successPercent: Int get() = wholePercent(successes, decided)
    val lossPercent: Int get() = wholePercent(failures, decided)
}

fun insightsOf(predictions: List<Prediction>): Insights {
    var open = 0
    var successes = 0
    var failures = 0
    var scratches = 0
    var beyond = 0
    var gainDecided = 0
    var gainSuccess = 0
    var lossDecided = 0
    var lossSuccess = 0
    var gainFlips = 0
    var lossFlips = 0
    var priced = 0
    var inStocks = 0.0
    var returned = 0.0
    var profitCount = 0
    var lossCount = 0
    var netBought = 0.0
    var gainsBought = 0.0
    var lossesBought = 0.0
    var netFollowed = 0.0
    val bySymbol = linkedMapOf<String, SymbolAccumulator>()
    val flips = mutableListOf<FlipRecord>()

    predictions.forEach { prediction ->
        val judged = prediction.judgement()
        when (judged.kind) {
            ResultKind.OPEN -> open += 1
            ResultKind.SCRATCH -> scratches += 1
            ResultKind.SUCCESS -> {
                successes += 1
                if (prediction.direction == Direction.GAIN) {
                    gainDecided += 1
                    gainSuccess += 1
                } else {
                    lossDecided += 1
                    lossSuccess += 1
                }
            }
            ResultKind.FAILED -> {
                failures += 1
                if (prediction.direction == Direction.GAIN) {
                    gainDecided += 1
                    gainFlips += 1
                } else {
                    lossDecided += 1
                    lossFlips += 1
                }
            }
        }
        if (judged.beyond) beyond += 1

        val bought = prediction.boughtResult()
        val followed = prediction.moneyExit()?.let { price ->
            GrowwMath.atPrice(prediction, price, prediction.followSide())
        }
        if (bought == null || followed == null) {
            inStocks += prediction.capital
        } else {
            priced += 1
            returned += prediction.capital + bought.netPnl
            netBought += bought.netPnl
            netFollowed += followed.netPnl
            when {
                bought.netPnl > 0.0 -> {
                    gainsBought += bought.netPnl
                    profitCount += 1
                }
                bought.netPnl < 0.0 -> {
                    lossesBought += bought.netPnl
                    lossCount += 1
                }
            }
        }

        if (judged.kind == ResultKind.SUCCESS || judged.kind == ResultKind.FAILED) {
            val bucket = bySymbol.getOrPut(prediction.symbol) { SymbolAccumulator() }
            bucket.decided += 1
            if (judged.kind == ResultKind.SUCCESS) bucket.successes += 1
            if (bought != null) {
                bucket.priced += 1
                bucket.netIfBought += bought.netPnl
            }
        }
        if (judged.kind == ResultKind.FAILED) {
            flips += FlipRecord(
                id = prediction.id,
                symbol = prediction.symbol,
                epochDay = prediction.epochDay,
                called = prediction.direction,
                netIfBought = bought?.netPnl,
            )
        }
    }

    return Insights(
        total = predictions.size,
        open = open,
        successes = successes,
        failures = failures,
        scratches = scratches,
        beyond = beyond,
        gainCallsDecided = gainDecided,
        gainCallSuccesses = gainSuccess,
        lossCallsDecided = lossDecided,
        lossCallSuccesses = lossSuccess,
        gainFlips = gainFlips,
        lossFlips = lossFlips,
        pricedTrades = priced,
        inStocks = inStocks,
        returned = returned,
        profitCount = profitCount,
        lossCount = lossCount,
        netIfBought = netBought,
        gainsIfBought = gainsBought,
        lossesIfBought = lossesBought,
        netIfFollowed = netFollowed,
        symbols = bySymbol.map { (symbol, bucket) ->
            SymbolRecord(symbol, bucket.decided, bucket.successes, bucket.priced, bucket.netIfBought)
        }.sortedWith(compareByDescending<SymbolRecord> { it.decided }.thenByDescending { it.successes }),
        recentFlips = flips.sortedWith(compareByDescending<FlipRecord> { it.epochDay }.thenByDescending { it.id }).take(5),
    )
}

private class SymbolAccumulator {
    var decided: Int = 0
    var successes: Int = 0
    var priced: Int = 0
    var netIfBought: Double = 0.0
}

fun Insights.madeMoney(): List<SymbolRecord> =
    symbols.filter { it.netIfBought > 0.5 }
        .sortedByDescending { it.netIfBought }
        .take(3)

fun Insights.costMoney(): List<SymbolRecord> =
    symbols.filter { it.netIfBought < -0.5 }
        .sortedBy { it.netIfBought }
        .take(3)

/** Cash free for the next stock: bank deposits, minus what is still in open trades, plus closed profit and loss. */
fun availableCash(banked: Double, insights: Insights, editing: Prediction? = null): Double {
    val released = if (editing != null && editing.boughtResult() == null) editing.capital else 0.0
    return banked - insights.inStocks + insights.netIfBought + released
}

fun Insights.reading(): List<String> {
    if (decided == 0) {
        return listOf(
            "Write a call, then mark what the stock actually did. Success rate and loss rate show up here.",
        )
    }
    val lines = mutableListOf<String>()
    lines += "Of $decided judged ${calls(decided)}, $successes matched the direction you wrote and $failures did not."
    if (gainCallsDecided > 0 && lossCallsDecided > 0) {
        val gainRate = wholePercent(gainCallSuccesses, gainCallsDecided)
        val lossRate = wholePercent(lossCallSuccesses, lossCallsDecided)
        lines += when {
            gainRate >= lossRate + 8 ->
                "You are sharper on gain calls ($gainRate% right) than on loss calls ($lossRate% right)."
            lossRate >= gainRate + 8 ->
                "You are sharper on loss calls ($lossRate% right) than on gain calls ($gainRate% right)."
            else -> "Gain calls and loss calls are landing at a similar rate."
        }
    } else if (gainCallsDecided > 0) {
        lines += "So far every judged call was a gain call: ${wholePercent(gainCallSuccesses, gainCallsDecided)}% right."
    } else if (lossCallsDecided > 0) {
        lines += "So far every judged call was a loss call: ${wholePercent(lossCallSuccesses, lossCallsDecided)}% right."
    }
    if (gainFlips > 0 || lossFlips > 0) {
        val parts = mutableListOf<String>()
        if (gainFlips > 0) {
            parts += "$gainFlips gain ${calls(gainFlips)} finished as a loss"
        }
        if (lossFlips > 0) {
            parts += "$lossFlips loss ${calls(lossFlips)} finished as a profit"
        }
        lines += parts.joinToString(", and ").replaceFirstChar { it.uppercase() } + "."
    }
    if (pricedTrades > 0) {
        lines += "Putting the written amount into each priced call on Groww would leave you ${rupees(netIfBought, signed = true)} after approximate charges."
        lines += "Following the call instead (buy gain calls, short intraday loss calls, stay out of delivery loss calls) would leave you ${rupees(netIfFollowed, signed = true)}."
    } else {
        lines += "Type the price Groww shows when you close a call, and the rupee result of putting that money in will show up here."
    }
    if (beyond > 0) {
        lines += "$beyond ${calls(beyond)} went beyond the target you wrote."
    }
    if (scratches > 0) {
        lines += "$scratches ${if (scratches == 1) "call finished" else "calls finished"} flat, so they stay out of the rate."
    }
    return lines
}

private fun calls(count: Int): String = if (count == 1) "call" else "calls"
