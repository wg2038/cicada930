package dev.x.opusone.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.data.model.Chapter
import dev.x.opusone.data.model.SanJiaZhuNote
import dev.x.opusone.data.model.Section
import dev.x.opusone.data.model.TagType
import dev.x.opusone.theme.*
import dev.x.opusone.ui.components.*
import dev.x.opusone.util.cd
import dev.x.opusone.util.toScript

/**
 * 《史记》精读器。
 *
 * 导航语义：
 * - 实体卡片跳转：压入新返回栈项（返回键回到上一篇章）
 * - 阅读页不设底部翻页栏，保持正文沉浸（篇章切换从首页进入）
 *
 * MD3 规范要点：
 * - 白话译文 / 三家注区块去掉 IntrinsicSize.Min + 4dp 强调竖条的自绘结构，
 *   改用填充式 Card（surfaceContainer 色阶）+ 标题行 + 分隔线的标准层级表达
 * - 除「正文阅读字号」这一用户可配置项外，其余排印一律回到 MD3 尺度，
 *   不再出现 10/11/13/16/18/19sp 等零散硬编码
 * - 段落编号（Purple Numbers）使用 primaryContainer 实底，不再叠加 40% 透明度
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapterId: Int,
    targetSectionPn: String? = null,
    onBackClick: () -> Unit,
    currentScript: ChineseScriptMode = ChineseScriptMode.SIMPLIFIED,
    viewModel: ReaderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scriptMode = LocalChineseScript.current
    val isDark = LocalIsDarkTheme.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 篇章头卡是否仍在屏内 —— 决定顶栏篇名的显隐。
    // 头卡显示时篇名由卡内承担（headlineSmall），滚出头卡后交给顶栏（titleLarge），
    // 保证任何时刻屏上只有一处篇名，避免「永久同屏、只差 2sp、谁也不像主角」。
    var mastheadHeightPx by remember { mutableStateOf(0) }
    val isMastheadOnScreen by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                (mastheadHeightPx == 0 || listState.firstVisibleItemScrollOffset < mastheadHeightPx)
        }
    }
    val appBarTitleAlpha by animateFloatAsState(
        targetValue = if (isMastheadOnScreen) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "MastheadTitleAlpha"
    )

    LaunchedEffect(chapterId, targetSectionPn) {
        viewModel.loadChapter(chapterId, targetSectionPn)
    }

    // 用 rememberSaveable 保证只滚动一次（切换设置重载 sections 时不再跳回）
    var hasScrolledToTarget by rememberSaveable(chapterId, targetSectionPn) { mutableStateOf(false) }
    LaunchedEffect(uiState.sections, targetSectionPn) {
        if (!hasScrolledToTarget && !targetSectionPn.isNullOrBlank() && uiState.sections.isNotEmpty()) {
            val normalizedTarget = normalizePnIndex(targetSectionPn)
            val targetIdx = uiState.sections.indexOfFirst {
                it.pnIndex == targetSectionPn || normalizePnIndex(it.pnIndex) == normalizedTarget
            }
            if (targetIdx >= 0) {
                listState.scrollToItem(targetIdx + 1) // +1 为顶部篇章卡
                hasScrolledToTarget = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (uiState.chapter?.title ?: "《史记》精读").toScript(scriptMode),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isMastheadOnScreen) {
                            // 篇名此刻由头卡显示，顶栏这一份既不显示也不进无障碍树（否则 TalkBack 读两遍）
                            Modifier
                                .alpha(appBarTitleAlpha)
                                .clearAndSetSemantics { }
                        } else {
                            Modifier.alpha(appBarTitleAlpha)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = cd("返回"))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val currentPn = if (listState.firstVisibleItemIndex <= 0) {
                            ""
                        } else {
                            uiState.sections.getOrNull(listState.firstVisibleItemIndex - 1)?.pnIndex ?: ""
                        }
                        viewModel.toggleBookmark(currentPn)
                    }) {
                        Icon(
                            imageVector = if (uiState.isBookmarked) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks,
                            contentDescription = cd(if (uiState.isBookmarked) "已加入书签" else "加入书签"),
                            tint = if (uiState.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.setShowSettingsSheet(true) }) {
                        Icon(Icons.Rounded.Tune, contentDescription = cd("阅读设置"))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val topPadding = paddingValues.calculateTopPadding()
            // 无底栏：正文底部自行避让手势条 / 安全区
            val bottomInset = maxOf(navBarBottom, safeInsets.calculateBottomPadding())

            val onEntityClickStable = remember(viewModel) {
                { key: String -> viewModel.onEntityClicked(key) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp + safeInsets.calculateStartPadding(layoutDirection),
                    end = 16.dp + safeInsets.calculateEndPadding(layoutDirection),
                    top = topPadding + 12.dp,
                    bottom = bottomInset + 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp) // 段后距：呼吸感优先于紧凑
            ) {
                item {
                    uiState.chapter?.let { ch ->
                        ChapterMastheadCard(
                            chapter = ch,
                            onHeightMeasured = { mastheadHeightPx = it }
                        )
                    }
                }

                items(uiState.sections, key = { it.id }) { section ->
                    // 从 ReaderUiState.sectionNotesMap 中直接获取后台预处理好的注疏列表（O(1) 查询），
                    // 彻底消除列表快速滑动时在主线程进行 O(N*M) 遍历和海量临时对象分配（解决 GC 掉帧）。
                    val filteredNotes = uiState.sectionNotesMap[section.id] ?: emptyList()
                    val isTargeted = !targetSectionPn.isNullOrBlank() && (
                        section.pnIndex == targetSectionPn ||
                        normalizePnIndex(section.pnIndex) == normalizePnIndex(targetSectionPn)
                    )

                    Box(
                        modifier = if (isTargeted) {
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ) {
                        SectionItemRow(
                            section = section,
                            fontSize = uiState.fontSize,
                            lineSpacingMultiplier = uiState.lineSpacingMultiplier,
                            enabledTags = uiState.enabledTags,
                            syntaxHighlightEnabled = uiState.syntaxHighlightEnabled,
                            showTranslation = uiState.showTranslation,
                            showSanJiaInline = uiState.showSanJiaInline,
                            sanjiazhuNotes = filteredNotes,
                            isDark = isDark,
                            onEntityClick = onEntityClickStable,
                            onQuoteClick = {
                                // 与段落徽标一致：清洗 p_ 前缀后再传给摘句弹窗
                                val cleanPn = section.pnIndex.trim().removePrefix("p_").removePrefix("P_")
                                viewModel.showQuoteDialog(cleanPn, OpusOneTagParser.cleanDisplayText(section.plainText))
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showSettingsSheet) {
        ReaderSettingsSheet(
            fontSize = uiState.fontSize,
            lineSpacingMultiplier = uiState.lineSpacingMultiplier,
            currentScript = currentScript,
            syntaxHighlightEnabled = uiState.syntaxHighlightEnabled,
            showTranslation = uiState.showTranslation,
            showSanJiaInline = uiState.showSanJiaInline,
            mergeParagraphs = uiState.mergeParagraphs,
            enabledTags = uiState.enabledTags,
            onFontSizeChange = { viewModel.setFontSize(it) },
            onLineSpacingChange = { viewModel.setLineSpacing(it) },
            onToggleSyntaxHighlight = { viewModel.toggleSyntaxHighlight() },
            onToggleTranslation = { viewModel.toggleTranslation() },
            onToggleSanJiaInline = { viewModel.toggleSanJiaInline() },
            onToggleMergeParagraphs = { viewModel.toggleMergeParagraphs() },
            onToggleTag = { viewModel.toggleTag(it) },
            onDismiss = { viewModel.setShowSettingsSheet(false) }
        )
    }

    if (uiState.showEntitySheet) {
        EntityBottomSheet(
            entity = uiState.selectedEntity,
            isLoading = uiState.isEntityLoading,
            onDismiss = { viewModel.dismissEntitySheet() }
        )
    }

    uiState.quoteDialogData?.let { (pn, text) ->
        QuoteCardDialog(
            chapterTitle = uiState.chapter?.title ?: "",
            pnIndex = pn,
            quoteText = text,
            onDismiss = { viewModel.dismissQuoteDialog() }
        )
    }
}

/**
 * 篇章封面展示卡片。
 */
@Composable
private fun ChapterMastheadCard(
    chapter: Chapter,
    onHeightMeasured: (Int) -> Unit = {}
) {
    val scriptMode = LocalChineseScript.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeightMeasured(it.size.height) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = ("《史记》卷 ${chapter.id} · ${chapter.category}")
                            .toScript(scriptMode),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = chapter.title.toScript(scriptMode),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (chapter.sectionCount > 0) {
                Text(
                    text = ("全篇共 ${chapter.sectionCount} 段 · ${chapter.wordCount} 字")
                        .toScript(scriptMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class VerbHighlightSpan(
    val color: Color,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

@Composable
private fun SectionItemRow(
    section: Section,
    fontSize: Float,
    lineSpacingMultiplier: Float,
    enabledTags: Set<TagType>,
    syntaxHighlightEnabled: Boolean,
    showTranslation: Boolean,
    showSanJiaInline: Boolean,
    sanjiazhuNotes: List<SanJiaZhuNote>,
    isDark: Boolean,
    onEntityClick: (String) -> Unit,
    onQuoteClick: () -> Unit
) {
    val scriptMode = LocalChineseScript.current
    val isHeadingSection = section.sectionType.startsWith("heading")
    if (isHeadingSection) {
        val cleanHeading = section.headingText
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^\\[\\d+(\\.\\d+)*\\]\\s*"), "")
            .replace(Regex("^\\[[0-9a-zA-Z_.]+\\]\\s*"), "")
            .replace(Regex("(?i)^h\\d+[_\\s]?\\d*\\s*"), "")
            .trim()
        if (cleanHeading.isNotBlank()) {
            // 智能吸收古籍“十表”中由 Markdown 划分遗留的单字“表”结构标题
            if (cleanHeading == "表") return

            when (section.headingLevel) {
                1, 2 -> Text(
                    text = cleanHeading.toScript(scriptMode),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                else -> Text(
                    text = cleanHeading.toScript(scriptMode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                )
            }
        }
        return
    }

    // 古籍多列表格分支（《史记》“十表”多列编年谱表与世系表）
    val isTable = remember(section.taggedContent) {
        OpusOneTableParser.isTableContent(section.taggedContent)
    }
    val tableData = if (isTable) {
        remember(section.taggedContent) {
            OpusOneTableParser.parseTable(section.taggedContent)
        }
    } else null

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (tableData != null) {
            ClassicalTableView(
                table = tableData,
                headingText = section.headingText,
                pnIndex = section.pnIndex,
                fontSize = fontSize,
                enabledTags = enabledTags,
                syntaxHighlightEnabled = syntaxHighlightEnabled,
                isDark = isDark,
                onEntityClick = onEntityClick,
                onQuoteClick = onQuoteClick
            )
        } else if (section.plainText.isNotBlank()) {
            val parsedAnnotatedText = remember(
                section.taggedContent, enabledTags, isDark, syntaxHighlightEnabled, scriptMode
            ) {
                OpusOneTagParser.parseTaggedText(
                    taggedText = section.taggedContent,
                    enabledTags = enabledTags,
                    isDark = isDark,
                    syntaxHighlightEnabled = syntaxHighlightEnabled,
                    scriptMode = scriptMode,
                    onEntityClick = onEntityClick
                )
            }

            val verbAnnotations = remember(parsedAnnotatedText) {
                parsedAnnotatedText.getStringAnnotations(
                    tag = "VERB_BG",
                    start = 0,
                    end = parsedAnnotatedText.text.length
                )
            }
            val density = LocalDensity.current
            val fontSizePx = with(density) { fontSize.sp.toPx() }
            val cornerRadiusPx = with(density) { 2.5.dp.toPx() }
            val padXPx = with(density) { 2.dp.toPx() }
            val padYPx = with(density) { 1.5.dp.toPx() }

            // 预缓存动词高亮矩形区域：在 onTextLayout 触发时一次性计算完成，
            // 避免在 drawBehind（120Hz 每帧执行）中反复调用 getBoundingBox 导致海量 Rect 对象分配与 GC 掉帧
            var highlightSpans by remember(parsedAnnotatedText, fontSizePx, padXPx, padYPx, isDark) {
                mutableStateOf<List<VerbHighlightSpan>>(emptyList())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Purple Number 典籍段落标引锚点（可点击复制/收藏/摘句）
                val cleanPn = section.pnIndex.trim().removePrefix("p_").removePrefix("P_")
                if (cleanPn.isNotBlank() && cleanPn != "0") {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .clickable { onQuoteClick() }
                    ) {
                        Text(
                            text = cleanPn,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Text(
                    text = parsedAnnotatedText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = ClassicalFontFamily,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineSpacingMultiplier).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    onTextLayout = { layout ->
                        if (verbAnnotations.isEmpty()) {
                            highlightSpans = emptyList()
                        } else {
                            val spans = mutableListOf<VerbHighlightSpan>()
                            for (annotation in verbAnnotations) {
                                val tagType = TagType.entries.find { it.name == annotation.item } ?: continue
                                val bgColor = OpusOneTagParser.getVerbBackgroundColor(tagType, isDark) ?: continue
                                val start = annotation.start
                                val end = annotation.end
                                val textLength = layout.layoutInput.text.length
                                if (start >= end || start >= textLength) continue
                                val safeEnd = minOf(end, textLength)

                                val startLine = layout.getLineForOffset(start)
                                val endLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(start))

                                for (line in startLine..endLine) {
                                    val lineStart = layout.getLineStart(line)
                                    val lineEnd = layout.getLineEnd(line)

                                    val spanStart = maxOf(start, lineStart)
                                    val spanEnd = minOf(safeEnd, lineEnd)
                                    if (spanStart >= spanEnd) continue

                                    var minLeft = Float.POSITIVE_INFINITY
                                    var maxRight = Float.NEGATIVE_INFINITY
                                    for (i in spanStart until spanEnd) {
                                        val box = layout.getBoundingBox(i)
                                        minLeft = minOf(minLeft, box.left, box.right)
                                        maxRight = maxOf(maxRight, box.left, box.right)
                                    }
                                    if (minLeft >= maxRight) continue

                                    val baseline = layout.getLineBaseline(line)
                                    val top = baseline - fontSizePx * 0.88f - padYPx
                                    val bottom = baseline + fontSizePx * 0.12f + padYPx
                                    val left = minLeft - padXPx
                                    val right = maxRight + padXPx

                                    spans.add(
                                        VerbHighlightSpan(
                                            color = bgColor,
                                            left = left,
                                            top = top,
                                            width = right - left,
                                            height = bottom - top
                                        )
                                    )
                                }
                            }
                            highlightSpans = spans
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .drawBehind {
                            if (highlightSpans.isEmpty()) return@drawBehind
                            for (span in highlightSpans) {
                                drawRoundRect(
                                    color = span.color,
                                    topLeft = Offset(span.left, span.top),
                                    size = Size(span.width, span.height),
                                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                                )
                            }
                        }
                )
            }
        }

        if (showTranslation && !section.translation.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 4.dp, bottom = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "白话译文".toScript(scriptMode),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = section.translation.orEmpty().toScript(scriptMode),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = (fontSize - 2).sp,
                            lineHeight = (fontSize * lineSpacingMultiplier * 0.85f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        if (showSanJiaInline && sanjiazhuNotes.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 4.dp, bottom = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "三家注".toScript(scriptMode),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    sanjiazhuNotes.forEach { note ->
                        val cleanAnchor = note.anchorText.trim(
                            '。', '，', '、', '；', '：', '！', '？', '」', '』', '》', '）',
                            '〉', '〈', '「', '『', '《', '（', '【', '】', ' ', '　', '"', '\'', '”', '“'
                        )
                        val cleanSimp = cleanAnchor.toScript(ChineseScriptMode.SIMPLIFIED)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (cleanSimp.length >= 2 && cleanSimp !in setOf("者", "也", "者也", "之", "之子")) {
                                Text(
                                    text = "【${cleanAnchor.toScript(scriptMode)}】",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (note.jijie.isNotBlank()) {
                                SanJiaLine(
                                    label = "集解".toScript(scriptMode),
                                    text = note.jijie.replace(Regex("^【集解】[:：\\s]*"), "").trim().toScript(scriptMode)
                                )
                            }
                            if (note.suoyin.isNotBlank()) {
                                SanJiaLine(
                                    label = "索隐".toScript(scriptMode),
                                    text = note.suoyin.replace(Regex("^【索隐】[:：\\s]*"), "").trim().toScript(scriptMode)
                                )
                            }
                            if (note.zhengyi.isNotBlank()) {
                                SanJiaLine(
                                    label = "正义".toScript(scriptMode),
                                    text = note.zhengyi.replace(Regex("^【正义】[:：\\s]*"), "").trim().toScript(scriptMode)
                                )
                            }
                            if (note.otherNotes.isNotBlank()) {
                                SanJiaLine(
                                    label = "考证/佚存".toScript(scriptMode),
                                    text = note.otherNotes.toScript(scriptMode)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SanJiaLine(label: String, text: String) {
    Layout(
        content = {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = ClassicalFontFamily,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val spacingPx = 8.dp.roundToPx()
        val badgePlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val textMaxWidth = (constraints.maxWidth - badgePlaceable.width - spacingPx).coerceAtLeast(0)
        val textPlaceable = measurables[1].measure(
            constraints.copy(minWidth = 0, maxWidth = textMaxWidth, minHeight = 0)
        )

        val firstBaseline = textPlaceable[FirstBaseline]
        val lastBaseline = textPlaceable[LastBaseline]
        val isSingleLine = firstBaseline != AlignmentLine.Unspecified && firstBaseline == lastBaseline

        val totalWidth = constraints.maxWidth
        val topOffsetPx = 2.dp.roundToPx()
        val totalHeight = if (isSingleLine) {
            maxOf(badgePlaceable.height, textPlaceable.height)
        } else {
            maxOf(badgePlaceable.height + topOffsetPx, textPlaceable.height)
        }

        layout(totalWidth, totalHeight) {
            val badgeX = 0
            val textX = badgePlaceable.width + spacingPx

            if (isSingleLine) {
                val badgeY = (totalHeight - badgePlaceable.height) / 2
                val textY = (totalHeight - textPlaceable.height) / 2
                badgePlaceable.placeRelative(badgeX, badgeY)
                textPlaceable.placeRelative(textX, textY)
            } else {
                badgePlaceable.placeRelative(badgeX, topOffsetPx)
                textPlaceable.placeRelative(textX, 0)
            }
        }
    }
}

/**
 * 规范化篇章段落编号（去除括号、引号、空白及历史 "p_" 前缀），保证不同来源的定位锚点能精确匹配。
 */
private fun normalizePnIndex(pn: String?): String {
    if (pn.isNullOrBlank()) return ""
    return pn.trim('\'', '"', '[', ']', ' ')
        .removePrefix("p_")
        .removePrefix("P_")
        .removePrefix("p")
        .removePrefix("P")
        .trim()
}
