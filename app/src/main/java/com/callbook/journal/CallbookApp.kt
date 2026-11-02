package com.callbook.journal

import android.app.Application
import android.content.Context
import com.callbook.journal.data.AccountStore
import com.callbook.journal.data.TickerStore
import com.callbook.journal.data.AppDatabase
import com.callbook.journal.data.PredictionRepository

class CallbookApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val repository = PredictionRepository(AppDatabase.get(context).predictionDao())
    val account = AccountStore(context)
    val tickers = TickerStore(context)
}
