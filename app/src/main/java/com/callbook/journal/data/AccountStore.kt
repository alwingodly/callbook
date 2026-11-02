package com.callbook.journal.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Dummy money added from the bank. Withdrawals change capital through each trade, not here. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("callbook_account", Context.MODE_PRIVATE)
    private val banked = MutableStateFlow(read())

    fun observe(): StateFlow<Double> = banked

    fun add(amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) return
        write(banked.value + amount)
    }

    /** Sends dummy money back to the bank. Cannot return more than was added. */
    fun take(amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) return
        write((banked.value - amount).coerceAtLeast(0.0))
    }

    private fun write(next: Double) {
        prefs.edit().putString(KEY, next.toString()).apply()
        banked.value = next
    }

    private fun read(): Double = prefs.getString(KEY, null)?.toDoubleOrNull() ?: 0.0

    private companion object {
        const val KEY = "banked"
    }
}
