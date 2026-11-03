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

private val subjects = setOf(
    "profit", "profits", "loss", "losses", "revenue", "sales", "income",
    "shares", "share", "stock", "price",
)

private val upVerbs = setOf(
    "rise", "rises", "rose", "jump", "jumps", "jumped", "surge", "surges", "surged",
    "grow", "grows", "grew", "gain", "gains", "beat", "beats", "climb", "climbs",
    "soar", "soars", "rebound", "rebounds", "higher",
)

private val downVerbs = setOf(
    "fall", "falls", "fell", "drop", "drops", "dropped", "tank", "tanks", "tanked",
    "slump", "slumps", "plunge", "plunges", "plunged", "decline", "declines", "declined",
    "slide", "slides", "slid", "tumble", "tumbles", "tumbled", "sink", "sinks", "sank",
    "slip", "slips", "crash", "crashes", "crashed",
)

private val reliefVerbs = setOf("narrow", "narrows", "narrowed", "shrink", "shrinks", "shrinked", "ease", "eases", "eased")

private val strongBad = setOf(
    "fraud", "probe", "default", "defaults", "defaulted", "bankruptcy", "insolvency",
    "scam", "raid", "resignation", "resigns", "resigned",
)

private val mildBad = setOf(
    "loss", "losses", "downgrade", "lawsuit", "penalty", "fine", "warning", "warn",
    "delay", "weak", "miss", "misses", "missed",
)

private val mildGood = setOf(
    "profit", "profits", "dividend", "upgrade", "approval", "approved", "bonus",
    "growth", "buyback", "gain", "gains", "rally", "boost", "expands", "expanded",
)

private val goodPhrases = listOf(
    listOf("bags", "order"),
    listOf("wins", "order"),
    listOf("wins", "contract"),
    listOf("order", "win"),
    listOf("share", "buyback"),
)

private val badPhrases = listOf(
    listOf("net", "loss"),
    listOf("posts", "loss"),
    listOf("reports", "loss"),
    listOf("guidance", "cut"),
    listOf("cuts", "guidance"),
    listOf("tax", "raid"),
    listOf("pledges", "shares"),
    listOf("shares", "pledged"),
)

private val negations = setOf("no", "not", "never", "without")

/**
 * How the headline sounds. A phrase such as "profit falls" is read together.
 * One harsh word, such as fraud, is enough. This is not the chance the price rises.
 */
fun readHeadline(title: String): HeadlineRead {
    val words = title.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return HeadlineRead(HeadlineTone.MIXED, 50)
    val used = BooleanArray(words.size)
    var good = 0
    var bad = 0
    fun flipped(index: Int): Boolean {
        var at = index - 1
        if (at >= 0 && words[at] == "a") at -= 1
        return at >= 0 && words[at] in negations && !used[at]
    }
    fun add(index: Int, weight: Int) {
        val signed = if (flipped(index)) -weight else weight
        if (signed > 0) good += signed else bad += -signed
    }
    fun takePhrase(phrase: List<String>, weight: Int) {
        if (phrase.size > words.size) return
        for (start in 0..words.size - phrase.size) {
            if ((0 until phrase.size).any { used[start + it] }) continue
            if ((0 until phrase.size).any { words[start + it] != phrase[it] }) continue
            add(start, weight)
            for (offset in phrase.indices) used[start + offset] = true
        }
    }
    fun takeDirection(window: Int, weightFor: (List<String>) -> Int?) {
        if (window > words.size) return
        for (start in 0..words.size - window) {
            val slice = words.subList(start, start + window)
            if (slice.indices.any { used[start + it] }) continue
            val weight = weightFor(slice) ?: continue
            val hit = slice.indices.filter { slice[it] in subjects || slice[it] in upVerbs || slice[it] in downVerbs || slice[it] in reliefVerbs }
            if (hit.isEmpty()) continue
            add(start + hit.first(), weight)
            hit.forEach { used[start + it] = true }
        }
    }
    fun direction(slice: List<String>): Int? {
        val hasSubject = slice.any { it in subjects }
        val hasDown = slice.any { it in downVerbs }
        val hasUp = slice.any { it in upVerbs }
        val hasLoss = slice.any { it == "loss" || it == "losses" }
        val hasRelief = slice.any { it in reliefVerbs }
        if (hasLoss && hasRelief && !hasDown) return 2
        if (hasSubject && hasDown && !hasUp) return -2
        if (hasSubject && hasUp && !hasDown) return 2
        return null
    }
    badPhrases.forEach { takePhrase(it, -2) }
    goodPhrases.forEach { takePhrase(it, 2) }
    takeDirection(3, ::direction)
    takeDirection(2, ::direction)
    words.forEachIndexed { index, word ->
        if (used[index]) return@forEachIndexed
        val weight = when (word) {
            in strongBad -> -2
            in downVerbs -> -1
            in mildBad -> -1
            in upVerbs -> 1
            in mildGood -> 1
            else -> 0
        }
        if (weight == 0) return@forEachIndexed
        add(index, weight)
        used[index] = true
    }
    if (good == 0 && bad == 0 || good == bad) return HeadlineRead(HeadlineTone.MIXED, 50)
    val tone = if (good > bad) HeadlineTone.POSITIVE else HeadlineTone.NEGATIVE
    val percent = if (maxOf(good, bad) >= 2) 90 else 70
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
