package com.callbook.journal.domain

enum class LoserBoard(val label: String, val variationKey: String? = null) {
    LARGE("Large cap"),
    MID("Mid cap"),
    ABOVE_20("Above ₹20", "SecGtr20"),
    FUTURES("F&O", "FOSec"),
    NIFTY("Nifty", "NIFTY"),
}

data class DayLoser(
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val previousClose: Double,
    val volume: String,
    val dayLow: Double = 0.0,
)

sealed interface LosersLoad {
    data object Idle : LosersLoad
    data object Loading : LosersLoad
    data class Ready(
        val asOf: String,
        val boards: Map<LoserBoard, List<DayLoser>>,
    ) : LosersLoad
    data class Unavailable(val message: String) : LosersLoad
}

/** NSE's public loser boards. The percent is today's move, steepest fall first. */
fun parseLoserBoards(json: String): LosersLoad {
    val boards = LoserBoard.entries.mapNotNull { board ->
        val key = board.variationKey ?: return@mapNotNull null
        board to losersIn(json, key)
    }.toMap()
    if (boards.values.all { it.isEmpty() }) return LosersLoad.Unavailable("No losers right now")
    val asOf = LoserBoard.entries.firstNotNullOfOrNull { board ->
        val key = board.variationKey ?: return@firstNotNullOfOrNull null
        val block = jsonObject(json, key) ?: return@firstNotNullOfOrNull null
        jsonString(block, "timestamp").takeIf { it.isNotBlank() }
    }.orEmpty()
    return LosersLoad.Ready(asOf, boards)
}

/**
 * Stocks in an NSE index quote, falling names only, steepest first.
 * Large cap is Nifty 100. Mid cap is Nifty Midcap 150.
 */
fun parseIndexLosers(json: String, limit: Int = 20): List<DayLoser> {
    val data = jsonArray(json, "data") ?: return emptyList()
    return jsonObjects(data).mapNotNull { row ->
        val symbol = jsonString(row, "symbol").trim()
        val price = jsonNumber(row, "lastPrice")
        val change = jsonNumber(row, "pChange")
        if (symbol.isEmpty() || symbol.any { it.isWhitespace() }) return@mapNotNull null
        if (price == null || price <= 0.0 || change == null || change >= 0.0) return@mapNotNull null
        DayLoser(
            symbol = symbol,
            price = price,
            changePercent = change,
            previousClose = jsonNumber(row, "previousClose") ?: 0.0,
            volume = compactCount(jsonNumber(row, "totalTradedVolume")),
            dayLow = jsonNumber(row, "dayLow") ?: 0.0,
        )
    }.sortedBy { it.changePercent }.take(limit)
}

data class IndexQuote(
    val symbol: String,
    val price: Double,
    val changePercent: Double,
    val previousClose: Double,
    val dayLow: Double,
    val dayHigh: Double,
    val monthChange: Double,
)

fun parseIndexQuotes(json: String): List<IndexQuote> {
    val data = jsonArray(json, "data") ?: return emptyList()
    return jsonObjects(data).mapNotNull { row ->
        val symbol = jsonString(row, "symbol").trim()
        val price = jsonNumber(row, "lastPrice")
        val change = jsonNumber(row, "pChange")
        if (symbol.isEmpty() || symbol.any { it.isWhitespace() }) return@mapNotNull null
        if (price == null || price <= 0.0 || change == null) return@mapNotNull null
        IndexQuote(
            symbol = symbol,
            price = price,
            changePercent = change,
            previousClose = jsonNumber(row, "previousClose") ?: 0.0,
            dayLow = jsonNumber(row, "dayLow") ?: 0.0,
            dayHigh = jsonNumber(row, "dayHigh") ?: 0.0,
            monthChange = jsonNumber(row, "perChange30d") ?: 0.0,
        )
    }
}

fun indexTimestamp(json: String): String = jsonString(json, "timestamp")

/** Move of the index row itself, such as Nifty 100. */
fun parseIndexMove(json: String): Double? {
    val data = jsonArray(json, "data") ?: return null
    return jsonObjects(data).firstNotNullOfOrNull { row ->
        val symbol = jsonString(row, "symbol")
        if (!symbol.any { it.isWhitespace() }) return@firstNotNullOfOrNull null
        jsonNumber(row, "pChange")
    }
}

private fun compactCount(volume: Double?): String {
    if (volume == null || volume <= 0.0) return ""
    return when {
        volume >= 1_00_00_000.0 -> String.format(java.util.Locale.US, "%.2f Cr", volume / 1_00_00_000.0)
        volume >= 1_00_000.0 -> String.format(java.util.Locale.US, "%.2f L", volume / 1_00_000.0)
        volume >= 1_000.0 -> String.format(java.util.Locale.US, "%.2f K", volume / 1_000.0)
        else -> volume.toLong().toString()
    }
}

private fun losersIn(json: String, key: String): List<DayLoser> {
    val block = jsonObject(json, key) ?: return emptyList()
    val data = jsonArray(block, "data") ?: return emptyList()
    return jsonObjects(data).mapNotNull { row ->
        val symbol = jsonString(row, "symbol").trim()
        val price = jsonNumber(row, "ltp")
        val change = jsonNumber(row, "perChange")
        if (symbol.isEmpty() || price == null || price <= 0.0 || change == null) return@mapNotNull null
        DayLoser(
            symbol = symbol,
            price = price,
            changePercent = change,
            previousClose = jsonNumber(row, "prev_price") ?: 0.0,
            volume = jsonString(row, "trade_quantity").trim(),
        )
    }.sortedBy { it.changePercent }
}

private fun jsonObject(text: String, key: String): String? {
    val keyAt = propertyAt(text, key)
    if (keyAt < 0) return null
    val open = text.indexOf('{', keyAt)
    if (open < 0) return null
    return balanced(text, open, '{', '}')
}

private fun jsonArray(text: String, key: String): String? {
    val keyAt = propertyAt(text, key)
    if (keyAt < 0) return null
    val open = text.indexOf('[', keyAt)
    if (open < 0) return null
    return balanced(text, open, '[', ']')
}

private fun propertyAt(text: String, key: String): Int =
    Regex(""""$key"\s*:""").find(text)?.range?.first ?: -1

private fun jsonObjects(array: String): List<String> {
    val body = array.removeSurrounding("[", "]")
    val rows = mutableListOf<String>()
    var index = 0
    while (index < body.length) {
        val open = body.indexOf('{', index)
        if (open < 0) break
        val row = balanced(body, open, '{', '}') ?: break
        rows += row
        index = open + row.length
    }
    return rows
}

private fun jsonString(text: String, key: String): String =
    Regex(""""$key"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1).orEmpty()

private fun jsonNumber(text: String, key: String): Double? =
    Regex(""""$key"\s*:\s*(-?\d+(?:\.\d+)?)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()

private fun balanced(text: String, open: Int, left: Char, right: Char): String? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in open until text.length) {
        val char = text[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            left -> depth += 1
            right -> {
                depth -= 1
                if (depth == 0) return text.substring(open, index + 1)
            }
        }
    }
    return null
}
