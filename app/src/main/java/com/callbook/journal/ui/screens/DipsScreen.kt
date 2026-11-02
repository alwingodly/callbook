package com.callbook.journal.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
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
import com.callbook.journal.domain.inMarketHours
import com.callbook.journal.domain.newsAge
import com.callbook.journal.domain.rupees
import com.callbook.journal.domain.signedPercent
import com.callbook.journal.ui.StrategyLink
import com.callbook.journal.ui.openArticle
import com.callbook.journal.ui.theme.Accent
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

@Composable
fun DipsScreen(
    dips: DipLoad,
    onLoad: (CapSize, Boolean, Boolean) -> Unit,
    focus: StrategyLink? = null,
    onFocusHandled: () -> Unit = {},
    onAdd: (DipCheck) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var capName by rememberSaveable { mutableStateOf(CapSize.LARGE.name) }
    val cap = CapSize.entries.firstOrNull { it.name == capName } ?: CapSize.LARGE
    var playName by rememberSaveable { mutableStateOf(StrategyPlay.DIP.name) }
    val play = StrategyPlay.entries.firstOrNull { it.name == playName } ?: StrategyPlay.DIP
    val listState = rememberLazyListState()
    var opened by rememberSaveable { mutableStateOf("") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val currentCap = rememberUpdatedState(cap)
    val currentPlay = rememberUpdatedState(play)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(cap, play) {
        onLoad(cap, play == StrategyPlay.DIP, false)
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val day = LocalDate.now(India)
            val time = LocalTime.now(India)
            if (!isMarketWeekday(day.dayOfWeek) || !inMarketHours(time)) continue
            val shownCap = currentCap.value
            val shownDip = currentPlay.value == StrategyPlay.DIP
            onLoad(shownCap, shownDip, true)
        }
    }
    LaunchedEffect(focus, dips) {
        val target = focus ?: return@LaunchedEffect
        capName = target.cap
        playName = target.play
        if (dips !is DipLoad.Ready) return@LaunchedEffect
        val board = dips.boards[CapSize.entries.firstOrNull { it.name == target.cap }]
        val pause = target.play == StrategyPlay.PAUSE.name
        val loaded = if (pause) board?.pausesLoaded == true else board?.dipsLoaded == true
        if (!loaded) return@LaunchedEffect
        val rows = if (pause) board?.pauses else board?.dips
        val index = rows.orEmpty().indexOfFirst { it.symbol.equals(target.symbol, true) }
        if (index >= 0) {
            opened = target.symbol
            listState.animateScrollToItem(index)
        }
        onFocusHandled()
    }
    val ready = dips as? DipLoad.Ready
    val board = ready?.boards?.get(cap)
    val playLoaded = if (play == StrategyPlay.DIP) board?.dipsLoaded == true else board?.pausesLoaded == true
    val shown = when (play) {
        StrategyPlay.DIP -> board?.dips.orEmpty()
        StrategyPlay.PAUSE -> board?.pauses.orEmpty()
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
                Text(
                    text = "Check",
                    modifier = Modifier.clickable { onLoad(cap, play == StrategyPlay.DIP, true) }.padding(vertical = 8.dp),
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
            Text(
                text = board?.let {
                    val stamp = ready?.asOf?.takeIf { it.isNotBlank() }?.let { "  ·  $it" }.orEmpty()
                    "${cap.label}  ${signedPercent(it.marketPercent)}$stamp"
                } ?: "Dip or pause. Large, mid, and small.",
                color = Mist,
                fontSize = 13.sp,
            )
            Text(
                text = "This list updates every minute. Buy alerts run 9:15 to 15:30 on large dips.",
                modifier = Modifier.padding(top = 2.dp),
                color = Mist,
                fontSize = 12.sp,
            )
            Text(
                text = if (play == StrategyPlay.PAUSE) {
                    "Already rising, then a small dip. Target is the recent high."
                } else {
                    "Down 1% or more. Target is yesterday's close when it pays."
                },
                modifier = Modifier.padding(top = 4.dp),
                color = Mist,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                StrategyPlay.entries.forEach { option ->
                    CapTab(option.label, option == play) { playName = option.name }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CapSize.entries.forEach { option ->
                    CapTab(option.label, option == cap) { capName = option.name }
                }
            }
        }
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
                    "No ${cap.label.lowercase()} cap is down 1% or more."
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = Mist,
            )
            board != null -> LazyColumn(
                state = listState,
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                items(shown, key = { it.symbol }) { dip ->
                    DipRow(
                        dip = dip,
                        expandNews = opened.equals(dip.symbol, true),
                        onAdd = if (dip.suggestion != DipSuggestion.SKIP && dip.limitPrice > 0.0) {
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
private fun DipRow(dip: DipCheck, expandNews: Boolean, onAdd: (() -> Unit)?) {
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
                    color = Loss,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
            }
            Text(text = rupees(dip.price), color = Mist, fontSize = 13.sp)
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
                text = "Danger ${dip.dangerPercent}%",
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
                        text = "Stop ${rupees(dip.stopPrice)}   ·   Target ${rupees(dip.targetPrice)}",
                        modifier = Modifier.padding(top = 2.dp),
                        color = Paper,
                        fontSize = 13.sp,
                    )
                }
                if (onAdd != null) {
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
                text = "Danger is high because the week is still falling, not because of a headline.",
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
