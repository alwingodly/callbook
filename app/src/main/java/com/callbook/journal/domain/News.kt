package com.callbook.journal.domain

enum class HeadlineTone(val label: String) {
    POSITIVE("Positive"),
    NEGATIVE("Negative"),
    MIXED("Mixed"),
}

data class HeadlineRead(val tone: HeadlineTone, val percent: Int)

data class StockHeadline(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: Long,
    val read: HeadlineRead,
)

sealed interface NewsLoad {
    data object Idle : NewsLoad
    data object Loading : NewsLoad
    data class Ready(val items: List<StockHeadline>) : NewsLoad
    data class Unavailable(val message: String) : NewsLoad
}

private val positiveWords = setOf(
    "profit", "profits", "growth", "rise", "rises", "rose", "gain", "gains",
    "upgrade", "beat", "beats", "record", "surge", "surges", "rally", "higher",
    "jump", "jumps", "boost", "order", "orders", "contract", "win", "wins",
    "strong", "expands", "dividend", "approval", "approved", "buyback", "bonus",
)

private val negativeWords = setOf(
    "loss", "losses", "fall", "falls", "fell", "drop", "drops", "downgrade",
    "miss", "misses", "weak", "fraud", "probe", "decline", "slump", "cut", "cuts",
    "lower", "lawsuit", "default", "plunge", "plunges", "plunged", "crash", "warning", "warn", "delay",
    "tanks", "tanked", "slumps", "tumbles", "tumbled",
    "resigns", "resignation", "penalty", "fine", "slips", "slid", "sink", "sinks",
)

private val negations = setOf("no", "not", "never", "without")

/** How the headline sounds. This is not the chance the price rises. */
fun readHeadline(title: String): HeadlineRead {
    val words = title.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    var positive = 0
    var negative = 0
    var negate = false
    for (word in words) {
        if (word in negations) {
            negate = true
            continue
        }
        val good = word in positiveWords
        val bad = word in negativeWords
        if (good || bad) {
            val countsAsGood = if (negate) bad else good
            if (countsAsGood) positive += 1 else negative += 1
        }
        negate = false
    }
    if (positive + negative == 0 || positive == negative) return HeadlineRead(HeadlineTone.MIXED, 50)
    val side = maxOf(positive, negative)
    val percent = (50 + side * 20).coerceAtMost(90)
    val tone = if (positive > negative) HeadlineTone.POSITIVE else HeadlineTone.NEGATIVE
    return HeadlineRead(tone, percent)
}

/** The publisher address inside a Google News resolve response. */
fun publisherLink(body: String): String? {
    val text = body.replace("\\/", "/")
    return Regex("https://[^\\s\"\\\\]+")
        .findAll(text)
        .map { it.value.trimEnd('\\', ',', ']') }
        .firstOrNull { "news.google." !in it && "gstatic." !in it && "googleusercontent." !in it }
}

fun newsAge(publishedAt: Long, now: Long = System.currentTimeMillis()): String {
    if (publishedAt <= 0L) return ""
    val minutes = ((now - publishedAt) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m"
        minutes < 60L * 24L -> "${minutes / 60L}h"
        else -> "${minutes / (60L * 24L)}d"
    }
}
