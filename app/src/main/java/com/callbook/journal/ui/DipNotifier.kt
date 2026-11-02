package com.callbook.journal.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.callbook.journal.MainActivity
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.DipCheck
import com.callbook.journal.domain.rupees
import com.callbook.journal.domain.signedPercent

private const val DIP_CHANNEL = "dip_alerts"

data class StrategyLink(val cap: String, val play: String, val symbol: String)

data class StrategyAlert(val cap: CapSize, val play: String, val dip: DipCheck)

fun strategyLink(intent: Intent?): StrategyLink? {
    val data = intent?.data ?: return null
    if (!data.scheme.equals("callbook", true) || !data.host.equals("strategy", true)) return null
    val symbol = data.getQueryParameter("symbol")?.trim().orEmpty()
    if (symbol.isEmpty()) return null
    val cap = data.getQueryParameter("cap")?.trim().orEmpty().ifEmpty { CapSize.LARGE.name }
    val play = data.getQueryParameter("play")?.trim().orEmpty().ifEmpty { "DIP" }
    return StrategyLink(cap, play, symbol)
}

private fun strategyUri(cap: String, play: String, symbol: String): Uri =
    Uri.parse("callbook://strategy").buildUpon()
        .appendQueryParameter("cap", cap)
        .appendQueryParameter("play", play)
        .appendQueryParameter("symbol", symbol)
        .build()

private fun dipAlertText(dip: DipCheck): String =
    if (dip.limitPrice > 0.0) "${dip.reason} Limit ${rupees(dip.limitPrice)}." else dip.reason

fun notifyDipBuys(context: Context, buys: List<StrategyAlert>) {
    if (buys.isEmpty()) return
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) {
        manager.createNotificationChannel(
            NotificationChannel(DIP_CHANNEL, "Dip alerts", NotificationManager.IMPORTANCE_HIGH),
        )
    }
    buys.forEach { alert ->
        val dip = alert.dip
        val open = Intent(
            Intent.ACTION_VIEW,
            strategyUri(alert.cap.name, alert.play, dip.symbol),
            context,
            MainActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            dip.symbol.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, DIP_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${dip.symbol}  ${signedPercent(dip.changePercent)}")
            .setContentText(dipAlertText(dip))
            .setStyle(NotificationCompat.BigTextStyle().bigText(dipAlertText(dip)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val id = 8_000 + (dip.symbol.hashCode() and 0x0FFF)
        runCatching { NotificationManagerCompat.from(context).notify(id, note) }
    }
}
