package com.callbook.journal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbook.journal.ui.CallbookRoot
import com.callbook.journal.ui.JournalViewModel
import com.callbook.journal.ui.strategyLink
import com.callbook.journal.ui.theme.StockCallsTheme

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf(strategyLink(null))

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_StockCalls)
        super.onCreate(savedInstanceState)
        incoming.value = strategyLink(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
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
        incoming.value = strategyLink(intent)
    }
}
