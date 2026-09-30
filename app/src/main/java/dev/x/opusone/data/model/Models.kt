package dev.x.opusone.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Chapter(
    val id: Int,
    val category: String,
    val title: String,
    val summary: String,
    val wordCount: Int,
    val sectionCount: Int = 0
)

@Immutable
data class Section(
    val id: Int,
    val chapterId: Int,
    val pnIndex: String,
    val sectionType: String,
    val headingLevel: Int,
    val headingText: String,
    val taggedContent: String,
    val plainText: String,
    val translation: String? = null,
    val orderInChapter: Int
)

@Immutable
data class EntityItem(
    val id: String,
    val label: String,
    val type: String,
    val typeNameZh: String,
    val aliases: List<String>,
    val description: String,
    val tags: List<String>,
    val occurrencesCount: Int
)

@Immutable
data class EntityOccurrence(
    val id: Int,
    val entityId: String,
    val chapterId: Int,
    val chapterTitle: String,
    val sectionPn: String
)

@Immutable
data class ChengyuItem(
    val id: Int,
    val word: String,
    val chapterId: Int,
    val chapterTitle: String,
    val pn: String,
    val quote: String,
    val meaning: String,
    val context: String
)

@Immutable
data class WarItem(
    val id: Int,
    val warId: String,
    val name: String,
    val chapterNum: String,
    val chapterTitle: String,
    val description: String,
    val fullDescription: String
)

@Immutable
data class TaiShiGongYueItem(
    val id: Int,
    val chapterId: Int,
    val chapterTitle: String,
    val content: String,
    val plainContent: String,
    val targetPn: String = ""
)

@Immutable
data class SanJiaZhuNote(
    val id: Int,
    val chapterId: Int,
    val noteId: String,
    val anchorText: String,
    val beforeContext: String,
    val afterContext: String,
    val jijie: String,
    val suoyin: String,
    val zhengyi: String,
    val otherNotes: String,
    val sentenceId: String
)

@Immutable
data class BookmarkItem(
    val id: Int = 0,
    val chapterId: Int,
    val chapterTitle: String,
    val pnIndex: String,
    val contentSnippet: String,
    val noteComment: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class TagType(
    val key: String,
    val code: String,
    val labelZh: String,
    val defaultEnabled: Boolean = true,
    val zhCode: String = ""
) {
    PERSON("person", "PE", "人物", true, "人"),
    PLACE("place", "PL", "地名", true, "地"),
    OFFICIAL("official", "OF", "官职", true, "官"),
    BIOLOGY("biology", "BI", "生物", false, "生"),
    CLAN("tribe", "CL", "邦国氏族", true, "族"),
    ARTIFACT("artifact", "AR", "器物名物", false, "器"),
    IDENTITY("identity", "ID", "身份群体", false, "群"),
    CONCEPT("concept", "CO", "思想天命", false, "思"),
    ASTRONOMY("astronomy", "AS", "天文历法", false, "历"),
    QUANTITY("quantity", "QU", "数量度量", false, "数"),
    CHENGYU("chengyu", "CY", "成语典故", true, "语"),
    MILITARY_VERB("military_verb", "MV", "军事战伐", true, "战"),
    PUNISH_VERB("punish_verb", "XV", "刑罚惩处", false, "刑"),
    POLITIC_VERB("politic_verb", "PV", "政治邦交", false, "政"),
    ECONOMIC_VERB("economic_verb", "EV", "经济财赋", false, "赋"),
    TEXT("text", "TX", "泛指标注", false, "录");

    companion object {
        /** 兼容古典单字文法与历史双字母代码，未登记代码一律按 TX/录 泛指降级 */
        fun fromCode(code: String): TagType? = entries.find {
            it.zhCode == code || it.code.equals(code, ignoreCase = true)
        }
    }
}

@Immutable
sealed interface SearchResultItem {
    @Immutable
    data class SectionMatch(
        val sectionId: Int,
        val chapterId: Int,
        val chapterTitle: String,
        val pnIndex: String,
        val snippet: String
    ) : SearchResultItem

    @Immutable
    data class EntityMatch(
        val entityId: String,
        val label: String,
        val typeNameZh: String,
        val description: String,
        val occurrencesCount: Int,
        // 实体别名列表
        val aliases: List<String> = emptyList()
    ) : SearchResultItem

    @Immutable
    data class ChengyuMatch(
        val id: Int,
        val word: String,
        val meaning: String,
        val quote: String,
        val chapterTitle: String
    ) : SearchResultItem

    @Immutable
    data class WarMatch(
        val id: Int,
        val name: String,
        val chapterTitle: String,
        val description: String
    ) : SearchResultItem
}

@Immutable
data class SearchResultSet(
    val query: String,
    val sections: List<SearchResultItem.SectionMatch> = emptyList(),
    val entities: List<SearchResultItem.EntityMatch> = emptyList(),
    val chengyu: List<SearchResultItem.ChengyuMatch> = emptyList(),
    val wars: List<SearchResultItem.WarMatch> = emptyList()
) {
    val totalCount: Int get() = sections.size + entities.size + chengyu.size + wars.size
    val isEmpty: Boolean get() = totalCount == 0
}
