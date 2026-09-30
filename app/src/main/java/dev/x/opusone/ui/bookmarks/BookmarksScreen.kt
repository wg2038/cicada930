package dev.x.opusone.ui.bookmarks

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.data.OpusOneRepository
import dev.x.opusone.data.model.BookmarkItem
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.theme.ClassicalTypography
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.ui.components.OpusOneEmptyState
import dev.x.opusone.ui.components.OpusOneItemCard
import dev.x.opusone.ui.components.OpusOneNavigationBar
import dev.x.opusone.util.cd
import dev.x.opusone.util.guardCoroutine
import dev.x.opusone.util.toScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class BookmarksUiState(
    val bookmarks: List<BookmarkItem> = emptyList(),
    val isLoading: Boolean = true
)

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)
    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    fun loadBookmarks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            guardCoroutine("BookmarksViewModel", "loadBookmarks", onError = {
                _uiState.update { it.copy(isLoading = false) }
            }) {
                val list = repository.getBookmarks()
                _uiState.update { it.copy(bookmarks = list, isLoading = false) }
            }
        }
    }

    fun deleteBookmark(id: Int) {
        viewModelScope.launch {
            guardCoroutine("BookmarksViewModel", "deleteBookmark", onError = {
                loadBookmarks()
            }) {
                repository.deleteBookmark(id)
                loadBookmarks()
            }
        }
    }
}

/**
 * 个人研读藏书阁（书签列表）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBackClick: () -> Unit,
    onNavigateToChapter: (Int, String) -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: BookmarksViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current
    // 每次进入书签页面时自动拉取最新书签，确保正文新收藏即时可见
    LaunchedEffect(Unit) {
        viewModel.loadBookmarks()
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "书签".toScript(scriptMode),
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
                selectedItem = 2,
                onItemSelected = { index ->
                    when (index) {
                        0 -> onNavigateToHome()
                        1 -> onNavigateToSearch()
                        3 -> onNavigateToSettings()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.bookmarks.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "暂无书签".toScript(scriptMode),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.bookmarks, key = { it.id }) { bm ->
                            BookmarkCard(
                                bookmark = bm,
                                onItemClick = { onNavigateToChapter(bm.chapterId, bm.pnIndex) },
                                onDelete = { viewModel.deleteBookmark(bm.id) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: BookmarkItem,
    onItemClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scriptMode = LocalChineseScript.current
    val dateStr = remember(bookmark.createdAt) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(bookmark.createdAt))
    }

    OpusOneItemCard(onClick = onItemClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "《史记 · ${bookmark.chapterTitle}》".toScript(scriptMode),
                    style = ClassicalTypography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 补偿前置全角书名号字框留白，实现视觉基准线对齐
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = cd("删除书签"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
