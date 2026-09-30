package dev.x.opusone.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.theme.OpusOneShapes
import dev.x.opusone.ui.components.OpusOneNavigationBar
import dev.x.opusone.util.toScript

/**
 * 探索主页（宏观史学大观枢纽）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToWiki: () -> Unit = {},
    onNavigateToGraph: () -> Unit = {},
    onNavigateToTimeline: () -> Unit = {},
    onNavigateToWars: () -> Unit = {},
    onNavigateToChengyu: () -> Unit = {},
    onNavigateToTaiShiGong: () -> Unit = {}
) {
    val scriptMode = LocalChineseScript.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "探索".toScript(scriptMode),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            OpusOneNavigationBar(
                selectedItem = 1,
                onItemSelected = { index ->
                    when (index) {
                        0 -> onNavigateToHome()
                        2 -> onNavigateToBookmarks()
                        3 -> onNavigateToSettings()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        ExploreHubContent(
            onNavigateToTimeline = onNavigateToTimeline,
            onNavigateToGraph = onNavigateToGraph,
            onNavigateToWiki = onNavigateToWiki,
            onNavigateToWars = onNavigateToWars,
            onNavigateToChengyu = onNavigateToChengyu,
            onNavigateToTaiShiGong = onNavigateToTaiShiGong,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}

/**
 * 探索页内容区。
 *
 * 排印层级约定（避免「分区标签」与「卡片标题」同构）：
 * - 专区标题用 `titleSmall`（14sp）+ primary 色——它是**标签**，尺寸应小于其下的内容。
 * - 卡片标题用 `titleMedium`（16sp）+ onSurface——它是**内容**，尺寸更大、字色更亮。
 * 两者字重均为档位内的 Medium(500)，层级差由「字号 + 色彩」承担，不再靠临时加粗。
 * 页面任何位置**不得**用 `.copy(fontWeight = …)` 覆写字重——强调一律走 type scale。
 *
 * 间距基准 4dp：页边距 20 / 区块间 20 / 区内行距 12 / 网格列距 16 / 卡内边距 16。
 */
@Composable
private fun ExploreHubContent(
    onNavigateToTimeline: () -> Unit,
    onNavigateToGraph: () -> Unit,
    onNavigateToWiki: () -> Unit,
    onNavigateToWars: () -> Unit,
    onNavigateToChengyu: () -> Unit,
    onNavigateToTaiShiGong: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scriptMode = LocalChineseScript.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "宏观时空".toScript(scriptMode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExploreActionCard(
                    icon = Icons.Filled.Timeline,
                    title = "编年长河".toScript(scriptMode),
                    onClick = onNavigateToTimeline,
                    modifier = Modifier.weight(1f)
                )
                ExploreActionCard(
                    icon = Icons.Filled.AccountTree,
                    title = "世系源流".toScript(scriptMode),
                    onClick = onNavigateToGraph,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "古籍专题".toScript(scriptMode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExploreActionCard(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "知识索引".toScript(scriptMode),
                    onClick = onNavigateToWiki,
                    modifier = Modifier.weight(1f)
                )
                ExploreActionCard(
                    icon = Icons.Filled.FormatQuote,
                    title = "太史公曰".toScript(scriptMode),
                    onClick = onNavigateToTaiShiGong,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExploreActionCard(
                    icon = Icons.Filled.AutoStories,
                    title = "成语典故".toScript(scriptMode),
                    onClick = onNavigateToChengyu,
                    modifier = Modifier.weight(1f)
                )
                ExploreActionCard(
                    icon = Icons.Filled.Shield,
                    title = "经典战役".toScript(scriptMode),
                    onClick = onNavigateToWars,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 探索入口卡片组件。
 */
@Composable
private fun ExploreActionCard(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = OpusOneShapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                // 图标为装饰性：卡片标题已由下方 Text 提供语义。
                // 若在此重复填写 title，TalkBack 会把标题播报两遍。
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            val scriptMode = LocalChineseScript.current
            Text(
                text = title.toScript(scriptMode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
