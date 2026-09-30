package dev.x.opusone.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.x.opusone.data.model.TagType
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.util.toScript

/**
 * 古籍年表与世系表二维渲染组件。
 *
 * 支持固定首列、表头横向联动滚动、实体高亮联动及全屏查看。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClassicalTableView(
    table: ClassicalTable,
    headingText: String,
    pnIndex: String,
    fontSize: Float,
    enabledTags: Set<TagType>,
    syntaxHighlightEnabled: Boolean,
    isDark: Boolean,
    onEntityClick: (String) -> Unit,
    onQuoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFullscreenDialog by remember { mutableStateOf(false) }

    val cleanTitle = remember(headingText) {
        val raw = headingText.replace(Regex("^#{1,6}\\s*"), "").trim()
        if (raw.isBlank() || raw == "表") "大事编年谱表" else raw
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TableToolbar(
                title = cleanTitle,
                onFullscreenClick = { showFullscreenDialog = true }
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            TableGrid(
                table = table,
                fontSize = fontSize,
                enabledTags = enabledTags,
                syntaxHighlightEnabled = syntaxHighlightEnabled,
                isDark = isDark,
                onEntityClick = onEntityClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 440.dp)
            )
        }
    }

    if (showFullscreenDialog) {
        ClassicalTableFullscreenDialog(
            table = table,
            title = cleanTitle,
            fontSize = fontSize,
            enabledTags = enabledTags,
            syntaxHighlightEnabled = syntaxHighlightEnabled,
            isDark = isDark,
            onEntityClick = onEntityClick,
            onDismiss = { showFullscreenDialog = false }
        )
    }
}

/**
 * 表格标题与快捷工具栏
 */
@Composable
private fun TableToolbar(
    title: String,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scriptMode = LocalChineseScript.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title.toScript(scriptMode),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        IconButton(
            onClick = onFullscreenClick
        ) {
            Icon(
                imageVector = Icons.Outlined.OpenInFull,
                contentDescription = "全屏查看年表",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 核心表格网格排版（支持吸顶表头与固定首列）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableGrid(
    table: ClassicalTable,
    fontSize: Float,
    enabledTags: Set<TagType>,
    syntaxHighlightEnabled: Boolean,
    isDark: Boolean,
    onEntityClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hScrollState = rememberScrollState()
    val scriptMode = LocalChineseScript.current

    // 首列自适应宽度
    val firstHeader = table.headers.getOrElse(0) { "序号" }
    val indexColWidth: Dp = if (firstHeader.length > 2) 76.dp else 56.dp
    // 单列表（如卷十三「帝王」条）整格都是叙述文字，窄列会把整句挤成十几行
    val dataColWidth: Dp = if (table.colCount <= 2) 320.dp else 104.dp

    val headerBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val frozenBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val cellBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // 吸顶表头
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(headerBgColor)
                ) {
                    Box(
                        modifier = Modifier
                            .width(indexColWidth)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = firstHeader.toScript(scriptMode),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(frozenBorderColor)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(hScrollState)
                    ) {
                        table.headers.drop(1).forEachIndexed { colIdx, header ->
                            val parsedHeader = remember(header, enabledTags, isDark, scriptMode) {
                                OpusOneTagParser.parseTaggedText(
                                    taggedText = header,
                                    enabledTags = enabledTags,
                                    isDark = isDark,
                                    syntaxHighlightEnabled = syntaxHighlightEnabled,
                                    scriptMode = scriptMode
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(dataColWidth)
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = parsedHeader,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                            if (colIdx < table.headers.size - 2) {
                                Box(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .fillMaxHeight()
                                        .background(cellBorderColor)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = frozenBorderColor)
            }

            // 数据行
            itemsIndexed(table.rows) { rowIndex, row ->
                val isEven = rowIndex % 2 == 0
                val rowBg = if (isEven) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .background(rowBg)
                ) {
                    Box(
                        modifier = Modifier
                            .width(indexColWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = row.indexCell.toScript(scriptMode),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(frozenBorderColor)
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(hScrollState)
                    ) {
                        row.cells.forEachIndexed { colIdx, cellContent ->
                            val annotatedCell = remember(cellContent, enabledTags, isDark, syntaxHighlightEnabled, scriptMode) {
                                if (cellContent.isBlank()) null else {
                                    OpusOneTagParser.parseTaggedText(
                                        taggedText = cellContent,
                                        enabledTags = enabledTags,
                                        isDark = isDark,
                                        syntaxHighlightEnabled = syntaxHighlightEnabled,
                                        scriptMode = scriptMode,
                                        onEntityClick = onEntityClick
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(dataColWidth)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                if (annotatedCell != null) {
                                    Text(
                                        text = annotatedCell,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = ClassicalFontFamily,
                                            fontSize = (fontSize * 0.82f).coerceAtLeast(11f).sp,
                                            lineHeight = (fontSize * 1.15f).coerceAtLeast(15f).sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "-",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            }

                            if (colIdx < row.cells.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .fillMaxHeight()
                                        .background(cellBorderColor)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = cellBorderColor)
            }
        }
    }
}

/**
 * 全屏沉浸式年表查阅对话框（横屏 / 大视野适配）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassicalTableFullscreenDialog(
    table: ClassicalTable,
    title: String,
    fontSize: Float,
    enabledTags: Set<TagType>,
    syntaxHighlightEnabled: Boolean,
    isDark: Boolean,
    onEntityClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scriptMode = LocalChineseScript.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // 顶部标题栏
                TopAppBar(
                    title = {
                        Text(
                            text = title.toScript(scriptMode),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭全屏"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                // 满屏表格主体
                TableGrid(
                    table = table,
                    fontSize = fontSize,
                    enabledTags = enabledTags,
                    syntaxHighlightEnabled = syntaxHighlightEnabled,
                    isDark = isDark,
                    onEntityClick = onEntityClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}
