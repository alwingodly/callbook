package com.callbook.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callbook.journal.domain.BuyOutcome
import com.callbook.journal.domain.BuyScore
import com.callbook.journal.domain.CapSize
import com.callbook.journal.domain.Danger
import com.callbook.journal.domain.DipCheck
import com.callbook.journal.domain.DipLoad
import com.callbook.journal.domain.DipSuggestion
import com.callbook.journal.domain.HeadlineTone
import com.callbook.journal.domain.StockHeadline
import com.callbook.journal.domain.WeekShape
import com.callbook.journal.domain.headlineDanger
import com.callbook.journal.domain.isMarketWeekday
import com.callbook.journal.domain.tradingDay
import com.callbook.journal.domain.inMarketHours
import com.callbook.journal.domain.newsAge
import com.callbook.journal.domain.rupees
import com.callbook.journal.domain.signedPercent
import com.callbook.journal.domain.SymbolLoad
import com.callbook.journal.ui.StrategyLink
import com.callbook.journal.ui.components.LiveRefresh
import com.callbook.journal.ui.openArticle
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Faint
import com.callbook.journal.ui.theme.Ink
import com.callbook.journal.ui.theme.InkCard
import com.callbook.journal.ui.theme.pnlColor
import com.callbook.journal.ui.theme.Wash
import com.callbook.journal.ui.theme.Amber
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Loss
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.Profit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay

private val India = ZoneId.of("Asia/Kolkata")

private enum class StrategyPlay(val label: String) {
    DIP("Dip"),
    PAUSE("Pause"),
}

private enum class StrategyPage(val label: String) {
    LIST("List"),
    CHECK("Check"),
    SCORE("Score"),
}

private sealed class StrategyLine {
    data class Head(val suggestion: DipSuggestion, val count: Int) : StrategyLine()
    data class Row(val dip: DipCheck) : StrategyLine()
}

private fun strategyLines(checks: List<DipCheck>): List<StrategyLine> =
    DipSuggestion.entries.flatMap { suggestion ->
        val rows = checks.filter { it.suggestion == suggestion }
        if (rows.isEmpty()) {
            emptyList()
        } else {
            listOf(StrategyLine.Head(suggestion, rows.size)) + rows.map { StrategyLine.Row(it) }
        }
    }

@Composable
fun DipsScreen(
    dips: DipLoad,
    lookup: SymbolLoad = SymbolLoad.Idle,
    onCheck: (String) -> Unit = {},
    onRefresh: suspend (CapSize, Boolean, String) -> Unit = { _, _, _ -> },
    onLoad: (CapSize, Boolean, Boolean) -> Unit,
    focus: StrategyLink? = null,
    onFocusHandled: () -> Unit = {},
    held: Set<String> = emptySet(),
    scores: List<BuyScore> = emptyList(),
    onAdd: (DipCheck) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var capName by rememberSaveable { mutableStateOf(CapSize.LARGE.name) }
    val cap = CapSize.entries.firstOrNull { it.name == capName } ?: CapSize.LARGE
    var playName by rememberSaveable { mutableStateOf(StrategyPlay.DIP.name) }
    val play = StrategyPlay.entries.firstOrNull { it.name == playName } ?: StrategyPlay.DIP
    var pageName by rememberSaveable { mutableStateOf(StrategyPage.LIST.name) }
    val page = StrategyPage.entries.firstOrNull { it.name == pageName } ?: StrategyPage.LIST
    val listState = rememberLazyListState()
    var opened by rememberSaveable { mutableStateOf("") }
    val currentCap = rememberUpdatedState(cap)
    val currentPlay = rememberUpdatedState(play)
    LaunchedEffect(cap, play) {
        onLoad(cap, play == StrategyPlay.DIP, false)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            val day = LocalDate.now(India)
            val time = LocalTime.now(India)
            if (!isMarketWeekday(day.dayOfWeek) || !inMarketHours(time)) continue
            onLoad(currentCap.value, currentPlay.value == StrategyPlay.DIP, true)
        }
    }
    LaunchedEffect(focus, dips, page, cap, play) {
        val target = focus ?: return@LaunchedEffect
        val targetCap = CapSize.entries.firstOrNull { it.name == target.cap } ?: CapSize.LARGE
        val targetPlay = StrategyPlay.entries.firstOrNull { it.name == target.play } ?: StrategyPlay.DIP
        if (page != StrategyPage.LIST || cap != targetCap || play != targetPlay) {
            pageName = StrategyPage.LIST.name
            capName = targetCap.name
            playName = targetPlay.name
            return@LaunchedEffect
        }
        if (dips !is DipLoad.Ready) return@LaunchedEffect
        val board = dips.boards[targetCap]
        val loaded = if (targetPlay == StrategyPlay.PAUSE) board?.pausesLoaded == true else board?.dipsLoaded == true
        if (!loaded) return@LaunchedEffect
        val rows = if (targetPlay == StrategyPlay.PAUSE) board?.pauses else board?.dips
        val index = strategyLines(rows.orEmpty()).indexOfFirst { line ->
            line is StrategyLine.Row && line.dip.symbol.equals(target.symbol, true)
        }
        if (index >= 0) {
            opened = target.symbol
            listState.animateScrollToItem(index)
        }
        onFocusHandled()
    }
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val ready = dips as? DipLoad.Ready
    val board = ready?.boards?.get(cap)
    val playLoaded = if (play == StrategyPlay.DIP) board?.dipsLoaded == true else board?.pausesLoaded == true
    val shown = when (play) {
        StrategyPlay.DIP -> board?.dips.orEmpty()
        StrategyPlay.PAUSE -> board?.pauses.orEmpty()
    }
    val subtitle = when (page) {
        StrategyPage.LIST -> board?.let {
            val stamp = ready?.asOf?.takeIf { it.isNotBlank() }?.let { "  ·  $it" }.orEmpty()
            "${cap.label}  ${signedPercent(it.marketPercent)}$stamp"
        } ?: "Names that fit a dip or a pause."
        StrategyPage.CHECK -> "One stock. The same buy, wait, or skip check."
        StrategyPage.SCORE -> "A buy is scored on the next session."
    }
    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Strategy",
                    color = Paper,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    modifier = Modifier.weight(1f),
                )
                if (page == StrategyPage.LIST) {
                    Text(
                        text = "Refresh",
                        modifier = Modifier.clickable {
                            onLoad(cap, play == StrategyPlay.DIP, true)
                        }.padding(vertical = 8.dp),
                        color = Accent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp),
                color = Mist,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                StrategyPage.entries.forEach { option ->
                    CapTab(option.label, option == page) { pageName = option.name }
                }
            }
        }
        when (page) {
            StrategyPage.LIST -> StrategyList(
                play = play,
                cap = cap,
                onPlay = { playName = it.name },
                onCap = { capName = it.name },
                playLoaded = playLoaded,
                dips = dips,
                ready = ready,
                shown = shown,
                listState = listState,
                opened = opened,
                held = held,
                onAdd = onAdd,
                onRefresh = { onRefresh(cap, play == StrategyPlay.DIP, "") },
                modifier = Modifier.weight(1f),
            )
            StrategyPage.CHECK -> StrategyCheck(
                query = query,
                onQuery = { query = it },
                lookup = lookup,
                held = held,
                onCheck = {
                    keyboard?.hide()
                    onCheck(query)
                },
                onAdd = onAdd,
                onRefresh = { onRefresh(cap, play == StrategyPlay.DIP, query) },
                modifier = Modifier.weight(1f),
            )
            StrategyPage.SCORE -> StrategyScore(
                scores = scores,
                onRefresh = { onRefresh(cap, play == StrategyPlay.DIP, "") },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StrategyList(
    play: StrategyPlay,
    cap: CapSize,
    onPlay: (StrategyPlay) -> Unit,
    onCap: (CapSize) -> Unit,
    playLoaded: Boolean,
    dips: DipLoad,
    ready: DipLoad.Ready?,
    shown: List<DipCheck>,
    listState: LazyListState,
    opened: String,
    held: Set<String>,
    onAdd: (DipCheck) -> Unit,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                StrategyPlay.entries.forEach { option ->
                    CapTab(option.label, option == play) { onPlay(option) }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CapSize.entries.forEach { option ->
                    CapTab(option.label, option == cap) { onCap(option) }
                }
            }
            Text(
                text = if (play == StrategyPlay.PAUSE) {
                    "Already rising, then a small dip. Target is the recent high."
                } else {
                    "Down 1% or more. Target is yesterday's close when it pays."
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = Mist,
                fontSize = 12.sp,
            )
        }
        LiveRefresh(onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            when {
                !playLoaded && dips is DipLoad.Unavailable && ready == null -> Text(
                    text = dips.message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Mist,
                )
                !playLoaded -> Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                    Text(
                        text = "  Checking ${cap.label.lowercase()} ${play.label.lowercase()}…",
                        color = Mist,
                        fontSize = 13.sp,
                    )
                }
                shown.isEmpty() -> Text(
                    text = if (play == StrategyPlay.PAUSE) {
                        "No ${cap.label.lowercase()} name is rising and pausing."
                    } else {
                        "No ${cap.label.lowercase()} name is down 1% or more."
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Mist,
                )
                else -> {
                    val lines = strategyLines(shown)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    ) {
                        items(lines, key = { line ->
                            when (line) {
                                is StrategyLine.Head -> "head-${line.suggestion.name}"
                                is StrategyLine.Row -> line.dip.symbol
                            }
                        }) { line ->
                            when (line) {
                                is StrategyLine.Head -> SuggestionHead(line.suggestion, line.count)
                                is StrategyLine.Row -> {
                                    val dip = line.dip
                                    val holding = held.any { it.equals(dip.symbol, true) }
                                    DipRow(
                                        dip = dip,
                                        expandNews = opened.equals(dip.symbol, true),
                                        holding = holding,
                                        onAdd = if (!holding && dip.suggestion != DipSuggestion.SKIP && dip.limitPrice > 0.0) {
                                            { onAdd(dip) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionHead(suggestion: DipSuggestion, count: Int) {
    val color = when (suggestion) {
        DipSuggestion.BUY -> Profit
        DipSuggestion.WAIT -> Amber
        DipSuggestion.SKIP -> Loss
    }
    Text(
        text = "${suggestion.label}  ·  $count",
        modifier = Modifier.padding(top = 16.dp),
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )
}

@Composable
private fun StrategyCheck(
    query: String,
    onQuery: (String) -> Unit,
    lookup: SymbolLoad,
    held: Set<String>,
    onCheck: () -> Unit,
    onAdd: (DipCheck) -> Unit,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiveRefresh(onRefresh = onRefresh, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onQuery(it.uppercase()) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Stock") },
                    placeholder = { Text("INFY") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { onCheck() }),
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
                Text(
                    text = "Check",
                    modifier = Modifier
                        .clickable(onClick = onCheck)
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
            when (lookup) {
                SymbolLoad.Idle -> Text(
                    text = "Type a symbol, such as INFY.",
                    modifier = Modifier.padding(top = 16.dp),
                    color = Mist,
                    fontSize = 13.sp,
                )
                SymbolLoad.Loading -> Text(
                    text = "Checking ${query.trim().ifBlank { "this name" }}…",
                    modifier = Modifier.padding(top = 16.dp),
                    color = Mist,
                    fontSize = 13.sp,
                )
                is SymbolLoad.Unavailable -> Text(
                    text = lookup.message,
                    modifier = Modifier.padding(top = 16.dp),
                    color = Mist,
                    fontSize = 13.sp,
                )
                is SymbolLoad.Ready -> DipRow(
                    dip = lookup.result.check,
                    expandNews = false,
                    holding = held.any { it.equals(lookup.result.check.symbol, true) },
                    capLabel = lookup.result.cap?.label,
                    onAdd = if (
                        !held.any { it.equals(lookup.result.check.symbol, true) } &&
                        lookup.result.check.suggestion != DipSuggestion.SKIP &&
                        lookup.result.check.limitPrice > 0.0
                    ) {
                        { onAdd(lookup.result.check) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun StrategyScore(
    scores: List<BuyScore>,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiveRefresh(onRefresh = onRefresh, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        ) {
            LastCheck(scores)
        }
    }
}

@Composable
private fun CapTab(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun LastCheck(scores: List<BuyScore>) {
    if (scores.isEmpty()) {
        Text(
            text = "No buy is waiting to be scored.",
            color = Mist,
            fontSize = 13.sp,
        )
        return
    }
    val today = tradingDay(LocalDate.now(India)).toEpochDay()
    val waiting = scores.filter { it.outcome == null && it.epochDay == today }
    val latest = scores.filter { it.outcome != null }.maxOfOrNull { it.epochDay }
    val shown = scores.filter { it.epochDay == latest && it.outcome != null }.sortedBy { it.symbol }
    if (shown.isEmpty() && waiting.isEmpty()) {
        Text(
            text = "No buy is waiting to be scored.",
            color = Mist,
            fontSize = 13.sp,
        )
        return
    }
    Column {
        if (shown.isNotEmpty()) {
            val targets = shown.count { it.outcome == BuyOutcome.TARGET }
            val stops = shown.count { it.outcome == BuyOutcome.STOP }
            val opens = shown.count { it.outcome == BuyOutcome.OPEN }
            Text("Last session", color = Mist, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text(
                text = "$targets target · $stops stop · $opens open",
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                color = Paper,
                fontSize = 13.sp,
            )
            shown.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = row.symbol,
                        modifier = Modifier.weight(1f),
                        color = Paper,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (row.outcome) {
                            BuyOutcome.TARGET -> "Target"
                            BuyOutcome.STOP -> "Stop"
                            BuyOutcome.OPEN -> "Open"
                            null -> ""
                        },
                        color = when (row.outcome) {
                            BuyOutcome.TARGET -> Profit
                            BuyOutcome.STOP -> Loss
                            else -> Mist
                        },
                        fontSize = 14.sp,
                    )
                }
            }
        }
        if (waiting.isNotEmpty()) {
            Text(
                text = "Pending next session",
                modifier = Modifier.padding(top = if (shown.isEmpty()) 0.dp else 8.dp),
                color = Mist,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            waiting.sortedBy { it.symbol }.forEach { row ->
                Text(
                    text = row.symbol,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = Paper,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DipRow(
    dip: DipCheck,
    expandNews: Boolean,
    holding: Boolean,
    capLabel: String? = null,
    onAdd: (() -> Unit)?,
) {
    var newsOpen by rememberSaveable(dip.symbol) { mutableStateOf(false) }
    LaunchedEffect(expandNews) {
        if (expandNews) newsOpen = true
    }
    val suggestionColor = when (dip.suggestion) {
        DipSuggestion.BUY -> Profit
        DipSuggestion.WAIT -> Amber
        DipSuggestion.SKIP -> Loss
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onAdd != null) Modifier.clickable(onClick = onAdd) else Modifier),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dip.symbol,
                    modifier = Modifier.weight(1f),
                    color = Paper,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
                Text(
                    text = signedPercent(dip.changePercent),
                    color = pnlColor(dip.changePercent),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
            }
            Text(
                text = if (capLabel.isNullOrBlank()) rupees(dip.price) else "$capLabel  ·  ${rupees(dip.price)}",
                color = Mist,
                fontSize = 13.sp,
            )
            Text(
                text = dip.week.label,
                modifier = Modifier.padding(top = 4.dp),
                color = Paper,
                fontSize = 13.sp,
            )
            val span = listOfNotNull(
                dip.weekChange?.let { "Week ${signedPercent(it)}" },
                dip.monthChange?.let { "Month ${signedPercent(it)}" },
            ).joinToString("  ·  ")
            if (span.isNotEmpty()) {
                Text(text = span, color = Mist, fontSize = 13.sp)
            }
            Text(
                text = "Risk ${dip.dangerPercent}%",
                modifier = Modifier.padding(top = 2.dp),
                color = when (dip.danger) {
                    Danger.LOW -> Profit
                    Danger.MEDIUM -> Amber
                    Danger.HIGH -> Loss
                },
                fontSize = 13.sp,
            )
            Text(
                text = dip.suggestion.label,
                modifier = Modifier.padding(top = 6.dp),
                color = suggestionColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Start,
            )
            if (dip.limitPrice > 0.0) {
                Text(
                    text = "Limit ${rupees(dip.limitPrice)}",
                    modifier = Modifier.padding(top = 2.dp),
                    color = Paper,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
                Text(text = dip.limitNote, color = Mist, fontSize = 13.sp)
                if (dip.stopPrice > 0.0 && dip.targetPrice > 0.0) {
                    Text(
                        text = "Stop loss ${rupees(dip.stopPrice)}   ·   Target ${rupees(dip.targetPrice)}",
                        modifier = Modifier.padding(top = 2.dp),
                        color = Paper,
                        fontSize = 13.sp,
                    )
                }
                if (holding) {
                    Text(
                        text = "Already open",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Amber,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                } else if (onAdd != null) {
                    Text(
                        text = "Tap to add · ₹1,00,000",
                        modifier = Modifier.padding(top = 4.dp),
                        color = Accent,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }
            Text(
                text = dip.reason,
                modifier = Modifier.padding(top = 2.dp),
                color = Mist,
                fontSize = 13.sp,
            )
        }
        StrategyNews(
            dip = dip,
            open = newsOpen,
            onToggle = { newsOpen = !newsOpen },
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 14.dp),
            color = Line,
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun StrategyNews(dip: DipCheck, open: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(Wash, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "News",
                modifier = Modifier.weight(1f),
                color = Paper,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                text = if (open) "Hide" else "Show",
                modifier = Modifier.clickable(onClick = onToggle).padding(vertical = 4.dp),
                color = Accent,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
        if (!open) return@Column
        Text(
            text = dip.newsNote,
            modifier = Modifier.padding(top = 8.dp),
            color = Paper,
            fontSize = 13.sp,
        )
        if (dip.danger == Danger.HIGH && dip.week == WeekShape.STILL_FALLING &&
            dip.headlines.none { it.read.tone == HeadlineTone.NEGATIVE && it.read.percent >= 90 }
        ) {
            Text(
                text = "Risk is high because the week is still falling, not because of a headline.",
                modifier = Modifier.padding(top = 6.dp),
                color = Mist,
                fontSize = 12.sp,
            )
        }
        if (dip.headlines.isEmpty()) {
            Text(
                text = "No headline found for this name.",
                modifier = Modifier.padding(top = 8.dp),
                color = Mist,
                fontSize = 13.sp,
            )
            return@Column
        }
        dip.headlines.forEach { item ->
            StrategyHeadline(item)
        }
    }
}

@Composable
private fun StrategyHeadline(item: StockHeadline) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var opening by remember(item.link) { mutableStateOf(false) }
    val effect = headlineDanger(item.read)
    val toneColor = when (item.read.tone) {
        HeadlineTone.POSITIVE -> Profit
        HeadlineTone.NEGATIVE -> Loss
        HeadlineTone.MIXED -> Mist
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(enabled = !opening) {
                scope.openArticle(context, item.link) { opening = it }
            },
    ) {
        HorizontalDivider(color = Line, thickness = 0.5.dp)
        Text(
            modifier = Modifier.padding(top = 10.dp),
            text = item.title,
            color = Paper,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val age = newsAge(item.publishedAt)
        Text(
            text = buildString {
                append(item.source)
                if (age.isNotEmpty()) append("  ·  $age")
                append("  ·  ${item.read.tone.label} ${item.read.percent}%")
            },
            modifier = Modifier.padding(top = 2.dp),
            color = toneColor,
            fontSize = 12.sp,
        )
        Text(
            text = if (opening) "Opening…" else "Open",
            modifier = Modifier.padding(top = 2.dp),
            color = Accent,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
        )
        Text(
            text = effect,
            color = when {
                item.read.tone == HeadlineTone.NEGATIVE && item.read.percent >= 90 -> Loss
                item.read.tone == HeadlineTone.NEGATIVE -> Amber
                else -> Profit
            },
            fontSize = 12.sp,
        )
    }
}
