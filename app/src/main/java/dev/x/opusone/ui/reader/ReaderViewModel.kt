package dev.x.opusone.ui.reader

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.x.opusone.data.OpusOneRepository
import dev.x.opusone.data.model.*
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.util.guardCoroutine
import dev.x.opusone.util.toScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.jvm.Volatile

data class ReaderUiState(
    val chapterId: Int = 1,
    val chapter: Chapter? = null,
    val sections: List<Section> = emptyList(),
    val sectionNotesMap: Map<Int, List<SanJiaZhuNote>> = emptyMap(),
    val isLoading: Boolean = true,
    val enabledTags: Set<TagType> = TagType.entries.filter { it.defaultEnabled }.toSet(),
    val selectedEntity: EntityItem? = null,
    val isEntityLoading: Boolean = false,
    val showEntitySheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showTranslation: Boolean = false,
    val showSanJiaInline: Boolean = true,
    val syntaxHighlightEnabled: Boolean = true,
    val mergeParagraphs: Boolean = false,
    val isBookmarked: Boolean = false,
    val bookmarkedPnIndex: String? = null,
    val quoteDialogData: Pair<String, String>? = null,
    val fontSize: Float = 19f,
    val lineSpacingMultiplier: Float = 1.9f
)

/**
 * 古籍阅读器状态管理与业务调度 ViewModel。
 * 负责分章节异步加载、正文过滤合并、注疏段落预索引及书签进度管理。
 */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OpusOneRepository(application)

    private var loadJob: Job? = null
    private var bookmarkJob: Job? = null
    private var entityJob: Job? = null

    @Volatile
    private var currentLoadChapterId: Int = -1

    private val readerPrefs: SharedPreferences by lazy {
        getApplication<Application>().getSharedPreferences("opusone_reader_prefs", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(
        ReaderUiState(
            fontSize = readerPrefs.getFloat("font_size", 19f),
            lineSpacingMultiplier = readerPrefs.getFloat("line_spacing", 1.9f)
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun loadChapter(chapterId: Int, targetSectionPn: String? = null) {
        loadJob?.cancel()
        val requestChapterId = chapterId
        currentLoadChapterId = chapterId
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    chapterId = chapterId
                )
            }

            val (ch, secs, notesMap) = withContext(Dispatchers.IO) {
                val chapter = repository.getChapterById(chapterId)
                val rawSections = repository.getSectionsByChapter(chapterId)
                val sections = rawSections.filter { sec ->
                    if (sec.sectionType == "heading1" || sec.pnIndex.startsWith("H1_")) return@filter false
                    if (sec.sectionType == "divider") return@filter false
                    if (sec.sectionType == "paragraph" && sec.plainText.isBlank()) return@filter false
                    if (sec.sectionType == "paragraph" &&
                        !sec.taggedContent.contains('\n') &&
                        sec.taggedContent.trim().startsWith(":::")
                    ) return@filter false
                    if (sec.sectionType.startsWith("heading") &&
                        sec.headingText.replace(Regex("^#{1,6}\\s*"), "").trim() == "表"
                    ) return@filter false
                    true
                }
                val sanjiazhu = repository.getSanJiaZhuNotesByChapter(chapterId)
                val rawNotesMap = indexSanJiaZhuNotes(sections, sanjiazhu)
                val (finalSections, finalMap) = if (_uiState.value.mergeParagraphs) {
                    mergeSectionsWithNotes(sections, rawNotesMap)
                } else {
                    sections to rawNotesMap
                }

                data class LoadedChapter(
                    val chapter: Chapter?,
                    val sections: List<Section>,
                    val notesMap: Map<Int, List<SanJiaZhuNote>>
                )
                LoadedChapter(chapter, finalSections, finalMap)
            }

            if (requestChapterId != currentLoadChapterId) return@launch
            val existingBm = repository.getBookmarkForChapter(chapterId)
            val isBm = existingBm != null
            val bmPn = existingBm?.pnIndex

            _uiState.update {
                it.copy(
                    chapter = ch,
                    sections = secs,
                    sectionNotesMap = notesMap,
                    isBookmarked = isBm,
                    bookmarkedPnIndex = bmPn,
                    isLoading = false
                )
            }
        }
    }

    /**
     * 在后台 IO 线程预处理注疏与正文段落对应关系。
     *
     * 避免在 UI 虚拟列表滚动中执行 O(N*M) 遍历与高频字符串清洗，
     * 优先采用 sentenceId 精确映射段落，缺失时回退到有效锚点文本匹配。
     */
    private fun indexSanJiaZhuNotes(
        sections: List<Section>,
        sanjiazhu: List<SanJiaZhuNote>
    ): Map<Int, List<SanJiaZhuNote>> {
        val invalidAnchors = setOf("者也", "之子", "者，", "者", "也", "之")
        val cleanedNotes = sanjiazhu.map { note ->
            val cleanAnchor = note.anchorText.trim(
                '。', '，', '、', '；', '：', '！', '？', '」', '』', '》', '）',
                '〉', '〈', '「', '『', '《', '（', '【', '】', ' ', '　', '"', '\'', '”', '“'
            )
            val cleanSimp = cleanAnchor.toScript(ChineseScriptMode.SIMPLIFIED)
            val hasValidAnchor = (cleanSimp.length >= 2 && cleanSimp !in invalidAnchors) || note.sentenceId.isNotBlank()
            val hasContent = note.jijie.isNotBlank() || note.suoyin.isNotBlank() || note.zhengyi.isNotBlank() || note.otherNotes.isNotBlank()
            Triple(note, cleanSimp, hasValidAnchor && hasContent)
        }
        val map = HashMap<Int, List<SanJiaZhuNote>>(sections.size)
        for (sec in sections) {
            val matched = cleanedNotes.filter { (note, cleanSimp, isValid) ->
                if (!isValid) return@filter false
                if (note.sentenceId.isNotBlank()) {
                    note.sentenceId == sec.pnIndex
                } else {
                    cleanSimp in sec.plainText
                }
            }.map { it.first }
            if (matched.isNotEmpty()) {
                map[sec.id] = matched
            }
        }
        return map
    }

    /**
     * 将连续的普通正文段落（[Section.sectionType] == "paragraph"）合并为更少的大段落，
     * 同时将各小段所绑定的三家注聚合到合并后的首段。
     */
    private fun mergeSectionsWithNotes(
        sections: List<Section>,
        notesMap: Map<Int, List<SanJiaZhuNote>>
    ): Pair<List<Section>, Map<Int, List<SanJiaZhuNote>>> {
        if (sections.isEmpty()) return sections to notesMap
        val resultSections = mutableListOf<Section>()
        val resultMap = HashMap<Int, List<SanJiaZhuNote>>()
        var i = 0
        while (i < sections.size) {
            val cur = sections[i]
            if (cur.sectionType != "paragraph") {
                resultSections.add(cur)
                val notes = notesMap[cur.id]
                if (!notes.isNullOrEmpty()) {
                    resultMap[cur.id] = notes
                }
                i++
                continue
            }
            // 收集一段连续的正文段落
            val group = mutableListOf(cur)
            var j = i + 1
            while (j < sections.size && sections[j].sectionType == "paragraph") {
                group.add(sections[j])
                j++
            }
            if (group.size == 1) {
                resultSections.add(cur)
                val notes = notesMap[cur.id]
                if (!notes.isNullOrEmpty()) {
                    resultMap[cur.id] = notes
                }
            } else {
                val first = group.first()
                val plainText = group.joinToString("\n") { it.plainText }
                val taggedContent = group.joinToString("\n") { it.taggedContent }
                val translation = group
                    .mapNotNull { it.translation?.takeIf { t -> t.isNotBlank() } }
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                resultSections.add(
                    first.copy(
                        plainText = plainText,
                        taggedContent = taggedContent,
                        translation = translation,
                        headingText = "",
                        headingLevel = 0
                    )
                )
                val combinedNotes = group.flatMap { notesMap[it.id].orEmpty() }
                if (combinedNotes.isNotEmpty()) {
                    resultMap[first.id] = combinedNotes
                }
            }
            i = j
        }
        return resultSections to resultMap
    }

    fun toggleTranslation() {
        _uiState.update { it.copy(showTranslation = !it.showTranslation) }
    }

    fun toggleSanJiaInline() {
        _uiState.update { it.copy(showSanJiaInline = !it.showSanJiaInline) }
    }

    fun toggleSyntaxHighlight() {
        _uiState.update { it.copy(syntaxHighlightEnabled = !it.syntaxHighlightEnabled) }
    }

    fun toggleMergeParagraphs() {
        val newVal = !_uiState.value.mergeParagraphs
        _uiState.update { it.copy(mergeParagraphs = newVal) }
        loadChapter(_uiState.value.chapterId)
    }

    fun toggleBookmark(currentPnIndex: String = "") {
        val chap = _uiState.value.chapter ?: return
        val currentBookmarked = _uiState.value.isBookmarked
        val savedPn = _uiState.value.bookmarkedPnIndex
        val chapterId = chap.id

        if (bookmarkJob?.isActive == true) return
        bookmarkJob = viewModelScope.launch {
            guardCoroutine("ReaderViewModel", "toggleBookmark") {
                if (currentBookmarked && savedPn == currentPnIndex) {
                    repository.deleteBookmarksForChapter(chapterId)
                    _uiState.update { it.copy(isBookmarked = false, bookmarkedPnIndex = null) }
                    Toast.makeText(getApplication(), "已取消书签", Toast.LENGTH_SHORT).show()
                } else {
                    val snippet = if (currentPnIndex.isNotBlank()) {
                        _uiState.value.sections.find { it.pnIndex == currentPnIndex }?.plainText?.let {
                            OpusOneTagParser.cleanDisplayText(it).take(100)
                        } ?: chap.title
                    } else {
                        _uiState.value.sections.firstOrNull()?.plainText?.let {
                            OpusOneTagParser.cleanDisplayText(it).take(100)
                        } ?: chap.title
                    }
                    repository.deleteBookmarksForChapter(chapterId)
                    repository.addBookmark(
                        chapterId = chapterId,
                        chapterTitle = chap.title,
                        pnIndex = currentPnIndex,
                        contentSnippet = snippet,
                        noteComment = ""
                    )
                    _uiState.update { it.copy(isBookmarked = true, bookmarkedPnIndex = currentPnIndex) }
                    val toastMsg = if (currentBookmarked) "已更新书签进度" else "已加入书签"
                    Toast.makeText(getApplication(), toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun onEntityClicked(entityId: String) {
        entityJob?.cancel()
        entityJob = viewModelScope.launch {
            _uiState.update { it.copy(isEntityLoading = true, showEntitySheet = true) }
            guardCoroutine("ReaderViewModel", "onEntityClicked", onError = {
                _uiState.update { it.copy(isEntityLoading = false, showEntitySheet = false) }
            }) {
                val entity = withContext(Dispatchers.IO) {
                    repository.getEntityById(entityId)
                }
                _uiState.update {
                    it.copy(
                        selectedEntity = entity,
                        isEntityLoading = false
                    )
                }
            }
        }
    }

    fun dismissEntitySheet() {
        entityJob?.cancel()
        _uiState.update { it.copy(showEntitySheet = false, selectedEntity = null) }
    }

    fun toggleTag(tag: TagType) {
        _uiState.update { state ->
            val updated = state.enabledTags.toMutableSet()
            if (updated.contains(tag)) {
                updated.remove(tag)
            } else {
                updated.add(tag)
            }
            state.copy(enabledTags = updated)
        }
    }

    fun setShowSettingsSheet(show: Boolean) {
        _uiState.update { it.copy(showSettingsSheet = show) }
    }

    fun showQuoteDialog(pnIndex: String, quoteText: String) {
        _uiState.update { it.copy(quoteDialogData = Pair(pnIndex, quoteText)) }
    }

    fun dismissQuoteDialog() {
        _uiState.update { it.copy(quoteDialogData = null) }
    }

    fun setFontSize(size: Float) {
        readerPrefs.edit().putFloat("font_size", size).apply()
        _uiState.update { it.copy(fontSize = size) }
    }

    fun setLineSpacing(spacing: Float) {
        readerPrefs.edit().putFloat("line_spacing", spacing).apply()
        _uiState.update { it.copy(lineSpacingMultiplier = spacing) }
    }
}
