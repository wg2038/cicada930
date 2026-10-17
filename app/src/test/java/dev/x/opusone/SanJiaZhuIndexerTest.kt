package dev.x.opusone

import dev.x.opusone.data.model.SanJiaZhuNote
import dev.x.opusone.data.model.Section
import dev.x.opusone.ui.reader.SanJiaZhuIndexer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanJiaZhuIndexerTest {

    private fun section(
        id: Int,
        pn: String,
        type: String = "paragraph",
        plain: String = "",
        order: Int = id
    ) = Section(
        id = id,
        chapterId = 16,
        pnIndex = pn,
        sectionType = type,
        headingLevel = if (type.startsWith("heading")) 2 else 0,
        headingText = if (type.startsWith("heading")) plain else "",
        taggedContent = plain,
        plainText = plain,
        translation = null,
        orderInChapter = order
    )

    private fun note(
        id: Int,
        sentenceId: String,
        anchor: String = "",
        jijie: String = "注"
    ) = SanJiaZhuNote(
        id = id,
        chapterId = 16,
        noteId = "n$id",
        anchorText = anchor,
        beforeContext = "",
        afterContext = "",
        jijie = jijie,
        suoyin = "",
        zhengyi = "",
        otherNotes = "",
        sentenceId = sentenceId
    )

    /**
     * H1_0 为卷篇总标题，其序文与总论注疏不应向后滚动污染下文的子标题或首个正文段落。
     */
    @Test
    fun noteOnChapterTitleHeadingDoesNotRollForward() {
        val all = listOf(
            section(1, "H1_0", type = "heading1", plain = "秦楚之际月表"),
            section(2, "H2_1", type = "heading2", plain = "太史公读秦楚之际"),
            section(3, "1.1", plain = "太史公读秦楚之际，曰：初作难。")
        )
        val visible = listOf(all[1], all[2])
        val n = note(10, "H1_0")

        val map = SanJiaZhuIndexer.index(all, visible, listOf(n))

        assertTrue(map.isEmpty())
    }

    /**
     * 锚点在子标题（H2）上的注疏，向后滚动到该标题下第一个可见的正文段落（而非挂在标题本身上）。
     */
    @Test
    fun noteOnSubHeadingRollsForwardToFirstVisibleParagraph() {
        val all = listOf(
            section(1, "H2_1", type = "heading2", plain = "太史公读秦楚之际"),
            section(2, "1.1", plain = "太史公读秦楚之际，曰：初作难。")
        )
        val visible = listOf(all[0], all[1])
        val n = note(10, "H2_1")

        val map = SanJiaZhuIndexer.index(all, visible, listOf(n))

        assertEquals(listOf(n), map[all[1].id])
    }

    /** 锚点落在空占位段时同样向后滚动。 */
    @Test
    fun noteOnBlankPlaceholderRollsForward() {
        val all = listOf(
            section(1, "1", plain = ""),
            section(2, "1.1", plain = "太史公曰：有以也夫。")
        )
        val visible = listOf(all[1])
        val n = note(11, "1")

        val map = SanJiaZhuIndexer.index(all, visible, listOf(n))

        assertEquals(listOf(n), map[all[1].id])
    }

    /** 锚点本身就是可见段落时保持原位，不应漂移。 */
    @Test
    fun noteOnVisibleSectionStaysPut() {
        val all = listOf(
            section(1, "1.1", plain = "太史公曰：有以也夫。"),
            section(2, "1.2", plain = "长沙王者，着令甲。")
        )
        val n = note(12, "1.1")

        val map = SanJiaZhuIndexer.index(all, all, listOf(n))

        assertEquals(listOf(n), map[all[0].id])
        assertFalse(map.containsKey(all[1].id))
    }

    /** 没有 sentence_id 的旧注疏按锚点文本匹配，过滤段中的文本也向后滚动。 */
    @Test
    fun textAnchorOnFilteredHeadingRollsForward() {
        val all = listOf(
            section(1, "H2_1", type = "heading2", plain = "太史公读列封至便侯"),
            section(2, "1.1", plain = "于是论次其文。")
        )
        val visible = listOf(all[1])
        val n = note(13, "", anchor = "太史公读列封至便侯")

        val map = SanJiaZhuIndexer.index(all, visible, listOf(n))

        assertEquals(listOf(n), map[all[1].id])
    }

    /** 同一注疏只能出现一次，不能因多段匹配重复渲染。 */
    @Test
    fun duplicateNoteIsAttachedOnce() {
        val all = listOf(
            section(1, "1.1", plain = "太史公曰"),
            section(2, "1.2", plain = "太史公曰")
        )
        val n = note(14, "", anchor = "太史公")

        val map = SanJiaZhuIndexer.index(all, all, listOf(n))

        assertEquals(1, map.values.sumOf { list -> list.count { it.id == n.id } })
    }

    /** 无正文的注疏与无效锚点不应渲染空卡片。 */
    @Test
    fun emptyNotesAreDropped() {
        val all = listOf(section(1, "1.1", plain = "太史公曰"))
        val empty = note(15, "1.1", jijie = "")
        val badAnchor = note(16, "", anchor = "也")

        val map = SanJiaZhuIndexer.index(all, all, listOf(empty, badAnchor))

        assertTrue(map.isEmpty())
    }

    /** 位于文末、后面没有任何可见段的注疏无法展示，但不应崩溃。 */
    @Test
    fun trailingFilteredAnchorIsDropped() {
        val all = listOf(
            section(1, "1.1", plain = "正文"),
            section(2, "1.2", type = "divider", plain = "---")
        )
        val visible = listOf(all[0])
        val n = note(17, "1.2")

        val map = SanJiaZhuIndexer.index(all, visible, listOf(n))

        assertTrue(map.isEmpty())
    }
}
