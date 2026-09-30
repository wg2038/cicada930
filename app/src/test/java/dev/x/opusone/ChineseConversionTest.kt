package dev.x.opusone

import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.util.ChineseConverter
import dev.x.opusone.util.toScript
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseConversionTest {

    @Test
    fun testTraditionalToSimplified() {
        assertEquals("项羽", ChineseConverter.toSimplified("項羽"))
        assertEquals("齐", ChineseConverter.toSimplified("齊"))
        assertEquals("赵", ChineseConverter.toSimplified("趙"))
        assertEquals("燕", ChineseConverter.toSimplified("燕"))
        assertEquals("韩", ChineseConverter.toSimplified("韓"))
        assertEquals("魏", ChineseConverter.toSimplified("魏"))
        assertEquals("楚", ChineseConverter.toSimplified("楚"))
        assertEquals("秦", ChineseConverter.toSimplified("秦"))
    }

    @Test
    fun testSimplifiedToTraditional() {
        assertEquals("項羽", ChineseConverter.toTraditional("项羽"))
        assertEquals("書籤", ChineseConverter.toTraditional("书签"))
        assertEquals("高亮標注", ChineseConverter.toTraditional("高亮标注"))
        assertEquals("行間距", ChineseConverter.toTraditional("行间距"))
        assertEquals("全選", ChineseConverter.toTraditional("全选"))
        assertEquals("默認", ChineseConverter.toTraditional("默认"))
    }

    @Test
    fun testToScriptExtension() {
        assertEquals("書籤", "书签".toScript(ChineseScriptMode.TRADITIONAL))
        assertEquals("书签", "書籤".toScript(ChineseScriptMode.SIMPLIFIED))
    }
}
