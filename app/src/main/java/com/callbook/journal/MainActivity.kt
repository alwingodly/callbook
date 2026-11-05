package com.callbook.journal

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbook.journal.ui.CallbookRoot
import com.callbook.journal.ui.JournalViewModel
import com.callbook.journal.ui.strategyLink
import com.callbook.journal.ui.theme.StockCallsTheme
import com.callbook.journal.ui.theme.applySystemBars
import com.callbook.journal.ui.theme.loadTheme

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf(strategyLink(null))

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_StockCalls)
        super.onCreate(savedInstanceState)
        incoming.value = strategyLink(intent)
        wakeForAlert(incoming.value != null)
        loadTheme(this)
        applySystemBars(this)
        val container = (application as CallbookApp).container
        setContent {
            StockCallsTheme {
                val viewModel: JournalViewModel = viewModel(
                    factory = JournalViewModel.factory(container.repository, container.account, container.tickers),
                )
                CallbookRoot(
                    viewModel = viewModel,
                    strategyLink = incoming.value,
                    onStrategyLinkHandled = {
                        incoming.value = null
                        setIntent(Intent(this, MainActivity::class.java))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val link = strategyLink(intent)
        incoming.value = link
        wakeForAlert(link != null)
    }

    /** A buy alert turns the screen on. Opening the app yourself does not. */
    private fun wakeForAlert(alert: Boolean) {
        if (!alert || Build.VERSION.SDK_INT < 27) return
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
}
