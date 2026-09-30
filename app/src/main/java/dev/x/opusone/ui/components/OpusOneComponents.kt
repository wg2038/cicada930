package dev.x.opusone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.util.cd
import dev.x.opusone.util.toScript

/**
 * 底部导航条目数据结构。
 */
data class OpusOneNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val OPUSONE_NAV_ITEMS = listOf(
    OpusOneNavItem("主页", Icons.Filled.Home, Icons.Outlined.Home),
    OpusOneNavItem("探索", Icons.Filled.Explore, Icons.Outlined.Explore),
    OpusOneNavItem("书签", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
    OpusOneNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

/**
 * 应用程序底部导航栏组件。
 */
@Composable
fun OpusOneNavigationBar(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    items: List<OpusOneNavItem> = OPUSONE_NAV_ITEMS
) {
    val scriptMode = LocalChineseScript.current

    NavigationBar(
        modifier = modifier,
        windowInsets = NavigationBarDefaults.windowInsets,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedItem == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onItemSelected(index) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label.toScript(scriptMode)
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * Material Design 3 风格搜索输入组件。
 * 提供只读入口与实时编辑两种交互模式。
 */
@ExperimentalMaterial3Api
@Composable
fun OpusOneSearchField(
    modifier: Modifier = Modifier,
    query: String = "",
    onQueryChange: ((String) -> Unit)? = null,
    placeholder: String = "搜索",
    onSearch: () -> Unit = {}
) {
    val scriptMode = LocalChineseScript.current
    val editable = onQueryChange != null

    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = { onQueryChange?.invoke(it) },
        onSearch = { onSearch() },
        expanded = false,
        onExpandedChange = { if (it) onSearch() },
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder.toScript(scriptMode),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = cd("搜索"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = if (editable && query.isNotBlank()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = cd("清空"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else null
    )
}

/**
 * 列表分组标题。
 */
@Composable
fun OpusOneSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    val scriptMode = LocalChineseScript.current
    Text(
        text = title.toScript(scriptMode),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 列表空状态占位展示。
 */
@Composable
fun OpusOneEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    val scriptMode = LocalChineseScript.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title.toScript(scriptMode),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description.toScript(scriptMode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 搜索型二级列表页面通用脚手架。
 * 整合顶部栏、搜索框、过滤器、空状态与内容虚拟列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpusOneSearchListScreen(
    title: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchPlaceholder: String,
    onBackClick: () -> Unit,
    isLoading: Boolean,
    itemCount: Int,
    itemNoun: String = "条",
    emptyTitle: String = "没有匹配的条目",
    emptyDescription: String? = null,
    showSearchField: Boolean = true,
    filterRow: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val scriptMode = LocalChineseScript.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title.toScript(scriptMode)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = cd("返回")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (showSearchField) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                ) {
                    OpusOneSearchField(
                        query = searchQuery,
                        onQueryChange = onSearchChange,
                        placeholder = searchPlaceholder,
                        onSearch = {}
                    )
                }
            }

            if (filterRow != null) {
                filterRow()
            }

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                itemCount == 0 -> {
                    OpusOneEmptyState(
                        icon = Icons.Outlined.Search,
                        title = emptyTitle.toScript(scriptMode),
                        description = emptyDescription?.toScript(scriptMode)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (searchQuery.isNotBlank()) {
                            item {
                                Text(
                                    text = "匹配 $itemCount $itemNoun".toScript(scriptMode),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        content()
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * 带有统一卡片样式与触控响应的通用容器。
 */
@Composable
fun OpusOneItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }

    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors
        ) { body() }
    } else {
        ElevatedCard(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors
        ) { body() }
    }
}

/**
 * 条目卡右上角的来源徽标。
 * 使用 tertiaryContainer / onTertiaryContainer，随主题自动反转。
 */
@Composable
fun OpusOneSourceBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
