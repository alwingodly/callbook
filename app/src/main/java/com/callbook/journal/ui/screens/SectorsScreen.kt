package com.callbook.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.HeadlineTone
import com.callbook.journal.domain.SectorLoad
import com.callbook.journal.domain.SectorRead
import com.callbook.journal.domain.StockHeadline
import com.callbook.journal.domain.newsAge
import com.callbook.journal.domain.signedPercent
import com.callbook.journal.ui.components.LiveRefresh
import com.callbook.journal.ui.openArticle
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Amber
import com.callbook.journal.ui.theme.Canvas
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.Profit
import com.callbook.journal.ui.theme.pnlColor

@Composable
fun SectorsScreen(
    sectors: SectorLoad,
    onLoad: (Boolean) -> Unit,
    onRefresh: suspend () -> Unit = {},
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = sectors as? SectorLoad.Ready
    LaunchedEffect(Unit) { onLoad(false) }
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sectors",
                color = Paper,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Check",
                modifier = Modifier.clickable { onLoad(true) }.padding(vertical = 8.dp),
                color = Accent,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
        Text(
            text = ready?.asOf?.takeIf { it.isNotBlank() }?.let { "Today  ·  $it" }
                ?: "Tap a sector for its news and stocks.",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            color = Mist,
            fontSize = 13.sp,
        )
        LiveRefresh(onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        when {
            sectors is SectorLoad.Loading && ready == null -> Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                Text("  Reading sectors…", color = Mist, fontSize = 13.sp)
            }
            sectors is SectorLoad.Unavailable && ready == null -> Text(
                text = sectors.message,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                color = Mist,
            )
            ready != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                items(ready.sectors.sortedBy { it.changePercent }, key = { it.sector.name }) { sector ->
                    SectorRow(sector, onOpen = { onOpen(sector.sector.name) })
                }
            }
        }
        }
    }
}

@Composable
private fun SectorRow(sector: SectorRead, onOpen: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = sector.sector.label,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                color = Paper,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = signedPercent(sector.changePercent),
                color = pnlColor(sector.changePercent),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
        }
        val note = sector.outlook?.note
        Text(
            text = note ?: "${sector.stocks.size} stocks",
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            color = if (note == null) Mist else Amber,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(color = Line, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectorScreen(
    sector: SectorRead?,
    loading: Boolean,
    onBack: () -> Unit,
    onLoad: (Boolean) -> Unit,
    onRefresh: suspend () -> Unit = {},
) {
    LaunchedEffect(sector, loading) {
        if (sector == null && !loading) onLoad(false)
    }
    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = sector?.sector?.label ?: "Sector",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Sectors")
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
            sector == null && loading -> Text(
                "Opening this sector…",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = Mist,
            )
            sector == null -> Text(
                "This sector is offline.",
                modifier = Modifier.padding(padding).padding(20.dp),
                color = Mist,
            )
            else -> SectorBody(sector, Modifier.padding(padding), onRefresh)
        }
    }
}

private enum class SectorPage(val label: String) {
    NEWS("News"),
    LIST("Stocks"),
}

@Composable
private fun SectorBody(sector: SectorRead, modifier: Modifier = Modifier, onRefresh: suspend () -> Unit) {
    var pageName by rememberSaveable(sector.sector.name) { mutableStateOf(SectorPage.NEWS.name) }
    val page = SectorPage.entries.firstOrNull { it.name == pageName } ?: SectorPage.NEWS
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            Text(
                text = signedPercent(sector.changePercent),
                color = pnlColor(sector.changePercent),
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
            )
            Text(
                text = "Nifty  ·  today  ·  ${sector.stocks.size} stocks",
                modifier = Modifier.padding(top = 2.dp),
                color = Mist,
                fontSize = 13.sp,
            )
            sector.outlook?.let { outlook ->
                Text(
                    text = outlook.note,
                    modifier = Modifier.padding(top = 12.dp),
                    color = Amber,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                SectorPage.entries.forEach { option ->
                    SectorTab(option.label, option == page) { pageName = option.name }
                }
            }
        }
        LiveRefresh(onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
        when (page) {
            SectorPage.NEWS -> LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                if (sector.headlines.isEmpty()) {
                    item {
                        Text(
                            text = "No Indian market story in the last three days.",
                            modifier = Modifier.padding(top = 8.dp),
                            color = Mist,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    itemsIndexed(sector.headlines, key = { index, item -> "$index-${item.title}" }) { _, headline ->
                        SectorHeadline(headline)
                    }
                }
            }
            SectorPage.LIST -> LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                items(sector.stocks, key = { it.symbol }) { stock ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stock.symbol,
                            modifier = Modifier.weight(1f),
                            color = Paper,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (stock.cap != null) {
                            Text(
                                text = stock.cap.label,
                                modifier = Modifier.padding(end = 12.dp),
                                color = Mist,
                                fontSize = 13.sp,
                            )
                        }
                        Text(
                            text = signedPercent(stock.changePercent),
                            color = pnlColor(stock.changePercent),
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SectorTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.width(IntrinsicSize.Max).clickable(onClick = onClick)) {
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

@Composable
private fun SectorHeadline(item: StockHeadline) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember(item.link) { mutableStateOf(false) }
    val age = newsAge(item.publishedAt)
    val toneColor = when (item.read.tone) {
        HeadlineTone.POSITIVE -> Profit
        HeadlineTone.NEGATIVE -> Loss
        HeadlineTone.MIXED -> Mist
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.link.isNotBlank() && !opening) {
                scope.openArticle(context, item.link) { opening = it }
            }
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = item.title,
            color = Paper,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(item.read.tone.label)
                if (item.source.isNotBlank()) append("  ·  ${item.source}")
                if (age.isNotEmpty()) append("  ·  $age")
            },
            modifier = Modifier.padding(top = 2.dp),
            color = toneColor,
            fontSize = 12.sp,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp), color = Line, thickness = 0.5.dp)
    }
}
