package com.callbook.journal.data

import com.callbook.journal.domain.Candle
import com.callbook.journal.domain.ChartLoad
import com.callbook.journal.domain.ListedStock
import com.callbook.journal.domain.LosersLoad
import com.callbook.journal.domain.NewsLoad
import com.callbook.journal.domain.CapDips
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.DEEP_DIP_PERCENT
import com.callbook.journal.domain.DayLoser
import com.callbook.journal.domain.HeadlineRead
import com.callbook.journal.domain.LoserBoard
import com.callbook.journal.domain.advisePause
import com.callbook.journal.domain.adviseDip
import com.callbook.journal.domain.isMildPause
import com.callbook.journal.domain.parseIndexQuotes
import com.callbook.journal.domain.risingPause
import com.callbook.journal.domain.indexTimestamp
import com.callbook.journal.domain.parseIndexLosers
import com.callbook.journal.domain.parseIndexMove
import com.callbook.journal.domain.parseLoserBoards
import com.callbook.journal.domain.PriceChart
import com.callbook.journal.domain.StockHeadline
import com.callbook.journal.domain.pickListedEquity
import com.callbook.journal.domain.publisherLink
import com.callbook.journal.domain.readHeadline
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

data class SpotQuote(val symbol: String, val price: Double)

data class StrategySlice(
    val asOf: String,
    val marketPercent: Double,
    val checks: List<com.callbook.journal.domain.DipCheck>,
)

/** Daily candles from the public Yahoo price feed. NSE stocks, then BSE. */
object PublicQuotes {
    private const val CHART_FRESH_MS = 45_000L
    private const val SPOT_FRESH_MS = 10_000L
    private const val STRATEGY_NEWS_MS = 120_000L
    private const val INDEX_FRESH_MS = 45_000L
    private const val COOKIE_FRESH_MS = 300_000L

    private val tickers = java.util.concurrent.ConcurrentHashMap<String, ListedStock>()
    private val readyByName = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, ChartLoad.Ready>>()
    private val spotBySymbol = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Double>>()
    private val newsByName = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, NewsLoad.Ready>>()
    private val articleByLink = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var losersCache: Pair<Long, LosersLoad.Ready>? = null
    private val indexBodies = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
    private var nseCookie: Pair<Long, String>? = null

    fun peek(query: String, range: String): ChartLoad.Ready? {
        val hit = readyByName[cacheKey(query, range)] ?: return null
        if (System.currentTimeMillis() - hit.first > CHART_FRESH_MS) return null
        return hit.second
    }

    /** Latest price only. Skips the candle history used by the chart. */
    fun spot(query: String, knownTicker: String? = null, allowCache: Boolean = true): SpotQuote? {
        val name = query.trim()
        if (name.isEmpty()) return null
        if (!knownTicker.isNullOrBlank()) {
            priceOnly(knownTicker, allowCache)?.let { return SpotQuote(knownTicker, it) }
        }
        val hit = tickers[name.uppercase()] ?: runCatching { find(name) }.getOrNull() ?: return null
        tickers[name.uppercase()] = hit
        val price = priceOnly(hit.symbol, allowCache) ?: return null
        return SpotQuote(hit.symbol, price)
    }

    /**
     * One cap and one list. The morning alert asks only for large dips,
     * so mid, small, and pause are not fetched until that tab is opened.
     */
    fun scanBoard(cap: CapSize, dips: Boolean, fresh: Boolean = false): StrategySlice? {
        val cookies = cachedCookies()
        val body = cachedIndex(cap, cookies, fresh) ?: return null
        val market = parseIndexMove(body) ?: 0.0
        val quotes = parseIndexQuotes(body)
        val checks = if (dips) {
            quotes
                .filter { it.changePercent <= DEEP_DIP_PERCENT }
                .sortedBy { it.changePercent }
                .take(4)
                .parallelMap { quote -> adviseOne(quote, market) }
        } else {
            quotes
                .filter { isMildPause(it.changePercent, it.monthChange) }
                .sortedByDescending { it.monthChange }
                .take(6)
                .parallelMap { quote -> advisePauseQuote(quote, market) }
                .filterNotNull()
                .take(4)
        }
        return StrategySlice(indexTimestamp(body), market, checks)
    }

    /** Today's NSE losers. Cached for two minutes. One response holds every board. */
    fun dayLosers(fresh: Boolean = false): LosersLoad {
        val cached = losersCache
        val now = System.currentTimeMillis()
        if (!fresh && cached != null && now - cached.first < 120_000L) return cached.second
        val loaded = runCatching { fetchLosers() }.getOrElse {
            LosersLoad.Unavailable("Loser list is offline")
        }
        if (loaded is LosersLoad.Ready) losersCache = System.currentTimeMillis() to loaded
        return loaded
    }

    /** A few recent headlines. The stock page keeps them for ten minutes. Strategy asks sooner. */
    fun headlines(query: String, maxAgeMs: Long = 600_000L): NewsLoad {
        val name = query.trim()
        if (name.isEmpty()) return NewsLoad.Unavailable("Type a stock name")
        val key = name.uppercase()
        val cached = newsByName[key]
        if (cached != null && System.currentTimeMillis() - cached.first < maxAgeMs) return cached.second
        val loaded = runCatching { fetchHeadlines(name) }.getOrElse {
            NewsLoad.Unavailable("News is offline")
        }
        if (loaded is NewsLoad.Ready) newsByName[key] = System.currentTimeMillis() to loaded
        return loaded
    }

    /** The publisher page for a headline. Google News wrappers are resolved once and cached. */
    fun articleUrl(link: String): String {
        val cached = articleByLink[link]
        if (cached != null) return cached
        val resolved = runCatching { resolveArticle(link) }.getOrNull()?.takeIf { it.startsWith("http") } ?: link
        articleByLink[link] = resolved
        return resolved
    }

    private fun resolveArticle(link: String): String {
        if (!link.contains("news.google.com/rss/articles/")) return link
        val html = get(link, accept = "text/html")
        val id = htmlAttr(html, "data-n-a-id") ?: return link
        val stamp = htmlAttr(html, "data-n-a-ts") ?: return link
        val signature = htmlAttr(html, "data-n-a-sg") ?: return link
        val inner = buildString {
            append("""["garturlreq",[["X","X",["X","X"],null,null,1,1,"US:en",null,1,null,null,null,null,null,0,1],"X","X",1,[1,1,1],1,1,null,0,0,null,0],"""")
            append(id)
            append("\",")
            append(stamp)
            append(",\"")
            append(signature)
            append("\"]")
        }
        val form = "f.req=" + URLEncoder.encode("""[[["Fbv4je",${jsonString(inner)},null,"generic"]]]""", Charsets.UTF_8.name())
        val body = post(
            url = "https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je",
            form = form,
            referer = "https://news.google.com/",
        )
        return publisherLink(body) ?: link
    }

    fun load(query: String, knownTicker: String? = null, range: String = "5d", limit: Int = 7): ChartLoad {
        val name = query.trim()
        if (name.isEmpty()) return ChartLoad.Unavailable("Type a stock name")
        if (!knownTicker.isNullOrBlank()) {
            val saved = runCatching { listed(knownTicker, name, range, limit) }.getOrNull()
            if (saved != null) return store(name, range, saved)
        }
        val hit = tickers[name.uppercase()] ?: run {
            val found = runCatching { find(name) }.getOrElse {
                return ChartLoad.Unavailable("Chart is offline")
            } ?: return ChartLoad.Unavailable("No public stock for this name")
            tickers[name.uppercase()] = found
            found
        }
        val loaded = runCatching { listed(hit.symbol, hit.name.ifBlank { name }, range, limit) }.getOrElse {
            return ChartLoad.Unavailable("Chart is offline")
        } ?: ChartLoad.Unavailable("No public prices for ${hit.symbol}")
        return store(name, range, loaded)
    }

    private fun store(name: String, range: String, loaded: ChartLoad): ChartLoad {
        if (loaded is ChartLoad.Ready) {
            val now = System.currentTimeMillis()
            readyByName[cacheKey(name, range)] = now to loaded
            spotBySymbol[loaded.chart.symbol] = now to loaded.chart.price
        }
        return loaded
    }

    private fun cacheKey(query: String, range: String) = "${query.trim().uppercase()}|$range"

    private fun priceOnly(symbol: String, allowCache: Boolean): Double? {
        val cached = spotBySymbol[symbol]
        val now = System.currentTimeMillis()
        if (allowCache && cached != null && now - cached.first < SPOT_FRESH_MS) return cached.second
        val price = runCatching {
            val body = get("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=1d")
            val market = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                .getJSONObject("meta").optDouble("regularMarketPrice")
            market.takeIf { it.isFinite() && it > 0.0 }
        }.getOrNull() ?: return null
        spotBySymbol[symbol] = now to price
        return price
    }

    private fun listed(symbol: String, name: String, range: String, limit: Int): ChartLoad? {
        val series = series(symbol, range, limit)
        if (series.candles.size < 2 || series.price <= 0.0) return null
        return ChartLoad.Ready(PriceChart(symbol, name, series.price, series.candles))
    }

    private fun find(query: String): ListedStock? {
        val q = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = get("https://query1.finance.yahoo.com/v1/finance/search?q=$q&quotesCount=8")
        val quotes = JSONObject(body).optJSONArray("quotes") ?: return null
        val hits = buildList {
            for (i in 0 until quotes.length()) {
                val quote = quotes.optJSONObject(i) ?: continue
                add(
                    ListedStock(
                        symbol = quote.optString("symbol"),
                        name = quote.optString("shortname"),
                        kind = quote.optString("quoteType"),
                    ),
                )
            }
        }
        return pickListedEquity(hits)
    }

    private data class Series(val price: Double, val candles: List<Candle>)

    private fun series(symbol: String, range: String, limit: Int): Series {
        val body = get("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=$range")
        val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val quote = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val open = quote.getJSONArray("open")
        val high = quote.getJSONArray("high")
        val low = quote.getJSONArray("low")
        val close = quote.getJSONArray("close")
        val count = minOf(open.length(), high.length(), low.length(), close.length())
        val candles = buildList {
            for (i in 0 until count) {
                val o = open.numberAt(i) ?: continue
                val h = high.numberAt(i) ?: continue
                val l = low.numberAt(i) ?: continue
                val c = close.numberAt(i) ?: continue
                add(Candle(o, h, l, c))
            }
        }.takeLast(limit)
        val market = result.getJSONObject("meta").optDouble("regularMarketPrice")
        val price = if (market.isFinite() && market > 0.0) market else candles.lastOrNull()?.close ?: 0.0
        return Series(price, candles)
    }

    private fun parseNewsRss(xml: String): List<StockHeadline> {
        val items = Regex("(?s)<item>(.*?)</item>").findAll(xml)
        return items.mapNotNull { match ->
            val block = match.groupValues[1]
            val rawTitle = xmlText(block, "title")
            val link = xmlText(block, "link")
            if (rawTitle.isBlank() || link.isBlank()) return@mapNotNull null
            val source = xmlText(block, "source").ifBlank { "News" }
            val title = rawTitle.removeSuffix(" - $source").trim()
            val published = runCatching {
                OffsetDateTime.parse(xmlText(block, "pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(0L)
            StockHeadline(title, source, link, published, readHeadline(title))
        }.toList()
    }

    private fun xmlText(block: String, tag: String): String {
        val raw = Regex("<$tag[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(block)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        return raw
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    private fun fetchLosers(): LosersLoad {
        val cookies = runCatching { nseCookies() }.getOrDefault("")
        val listed = runCatching {
            val body = get(
                url = "https://www.nseindia.com/api/live-analysis-variations?index=loosers",
                accept = "application/json",
                referer = "https://www.nseindia.com/",
                cookie = cookies,
            )
            parseLoserBoards(body)
        }.getOrNull()
        val largeBody = runCatching { indexBody("NIFTY 100", cookies) }.getOrNull()
        val midBody = runCatching { indexBody("NIFTY MIDCAP 150", cookies) }.getOrNull()
        val boards = linkedMapOf<LoserBoard, List<DayLoser>>()
        boards[LoserBoard.LARGE] = largeBody?.let(::parseIndexLosers).orEmpty()
        boards[LoserBoard.MID] = midBody?.let(::parseIndexLosers).orEmpty()
        val ready = listed as? LosersLoad.Ready
        LoserBoard.entries.filter { it.variationKey != null }.forEach { board ->
            boards[board] = ready?.boards?.get(board).orEmpty()
        }
        if (boards.values.all { it.isEmpty() }) return LosersLoad.Unavailable("Loser list is offline")
        val asOf = ready?.asOf?.takeIf { it.isNotBlank() }
            ?: largeBody?.let(::indexTimestamp)?.takeIf { it.isNotBlank() }
            ?: midBody?.let(::indexTimestamp).orEmpty()
        return LosersLoad.Ready(asOf, boards)
    }

    private fun cachedCookies(): String {
        val hit = nseCookie
        val now = System.currentTimeMillis()
        if (hit != null && now - hit.first < COOKIE_FRESH_MS) return hit.second
        val value = runCatching { nseCookies() }.getOrDefault("")
        nseCookie = now to value
        return value
    }

    private fun cachedIndex(cap: CapSize, cookies: String, fresh: Boolean): String? {
        val hit = indexBodies[cap.index]
        val now = System.currentTimeMillis()
        if (!fresh && hit != null && now - hit.first < INDEX_FRESH_MS) return hit.second
        val body = runCatching { indexBody(cap.index, cookies) }.getOrNull()
        if (body != null) {
            indexBodies[cap.index] = now to body
            return body
        }
        return hit?.second
    }

    private fun advisePauseQuote(
        quote: com.callbook.journal.domain.IndexQuote,
        market: Double,
    ): com.callbook.journal.domain.DipCheck? {
        val bars = runCatching { series("${quote.symbol}.NS", "5d", 6).candles }.getOrNull() ?: return null
        val lows = bars.map { it.low }
        val closes = bars.map { it.close }
        if (!risingPause(lows, closes)) return null
        val news = runCatching { headlines(quote.symbol, STRATEGY_NEWS_MS) }.getOrNull()
        val stories = (news as? NewsLoad.Ready)?.items.orEmpty()
        val reads: List<HeadlineRead> = stories.map { it.read }
        return advisePause(
            quote.symbol,
            quote.price,
            quote.changePercent,
            closes,
            bars.map { it.high },
            reads,
            market,
            quote.dayLow,
            quote.previousClose,
            stories = stories,
            monthChange = quote.monthChange,
        )
    }

    private fun adviseOne(
        quote: com.callbook.journal.domain.IndexQuote,
        market: Double,
    ): com.callbook.journal.domain.DipCheck {
        val closes = runCatching { series("${quote.symbol}.NS", "5d", 6).candles.map { it.close } }
            .getOrDefault(emptyList())
        val news = runCatching { headlines(quote.symbol, STRATEGY_NEWS_MS) }.getOrNull()
        val stories = (news as? NewsLoad.Ready)?.items.orEmpty()
        val reads: List<HeadlineRead> = stories.map { it.read }
        return adviseDip(
            quote.symbol,
            quote.price,
            quote.changePercent,
            closes,
            reads,
            market,
            quote.dayLow,
            quote.previousClose,
            stories = stories,
            monthChange = quote.monthChange,
        )
    }

    private fun indexBody(index: String, cookies: String): String {
        val name = URLEncoder.encode(index, Charsets.UTF_8.name())
        return get(
            url = "https://www.nseindia.com/api/equity-stock-indices?index=$name",
            accept = "application/json",
            referer = "https://www.nseindia.com/",
            cookie = cookies,
        )
    }

    private fun nseCookies(): String {
        val connection = (URL("https://www.nseindia.com/").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Accept", "text/html")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.close()
            connection.headerFields["Set-Cookie"].orEmpty()
                .map { it.substringBefore(';').trim() }
                .filter { it.contains('=') }
                .joinToString("; ")
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchHeadlines(name: String): NewsLoad {
        val q = URLEncoder.encode("$name when:14d", Charsets.UTF_8.name())
        val body = get("https://news.google.com/rss/search?q=$q&hl=en-IN&gl=IN&ceid=IN:en")
        val items = parseNewsRss(body).take(6)
        if (items.isEmpty()) return NewsLoad.Unavailable("No news for this name")
        return NewsLoad.Ready(items)
    }

    private fun get(
        url: String,
        accept: String = "application/json",
        referer: String? = null,
        cookie: String? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Accept", accept)
            if (!referer.isNullOrBlank()) setRequestProperty("Referer", referer)
            if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
        }
        try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                error("HTTP ${connection.responseCode}")
            }
            return text
        } catch (failure: Throwable) {
            connection.disconnect()
            throw failure
        }
    }

    private fun post(url: String, form: String, referer: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            setRequestProperty("Referer", referer)
        }
        try {
            connection.outputStream.use { it.write(form.toByteArray()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                error("HTTP ${connection.responseCode}")
            }
            return text
        } catch (failure: Throwable) {
            connection.disconnect()
            throw failure
        }
    }
}

private fun <T, R> List<T>.parallelMap(block: (T) -> R): List<R> {
    if (isEmpty()) return emptyList()
    return runBlocking(Dispatchers.IO) {
        map { item -> async { block(item) } }.awaitAll()
    }
}

private fun htmlAttr(html: String, name: String): String? =
    Regex("""$name="([^"]+)"""").find(html)?.groupValues?.get(1)

private fun jsonString(value: String): String = buildString {
    append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            else -> append(ch)
        }
    }
    append('"')
}

private fun org.json.JSONArray.numberAt(index: Int): Double? {
    if (isNull(index)) return null
    val value = optDouble(index)
    return value.takeIf { it.isFinite() }
}
