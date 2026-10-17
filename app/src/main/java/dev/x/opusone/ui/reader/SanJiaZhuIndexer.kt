package dev.x.opusone.ui.reader

import dev.x.opusone.data.model.SanJiaZhuNote
import dev.x.opusone.data.model.Section
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.util.toScript

/**
 * 把三家注锚点映射到阅读器实际渲染的段落。
 *
 * 注疏只应挂载在可见的正文段落或表格上，绝不能挂载在标题（Heading）上；
 * 篇章标题级注疏（H1_0 / H1_*，如篇题总序或长序）不应向后滚动污染下文的正文或子标题。
 */
internal object SanJiaZhuIndexer {

    private val INVALID_ANCHORS = setOf("者也", "之子", "者，", "者", "也", "之")
    private val TRIM_CHARS = charArrayOf(
        '。', '，', '、', '；', '：', '！', '？', '」', '』', '》', '）',
        '〉', '〈', '「', '『', '《', '（', '【', '】', ' ', '　', '"', '\'', '”', '“'
    )

    fun index(
        allSections: List<Section>,
        visibleSections: List<Section>,
        sanjiazhu: List<SanJiaZhuNote>
    ): Map<Int, List<SanJiaZhuNote>> {
        // 注疏只能挂在可见的正文段落或表格上，不能挂在标题（heading）上
        val targetableSections = visibleSections.filter { !it.sectionType.startsWith("heading") }
        val targetableByPn = targetableSections.associateBy { it.pnIndex }

        // nextTargetable[i] = 原始段落 i 自身（若为可见正文）或其后的第一个可见正文段落
        val nextTargetable = arrayOfNulls<Section>(allSections.size)
        var pending: Section? = null
        for (i in allSections.indices.reversed()) {
            val sec = allSections[i]
            if (targetableByPn.containsKey(sec.pnIndex)) pending = sec
            nextTargetable[i] = pending
        }
        val orderByPn = allSections.withIndex().associate { (i, s) -> s.pnIndex to i }

        val map = HashMap<Int, MutableList<SanJiaZhuNote>>(targetableSections.size)
        for (note in sanjiazhu) {
            val cleanAnchor = note.anchorText.trim(*TRIM_CHARS)
            val cleanSimp = cleanAnchor.toScript(ChineseScriptMode.SIMPLIFIED)
            val hasValidAnchor =
                (cleanSimp.length >= 2 && cleanSimp !in INVALID_ANCHORS) || note.sentenceId.isNotBlank()
            val hasContent = note.jijie.isNotBlank() || note.suoyin.isNotBlank() ||
                note.zhengyi.isNotBlank() || note.otherNotes.isNotBlank()
            if (!hasValidAnchor || !hasContent) continue

            // 篇章标题级注疏（H1_0 / H1_*）为全书或卷篇总序，不应向后滚动污染正文
            if (note.sentenceId == "H1_0" || note.sentenceId.startsWith("H1_")) continue

            val target = if (note.sentenceId.isNotBlank()) {
                targetableByPn[note.sentenceId]
                    ?: orderByPn[note.sentenceId]?.let { nextTargetable[it] }
            } else {
                targetableSections.firstOrNull { cleanSimp in it.plainText }
                    ?: allSections.indexOfFirst { cleanSimp in it.plainText }
                        .takeIf { it >= 0 }
                        ?.let { idx ->
                            val matchedSec = allSections[idx]
                            // 若锚点文本仅落在篇章标题（H1）中，同样不向后滚动
                            if (matchedSec.sectionType == "heading1" || matchedSec.pnIndex.startsWith("H1_")) {
                                null
                            } else {
                                nextTargetable[idx]
                            }
                        }
            } ?: continue

            val notes = map.getOrPut(target.id) { mutableListOf() }
            if (notes.none { it.id == note.id }) notes.add(note)
        }
        return map
    }
}
