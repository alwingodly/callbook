package com.callbook.journal

import com.callbook.journal.domain.ListedStock
import com.callbook.journal.domain.availableCash
import com.callbook.journal.domain.pickListedEquity
import com.callbook.journal.domain.Direction
import com.callbook.journal.domain.daySummary
import com.callbook.journal.domain.GrowwMath
import com.callbook.journal.domain.Mark
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.ResultKind
import com.callbook.journal.domain.Segment
import com.callbook.journal.domain.TradeSide
import com.callbook.journal.domain.followSide
import com.callbook.journal.domain.costMoney
import com.callbook.journal.domain.holdingPnl
import com.callbook.journal.domain.insightsOf
import com.callbook.journal.domain.madeMoney
import com.callbook.journal.domain.markToMarket
import com.callbook.journal.domain.judgement
import com.callbook.journal.domain.parseAmount
import com.callbook.journal.domain.planWarning
import com.callbook.journal.domain.reading
import com.callbook.journal.domain.returnedCash
import com.callbook.journal.domain.stillHolding
import com.callbook.journal.domain.HeadlineTone
import com.callbook.journal.domain.LoserBoard
import com.callbook.journal.domain.LosersLoad
import com.callbook.journal.domain.Danger
import com.callbook.journal.domain.DipSuggestion
import com.callbook.journal.domain.HeadlineRead
import com.callbook.journal.domain.StockHeadline
import com.callbook.journal.domain.headlineDanger
import com.callbook.journal.domain.WeekShape
import com.callbook.journal.domain.isMildPause
import com.callbook.journal.domain.risingPause
import com.callbook.journal.domain.shiftTradingDay
import com.callbook.journal.domain.tradingDay
import com.callbook.journal.domain.swingPlan
import com.callbook.journal.domain.buyLimit
import com.callbook.journal.domain.adviseDip
import com.callbook.journal.domain.weekShape
import com.callbook.journal.domain.parseIndexLosers
import com.callbook.journal.domain.parseLoserBoards
import com.callbook.journal.domain.publisherLink
import com.callbook.journal.domain.readHeadline
import com.callbook.journal.domain.verdictSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalLogicTest {
    @Test
    fun loserBoardsKeepTheSteepestFallFirst() {
        val parsed = parseLoserBoards(
            """
            {"legends":[["NIFTY","NIFTY 50"],["SecGtr20","Securities > Rs 20"],["FOSec","F&O Securities"]],
            "NIFTY":{"timestamp":"02-Nov-2026 10:34:33","data":[{"symbol":"ADANIPORTS","ltp":1834,"perChange":-0.86,"prev_price":1850,"trade_quantity":"5.36 L"}]},
            "SecGtr20":{"timestamp":"02-Nov-2026 10:34:33","data":[
              {"symbol":"IGL","ltp":132,"perChange":-5.17,"prev_price":139.19,"trade_quantity":"53.14 L"},
              {"symbol":"INDIGOPNTS","ltp":876,"perChange":-12.16,"prev_price":997.3,"trade_quantity":"4.27 L"}
            ]},"FOSec":{"data":[{"symbol":"NAVINFLUOR","ltp":8320,"perChange":-4.15,"prev_price":8680,"trade_quantity":"1.32 L"}]}}
            """.trimIndent(),
        )
        val ready = parsed as LosersLoad.Ready
        assertEquals("02-Nov-2026 10:34:33", ready.asOf)
        assertEquals("INDIGOPNTS", ready.boards.getValue(LoserBoard.ABOVE_20).first().symbol)
        assertEquals(-12.16, ready.boards.getValue(LoserBoard.ABOVE_20).first().changePercent, 0.001)
        assertEquals("NAVINFLUOR", ready.boards.getValue(LoserBoard.FUTURES).single().symbol)
        assertEquals("ADANIPORTS", ready.boards.getValue(LoserBoard.NIFTY).single().symbol)
        assertEquals(LosersLoad.Unavailable("No losers right now"), parseLoserBoards("""{"SecGtr20":{"data":[]}}"""))
    }

    @Test
    fun indexLosersKeepFallingLargeAndMidNames() {
        val losers = parseIndexLosers(
            """
            {"timestamp":"02-Nov-2026 11:01:00","data":[
              {"symbol":"NIFTY 100","priority":1,"pChange":1.27,"lastPrice":24275.6,"previousClose":23971.55,"totalTradedVolume":100},
              {"symbol":"WIPRO","pChange":-0.65,"lastPrice":197.03,"previousClose":198.31,"totalTradedVolume":8449691},
              {"symbol":"INFY","pChange":-1.3,"lastPrice":1169.6,"previousClose":1185,"totalTradedVolume":4288336},
              {"symbol":"RELIANCE","pChange":0.4,"lastPrice":1400,"previousClose":1394,"totalTradedVolume":1000}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("INFY", "WIPRO"), losers.map { it.symbol })
        assertEquals("42.88 L", losers.first().volume)
    }

    @Test
    fun pauseNeedsARisingWeekAndASmallDip() {
        assertTrue(isMildPause(-0.6, 5.0))
        assertTrue(!isMildPause(-1.4, 8.0))
        assertTrue(
            risingPause(
                lows = listOf(10.0, 9.0, 9.4, 9.2),
                closes = listOf(10.0, 11.0, 12.0, 11.6),
            ),
        )
        assertTrue(
            !risingPause(
                lows = listOf(10.0, 9.0, 8.5, 8.0),
                closes = listOf(10.0, 9.0, 8.5, 8.0),
            ),
        )
    }

    @Test
    fun todaySkipsSaturdayAndSunday() {
        val monday = java.time.LocalDate.of(2026, 11, 2)
        val friday = java.time.LocalDate.of(2026, 10, 30)
        val saturday = java.time.LocalDate.of(2026, 10, 31)
        assertEquals(friday, shiftTradingDay(monday, -1))
        assertEquals(monday, shiftTradingDay(friday, 1))
        assertEquals(friday, tradingDay(saturday))
    }

    @Test
    fun dipAdviceSkipsBadNewsAndASlide() {
        assertEquals(WeekShape.STILL_FALLING, weekShape(listOf(110.0, 108.0, 105.0, 101.0)))
        assertEquals(WeekShape.SLOWING, weekShape(listOf(100.0, 90.0, 95.0, 94.0)))
        val bad = adviseDip(
            symbol = "INFY",
            price = 1170.0,
            changePercent = -1.4,
            closes = listOf(1200.0, 1190.0, 1188.0),
            headlines = listOf(HeadlineRead(HeadlineTone.NEGATIVE, 90)),
            marketPercent = 0.2,
        )
        assertEquals(DipSuggestion.SKIP, bad.suggestion)
        val buy = adviseDip(
            symbol = "INFY",
            price = 1170.0,
            changePercent = -1.4,
            closes = listOf(1160.0, 1180.0, 1175.0),
            headlines = listOf(HeadlineRead(HeadlineTone.MIXED, 50)),
            marketPercent = 0.2,
        )
        assertEquals(DipSuggestion.BUY, buy.suggestion)
        val onTheLow = buyLimit(1160.1, 1160.0)
        val bounced = buyLimit(1170.0, 1160.0)
        assertEquals(1160.1, onTheLow.price, 0.001)
        assertEquals(1160.0, bounced.price, 0.001)
        val plan = swingPlan(entry = 1170.0, dayLow = 1160.0, previousClose = 1200.0)
        assertEquals(1150.72, plan.stop, 0.001)
        assertEquals(1200.0, plan.target, 0.001)
        assertTrue(plan.stop < plan.entry && plan.target > plan.entry)
    }

    @Test
    fun newsCoversOnlyPartOfADeeperFall() {
        val matched = adviseDip(
            symbol = "INFY",
            price = 1170.0,
            changePercent = -1.2,
            closes = listOf(1160.0, 1180.0, 1175.0),
            headlines = listOf(HeadlineRead(HeadlineTone.NEGATIVE, 70)),
            marketPercent = 0.2,
        )
        assertEquals(1.0, matched.newsCovers, 0.001)
        assertEquals(DipSuggestion.WAIT, matched.suggestion)
        assertEquals(Danger.MEDIUM, matched.danger)
        val deeper = adviseDip(
            symbol = "INFY",
            price = 1140.0,
            changePercent = -3.5,
            closes = listOf(1160.0, 1180.0, 1175.0),
            headlines = listOf(HeadlineRead(HeadlineTone.NEGATIVE, 70)),
            marketPercent = 0.2,
        )
        assertEquals(DipSuggestion.BUY, deeper.suggestion)
        assertEquals(Danger.LOW, deeper.danger)
        assertTrue(deeper.newsNote.contains("not in the news"))
        val story = StockHeadline("Shares fall after fraud probe", "Mint", "https://example.com", 0L, HeadlineRead(HeadlineTone.NEGATIVE, 90))
        assertEquals("Makes danger high", headlineDanger(story.read))
        assertEquals("Keeps danger low", headlineDanger(HeadlineRead(HeadlineTone.MIXED, 50)))
    }

    @Test
    fun headlineWordsLeanPositiveOrNegative() {
        val profit = readHeadline("Company reports record profit")
        val fraud = readHeadline("Shares fall after fraud probe")
        val quiet = readHeadline("Board meeting next week")
        assertEquals(HeadlineTone.POSITIVE, profit.tone)
        assertTrue(profit.percent >= 70)
        assertEquals(HeadlineTone.NEGATIVE, fraud.tone)
        assertEquals(HeadlineTone.MIXED, quiet.tone)
        assertEquals(50, quiet.percent)
        assertEquals(HeadlineTone.MIXED, readHeadline("Net profit tanks").tone)
        val body = """["garturlres","https://www.reuters.com/world/india/infosys-falls",1]"""
        assertEquals(
            "https://www.reuters.com/world/india/infosys-falls",
            publisherLink(body),
        )
    }

    @Test
    fun quantityFloorsToWholeShares() {
        assertEquals(4, GrowwMath.quantity(10_000.0, 2_456.0))
        assertEquals(0, GrowwMath.quantity(100.0, 2_456.0))
    }

    @Test
    fun parseAmountAcceptsIndianGrouping() {
        assertEquals(10000.0, parseAmount("10,000")!!, 0.001)
        assertEquals(2456.5, parseAmount("Rs 2456.50")!!, 0.001)
        assertNull(parseAmount("0"))
        assertNull(parseAmount("abc"))
    }

    @Test
    fun deliveryBuyKeepsChargesBelowTheGrossGain() {
        val call = sample(direction = Direction.GAIN, segment = Segment.DELIVERY, entry = 100.0, target = 110.0, stop = 95.0, capital = 1_000.0)
        val result = GrowwMath.atPrice(call, 110.0, TradeSide.LONG)
        assertEquals(10, result.quantity)
        assertEquals(100.0, result.grossPnl, 0.001)
        assertTrue(result.brokerCost > 20.0)
        assertTrue(result.charges > result.brokerCost)
        assertTrue(result.netPnl < result.grossPnl)
        assertTrue(result.netPnl > 0.0)
    }

    @Test
    fun brokerageIsTheLowerOfTwentyOrPointOnePercent() {
        assertEquals(20.0, GrowwMath.orderBrokerage(100_000.0), 0.001)
        assertEquals(10.0, GrowwMath.orderBrokerage(10_000.0), 0.001)
        assertEquals(5.0, GrowwMath.orderBrokerage(1_000.0), 0.001)
        val roundTrip = GrowwMath.quote(Segment.DELIVERY, buyValue = 10_000.0, sellValue = 11_000.0)
        assertEquals(21.0, roundTrip.brokerage, 0.001)
        assertEquals(20.0, roundTrip.dp, 0.001)
        assertEquals(7.38, roundTrip.brokerGst, 0.001)
        assertEquals(48.38, roundTrip.brokerCost, 0.001)
    }

    @Test
    fun intradayShortProfitsWhenPriceFalls() {
        val call = sample(direction = Direction.LOSS, segment = Segment.INTRADAY, entry = 100.0, target = 90.0, stop = 105.0, capital = 1_000.0)
        val result = GrowwMath.atPrice(call, 90.0, TradeSide.SHORT)
        assertEquals(100.0, result.grossPnl, 0.001)
        assertTrue(result.brokerCost > 0.0)
        assertTrue(result.netPnl > 80.0)
        assertTrue(result.netPnl < result.grossPnl)
    }

    @Test
    fun deliveryLossCallStaysOut() {
        val call = sample(direction = Direction.LOSS, segment = Segment.DELIVERY)
        assertEquals(TradeSide.STAY_OUT, call.followSide())
        val stayed = GrowwMath.atPrice(call, 90.0, call.followSide())
        assertEquals(0.0, stayed.netPnl, 0.001)
        assertEquals(0.0, stayed.charges, 0.001)
    }

    @Test
    fun gainCallThatFallsIsAFailedPrediction() {
        val call = sample(direction = Direction.GAIN, actual = 90.0, entry = 100.0, target = 110.0, stop = 95.0)
        val judged = call.judgement()
        assertEquals(ResultKind.FAILED, judged.kind)
        assertTrue(judged.hitStop)
        assertTrue(call.verdictSentence()!!.contains("went to a loss") || call.verdictSentence()!!.contains("hit your stop"))
    }

    @Test
    fun lossCallThatRisesIsAFailedPrediction() {
        val call = sample(direction = Direction.LOSS, actual = 112.0, entry = 100.0, target = 90.0, stop = 108.0)
        val judged = call.judgement()
        assertEquals(ResultKind.FAILED, judged.kind)
        assertTrue(call.verdictSentence()!!.contains("went to a profit") || call.verdictSentence()!!.contains("hit your stop"))
    }

    @Test
    fun pricePastTheTargetCountsAsBeyondAndRight() {
        val gain = sample(direction = Direction.GAIN, actual = 120.0, entry = 100.0, target = 110.0, stop = 95.0)
        assertEquals(ResultKind.SUCCESS, gain.judgement().kind)
        assertTrue(gain.judgement().beyond)
        val loss = sample(direction = Direction.LOSS, actual = 80.0, entry = 100.0, target = 90.0, stop = 108.0)
        assertEquals(ResultKind.SUCCESS, loss.judgement().kind)
        assertTrue(loss.judgement().beyond)
        assertTrue(loss.verdictSentence()!!.contains("beyond"))
    }

    @Test
    fun flatPriceIsAScratch() {
        val call = sample(direction = Direction.GAIN, actual = 100.0)
        assertEquals(ResultKind.SCRATCH, call.judgement().kind)
    }

    @Test
    fun markWithoutPriceStillScores() {
        val opposite = sample(direction = Direction.GAIN, mark = Mark.OPPOSITE)
        assertEquals(ResultKind.FAILED, opposite.judgement().kind)
        val beyond = sample(direction = Direction.LOSS, mark = Mark.BEYOND)
        assertEquals(ResultKind.SUCCESS, beyond.judgement().kind)
        assertTrue(beyond.judgement().beyond)
    }

    @Test
    fun withdrawChoiceWinsOverAContradictingPrice() {
        val call = sample(direction = Direction.GAIN, actual = 90.0, mark = Mark.WITHDRAW_PROFIT, stop = 80.0)
        assertEquals(ResultKind.SUCCESS, call.judgement().kind)
        assertTrue(call.returnedCash()!! > call.capital)
    }

    @Test
    fun ratesAndFlipsComeFromTheJournal() {
        val calls = listOf(
            sample(id = 1, symbol = "RELIANCE", direction = Direction.GAIN, actual = 110.0),
            sample(id = 2, symbol = "RELIANCE", direction = Direction.GAIN, actual = 90.0, stop = 80.0),
            sample(id = 3, symbol = "TCS", direction = Direction.LOSS, actual = 112.0, target = 90.0, stop = 115.0),
            sample(id = 4, symbol = "TCS", direction = Direction.LOSS, actual = 90.0, target = 90.0, stop = 108.0),
            sample(id = 5, symbol = "INFY", direction = Direction.GAIN),
        )
        val insights = insightsOf(calls)
        assertEquals(2, insights.successes)
        assertEquals(2, insights.failures)
        assertEquals(1, insights.open)
        assertEquals(50, insights.successPercent)
        assertEquals(50, insights.lossPercent)
        assertEquals(1, insights.gainFlips)
        assertEquals(1, insights.lossFlips)
        assertEquals(10_000.0, insights.inStocks, 0.001)
        assertEquals(40_000.0 + insights.netIfBought, insights.returned, 0.05)
        assertEquals(4, insights.pricedTrades)
        assertEquals(2, insights.profitCount)
        assertEquals(2, insights.lossCount)
        assertTrue(insights.gainsIfBought > 0.0)
        assertTrue(insights.lossesIfBought < 0.0)
        assertEquals(insights.gainsIfBought + insights.lossesIfBought, insights.netIfBought, 0.001)
        assertTrue(insights.reading().any { it.contains("finished as a loss") })
        assertTrue(insights.reading().any { it.contains("finished as a profit") })
        val reliance = insights.symbols.first { it.symbol == "RELIANCE" }
        assertEquals(1, reliance.successes)
        assertEquals(2, reliance.decided)
    }

    @Test
    fun capitalGrowsOnProfitAndShrinksOnLoss() {
        val open = sample(id = 1, capital = 1_000.0)
        val profit = sample(id = 2, capital = 1_000.0, mark = Mark.WITHDRAW_PROFIT)
        val loss = sample(id = 3, capital = 1_000.0, mark = Mark.WITHDRAW_LOSS, stop = 90.0)
        val insights = insightsOf(listOf(open, profit, loss))
        val cash = availableCash(banked = 5_000.0, insights)
        assertEquals(1_000.0, insights.inStocks, 0.001)
        assertEquals(5_000.0 - 1_000.0 + insights.netIfBought, cash, 0.05)
        assertTrue(insights.netIfBought != 0.0)
        val editing = availableCash(banked = 5_000.0, insights, editing = open)
        assertEquals(cash + 1_000.0, editing, 0.05)
    }

    @Test
    fun markedCallCountsMoneyAtTheWrittenLevel() {
        val insights = insightsOf(
            listOf(
                sample(id = 1, direction = Direction.GAIN, mark = Mark.CORRECT, capital = 1_000.0),
                sample(id = 2, direction = Direction.GAIN, mark = Mark.HIT_STOP, stop = 90.0, capital = 1_000.0),
            ),
        )
        assertEquals(0.0, insights.inStocks, 0.001)
        assertEquals(2_000.0 + insights.netIfBought, insights.returned, 0.05)
        assertEquals(2, insights.pricedTrades)
        assertEquals(1, insights.profitCount)
        assertEquals(1, insights.lossCount)
        assertTrue(insights.gainsIfBought > 0.0)
        assertTrue(insights.lossesIfBought < 0.0)
        assertTrue(insights.netIfBought < insights.gainsIfBought)
    }

    @Test
    fun todayTotalsSeparateMatchedNamesFromStocksThatFell() {
        val summary = daySummary(
            listOf(
                sample(id = 1, symbol = "TCS", direction = Direction.GAIN, actual = 110.0, capital = 5_000.0),
                sample(id = 2, symbol = "INFY", direction = Direction.GAIN, actual = 90.0, capital = 4_000.0, stop = 80.0),
                sample(id = 3, symbol = "RELIANCE", direction = Direction.LOSS, actual = 90.0, capital = 6_000.0, target = 90.0, stop = 108.0),
            ),
        )
        assertEquals(15_000.0, summary.invested, 0.001)
        assertEquals(listOf("TCS", "RELIANCE"), summary.matched)
        assertEquals(listOf("INFY", "RELIANCE"), summary.fell)
        assertTrue(summary.callGain > 0.0)
        assertTrue(summary.callLoss < 0.0)
        assertTrue(summary.gain > 0.0)
        assertTrue(summary.loss < 0.0)
        assertEquals(summary.gain + summary.loss, summary.moved, 0.05)
        assertEquals(3, summary.priced)
    }

    @Test
    fun moneyNamesRankTheRupeesEachStockMadeOrCost() {
        val insights = insightsOf(
            listOf(
                sample(id = 1, symbol = "TCS", actual = 110.0),
                sample(id = 2, symbol = "RELIANCE", actual = 120.0),
                sample(id = 3, symbol = "INFY", actual = 90.0),
                sample(id = 4, symbol = "HDFC"),
            ),
        )
        assertEquals(listOf("RELIANCE", "TCS"), insights.madeMoney().map { it.symbol })
        assertEquals(listOf("INFY"), insights.costMoney().map { it.symbol })
    }

    @Test
    fun stillHoldingKeepsEarlierOpenCallsAndDropsSoldOnes() {
        val day = 20_000L
        val holding = stillHolding(
            listOf(
                sample(id = 1, epochDay = day - 2),
                sample(id = 2, symbol = "TCS", epochDay = day - 1),
                sample(id = 3, symbol = "INFY", epochDay = day),
                sample(id = 4, symbol = "WIPRO", epochDay = day - 3, mark = Mark.WITHDRAW_PROFIT),
            ),
            day,
        )
        assertEquals(listOf(1L, 2L), holding.map { it.id })
    }

    @Test
    fun holdingTotalAddsTheOpenBuysThatHaveAPrice() {
        val up = sample(id = 1, symbol = "TCS", entry = 100.0, capital = 10_000.0)
        val down = sample(id = 2, symbol = "INFY", entry = 100.0, capital = 10_000.0)
        val sold = sample(id = 3, symbol = "WIPRO", mark = Mark.WITHDRAW_PROFIT)
        val total = holdingPnl(
            listOf(up, down, sold),
            mapOf("TCS" to 110.0, "INFY" to 90.0),
        )
        assertEquals(up.markToMarket(110.0).netPnl + down.markToMarket(90.0).netPnl, total!!, 0.05)
        assertNull(holdingPnl(listOf(up), emptyMap()))
    }

    @Test
    fun anOpenBuyShowsProfitOrLossAtThePublicPrice() {
        val call = sample(entry = 100.0, capital = 10_000.0)
        assertTrue(call.markToMarket(110.0).netPnl > 0.0)
        assertTrue(call.markToMarket(90.0).netPnl < 0.0)
    }

    @Test
    fun publicChartPrefersTheNseListing() {
        val picked = pickListedEquity(
            listOf(
                ListedStock("SAIL.BO", "Steel Authority", "EQUITY"),
                ListedStock("^NSEI", "Nifty", "INDEX"),
                ListedStock("SAIL.NS", "Steel Authority", "EQUITY"),
            ),
        )
        assertEquals("SAIL.NS", picked?.symbol)
        assertNull(pickListedEquity(listOf(ListedStock("NIFTYBEES.NS", "Nifty bees", "ETF"))))
    }

    @Test
    fun planWarningCatchesATargetOnTheWrongSide() {
        assertTrue(planWarning(Direction.GAIN, entry = 100.0, target = 90.0, stop = 95.0)!!.contains("target"))
        assertNull(planWarning(Direction.LOSS, entry = 100.0, target = 90.0, stop = 108.0))
    }

    private fun sample(
        id: Long = 1,
        symbol: String = "RELIANCE",
        direction: Direction = Direction.GAIN,
        segment: Segment = Segment.DELIVERY,
        entry: Double = 100.0,
        target: Double = 110.0,
        stop: Double = 95.0,
        capital: Double = 10_000.0,
        actual: Double? = null,
        mark: Mark? = null,
        epochDay: Long = 20_000,
    ) = Prediction(
        id = id,
        epochDay = epochDay,
        symbol = symbol,
        direction = direction,
        segment = segment,
        entryPrice = entry,
        targetPrice = target,
        stopPrice = stop,
        capital = capital,
        actualPrice = actual,
        note = "",
        mark = mark,
        createdAt = id,
    )
}
