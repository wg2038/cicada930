package dev.x.opusone.ui.home

import android.app.Application
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.data.OpusOneRepository
import dev.x.opusone.data.model.Chapter
import dev.x.opusone.data.model.CLASSICAL_QUOTES
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.theme.ClassicalTypography
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.ui.components.OpusOneNavigationBar
import dev.x.opusone.util.cd
import dev.x.opusone.util.guardCoroutine
import dev.x.opusone.util.toScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class CategoryMeta(
    val key: String,
    val title: String,
    val desc: String
)

val CATEGORY_METAS = listOf(
    CategoryMeta("本纪", "本纪", "记载帝王言行政绩，贯通古今，是全书的纲领"),
    CategoryMeta("表", "表", "谱列年代世系，排比大事，是全书的经纬"),
    CategoryMeta("书", "书", "详述典章制度，考镜源流，是全书的经世之学"),
    CategoryMeta("世家", "世家", "叙述诸侯世系，褒贬功过，是全书的支柱"),
    CategoryMeta("列传", "列传", "展现百家众生，各具风采，是全书的血肉")
)

val CATEGORY_FILTERS = CATEGORY_METAS.map { it.key }

data class HomeUiState(
    val chapters: List<Chapter> = emptyList(),
    val selectedCategory: String = "本纪",
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            guardCoroutine("HomeViewModel", "loadData", onError = {
                _uiState.update { it.copy(isLoading = false) }
            }) {
                val chapters = withContext(Dispatchers.IO) {
                    repository.getAllChapters()
                }
                _uiState.update { it.copy(chapters = chapters, isLoading = false) }
            }
        }
    }

    fun selectCategory(category: String) {
        if (_uiState.value.selectedCategory != category) {
            _uiState.update { it.copy(selectedCategory = category) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChapter: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.selectedCategory) {
        listState.scrollToItem(0)
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "一家言".toScript(scriptMode),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {},
                actions = {},
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        bottomBar = {
            OpusOneNavigationBar(
                selectedItem = 0,
                onItemSelected = { index ->
                    when (index) {
                        1 -> onNavigateToSearch()
                        2 -> onNavigateToBookmarks()
                        3 -> onNavigateToSettings()
                    }
                }
            )
        },
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    HomeQuoteBanner(
                        onQuoteClick = { chapterId ->
                            onNavigateToChapter(chapterId)
                        }
                    )
                }

                item {
                    CategoryFilterRow(
                        selectedCategory = uiState.selectedCategory,
                        onSelectCategory = { viewModel.selectCategory(it) },
                        chapters = uiState.chapters
                    )
                }

                val sectionChapters = uiState.chapters.filter { it.category == uiState.selectedCategory }
                itemsIndexed(
                    items = sectionChapters,
                    key = { _, chapter -> "chapter_${chapter.id}" }
                ) { index, chapter ->
                    val shape = when {
                        sectionChapters.size == 1 -> MaterialTheme.shapes.medium
                        index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        index == sectionChapters.lastIndex -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                        else -> RectangleShape
                    }
                    ChapterListItem(
                        chapter = chapter,
                        shape = shape,
                        showDivider = index != sectionChapters.lastIndex,
                        onChapterClick = onNavigateToChapter
                    )
                }
            }
        }
    }
}

/**
 * 卷首名句摘录横幅组件。
 */
@Composable
private fun HomeQuoteBanner(
    onQuoteClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scriptMode = LocalChineseScript.current
    val quoteIndex = remember {
        val epochDay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            java.time.LocalDate.now().toEpochDay()
        } else {
            System.currentTimeMillis() / (1000L * 60 * 60 * 24)
        }
        val hashed = kotlin.math.abs((epochDay * 31L + 17L).hashCode())
        hashed % CLASSICAL_QUOTES.size
    }
    val currentQuote = CLASSICAL_QUOTES[quoteIndex]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            onClick = { onQuoteClick(currentQuote.chapterId) },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 140.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "“",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    fontFamily = ClassicalFontFamily,
                    modifier = Modifier.height(24.dp)
                )

                Text(
                    text = currentQuote.quote.toScript(scriptMode),
                    style = MaterialTheme.typography.titleLarge.copy(
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    fontFamily = ClassicalFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = currentQuote.source.toScript(scriptMode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = ClassicalFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/** 分类筛选：MD3 SecondaryTabRow，纤细内敛的指示器随选中项平滑滑动。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    chapters: List<Chapter>
) {
    val scriptMode = LocalChineseScript.current

    CompositionLocalProvider(
        LocalRippleConfiguration provides null
    ) {
        SecondaryTabRow(
            selectedTabIndex = CATEGORY_FILTERS.indexOf(selectedCategory).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            containerColor = Color.Transparent
        ) {
            CATEGORY_FILTERS.forEach { cat ->
                Tab(
                    selected = selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    text = {
                        Text(
                            text = cat.toScript(scriptMode),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
            }
        }
    }
}

/** 篇目分组列表项：支持卡片外形、行间分隔线与虚拟化复用。 */
@Composable
private fun ChapterListItem(
    chapter: Chapter,
    shape: androidx.compose.ui.graphics.Shape,
    showDivider: Boolean,
    onChapterClick: (Int) -> Unit
) {
    val scriptMode = LocalChineseScript.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(
                        text = chapter.title.toScript(scriptMode),
                        style = ClassicalTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = if (chapter.summary.isNotBlank()) {
                    {
                        Text(
                            text = chapter.summary.toScript(scriptMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else null,
                leadingContent = {
                    Text(
                        text = chapter.id.toString().padStart(3, '0'),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onChapterClick(chapter.id) }
            )
            if (showDivider) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }
    }
}
