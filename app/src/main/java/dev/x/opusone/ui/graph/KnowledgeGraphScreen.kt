package dev.x.opusone.ui.graph

import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.util.cd
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.util.guardCoroutine
import dev.x.opusone.util.toScript

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.data.OpusOneRepository
import dev.x.opusone.data.model.EntityItem
import dev.x.opusone.theme.*
import dev.x.opusone.ui.components.EntityBottomSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GraphNode(
    val id: String,
    val label: String,
    val role: String,
    val x: Float,
    val y: Float,
    val tone: ChartTone,
    val generation: Int = 0,
    val notes: String = ""
)

data class GraphEdge(
    val fromId: String,
    val toId: String,
    val label: String = "",
    val isDashed: Boolean = false
)

data class GraphPreset(
    val title: String,
    val isGenealogy: Boolean = false,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

val GRAPH_PRESETS = listOf(
    GraphPreset(
        title = "五帝三代",
        isGenealogy = true,
        nodes = listOf(
            GraphNode("黄帝", "黄帝", "人文始祖", 500f, 100f, ChartTone.CRIMSON, 1),
            GraphNode("玄嚣", "玄嚣", "少昊青阳", 350f, 250f, ChartTone.AMBER, 2),
            GraphNode("昌意", "昌意", "降居若水", 660f, 250f, ChartTone.AMBER, 2),
            GraphNode("帝喾", "帝喾", "五帝之一", 350f, 400f, ChartTone.CRIMSON, 3),
            GraphNode("高阳", "颛顼", "高阳圣德", 660f, 400f, ChartTone.CRIMSON, 3),
            GraphNode("契", "契", "商朝始祖", 120f, 550f, ChartTone.INDIGO, 4),
            GraphNode("后稷", "后稷", "周朝始祖", 250f, 550f, ChartTone.JADE, 4),
            GraphNode("帝挚", "帝挚", "继位不善", 380f, 550f, ChartTone.OCHRE, 4),
            GraphNode("尧", "帝尧", "陶唐圣君", 500f, 550f, ChartTone.CRIMSON, 4),
            GraphNode("鲧", "鲧", "治水不就", 660f, 550f, ChartTone.OCHRE, 4),
            GraphNode("舜", "帝舜", "重华孝德", 500f, 700f, ChartTone.CRIMSON, 5),
            GraphNode("禹", "大禹", "夏朝始祖", 660f, 700f, ChartTone.TEAL, 5),
            GraphNode("汤", "商汤", "商汤伐桀", 120f, 750f, ChartTone.INDIGO, 6),
            GraphNode("周文王", "周文王", "西伯昌", 250f, 750f, ChartTone.JADE, 6)
        ),
        edges = listOf(
            GraphEdge("黄帝", "玄嚣", "长子"),
            GraphEdge("黄帝", "昌意", "次子"),
            GraphEdge("玄嚣", "帝喾", "子孙"),
            GraphEdge("昌意", "高阳", "子"),
            GraphEdge("帝喾", "契", "子(商祖)"),
            GraphEdge("帝喾", "后稷", "子(周祖)"),
            GraphEdge("帝喾", "帝挚", "长子"),
            GraphEdge("帝喾", "尧", "次子"),
            GraphEdge("高阳", "鲧", "后世"),
            GraphEdge("尧", "舜", "禅让", true),
            GraphEdge("鲧", "禹", "子(夏祖)"),
            GraphEdge("舜", "禹", "禅让", true),
            GraphEdge("契", "汤", "十四世"),
            GraphEdge("后稷", "周文王", "后裔")
        )
    ),
    GraphPreset(
        title = "大秦世系",
        isGenealogy = true,
        nodes = listOf(
            GraphNode("伯益", "伯益", "赢姓始祖", 360f, 80f, ChartTone.OCHRE, 1),
            GraphNode("非子", "秦非子", "封邑主马", 360f, 200f, ChartTone.AMBER, 2),
            GraphNode("秦襄公", "秦襄公", "始列诸侯", 360f, 320f, ChartTone.AMBER, 3),
            GraphNode("秦穆公", "秦穆公", "春秋五霸", 360f, 440f, ChartTone.CRIMSON, 4),
            GraphNode("秦孝公", "秦孝公", "商鞅变法", 360f, 560f, ChartTone.CRIMSON, 5),
            GraphNode("商鞅", "商鞅", "大良造", 580f, 560f, ChartTone.INDIGO, 5),
            GraphNode("秦惠文王", "惠文王", "初称为王", 360f, 680f, ChartTone.AMBER, 6),
            GraphNode("张仪", "张仪", "连横破合纵", 580f, 680f, ChartTone.INDIGO, 6),
            GraphNode("秦昭襄王", "昭襄王", "长平克赵", 360f, 800f, ChartTone.CRIMSON, 7),
            GraphNode("白起", "白起", "武安君", 580f, 800f, ChartTone.CRIMSON, 7),
            GraphNode("秦庄襄王", "庄襄王", "子楚", 360f, 920f, ChartTone.AMBER, 8),
            GraphNode("吕不韦", "吕不韦", "文信侯相国", 580f, 920f, ChartTone.VIOLET, 8),
            GraphNode("秦始皇", "始皇帝", "千古一帝", 360f, 1040f, ChartTone.CRIMSON, 9),
            GraphNode("李斯", "李斯", "丞相", 580f, 1040f, ChartTone.INDIGO, 9),
            GraphNode("胡亥", "二世胡亥", "望夷之变", 240f, 1160f, ChartTone.OCHRE, 10),
            GraphNode("子婴", "秦王子婴", "降汉", 480f, 1160f, ChartTone.OCHRE, 10)
        ),
        edges = listOf(
            GraphEdge("伯益", "非子", "后世"),
            GraphEdge("非子", "秦襄公", "世系"),
            GraphEdge("秦襄公", "秦穆公", "世系"),
            GraphEdge("秦穆公", "秦孝公", "后世"),
            GraphEdge("商鞅", "秦孝公", "任用变法"),
            GraphEdge("秦孝公", "秦惠文王", "子"),
            GraphEdge("张仪", "秦惠文王", "相国"),
            GraphEdge("秦惠文王", "秦昭襄王", "子"),
            GraphEdge("白起", "秦昭襄王", "统帅"),
            GraphEdge("秦昭襄王", "秦庄襄王", "孙"),
            GraphEdge("吕不韦", "秦庄襄王", "辅佐立嗣"),
            GraphEdge("秦庄襄王", "秦始皇", "长子"),
            GraphEdge("李斯", "秦始皇", "统一度量衡"),
            GraphEdge("秦始皇", "胡亥", "少子"),
            GraphEdge("胡亥", "子婴", "族子")
        )
    ),
    GraphPreset(
        title = "楚汉相争",
        nodes = listOf(
            // 汉阵营（左翼）
            GraphNode("刘邦", "刘邦", "汉高祖", 240f, 140f, ChartTone.CRIMSON, 1),
            GraphNode("萧何", "萧何", "开国丞相", 100f, 290f, ChartTone.JADE, 2),
            GraphNode("张良", "张良", "留侯谋圣", 320f, 290f, ChartTone.INDIGO, 2),
            GraphNode("韩信", "韩信", "淮阴侯大将", 320f, 440f, ChartTone.CRIMSON, 3),
            GraphNode("彭越", "彭越", "合围垓下", 200f, 440f, ChartTone.OCHRE, 3),
            GraphNode("曹参", "曹参", "平阳侯", 100f, 440f, ChartTone.JADE, 3),
            GraphNode("樊哙", "樊哙", "鸿门拔剑", 200f, 580f, ChartTone.AMBER, 4),

            // 楚阵营（右翼）
            GraphNode("项羽", "项羽", "西楚霸王", 640f, 140f, ChartTone.CRIMSON, 1),
            GraphNode("项伯", "项伯", "射阳侯", 560f, 290f, ChartTone.AMBER, 2),
            GraphNode("范增", "范增", "亚父谋士", 760f, 290f, ChartTone.VIOLET, 2),
            GraphNode("龙且", "龙且", "楚将统帅", 560f, 440f, ChartTone.OCHRE, 3),
            GraphNode("钟离昧", "钟离昧", "楚名将", 760f, 440f, ChartTone.OCHRE, 3),
            GraphNode("虞姬", "虞姬", "霸王别姬", 680f, 580f, ChartTone.VIOLET, 4)
        ),
        edges = listOf(
            // 汉阵营内部连线
            GraphEdge("刘邦", "萧何", "镇守关中"),
            GraphEdge("刘邦", "张良", "筹谋帷幄"),
            GraphEdge("刘邦", "韩信", "登坛拜将"),
            GraphEdge("萧何", "曹参", "萧规曹随"),
            GraphEdge("韩信", "彭越", "合兵垓下"),
            GraphEdge("刘邦", "樊哙", "宿卫猛将"),

            // 楚阵营内部连线
            GraphEdge("项羽", "项伯", "从叔"),
            GraphEdge("项羽", "范增", "尊为亚父"),
            GraphEdge("项羽", "龙且", "心腹大将"),
            GraphEdge("项羽", "钟离昧", "战将"),
            GraphEdge("项羽", "虞姬", "情深别姬"),

            // 楚汉跨阵营对抗与沟通（横跨中央，无中间节点阻挡）
            GraphEdge("刘邦", "项羽", "楚汉相争", true),
            GraphEdge("张良", "项伯", "鸿门私通", true),
            GraphEdge("韩信", "龙且", "潍水击杀", true)
        )
    ),
    GraphPreset(
        title = "战国四君",
        nodes = listOf(
            // 齐国孟尝君
            GraphNode("孟尝君", "孟尝君田文", "齐国宗室", 200f, 140f, ChartTone.CRIMSON, 1),
            GraphNode("冯谖", "冯谖", "狡兔三窟", 200f, 300f, ChartTone.INDIGO, 2),

            // 魏国信陵君
            GraphNode("信陵君", "信陵君魏无忌", "魏昭王少子", 560f, 140f, ChartTone.CRIMSON, 1),
            GraphNode("侯赢", "侯嬴", "大梁夷门监", 460f, 300f, ChartTone.INDIGO, 2),
            GraphNode("朱亥", "朱亥", "椎杀晋鄙", 660f, 300f, ChartTone.AMBER, 2),

            // 赵国平原君
            GraphNode("平原君", "平原君赵胜", "赵武灵王之子", 200f, 480f, ChartTone.CRIMSON, 1),
            GraphNode("毛遂", "毛遂", "锥处囊中", 200f, 640f, ChartTone.INDIGO, 2),

            // 楚国春申君
            GraphNode("春申君", "春申君黄歇", "楚考烈王相", 560f, 480f, ChartTone.CRIMSON, 1),
            GraphNode("李园", "李园", "棘门之变", 560f, 640f, ChartTone.OCHRE, 2)
        ),
        edges = listOf(
            GraphEdge("孟尝君", "冯谖", "门客市义"),
            GraphEdge("信陵君", "侯赢", "宾礼参谋"),
            GraphEdge("信陵君", "朱亥", "勇士夺军"),
            GraphEdge("平原君", "毛遂", "自荐使楚"),
            GraphEdge("春申君", "李园", "进女遇害", true)
        )
    )
)

data class KnowledgeGraphUiState(
    val selectedPresetIndex: Int = 0,
    val selectedNodeId: String? = null,
    val selectedEntity: EntityItem? = null,
    val isSheetLoading: Boolean = false,
    val showSheet: Boolean = false
)

class KnowledgeGraphViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)
    private var nodeJob: Job? = null
    private val _uiState = MutableStateFlow(KnowledgeGraphUiState())
    val uiState: StateFlow<KnowledgeGraphUiState> = _uiState.asStateFlow()

    fun selectPreset(index: Int) {
        _uiState.update {
            it.copy(
                selectedPresetIndex = index,
                selectedNodeId = null,
                selectedEntity = null,
                showSheet = false
            )
        }
    }

    fun onNodeClicked(entityId: String) {
        nodeJob?.cancel()
        _uiState.update { it.copy(selectedNodeId = entityId, isSheetLoading = true, showSheet = true) }
        nodeJob = viewModelScope.launch {
            guardCoroutine("KnowledgeGraphViewModel", "onNodeClicked", onError = {
                _uiState.update { it.copy(selectedEntity = null, isSheetLoading = false, showSheet = false) }
            }) {
                val ent = repository.getEntityById(entityId)
                _uiState.update { it.copy(selectedEntity = ent, isSheetLoading = false) }
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedNodeId = null, showSheet = false, selectedEntity = null) }
    }

    fun dismissSheet() {
        // 关闭底抽屉时保留选中的节点高亮，便于继续探索血脉路径
        _uiState.update { it.copy(showSheet = false) }
    }
}

private val CHINESE_GENERATION_NAMES = mapOf(
    1 to "第一世 · 始祖",
    2 to "第二世",
    3 to "第三世",
    4 to "第四世",
    5 to "第五世",
    6 to "第六世",
    7 to "第七世",
    8 to "第八世",
    9 to "第九世",
    10 to "第十世"
)

private fun computeActiveChain(
    selectedId: String?,
    preset: GraphPreset
): Pair<Set<String>, Set<Pair<String, String>>> {
    if (selectedId == null) return emptySet<String>() to emptySet<Pair<String, String>>()
    val activeNodes = mutableSetOf<String>()
    val activeEdges = mutableSetOf<Pair<String, String>>()
    activeNodes.add(selectedId)

    val queue = ArrayDeque<String>()
    queue.add(selectedId)
    while (queue.isNotEmpty()) {
        val curr = queue.removeFirst()
        preset.edges.filter { it.toId == curr }.forEach { edge ->
            activeEdges.add(edge.fromId to edge.toId)
            if (activeNodes.add(edge.fromId)) {
                queue.add(edge.fromId)
            }
        }
    }

    preset.edges.filter { it.fromId == selectedId }.forEach { edge ->
        activeNodes.add(edge.toId)
        activeEdges.add(edge.fromId to edge.toId)
    }

    return activeNodes to activeEdges
}
/**
 * 知识图谱与世系。
 *
 * MD3 规范要点：
 * - 节点只声明「色相语义」（ChartTone），在可组合作用域内解析为实际颜色后
 *   再传入 Canvas，解决原先顶层常量写死颜色导致暗色模式失效的问题
 * - 画布内的表面色 / 文字色取自 colorScheme 后在 Canvas 外解析完毕传入，
 *   DrawScope 内不直接引用主题
 * - TabRow / FAB 全部使用默认配色，不再指定朱红 contentColor
 * - 缩放控件改用标准 SmallFloatingActionButton（40dp），不再是自定 42dp
 */
private fun calculateFitTransform(
    nodes: List<GraphNode>,
    viewportWidth: Float,
    viewportHeight: Float,
    isGenealogy: Boolean = false
): Pair<Float, Offset> {
    if (nodes.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) return 1.0f to Offset(50f, 50f)
    val padLeft = if (isGenealogy) 130f else 80f
    val padRight = 80f
    val padTop = 80f
    val padBottom = if (isGenealogy) 120f else 80f
    val minX = nodes.minOf { it.x } - padLeft
    val maxX = nodes.maxOf { it.x } + padRight
    val minY = nodes.minOf { it.y } - padTop
    val maxY = nodes.maxOf { it.y } + padBottom
    val contentW = (maxX - minX).coerceAtLeast(100f)
    val contentH = (maxY - minY).coerceAtLeast(100f)

    val scale = (minOf(viewportWidth / contentW, viewportHeight / contentH) * 0.90f).coerceIn(0.2f, 4.5f)
    val centerX = (minX + maxX) / 2f
    val centerY = (minY + maxY) / 2f
    val offX = viewportWidth / 2f - centerX * scale
    val offY = viewportHeight / 2f - centerY * scale
    return scale to Offset(offX, offY)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeGraphScreen(
    onBackClick: () -> Unit,
    onNavigateToChapter: (Int, String) -> Unit,
    viewModel: KnowledgeGraphViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current
    val textMeasurer = rememberTextMeasurer()
    val currentPreset = GRAPH_PRESETS[uiState.selectedPresetIndex]

    // Canvas 变换状态
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset(50f, 50f)) }
    var minScale by remember { mutableFloatStateOf(0.2f) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }

    val textLayoutCache = remember(scriptMode, uiState.selectedPresetIndex) { mutableMapOf<String, TextLayoutResult>() }
    var lastScale by remember { mutableFloatStateOf(scale) }
    var lastSelectedId by remember { mutableStateOf(uiState.selectedNodeId) }
    if (lastScale != scale || lastSelectedId != uiState.selectedNodeId) {
        textLayoutCache.clear()
        lastScale = scale
        lastSelectedId = uiState.selectedNodeId
    }

    fun resetView() {
        if (viewportSize.width > 0f && viewportSize.height > 0f) {
            val (fitScale, fitOffset) = calculateFitTransform(
                currentPreset.nodes,
                viewportSize.width,
                viewportSize.height,
                isGenealogy = currentPreset.isGenealogy
            )
            scale = fitScale
            offset = fitOffset
            minScale = fitScale
        } else {
            scale = 1.0f
            offset = Offset(50f, 50f)
            minScale = 1.0f
        }
    }

    var hasAutoFit by remember(uiState.selectedPresetIndex) { mutableStateOf(false) }
    LaunchedEffect(uiState.selectedPresetIndex, viewportSize) {
        if (!hasAutoFit && viewportSize.width > 0f && viewportSize.height > 0f) {
            resetView()
            hasAutoFit = true
        }
    }

    // 在可组合作用域内把色相解析为实际颜色，再交给 DrawScope 使用
    val toneColors = ChartTone.entries.associateWith { it.resolve() }
    val nodeSurface = MaterialTheme.colorScheme.surface
    val nodeLabelColor = MaterialTheme.colorScheme.onSurface
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val edgeLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val edgeDashedColor = MaterialTheme.colorScheme.tertiary

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { menuExpanded = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentPreset.title.toScript(scriptMode),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (menuExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = cd("切换谱系"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            GRAPH_PRESETS.forEachIndexed { idx, preset ->
                                val isSelected = (idx == uiState.selectedPresetIndex)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = preset.title.toScript(scriptMode),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        if (!isSelected) {
                                            viewModel.selectPreset(idx)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = cd("返回"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            val (activeNodes, activeEdges) = remember(uiState.selectedNodeId, currentPreset) {
                computeActiveChain(uiState.selectedNodeId, currentPreset)
            }

            val nodeW = 80f
            val nodeH = 44f
            val hw = (nodeW / 2) * scale
            val hh = (nodeH / 2) * scale
            val cornerRadius = 6f * scale

            // 可交互画布
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .onSizeChanged { size ->
                        viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                    }
                    .pointerInput(currentPreset) {
                        detectTapGestures { tapOffset ->
                            val halfHitW = (nodeW / 2 + 10f) * scale
                            val halfHitH = (nodeH / 2 + 10f) * scale
                            val hitNode = currentPreset.nodes.find { node ->
                                val screenNodeX = node.x * scale + offset.x
                                val screenNodeY = node.y * scale + offset.y
                                val dx = kotlin.math.abs(tapOffset.x - screenNodeX)
                                val dy = kotlin.math.abs(tapOffset.y - screenNodeY)
                                dx <= halfHitW && dy <= halfHitH
                            }
                            if (hitNode != null) {
                                viewModel.onNodeClicked(hitNode.id)
                            } else {
                                // 点击空白区域：取消血脉高亮与关闭抽屉
                                viewModel.clearSelection()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            // 缩放围绕手势中心（centroid）锚定，最小缩放限制为初始全貌适配比例（只能放大，不能再缩小）
                            val newScale = (scale * zoom).coerceIn(minScale, 6.0f)
                            if (scale > 0f) {
                                val worldX = (centroid.x - offset.x) / scale
                                val worldY = (centroid.y - offset.y) / scale
                                offset = centroid - Offset(worldX, worldY) * newScale + pan
                                scale = newScale
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSpacing = 48f * scale
                    if (gridSpacing >= 16f) {
                        val startGridX = offset.x % gridSpacing
                        val startGridY = offset.y % gridSpacing
                        val gridColor = nodeLabelColor.copy(alpha = 0.055f)
                        var gx = startGridX
                        while (gx < size.width) {
                            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                            gx += gridSpacing
                        }
                        var gy = startGridY
                        while (gy < size.height) {
                            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                            gy += gridSpacing
                        }
                    }

                    if (currentPreset.isGenealogy && currentPreset.nodes.any { it.generation > 0 }) {
                        val genMap = currentPreset.nodes.filter { it.generation > 0 }.groupBy { it.generation }
                        val minNodeX = currentPreset.nodes.minOf { it.x }
                        val rulerWorldX = minNodeX - 105f
                        val screenRulerX = rulerWorldX * scale + offset.x

                        val sortedGens = genMap.keys.sorted()
                        if (sortedGens.size >= 2) {
                            val firstNodes = genMap[sortedGens.first()]
                            val lastNodes = genMap[sortedGens.last()]
                            if (!firstNodes.isNullOrEmpty() && !lastNodes.isNullOrEmpty()) {
                                val firstY = firstNodes.map { it.y }.average().toFloat()
                                val lastY = lastNodes.map { it.y }.average().toFloat()
                                drawLine(
                                    color = edgeColor.copy(alpha = 0.35f),
                                    start = Offset(screenRulerX, firstY * scale + offset.y),
                                    end = Offset(screenRulerX, lastY * scale + offset.y),
                                    strokeWidth = 1f * scale
                                )
                            }
                        }

                        genMap.forEach { (gen, nodesInGen) ->
                            val avgY = nodesInGen.map { it.y }.average().toFloat()
                            val screenY = avgY * scale + offset.y
                            if (screenY in -30f..(size.height + 30f)) {
                                val genName = CHINESE_GENERATION_NAMES[gen] ?: "第${gen}世"
                                val genText = genName.toScript(scriptMode)
                                val genCol = edgeLabelColor.copy(alpha = 0.60f)
                                val genKey = "g_${gen}_${genText}_${genCol.value}"
                                val layout = textLayoutCache.getOrPut(genKey) {
                                    textMeasurer.measure(
                                        text = genText,
                                        style = TextStyle(
                                            fontSize = (8.5f * scale).toSp(),
                                            fontFamily = ClassicalFontFamily,
                                            color = genCol
                                        )
                                    )
                                }
                                drawCircle(
                                    color = nodeSurface,
                                    radius = 3.5f * scale,
                                    center = Offset(screenRulerX, screenY)
                                )
                                drawCircle(
                                    color = edgeColor.copy(alpha = 0.65f),
                                    radius = 2.2f * scale,
                                    center = Offset(screenRulerX, screenY)
                                )
                                drawText(
                                    textLayoutResult = layout,
                                    topLeft = Offset(screenRulerX + 6f * scale, screenY - layout.size.height / 2f)
                                )
                            }
                        }
                    }

                    currentPreset.edges.forEach { edge ->
                        val fromNode = currentPreset.nodes.find { it.id == edge.fromId }
                        val toNode = currentPreset.nodes.find { it.id == edge.toId }
                        if (fromNode != null && toNode != null) {
                            val isEdgeActive = activeEdges.contains(edge.fromId to edge.toId)
                            val edgeAlpha = if (activeEdges.isEmpty()) 1f else if (isEdgeActive) 1f else 0.16f
                            val strokeWidth = if (isEdgeActive) 2.2f * scale else 1.3f * scale

                            val fromCenterX = fromNode.x * scale + offset.x
                            val fromCenterY = fromNode.y * scale + offset.y
                            val toCenterX = toNode.x * scale + offset.x
                            val toCenterY = toNode.y * scale + offset.y

                            val dx = toNode.x - fromNode.x
                            val dy = toNode.y - fromNode.y

                            val startX: Float
                            val startY: Float
                            val endX: Float
                            val endY: Float
                            val arrowDirX: Float
                            val arrowDirY: Float

                            if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
                                if (dy >= 0f) {
                                    startX = fromCenterX
                                    startY = fromCenterY + hh
                                    endX = toCenterX
                                    endY = toCenterY - hh
                                    arrowDirX = 0f
                                    arrowDirY = 1f
                                } else {
                                    startX = fromCenterX
                                    startY = fromCenterY - hh
                                    endX = toCenterX
                                    endY = toCenterY + hh
                                    arrowDirX = 0f
                                    arrowDirY = -1f
                                }
                            } else {
                                if (dx >= 0f) {
                                    startX = fromCenterX + hw
                                    startY = fromCenterY
                                    endX = toCenterX - hw
                                    endY = toCenterY
                                    arrowDirX = 1f
                                    arrowDirY = 0f
                                } else {
                                    startX = fromCenterX - hw
                                    startY = fromCenterY
                                    endX = toCenterX + hw
                                    endY = toCenterY
                                    arrowDirX = -1f
                                    arrowDirY = 0f
                                }
                            }

                            val isCombat = edge.label.contains("相争") || edge.label.contains("击杀")
                            val strokeColor = if (isCombat) {
                                toneColors[ChartTone.CRIMSON]?.copy(alpha = edgeAlpha) ?: edgeDashedColor.copy(alpha = edgeAlpha)
                            } else if (edge.isDashed) {
                                edgeDashedColor.copy(alpha = edgeAlpha)
                            } else {
                                edgeColor.copy(alpha = edgeAlpha)
                            }

                            val pathEffect = if (edge.isDashed) {
                                PathEffect.dashPathEffect(floatArrayOf(6f * scale, 5f * scale), 0f)
                            } else null

                            val path = Path().apply {
                                moveTo(startX, startY)
                                if (arrowDirX == 0f) {
                                    cubicTo(
                                        startX, (startY + endY) / 2f,
                                        endX, (startY + endY) / 2f,
                                        endX, endY
                                    )
                                } else {
                                    cubicTo(
                                        (startX + endX) / 2f, startY,
                                        (startX + endX) / 2f, endY,
                                        endX, endY
                                    )
                                }
                            }

                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(width = strokeWidth, pathEffect = pathEffect)
                            )

                            // 流向箭头
                            val arrowLen = 5f * scale
                            val arrowHalfW = 3f * scale
                            val arrowPath = Path().apply {
                                if (arrowDirY > 0f) {
                                    moveTo(endX, endY)
                                    lineTo(endX - arrowHalfW, endY - arrowLen)
                                    lineTo(endX + arrowHalfW, endY - arrowLen)
                                } else if (arrowDirY < 0f) {
                                    moveTo(endX, endY)
                                    lineTo(endX - arrowHalfW, endY + arrowLen)
                                    lineTo(endX + arrowHalfW, endY + arrowLen)
                                } else if (arrowDirX > 0f) {
                                    moveTo(endX, endY)
                                    lineTo(endX - arrowLen, endY - arrowHalfW)
                                    lineTo(endX - arrowLen, endY + arrowHalfW)
                                } else {
                                    moveTo(endX, endY)
                                    lineTo(endX + arrowLen, endY - arrowHalfW)
                                    lineTo(endX + arrowLen, endY + arrowHalfW)
                                }
                                close()
                            }
                            drawPath(path = arrowPath, color = strokeColor)

                            // 关系文字标签
                            if (edge.label.isNotBlank() && scale >= 0.50f) {
                                val midX = (startX + endX) / 2f
                                val midY = (startY + endY) / 2f
                                val edgeText = edge.label.toScript(scriptMode)
                                val edgeCol = edgeLabelColor.copy(alpha = edgeAlpha)
                                val edgeKey = "e_${edge.fromId}_${edge.toId}_${edgeText}_${edgeCol.value}"
                                val labelLayout = textLayoutCache.getOrPut(edgeKey) {
                                    textMeasurer.measure(
                                        text = edgeText,
                                        style = TextStyle(
                                            fontSize = (7.5f * scale).toSp(),
                                            fontFamily = ClassicalFontFamily,
                                            color = edgeCol
                                        )
                                    )
                                }
                                val padH = 3.5f * scale
                                val padV = 1.5f * scale
                                drawRoundRect(
                                    color = nodeSurface.copy(alpha = 0.95f * edgeAlpha),
                                    topLeft = Offset(midX - labelLayout.size.width / 2f - padH, midY - labelLayout.size.height / 2f - padV),
                                    size = Size(labelLayout.size.width + padH * 2f, labelLayout.size.height + padV * 2f),
                                    cornerRadius = CornerRadius(3f * scale, 3f * scale)
                                )
                                drawRoundRect(
                                    color = strokeColor.copy(alpha = 0.35f * edgeAlpha),
                                    topLeft = Offset(midX - labelLayout.size.width / 2f - padH, midY - labelLayout.size.height / 2f - padV),
                                    size = Size(labelLayout.size.width + padH * 2f, labelLayout.size.height + padV * 2f),
                                    cornerRadius = CornerRadius(3f * scale, 3f * scale),
                                    style = Stroke(width = 0.8f * scale)
                                )
                                drawText(
                                    textLayoutResult = labelLayout,
                                    topLeft = Offset(midX - labelLayout.size.width / 2f, midY - labelLayout.size.height / 2f)
                                )
                            }
                        }
                    }

                    currentPreset.nodes.forEach { node ->
                        val nodeX = node.x * scale + offset.x
                        val nodeY = node.y * scale + offset.y
                        val isSelected = (node.id == uiState.selectedNodeId)
                        val isInChain = activeNodes.isEmpty() || activeNodes.contains(node.id)
                        val nodeAlpha = if (isInChain) 1f else 0.22f
                        val nodeColor = toneColors[node.tone] ?: edgeColor

                        val plaqueTopLeft = Offset(nodeX - hw, nodeY - hh)
                        val plaqueSize = Size(nodeW * scale, nodeH * scale)

                        if (isSelected) {
                            drawRoundRect(
                                color = nodeColor.copy(alpha = 0.38f * nodeAlpha),
                                topLeft = Offset(nodeX - hw - 3f * scale, nodeY - hh - 3f * scale),
                                size = Size((nodeW + 6f) * scale, (nodeH + 6f) * scale),
                                cornerRadius = CornerRadius(cornerRadius + 3f * scale)
                            )
                        } else if (isInChain && activeNodes.isNotEmpty()) {
                            drawRoundRect(
                                color = nodeColor.copy(alpha = 0.16f * nodeAlpha),
                                topLeft = Offset(nodeX - hw - 1.5f * scale, nodeY - hh - 1.5f * scale),
                                size = Size((nodeW + 3f) * scale, (nodeH + 3f) * scale),
                                cornerRadius = CornerRadius(cornerRadius + 1.5f * scale)
                            )
                        }

                        drawRoundRect(
                            color = nodeSurface.copy(alpha = nodeAlpha),
                            topLeft = plaqueTopLeft,
                            size = plaqueSize,
                            cornerRadius = CornerRadius(cornerRadius)
                        )

                        val dividerY = nodeY + 3f * scale
                        drawLine(
                            color = nodeColor.copy(alpha = 0.20f * nodeAlpha),
                            start = Offset(nodeX - hw + 4f * scale, dividerY),
                            end = Offset(nodeX + hw - 4f * scale, dividerY),
                            strokeWidth = 0.8f * scale
                        )

                        val strokeWidth = if (isSelected) 2.2f * scale else if (isInChain && activeNodes.isNotEmpty()) 1.6f * scale else 1.1f * scale
                        drawRoundRect(
                            color = nodeColor.copy(alpha = (if (isSelected) 1f else 0.85f) * nodeAlpha),
                            topLeft = plaqueTopLeft,
                            size = plaqueSize,
                            cornerRadius = CornerRadius(cornerRadius),
                            style = Stroke(width = strokeWidth)
                        )

                        val nameText = node.label.toScript(scriptMode)
                        val nameCol = nodeLabelColor.copy(alpha = nodeAlpha)
                        val nameKey = "n_name_${node.id}_${nameText}_${nameCol.value}"
                        val nameLayout = textLayoutCache.getOrPut(nameKey) {
                            textMeasurer.measure(
                                text = nameText,
                                style = TextStyle(
                                    fontSize = (11f * scale).toSp(),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = ClassicalFontFamily,
                                    color = nameCol
                                )
                            )
                        }
                        drawText(
                            textLayoutResult = nameLayout,
                            topLeft = Offset(
                                nodeX - nameLayout.size.width / 2f,
                                nodeY - 6.5f * scale - nameLayout.size.height / 2f
                            )
                        )

                        if (scale >= 0.40f) {
                            val roleText = node.role.toScript(scriptMode)
                            val roleCol = nodeColor.copy(alpha = (if (isInChain) 0.95f else 0.45f) * nodeAlpha)
                            val roleKey = "n_role_${node.id}_${roleText}_${roleCol.value}"
                            val roleLayout = textLayoutCache.getOrPut(roleKey) {
                                textMeasurer.measure(
                                    text = roleText,
                                    style = TextStyle(
                                        fontSize = (7.5f * scale).toSp(),
                                        fontFamily = ClassicalFontFamily,
                                        color = roleCol
                                    )
                                )
                            }
                            drawText(
                                textLayoutResult = roleLayout,
                                topLeft = Offset(
                                    nodeX - roleLayout.size.width / 2f,
                                    nodeY + 11.5f * scale - roleLayout.size.height / 2f
                                )
                            )
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
