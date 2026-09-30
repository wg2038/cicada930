package dev.x.opusone.ui.taishigong

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.x.opusone.data.model.TaiShiGongYueItem
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

data class TaiShiGongUiState(
    val list: List<TaiShiGongYueItem> = emptyList(),
    val isLoading: Boolean = true
)

class TaiShiGongViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)
    private val _uiState = MutableStateFlow(TaiShiGongUiState())
    val uiState: StateFlow<TaiShiGongUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            guardCoroutine("TaiShiGongViewModel", "loadData", onError = {
                _uiState.update { it.copy(isLoading = false) }
            }) {
                val all = repository.getAllTaiShiGongYue()
                _uiState.update { it.copy(list = all, isLoading = false) }
            }
        }
    }
}

@Composable
fun TaiShiGongYueScreen(
    onBackClick: () -> Unit,
    onNavigateToChapter: (Int, String) -> Unit,
    viewModel: TaiShiGongViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current

    OpusOneSearchListScreen(
        title = "太史公曰",
        searchQuery = "",
        onSearchChange = {},
        searchPlaceholder = "",
        showSearchField = false,
        onBackClick = onBackClick,
        isLoading = uiState.isLoading,
        itemCount = uiState.list.size,
        itemNoun = "篇",
        emptyTitle = "暂无论赞",
        emptyDescription = null
    ) {
        items(uiState.list, key = { it.id }) { item ->
            OpusOneItemCard(onClick = { onNavigateToChapter(item.chapterId, item.targetPn) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "《史记 · ${item.chapterTitle}》".toScript(scriptMode),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = ClassicalFontFamily
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OpusOneSourceBadge(text = "第 ${item.chapterId} 篇".toScript(scriptMode))
                }

                Text(
                    text = OpusOneTagParser.cleanDisplayText(item.plainContent).toScript(scriptMode),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = ClassicalFontFamily
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
