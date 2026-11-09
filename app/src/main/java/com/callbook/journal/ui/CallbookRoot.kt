package com.callbook.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callbook.journal.domain.alreadyHolding
import com.callbook.journal.domain.availableCash
import com.callbook.journal.domain.insightsOf
import com.callbook.journal.domain.judgement
import com.callbook.journal.domain.ResultKind
import com.callbook.journal.ui.screens.DetailScreen
import com.callbook.journal.ui.screens.EditCallScreen
import com.callbook.journal.ui.screens.InsightsScreen
import com.callbook.journal.ui.screens.DipsScreen
import com.callbook.journal.ui.screens.JournalScreen
import com.callbook.journal.ui.screens.SettingsScreen
import com.callbook.journal.domain.SectorLoad
import com.callbook.journal.ui.screens.SectorScreen
import com.callbook.journal.ui.screens.SectorsScreen
import com.callbook.journal.ui.screens.TodayScreen
import com.callbook.journal.ui.theme.Canvas
import com.callbook.journal.ui.theme.Ink
import com.callbook.journal.ui.theme.InkRaised
import com.callbook.journal.ui.theme.Line
import com.callbook.journal.ui.theme.Mist

private object Routes {
    const val Today = "today"
    const val Journal = "journal"
    const val Insights = "insights"
    const val Dips = "dips"
    const val Sectors = "sectors"
    const val Sector = "sector/{name}"
    const val Settings = "settings"
    const val Edit = "edit?id={id}"
    const val Detail = "detail/{id}"

    fun edit(id: Long = -1L) = "edit?id=$id"
    fun detail(id: Long) = "detail/$id"
    fun sector(name: String) = "sector/$name"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.Today, "Today", Icons.Filled.DateRange),
    Tab(Routes.Journal, "Stocks", Icons.AutoMirrored.Filled.List),
    Tab(Routes.Dips, "Strategy", Icons.Filled.Notifications),
    Tab(Routes.Sectors, "Sectors", Icons.Filled.GridView),
    Tab(Routes.Insights, "Money", Icons.Filled.Info),
)

@Composable
fun CallbookRoot(
    viewModel: JournalViewModel,
    strategyLink: StrategyLink? = null,
    onStrategyLinkHandled: () -> Unit = {},
) {
    val nav = rememberNavController()
    val load by viewModel.load.collectAsStateWithLifecycle()
    val day by viewModel.selectedEpochDay.collectAsStateWithLifecycle()
    val banked by viewModel.banked.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val chartRange by viewModel.chartRange.collectAsStateWithLifecycle()
    val nowPrices by viewModel.nowPrices.collectAsStateWithLifecycle()
    val capBadges by viewModel.capBadges.collectAsStateWithLifecycle()
    val pricesLive by viewModel.pricesLive.collectAsStateWithLifecycle()
    val chartSpot by viewModel.chartSpot.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val dips by viewModel.dips.collectAsStateWithLifecycle()
    val symbolCheck by viewModel.symbolCheck.collectAsStateWithLifecycle()
    val sectors by viewModel.sectors.collectAsStateWithLifecycle()
    val buyScores by viewModel.buyScores.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pricesLive) {
        if (!pricesLive) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                viewModel.refreshOpenPrices(fresh = true)
            }
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(15_000)
                viewModel.refreshShownPrice()
            }
        }
    }
    LaunchedEffect(strategyLink) {
        if (strategyLink == null) return@LaunchedEffect
        nav.navigate(Routes.Dips) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val filter by viewModel.journalFilter.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val predictions = (load as? JournalLoad.Ready)?.predictions.orEmpty()
    val held = remember(predictions) {
        predictions
            .filter { it.judgement().kind == ResultKind.OPEN }
            .map { it.symbol.trim().uppercase() }
            .toSet()
    }
    val loading = load is JournalLoad.Loading
    val showChrome = route == Routes.Today || route == Routes.Journal || route == Routes.Insights ||
        route == Routes.Dips || route == Routes.Sectors || route == Routes.Settings
    Box(modifier = Modifier.fillMaxSize().background(Canvas)) {
        NavHost(navController = nav, startDestination = Routes.Today, modifier = Modifier.fillMaxSize()) {
            composable(Routes.Today) {
                TodayScreen(
                    day = day,
                    predictions = predictions,
                    loading = loading,
                    onPrevious = { viewModel.shiftDay(-1) },
                    onNext = { viewModel.shiftDay(1) },
                    onJumpToday = viewModel::jumpToToday,
                    onOpen = { id -> nav.navigate(Routes.detail(id)) },
                    nowPrices = nowPrices,
                    caps = capBadges,
                    pricesLive = pricesLive,
                    onPrices = viewModel::toggleLivePrices,
                    onNew = { nav.navigate(Routes.edit()) },
                    onSettings = { nav.openRoot(Routes.Settings) },
                    onRefresh = viewModel::refreshJournal,
                )
            }
            composable(Routes.Journal) {
                JournalScreen(
                    predictions = predictions,
                    loading = loading,
                    filter = filter,
                    onFilter = viewModel::setFilter,
                    onOpen = { id -> nav.navigate(Routes.detail(id)) },
                    nowPrices = nowPrices,
                    caps = capBadges,
                    pricesLive = pricesLive,
                    onPrices = viewModel::toggleLivePrices,
                    onNew = { nav.navigate(Routes.edit()) },
                    onRefresh = viewModel::refreshJournal,
                )
            }
            composable(Routes.Dips) {
                DipsScreen(
                    dips = dips,
                    lookup = symbolCheck,
                    onCheck = viewModel::checkStock,
                    onRefresh = { cap, dipsPlay, symbol ->
                        viewModel.refreshBoard(cap, dipsPlay)
                        if (symbol.isNotBlank()) viewModel.refreshSymbol(symbol)
                    },
                    onLoad = viewModel::loadBoard,
                    focus = strategyLink,
                    onFocusHandled = onStrategyLinkHandled,
                    held = held,
                    scores = buyScores,
                    onAdd = { dip ->
                        if (!alreadyHolding(predictions, dip.symbol)) {
                            val free = availableCash(banked, insightsOf(predictions))
                            viewModel.stageDip(dip, free)
                            nav.navigate(Routes.edit())
                        }
                    },
                )
            }
            composable(Routes.Sectors) {
                SectorsScreen(
                    sectors = sectors,
                    onLoad = viewModel::loadSectors,
                    onRefresh = viewModel::refreshSectors,
                    onOpen = { name -> nav.navigate(Routes.sector(name)) },
                )
            }
            composable(
                route = Routes.Sector,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                val name = entry.arguments?.getString("name").orEmpty()
                val read = (sectors as? SectorLoad.Ready)?.sectors?.firstOrNull { it.sector.name == name }
                SectorScreen(
                    sector = read,
                    loading = sectors is SectorLoad.Loading || sectors is SectorLoad.Idle,
                    onBack = { nav.popBackStack() },
                    onLoad = viewModel::loadSectors,
                    onRefresh = viewModel::refreshSectors,
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(onBack = { nav.openRoot(Routes.Today) })
            }
            composable(Routes.Insights) {
                InsightsScreen(
                    predictions = predictions,
                    loading = loading,
                    banked = banked,
                    onAddFromBank = viewModel::addFromBank,
                    onReturnToBank = viewModel::returnToBank,
                    onRefresh = viewModel::refreshJournal,
                )
            }
            composable(
                route = Routes.Edit,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                val existing = predictions.firstOrNull { it.id == id }
                val prefill = remember { viewModel.takeDipDraft() }
                EditCallScreen(
                    existing = existing,
                    loading = loading && id > 0,
                    missing = !loading && id > 0 && existing == null,
                    prefill = prefill,
                    initialDay = day,
                    available = availableCash(banked, insightsOf(predictions), existing),
                    onAddFromBank = viewModel::addFromBank,
                    onBack = { nav.popBackStack() },
                    holding = { symbol -> alreadyHolding(predictions, symbol, existing?.id ?: 0L) },
                    onSave = { prediction ->
                        viewModel.persist(prediction)
                        viewModel.selectDay(prediction.epochDay)
                        nav.popBackStack()
                    },
                )
            }
            composable(
                route = Routes.Detail,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                val existing = predictions.firstOrNull { it.id == id }
                DetailScreen(
                    prediction = existing,
                    loading = loading,
                    chart = chart,
                    chartRange = chartRange,
                    nowPrices = nowPrices,
                    watchedPrice = chartSpot?.takeIf { it.first.equals(existing?.symbol, ignoreCase = true) }?.second,
                    onChartRange = viewModel::setChartSpan,
                    onShowChart = viewModel::showChart,
                    onHideChart = viewModel::hideChart,
                    news = news,
                    onShowNews = viewModel::showNews,
                    onHideNews = viewModel::hideNews,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.edit(id)) },
                    onSaveOutcome = { price, mark, manual -> viewModel.saveOutcome(id, price, mark, manual) },
                    onReopen = { viewModel.reopen(id) },
                    onDelete = {
                        viewModel.delete(id)
                        nav.popBackStack()
                    },
                    onRefresh = { viewModel.refreshDetail(existing?.symbol.orEmpty()) },
                )
            }
        }
        if (showChrome) {
            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .drawBehind {
                        drawLine(
                            color = Line,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
                containerColor = InkRaised,
                contentColor = Mist,
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.openRoot(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink,
                            selectedTextColor = Ink,
                            unselectedIconColor = Mist,
                            unselectedTextColor = Mist,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

/** Tabs and Settings sit side by side. Today restores Today, not a screen opened on top of it. */
private fun NavHostController.openRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
