package com.callbook.journal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.Prediction
import com.callbook.journal.domain.ResultKind
import com.callbook.journal.domain.Segment
import com.callbook.journal.domain.boughtResult
import com.callbook.journal.domain.judgement
import com.callbook.journal.domain.markToMarket
import com.callbook.journal.domain.rupees
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.Profit
import com.callbook.journal.ui.theme.pnlColor
import java.time.LocalDate

@Composable
fun CallCard(
    prediction: Prediction,
    onClick: () -> Unit,
    now: Double? = null,
    modifier: Modifier = Modifier,
) {
    val judged = prediction.judgement()
    val days = (LocalDate.now().toEpochDay() - prediction.epochDay).toInt()
    val withdrawn = judged.kind != ResultKind.OPEN
    val marked = if (!withdrawn && now != null && now > 0.0) prediction.markToMarket(now) else null
    val age = when {
        days <= 0 -> ""
        days == 1 -> " · 1 day"
        else -> " · $days days"
    }
    val subtitle = buildString {
        append(if (prediction.segment == Segment.DELIVERY) "Delivery" else "Intraday")
        if (marked != null && marked.quantity > 0) append(" · ${marked.quantity} shares")
        if (withdrawn) append(" · Withdrawn") else if (marked == null) append(" · Not sold")
        if (!withdrawn) append(age)
    }
    val booked = prediction.boughtResult()?.netPnl
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = prediction.symbol,
                    color = Paper,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = Mist,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                marked != null && marked.quantity > 0 -> Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = rupees(now!! * marked.quantity),
                        color = Paper,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = rupees(marked.netPnl, signed = true),
                        color = pnlColor(marked.netPnl),
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                    )
                }
                !withdrawn -> Text(
                    text = rupees(prediction.capital),
                    color = Paper,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
                else -> Text(
                    text = booked?.let { rupees(it, signed = true) } ?: "—",
                    color = when (judged.kind) {
                        ResultKind.SUCCESS -> Profit
                        ResultKind.FAILED -> Loss
                        else -> Mist
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
            }
        }
        HorizontalDivider(color = Line, thickness = 1.dp)
    }
}
