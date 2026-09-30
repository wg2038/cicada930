package dev.x.opusone.ui.timeline

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.x.opusone.theme.ChartTone
import dev.x.opusone.theme.ClassicalFontFamily
import dev.x.opusone.theme.LocalChineseScript
import dev.x.opusone.theme.resolve
import dev.x.opusone.util.cd
import dev.x.opusone.util.toScript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 编年长河史实节点（纪事玉牒）。
 */
data class ChronicleNode(
    val id: String,
    val name: String,
    val era: String,
    val yearBc: Int,
    val epochId: Int,
    val x: Float,
    val y: Float,
    val chapterId: Int,
    val chapterTitle: String,
    val sectionPn: String,
    val summary: String,
    val tone: ChartTone = ChartTone.CRIMSON
)

/**
 * 历史转折与因果连线。
 */
data class ChronicleEdge(
    val fromId: String,
    val toId: String,
    val label: String = "",
    val isDashed: Boolean = false
)

/**
 * 纪元标尺刻度。
 */
data class RulerMilestone(
    val label: String,
    val subLabel: String,
    val y: Float,
    val tone: ChartTone
)

/**
 * 编年长河预设视角。
 */
data class ChroniclePreset(
    val title: String,
    val epochId: Int? = null,
    val nodes: List<ChronicleNode>,
    val edges: List<ChronicleEdge>,
    val milestones: List<RulerMilestone> = emptyList()
)

/**
 * 十九大核心纪事节点数据。
 *
 * 坐标经过精细布局（最小间距 >= 150f，远超 140f 防碰撞阈值）：
 * - 上古三代 (y=160, x: 220 -> 900)
 * - 春秋战国 (y=380, x: 220 -> 900)
 * - 大秦一统 (y=600, x: 300 -> 820)
 * - 楚汉两汉 (y=820, x: 180 -> 930)
 * 顺时流淌，纪元脉络分明。
 */
val CHRONICLE_NODES: List<ChronicleNode> = listOf(
    // 纪元一 · 上古三代 (y=160)
    ChronicleNode("S01", "阪泉涿鹿", "约前2680", 2680, 1, 220f, 160f, 1, "五帝本纪", "1.1", "黄帝战炎帝、擒蚩尤，一统万邦", ChartTone.TEAL),
    ChronicleNode("S02", "大禹治水", "约前2070", 2070, 1, 390f, 160f, 2, "夏本纪", "1", "禹平水土，会稽论功，开夏后氏四百年基业", ChartTone.TEAL),
    ChronicleNode("S03", "盘庚迁殷", "约前1300", 1300, 1, 560f, 160f, 3, "殷本纪", "7", "盘庚渡河迁都，商道复兴", ChartTone.TEAL),
    ChronicleNode("S04", "武王伐纣", "前1046", 1046, 1, 730f, 160f, 4, "周本纪", "11", "牧野之战，商覆周兴，成康之治", ChartTone.JADE),
    ChronicleNode("S05", "平王东迁", "前770", 770, 1, 900f, 160f, 4, "周本纪", "23", "幽王烽火戏诸侯，平王迁都洛邑，春秋始", ChartTone.AMBER),

    // 纪元二 · 春秋战国 (y=380)
    ChronicleNode("S06", "齐桓首霸", "前651", 651, 2, 220f, 380f, 32, "齐太公世家", "15", "葵丘之会，尊王攘夷，九合诸侯", ChartTone.CRIMSON),
    ChronicleNode("S07", "晋楚争霸", "前597", 597, 2, 390f, 380f, 39, "晋世家", "28", "邲之战，楚庄王问鼎中原", ChartTone.AMBER),
    ChronicleNode("S08", "三家分晋", "前403", 403, 2, 560f, 380f, 44, "魏世家", "1", "韩赵魏列为诸侯，战国大幕拉开", ChartTone.OCHRE),
    ChronicleNode("S09", "商鞅变法", "前356", 356, 2, 730f, 380f, 68, "商君列传", "2", "废井田开阡陌，秦国富国强兵", ChartTone.INDIGO),
    ChronicleNode("S10", "长平之战", "前260", 260, 2, 900f, 380f, 73, "白起王翦列传", "5", "白起围歼赵括，坑杀赵军四十万", ChartTone.CRIMSON),

    // 纪元三 · 大秦一统 (y=600)
    ChronicleNode("S11", "始皇称帝", "前221", 221, 3, 300f, 600f, 6, "秦始皇本纪", "15", "吞并六国，废分封立郡县，车同轨书同文", ChartTone.CRIMSON),
    ChronicleNode("S12", "沙丘之变", "前210", 210, 3, 560f, 600f, 6, "秦始皇本纪", "38", "始皇崩，赵高李斯矫诏立胡亥", ChartTone.OCHRE),
    ChronicleNode("S13", "大泽乡起义", "前209", 209, 3, 820f, 600f, 48, "陈涉世家", "2", "陈胜吴广揭竿而起，王侯将相宁有种乎", ChartTone.AMBER),

    // 纪元四 · 楚汉西汉 (y=820)
    ChronicleNode("S14", "巨鹿之战", "前207", 207, 4, 180f, 820f, 7, "项羽本纪", "19", "破釜沉舟，项羽诸侯上将军", ChartTone.CRIMSON),
    ChronicleNode("S15", "鸿门之宴", "前206", 206, 4, 330f, 820f, 7, "项羽本纪", "23", "项庄舞剑意在沛公，刘邦脱险入关", ChartTone.VIOLET),
    ChronicleNode("S16", "垓下合围", "前202", 202, 4, 480f, 820f, 7, "项羽本纪", "36", "四面楚歌，霸王别姬，乌江自刎", ChartTone.CRIMSON),
    ChronicleNode("S17", "高祖建汉", "前202", 202, 4, 630f, 820f, 8, "高祖本纪", "33", "定都长安，约法三章，郡国并行", ChartTone.INDIGO),
    ChronicleNode("S18", "文景之治", "前180", 180, 4, 780f, 820f, 10, "孝文本纪", "1", "轻徭薄赋，与民休息，天下殷富", ChartTone.JADE),
    ChronicleNode("S19", "武帝封禅", "前110", 110, 4, 930f, 820f, 28, "封禅书", "42", "汉武帝登泰山封禅，太史公著史成书", ChartTone.INDIGO)
)

/** 历史转折因果脉络连线 */
val CHRONICLE_EDGES = listOf(
    ChronicleEdge("S01", "S02", "夏启神州"),
    ChronicleEdge("S02", "S03", "迁殷复兴"),
    ChronicleEdge("S03", "S04", "牧野更替"),
    ChronicleEdge("S04", "S05", "宗周倾覆"),
    ChronicleEdge("S05", "S06", "春秋争霸"),
    ChronicleEdge("S06", "S07", "问鼎中原"),
    ChronicleEdge("S07", "S08", "三家分晋"),
    ChronicleEdge("S08", "S09", "变法图强"),
    ChronicleEdge("S09", "S10", "长平坑赵"),
    ChronicleEdge("S10", "S11", "扫灭六国"),
    ChronicleEdge("S11", "S12", "沙丘遗恨"),
    ChronicleEdge("S12", "S13", "斩木揭竿"),
    ChronicleEdge("S13", "S14", "破釜沉舟"),
    ChronicleEdge("S14", "S15", "鸿门赴宴"),
    ChronicleEdge("S15", "S16", "垓下合围"),
    ChronicleEdge("S16", "S17", "汉鼎初立"),
    ChronicleEdge("S17", "S18", "休养生息"),
    ChronicleEdge("S18", "S19", "太史立著")
)

/** 治乱纪元史尺刻度 */
val CHRONICLE_MILESTONES = listOf(
    RulerMilestone("上古三代", "约前2680 - 前770", 160f, ChartTone.TEAL),
    RulerMilestone("春秋战国", "前651 - 前260", 380f, ChartTone.CRIMSON),
    RulerMilestone("大秦一统", "前221 - 前209", 600f, ChartTone.OCHRE),
    RulerMilestone("楚汉西汉", "前207 - 前110", 820f, ChartTone.INDIGO)
)

val CHRONICLE_PRESETS = listOf(
    ChroniclePreset(
        title = "编年长河",
        nodes = CHRONICLE_NODES,
        edges = CHRONICLE_EDGES,
        milestones = CHRONICLE_MILESTONES
    )
)

/**
 * 计算画布最佳自适应视口矩阵。
 */
fun calculateTimelineFitTransform(
    nodes: List<ChronicleNode>,
    viewportWidth: Float,
    viewportHeight: Float
): Pair<Float, Offset> {
    if (nodes.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) {
        return 1.0f to Offset(50f, 50f)
    }
    val padLeft = 130f
    val padRight = 80f
    val padTop = 80f
    val padBottom = 95f
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

data class TimelineUiState(
    val selectedNodeId: String? = null,
    val selectedNode: ChronicleNode? = null,
    val showNodeDialog: Boolean = false
)

class TimelineViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    fun onNodeClick(node: ChronicleNode) {
        _uiState.update {
            it.copy(
                selectedNodeId = node.id,
                selectedNode = node,
                showNodeDialog = true
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedNodeId = null,
                selectedNode = null,
                showNodeDialog = false
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                showNodeDialog = false,
                selectedNode = null
            )
        }
    }
}

/**
 * 计算选定事件的因果链（溯源前因与承继后果）。
 */
private fun computeActiveChronicleChain(
    selectedId: String?,
    preset: ChroniclePreset
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
 * 编年长河。
 *
 * 编年史时间线长河视图：
 * - 集中展示通史时间线；
 * - 节点展示纪年、事件铭文、出处；
 * - 支持缩放平移与章节跳转。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronicleTimelineScreen(
    onBackClick: () -> Unit,
    onNavigateToChapter: (Int, String) -> Unit,
    viewModel: TimelineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scriptMode = LocalChineseScript.current
    val textMeasurer = rememberTextMeasurer()
    val chroniclePreset = CHRONICLE_PRESETS[0]

    // 处理系统返回手势：弹框打开时优先关闭弹框，选中节点时清除选中状态，否则由上层导航返回
    BackHandler(enabled = uiState.showNodeDialog || uiState.selectedNodeId != null) {
        if (uiState.showNodeDialog) {
            viewModel.dismissDialog()
        } else {
            viewModel.clearSelection()
        }
    }

    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset(50f, 50f)) }
    var minScale by remember { mutableFloatStateOf(0.2f) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }

    val scaleKey = (scale * 1000).toInt()  // 量化为整数避免浮点微抖动
    val textLayoutCache = remember(scriptMode, scaleKey, uiState.selectedNodeId) {
        mutableMapOf<String, TextLayoutResult>()
    }

    fun fitToScreen() {
        if (viewportSize.width > 0f && viewportSize.height > 0f) {
            val (fitScale, fitOffset) = calculateTimelineFitTransform(
                chroniclePreset.nodes,
                viewportSize.width,
                viewportSize.height
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

    var hasAutoFit by remember { mutableStateOf(false) }
    LaunchedEffect(viewportSize) {
        if (!hasAutoFit && viewportSize.width > 0f && viewportSize.height > 0f) {
            fitToScreen()
            hasAutoFit = true
        }
    }

    // 主题色与色相映射
    val toneColors = ChartTone.entries.associateWith { it.resolve() }
    val nodeSurface = MaterialTheme.colorScheme.surface
    val nodeLabelColor = MaterialTheme.colorScheme.onSurface
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val edgeLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val riverActiveColor = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "编年长河".toScript(scriptMode),
                        style = MaterialTheme.typography.titleLarge
                    )
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
            val (activeNodes, activeEdges) = remember(uiState.selectedNodeId, chroniclePreset) {
                computeActiveChronicleChain(uiState.selectedNodeId, chroniclePreset)
            }

            val nodeW = 86f
            val nodeH = 46f
            val hw = (nodeW / 2) * scale
            val hh = (nodeH / 2) * scale
            val cornerRadius = 6f * scale

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .onSizeChanged { size ->
                        viewportSize = Size(size.width.toFloat(), size.height.toFloat())
                    }
                    .pointerInput(chroniclePreset) {
                        detectTapGestures { tapOffset ->
                            val halfHitW = (nodeW / 2) * scale + 16f
                            val halfHitH = (nodeH / 2) * scale + 16f
                            val hitNode = chroniclePreset.nodes.findLast { node ->
                                val screenNodeX = node.x * scale + offset.x
                                val screenNodeY = node.y * scale + offset.y
                                val dx = kotlin.math.abs(tapOffset.x - screenNodeX)
                                val dy = kotlin.math.abs(tapOffset.y - screenNodeY)
                                dx <= halfHitW && dy <= halfHitH
                            }
                            if (hitNode != null) {
                                viewModel.onNodeClick(hitNode)
                            } else {
                                viewModel.clearSelection()
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
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
                        val startGridX = ((offset.x % gridSpacing) + gridSpacing) % gridSpacing
                        val startGridY = ((offset.y % gridSpacing) + gridSpacing) % gridSpacing
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

                    if (chroniclePreset.milestones.isNotEmpty()) {
                        val minNodeX = chroniclePreset.nodes.minOfOrNull { it.x } ?: 180f
                        val rulerWorldX = minNodeX - 105f
                        val screenRulerX = rulerWorldX * scale + offset.x

                        chroniclePreset.milestones.forEach { milestone ->
                            val screenY = milestone.y * scale + offset.y
                            if (screenY in -40f..(size.height + 40f)) {
                                val mColor = toneColors[milestone.tone] ?: edgeColor

                                drawCircle(
                                    color = mColor.copy(alpha = 0.65f),
                                    radius = 3f * scale,
                                    center = Offset(screenRulerX, screenY)
                                )
                                drawCircle(
                                    color = nodeSurface,
                                    radius = 1.6f * scale,
                                    center = Offset(screenRulerX, screenY)
                                )

                                val titleText = milestone.label.toScript(scriptMode)
                                val titleColor = mColor.copy(alpha = 0.85f)
                                val titleKey = "m_t_${milestone.label}_${titleText}_${titleColor.value}"
                                val titleLayout = textLayoutCache.getOrPut(titleKey) {
                                    textMeasurer.measure(
                                        text = titleText,
                                        style = TextStyle(
                                            fontSize = (9f * scale).toSp(),
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = ClassicalFontFamily,
                                            color = titleColor
                                        )
                                    )
                                }
                                drawText(
                                    textLayoutResult = titleLayout,
                                    topLeft = Offset(screenRulerX + 8f * scale, screenY - titleLayout.size.height + 1f * scale)
                                )

                                if (scale >= 0.40f) {
                                    val subText = milestone.subLabel.toScript(scriptMode)
                                    val subColor = edgeLabelColor.copy(alpha = 0.55f)
                                    val subKey = "m_s_${milestone.label}_${subText}_${subColor.value}"
                                    val subLayout = textLayoutCache.getOrPut(subKey) {
                                        textMeasurer.measure(
                                            text = subText,
                                            style = TextStyle(
                                                fontSize = (6.8f * scale).toSp(),
                                                fontFamily = ClassicalFontFamily,
                                                color = subColor
                                            )
                                        )
                                    }
                                    drawText(
                                        textLayoutResult = subLayout,
                                        topLeft = Offset(screenRulerX + 8f * scale, screenY + 2f * scale)
                                    )
                                }
                            }
                        }
                    }

                    chroniclePreset.edges.forEach { edge ->
                        val fromNode = chroniclePreset.nodes.find { it.id == edge.fromId }
                        val toNode = chroniclePreset.nodes.find { it.id == edge.toId }
                        if (fromNode != null && toNode != null) {
                            val isEdgeActive = activeEdges.contains(edge.fromId to edge.toId)
                            val edgeAlpha = if (activeEdges.isEmpty()) 1f else if (isEdgeActive) 1f else 0.16f
                            val strokeWidth = if (isEdgeActive) 2.4f * scale else 1.4f * scale

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

                            if (kotlin.math.abs(dy) > 30f) {
                                // 跨行弯曲流转（曲水回澜）
                                startX = fromCenterX
                                startY = fromCenterY + hh
                                endX = toCenterX
                                endY = toCenterY - hh
                                arrowDirX = 0f
                                arrowDirY = 1f
                            } else {
                                // 同一历史时期横向顺流
                                startX = fromCenterX + hw
                                startY = fromCenterY
                                endX = toCenterX - hw
                                endY = toCenterY
                                arrowDirX = 1f
                                arrowDirY = 0f
                            }

                            val strokeColor = if (isEdgeActive) riverActiveColor.copy(alpha = edgeAlpha) else edgeColor.copy(alpha = edgeAlpha)

                            val path = Path().apply {
                                moveTo(startX, startY)
                                if (arrowDirX == 0f) {
                                    val ctrlDistY = kotlin.math.max(30f * scale, kotlin.math.abs(endY - startY) * 0.45f)
                                    cubicTo(
                                        startX, startY + ctrlDistY,
                                        endX, endY - ctrlDistY,
                                        endX, endY
                                    )
                                } else {
                                    val ctrlDistX = kotlin.math.max(20f * scale, (endX - startX) * 0.45f)
                                    cubicTo(
                                        startX + ctrlDistX, startY,
                                        endX - ctrlDistX, endY,
                                        endX, endY
                                    )
                                }
                            }

                            // 水流微晕底衬
                            drawPath(
                                path = path,
                                color = strokeColor.copy(alpha = 0.10f * edgeAlpha),
                                style = Stroke(width = 6f * scale)
                            )
                            // 水墨流线主干
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(width = strokeWidth)
                            )

                            // 流向箭头
                            val arrowLen = 5.5f * scale
                            val arrowHalfW = 3.2f * scale
                            val arrowPath = Path().apply {
                                if (arrowDirY > 0f) {
                                    moveTo(endX, endY)
                                    lineTo(endX - arrowHalfW, endY - arrowLen)
                                    lineTo(endX + arrowHalfW, endY - arrowLen)
                                } else {
                                    moveTo(endX, endY)
                                    lineTo(endX - arrowLen, endY - arrowHalfW)
                                    lineTo(endX - arrowLen, endY + arrowHalfW)
                                }
                                close()
                            }
                            drawPath(path = arrowPath, color = strokeColor)

                            // 历史转折关隘释文标签
                            if (edge.label.isNotBlank() && scale >= 0.45f) {
                                val midX = (startX + endX) / 2f
                                val midY = (startY + endY) / 2f
                                val edgeText = edge.label.toScript(scriptMode)
                                val labelCol = edgeLabelColor.copy(alpha = edgeAlpha)
                                val edgeKey = "e_${edge.fromId}_${edge.toId}_${edgeText}_${labelCol.value}"
                                val labelLayout = textLayoutCache.getOrPut(edgeKey) {
                                    textMeasurer.measure(
                                        text = edgeText,
                                        style = TextStyle(
                                            fontSize = (7.5f * scale).toSp(),
                                            fontFamily = ClassicalFontFamily,
                                            color = labelCol
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

                    chroniclePreset.nodes.forEach { node ->
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

                        if (scale >= 0.40f) {
                            val eraText = node.era.toScript(scriptMode)
                            val eraCol = nodeColor.copy(alpha = 0.90f * nodeAlpha)
                            val eraKey = "n_era_${node.id}_${eraText}_${eraCol.value}"
                            val eraLayout = textLayoutCache.getOrPut(eraKey) {
                                textMeasurer.measure(
                                    text = eraText,
                                    style = TextStyle(
                                        fontSize = (6.8f * scale).toSp(),
                                        fontFamily = ClassicalFontFamily,
                                        color = eraCol
                                    )
                                )
                            }
                            drawText(
                                textLayoutResult = eraLayout,
                                topLeft = Offset(
                                    nodeX - eraLayout.size.width / 2f,
                                    nodeY - 14f * scale - eraLayout.size.height / 2f
                                )
                            )
                        }

                        val nameText = node.name.toScript(scriptMode)
                        val nameCol = nodeLabelColor.copy(alpha = nodeAlpha)
                        val nameKey = "n_name_${node.id}_${nameText}_${nameCol.value}"
                        val nameLayout = textLayoutCache.getOrPut(nameKey) {
                            textMeasurer.measure(
                                text = nameText,
                                style = TextStyle(
                                    fontSize = (10.5f * scale).toSp(),
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
                                nodeY - 4.5f * scale - nameLayout.size.height / 2f
                            )
                        )

                        if (scale >= 0.40f) {
                            val chapText = "《${node.chapterTitle}》".toScript(scriptMode)
                            val chapCol = nodeColor.copy(alpha = (if (isInChain) 0.95f else 0.48f) * nodeAlpha)
                            val chapKey = "n_chap_${node.id}_${chapText}_${chapCol.value}"
                            val chapterLayout = textLayoutCache.getOrPut(chapKey) {
                                textMeasurer.measure(
                                    text = chapText,
                                    style = TextStyle(
                                        fontSize = (7.2f * scale).toSp(),
                                        fontFamily = ClassicalFontFamily,
                                        color = chapCol
                                    )
                                )
                            }
                            drawText(
                                textLayoutResult = chapterLayout,
                                topLeft = Offset(
                                    nodeX - chapterLayout.size.width / 2f,
                                    nodeY + 11.5f * scale - chapterLayout.size.height / 2f
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // 史实研读抽屉（M3 规范 ModalBottomSheet 替代居中弹窗）
    val selectedNode = uiState.selectedNode
    if (uiState.showNodeDialog && selectedNode != null) {
        val st = selectedNode
        val scrollState = rememberScrollState()

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissDialog() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = st.name.toScript(scriptMode),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = ClassicalFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = st.era.toScript(scriptMode),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    fontFamily = ClassicalFontFamily,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = "《史记 · ${st.chapterTitle}》".toScript(scriptMode),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = ClassicalFontFamily,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.dismissDialog() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = cd("关闭"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = st.summary.toScript(scriptMode),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = ClassicalFontFamily,
                            lineHeight = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "本纪事载于《史记 · ${st.chapterTitle}》".toScript(scriptMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        viewModel.dismissDialog()
                        onNavigateToChapter(st.chapterId, st.sectionPn)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "前往原文精读".toScript(scriptMode),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
