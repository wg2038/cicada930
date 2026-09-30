package dev.x.opusone.ui.entities

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.data.OpusOneRepository
import dev.x.opusone.data.model.EntityItem
import dev.x.opusone.ui.components.EntityBottomSheet
import dev.x.opusone.ui.components.OpusOneItemCard
import dev.x.opusone.ui.components.OpusOneSearchField
import dev.x.opusone.ui.components.OpusOneSourceBadge
import dev.x.opusone.ui.reader.OpusOneTagParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.x.opusone.util.cd
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.util.guardCoroutine
import dev.x.opusone.util.toScript

data class EntityCategory(val key: String, val label: String)

val ENTITY_CATEGORIES = listOf(
    EntityCategory("all", "全部"),
    EntityCategory("person", "人物"),
    EntityCategory("place", "地名"),
    EntityCategory("official", "官职"),
    EntityCategory("state", "邦国"),
    EntityCategory("event", "历史事件"),
    EntityCategory("identity", "身份群体"),
    EntityCategory("artifact", "器物名物"),
    EntityCategory("concept", "思想天命"),
    EntityCategory("biology", "生物"),
    EntityCategory("astronomy", "天文历法")
)

data class EntityWikiUiState(
    val selectedCategory: String = "all",
    val entities: List<EntityItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedEntity: EntityItem? = null,
    val isSheetLoading: Boolean = false,
    val showSheet: Boolean = false
)

class EntityWikiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)
    private var searchJob: Job? = null
    private var entityJob: Job? = null

    /** 分类/列表加载任务：连续切换分类时取消上一次，避免后完成的旧分类结果覆盖新分类。 */
    private var listJob: Job? = null

    private val _uiState = MutableStateFlow(EntityWikiUiState())
    val uiState: StateFlow<EntityWikiUiState> = _uiState.asStateFlow()

    init {
        loadEntities("all")
    }

    fun selectCategory(catKey: String) {
        // 切换分类时取消在途的防抖搜索，避免异步搜索结果覆盖分类数据
        searchJob?.cancel()
        _uiState.update { it.copy(selectedCategory = catKey, searchQuery = "") }
        loadEntities(catKey)
    }

    private fun loadEntities(type: String) {
        // 用 Job 跟踪：连续切分类时后一次取消前一次，避免多个协程并发写 entities，
        // 导致列表内容与选中的分类不一致。
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            guardCoroutine("EntityWikiViewModel", "loadEntities", onError = {
                _uiState.update { it.copy(isLoading = false) }
            }) {
                val list = repository.getEntitiesByType(type, limit = 200)
                _uiState.update { it.copy(entities = list, isLoading = false) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        // 在途的分类加载若后完成会覆盖搜索结果，一并发起搜索时取消
        listJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                guardCoroutine("EntityWikiViewModel", "search", onError = {
                    _uiState.update { it.copy(isLoading = false) }
                }) {
                    delay(200) // 防抖：避免每敲一字都发起查询，旧结果覆盖新查询
                    val matches = repository.searchEntities(query, limit = 50)
                    val mapped = matches.map { match ->
                        EntityItem(
                            id = match.entityId,
                            label = match.label,
                            type = "",
                            typeNameZh = match.typeNameZh,
                            aliases = match.aliases,
                            description = match.description,
                            tags = emptyList(),
                            occurrencesCount = match.occurrencesCount
                        )
                    }
                    _uiState.update { it.copy(entities = mapped, isLoading = false) }
                }
            }
        } else {
            // 清空输入框 → 回到分类列表。直接调用 loadEntities（其内部自带 Job 跟踪），
            // 不再嵌套一层 launch：嵌套的 launch 不受 searchJob 取消控制，取消拦不住它写回。
            loadEntities(_uiState.value.selectedCategory)
        }
    }

    fun onEntityClick(entityId: String) {
        entityJob?.cancel()
        entityJob = viewModelScope.launch {
            _uiState.update { it.copy(isSheetLoading = true, showSheet = true) }
            guardCoroutine("EntityWikiViewModel", "onEntityClick", onError = {
                _uiState.update { it.copy(isSheetLoading = false, showSheet = false) }
            }) {
                val ent = repository.getEntityById(entityId)
                _uiState.update {
                    it.copy(selectedEntity = ent, isSheetLoading = false)
                }
            }
        }
    }

    fun dismissSheet() {
        _uiState.update { it.copy(showSheet = false, selectedEntity = null) }
    }
}

/**
 * 实体百科检索页。
 *
 * MD3 规范要点：
 * - 条目卡片统一 OpusOneItemCard（medium 圆角 + surfaceContainerLow）
 * - 类型徽标使用 tertiaryContainer，不再使用 12% 透明度朱红底 + 朱红字
 *   （低透明度叠加在暗色主题下几乎不可读）
 * - 分类筛选使用默认 FilterChip 配色
 * - 搜索框去除自定义 20dp 圆角与朱红描边
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityWikiScreen(
    onBackClick: () -> Unit,
    onNavigateToChapter: (Int, String) -> Unit,
    viewModel: EntityWikiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "知识索引".toScript(scriptMode)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = cd("返回"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OpusOneSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    placeholder = "在词条中搜索".toScript(scriptMode),
                    onSearch = {}
                )
            }

            // 分类筛选 — M3 SecondaryScrollableTabRow（与主页 Tab 样式统一，细腻扁平指示线，无厚重高对比条）
            val selectedTabIndex = ENTITY_CATEGORIES.indexOfFirst { it.key == uiState.selectedCategory }.coerceAtLeast(0)
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = Color.Transparent
                ) {
                    ENTITY_CATEGORIES.forEach { cat ->
                        Tab(
                            selected = uiState.selectedCategory == cat.key,
                            onClick = { viewModel.selectCategory(cat.key) },
                            text = {
                                Text(
                                    text = cat.label.toScript(scriptMode),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.entities.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "该分类下暂无词条".toScript(scriptMode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.entities, key = { it.id }) { item ->
                        OpusOneItemCard(onClick = { viewModel.onEntityClick(item.id) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.label.toScript(scriptMode),
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = ClassicalFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                OpusOneSourceBadge(text = item.typeNameZh.toScript(scriptMode))
                            }


                            if (item.description.isNotBlank()) {
                                Text(
                                    text = OpusOneTagParser.cleanDisplayText(item.description).toScript(scriptMode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showSheet) {
        EntityBottomSheet(
            entity = uiState.selectedEntity,
            isLoading = uiState.isSheetLoading,
            onDismiss = { viewModel.dismissSheet() }
        )
    }
}
