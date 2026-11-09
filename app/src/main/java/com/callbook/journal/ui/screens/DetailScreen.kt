package com.callbook.journal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.callbook.journal.domain.StockHeadline
import com.callbook.journal.ui.components.LiveRefresh
import com.callbook.journal.ui.openArticle
import com.callbook.journal.domain.ChartLoad
import com.callbook.journal.domain.ChartSpan
import com.callbook.journal.domain.HeadlineTone
import com.callbook.journal.domain.NewsLoad
import com.callbook.journal.domain.newsAge
import com.callbook.journal.domain.Direction
import com.callbook.journal.domain.GrowwMath
import com.callbook.journal.domain.Mark
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.ResultKind
import com.callbook.journal.domain.Segment
import com.callbook.journal.domain.TradeSide
import com.callbook.journal.domain.fieldPrice
import com.callbook.journal.domain.followSide
import com.callbook.journal.domain.judgement
import com.callbook.journal.domain.markToMarket
import com.callbook.journal.domain.parseAmount
import com.callbook.journal.domain.parseSignedAmount
import com.callbook.journal.domain.taxCutting
import com.callbook.journal.domain.boughtResult
import com.callbook.journal.domain.returnedCash
import com.callbook.journal.domain.priceText
import com.callbook.journal.domain.rupees
import com.callbook.journal.domain.signedPercent
import com.callbook.journal.domain.verdictSentence
import com.callbook.journal.ui.components.CandleChart
import com.callbook.journal.ui.components.StudyChartHeight
import com.callbook.journal.ui.components.MoneyLine
import com.callbook.journal.ui.components.Panel
import com.callbook.journal.ui.formatDay
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Wash
import com.callbook.journal.ui.theme.Canvas
import com.callbook.journal.ui.theme.Faint
import com.callbook.journal.ui.theme.Ink
import com.callbook.journal.ui.theme.InkCard
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.Profit
import com.callbook.journal.ui.theme.pnlColor
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    prediction: Prediction?,
    loading: Boolean,
    chart: ChartLoad,
    chartRange: ChartSpan,
    nowPrices: Map<String, Double> = emptyMap(),
    watchedPrice: Double? = null,
    onChartRange: (ChartSpan) -> Unit,
    onShowChart: (String) -> Unit,
    onHideChart: () -> Unit,
    news: NewsLoad,
    onShowNews: (String) -> Unit,
    onHideNews: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSaveOutcome: (actualPrice: Double?, mark: Mark?, manualPnl: Double?) -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: suspend () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var chartOn by remember(prediction?.id) { mutableStateOf(false) }
    var newsOn by remember(prediction?.id) { mutableStateOf(false) }
    LaunchedEffect(prediction?.id) {
        onHideChart()
        onHideNews()
    }
    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text(prediction?.symbol ?: "Call") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (prediction != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit call", tint = Paper)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete call", tint = Loss)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Canvas,
                    titleContentColor = Paper,
                    navigationIconContentColor = Paper,
                ),
            )
        },
    ) { padding ->
        when {
            prediction == null && loading -> Text(
                "Opening this call...",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = Mist,
            )
            prediction == null -> Text(
                "This call is no longer in the journal.",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = Mist,
            )
            else -> LiveRefresh(
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
            DetailBody(
                prediction = prediction,
                chart = chart,
                chartRange = chartRange,
                chartOn = chartOn,
                nowPrices = nowPrices,
                watchedPrice = watchedPrice,
                onChartRange = onChartRange,
                onChartToggle = {
                    val next = !chartOn
                    chartOn = next
                    if (next) onShowChart(prediction.symbol) else onHideChart()
                },
                news = news,
                newsOn = newsOn,
                onNewsToggle = {
                    val next = !newsOn
                    newsOn = next
                    if (next) onShowNews(prediction.symbol) else onHideNews()
                },
                onSaveOutcome = onSaveOutcome,
                onReopen = onReopen,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = InkCard,
            titleContentColor = Paper,
            textContentColor = Mist,
            title = { Text("Delete this call?") },
            text = { Text("It will leave your success rate and the rupee record.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = Loss) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = Mist) }
            },
        )
    }
}

@Composable
private fun DetailBody(
    prediction: Prediction,
    chart: ChartLoad,
    chartRange: ChartSpan,
    chartOn: Boolean,
    nowPrices: Map<String, Double>,
    watchedPrice: Double?,
    onChartRange: (ChartSpan) -> Unit,
    onChartToggle: () -> Unit,
    news: NewsLoad,
    newsOn: Boolean,
    onNewsToggle: () -> Unit,
    onSaveOutcome: (Double?, Mark?, Double?) -> Unit,
    onReopen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var priceText by remember(prediction.id, prediction.actualPrice, prediction.mark) {
        mutableStateOf(prediction.actualPrice?.let(::fieldPrice).orEmpty())
    }
    var resultText by remember(prediction.id, prediction.manualPnl) {
        mutableStateOf(prediction.manualPnl?.let(::fieldPrice).orEmpty())
    }
    var error by remember(prediction.id) { mutableStateOf<String?>(null) }
    var notice by remember(prediction.id) { mutableStateOf<String?>(null) }
    val typed = parseAmount(priceText)
    val manual = if (resultText.isBlank()) null else parseSignedAmount(resultText)
    val judged = prediction.judgement()
    val booked = prediction.boughtResult()
    val profitCall = prediction.copy(actualPrice = typed, mark = Mark.WITHDRAW_PROFIT, manualPnl = manual)
    val profitBack = profitCall.returnedCash()
    val profitTax = profitCall.taxCutting()
    val afterTaxBack = profitBack?.minus(profitTax)
    val lossCall = prediction.copy(actualPrice = typed, mark = Mark.WITHDRAW_LOSS, manualPnl = manual)
    val lossBack = lossCall.returnedCash()
    val lossTax = lossCall.taxCutting()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ChartSwitch(on = chartOn, onClick = onChartToggle)
            NewsSwitch(on = newsOn, onClick = onNewsToggle)
        }
        if (chartOn) {
            PriceChartBlock(prediction, chart, chartRange, nowPrices, watchedPrice, onChartRange)
        }
        if (newsOn) {
            NewsBlock(news)
        }
        Text(
            text = buildString {
                append(if (prediction.direction == Direction.GAIN) "Gain" else "Loss")
                append("  ·  ")
                append(if (prediction.segment == Segment.DELIVERY) "Delivery" else "Intraday")
                append("  ·  ")
                append(formatDay(prediction.epochDay))
            },
            color = if (prediction.direction == Direction.GAIN) Accent else Loss,
            fontSize = 13.sp,
        )
        if (prediction.note.isNotBlank()) {
            Text(prediction.note, color = Paper, fontSize = 15.sp, lineHeight = 21.sp)
        }
        Panel {
            Fact("Entry", priceText(prediction.entryPrice))
            Fact("Target", priceText(prediction.targetPrice))
            Fact("Stop", priceText(prediction.stopPrice))
            Fact("Amount", rupees(prediction.capital))
            val shares = GrowwMath.quantity(prediction.capital, prediction.entryPrice)
            if (shares > 0) {
                Text("$shares shares", color = Faint, fontSize = 13.sp)
            }
        }
        Panel {
            MoneyLine("Target", GrowwMath.atPrice(prediction, prediction.targetPrice, TradeSide.LONG))
            MoneyLine("Stop", GrowwMath.atPrice(prediction, prediction.stopPrice, TradeSide.LONG))
            typed?.let { price ->
                val bought = GrowwMath.atPrice(prediction, price, TradeSide.LONG)
                MoneyLine("Now", bought)
                if (bought.quantity > 0) {
                    Text(
                        text = "${signedPercent(bought.stockMovePercent)}  ·  broker ${rupees(bought.brokerCost)}",
                        color = Faint,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        FollowBlock(prediction, typed)
        booked?.let { result ->
            val tax = prediction.taxCutting()
            val after = result.netPnl - tax
            Panel {
                if (prediction.manualPnl == null) {
                    Fact("Price move", rupees(result.grossPnl, signed = true))
                    if (result.charges > 0.5) Fact("Charges", rupees(result.charges))
                }
                Fact("Net P&L", rupees(result.netPnl, signed = true))
                Fact("STCG", rupees(tax))
                Fact("After STCG", rupees(after, signed = true))
                Fact("Proceeds", rupees(prediction.capital + after))
            }
        }
        prediction.verdictSentence()?.let { sentence ->
            val color = when (judged.kind) {
                ResultKind.SUCCESS -> Profit
                ResultKind.FAILED -> Loss
                else -> Mist
            }
            Text(sentence, color = color, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
        }
        OutlinedTextField(
            value = priceText,
            onValueChange = {
                priceText = it
                error = null
                notice = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Exit price") },
            placeholder = { Text("Optional") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Line,
                focusedLabelColor = Accent,
                unfocusedLabelColor = Mist,
                cursorColor = Ink,
                focusedTextColor = Paper,
                unfocusedTextColor = Paper,
                focusedContainerColor = InkCard,
                unfocusedContainerColor = InkCard,
                focusedPlaceholderColor = Faint,
                unfocusedPlaceholderColor = Faint,
            ),
        )
        OutlinedTextField(
            value = resultText,
            onValueChange = {
                resultText = it
                error = null
                notice = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("P&L") },
            placeholder = { Text("Optional, like 850 or -320") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ink,
                unfocusedBorderColor = Line,
                focusedLabelColor = Accent,
                unfocusedLabelColor = Mist,
                cursorColor = Ink,
                focusedTextColor = Paper,
                unfocusedTextColor = Paper,
                focusedContainerColor = InkCard,
                unfocusedContainerColor = InkCard,
                focusedPlaceholderColor = Faint,
                unfocusedPlaceholderColor = Faint,
            ),
        )
        Text(
            text = "Leave this blank to use the exit price. A minus is a loss.",
            color = Faint,
            fontSize = 12.sp,
        )
        error?.let { Text(it, color = Loss, fontSize = 13.sp) }
        notice?.let { Text(it, color = Profit, fontSize = 13.sp) }
        WithdrawButton(
            title = "Book profit",
            back = afterTaxBack,
            tax = profitTax.takeIf { it > 0.0 },
            afterTax = afterTaxBack.takeIf { profitTax > 0.0 },
            container = Profit,
            onClick = {
                saveWithdraw(priceText, typed, resultText, manual, Mark.WITHDRAW_PROFIT, onSaveOutcome) { message ->
                    error = message
                    if (message == null) notice = "Sold. Money is back."
                }
            },
        )
        WithdrawButton(
            title = "Book loss",
            back = lossBack?.minus(lossTax),
            tax = lossTax.takeIf { it > 0.0 },
            afterTax = lossBack?.minus(lossTax)?.takeIf { lossTax > 0.0 },
            container = Loss,
            onClick = {
                saveWithdraw(priceText, typed, resultText, manual, Mark.WITHDRAW_LOSS, onSaveOutcome) { message ->
                    error = message
                    if (message == null) notice = "Sold. What is left is back."
                }
            },
        )
        if (prediction.judgement().kind != ResultKind.OPEN) {
            TextButton(onClick = {
                priceText = ""
                resultText = ""
                notice = null
                error = null
                onReopen()
            }) {
                Text("Reopen", color = Mist)
            }
        }
    }
}

@Composable
private fun WithdrawButton(
    title: String,
    back: Double?,
    container: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    tax: Double? = null,
    afterTax: Double? = null,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Canvas),
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (back != null) {
                Text("${rupees(back)} back", fontSize = 12.sp)
            }
            if (tax != null && afterTax != null) {
                Text(
                    text = "STCG ${rupees(tax)}  ·  ${rupees(afterTax)} after STCG",
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun saveWithdraw(
    priceText: String,
    typed: Double?,
    resultText: String,
    manual: Double?,
    mark: Mark,
    onSaveOutcome: (Double?, Mark?, Double?) -> Unit,
    onResult: (String?) -> Unit,
) {
    if (priceText.isNotBlank() && typed == null) {
        onResult("Enter a price like 2456.50.")
        return
    }
    if (resultText.isNotBlank() && manual == null) {
        onResult("Enter the profit or loss, like 850 or -320.")
        return
    }
    onSaveOutcome(typed, mark, manual)
    onResult(null)
}

@Composable
private fun NewsSwitch(on: Boolean, onClick: () -> Unit) {
    Text(
        text = if (on) "Hide news" else "News",
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        color = Accent,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

@Composable
private fun NewsBlock(news: NewsLoad) {
    val bringNews = remember { BringIntoViewRequester() }
    LaunchedEffect(news) {
        if (news !is NewsLoad.Idle) bringNews.bringIntoView()
    }
    Column(
        modifier = Modifier.bringIntoViewRequester(bringNews),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("From phrases in the headline", color = Faint, fontSize = 11.sp)
        when (news) {
            NewsLoad.Idle, NewsLoad.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Accent,
                    strokeWidth = 2.dp,
                )
                Text("  Loading news…", color = Mist, fontSize = 13.sp)
            }
            is NewsLoad.Unavailable -> Text(news.message, color = Mist, fontSize = 13.sp)
            is NewsLoad.Ready -> news.items.forEach { item ->
                HeadlineLink(item)
            }
        }
    }
}

@Composable
private fun HeadlineLink(item: StockHeadline) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember(item.link) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !opening) {
                scope.openArticle(context, item.link) { opening = it }
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = item.title,
            color = Paper,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val toneColor = when (item.read.tone) {
            HeadlineTone.POSITIVE -> Profit
            HeadlineTone.NEGATIVE -> Loss
            HeadlineTone.MIXED -> Mist
        }
        val age = newsAge(item.publishedAt)
        Text(
            text = buildString {
                append(item.source)
                if (age.isNotEmpty()) append("  ·  $age")
                append("  ·  ${item.read.tone.label} ${item.read.percent}%")
            },
            color = toneColor,
            fontSize = 12.sp,
        )
        Text(
            text = if (opening) "Opening…" else "Open",
            color = Accent,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ChartSwitch(on: Boolean, onClick: () -> Unit) {
    Text(
        text = if (on) "Hide chart" else "Show chart",
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        color = Accent,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

@Composable
private fun PriceChartBlock(
    prediction: Prediction,
    chart: ChartLoad,
    chartRange: ChartSpan,
    nowPrices: Map<String, Double>,
    watchedPrice: Double?,
    onChartRange: (ChartSpan) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (chart !is ChartLoad.Idle) {
            SpanPicker(chartRange, onChartRange)
        }
        ChartBody(prediction, chart, chartRange, nowPrices, watchedPrice)
    }
}

@Composable
private fun SpanPicker(selected: ChartSpan, onSelect: (ChartSpan) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartSpan.entries.forEach { span ->
            val on = span == selected
            Text(
                text = span.label,
                modifier = Modifier
                    .clickable { onSelect(span) }
                    .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
                color = if (on) Accent else Mist,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun ChartBody(
    prediction: Prediction,
    chart: ChartLoad,
    chartRange: ChartSpan,
    nowPrices: Map<String, Double>,
    watchedPrice: Double?,
) {
    when (chart) {
        ChartLoad.Idle -> Unit
        ChartLoad.Loading -> Box(
            modifier = Modifier.fillMaxWidth().height(StudyChartHeight),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Accent,
                strokeWidth = 2.5.dp,
            )
        }
        is ChartLoad.Unavailable -> Text(chart.message, color = Mist, fontSize = 13.sp)
        is ChartLoad.Ready -> {
            val candles = chart.chart.candles
            val now = watchedPrice?.takeIf { it > 0.0 }
                ?: nowPrices[prediction.symbol]?.takeIf { it > 0.0 }
                ?: chart.chart.price
            val prev = candles[candles.lastIndex - 1].close
            val move = if (prev == 0.0) 0.0 else (now - prev) / prev * 100.0
            val open = prediction.judgement().kind == ResultKind.OPEN
            val held = if (open) prediction.markToMarket(now) else null
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chart.chart.symbol.substringBefore('.'),
                        color = Paper,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(rupees(now), color = pnlColor(move), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("  ${signedPercent(move)}", color = pnlColor(move), fontSize = 13.sp)
                }
                Text("Public price · ${chartRange.label}", color = Faint, fontSize = 11.sp)
                CandleChart(candles, resetKey = chart.chart.symbol + chartRange.name)
                if (held != null && held.quantity > 0) {
                    val word = when {
                        held.netPnl > 0.5 -> "In profit"
                        held.netPnl < -0.5 -> "In loss"
                        else -> "Flat"
                    }
                    Text(
                        text = "$word  ${rupees(held.netPnl, signed = true)} on ${rupees(prediction.capital)}",
                        color = pnlColor(held.netPnl),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Mist, fontSize = 14.sp)
        Text(value, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun FollowBlock(prediction: Prediction, typed: Double?) {
    val follow = prediction.followSide()
    if (follow == TradeSide.LONG) return
    when (follow) {
        TradeSide.STAY_OUT -> {
            val price = typed ?: return
            val bought = GrowwMath.atPrice(prediction, price, TradeSide.LONG)
            val line = when {
                bought.quantity == 0 -> "Below one share"
                bought.netPnl < -0.5 -> "Staying out avoids ${rupees(abs(bought.netPnl))}"
                bought.netPnl > 0.5 -> "Staying out misses ${rupees(bought.netPnl)}"
                else -> "Staying out is about flat"
            }
            Text(line, color = Mist, fontSize = 13.sp)
        }
        TradeSide.SHORT -> {
            MoneyLine("Short, target", GrowwMath.atPrice(prediction, prediction.targetPrice, TradeSide.SHORT))
            MoneyLine("Short, stop", GrowwMath.atPrice(prediction, prediction.stopPrice, TradeSide.SHORT))
            typed?.let { price ->
                MoneyLine("Short, now", GrowwMath.atPrice(prediction, price, TradeSide.SHORT))
            }
        }
        TradeSide.LONG -> Unit
    }
}
