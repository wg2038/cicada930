package dev.x.opusone.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.x.opusone.data.model.TagType
import dev.x.opusone.theme.*
import dev.x.opusone.util.toScript

/**
 * OAM（OpusOne Annotation Markup v1.0）解析器。
 * 语法权威定义见 docs/ANNOTATION_SPEC.md：⟪CODE 正文⟫ / ⟪CODE 显示词|索引键⟫
 */
object OpusOneTagParser {

    private val TAG_PATTERN = Regex("⟪([A-Za-z]{2}|[\\u4e00-\\u9fa5])[\\s\\u3000]+([^⟪⟫]+?)⟫")

    /**
     * 已知 OAM 类型码（与 [TagType] 码表同源），用于清理失配标签残留的孤立类型码。
     * 只在「已知码 + 紧跟汉字」时清理，避免误删正文里真实的两字母拉丁缩写。
     */
    private val KNOWN_TYPE_CODE_REGEX =
        Regex("\\b(?:" + TagType.entries.joinToString("|") { it.code } + ")(?=[\\u4e00-\\u9fff])")

    private val REGEX_MARKDOWN_HEADINGS = Regex("(?m)^#{1,6}\\s*")
    private val REGEX_PN_BRACKETS = Regex("(?m)^\\[\\d+(\\.\\d+)*\\]\\s*")
    private val REGEX_ALPHANUM_BRACKETS = Regex("(?m)^\\[[0-9a-zA-Z_.]+\\]\\s*")
    private val REGEX_H_TAGS = Regex("(?m)(?i)^h\\d+[_\\s]?\\d*\\s*")
    /** 表格首列行标正则，用于非表格场景剥除格式前缀 */
    private val REGEX_TABLE_ROW_LABEL = Regex("(?m)^[ \\t]*[a-z]\\d+\\][ \\t]*")
    private val REGEX_CALLOUTS = Regex("(?m)^:::\\s*")
    private val REGEX_BLOCKQUOTES = Regex("(?m)^>\\s*")
    private val REGEX_ORPHAN_SURROGATES = Regex("[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF])|(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF]")
    private val REGEX_ORPHAN_TYPE_CODE_SPACE = Regex("\\b[A-Z]{2}(?![A-Za-z0-9])[\\s\\u3000]+")

    fun parseTaggedText(
        taggedText: String,
        enabledTags: Set<TagType>,
        isDark: Boolean,
        syntaxHighlightEnabled: Boolean = true,
        scriptMode: ChineseScriptMode = ChineseScriptMode.SIMPLIFIED,
        onEntityClick: ((String) -> Unit)? = null
    ): AnnotatedString {
        if (taggedText.isBlank()) return AnnotatedString("")

        val normalizedText = taggedText
            .replace(REGEX_MARKDOWN_HEADINGS, "")
            .replace(REGEX_PN_BRACKETS, "")
            .replace(REGEX_ALPHANUM_BRACKETS, "")
            .replace(REGEX_H_TAGS, "")
            .replace(REGEX_CALLOUTS, "")
            .replace(REGEX_BLOCKQUOTES, "")

        return buildAnnotatedString {
            var lastIndex = 0
            val matches = TAG_PATTERN.findAll(normalizedText)

            for (match in matches) {
                if (match.range.first > lastIndex) {
                    val rawPrefix = normalizedText.substring(lastIndex, match.range.first)
                    append(cleanDelimiters(rawPrefix).toScript(scriptMode))
                }

                val typeCode = match.groupValues[1]
                val rawBody = match.groupValues[2]
                val tagType = TagType.fromCode(typeCode) ?: TagType.TEXT

                val parts = rawBody.split("|")
                val displayText = cleanDelimiters(parts[0]).toScript(scriptMode)
                val entityKey = if (parts.size > 1) parts[1].trim() else parts[0].trim()

                val isEnabled = syntaxHighlightEnabled && enabledTags.contains(tagType)

                if (isEnabled && displayText.isNotBlank()) {
                    val start = length
                    append(displayText)
                    val end = length

                    addStringAnnotation(
                        tag = "ENTITY_CLICK",
                        annotation = entityKey,
                        start = start,
                        end = end
                    )

                    if (onEntityClick != null) {
                        addLink(
                            clickable = LinkAnnotation.Clickable(
                                tag = entityKey,
                                styles = TextLinkStyles(),
                                linkInteractionListener = { link ->
                                    val key = (link as? LinkAnnotation.Clickable)?.tag ?: entityKey
                                    onEntityClick(key)
                                }
                            ),
                            start = start,
                            end = end
                        )
                    }

                    if (isVerbTag(tagType)) {
                        addStringAnnotation(
                            tag = "VERB_BG",
                            annotation = tagType.name,
                            start = start,
                            end = end
                        )
                    }

                    val style = getTagStyle(tagType, isDark)
                    addStyle(style = style, start = start, end = end)
                } else {
                    val start = length
                    append(displayText)
                    val end = length

                    if (displayText.isNotBlank()) {
                        addStringAnnotation(
                            tag = "ENTITY_CLICK",
                            annotation = entityKey,
                            start = start,
                            end = end
                        )

                        if (onEntityClick != null) {
                            addLink(
                                clickable = LinkAnnotation.Clickable(
                                tag = entityKey,
                                styles = TextLinkStyles(),
                                linkInteractionListener = { link ->
                                    val key = (link as? LinkAnnotation.Clickable)?.tag ?: entityKey
                                    onEntityClick(key)
                                }
                            ),
                            start = start,
                            end = end
                        )
                    }
                }
            }

            lastIndex = match.range.last + 1
        }

        if (lastIndex < normalizedText.length) {
            val remaining = normalizedText.substring(lastIndex)
            append(cleanDelimiters(remaining).toScript(scriptMode))
        }
    }
}

    // OAM 当前语法 + 旧版括号语法（后者仅作历史语料容错，规范上已废弃）
    private val OAM_TAG_REGEX = Regex("⟪(?:(?:[A-Za-z]{2}|[\\u4e00-\\u9fa5])[\\s\\u3000]?)?([^⟪⟫]+?)⟫")
    private val LEGACY_TAG_REGEX = Regex("[〖⟦〘](?:[@=;&•+_!$#~^%:◈◉○*?◆◇※■□▲▼]?)?([^〖⟦〘〗⟧〙]+?)[〗⟧〙]")
    private val LEADING_INDEX_REGEX = Regex("(?m)^\\s*(\\[\\d+(\\.\\d+)*\\]|\\[[0-9a-zA-Z_.]+\\]|h\\d+[_\\s]?\\d*|#{1,6}|:::|>)\\s*")
    private val TAG_SYMBOLS = setOf('@', '=', ';', '&', '•', '+', '_', '!', '$', '#', '~', '^', '%', ':', '◈', '◉', '○', '*', '?', '◆', '◇', '※', '■', '□', '▲', '▼')

    /**
     * 清除标注语法及段落索引标记，返回纯文本内容。
     */
    fun cleanDisplayText(text: String): String {
        val step1 = OAM_TAG_REGEX.replace(text) { match -> surfaceOf(match.groupValues[1]) }
            .let { t -> LEGACY_TAG_REGEX.replace(t) { match -> surfaceOf(match.groupValues[1]) } }
        val step2 = LEADING_INDEX_REGEX.replace(REGEX_TABLE_ROW_LABEL.replace(step1, ""), "")
        return cleanDelimiters(step2).trim()
    }

    private fun surfaceOf(body: String): String {
        val surface = body.split("|")[0].trim()
        var s = surface
        while (s.isNotEmpty() && s.first() in TAG_SYMBOLS) {
            s = s.substring(1)
        }
        return s
    }

    fun cleanDelimiters(text: String): String {
        return text
            .replace(REGEX_MARKDOWN_HEADINGS, "")
            .replace(REGEX_TABLE_ROW_LABEL, "")
            .replace(REGEX_PN_BRACKETS, "")
            .replace(REGEX_ALPHANUM_BRACKETS, "")
            .replace(REGEX_H_TAGS, "")
            .replace(":::", "")
            .replace(REGEX_BLOCKQUOTES, "")
            .replace("**", "")
            .replace("*", "")
            .replace("◆", "")
            .replace("◇", "")
            .replace("※", "")
            .replace("◈", "")
            .replace("◉", "")
            .replace("○", "")
            .replace("■", "")
            .replace("□", "")
            .replace("▲", "")
            .replace("▼", "")
            .replace("{", "")
            .replace("}", "")
            .replace("〖", "")
            .replace("〗", "")
            .replace("⟦", "")
            .replace("⟧", "")
            .replace("〘", "")
            .replace("〙", "")
            .replace("|", "")
            .replace("⟪", "")
            .replace("⟫", "")
            .replace(REGEX_ORPHAN_SURROGATES, "")
            .replace(REGEX_ORPHAN_TYPE_CODE_SPACE, "")
            .replace(KNOWN_TYPE_CODE_REGEX, "")
            .replace("\uFFFD", "")
    }

    /**
     * 按实体类型与明暗模式返回对应的 SpanStyle。
     *
     * 这些色彩由 theme/Color.kt 中的「语义数据色」集中提供，
     * 明暗两套值均已做过对比度校验，避免在深色底上出现暗色文字。
     */
    private fun getTagStyle(tagType: TagType, isDark: Boolean): SpanStyle {
        return if (!isDark) {
            when (tagType) {
                TagType.PERSON -> SpanStyle(
                    color = TagPersonColorLight,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
                TagType.PLACE -> SpanStyle(
                    color = TagPlaceColorLight,
                    textDecoration = TextDecoration.Underline
                )
                TagType.OFFICIAL -> SpanStyle(
                    color = TagOfficialColorLight,
                    fontWeight = FontWeight.SemiBold
                )
                TagType.IDENTITY -> SpanStyle(
                    color = TagIdentityColorLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.CLAN -> SpanStyle(
                    color = TagDynastyColorLight,
                    textDecoration = TextDecoration.Underline
                )
                TagType.ARTIFACT -> SpanStyle(
                    color = TagArtifactColorLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.BIOLOGY -> SpanStyle(
                    color = TagBiologyColorLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.CONCEPT -> SpanStyle(
                    color = TagConceptColorLight,
                    textDecoration = TextDecoration.Underline
                )
                TagType.ASTRONOMY -> SpanStyle(
                    color = TagAstronomyColorLight,
                    fontStyle = FontStyle.Italic
                )
                TagType.QUANTITY -> SpanStyle(
                    color = TagQuantityColorLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.CHENGYU -> SpanStyle(
                    color = TagIdiomColorLight,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
                TagType.MILITARY_VERB -> SpanStyle(
                    color = VerbMilitaryTextLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.PUNISH_VERB -> SpanStyle(
                    color = VerbPenaltyTextLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.POLITIC_VERB -> SpanStyle(
                    color = VerbPoliticalTextLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.ECONOMIC_VERB -> SpanStyle(
                    color = VerbEconomicTextLight,
                    fontWeight = FontWeight.Medium
                )
                TagType.TEXT -> SpanStyle()
            }
        } else {
            when (tagType) {
                TagType.PERSON -> SpanStyle(
                    color = TagPersonColorDark,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
                TagType.PLACE -> SpanStyle(
                    color = TagPlaceColorDark,
                    textDecoration = TextDecoration.Underline
                )
                TagType.OFFICIAL -> SpanStyle(
                    color = TagOfficialColorDark,
                    fontWeight = FontWeight.SemiBold
                )
                TagType.IDENTITY -> SpanStyle(
                    color = TagIdentityColorDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.CLAN -> SpanStyle(
                    color = TagDynastyColorDark,
                    textDecoration = TextDecoration.Underline
                )
                TagType.ARTIFACT -> SpanStyle(
                    color = TagArtifactColorDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.BIOLOGY -> SpanStyle(
                    color = TagBiologyColorDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.CONCEPT -> SpanStyle(
                    color = TagConceptColorDark,
                    textDecoration = TextDecoration.Underline
                )
                TagType.ASTRONOMY -> SpanStyle(
                    color = TagAstronomyColorDark,
                    fontStyle = FontStyle.Italic
                )
                TagType.QUANTITY -> SpanStyle(
                    color = TagQuantityColorDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.CHENGYU -> SpanStyle(
                    color = TagIdiomColorDark,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
                TagType.MILITARY_VERB -> SpanStyle(
                    color = VerbMilitaryTextDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.PUNISH_VERB -> SpanStyle(
                    color = VerbPenaltyTextDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.POLITIC_VERB -> SpanStyle(
                    color = VerbPoliticalTextDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.ECONOMIC_VERB -> SpanStyle(
                    color = VerbEconomicTextDark,
                    fontWeight = FontWeight.Medium
                )
                TagType.TEXT -> SpanStyle()
            }
        }
    }

    /**
     * 判断是否属于需要绘制紧凑独立矩形底色的动词分类。
     */
    fun isVerbTag(tagType: TagType): Boolean = when (tagType) {
        TagType.MILITARY_VERB,
        TagType.PUNISH_VERB,
        TagType.POLITIC_VERB,
        TagType.ECONOMIC_VERB -> true
        else -> false
    }

    /**
     * 获取动词分类高亮矩形的背景颜色（由 ReaderScreen 自定义绘制，避免随行距拉伸）。
     */
    fun getVerbBackgroundColor(tagType: TagType, isDark: Boolean): Color? = if (isDark) {
        when (tagType) {
            TagType.MILITARY_VERB -> VerbMilitaryBgDark
            TagType.PUNISH_VERB -> VerbPenaltyBgDark
            TagType.POLITIC_VERB -> VerbPoliticalBgDark
            TagType.ECONOMIC_VERB -> VerbEconomicBgDark
            else -> null
        }
    } else {
        when (tagType) {
            TagType.MILITARY_VERB -> VerbMilitaryBgLight
            TagType.PUNISH_VERB -> VerbPenaltyBgLight
            TagType.POLITIC_VERB -> VerbPoliticalBgLight
            TagType.ECONOMIC_VERB -> VerbEconomicBgLight
            else -> null
        }
    }
}
