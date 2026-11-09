package com.callbook.journal.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class Sector(val label: String, val index: String, val newsQuery: String) {
    BANK("Banking", "NIFTY BANK", "Nifty Bank India"),
    IT("Information technology", "NIFTY IT", "Nifty IT India"),
    AUTO("Automobile", "NIFTY AUTO", "Nifty Auto India"),
    PHARMA("Pharmaceuticals", "NIFTY PHARMA", "Nifty Pharma India"),
    METAL("Metals", "NIFTY METAL", "Nifty Metal India"),
    FMCG("Consumer goods", "NIFTY FMCG", "Nifty FMCG India"),
    ENERGY("Power and energy", "NIFTY ENERGY", "Nifty Energy India"),
    REALTY("Real estate", "NIFTY REALTY", "Nifty Realty India"),
}

data class SectorStock(
    val symbol: String,
    val changePercent: Double,
    val cap: CapSize?,
)

data class SectorOutlook(
    val note: String,
    val headline: String,
    val source: String,
    val link: String,
    val publishedAt: Long,
)

data class SectorRead(
    val sector: Sector,
    val changePercent: Double,
    val stocks: List<SectorStock>,
    val outlook: SectorOutlook?,
    val headlines: List<StockHeadline> = emptyList(),
)

sealed interface SectorLoad {
    data object Idle : SectorLoad
    data object Loading : SectorLoad
    data class Ready(val asOf: String, val sectors: List<SectorRead>) : SectorLoad
    data class Unavailable(val message: String) : SectorLoad
}

/**
 * Keeps a headline when it is about the Indian market.
 * A story that names another market is left out, unless it also names India.
 */
fun fitsIndianMarket(title: String): Boolean {
    val words = title.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    fun has(vararg marks: String) = marks.any { it in words }
    fun phrase(left: String, right: String): Boolean {
        for (index in 0 until words.size - 1) {
            if (words[index] == left && words[index + 1] == right) return true
        }
        return false
    }
    val india = has("india", "indian", "nifty", "sensex", "nse", "bse", "rbi", "rupee", "mumbai", "sebi")
    if (india) return true
    val otherMarket = has(
        "nasdaq", "dow", "nyse", "fed", "nikkei", "china", "chinese",
        "europe", "european", "ftse",
    ) || phrase("wall", "street") || phrase("hang", "seng") || phrase("hong", "kong")
    return !otherMarket
}

/**
 * When the note applies. Thursday says tomorrow. Friday, Saturday, and Sunday say Monday,
 * because the cash market is closed on the weekend.
 */
fun nextSessionLabel(today: LocalDate): String {
    val anchor = if (isMarketWeekday(today.dayOfWeek)) today else tradingDay(today)
    val next = shiftTradingDay(anchor, 1)
    return if (next == today.plusDays(1)) {
        "tomorrow"
    } else {
        "on ${next.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
    }
}

/**
 * Why a segment may need care next session.
 * A major story on the segment counts, even on a flat day. A hard fall counts when the list is empty.
 */
fun sectorOutlook(
    changePercent: Double,
    headlines: List<StockHeadline>,
    session: String = "tomorrow",
): SectorOutlook? {
    val ranked = headlines.sortedByDescending { storyRank(it) }
    val major = ranked.firstOrNull { storyRank(it) >= 3 }
    if (major != null) {
        val note = segmentNote(wordsOf(major.title), session)
            ?: "Negative for this sector $session."
        return outlookOf(note, major)
    }
    val negatives = headlines.filter { it.read.tone == HeadlineTone.NEGATIVE }
    if (negatives.size >= 2 && changePercent < -0.05) {
        return outlookOf(
            "Several negative stories, and the sector is down. It may stay weak $session.",
            negatives.first(),
        )
    }
    if (changePercent <= -1.5 && negatives.isNotEmpty()) {
        return outlookOf("The fall matches this news. Caution $session.", negatives.first())
    }
    if (changePercent <= -2.0) {
        val into = if (session == "tomorrow") "tomorrow" else session.removePrefix("on ")
        return SectorOutlook(
            note = "A sharp fall today, with no headline. It may extend into $into.",
            headline = "",
            source = "",
            link = "",
            publishedAt = 0L,
        )
    }
    return null
}

private fun outlookOf(note: String, headline: StockHeadline) = SectorOutlook(
    note = note,
    headline = headline.title,
    source = headline.source,
    link = headline.link,
    publishedAt = headline.publishedAt,
)

private fun storyRank(headline: StockHeadline): Int {
    if (headline.read.tone == HeadlineTone.POSITIVE) return 0
    var rank = 0
    if (headline.read.tone == HeadlineTone.NEGATIVE && headline.read.percent >= 90) rank += 4
    else if (headline.read.tone == HeadlineTone.NEGATIVE) rank += 1
    if (segmentNote(wordsOf(headline.title)) != null) rank += 3
    return rank
}

private fun wordsOf(title: String): List<String> =
    title.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

private fun segmentNote(words: List<String>, session: String = "tomorrow"): String? {
    fun has(vararg marks: String) = marks.any { it in words }
    fun phrase(left: String, right: String): Boolean {
        for (index in 0 until words.size - 1) {
            if (words[index] == left && words[index + 1] == right) return true
        }
        return false
    }
    return when {
        has("rbi", "repo") || phrase("interest", "rate") || phrase("rate", "hike") ||
            phrase("rate", "cut") || phrase("repo", "rate") ->
            "Rates may move this sector $session."
        has("crude", "brent") || phrase("oil", "price") || phrase("oil", "prices") ->
            "Oil prices may move this sector $session."
        has("tariff", "sanction", "sanctions") || phrase("trade", "war") ->
            "Trade news may move this sector $session."
        has("gst", "budget", "regulation", "ban") ->
            "Policy news may move this sector $session."
        has("strike", "outage") ->
            "A disruption may move this sector $session."
        has("war") ->
            "A conflict may move this sector $session."
        else -> null
    }
}
