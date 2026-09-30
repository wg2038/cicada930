package dev.x.opusone.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import dev.x.opusone.theme.ChineseScriptMode
import dev.x.opusone.theme.LocalChineseScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/** 词典加载处于 LOADING 状态超过该时长即视为卡死，允许重新发起加载。 */
private const val LOAD_STUCK_TIMEOUT_MS = 30_000L

object ChineseConverter {

    @Volatile
    private var s2tCharMap: Map<Char, Char>

    @Volatile
    private var t2sCharMap: Map<Char, Char>

    @Volatile
    private var s2tTrie: TrieNode = TrieNode()

    @Volatile
    private var t2sTrie: TrieNode = TrieNode()

    @Volatile
    private var isInitialized = false

    private val _isReady = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isReady: kotlinx.coroutines.flow.StateFlow<Boolean> = _isReady

    // 加载状态机：防止并发重复加载，支持异常状态自愈
    private val STATE_UNINITIALIZED = 0
    private val STATE_LOADING = 1
    private val STATE_LOADED = 2
    private val STATE_FAILED = 3
    @Volatile
    private var initState = STATE_UNINITIALIZED

    /** 本次 LOADING 的开始时刻：用于识别「卡在 LOADING」的单向死锁并允许重试。 */
    @Volatile
    private var loadStartedAt = 0L

    // Built-in core classical dictionary fallback (available synchronously at static init)
    init {
        val coreS2T = HashMap<Char, Char>(100)
        val coreT2S = HashMap<Char, Char>(100)
        val corePairs = listOf(
            "纪" to "紀", "传" to "傳", "书" to "書", "赞" to "贊",
            "齐" to "齊", "赵" to "趙", "魏" to "魏", "韩" to "韓", "燕" to "燕", "楚" to "楚", "秦" to "秦",
            "汉" to "漢", "隐" to "隱", "义" to "義", "解" to "解", "注" to "注",
            "迁" to "遷", "马" to "馬", "诸" to "諸", "侯" to "侯", "国" to "國", "帝" to "帝",
            "战" to "戰", "争" to "爭", "师" to "師", "将" to "將", "相" to "相",
            "语" to "語", "话" to "話", "译" to "譯", "文" to "文", "释" to "釋", "评" to "評",
            "经" to "經", "典" to "典", "录" to "錄", "藏" to "藏", "阁" to "閣",
            "图" to "圖", "谱" to "譜", "铁" to "鐵", "览" to "覽", "历" to "歷",
            "时" to "時", "间" to "間", "考" to "考", "据" to "據", "识" to "識",
            "专" to "專", "项" to "項", "索" to "索", "引" to "引", "签" to "籤",
            "黄" to "黃", "简" to "簡", "韵" to "韻", "读" to "讀", "号" to "號",
            "两" to "兩", "应" to "應", "为" to "為", "与" to "與", "发" to "發",
            "复" to "復", "并" to "並", "准" to "準", "庄" to "莊", "襄" to "襄",
            "问" to "問", "答" to "答", "题" to "題", "岁" to "歲", "年" to "年",
            "东" to "東", "西" to "西", "南" to "南", "北" to "北", "统" to "統",
            "权" to "權", "变" to "變", "归" to "歸", "尽" to "盡", "实" to "實",
            "选" to "選", "认" to "認", "标" to "標", "体" to "體", "点" to "點", "线" to "線"
        )
        for ((s, t) in corePairs) {
            if (s.isNotEmpty() && t.isNotEmpty()) {
                coreS2T[s[0]] = t[0]
                coreT2S[t[0]] = s[0]
            }
        }
        s2tCharMap = coreS2T
        t2sCharMap = coreT2S
    }

    /** 结构化协程初始化方法，支持生命周期感知与异步加载完成通知。 */
    suspend fun initialize(context: Context) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (initState == STATE_LOADED) {
            _isReady.value = true
            return@withContext
        }
        synchronized(this@ChineseConverter) {
            if (initState == STATE_LOADED) {
                _isReady.value = true
                return@withContext
            }
            if (initState == STATE_LOADING && System.currentTimeMillis() - loadStartedAt < LOAD_STUCK_TIMEOUT_MS) {
                return@withContext
            }
            initState = STATE_LOADING
            loadStartedAt = System.currentTimeMillis()
        }

        val appContext = context.applicationContext
        try {
            val newS2tCharMap = HashMap<Char, Char>(6000).apply { putAll(s2tCharMap) }
            val newT2sCharMap = HashMap<Char, Char>(6000).apply { putAll(t2sCharMap) }
            val newS2tTrie = TrieNode()
            val newT2sTrie = TrieNode()

            appContext.assets.open("s2t_chars.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split("\t")
                        if (parts.size >= 2 && parts[0].length == 1 && parts[1].length == 1) {
                            val s = parts[0][0]
                            val t = parts[1][0]
                            if (!s.isSurrogate() && !t.isSurrogate()) {
                                newS2tCharMap[s] = t
                                newT2sCharMap[t] = s
                            }
                        }
                    }
                }
            }

            appContext.assets.open("s2t_phrases.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split("\t")
                        if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                            newS2tTrie.insert(parts[0], parts[1])
                            newT2sTrie.insert(parts[1], parts[0])
                            if (parts[0].length == parts[1].length) {
                                for (k in parts[0].indices) {
                                    val simp = parts[0][k]
                                    val trad = parts[1][k]
                                    if (simp != trad && !simp.isSurrogate() && !trad.isSurrogate() &&
                                        !newT2sCharMap.containsKey(trad)
                                    ) {
                                        newT2sCharMap[trad] = simp
                                    }
                                }
                            }
                        }
                    }
                }
            }

            s2tCharMap = newS2tCharMap
            t2sCharMap = newT2sCharMap
            s2tTrie = newS2tTrie
            t2sTrie = newT2sTrie
            isInitialized = true
            initState = STATE_LOADED
            _isReady.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            initState = STATE_FAILED
        }
    }

    private val fallbackScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    @Synchronized
    fun init(context: Context) {
        if (initState == STATE_LOADED) return
        fallbackScope.launch {
            initialize(context)
        }
    }

    fun convert(text: String, mode: ChineseScriptMode): String {
        if (text.isEmpty()) return text
        return when (mode) {
            ChineseScriptMode.SIMPLIFIED -> toSimplified(text)
            ChineseScriptMode.TRADITIONAL -> toTraditional(text)
        }
    }

    fun toTraditional(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        var i = 0
        val len = text.length
        val currentTrie = s2tTrie
        val currentS2T = s2tCharMap

        while (i < len) {
            // Check longest phrase match in Trie if available
            val match = if (isInitialized) currentTrie.searchLongest(text, i) else null
            if (match != null) {
                sb.append(match.replacement)
                i += match.length
            } else {
                val c = text[i]
                if (c.isHighSurrogate() && i + 1 < len && text[i + 1].isLowSurrogate()) {
                    sb.append(c)
                    sb.append(text[i + 1])
                    i += 2
                } else if (c.isSurrogate()) {
                    i++ // Drop orphan surrogate
                } else {
                    val mapped = currentS2T[c] ?: c
                    if (!mapped.isSurrogate()) {
                        sb.append(mapped)
                    } else {
                        sb.append(c)
                    }
                    i++
                }
            }
        }
        return sb.toString()
    }

    fun toSimplified(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        val currentT2S = t2sCharMap
        val currentT2STrie = t2sTrie
        var i = 0
        val len = text.length
        while (i < len) {
            // 优先走独立 t2s 词组 Trie（繁→简）最长匹配
            val match = if (isInitialized) currentT2STrie.searchLongest(text, i) else null
            if (match != null) {
                sb.append(match.replacement)
                i += match.length
            } else {
                val c = text[i]
                if (c.isHighSurrogate() && i + 1 < len && text[i + 1].isLowSurrogate()) {
                    sb.append(c)
                    sb.append(text[i + 1])
                    i += 2
                } else if (c.isSurrogate()) {
                    i++ // Drop orphan surrogate
                } else {
                    val mapped = currentT2S[c] ?: c
                    if (!mapped.isSurrogate()) {
                        sb.append(mapped)
                    } else {
                        sb.append(c)
                    }
                    i++
                }
            }
        }
        return sb.toString()
    }

    private class MatchResult(val length: Int, val replacement: String)

    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var replacement: String? = null

        fun insert(phrase: String, target: String) {
            var curr = this
            for (c in phrase) {
                curr = curr.children.getOrPut(c) { TrieNode() }
            }
            curr.replacement = target
        }

        fun searchLongest(text: String, startIndex: Int): MatchResult? {
            var curr = this
            var lastMatch: MatchResult? = null
            var len = 0

            for (i in startIndex until text.length) {
                val c = text[i]
                curr = curr.children[c] ?: break
                len++
                if (curr.replacement != null) {
                    lastMatch = MatchResult(len, curr.replacement!!)
                }
            }
            return lastMatch
        }
    }
}

/**
 * Convenient extension functions for UI composables and strings
 */
fun String.toScript(mode: ChineseScriptMode): String {
    return ChineseConverter.convert(this, mode)
}

/**
 * 无障碍文案（contentDescription / label）的简繁跟随。
 * 在 Composable 作用域内直接读取当前文字版本设置，
 * 保证 TalkBack 读出的语言与界面显示一致。
 */
@Composable
fun cd(text: String): String = text.toScript(LocalChineseScript.current)
