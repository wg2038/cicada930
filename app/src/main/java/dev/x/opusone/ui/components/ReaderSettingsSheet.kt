package dev.x.opusone.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import dev.x.opusone.data.model.TagType
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.util.toScript

/**
 * 阅读器配置抽屉：遵循 Material 3 规范，支持定高预览与高亮类别内联向下展开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    fontSize: Float,
    lineSpacingMultiplier: Float,
    currentScript: ChineseScriptMode = ChineseScriptMode.SIMPLIFIED,
    syntaxHighlightEnabled: Boolean,
    showTranslation: Boolean,
    showSanJiaInline: Boolean,
    mergeParagraphs: Boolean,
    enabledTags: Set<TagType> = emptySet(),
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onToggleSyntaxHighlight: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleSanJiaInline: () -> Unit,
    onToggleMergeParagraphs: () -> Unit,
    onToggleTag: (TagType) -> Unit = {},
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var isTagsExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isTagsExpanded) {
        if (isTagsExpanded) {
            delay(320)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionLabel(text = "正文呈现".toScript(currentScript))

            val cardShape = MaterialTheme.shapes.medium
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape),
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                SettingSwitchRow(
                    title = "白话对译".toScript(currentScript),
                    checked = showTranslation,
                    onCheckedChange = { onToggleTranslation() }
                )
                SettingSwitchRow(
                    title = "三家注".toScript(currentScript),
                    checked = showSanJiaInline,
                    onCheckedChange = { onToggleSanJiaInline() }
                )
                SettingSwitchRow(
                    title = "智能分段".toScript(currentScript),
                    checked = mergeParagraphs,
                    onCheckedChange = { onToggleMergeParagraphs() }
                )
                SettingSwitchRow(
                    title = "语法高亮".toScript(currentScript),
                    checked = syntaxHighlightEnabled,
                    onCheckedChange = { onToggleSyntaxHighlight() }
                )

                if (syntaxHighlightEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isTagsExpanded) 180f else 0f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = 320f
                        ),
                        label = "ChevronRotation"
                    )
                    val chevronTint by animateColorAsState(
                        targetValue = if (isTagsExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "ChevronTint"
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "高亮类别".toScript(currentScript),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = (if (isTagsExpanded) "收起高亮类别" else "展开高亮类别").toScript(currentScript),
                                tint = chevronTint,
                                modifier = Modifier.rotate(chevronRotation)
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isTagsExpanded = !isTagsExpanded
                            }
                    )

                    AnimatedVisibility(
                        visible = isTagsExpanded,
                        enter = expandVertically(
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = 340f
                            ),
                            expandFrom = Alignment.Top // 严格向下单向舒展
                        ) + slideInVertically(
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = 340f
                            ),
                            initialOffsetY = { -it / 6 } // 视差滑入：1/6 位移顺流而下
                        ) + fadeIn(animationSpec = tween(220, easing = LinearOutSlowInEasing)),
                        exit = shrinkVertically(
                            animationSpec = tween(200, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        ) + slideOutVertically(
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it / 8 }
                        ) + fadeOut(animationSpec = tween(150))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp)
                                .graphicsLayer { clip = true }, // 硬件裁剪加速，避免每帧重排
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                thickness = 0.5.dp
                            )

                            // 16个标签按2列排列
                            TagType.entries.chunked(2).forEach { rowTags ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowTags.forEach { tag ->
                                        val isChecked = enabledTags.contains(tag)
                                        FilterChip(
                                            selected = isChecked,
                                            onClick = { onToggleTag(tag) },
                                            modifier = Modifier.weight(1f),
                                            label = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Text(text = tag.code, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(text = tag.labelZh.toScript(currentScript))
                                                }
                                            }
                                        )
                                    }
                                    if (rowTags.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "字号大小".toScript(currentScript),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${fontSize.toInt()} sp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 15f..28f,
                        steps = 12,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "A",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "行间距".toScript(currentScript),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", lineSpacingMultiplier),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "紧凑".toScript(currentScript),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = lineSpacingMultiplier,
                        onValueChange = onLineSpacingChange,
                        valueRange = 1.5f..2.5f,
                        steps = 4,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "舒朗".toScript(currentScript),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}
