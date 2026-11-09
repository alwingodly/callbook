package com.callbook.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.DaySummary
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.ResultKind
import com.callbook.journal.domain.daySummary
import com.callbook.journal.domain.holdingPnl
import com.callbook.journal.domain.judgement
import com.callbook.journal.domain.rupees
import com.callbook.journal.domain.shownTarget
import com.callbook.journal.domain.stillHolding
import com.callbook.journal.ui.components.CallCard
import com.callbook.journal.ui.components.LiveRefresh
import com.callbook.journal.ui.components.LivePriceChip
import com.callbook.journal.ui.formatShortDay
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Faint
import com.callbook.journal.ui.theme.Fraunces
import com.callbook.journal.ui.theme.InkCard
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.Profit
import com.callbook.journal.ui.theme.pnlColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    day: Long,
    predictions: List<Prediction>,
    loading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpToday: () -> Unit,
    onOpen: (Long) -> Unit,
    nowPrices: Map<String, Double> = emptyMap(),
    caps: Map<String, CapSize> = emptyMap(),
    pricesLive: Boolean = false,
    onPrices: () -> Unit = {},
    onNew: () -> Unit = {},
    onSettings: () -> Unit = {},
    onRefresh: suspend () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now().toEpochDay()
    val calls = remember(predictions, day) {
        predictions.filter { it.epochDay == day }.sortedBy { it.createdAt }
    }
    val holding = remember(predictions, day) { stillHolding(predictions, day) }
    val plans = remember(predictions, day) {
        predictions
            .filter { it.notStarted && it.epochDay <= day && it.judgement().kind == ResultKind.OPEN }
            .sortedWith(compareBy<Prediction> { it.epochDay }.thenBy { it.createdAt })
    }
    val stillOpen = remember(calls) { calls.filter { !it.notStarted && it.judgement().kind == ResultKind.OPEN } }
    val withdrawn = remember(calls) { calls.filter { it.judgement().kind != ResultKind.OPEN } }
    val liveOpen = remember(holding, stillOpen, nowPrices) {
        (holding + stillOpen).filter { (nowPrices[it.symbol] ?: 0.0) > 0.0 }
    }
    val together = holdingPnl(liveOpen, nowPrices)
    val ifTarget = remember(liveOpen, withdrawn) { shownTarget(liveOpen + withdrawn) }
    val summary = daySummary(calls)
    val date = LocalDate.ofEpochDay(day)
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = date.format(weekdayFormat),
                        color = Mist,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = date.format(dateFormat),
                        color = Paper,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day", tint = Mist)
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day", tint = Mist)
                }
                IconButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = "New stock", tint = Accent)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Mist)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LivePriceChip(on = pricesLive, onClick = onPrices)
                if (day != today) {
                    TextButton(onClick = onJumpToday) {
                        Text("Today", color = Accent, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        LiveRefresh(onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        if (loading && predictions.isEmpty()) {
            Text(
                text = "Opening your journal...",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                color = Mist,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (together != null) {
                    item { HoldingTotal(together) }
                }
                if (holding.isNotEmpty()) {
                    item { SectionLabel("Open") }
                    items(holding, key = { "hold-${it.id}" }) { prediction ->
                        CallCard(
                            prediction = prediction,
                            now = nowPrices[prediction.symbol],
                            cap = caps[prediction.symbol.trim().uppercase()],
                            onClick = { onOpen(prediction.id) },
                        )
                    }
                }
                if (calls.isNotEmpty()) {
                    item { DayTotals(summary, ifTarget) }
                }
                if (stillOpen.isNotEmpty()) {
                    item { SectionLabel("Bought") }
                    items(stillOpen, key = { it.id }) { prediction ->
                        CallCard(
                            prediction = prediction,
                            now = nowPrices[prediction.symbol],
                            cap = caps[prediction.symbol.trim().uppercase()],
                            onClick = { onOpen(prediction.id) },
                        )
                    }
                }
                if (plans.isNotEmpty()) {
                    item { SectionLabel("Planned") }
                    items(plans, key = { "plan-${it.id}" }) { prediction ->
                        CallCard(
                            prediction = prediction,
                            cap = caps[prediction.symbol.trim().uppercase()],
                            onClick = { onOpen(prediction.id) },
                        )
                    }
                }
                if (withdrawn.isNotEmpty()) {
                    item { SectionLabel("Closed") }
                    items(withdrawn, key = { "sold-${it.id}" }) { prediction ->
                        CallCard(
                            prediction = prediction,
                            now = nowPrices[prediction.symbol],
                            cap = caps[prediction.symbol.trim().uppercase()],
                            onClick = { onOpen(prediction.id) },
                        )
                    }
                }
                if (calls.isEmpty() && plans.isEmpty()) {
                    item {
                        Text(
                            text = if (holding.isEmpty()) "No stocks this day." else "Nothing new this day.",
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Mist,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HoldingTotal(amount: Double) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text("Unrealised P&L", color = Mist, fontSize = 13.sp)
        Text(
            text = rupees(amount, signed = true),
            color = pnlColor(amount),
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
        color = Mist,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

@Composable
private fun DayTotals(summary: DaySummary, ifTarget: Double?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MoneyTotal("Invested", summary.invested, Modifier.weight(1f), signed = false)
            MoneyTotal("At target", ifTarget ?: 0.0, Modifier.weight(1f), blank = ifTarget == null)
            MoneyTotal("Sold", if (summary.priced > 0) summary.moved else 0.0, Modifier.weight(1f), blank = summary.priced == 0)
        }
    }
}

@Composable
private fun MoneyTotal(
    label: String,
    amount: Double,
    modifier: Modifier = Modifier,
    blank: Boolean = false,
    signed: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = Mist, fontSize = 12.sp)
        Text(
            text = if (blank) "—" else rupees(amount, signed = signed && (amount > 0.005 || amount < -0.005)),
            color = when {
                blank || !signed -> Paper
                amount > 0.005 -> Profit
                amount < -0.005 -> Loss
                else -> Paper
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val dateFormat = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
