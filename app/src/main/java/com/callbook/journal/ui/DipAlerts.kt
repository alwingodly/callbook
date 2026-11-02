package com.callbook.journal.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.callbook.journal.data.PublicQuotes
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.DipSuggestion
import com.callbook.journal.domain.inMarketHours
import com.callbook.journal.domain.isMarketWeekday
import com.callbook.journal.domain.nextAlertAt
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val ALERT_PREFS = "callbook_alerts"
private const val ALERT_DAY = "day"
private const val ALERT_SYMBOLS = "symbols"
private val India = ZoneId.of("Asia/Kolkata")

/** Large-dip buy alerts for the whole session, not only while Strategy is open. */
object DipAlerts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Any()

    fun start(context: Context) {
        val app = context.applicationContext
        schedule(app)
        scope.launch {
            while (isActive) {
                scan(app)
                delay(60_000)
            }
        }
    }

    fun scan(context: Context) {
        val now = LocalDateTime.now(India)
        if (!isMarketWeekday(now.dayOfWeek) || !inMarketHours(now.toLocalTime())) return
        val slice = runCatching { PublicQuotes.scanBoard(CapSize.LARGE, dips = true, fresh = true) }.getOrNull()
        val buys = slice?.checks.orEmpty().filter { it.suggestion == DipSuggestion.BUY }
        Log.i("Callbook", "dip scan buys=${buys.size} checked=${slice?.checks?.size ?: -1}")
        if (buys.isEmpty()) return
        val fresh = synchronized(gate) {
            val prefs = context.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE)
            val day = now.toLocalDate().toString()
            val sent = if (prefs.getString(ALERT_DAY, "") == day) {
                prefs.getStringSet(ALERT_SYMBOLS, emptySet()).orEmpty()
            } else {
                emptySet()
            }
            val next = buys.filter { it.symbol !in sent }
            if (next.isNotEmpty()) {
                prefs.edit()
                    .putString(ALERT_DAY, day)
                    .putStringSet(ALERT_SYMBOLS, sent + next.map { it.symbol })
                    .commit()
            }
            next
        }
        if (fresh.isEmpty()) return
        notifyDipBuys(context, fresh.map { StrategyAlert(CapSize.LARGE, "DIP", it) })
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            app,
            41,
            Intent(app, DipAlertReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = nextAlertAt(LocalDateTime.now(India))
            .atZone(India)
            .toInstant()
            .toEpochMilli()
        runCatching {
            val exact = android.os.Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
            if (exact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }
    }
}

class DipAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val wake = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "callbook:dip")
        wake.acquire(25_000L)
        Thread {
            try {
                DipAlerts.scan(context.applicationContext)
            } finally {
                DipAlerts.schedule(context.applicationContext)
                if (wake.isHeld) wake.release()
                pending.finish()
            }
        }.start()
    }
}
