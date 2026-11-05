package com.callbook.journal.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.callbook.journal.data.AppDatabase
import com.callbook.journal.data.PublicQuotes
import com.callbook.journal.data.toModel
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.DipSuggestion
import com.callbook.journal.domain.FallWatch
import com.callbook.journal.domain.ResultKind
import com.callbook.journal.domain.fallHasPaused
import com.callbook.journal.domain.inAlertHours
import com.callbook.journal.domain.isMarketWeekday
import com.callbook.journal.domain.judgement
import com.callbook.journal.domain.nextAlertAt
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ALERT_PREFS = "callbook_alerts"
private const val ALERT_DAY = "day"
private const val ALERT_SYMBOLS = "symbols"
private const val ALERT_ON = "background"
private const val FALL_WATCH = "fall_watch"
private val India = ZoneId.of("Asia/Kolkata")

/** Large-dip buy alerts for the whole session, not only while Strategy is open. */
object DipAlerts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Any()
    private var loop: Job? = null

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE).getBoolean(ALERT_ON, true)

    fun phoneKeepsAwake(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun start(context: Context) {
        val app = context.applicationContext
        ensureDipChannel(app)
        if (!enabled(app)) return
        startLoop(app)
        schedule(app)
    }

    /** On starts the check and asks the phone to keep Callbook awake. Off stops the check. */
    fun setEnabled(context: Context, on: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE).edit().putBoolean(ALERT_ON, on).commit()
        if (on) {
            startLoop(app)
            schedule(app)
            requestPhoneAwake(context)
        } else {
            stopLoop()
            cancel(app)
        }
    }

    /** Allow or remove the phone's battery exemption. Android asks before either change. */
    fun openPhoneBattery(context: Context) {
        val activity = context as? android.app.Activity ?: return
        val intent = if (phoneKeepsAwake(activity)) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${activity.packageName}"),
            )
        }
        runCatching { activity.startActivity(intent) }
    }

    fun scan(context: Context) {
        if (!enabled(context)) return
        val now = LocalDateTime.now(India)
        if (!isMarketWeekday(now.dayOfWeek) || !inAlertHours(now.toLocalTime())) return
        val slice = runCatching { PublicQuotes.scanBoard(CapSize.LARGE, dips = true, fresh = true) }.getOrNull()
        val held = openSymbols(context)
        val buys = slice?.checks.orEmpty().filter {
            it.suggestion == DipSuggestion.BUY && it.symbol.uppercase() !in held
        }
        val nowMillis = System.currentTimeMillis()
        val fresh = synchronized(gate) {
            val prefs = context.getSharedPreferences(ALERT_PREFS, Context.MODE_PRIVATE)
            val day = now.toLocalDate().toString()
            val sameDay = prefs.getString(ALERT_DAY, "") == day
            val sent = if (sameDay) prefs.getStringSet(ALERT_SYMBOLS, emptySet()).orEmpty() else emptySet()
            val watches = if (sameDay) decodeWatches(prefs.getString(FALL_WATCH, "")) else emptyMap()
            val nextWatches = linkedMapOf<String, FallWatch>()
            val calmed = mutableListOf<com.callbook.journal.domain.DipCheck>()
            buys.forEach { buy ->
                val key = buy.symbol.uppercase()
                val (watch, paused) = fallHasPaused(buy.dayLow, buy.price, watches[key], nowMillis)
                nextWatches[key] = watch
                if (paused) calmed += buy
            }
            val next = calmed.filter { it.symbol !in sent }
            Log.i(
                "Callbook",
                "dip scan buys=${buys.size} waiting=${buys.size - calmed.size} alerting=${next.size} checked=${slice?.checks?.size ?: -1}",
            )
            val edit = prefs.edit()
                .putString(ALERT_DAY, day)
                .putString(FALL_WATCH, encodeWatches(nextWatches))
            if (next.isNotEmpty()) {
                edit.putStringSet(ALERT_SYMBOLS, sent + next.map { it.symbol })
            } else if (!sameDay) {
                edit.putStringSet(ALERT_SYMBOLS, emptySet())
            }
            edit.commit()
            next
        }
        if (fresh.isEmpty()) return
        notifyDipBuys(context, fresh.map { StrategyAlert(CapSize.LARGE, "DIP", it) })
    }

    fun schedule(context: Context) {
        if (!enabled(context)) return
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(app)
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

    private fun startLoop(app: Context) {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive && enabled(app)) {
                scan(app)
                delay(60_000)
            }
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
    }

    private fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarm.cancel(pending)
        pending.cancel()
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            41,
            Intent(context, DipAlertReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun requestPhoneAwake(context: Context) {
        if (phoneKeepsAwake(context)) return
        openPhoneBattery(context)
    }

    private fun encodeWatches(watches: Map<String, FallWatch>): String =
        watches.entries.joinToString("\n") { "${it.key}|${it.value.low}|${it.value.quietSinceMillis}" }

    private fun decodeWatches(raw: String?): Map<String, FallWatch> {
        if (raw.isNullOrBlank()) return emptyMap()
        val watches = linkedMapOf<String, FallWatch>()
        raw.lineSequence().forEach { line ->
            val parts = line.split('|')
            if (parts.size != 3) return@forEach
            val low = parts[1].toDoubleOrNull() ?: return@forEach
            val since = parts[2].toLongOrNull() ?: return@forEach
            watches[parts[0]] = FallWatch(low, since)
        }
        return watches
    }

    private fun openSymbols(context: Context): Set<String> = runBlocking(Dispatchers.IO) {
        runCatching {
            AppDatabase.get(context).predictionDao().listAll()
                .map { it.toModel() }
                .filter { it.judgement().kind == ResultKind.OPEN }
                .map { it.symbol.trim().uppercase() }
                .toSet()
        }.getOrDefault(emptySet())
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
