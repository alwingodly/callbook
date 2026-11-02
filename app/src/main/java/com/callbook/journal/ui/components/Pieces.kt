package com.callbook.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.PositionResult
import com.callbook.journal.domain.TradeSide
import com.callbook.journal.domain.rupees
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Canvas
import com.callbook.journal.ui.theme.Fraunces
import com.callbook.journal.ui.theme.Ink
import com.callbook.journal.ui.theme.InkCard
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.Wash
import com.callbook.journal.ui.theme.pnlColor

@Composable
fun LivePriceChip(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = if (on) "Prices on" else "Prices",
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        color = if (on) Accent else Mist,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = Mist,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(100))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun ChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Wash)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Ink else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) Canvas else Mist,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier.fillMaxWidth(), content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InkCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
fun MoneyLine(label: String, result: PositionResult, modifier: Modifier = Modifier) {
    val amount = when {
        result.side == TradeSide.STAY_OUT -> 0.0
        result.quantity == 0 -> 0.0
        else -> result.netPnl
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = Mist, fontSize = 13.sp)
            Text(
                text = when {
                    result.side == TradeSide.STAY_OUT -> "No order"
                    result.quantity == 0 -> "Below one share"
                    else -> "${result.quantity} shares"
                },
                color = Paper,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
        Text(
            text = rupees(amount, signed = result.side != TradeSide.STAY_OUT && result.quantity > 0),
            color = pnlColor(amount),
            fontFamily = Fraunces,
            fontSize = 22.sp,
        )
    }
}
