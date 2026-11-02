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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.DayLoser
import com.callbook.journal.domain.LoserBoard
import com.callbook.journal.domain.LosersLoad
import com.callbook.journal.domain.rupees
import com.callbook.journal.domain.signedPercent
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper

@Composable
fun LosersScreen(
    losers: LosersLoad,
    onLoad: (fresh: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardName by rememberSaveable { mutableStateOf(LoserBoard.LARGE.name) }
    val board = LoserBoard.entries.firstOrNull { it.name == boardName } ?: LoserBoard.LARGE
    LaunchedEffect(Unit) { onLoad(false) }
    val ready = losers as? LosersLoad.Ready
    val rows = ready?.boards?.get(board).orEmpty()
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Losers",
                    color = Paper,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Refresh",
                    modifier = Modifier.clickable { onLoad(true) }.padding(vertical = 8.dp),
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
            Text(
                text = ready?.asOf?.takeIf { it.isNotBlank() }?.let { "NSE · $it" } ?: "Today's fall, steepest first",
                color = Mist,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LoserBoard.entries.forEach { option ->
                    BoardTab(
                        label = option.label,
                        selected = option == board,
                        onClick = { boardName = option.name },
                    )
                }
            }
        }
        when {
            losers is LosersLoad.Loading && ready == null -> Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                Text("  Loading losers…", color = Mist, fontSize = 13.sp)
            }
            losers is LosersLoad.Unavailable && ready == null -> Text(
                text = losers.message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Mist,
            )
            rows.isEmpty() -> Text(
                text = "Nothing in this list.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Mist,
            )
            else -> LazyColumn(
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                items(rows, key = { it.symbol }) { loser ->
                    LoserRow(loser)
                }
            }
        }
    }
}

@Composable
private fun LoserRow(loser: DayLoser) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = loser.symbol,
                    color = Paper,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loser.volume.isNotEmpty()) {
                    Text(text = loser.volume, color = Mist, fontSize = 13.sp, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = rupees(loser.price),
                    color = Paper,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = signedPercent(loser.changePercent),
                    color = Loss,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
        HorizontalDivider(color = Line, thickness = 0.5.dp)
    }
}

@Composable
private fun BoardTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
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
