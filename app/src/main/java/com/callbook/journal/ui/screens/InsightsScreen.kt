package com.callbook.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.Direction
import com.callbook.journal.domain.SymbolRecord
import com.callbook.journal.domain.costMoney
import com.callbook.journal.domain.madeMoney
import com.callbook.journal.domain.Insights
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.availableCash
import com.callbook.journal.domain.insightsOf
import com.callbook.journal.domain.parseAmount
import com.callbook.journal.domain.rupees
import com.callbook.journal.domain.wholePercent
import com.callbook.journal.ui.components.Panel
import com.callbook.journal.ui.formatShortDay
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Canvas
import com.callbook.journal.ui.theme.Faint
import com.callbook.journal.ui.theme.Ink
import com.callbook.journal.ui.theme.InkCard
import com.callbook.journal.ui.theme.Fraunces
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.Profit
import com.callbook.journal.ui.theme.pnlColor

@Composable
fun InsightsScreen(
    predictions: List<Prediction>,
    loading: Boolean,
    banked: Double,
    onAddFromBank: (Double) -> Unit,
    onReturnToBank: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            Text("Opening...", color = Mist)
        }
        return
    }
    val insights = remember(predictions) { insightsOf(predictions) }
    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Money", color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
            Text(
                text = "${insights.open} not sold  ·  ${insights.total} stocks",
                color = Mist,
                fontSize = 12.sp,
            )
        }
        item {
            LedgerBlock(
                insights = insights,
                banked = banked,
                onAddFromBank = onAddFromBank,
                onReturnToBank = onReturnToBank,
            )
        }
        val made = insights.madeMoney()
        val cost = insights.costMoney()
        if (made.isNotEmpty() || cost.isNotEmpty()) {
            item { MoneyNames(made, cost) }
        }
        if (insights.decided > 0) {
            item {
                Text(
                    text = "${insights.successes} of ${insights.decided} right",
                    color = Mist,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun RateBlock(insights: Insights) {
    Panel {
        if (insights.decided == 0) {
            Text("No judged calls yet.", color = Paper, fontWeight = FontWeight.SemiBold)
            Text("Mark a result, or type the price from Groww, and the rates will fill in.", color = Mist, fontSize = 14.sp)
        } else {
            RateFigures(insights)
        }
    }
}

@Composable
private fun RateFigures(insights: Insights) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RateFigure("Success", insights.successPercent, "${insights.successes} calls", Accent)
            RateFigure("Loss", insights.lossPercent, "${insights.failures} calls", Loss)
        }
        RateBar(success = insights.successes, failure = insights.failures)
        if (insights.beyond > 0 || insights.scratches > 0) {
            Text(
                text = buildString {
                    if (insights.beyond > 0) append("${insights.beyond} went beyond the target")
                    if (insights.beyond > 0 && insights.scratches > 0) append("  ·  ")
                    if (insights.scratches > 0) append("${insights.scratches} flat")
                },
                color = Mist,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun RateFigure(label: String, percent: Int, caption: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, color = Mist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text("$percent%", color = color, fontFamily = Fraunces, fontSize = 32.sp)
        Text(caption, color = Faint, fontSize = 12.sp)
    }
}

@Composable
private fun RateBar(success: Int, failure: Int) {
    val total = (success + failure).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(100))
            .background(Line),
    ) {
        if (success > 0) {
            Box(
                modifier = Modifier
                    .weight(success.toFloat())
                    .fillMaxHeight()
                    .background(Profit),
            )
        }
        if (failure > 0) {
            Box(
                modifier = Modifier
                    .weight(failure.toFloat())
                    .fillMaxHeight()
                    .background(Loss),
            )
        }
        if (success == 0 && failure == 0) {
            Box(modifier = Modifier.weight(total.toFloat()))
        }
    }
}

@Composable
private fun DirectionBlock(insights: Insights) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        SplitStat(
            title = "Gain calls",
            decided = insights.gainCallsDecided,
            successes = insights.gainCallSuccesses,
            modifier = Modifier.weight(1f),
        )
        SplitStat(
            title = "Loss calls",
            decided = insights.lossCallsDecided,
            successes = insights.lossCallSuccesses,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SplitStat(title: String, decided: Int, successes: Int, modifier: Modifier = Modifier) {
    Panel(modifier = modifier) {
        Text(title, color = Mist, fontSize = 13.sp, lineHeight = 17.sp)
        Text(
            text = if (decided == 0) "-" else "${wholePercent(successes, decided)}%",
            color = Paper,
            fontFamily = Fraunces,
            fontSize = 28.sp,
        )
        Text(
            text = if (decided == 0) "No judged calls" else "$successes of $decided right",
            color = Faint,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun LedgerBlock(
    insights: Insights,
    banked: Double,
    onAddFromBank: (Double) -> Unit,
    onReturnToBank: (Double) -> Unit,
) {
    val capital = availableCash(banked, insights)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Cash", color = Mist, fontSize = 12.sp)
            Text(
                text = rupees(capital, signed = capital < -0.005),
                color = when {
                    capital > 0.005 -> Paper
                    capital < -0.005 -> Loss
                    else -> Paper
                },
                fontFamily = Fraunces,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
            )
            Text(
                text = "In stocks ${rupees(insights.inStocks)}   ·   Bank ${rupees(banked)}",
                color = Mist,
                fontSize = 12.sp,
            )
        }
        BankAdd(
            banked = banked,
            shortfall = if (capital < -0.005) -capital else 0.0,
            onAdd = onAddFromBank,
            onReturn = onReturnToBank,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            LedgerMoney(
                label = "Profit",
                count = insights.profitCount,
                amount = insights.gainsIfBought,
                modifier = Modifier.weight(1f),
            )
            LedgerMoney(
                label = "Loss",
                count = insights.lossCount,
                amount = insights.lossesIfBought,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Total  ${rupees(insights.netIfBought, signed = true)}",
            color = pnlColor(insights.netIfBought),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun BankAdd(
    banked: Double,
    shortfall: Double,
    onAdd: (Double) -> Unit,
    onReturn: (Double) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val typed = parseAmount(text)
    val backAmount = when {
        typed == null -> if (banked > 0.5) banked else null
        typed <= banked + 0.001 -> typed
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shortfall > 0.5) {
            TextButton(onClick = { onAdd(shortfall) }, modifier = Modifier.fillMaxWidth()) {
                Text("Add ${rupees(shortfall)} from bank", color = Ink, fontWeight = FontWeight.SemiBold)
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Amount") },
            placeholder = { Text("50000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
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
        if (typed != null && backAmount == null && banked > 0.5) {
            Text("Only ${rupees(banked)} is from the bank.", color = Mist, fontSize = 12.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    typed?.let {
                        onAdd(it)
                        text = ""
                    }
                },
                enabled = typed != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Canvas, disabledContainerColor = InkCard, disabledContentColor = Faint),
            ) {
                Text("Add", fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    backAmount?.let {
                        onReturn(it)
                        text = ""
                    }
                },
                enabled = backAmount != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InkCard, contentColor = Paper, disabledContainerColor = InkCard, disabledContentColor = Faint),
            ) {
                Text("To bank", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MoneyNames(made: List<SymbolRecord>, cost: List<SymbolRecord>) {
    Panel {
        if (made.isNotEmpty()) {
            Text(
                text = "Made money",
                color = Faint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
            )
            made.forEach { NameMoney(it) }
        }
        if (cost.isNotEmpty()) {
            Text(
                text = "Lost money",
                color = Faint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.9.sp,
            )
            cost.forEach { NameMoney(it) }
        }
    }
}

@Composable
private fun NameMoney(record: SymbolRecord) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(record.symbol, color = Paper, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(rupees(record.netIfBought, signed = true), color = pnlColor(record.netIfBought), fontFamily = Fraunces, fontSize = 16.sp)
    }
}

@Composable
private fun LedgerCount(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Faint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        )
        Text(value, color = Paper, fontFamily = Fraunces, fontSize = 22.sp)
    }
}

@Composable
private fun LedgerMoney(label: String, count: Int, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Faint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        )
        Text(
            text = rupees(amount, signed = amount > 0.005),
            color = pnlColor(amount),
            fontFamily = Fraunces,
            fontSize = 16.sp,
        )
        Text(
            text = if (count == 1) "1 call" else "$count calls",
            color = Mist,
            fontSize = 12.sp,
        )
    }
}
