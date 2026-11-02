package com.callbook.journal.data

import android.content.Context
import com.callbook.journal.domain.ChartSpan

/** Remembers the NSE or BSE symbol once a written name has matched a public stock. */
class TickerStore(context: Context) {
    private val prefs = context.getSharedPreferences("callbook_tickers", Context.MODE_PRIVATE)

    fun get(name: String): String? = prefs.getString(key(name), null)?.takeIf { it.isNotBlank() }

    fun chartSpan(): ChartSpan = ChartSpan.fromName(prefs.getString(SPAN, null))

    fun setChartSpan(span: ChartSpan) {
        prefs.edit().putString(SPAN, span.name).apply()
    }

    fun save(name: String, ticker: String) {
        val cleanName = name.trim()
        val cleanTicker = ticker.trim()
        if (cleanName.isEmpty() || cleanTicker.isEmpty()) return
        if (get(cleanName) == cleanTicker) return
        prefs.edit().putString(key(cleanName), cleanTicker).apply()
    }

    private fun key(name: String) = name.trim().uppercase()

    private companion object {
        const val SPAN = "chart_span"
    }
}
