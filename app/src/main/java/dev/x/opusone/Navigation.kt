package dev.x.opusone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.theme.DarkMode
import dev.x.opusone.theme.ThemeStyle
import dev.x.opusone.ui.bookmarks.BookmarksScreen
import dev.x.opusone.ui.chengyu.ChengyuScreen
import dev.x.opusone.ui.entities.EntityWikiScreen
import dev.x.opusone.ui.graph.KnowledgeGraphScreen
import dev.x.opusone.ui.home.HomeScreen
import dev.x.opusone.ui.reader.ReaderScreen
import dev.x.opusone.ui.reader.ReaderViewModel
import dev.x.opusone.ui.search.SearchScreen
import dev.x.opusone.ui.taishigong.TaiShiGongYueScreen
import dev.x.opusone.ui.timeline.ChronicleTimelineScreen
import dev.x.opusone.ui.wars.WarsScreen

sealed interface Screen {
    data object Home : Screen
    data class Reader(val chapterId: Int, val targetPn: String? = null) : Screen
    data object Search : Screen
    data object Chengyu : Screen
    data object Wars : Screen
    data object Wiki : Screen
    data object Graph : Screen
    data object Timeline : Screen
    data object TaiShiGongYue : Screen
    data object Bookmarks : Screen
    data object Settings : Screen
}

/** 底部导航栏对应的顶层目的地（MD3 语义：平级切换，不层层压栈）。 */
private val TOP_LEVEL_TABS = setOf<Screen>(Screen.Home, Screen.Search, Screen.Bookmarks, Screen.Settings)

/**
 * 返回栈的持久化编解码。
 *
 * 使用 rememberSaveable + Saver 机制保存返回栈，支持系统回收重建后恢复状态。
 */
private fun encodeScreen(screen: Screen): String = when (screen) {
    is Screen.Home -> "home"
    is Screen.Reader -> "reader|${screen.chapterId}|${screen.targetPn ?: ""}"
    is Screen.Search -> "search"
    is Screen.Chengyu -> "chengyu"
    is Screen.Wars -> "wars"
    is Screen.Wiki -> "wiki"
    is Screen.Graph -> "graph"
    is Screen.Timeline -> "timeline"
    is Screen.TaiShiGongYue -> "taishigongyue"
    is Screen.Bookmarks -> "bookmarks"
    is Screen.Settings -> "settings"
}

private fun decodeScreen(value: String): Screen {
    val parts = value.split("|")
    return when (parts.getOrNull(0)) {
        "reader" -> Screen.Reader(
            chapterId = parts.getOrNull(1)?.toIntOrNull() ?: 1,
            targetPn = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
        )
        "search" -> Screen.Search
        "chengyu" -> Screen.Chengyu
        "wars" -> Screen.Wars
        "wiki" -> Screen.Wiki
        "graph" -> Screen.Graph
        "timeline" -> Screen.Timeline
        "taishigongyue" -> Screen.TaiShiGongYue
        "bookmarks" -> Screen.Bookmarks
        "settings" -> Screen.Settings
        else -> Screen.Home
    }
}

private val backStackSaver = listSaver<List<Screen>, String>(
    save = { stack -> stack.map(::encodeScreen) },
    restore = { saved -> saved.map(::decodeScreen) }
)

@Composable
fun MainNavigation(
    currentThemeStyle: ThemeStyle,
    currentDarkMode: DarkMode,
    onSetTheme: (ThemeStyle) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    currentScript: ChineseScriptMode = ChineseScriptMode.SIMPLIFIED,
    onSetScript: (ChineseScriptMode) -> Unit = {}
) {
    var backStack by rememberSaveable(stateSaver = backStackSaver) {
        mutableStateOf(listOf<Screen>(Screen.Home))
    }
    var isBackNav by remember { mutableStateOf(false) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home

    fun navigateTo(screen: Screen) {
        // 目的地在栈顶时不再重复压栈，避免返回栈冗余增长
        if (backStack.lastOrNull() == screen) return
        isBackNav = false
        backStack = backStack + screen
    }

    /**
     * 顶层目的地切换（底部导航栏专用）：
     * 始终以 Home 为根，其余 tab 互斥平级替换，返回键直接回到 Home。
     */
    fun navigateToTab(screen: Screen) {
        // 只有 TOP_LEVEL_TABS 里的目的地才允许走「平级替换」，其余按普通压栈处理
        if (screen !in TOP_LEVEL_TABS) {
            navigateTo(screen)
            return
        }
        if (screen == Screen.Home) {
            if (currentScreen != Screen.Home) {
                isBackNav = true
                backStack = listOf(Screen.Home)
            }
            return
        }
        // Tab 间平级切换，转场方向固定为前进
        isBackNav = false
        backStack = listOf(Screen.Home, screen)
    }

    fun popBack() {
        if (backStack.size > 1) {
            isBackNav = true
            backStack = backStack.dropLast(1)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        popBack()
    }

    AnimatedContent(
        targetState = currentScreen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val isReaderSwap = initialState is Screen.Reader && targetState is Screen.Reader
            val isTabSwap = initialState in TOP_LEVEL_TABS && targetState in TOP_LEVEL_TABS
            if (isReaderSwap || isTabSwap) {
                // 平级切换或章节切换使用交叉淡入淡出，保持底栏稳定
                fadeIn(animationSpec = tween(200)) togetherWith
                    fadeOut(animationSpec = tween(150))
            } else {
                // 标准 Shared Axis X 轴水平位移转场
                val enter = slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { width -> if (isBackNav) -width / 4 else width } +
                    fadeIn(animationSpec = tween(220))
                val exit = slideOutHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing)
                ) { width -> if (isBackNav) width else -width / 10 } +
                    fadeOut(animationSpec = tween(200))
                enter.togetherWith(exit)
            }
        },
    ) { screen ->
        when (screen) {
            is Screen.Home -> {
                HomeScreen(
                    onNavigateToChapter = { chapId -> navigateTo(Screen.Reader(chapId)) },
                    onNavigateToSearch = { navigateToTab(Screen.Search) },
                    onNavigateToBookmarks = { navigateToTab(Screen.Bookmarks) },
                    onNavigateToSettings = { navigateToTab(Screen.Settings) }
                )
            }

            is Screen.Reader -> {
                val readerViewModel: ReaderViewModel = viewModel(key = "reader_${screen.chapterId}")
                ReaderScreen(
                    chapterId = screen.chapterId,
                    targetSectionPn = screen.targetPn,
                    onBackClick = { popBack() },
                    currentScript = currentScript,
                    viewModel = readerViewModel
                )
            }

            is Screen.Search -> {
                SearchScreen(
                    onNavigateToHome = { navigateToTab(Screen.Home) },
                    onNavigateToBookmarks = { navigateToTab(Screen.Bookmarks) },
                    onNavigateToSettings = { navigateToTab(Screen.Settings) },
                    onNavigateToWiki = { navigateTo(Screen.Wiki) },
                    onNavigateToGraph = { navigateTo(Screen.Graph) },
                    onNavigateToTimeline = { navigateTo(Screen.Timeline) },
                    onNavigateToWars = { navigateTo(Screen.Wars) },
                    onNavigateToChengyu = { navigateTo(Screen.Chengyu) },
                    onNavigateToTaiShiGong = { navigateTo(Screen.TaiShiGongYue) }
                )
            }

            is Screen.Chengyu -> {
                ChengyuScreen(
                    onBackClick = { popBack() },
                    onNavigateToChapter = { chapId, pn ->
                        navigateTo(Screen.Reader(chapId, pn))
                    }
                )
            }

            is Screen.Wars -> {
                WarsScreen(
                    onBackClick = { popBack() },
                    onNavigateToChapter = { chapId, pn ->
                        navigateTo(Screen.Reader(chapId, pn))
                    }
                )
            }

            is Screen.Wiki -> {
                EntityWikiScreen(
                    onBackClick = { popBack() },
                    onNavigateToChapter = { chapId, pn ->
                        navigateTo(Screen.Reader(chapId, pn))
                    }
                )
            }

            is Screen.Graph -> {
                KnowledgeGraphScreen(
                    onBackClick = { popBack() },
                    onNavigateToChapter = { chapId, pn ->
                        navigateTo(Screen.Reader(chapId, pn))
                    }
                )
            }

            is Screen.Timeline -> {
                ChronicleTimelineScreen(
                    onBackClick = { popBack() },
                    onNavigateToChapter = { chapId, pn ->
                        navigateTo(Screen.Reader(chapId, pn))
                    }
                )
            }

            is Screen.TaiShiGongYue -> {
                TaiShiGongYueScreen(
                    onBackClick = { popBack() },
                    onNavigateToChapter = { chapId, pn ->
                        navigateTo(Screen.Reader(chapId, pn))
                    }
                )
            }

            is Screen.Bookmarks -> {
                BookmarksScreen(
                    onBackClick = { popBack() },
                    onNavigateToChapter = { chapId, pn ->
                        navigateTo(Screen.Reader(chapId, pn))
                    },
                    onNavigateToHome = { navigateToTab(Screen.Home) },
                    onNavigateToSearch = { navigateToTab(Screen.Search) },
                    onNavigateToSettings = { navigateToTab(Screen.Settings) }
                )
            }

            is Screen.Settings -> {
                dev.x.opusone.ui.settings.SettingsScreen(
                    currentThemeStyle = currentThemeStyle,
                    currentDarkMode = currentDarkMode,
                    onSetTheme = onSetTheme,
                    onSetDarkMode = onSetDarkMode,
                    currentScript = currentScript,
                    onSetScript = onSetScript,
                    onNavigateToHome = { navigateToTab(Screen.Home) },
                    onNavigateToSearch = { navigateToTab(Screen.Search) },
                    onNavigateToBookmarks = { navigateToTab(Screen.Bookmarks) }
                )
            }
        }
    }
}
