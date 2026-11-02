package com.callbook.journal.domain

import java.util.Locale
import kotlin.math.abs

/** An amount typed for To bank. Empty stays in the app. More than the banked cash stays put. */
fun bankReturnAmount(typed: Double?, banked: Double): Double? {
    if (typed == null || banked <= 0.5) return null
    if (typed > banked + 0.001) return null
    return typed
}

fun parseAmount(raw: String): Double? {
    val cleaned = raw
        .trim()
        .replace(",", "")
        .replace(" ", "")
        .removePrefix("Rs")
        .removePrefix("rs")
        .removePrefix("₹")
    if (cleaned.isEmpty() || cleaned == "." || cleaned == "-") return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value <= 0.0) return null
    return value
}

fun normalizeSymbol(raw: String): String = raw
    .uppercase(Locale.US)
    .trim()
    .replace(Regex("[^A-Z0-9&.\\- ]"), "")
    .replace(Regex(" +"), " ")
    .take(24)

private fun inrFormat(): java.text.NumberFormat =
    java.text.NumberFormat.getNumberInstance(
        Locale.Builder().setLanguage("en").setRegion("IN").build(),
    ).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

fun rupees(value: Double, signed: Boolean = false): String {
    val body = "₹${inrFormat().format(abs(value))}"
    return when {
        value < -0.005 -> "-$body"
        signed && value > 0.005 -> "+$body"
        else -> body
    }
}

fun priceText(value: Double): String = inrFormat().format(value)

fun fieldPrice(value: Double): String = String.format(Locale.US, "%.2f", value)

fun signedPercent(value: Double): String {
    val body = String.format(Locale.US, "%.2f%%", abs(value))
    return when {
        value > 0.0005 -> "+$body"
        value < -0.0005 -> "-$body"
        else -> body
    }
}

fun wholePercent(part: Int, whole: Int): Int {
    if (whole <= 0) return 0
    return (part * 100.0 / whole).let { kotlin.math.round(it).toInt() }
}

fun planWarning(
    direction: Direction,
    entry: Double,
    target: Double,
    stop: Double,
): String? = when (direction) {
    Direction.GAIN -> when {
        target <= entry -> "A gain call usually has a target above the price you see now."
        stop >= entry -> "A gain call usually has a stop below the price you see now."
        else -> null
    }
    Direction.LOSS -> when {
        target >= entry -> "A loss call usually has a target below the price you see now."
        stop <= entry -> "A loss call usually has a stop above the price you see now."
        else -> null
    }
}

fun riskReward(entry: Double, target: Double, stop: Double): Double? {
    val risk = abs(entry - stop)
    if (risk < 0.0001) return null
    return abs(target - entry) / risk
}

fun riskRewardLabel(entry: Double, target: Double, stop: Double): String? {
    val ratio = riskReward(entry, target, stop) ?: return null
    return "Risk 1 to reward ${String.format(Locale.US, "%.2f", ratio)}"
}
