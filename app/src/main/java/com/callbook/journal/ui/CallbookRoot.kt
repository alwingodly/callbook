package com.callbook.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TrendingDown
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callbook.journal.domain.availableCash
import com.callbook.journal.domain.insightsOf
import com.callbook.journal.ui.screens.DetailScreen
import com.callbook.journal.ui.screens.EditCallScreen
import com.callbook.journal.ui.screens.InsightsScreen
import com.callbook.journal.ui.screens.DipsScreen
import com.callbook.journal.ui.screens.JournalScreen
import com.callbook.journal.ui.screens.LosersScreen
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
    const val Losers = "losers"
    const val Dips = "dips"
    const val Edit = "edit?id={id}"
    const val Detail = "detail/{id}"

    fun edit(id: Long = -1L) = "edit?id=$id"
    fun detail(id: Long) = "detail/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.Today, "Today", Icons.Filled.DateRange),
    Tab(Routes.Journal, "Stocks", Icons.AutoMirrored.Filled.List),
    Tab(Routes.Losers, "Losers", Icons.Filled.TrendingDown),
    Tab(Routes.Dips, "Strategy", Icons.Filled.Notifications),
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
    val pricesLive by viewModel.pricesLive.collectAsStateWithLifecycle()
    val chartSpot by viewModel.chartSpot.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val losers by viewModel.losers.collectAsStateWithLifecycle()
    val dips by viewModel.dips.collectAsStateWithLifecycle()
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
    val loading = load is JournalLoad.Loading
    val showChrome = route == Routes.Today || route == Routes.Journal || route == Routes.Insights ||
        route == Routes.Losers || route == Routes.Dips
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
                    pricesLive = pricesLive,
                    onPrices = viewModel::toggleLivePrices,
                    onNew = { nav.navigate(Routes.edit()) },
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
                    pricesLive = pricesLive,
                    onPrices = viewModel::toggleLivePrices,
                    onNew = { nav.navigate(Routes.edit()) },
                )
            }
            composable(Routes.Losers) {
                LosersScreen(
                    losers = losers,
                    onLoad = viewModel::loadLosers,
                )
            }
            composable(Routes.Dips) {
                DipsScreen(
                    dips = dips,
                    onLoad = viewModel::loadDips,
                    focus = strategyLink,
                    onFocusHandled = onStrategyLinkHandled,
                    onAdd = { dip ->
                        viewModel.stageDip(dip)
                        nav.navigate(Routes.edit())
                    },
                )
            }
            composable(Routes.Insights) {
                InsightsScreen(
                    predictions = predictions,
                    loading = loading,
                    banked = banked,
                    onAddFromBank = viewModel::addFromBank,
                    onReturnToBank = viewModel::returnToBank,
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
                    onSaveOutcome = { price, mark -> viewModel.saveOutcome(id, price, mark) },
                    onReopen = { viewModel.reopen(id) },
                    onDelete = {
                        viewModel.delete(id)
                        nav.popBackStack()
                    },
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
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
