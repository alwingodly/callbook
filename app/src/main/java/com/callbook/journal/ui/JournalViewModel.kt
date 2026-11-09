package com.callbook.journal.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.callbook.journal.data.AccountStore
import com.callbook.journal.data.PredictionRepository
import com.callbook.journal.data.PublicQuotes
import com.callbook.journal.data.TickerStore
import com.callbook.journal.domain.BuyScore
import com.callbook.journal.domain.CapDips
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.DipCheck
import com.callbook.journal.domain.DipSuggestion
import com.callbook.journal.domain.ChartLoad
import com.callbook.journal.domain.ChartSpan
import com.callbook.journal.domain.DipLoad
import com.callbook.journal.domain.ListedRead
import com.callbook.journal.domain.allowsAutoBuy
import com.callbook.journal.domain.availableCash
import com.callbook.journal.domain.insightsOf
import com.callbook.journal.domain.settleNotStarted
import com.callbook.journal.domain.showsCapBadge
import com.callbook.journal.domain.nextSessionDay
import com.callbook.journal.domain.scoreSession
import com.callbook.journal.domain.isMarketWeekday
import com.callbook.journal.domain.shiftTradingDay
import com.callbook.journal.domain.tradingDay
import com.callbook.journal.domain.Mark
import com.callbook.journal.domain.NewsLoad
import com.callbook.journal.domain.SectorLoad
import com.callbook.journal.domain.SymbolLoad
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.ResultKind
import com.callbook.journal.domain.judgement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class JournalFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    RIGHT("Profit"),
    WRONG("Loss"),
    BEYOND("Beyond target"),
}

fun JournalFilter.accepts(prediction: Prediction): Boolean {
    val judged = prediction.judgement()
    return when (this) {
        JournalFilter.ALL -> true
        JournalFilter.OPEN -> judged.kind == ResultKind.OPEN
        JournalFilter.RIGHT -> judged.kind == ResultKind.SUCCESS
        JournalFilter.WRONG -> judged.kind == ResultKind.FAILED
        JournalFilter.BEYOND -> judged.beyond
    }
}

sealed interface JournalLoad {
    data object Loading : JournalLoad
    data class Ready(val predictions: List<Prediction>) : JournalLoad
}

class JournalViewModel(
    private val repository: PredictionRepository,
    private val account: AccountStore,
    private val tickers: TickerStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    val banked: StateFlow<Double> = account.observe()

    private val chartQuery = MutableStateFlow("")
    private val chartSpan = MutableStateFlow(tickers.chartSpan())
    private var chartReady = false

    val chartRange: StateFlow<ChartSpan> = chartSpan

    private val forcedChart = MutableStateFlow<Pair<String, ChartLoad>?>(null)

    private val chartBase: StateFlow<ChartLoad> = combine(chartQuery, chartSpan) { query, span ->
        query to span
    }.flatMapLatest { (query, span) ->
        flow {
            if (query.isBlank()) {
                chartReady = false
                emit(ChartLoad.Idle)
            } else {
                val cached = PublicQuotes.peek(query, span.yahooRange)
                if (cached != null) {
                    chartReady = true
                    emit(cached)
                } else {
                    if (!chartReady) emit(ChartLoad.Loading)
                    val next = withContext(Dispatchers.IO) { loadNamed(query, span) }
                    if (next is ChartLoad.Ready) chartReady = true
                    emit(next)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartLoad.Idle)

    val chart: StateFlow<ChartLoad> = combine(chartBase, forcedChart, chartQuery) { base, forced, query ->
        val held = forced
        if (held != null && held.first.equals(query, true)) held.second else base
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartLoad.Idle)

    fun showChart(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty() || cleaned == chartQuery.value) return
        chartReady = false
        forcedChart.value = null
        shownPrice.value = null
        chartQuery.value = cleaned
    }

    fun hideChart() {
        chartReady = false
        forcedChart.value = null
        shownPrice.value = null
        shownJob?.cancel()
        chartQuery.value = ""
    }

    private var newsName = ""
    private val newsState = MutableStateFlow<NewsLoad>(NewsLoad.Idle)
    val news: StateFlow<NewsLoad> = newsState
    private var newsJob: Job? = null

    fun showNews(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        if (cleaned.equals(newsName, true) && newsState.value is NewsLoad.Ready) return
        newsName = cleaned
        newsJob?.cancel()
        newsJob = viewModelScope.launch {
            newsState.value = NewsLoad.Loading
            newsState.value = withContext(Dispatchers.IO) { PublicQuotes.headlines(cleaned) }
        }
    }

    fun hideNews() {
        newsJob?.cancel()
        newsName = ""
        newsState.value = NewsLoad.Idle
    }

    /** Prices on Today, Stocks, and Money. Leaves the lists on screen. */
    suspend fun refreshJournal() {
        if (livePrices.value) {
            val names = openNames(load.value)
            if (names.isNotEmpty()) {
                val next = pricesFor(names, allowCache = false)
                if (livePrices.value) {
                    val keep = names.toSet()
                    spotPrices.value = spotPrices.value.filterKeys { it in keep } + next
                    pricedAt = System.currentTimeMillis()
                }
            }
        }
        val symbols = (load.value as? JournalLoad.Ready)?.predictions?.map { it.symbol }.orEmpty()
        if (symbols.isEmpty()) return
        val reads = withContext(Dispatchers.IO) { PublicQuotes.listedReads(symbols) }
        capBadgeState.value = reads.filterValues { showsCapBadge(it.suggestion) }.mapValues { it.value.cap }
    }

    /** Sector moves and the Indian headlines, ignoring the saved copy. */
    suspend fun refreshSectors() {
        sectorJob?.cancel()
        sectorState.value = withContext(Dispatchers.IO) { PublicQuotes.sectors(fresh = true) }
    }

    /** The open strategy list, with new headlines. */
    suspend fun refreshBoard(cap: CapSize, dips: Boolean) {
        val key = "${cap.name}:$dips"
        boardJobs[key]?.cancel()
        reloadBoard(cap, dips, fresh = true)
    }

    /** The typed strategy name, with new headlines. */
    suspend fun refreshSymbol(symbol: String) {
        symbolJob?.cancel()
        val name = symbol.trim()
        if (name.isEmpty()) {
            symbolState.value = SymbolLoad.Idle
            return
        }
        if (symbolState.value !is SymbolLoad.Ready) symbolState.value = SymbolLoad.Loading
        val result = withContext(Dispatchers.IO) {
            runCatching { PublicQuotes.checkSymbol(name) }.getOrNull()
        }
        coroutineContext.ensureActive()
        if (result == null) {
            symbolState.value = SymbolLoad.Unavailable("No quote for this name")
            return
        }
        symbolState.value = SymbolLoad.Ready(result)
        rememberBuys(listOf(result.check))
    }

    /** The open stock page: its news, and the chart when that is open. */
    suspend fun refreshDetail(symbol: String) {
        val name = symbol.trim()
        coroutineScope {
            val headlines = async(Dispatchers.IO) {
                if (name.isEmpty()) null else PublicQuotes.headlines(name, maxAgeMs = 0)
            }
            val chartWork = async {
                val query = chartQuery.value
                if (query.isBlank()) return@async
                val next = withContext(Dispatchers.IO) { loadNamed(query, chartSpan.value) }
                if (chartQuery.value == query) forcedChart.value = query to next
            }
            val priceWork = async {
                val query = chartQuery.value
                if (query.isBlank()) return@async
                val quote = withContext(Dispatchers.IO) {
                    PublicQuotes.spot(query, tickers.get(query), allowCache = false)
                } ?: return@async
                if (chartQuery.value != query) return@async
                tickers.save(query, quote.symbol)
                shownPrice.value = query to quote.price
            }
            val loaded = headlines.await()
            if (loaded != null && newsName.equals(name, true)) newsState.value = loaded
            chartWork.await()
            priceWork.await()
        }
    }

    private val sectorState = MutableStateFlow<SectorLoad>(SectorLoad.Idle)
    val sectors: StateFlow<SectorLoad> = sectorState
    private var sectorJob: Job? = null

    fun loadSectors(fresh: Boolean = false) {
        if (sectorJob?.isActive == true && !fresh) return
        sectorJob?.cancel()
        sectorJob = viewModelScope.launch {
            if (sectorState.value !is SectorLoad.Ready) sectorState.value = SectorLoad.Loading
            sectorState.value = withContext(Dispatchers.IO) { PublicQuotes.sectors(fresh) }
        }
    }

    private val dipsState = MutableStateFlow<DipLoad>(DipLoad.Idle)
    val dips: StateFlow<DipLoad> = dipsState
    private val symbolState = MutableStateFlow<SymbolLoad>(SymbolLoad.Idle)
    val symbolCheck: StateFlow<SymbolLoad> = symbolState
    private var symbolJob: Job? = null

    /** One typed name, through the same buy, wait, or skip check as the list. */
    fun checkStock(symbol: String) {
        val name = symbol.trim()
        if (name.isEmpty()) {
            symbolState.value = SymbolLoad.Idle
            return
        }
        symbolJob?.cancel()
        symbolJob = viewModelScope.launch {
            symbolState.value = SymbolLoad.Loading
            val result = withContext(Dispatchers.IO) {
                runCatching { PublicQuotes.checkSymbol(name) }.getOrNull()
            }
            if (!isActive) return@launch
            if (result == null) {
                symbolState.value = SymbolLoad.Unavailable("No quote for this name")
                return@launch
            }
            symbolState.value = SymbolLoad.Ready(result)
            rememberBuys(listOf(result.check))
        }
    }
    private val boardJobs = mutableMapOf<String, Job>()
    private val boardLock = Mutex()

    private var dipDraft: CallDraft? = null

    fun stageDip(dip: com.callbook.journal.domain.DipCheck, freeCash: Double) {
        dipDraft = CallDraft.fromDip(LocalDate.now().toEpochDay(), dip, freeCash)
    }

    fun takeDipDraft(): CallDraft? = dipDraft.also { dipDraft = null }

    /** One cap and one list. A refresh of large dips does not wait for mid, small, or pause. */
    fun loadBoard(cap: CapSize, dips: Boolean, fresh: Boolean = false) {
        val key = "${cap.name}:$dips"
        val running = boardJobs[key]
        if (running?.isActive == true && !fresh) return
        running?.cancel()
        boardJobs[key] = viewModelScope.launch { reloadBoard(cap, dips, fresh) }
    }

    private suspend fun reloadBoard(cap: CapSize, dips: Boolean, fresh: Boolean) {
        if (dipsState.value !is DipLoad.Ready) dipsState.value = DipLoad.Loading
        val slice = withContext(Dispatchers.IO) {
            runCatching { PublicQuotes.scanBoard(cap, dips, fresh) }.getOrNull()
        }
        coroutineContext.ensureActive()
        if (slice == null) {
            if (dipsState.value !is DipLoad.Ready) {
                dipsState.value = DipLoad.Unavailable("This list is offline")
            }
            return
        }
        boardLock.withLock {
            coroutineContext.ensureActive()
            val ready = dipsState.value as? DipLoad.Ready
            val boards = ready?.boards.orEmpty().toMutableMap()
            val previous = boards[cap]
            boards[cap] = if (dips) {
                CapDips(
                    marketPercent = slice.marketPercent,
                    dips = slice.checks,
                    pauses = previous?.pauses.orEmpty(),
                    dipsLoaded = true,
                    pausesLoaded = previous?.pausesLoaded == true,
                )
            } else {
                CapDips(
                    marketPercent = slice.marketPercent,
                    dips = previous?.dips.orEmpty(),
                    pauses = slice.checks,
                    dipsLoaded = previous?.dipsLoaded == true,
                    pausesLoaded = true,
                )
            }
            val asOf = slice.asOf.ifBlank { ready?.asOf.orEmpty() }
            dipsState.value = DipLoad.Ready(asOf, boards)
        }
        rememberBuys(slice.checks)
    }

    /** The first time a name is a buy today, keep its limit, stop, and target. */
    private fun rememberBuys(checks: List<DipCheck>) {
        val day = tradingDay(LocalDate.now(ZoneId.of("Asia/Kolkata"))).toEpochDay()
        val buys = checks.filter {
            it.suggestion == DipSuggestion.BUY && it.limitPrice > 0.0 && it.stopPrice > 0.0 && it.targetPrice > 0.0
        }
        if (buys.isEmpty()) return
        viewModelScope.launch {
            buys.forEach { dip ->
                repository.rememberBuy(dip.symbol, day, dip.limitPrice, dip.stopPrice, dip.targetPrice)
            }
        }
    }

    fun setChartSpan(span: ChartSpan) {
        if (span == chartSpan.value) return
        tickers.setChartSpan(span)
        chartReady = false
        forcedChart.value = null
        chartSpan.value = span
    }

    fun addFromBank(amount: Double) {
        account.add(amount)
    }

    fun returnToBank(amount: Double) {
        account.take(amount)
    }

    val load: StateFlow<JournalLoad> = repository.observeAll()
        .map<List<Prediction>, JournalLoad> { JournalLoad.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalLoad.Loading)

    private val spotPrices = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val livePrices = MutableStateFlow(false)
    private var priceJob: Job? = null
    private var pricedAt = 0L
    private var bypassCache = false

    /** Latest public price for each unsold stock name. Empty until Prices is on. */
    val nowPrices: StateFlow<Map<String, Double>> = spotPrices

    /** Prices stay off until the user asks. While on, they refresh every 15 seconds. */
    val pricesLive: StateFlow<Boolean> = livePrices

    private val shownPrice = MutableStateFlow<Pair<String, Double>?>(null)
    private var shownJob: Job? = null

    /** Latest price for the open chart, refreshed while that chart stays on screen. */
    val chartSpot: StateFlow<Pair<String, Double>?> = shownPrice

    init {
        viewModelScope.launch {
            var quoted = emptySet<String>()
            combine(load, livePrices) { state, on -> on to openNames(state).toSet() }
                .collect { (on, names) ->
                    if (!on) {
                        quoted = names
                        return@collect
                    }
                    if (names == quoted && names.all { it in spotPrices.value }) return@collect
                    quoted = names
                    refreshOpenPrices()
                }
        }
    }

    fun toggleLivePrices() {
        val next = !livePrices.value
        if (next) bypassCache = true
        livePrices.value = next
        if (!next) {
            priceJob?.cancel()
            spotPrices.value = emptyMap()
            pricedAt = 0L
        }
    }

    /** Fetch unsold names. Does nothing while Prices is off. `fresh` skips the short cache. */
    fun refreshOpenPrices(fresh: Boolean = false) {
        if (!livePrices.value) return
        val names = openNames(load.value)
        if (names.isEmpty()) return
        val now = System.currentTimeMillis()
        val allowCache = !fresh && !bypassCache
        bypassCache = false
        if (!fresh && allowCache && now - pricedAt < 2_000L) return
        pricedAt = now
        priceJob?.cancel()
        priceJob = viewModelScope.launch {
            val next = pricesFor(names, allowCache)
            if (!livePrices.value) return@launch
            val keep = names.toSet()
            spotPrices.value = spotPrices.value.filterKeys { it in keep } + next
        }
    }

    /** One price for the chart that is open. Skips the candle download. */
    fun refreshShownPrice() {
        val name = chartQuery.value
        if (name.isBlank()) return
        shownJob?.cancel()
        shownJob = viewModelScope.launch {
            val quote = withContext(Dispatchers.IO) {
                PublicQuotes.spot(name, tickers.get(name), allowCache = false)
            } ?: return@launch
            if (chartQuery.value != name) return@launch
            tickers.save(name, quote.symbol)
            shownPrice.value = name to quote.price
        }
    }

    private fun openNames(state: JournalLoad): List<String> =
        (state as? JournalLoad.Ready)?.predictions
            ?.filter { it.judgement().kind == ResultKind.OPEN }
            ?.map { it.symbol.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()

    private fun loadNamed(name: String, span: ChartSpan? = null): ChartLoad {
        val result = if (span == null) {
            PublicQuotes.load(name, tickers.get(name))
        } else {
            PublicQuotes.load(name, tickers.get(name), span.yahooRange, span.limit)
        }
        if (result is ChartLoad.Ready) tickers.save(name, result.chart.symbol)
        return result
    }

    private suspend fun pricesFor(names: List<String>, allowCache: Boolean): Map<String, Double> {
        if (names.isEmpty()) return emptyMap()
        return coroutineScope {
            names.map { name ->
                async(Dispatchers.IO) {
                    val quote = PublicQuotes.spot(name, tickers.get(name), allowCache) ?: return@async null
                    tickers.save(name, quote.symbol)
                    name to quote.price
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private val capBadgeState = MutableStateFlow<Map<String, CapSize>>(emptyMap())

    /** Large, mid, or small for a name that is not a skip. */
    val capBadges: StateFlow<Map<String, CapSize>> = capBadgeState

    /** Buys the checklist wrote down, including names that were not saved. */
    val buyScores: StateFlow<List<BuyScore>> = repository.observeScores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val raw = savedState.get<Long>("day") ?: LocalDate.now().toEpochDay()
        val date = LocalDate.ofEpochDay(raw)
        if (!isMarketWeekday(date.dayOfWeek)) savedState["day"] = tradingDay(date).toEpochDay()
        viewModelScope.launch {
            while (isActive) {
                watchPlans()
                scoreBuys()
                delay(60_000)
            }
        }
    }

    /** Refresh cap badges, then buy a plan only when the check is still a buy. */
    private suspend fun watchPlans() {
        val all = repository.listAll()
        val reads = withContext(Dispatchers.IO) { PublicQuotes.listedReads(all.map { it.symbol }) }
        capBadgeState.value = reads.filterValues { showsCapBadge(it.suggestion) }.mapValues { it.value.cap }
        val plans = all.filter { it.notStarted && it.mark == null }
            .sortedWith(compareBy<Prediction> { it.epochDay }.thenBy { it.createdAt })
        if (plans.isEmpty()) return
        val now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
        var free = availableCash(account.observe().value, insightsOf(all))
        for (call in plans) {
            val read: ListedRead? = reads[call.symbol.trim().uppercase()]
            val low = withContext(Dispatchers.IO) { PublicQuotes.lowSince(call.symbol, call.createdAt) }
            val next = settleNotStarted(
                call,
                now.toLocalDate(),
                now.toLocalTime(),
                low,
                free,
                clearBuy = allowsAutoBuy(read?.suggestion),
            ) ?: continue
            repository.save(next)
            if (!next.notStarted) free -= next.capital
        }
    }

    /** Mark a buy from the session after it was written down. */
    private suspend fun scoreBuys() {
        val now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"))
        val today = now.toLocalDate().toEpochDay()
        for (buy in repository.unscoredBuys()) {
            val session = nextSessionDay(buy.epochDay)
            if (session.toEpochDay() > today) continue
            val range = withContext(Dispatchers.IO) {
                PublicQuotes.sessionRange(buy.symbol, session.toEpochDay())
            } ?: continue
            val outcome = scoreSession(
                buy.stopPrice,
                buy.targetPrice,
                range.first,
                range.second,
                session.toEpochDay(),
                today,
                now.toLocalTime(),
            ) ?: continue
            repository.setOutcome(buy.id, outcome)
        }
    }

    val selectedEpochDay = savedState.getStateFlow("day", tradingDay(LocalDate.now()).toEpochDay())

    val journalFilter: StateFlow<JournalFilter> = savedState.getStateFlow("filter", JournalFilter.ALL.name)
        .map { name -> runCatching { JournalFilter.valueOf(name) }.getOrDefault(JournalFilter.ALL) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalFilter.ALL)

    fun shiftDay(days: Long) {
        val current = LocalDate.ofEpochDay(selectedEpochDay.value)
        savedState["day"] = shiftTradingDay(current, days).toEpochDay()
    }

    fun jumpToToday() {
        savedState["day"] = tradingDay(LocalDate.now()).toEpochDay()
    }

    fun selectDay(epochDay: Long) {
        savedState["day"] = epochDay
    }

    fun setFilter(filter: JournalFilter) {
        savedState["filter"] = filter.name
    }

    fun persist(prediction: Prediction) {
        viewModelScope.launch { repository.save(prediction) }
    }

    fun saveOutcome(id: Long, actualPrice: Double?, mark: Mark?, manualPnl: Double?) {
        viewModelScope.launch {
            val current = repository.get(id) ?: return@launch
            val next = current.copy(actualPrice = actualPrice, mark = mark, manualPnl = manualPnl)
            repository.save(next)
        }
    }

    fun reopen(id: Long) {
        viewModelScope.launch {
            val current = repository.get(id) ?: return@launch
            repository.save(current.copy(actualPrice = null, mark = null, manualPnl = null))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        fun factory(
            repository: PredictionRepository,
            account: AccountStore,
            tickers: TickerStore,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return JournalViewModel(repository, account, tickers, extras.createSavedStateHandle()) as T
                }
            }
    }
}
