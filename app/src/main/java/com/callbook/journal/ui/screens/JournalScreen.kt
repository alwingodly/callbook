package com.callbook.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.Prediction
import com.callbook.journal.ui.JournalFilter
import com.callbook.journal.ui.accepts
import com.callbook.journal.ui.components.CallCard
import com.callbook.journal.ui.components.LiveRefresh
import com.callbook.journal.ui.components.LivePriceChip
import com.callbook.journal.ui.formatDay
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper

private sealed interface JournalRow {
    data class Header(val day: Long) : JournalRow
    data class Item(val prediction: Prediction) : JournalRow
}

@Composable
fun JournalScreen(
    predictions: List<Prediction>,
    loading: Boolean,
    filter: JournalFilter,
    onFilter: (JournalFilter) -> Unit,
    onOpen: (Long) -> Unit,
    nowPrices: Map<String, Double> = emptyMap(),
    caps: Map<String, CapSize> = emptyMap(),
    pricesLive: Boolean = false,
    onPrices: () -> Unit = {},
    onNew: () -> Unit = {},
    onRefresh: suspend () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val rows = remember(predictions, filter) { buildRows(predictions.filter(filter::accepts)) }
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Stocks",
                    color = Paper,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    modifier = Modifier.weight(1f),
                )
                LivePriceChip(on = pricesLive, onClick = onPrices)
                IconButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = "New stock", tint = Accent)
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                JournalFilter.entries.forEach { option ->
                    GrowwTab(
                        label = option.label,
                        selected = option == filter,
                        onClick = { onFilter(option) },
                    )
                }
            }
        }
        LiveRefresh(onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        if (loading && predictions.isEmpty()) {
            Text(
                text = "Opening your journal...",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = Mist,
            )
        } else if (rows.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
                item {
                    Text(
                        text = if (predictions.isEmpty()) "No calls yet." else "Nothing in this filter.",
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = Mist,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(
                    items = rows,
                    key = { row ->
                        when (row) {
                            is JournalRow.Header -> "day-${row.day}"
                            is JournalRow.Item -> "call-${row.prediction.id}"
                        }
                    },
                    contentType = { row -> if (row is JournalRow.Header) "header" else "call" },
                ) { row ->
                    when (row) {
                        is JournalRow.Header -> Text(
                            text = formatDay(row.day),
                            modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                            color = Mist,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                        is JournalRow.Item -> CallCard(
                            prediction = row.prediction,
                            now = nowPrices[row.prediction.symbol],
                            cap = caps[row.prediction.symbol.trim().uppercase()],
                            onClick = { onOpen(row.prediction.id) },
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun GrowwTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick).padding(top = 8.dp)) {
        Text(
            text = label,
            color = if (selected) Accent else Mist,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp,
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) Accent else Color.Transparent),
        )
    }
}

private fun buildRows(predictions: List<Prediction>): List<JournalRow> {
    val rows = mutableListOf<JournalRow>()
    predictions.sortedWith(compareByDescending<Prediction> { it.epochDay }.thenByDescending { it.createdAt })
        .groupBy { it.epochDay }
        .forEach { (day, calls) ->
            rows += JournalRow.Header(day)
            calls.forEach { rows += JournalRow.Item(it) }
        }
    return rows
}
