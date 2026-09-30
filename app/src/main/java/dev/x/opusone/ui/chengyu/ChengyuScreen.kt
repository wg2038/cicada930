package dev.x.opusone.ui.chengyu

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.data.OpusOneRepository
import dev.x.opusone.data.model.ChengyuItem
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.ui.components.OpusOneItemCard
import dev.x.opusone.ui.components.OpusOneSearchListScreen
import dev.x.opusone.ui.components.OpusOneSourceBadge
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.util.guardCoroutine
import dev.x.opusone.util.toScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChengyuUiState(
    val list: List<ChengyuItem> = emptyList(),
    val filteredList: List<ChengyuItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

class ChengyuViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)
    private val _uiState = MutableStateFlow(ChengyuUiState())
    val uiState: StateFlow<ChengyuUiState> = _uiState.asStateFlow()

    init {
        loadChengyu()
    }

    private fun loadChengyu() {
        viewModelScope.launch {
            guardCoroutine("ChengyuViewModel", "loadChengyu", onError = {
                _uiState.update { it.copy(isLoading = false) }
            }) {
                val all = repository.getAllChengyu()
                _uiState.update { it.copy(list = all, filteredList = all, isLoading = false) }
            }
        }
    }

    fun onSearchChange(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.list
            } else {
                val q = query.trim()
                val simp = dev.x.opusone.util.ChineseConverter.toSimplified(q)
                val trad = dev.x.opusone.util.ChineseConverter.toTraditional(q)
                val queryVariants = setOf(q, simp, trad).filter { it.isNotBlank() }
                state.list.filter { item ->
                    queryVariants.any { v ->
                        item.word.contains(v, ignoreCase = true) ||
                            item.meaning.contains(v, ignoreCase = true) ||
                            item.chapterTitle.contains(v, ignoreCase = true)
                    }
                }
            }
            state.copy(searchQuery = query, filteredList = filtered)
        }
    }
}

@Composable
fun ChengyuScreen(
    onBackClick: () -> Unit,
    onNavigateToChapter: (Int, String) -> Unit,
    viewModel: ChengyuViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current

    OpusOneSearchListScreen(
        title = "成语溯源",
        searchQuery = uiState.searchQuery,
        onSearchChange = { viewModel.onSearchChange(it) },
        searchPlaceholder = "搜索成语、释义或章节",
        onBackClick = onBackClick,
        isLoading = uiState.isLoading,
        itemCount = uiState.filteredList.size,
        itemNoun = "条",
        emptyTitle = "没有匹配的成语",
        emptyDescription = "换个关键词试试，或清空搜索查看全部"
    ) {
        items(uiState.filteredList, key = { it.id }) { item ->
            OpusOneItemCard(
                onClick = { onNavigateToChapter(item.chapterId, item.pn) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.word.toScript(scriptMode),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = ClassicalFontFamily
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OpusOneSourceBadge(
                        text = "出自《${item.chapterTitle}》".toScript(scriptMode)
                    )
                }

                if (item.meaning.isNotBlank()) {
                    Text(
                        text = "释义：${item.meaning}".toScript(scriptMode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (item.quote.isNotBlank()) {
                    Text(
                        text = "原文：「${item.quote}」".toScript(scriptMode),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = ClassicalFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
