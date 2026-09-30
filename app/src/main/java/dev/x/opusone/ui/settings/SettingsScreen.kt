package dev.x.opusone.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.x.opusone.BuildConfig
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.theme.DarkMode
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.theme.LocalIsDarkTheme
import dev.x.opusone.theme.ThemePreviewColors
import dev.x.opusone.theme.ThemeStyle
import dev.x.opusone.theme.previewColorsFor
import dev.x.opusone.ui.components.OpusOneNavigationBar
import dev.x.opusone.util.toScript

private const val SECTION_THEME = "theme"
private const val SECTION_DARK = "dark"
private const val SECTION_SCRIPT = "script"

/**
 * 全局设置页。
 *
 * 选项采用列表项内嵌展开（Accordion），支持主题风格与深浅色模式切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentThemeStyle: ThemeStyle,
    currentDarkMode: DarkMode,
    onSetTheme: (ThemeStyle) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    currentScript: ChineseScriptMode,
    onSetScript: (ChineseScriptMode) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToBookmarks: () -> Unit
) {
    val scriptMode = LocalChineseScript.current
    val isDark = LocalIsDarkTheme.current
    var showLicenseDialog by rememberSaveable { mutableStateOf(false) }

    // 手风琴：同时只展开一个选择区。null = 全部收起。
    var expandedSection by rememberSaveable { mutableStateOf<String?>(null) }
    val toggleSection: (String) -> Unit = { key ->
        expandedSection = if (expandedSection == key) null else key
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置".toScript(scriptMode),
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
                selectedItem = 3,
                onItemSelected = { index ->
                    when (index) {
                        0 -> onNavigateToHome()
                        1 -> onNavigateToSearch()
                        2 -> onNavigateToBookmarks()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingsGroupHeader(title = "外观".toScript(scriptMode))
            }
            item {
                SettingsCard {
                    SettingsChoiceSection(
                        title = "主题风格".toScript(scriptMode),
                        value = currentThemeStyle.labelZh.toScript(scriptMode),
                        expanded = expandedSection == SECTION_THEME,
                        onToggle = { toggleSection(SECTION_THEME) }
                    ) {
                        ThemeStyle.entries.forEach { style ->
                            InlineOptionRow(
                                headline = style.labelZh.toScript(scriptMode),
                                selected = currentThemeStyle == style,
                                leading = { ThemeSwatch(style = style, isDark = isDark) },
                                onClick = {
                                    onSetTheme(style)
                                    expandedSection = null
                                }
                            )
                        }
                    }

                    SettingsChoiceSection(
                        title = "深色模式".toScript(scriptMode),
                        value = currentDarkMode.labelZh.toScript(scriptMode),
                        expanded = expandedSection == SECTION_DARK,
                        onToggle = { toggleSection(SECTION_DARK) }
                    ) {
                        DarkMode.entries.forEach { mode ->
                            InlineOptionRow(
                                headline = mode.labelZh.toScript(scriptMode),
                                selected = currentDarkMode == mode,
                                onClick = {
                                    onSetDarkMode(mode)
                                    expandedSection = null
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroupHeader(title = "语言".toScript(scriptMode))
            }
            item {
                SettingsCard {
                    SettingsChoiceSection(
                        title = "简繁体切换".toScript(scriptMode),
                        value = currentScript.fullLabel,
                        expanded = expandedSection == SECTION_SCRIPT,
                        onToggle = { toggleSection(SECTION_SCRIPT) }
                    ) {
                        ChineseScriptMode.entries.forEach { mode ->
                            InlineOptionRow(
                                headline = mode.fullLabel,
                                selected = currentScript == mode,
                                onClick = {
                                    onSetScript(mode)
                                    expandedSection = null
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroupHeader(title = "关于".toScript(scriptMode))
            }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val iconContainerColor = if (isDark) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        val iconContentColor = if (isDark) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = iconContainerColor,
                            border = if (isDark) {
                                BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            } else {
                                null
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    id = dev.x.opusone.R.drawable.ic_launcher_foreground
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(iconContentColor),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "一家言".toScript(scriptMode),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = ClassicalFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "究天人之际，通古今之变，成一家之言。".toScript(scriptMode),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = ClassicalFontFamily
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingsRow(
                        title = "版本".toScript(scriptMode),
                        value = BuildConfig.VERSION_NAME
                    )
                    SettingsRow(
                        title = "开发者".toScript(scriptMode),
                        value = "Cicada"
                    )
                    SettingsRow(
                        title = "授权许可".toScript(scriptMode),
                        value = "MIT License"
                    ) { showLicenseDialog = true }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = {
                Text(
                    text = "授权许可".toScript(scriptMode),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Copyright (c) 2026 Cicada",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text("确定".toScript(scriptMode))
                }
            }
        )
    }
}

/**
 * 设置分组卡片，使用统一容器背景承载设置项。
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(content = content)
    }
}

/**
 * 可展开的设置行：行本身是标题 + 当前值 + 折叠箭头，选项就地在下方展开。
 *
 * 选择区与行同属一张卡、共享同一条左基线，因此不需要分隔线 —— 展开内容靠**左内缩 16dp**
 * （见 [InlineOptionRow]）表达从属关系。展开动画从顶部开始（`expandFrom = Alignment.Top`），
 * 默认的 `Alignment.Bottom` 会让内容像从下往上顶出来，与「向下展开」的预期相反。
 */
@Composable
private fun SettingsChoiceSection(
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    options: @Composable () -> Unit
) {
    SettingsRow(
        title = title,
        value = value,
        expanded = expanded,
        onClick = onToggle
    )
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top
        ) + fadeIn(animationSpec = tween(durationMillis = 150)),
        exit = shrinkVertically(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            options()
        }
    }
}

/**
 * 设置行：左侧标题，右侧「当前值 + 指示箭头」。
 *
 * 箭头有两种语义，用 [expanded] 区分：
 * - `expanded == null` → 静态 `›`，表示点击会打开另一个层（如授权许可对话框）
 * - `expanded != null` → 可旋转的折叠箭头，表示点击会就地在下方展开
 */
@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    expanded: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onClick != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    if (expanded == null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                            label = "chevronRotation"
                        )
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = if (expanded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = when {
            onClick == null -> Modifier
            // toggleable 让 TalkBack 能读出「已展开 / 已收起」，比裸 clickable 语义更准
            expanded != null -> Modifier.toggleable(
                value = expanded,
                role = Role.Button,
                onValueChange = { onClick() }
            )
            else -> Modifier.clickable(onClick = onClick)
        }
    )
}

/**
 * 就地展开的单选项行。
 *
 * 左内缩 16dp 表达「这是上方那一行的从属内容」。整行用 `selectable` 而非 `clickable`，
 * TalkBack 才会把它读成单选组而不是一组按钮。选中标记用行尾对勾（M3 菜单惯例），
 * 文字保持 onSurface —— 只有对勾染色，避免整页出现大块彩色文字。
 */
@Composable
private fun InlineOptionRow(
    headline: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = leading,
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(start = 16.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
    )
}

/**
 * 主题风格色板缩略图，展示对应明暗模式下的预览色块。
 */
@Composable
private fun ThemeSwatch(
    style: ThemeStyle,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val preview: ThemePreviewColors = previewColorsFor(style, isDark)

    Surface(
        modifier = modifier.size(width = 48.dp, height = 40.dp),
        shape = MaterialTheme.shapes.small,
        color = preview.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = preview.primary
            ) {}
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = preview.onSurface.copy(alpha = 0.55f)
            ) {}
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(3.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = preview.onSurface.copy(alpha = 0.28f)
            ) {}
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
