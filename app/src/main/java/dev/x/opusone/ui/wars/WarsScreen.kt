package dev.x.opusone.ui.wars

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import dev.x.opusone.data.model.WarItem
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.ui.components.OpusOneItemCard
import dev.x.opusone.ui.components.OpusOneSearchListScreen
import dev.x.opusone.ui.components.OpusOneSourceBadge
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.ui.reader.OpusOneTagParser
import dev.x.opusone.util.guardCoroutine
import dev.x.opusone.util.toScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarsUiState(
    val list: List<WarItem> = emptyList(),
    val filteredList: List<WarItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

class WarsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)
    private val _uiState = MutableStateFlow(WarsUiState())
    val uiState: StateFlow<WarsUiState> = _uiState.asStateFlow()

    init {
        loadWars()
    }

    private fun loadWars() {
        viewModelScope.launch {
            guardCoroutine("WarsViewModel", "loadWars", onError = {
                _uiState.update { it.copy(isLoading = false) }
            }) {
                val all = repository.getAllWars()
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
                        item.name.contains(v, ignoreCase = true) ||
                            item.description.contains(v, ignoreCase = true) ||
                            item.fullDescription.contains(v, ignoreCase = true) ||
                            item.chapterTitle.contains(v, ignoreCase = true)
                    }
                }
            }
            state.copy(searchQuery = query, filteredList = filtered)
        }
    }
}

@Composable
fun WarsScreen(
    onBackClick: () -> Unit,
    onNavigateToChapter: (Int, String) -> Unit,
    viewModel: WarsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current

    OpusOneSearchListScreen(
        title = "经典战役",
        searchQuery = uiState.searchQuery,
        onSearchChange = { viewModel.onSearchChange(it) },
        searchPlaceholder = "搜索战役名称、交战地点或章节",
        onBackClick = onBackClick,
        isLoading = uiState.isLoading,
        itemCount = uiState.filteredList.size,
        itemNoun = "场",
        emptyTitle = "没有匹配的战役",
        emptyDescription = "换个关键词试试，或清空搜索查看全部"
    ) {
        items(uiState.filteredList, key = { it.id }) { item ->
            val chapId = item.chapterNum.toIntOrNull()

            OpusOneItemCard(onClick = {
                if (chapId != null && chapId > 0) onNavigateToChapter(chapId, "")
            }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name.toScript(scriptMode),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = ClassicalFontFamily
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OpusOneSourceBadge(text = "《${item.chapterTitle}》".toScript(scriptMode))
                }

                if (item.description.isNotBlank()) {
                    Text(
                        text = OpusOneTagParser.cleanDisplayText(item.description)
                            .replace("\n", " · ")
                            .toScript(scriptMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.fullDescription.isNotBlank()) {
                    Text(
                        text = OpusOneTagParser.cleanDisplayText(item.fullDescription).toScript(scriptMode),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = ClassicalFontFamily
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "研读《${item.chapterTitle}》本篇".toScript(scriptMode),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
