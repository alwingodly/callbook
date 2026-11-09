package com.callbook.journal

import android.app.Application
import android.content.Context
import com.callbook.journal.data.AccountStore
import com.callbook.journal.data.TickerStore
import com.callbook.journal.data.AppDatabase
import com.callbook.journal.data.PredictionRepository
import com.callbook.journal.ui.DipAlerts
import com.callbook.journal.ui.theme.loadTheme

class CallbookApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        loadTheme(this)
        DipAlerts.start(this)
    }
}

class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)
    val repository = PredictionRepository(database.predictionDao(), database.buyScoreDao())
    val account = AccountStore(context)
    val tickers = TickerStore(context)
}
