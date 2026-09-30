package dev.x.opusone

import dev.x.opusone.ui.reader.OpusOneTableParser
import org.junit.Assert.*
import org.junit.Test

class OpusOneTableParserTest {

    @Test
    fun testNormalProseIsNotTable() {
        val prose = "太史公曰：余于是因秦记，踵春秋之后，起周元王，表六国时事，讫二世，凡二百七十年。"
        assertFalse(OpusOneTableParser.isTableContent(prose))
        assertNull(OpusOneTableParser.parseTable(prose))
    }

    @Test
    fun testDividerSectionIsNotTable() {
        val divider = "---"
        assertFalse(OpusOneTableParser.isTableContent(divider))
        assertNull(OpusOneTableParser.parseTable(divider))
    }

    /**
     * 卷十五《六国年表》片段。
     *
     * 语料特征：表头用 2 空格间隔、数据行用 4 空格间隔；行标 `r1]` 与首个值之间也是 4 空格。
     * 第 2 行 `⟪PE 晋定公⟫卒。` 之后是 **8 个空格**（= 2 个 U），即「韩」列**真实为空**，
     * 必须保留这个空单元格，否则其后各列（赵/楚/燕/齐）会整体左移一列。
     */
    @Test
    fun testParseLiuGuoNianBiaoSample() {
        val sample = """
   周  秦  魏  韩  赵  楚  燕  齐 
 ---  ---  ---  ---  ---  ---  ---  ---  --- 
  r1]    ⟪PE 周元王⟫⟪QU 元年⟫。。    秦⟪PE 厉共公⟫⟪QU 元年⟫。    魏献子。    ⟪PE 韩宣子⟫    ⟪PE 赵简子⟫。    ⟪PE 楚惠王⟫。    燕⟪PE 献公⟫。    ⟪PE 齐平公⟫。  
  r2]    二    二。蜀人来赂。    ⟪PE 晋定公⟫卒。        ⟪QU 四十⟫三    ⟪QU 十四⟫。    十八    六  
        """.trimIndent()

        assertTrue(OpusOneTableParser.isTableContent(sample))
        val table = OpusOneTableParser.parseTable(sample)
        assertNotNull(table)

        // 表头行没有写「序号」列的名字 -> 表头整体右移一列
        assertEquals(9, table!!.colCount)
        assertEquals(9, table.headers.size)
        assertEquals("序号", table.headers[0])
        assertEquals("周", table.headers[1])
        assertEquals("齐", table.headers[8])

        assertEquals(2, table.rows.size)
        // 行标 r1] 是最左一列本身的内容，清洗为 [1]
        assertEquals("[1]", table.rows[0].indexCell)
        assertEquals(8, table.rows[0].cells.size)
        assertEquals("⟪PE 周元王⟫⟪QU 元年⟫。", table.rows[0].cells[0]) // “。。” 折叠为 “。”
        assertEquals("秦⟪PE 厉共公⟫⟪QU 元年⟫。", table.rows[0].cells[1])

        // 第 2 行：8 空格 = 1 个空列，「韩」列为空
        assertEquals("[2]", table.rows[1].indexCell)
        assertEquals(8, table.rows[1].cells.size)
        assertEquals("二", table.rows[1].cells[0])
        assertEquals("二。蜀人来赂。", table.rows[1].cells[1])
        assertEquals("⟪PE 晋定公⟫卒。", table.rows[1].cells[2])
        assertEquals("", table.rows[1].cells[3]) // 韩 真实为空
        assertEquals("⟪QU 四十⟫三", table.rows[1].cells[4]) // 赵
        assertEquals("⟪QU 十四⟫。", table.rows[1].cells[5]) // 楚
    }

    /**
     * 卷十三《三代世表》片段。
     *
     * 语料特征：表头 8 个国号 + 数据行 8 格，行标 `a1]` 与首值之间只有 **1 个空格**
     * （不足一个单位宽，视为相邻列）。
     * 表头行缺「序号」列列头，故 `帝王世国号` 应落在第 2 列（对应首格「黄帝号有熊」）。
     */
    @Test
    fun testParseSanDaiShiBiaoSample() {
        val sample = """
帝王世国号  ⟪PE 颛顼⟫属  俈属  ⟪PE 尧⟫属  ⟪PE 舜⟫属  ⟪CL 夏⟫属  ⟪CL 殷⟫属  ⟪CL 周⟫属 
 ---  ---  ---  ---  ---  ---  ---  --- 
 a1] ⟪PE 黄帝⟫号⟪CL 有熊⟫。  ⟪PE 黄帝⟫生⟪PE 昌意⟫。  ⟪PE 黄帝⟫生⟪PE 玄嚣⟫。  ⟪PE 黄帝⟫生⟪PE 玄嚣⟫。  ⟪PE 黄帝⟫生⟪PE 昌意⟫。  ⟪PE 黄帝⟫生⟪PE 昌意⟫。  ⟪PE 黄帝⟫生⟪PE 玄嚣⟫。  ⟪PE 黄帝⟫生⟪PE 玄嚣⟫。 
        """.trimIndent()

        assertTrue(OpusOneTableParser.isTableContent(sample))
        val table = OpusOneTableParser.parseTable(sample)
        assertNotNull(table)

        assertEquals(9, table!!.colCount)
        assertEquals("序号", table.headers[0])
        assertEquals("帝王世国号", table.headers[1])
        assertEquals("⟪PE 颛顼⟫属", table.headers[2])
        assertEquals("⟪CL 周⟫属", table.headers[8])

        assertEquals(1, table.rows.size)
        assertEquals("[1]", table.rows[0].indexCell)
        assertEquals(8, table.rows[0].cells.size)
        assertEquals("⟪PE 黄帝⟫号⟪CL 有熊⟫。", table.rows[0].cells[0])
        assertEquals("⟪PE 黄帝⟫生⟪PE 昌意⟫。", table.rows[0].cells[1])
    }

    /**
     * 单列表（卷十三「帝王」条）：分隔行只有 1 段横杠，也应识别为表格。
     * 列数为 2（序号 + 帝王）。
     */
    @Test
    fun testParseSingleColumnTable() {
        val sample = """
 帝王 
 --- 
 b1] ⟪PE 帝外丙⟫，⟪PE 汤⟫太子。⟪PE 太丁⟫蚤卒，故立次弟外丙。 
 b2] ⟪PE 帝仲壬⟫，外丙弟。 
        """.trimIndent()

        assertTrue(OpusOneTableParser.isTableContent(sample))
        val table = OpusOneTableParser.parseTable(sample)
        assertNotNull(table)

        assertEquals(2, table!!.colCount)
        assertEquals("序号", table.headers[0])
        assertEquals("帝王", table.headers[1])
        assertEquals(2, table.rows.size)
        assertEquals("[1]", table.rows[0].indexCell)
        assertEquals(1, table.rows[0].cells.size)
        assertEquals("⟪PE 帝外丙⟫，⟪PE 汤⟫太子。⟪PE 太丁⟫蚤卒，故立次弟外丙。", table.rows[0].cells[0])
        assertEquals("[2]", table.rows[1].indexCell)
        assertEquals("⟪PE 帝仲壬⟫，外丙弟。", table.rows[1].cells[0])
    }

    /**
     * 表头含跨列（colspan）空位时，列名必须落在**正确的列位**上，不能被「左堆齐」。
     * 卷十七《汉兴以来诸侯王年表》表头：纪年在第 2 列、楚在第 3 列、齐在第 6 列。
     */
    @Test
    fun testHeaderColspanPositionsPreserved() {
        // 表头：纪年 | 楚 | (跨2列空位) | 齐 ；数据行 楚。 与 齐。 之间恰好 12 空格 = 3 个 U = 2 个空列
        val sample = "  纪年  楚" + " ".repeat(6) + "齐 \n" +
            " ---  ---  ---  ---  --- \n" +
            "  r1]    ⟪PE 高祖⟫⟪QU 元年⟫    楚。" + " ".repeat(12) + "齐。 "

        assertTrue(OpusOneTableParser.isTableContent(sample))
        val table = OpusOneTableParser.parseTable(sample)
        assertNotNull(table)

        assertEquals("序号", table!!.headers[0])
        assertEquals("纪年", table.headers[1])
        assertEquals("楚", table.headers[2])
        assertEquals("", table.headers[3]) // 跨列留下的空位，必须留白而不是被吃掉
        assertEquals("", table.headers[4])
        assertEquals("齐", table.headers[5])
        assertEquals(6, table.colCount)

        assertEquals("[1]", table.rows[0].indexCell)
        assertEquals("⟪PE 高祖⟫⟪QU 元年⟫", table.rows[0].cells[0])
        assertEquals("楚。", table.rows[0].cells[1])
        assertEquals("", table.rows[0].cells[2])
        assertEquals("", table.rows[0].cells[3])
        assertEquals("齐。", table.rows[0].cells[4])
    }
}
