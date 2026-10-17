package dev.x.opusone.ui.reader

import dev.x.opusone.data.model.SanJiaZhuNote
import dev.x.opusone.data.model.Section
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.util.toScript

/**
 * 把三家注锚点映射到阅读器实际渲染的段落。
 *
 * 阅读器会过滤掉 heading1、分隔线、空段和 `:::` 段落，而注疏的
 * `sentence_id` 可能正指向这些段落。旧实现只在过滤后的列表里匹配，
 * 导致这些注疏从此不再显示；这里把锚点向后滚动到最近的可见段。
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
        val visibleByPn = visibleSections.associateBy { it.pnIndex }

        // nextVisible[i] = 原始段落 i 自身或其后的第一个可见段落
        val nextVisible = arrayOfNulls<Section>(allSections.size)
        var pending: Section? = null
        for (i in allSections.indices.reversed()) {
            val sec = allSections[i]
            if (visibleByPn.containsKey(sec.pnIndex)) pending = sec
            nextVisible[i] = pending
        }
        val orderByPn = allSections.withIndex().associate { (i, s) -> s.pnIndex to i }

        val map = HashMap<Int, MutableList<SanJiaZhuNote>>(visibleSections.size)
        for (note in sanjiazhu) {
            val cleanAnchor = note.anchorText.trim(*TRIM_CHARS)
            val cleanSimp = cleanAnchor.toScript(ChineseScriptMode.SIMPLIFIED)
            val hasValidAnchor =
                (cleanSimp.length >= 2 && cleanSimp !in INVALID_ANCHORS) || note.sentenceId.isNotBlank()
            val hasContent = note.jijie.isNotBlank() || note.suoyin.isNotBlank() ||
                note.zhengyi.isNotBlank() || note.otherNotes.isNotBlank()
            if (!hasValidAnchor || !hasContent) continue

            val target = if (note.sentenceId.isNotBlank()) {
                visibleByPn[note.sentenceId]
                    ?: orderByPn[note.sentenceId]?.let { nextVisible[it] }
            } else {
                visibleSections.firstOrNull { cleanSimp in it.plainText }
                    ?: allSections.indexOfFirst { cleanSimp in it.plainText }
                        .takeIf { it >= 0 }
                        ?.let { nextVisible[it] }
            } ?: continue

            val notes = map.getOrPut(target.id) { mutableListOf() }
            if (notes.none { it.id == note.id }) notes.add(note)
        }
        return map
    }
}
