package dev.x.opusone.ui.reader

/**
 * 结构化年表数据模型。
 *
 * @property headers 表头列表（长度为 [colCount]，首项为序号列列头，通常为“序号”）
 * @property rows 数据行列表
 * @property colCount 总列数
 */
data class ClassicalTable(
    val headers: List<String>,
    val rows: List<ClassicalTableRow>,
    val colCount: Int
)

/**
 * 年表单行数据模型。
 *
 * @property indexCell 冻结的首列单元格内容（行标，如 "r1]" 清洗为 "[1]"）
 * @property cells 可横向滚动的其余各列单元格内容（长度恒等于 colCount - 1）
 */
data class ClassicalTableRow(
    val indexCell: String,
    val cells: List<String>
)

/**
 * 古籍多列表格解析器（针对《史记》“十表”多列编年谱表与世系表）。
 *
 * 解析规则：
 * 1. 依据行内连续空格跨度计算列位间隔，保留真实空白单元格；
 * 2. 提取行首标记（如 r1]、a1] 等）作为首列序号；
 * 3. 表头补充序号列占位，保证表头与数据行列位严格对齐。
 */
object OpusOneTableParser {

    /** 分隔行里的单个「横杠段」，如 `---` */
    private val DIVIDER_TOKEN_REGEX = Regex("^-{2,}$")

    /** 行标：`r1]` / `a12]` / `b3]` -> 序号数字 */
    private val ROW_INDEX_CLEAN_REGEX = Regex("^([a-z])(\\d+)]$")

    /** 历史数字化 / OCR 遗留的连续重复句号（如“元年。。”） */
    private val CONSECUTIVE_PERIODS_REGEX = Regex("。{2,}")

    /**
     * 词法单元：空格 run（组 1）或非空块（组 2）。
     *
     * 块内可以包含 OAM 实体标注，标注自身允许 ASCII 空格（如 `⟪PE 黄帝⟫`），
     * 所以必须把 `⟪…⟫` 整体当成原子，不能让它被空格切开。
     */
    private val TOKEN_REGEX = Regex("( +)|((?:⟪[^⟫]*⟫|[^ ])+)")

    private sealed class Tok {
        /** 空格 run，n 为空格个数 */
        class Space(val n: Int) : Tok()

        /** 非空块（可能含 OAM 标注） */
        class Text(val s: String) : Tok()
    }

    /**
     * 判断文本段落是否为古籍多列表格。
     *
     * 规则：存在一行「整行都由横杠段组成」的分隔线（至少 1 段），
     * 且它不是首行（上面还有表头）、也不是末行（下面还有数据）。
     * 单列表（如卷十三「帝王」条，序号 + 帝王）同样满足。
     */
    fun isTableContent(content: String): Boolean {
        if (!content.contains("---")) return false
        val lines = content.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
        for (i in lines.indices) {
            if (i == 0 || i + 1 >= lines.size) continue
            if (isDividerLine(lines[i])) return true
        }
        return false
    }

    /**
     * 将古籍文本段落解析为结构化年表数据 [ClassicalTable]。
     *
     * 算法通过分隔行定位表头与数据，计算数据行列间空格的最小公约跨度（单位宽 U）进行列对齐，
     * 并处理首列序号与连续标点清洗。
     */
    fun parseTable(content: String): ClassicalTable? {
        val lines = content.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
        if (lines.size < 3) return null

        val dividerIndex = lines.indexOfFirst { isDividerLine(it) }
        if (dividerIndex <= 0 || dividerIndex + 1 >= lines.size) return null

        val headerLine = lines[dividerIndex - 1]
        val dataLines = lines.subList(dividerIndex + 1, lines.size)

        val uData = dataLines
            .mapNotNull { innerRuns(tokenize(it)).drop(1).minOrNull() }
            .minOrNull()
            ?.coerceAtLeast(1)
            ?: 2

        val headerToks = tokenize(headerLine)
        val uHeader = (innerRuns(headerToks).minOrNull() ?: uData).coerceAtLeast(1)

        val headerCells = decodeCells(headerToks, uHeader)
        val rowCells = dataLines.map { decodeCells(tokenize(it), uData) }

        val colCount = maxOf(
            1 + headerCells.size,
            rowCells.maxOfOrNull { it.size } ?: 0
        )
        val headers = MutableList(colCount) { "" }
        headers[0] = "序号"
        headerCells.forEachIndexed { i, name ->
            if (name.isNotEmpty() && 1 + i < colCount) headers[1 + i] = name
        }

        val rows = ArrayList<ClassicalTableRow>(dataLines.size)
        for (cells in rowCells) {
            val rawIndex = cells.firstOrNull() ?: ""
            val matched = ROW_INDEX_CLEAN_REGEX.find(rawIndex)
            val indexCell = if (matched != null) "[${matched.groupValues[2]}]" else rawIndex

            val rest = ArrayList<String>(colCount - 1)
            for (col in 1 until colCount) {
                val cell = if (col < cells.size) cells[col] else ""
                rest.add(cell.replace(CONSECUTIVE_PERIODS_REGEX, "。"))
            }
            rows.add(ClassicalTableRow(indexCell = indexCell, cells = rest))
        }

        return ClassicalTable(headers = headers, rows = rows, colCount = colCount)
    }

    private fun isDividerLine(line: String): Boolean {
        if (!line.contains('-')) return false
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val tokens = trimmed.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        return tokens.isNotEmpty() && tokens.all { DIVIDER_TOKEN_REGEX.matches(it) }
    }

    private fun tokenize(line: String): List<Tok> {
        val out = ArrayList<Tok>()
        for (m in TOKEN_REGEX.findAll(line)) {
            val spaces = m.groupValues[1]
            if (spaces.isNotEmpty()) {
                out.add(Tok.Space(spaces.length))
            } else {
                out.add(Tok.Text(m.groupValues[2]))
            }
        }
        return out
    }

    /** 只取夹在两个非空块之间的空格 run（行首行尾的留白不算） */
    private fun innerRuns(toks: List<Tok>): List<Int> {
        val out = ArrayList<Int>()
        var seenBlock = false
        for (i in toks.indices) {
            when (val t = toks[i]) {
                is Tok.Text -> seenBlock = true
                is Tok.Space -> if (seenBlock && i + 1 < toks.size && toks[i + 1] is Tok.Text) {
                    out.add(t.n)
                }
            }
        }
        return out
    }

    /**
     * 按单位宽 U 解码一行：空格 run 长度 g 表示两格之间空 `g / U - 1` 列。
     * 末尾的留白不产生空列（不会凭空多出尾列）。
     */
    private fun decodeCells(toks: List<Tok>, u: Int): List<String> {
        val cells = ArrayList<String>()
        var pending = 0
        var started = false
        for (t in toks) {
            when (t) {
                is Tok.Text -> {
                    if (started) repeat(pending) { cells.add("") }
                    cells.add(t.s)
                    pending = 0
                    started = true
                }
                is Tok.Space -> if (started) {
                    pending = maxOf(0, t.n / u - 1)
                }
            }
        }
        return cells
    }

    private val WHITESPACE_REGEX = Regex("\\s+")
}
