package dev.x.opusone

import dev.x.opusone.data.model.TagType
import dev.x.opusone.ui.reader.OpusOneTagParser
import org.junit.Assert.assertEquals
import org.junit.Test

class OpusOneTagParserTest {

    @Test
    fun testParseOamText() {
        val sample = "⟪PE 黄帝⟫与⟪PE 炎帝⟫⟪MV 战⟫于⟪PL 阪泉之野⟫。"
        val enabledTags = setOf(TagType.PERSON, TagType.PLACE, TagType.MILITARY_VERB)
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = enabledTags,
            isDark = false
        )

        assertEquals("黄帝与炎帝战于阪泉之野。", annotated.text)

        val annotations = annotated.getStringAnnotations("ENTITY_CLICK", 0, annotated.text.length)
        assertEquals(4, annotations.size)
        assertEquals("黄帝", annotations[0].item)
        assertEquals("炎帝", annotations[1].item)
        assertEquals("战", annotations[2].item)
        assertEquals("阪泉之野", annotations[3].item)
    }

    @Test
    fun testAliasUsesIndexKey() {
        val sample = "⟪PE 政|嬴政⟫立。"
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = setOf(TagType.PERSON),
            isDark = false
        )

        assertEquals("政立。", annotated.text)
        val annotations = annotated.getStringAnnotations("ENTITY_CLICK", 0, annotated.text.length)
        assertEquals(1, annotations.size)
        assertEquals("嬴政", annotations[0].item)
    }

    @Test
    fun testFilterToggle() {
        val sample = "⟪PE 黄帝⟫与⟪PE 炎帝⟫⟪MV 战⟫于⟪PL 阪泉之野⟫。"
        // Only enable PERSON
        val enabledTags = setOf(TagType.PERSON)
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = enabledTags,
            isDark = false
        )

        assertEquals("黄帝与炎帝战于阪泉之野。", annotated.text)

        // 行为约定：关闭某类标签仅去除高亮，ENTITY_CLICK 注解保留（仍可点击查看实体）
        val annotations = annotated.getStringAnnotations("ENTITY_CLICK", 0, annotated.text.length)
        assertEquals(4, annotations.size)
        assertEquals("黄帝", annotations[0].item)
        assertEquals("炎帝", annotations[1].item)
    }

    @Test
    fun testUnknownCodeDegradesToClickablePlain() {
        // 未知代码：无专属样式，但保留点击语义（规范 §3 TX 降级）
        val sample = "⟪ZZ 甸服⟫之制。"
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = TagType.entries.toSet(),
            isDark = false
        )

        assertEquals("甸服之制。", annotated.text)
        val annotations = annotated.getStringAnnotations("ENTITY_CLICK", 0, annotated.text.length)
        assertEquals(1, annotations.size)
        assertEquals("甸服", annotations[0].item)
    }

    @Test
    fun testCleanDisplayTextStripsOam() {
        assertEquals("羽", OpusOneTagParser.cleanDisplayText("⟪PE 羽|项羽⟫"))
        assertEquals("黄帝与炎帝战于阪泉之野。", OpusOneTagParser.cleanDisplayText("⟪PE 黄帝⟫与⟪PE 炎帝⟫⟪MV 战⟫于⟪PL 阪泉之野⟫。"))
    }

    @Test
    fun testVerbBgAnnotationAndColors() {
        val sample = "⟪PE 黄帝⟫与⟪PE 炎帝⟫⟪MV 战⟫于⟪PL 阪泉之野⟫。"
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = setOf(TagType.PERSON, TagType.PLACE, TagType.MILITARY_VERB),
            isDark = false
        )

        val verbBgs = annotated.getStringAnnotations("VERB_BG", 0, annotated.text.length)
        assertEquals(1, verbBgs.size)
        assertEquals(TagType.MILITARY_VERB.name, verbBgs[0].item)
        assertEquals("战", annotated.text.substring(verbBgs[0].start, verbBgs[0].end))

        // When MILITARY_VERB is disabled, VERB_BG should not be present
        val disabledAnnotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = setOf(TagType.PERSON, TagType.PLACE),
            isDark = false
        )
        val disabledVerbBgs = disabledAnnotated.getStringAnnotations("VERB_BG", 0, disabledAnnotated.text.length)
        assertEquals(0, disabledVerbBgs.size)

        // Verify helper methods
        assertEquals(true, OpusOneTagParser.isVerbTag(TagType.MILITARY_VERB))
        assertEquals(false, OpusOneTagParser.isVerbTag(TagType.PERSON))
        org.junit.Assert.assertNotNull(OpusOneTagParser.getVerbBackgroundColor(TagType.MILITARY_VERB, false))
        org.junit.Assert.assertNotNull(OpusOneTagParser.getVerbBackgroundColor(TagType.MILITARY_VERB, true))
    }

    @Test
    fun testLinkAnnotationClickableAttachedWhenCallbackProvided() {
        val clickedList = mutableListOf<String>()
        val sample = "⟪PE 政|嬴政⟫立，⟪MV 战⟫于⟪PL 阪泉之野⟫。"
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = setOf(TagType.PERSON, TagType.MILITARY_VERB, TagType.PLACE),
            isDark = false,
            onEntityClick = { clickedList.add(it) }
        )

        val links = annotated.getLinkAnnotations(0, annotated.text.length)
        assertEquals(3, links.size)

        val link1 = links[0].item as androidx.compose.ui.text.LinkAnnotation.Clickable
        assertEquals("嬴政", link1.tag)
        org.junit.Assert.assertNull(link1.styles?.style)

        val link2 = links[1].item as androidx.compose.ui.text.LinkAnnotation.Clickable
        assertEquals("战", link2.tag)

        val link3 = links[2].item as androidx.compose.ui.text.LinkAnnotation.Clickable
        assertEquals("阪泉之野", link3.tag)

        // Trigger onClick listeners
        link1.linkInteractionListener?.onClick(link1)
        link2.linkInteractionListener?.onClick(link2)
        link3.linkInteractionListener?.onClick(link3)

        assertEquals(listOf("嬴政", "战", "阪泉之野"), clickedList)
    }

    @Test
    fun testLinkAnnotationNotAddedWhenCallbackNull() {
        val sample = "⟪PE 黄帝⟫立。"
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = setOf(TagType.PERSON),
            isDark = false,
            onEntityClick = null
        )

        val links = annotated.getLinkAnnotations(0, annotated.text.length)
        assertEquals(0, links.size)

        // But ENTITY_CLICK string annotation still exists
        val annotations = annotated.getStringAnnotations("ENTITY_CLICK", 0, annotated.text.length)
        assertEquals(1, annotations.size)
        assertEquals("黄帝", annotations[0].item)
    }

    @Test
    fun testClassicalChineseOamTags() {
        val sample = "⟪人 黄帝⟫与⟪人 炎帝⟫⟪战 战⟫于⟪地 阪泉之野⟫。"
        val enabledTags = setOf(TagType.PERSON, TagType.PLACE, TagType.MILITARY_VERB)
        val annotated = OpusOneTagParser.parseTaggedText(
            taggedText = sample,
            enabledTags = enabledTags,
            isDark = false
        )

        assertEquals("黄帝与炎帝战于阪泉之野。", annotated.text)

        val annotations = annotated.getStringAnnotations("ENTITY_CLICK", 0, annotated.text.length)
        assertEquals(4, annotations.size)
        assertEquals("黄帝", annotations[0].item)
        assertEquals("炎帝", annotations[1].item)
        assertEquals("战", annotations[2].item)
        assertEquals("阪泉之野", annotations[3].item)

        val verbBgs = annotated.getStringAnnotations("VERB_BG", 0, annotated.text.length)
        assertEquals(1, verbBgs.size)
        assertEquals(TagType.MILITARY_VERB.name, verbBgs[0].item)

        assertEquals("黄帝与炎帝战于阪泉之野。", OpusOneTagParser.cleanDisplayText(sample))
    }

    @Test
    fun testTagTypeDualCodeResolution() {
        for (tag in TagType.entries) {
            assertEquals(tag, TagType.fromCode(tag.code))
            assertEquals(tag, TagType.fromCode(tag.zhCode))
        }
    }
}

