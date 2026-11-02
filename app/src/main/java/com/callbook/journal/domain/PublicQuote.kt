package com.callbook.journal.domain

enum class ChartSpan(val label: String, val yahooRange: String, val limit: Int) {
    WEEK("Week", "5d", 7),
    MONTH("1 month", "1mo", 23),
    QUARTER("3 months", "3mo", 70);

    companion object {
        fun fromName(name: String?): ChartSpan = entries.find { it.name == name } ?: QUARTER
    }
}

data class ListedStock(
    val symbol: String,
    val name: String,
    val kind: String,
)

data class Candle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

data class PriceChart(
    val symbol: String,
    val name: String,
    val price: Double,
    val candles: List<Candle>,
)

sealed interface ChartLoad {
    data object Idle : ChartLoad
    data object Loading : ChartLoad
    data class Ready(val chart: PriceChart) : ChartLoad
    data class Unavailable(val message: String) : ChartLoad
}

/** NSE equity first, then BSE. Skips funds and indexes. */
fun pickListedEquity(hits: List<ListedStock>): ListedStock? {
    val equities = hits.filter { it.kind.equals("EQUITY", ignoreCase = true) && it.symbol.isNotBlank() }
    return equities.firstOrNull { it.symbol.endsWith(".NS") }
        ?: equities.firstOrNull { it.symbol.endsWith(".BO") }
}
