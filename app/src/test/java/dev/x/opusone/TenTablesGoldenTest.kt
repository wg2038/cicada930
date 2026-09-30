package dev.x.opusone

import dev.x.opusone.ui.reader.OpusOneTableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * 「十表」解析回归测试（基准数据集比对）。
 *
 * 验证真实库中年表与世系表的列数、表头与单元格解码准确性。
 */
class TenTablesGoldenTest {

    private companion object {
        private const val UNIT_SEP = '\u001f'
        private const val FIXTURE = "ten_tables_golden.txt"

        /** 「序号」列列头，表头行没写它，解析器需补占位 */
        private const val INDEX_HEADER = "序号"

        /** 13 表数据行总数 —— 防止夹具或语料被悄悄截短 */
        private const val TOTAL_ROWS = 1597

        fun md5Of(cells: List<String>): String {
            val joined = cells.joinToString(UNIT_SEP.toString())
            val digest = MessageDigest.getInstance("MD5").digest(joined.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private data class GoldenRow(val indexCell: String, val cellsMd5: String)

    private data class GoldenTable(
        val chapterId: Int,
        val pn: String,
        val colCount: Int,
        val headers: List<String>,
        val rows: List<GoldenRow>,
        val content: String
    )

    private fun loadGolden(): List<GoldenTable> {
        val text = javaClass.classLoader!!.getResourceAsStream(FIXTURE)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("找不到测试夹具 $FIXTURE（应由 gen_golden_fixture.py 生成）")

        val tables = mutableListOf<GoldenTable>()
        var chapterId = 0
        var pn = ""
        var colCount = 0
        var headers = emptyList<String>()
        var rows = mutableListOf<GoldenRow>()
        val contentLines = mutableListOf<String>()
        var inContent = false

        fun flush() {
            if (pn.isEmpty()) return
            tables.add(
                GoldenTable(
                    chapterId = chapterId,
                    pn = pn,
                    colCount = colCount,
                    headers = headers,
                    rows = rows.toList(),
                    content = contentLines.joinToString("\n")
                )
            )
        }

        for (line in text.split('\n')) {
            when {
                line.startsWith("#T ") -> {
                    flush()
                    val f = line.removePrefix("#T ").split(' ')
                    chapterId = f[0].toInt()
                    pn = f[1]
                    colCount = f[2].toInt()
                    headers = emptyList()
                    rows = mutableListOf()
                    contentLines.clear()
                    inContent = false
                }
                line.startsWith("#H ") -> headers = line.removePrefix("#H ").split(UNIT_SEP)
                line.startsWith("#R ") -> {
                    val body = line.removePrefix("#R ")
                    rows.add(
                        GoldenRow(
                            indexCell = body.substringBefore(UNIT_SEP),
                            cellsMd5 = body.substringAfter(UNIT_SEP)
                        )
                    )
                }
                line == "#C" -> inContent = true
                inContent -> contentLines.add(line)
            }
        }
        flush()
        return tables
    }

    @Test
    fun fixtureCoversAllTenTables() {
        val golden = loadGolden()
        assertEquals("夹具应含 13 张表（十表 + 卷十三两张小表）", 13, golden.size)
        assertEquals("数据行总数应与语料一致", TOTAL_ROWS, golden.sumOf { it.rows.size })
        // 列数与「分隔行与真实列数无关」的结论相印证：最大的表是卷十六「秦楚之际月表」
        assertEquals(21, golden.first { it.chapterId == 16 && it.pn == "p_21" }.colCount)
    }

    @Test
    fun everyRealTableIsDetectedAndDecodedExactly() {
        var checkedRows = 0
        for (t in loadGolden()) {
            val where = "ch${t.chapterId}/${t.pn}"

            assertTrue("$where 应被判为表格", OpusOneTableParser.isTableContent(t.content))
            val table = OpusOneTableParser.parseTable(t.content)
            assertNotNull("$where 应能解析成表", table)
            table!!

            assertEquals("$where 列数", t.colCount, table.colCount)
            assertEquals("$where 表头（含空列占位）", t.headers, table.headers)
            assertEquals("$where 首列列头", INDEX_HEADER, table.headers[0])
            assertEquals("$where 数据行数", t.rows.size, table.rows.size)

            for (i in t.rows.indices) {
                val want = t.rows[i]
                val got = table.rows[i]
                assertEquals("$where 第${i + 1}行 序号列", want.indexCell, got.indexCell)
                assertEquals("$where 第${i + 1}行 列数", t.colCount - 1, got.cells.size)
                assertEquals(
                    "$where 第${i + 1}行 单元格内容（序号=${want.indexCell}）",
                    want.cellsMd5, md5Of(got.cells)
                )
                checkedRows++
            }
        }
        assertEquals(TOTAL_ROWS, checkedRows)
    }

    /**
     * 与文本无关的史实锚点 —— 用来证明「列位解对了」，不是只证明「解出来了」。
     *
     * 若把「长空格 run」误读成 0 个空列（曾经的错误做法），这三条会同时失败：
     * 整表左移一列后，「郑」「吴」「周」的首个有值位置都会前移。
     */
    @Test
    fun historicalAnchorsConfirmColumnPositions() {
        val byKey = loadGolden().associateBy { "${it.chapterId}/${it.pn}" }

        // 卷十四「十二诸侯年表」：「郑」列首个有值 = 郑桓公友元年（周宣王二十二年，前806）
        val ch14 = byKey.getValue("14/p_31")
        assertEquals(16, ch14.colCount)
        val zheng = ch14.headers.indexOf("郑") - 1
        assertTrue("卷十四应有「郑」列", zheng >= 0)
        val firstZheng = firstFilledRow(ch14, zheng)
        assertEquals("「郑」列应自第 36 行（郑桓公始封）起才有值", 36, firstZheng)

        // 卷十五「六国年表」：「周」列自赧王卒（第 221 行）之后整列为空
        val ch15 = byKey.getValue("15/p_35")
        assertEquals(9, ch15.colCount)
        val zhou = ch15.headers.indexOf("周") - 1
        assertTrue("卷十五应有「周」列", zhou >= 0)
        val firstEmpty = (6 until ch15.rows.size).first { !hasCell(ch15, it, zhou) }
        assertEquals("「周」列应恰自第 222 行起为空（赧王卒、秦取西周）", 222, firstEmpty + 1)
    }

    /** 表头行没有「序号」列列名，所以数据列 i 对应 headers[i + 1] */
    private fun hasCell(t: GoldenTable, row: Int, dataCol: Int): Boolean =
        OpusOneTableParser.parseTable(t.content)!!.rows[row].cells[dataCol].isNotEmpty()

    private fun firstFilledRow(t: GoldenTable, dataCol: Int): Int =
        OpusOneTableParser.parseTable(t.content)!!.rows.indexOfFirst { it.cells[dataCol].isNotEmpty() } + 1
}
