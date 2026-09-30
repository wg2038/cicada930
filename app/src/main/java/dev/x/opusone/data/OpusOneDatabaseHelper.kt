package dev.x.opusone.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import dev.x.opusone.data.model.*
import dev.x.opusone.util.ChineseConverter
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

class OpusOneDatabaseHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "opusone.db"

        /**
         * assets 内嵌库的**产物版本**（不是 schema 版本 —— 本库从不迁移，只整份替换）。
         *
         * 必须与建库脚本 `etl/build_from_corpus.py` 的 `PRAGMA user_version` 一致。
         * 语义约定：**只要重新构建了 assets/opusone.db，就必须把这个数字 +1**，
         * 否则已装过 App 的设备 prefs 里记着旧版本号、判定「无需替换」，
         * 会继续用旧库 —— 测试时会表现为「修好的表还是坏的」，且不报任何错。
         */
        private const val DB_VERSION = 13
        private const val PREF_KEY_DB_VERSION = "opusone_db_version"
        private const val TAG = "OpusOneDatabaseHelper"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: OpusOneDatabaseHelper? = null

        fun getInstance(context: Context): OpusOneDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: OpusOneDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }

        /**
         * 「十表」行标 `r12]` / `a3]` / `b7]`。
         *
         * 它是表格最左「序号列」的真实内容，**数据层必须保留**（删掉等于整表左移一列）；
         * 检索摘要里它是给读者看的纯噪声，所以在生成 snippet 时按行首剥掉。
         * 表格本身的渲染由 `ui/reader/OpusOneTableParser` 负责，会把它转成冻结的 `[12]` 列。
         */
        private val ROW_LABEL_REGEX = Regex("(?m)^[ \\t]*[a-z]\\d+\\][ \\t]*")
    }

    /**
     * 拷库状态标记：ensureDatabaseCopied 只在真正执行一次。
     * 注意拷库不放在 getInstance / 构造路径上（那会跑在主线程），
     * 而是延迟到首次实际查询——所有查询都经由 Repository 的 IO 调度器，
     * 因此 55MB 的首次拷贝永远不会阻塞 UI。
     */
    @Volatile
    private var copyChecked = false

    private fun ensureDatabaseCopied() {
        if (copyChecked) return
        synchronized(this) {
            if (copyChecked) return
            val dbFile = context.getDatabasePath(DB_NAME)
            val prefs = context.getSharedPreferences("opusone_db_prefs", Context.MODE_PRIVATE)
            val currentInstalledVersion = prefs.getInt(PREF_KEY_DB_VERSION, 0)

            if (!dbFile.exists() || currentInstalledVersion < DB_VERSION) {
                // 版本升级需要替换文件，先释放缓存的只读句柄，否则句柄占用会让替换失败
                releaseReadonlyDb()
                dbFile.parentFile?.mkdirs()
                if (dbFile.exists()) {
                    // 升级前先从旧库迁移用户书签到 user_data.db，避免覆盖导致用户数据丢失
                    val migrated = try {
                        UserDataHelper.getInstance(context).migrateFromLegacyIfNeeded()
                    } catch (e: Exception) {
                        Log.w(TAG, "legacy bookmark migration failed before re-copy", e)
                        false
                    }
                    if (!migrated) {
                        Log.w(TAG, "skip db re-copy: legacy bookmark migration failed, keeping old db for retry")
                        return
                    }
                }

                val tempFile = File(dbFile.parentFile, "$DB_NAME.tmp")
                if (tempFile.exists()) tempFile.delete()

                val copySuccess = try {
                    context.assets.open(DB_NAME).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } >= 0) {
                                output.write(buffer, 0, bytes)
                            }
                            output.flush()
                        }
                    }
                    tempFile.exists() && tempFile.length() > 0
                } catch (e: Exception) {
                    Log.e(TAG, "failed to copy db from assets to temp file", e)
                    if (tempFile.exists()) tempFile.delete()
                    false
                }

                if (!copySuccess) {
                    Log.e(TAG, "db copy aborted due to asset copy failure, keeping existing db if any")
                    return
                }

                // 确认新文件写入成功后，再清理旧数据库及 WAL/shm 临时文件
                if (dbFile.exists()) {
                    deleteDatabaseFiles(dbFile)
                }

                val replaced = if (tempFile.renameTo(dbFile)) {
                    true
                } else {
                    try {
                        tempFile.copyTo(dbFile, overwrite = true)
                        tempFile.delete()
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "failed to move temp db to destination dbFile", e)
                        false
                    }
                }

                if (replaced) {
                    prefs.edit().putInt(PREF_KEY_DB_VERSION, DB_VERSION).apply()
                    cachedChapters = null
                } else {
                    Log.e(TAG, "failed to replace dbFile with new database")
                    return
                }
            }
            copyChecked = true
        }
    }

    private fun deleteDatabaseFiles(dbFile: File) {
        dbFile.delete()
        File(dbFile.path + ".tmp").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
    }

    /**
     * 配置静态典籍库底层 SQLite 参数。
     * 开启 64MB mmap 内存映射零拷贝、16MB 页面缓存，并在内存中处理临时表排序。
     */
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        applyPragma(db, "PRAGMA mmap_size = 67108864")
        applyPragma(db, "PRAGMA cache_size = -16384")
        applyPragma(db, "PRAGMA temp_store = MEMORY")
        applyPragma(db, "PRAGMA synchronous = NORMAL")
    }

    /**
     * 执行单条 PRAGMA，并保证它真的生效。
     *
     * **不能用 `execSQL`**：`PRAGMA mmap_size = N` 是**会返回结果行**的语句
     * （查询形式与赋值形式都返回一行），而 `SQLiteDatabase.execSQL` 内部走
     * `SQLiteStatement.executeUpdateDelete()` → `executeForChangedRowCount`，
     * 碰到结果行就抛：
     *
     *     SQLiteException: Queries can be performed using SQLiteDatabase
     *     query or rawQuery methods only.
     *
     * 旧实现把 4 条调优 PRAGMA 塞进同一个 try，第 1 条（mmap_size）就抛，
     * 于是 `cache_size` / `temp_store` / `synchronous` **全部静默失效**，
     * 只在 logcat 留一条 W 级告警 —— 检索性能白丢一个量级却毫无征兆。
     * 现在改为逐条 `rawQuery` + 独立 try：任何一条失败都只影响它自己。
     */
    private fun applyPragma(db: SQLiteDatabase, sql: String) {
        try {
            // 用 rawQuery 消费结果行；对不返回行的 PRAGMA，游标为空即可，无副作用
            db.rawQuery(sql, null).use { it.moveToFirst() }
        } catch (e: Exception) {
            Log.w(TAG, "PRAGMA failed: $sql", e)
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // 预置数据库，由 assets 复制初始化
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // 预置静态典籍库升级由 ensureDatabaseCopied 统一按版本校验复制
    }

    /**
     * 后台异步预热：在 App 启动生命周期尽早调用，提前完成 Assets 解压校验与连接初始化。
     */
    fun prewarm() {
        try {
            ensureDatabaseCopied()
            getAllChapters()
        } catch (e: Exception) {
            Log.w(TAG, "Database prewarm failed", e)
        }
    }

    @Volatile
    private var readonlyDb: SQLiteDatabase? = null

    /**
     * 打开语料库（**只读**）。
     *
     * 原先走 `SQLiteOpenHelper.getReadableDatabase()`，按 Android 文档其语义是
     * 「尽可能以可读写方式打开」—— 对一个静态语料库而言这没有任何好处，反而
     * ① 会引入 journal/WAL 旁路文件；② 让任何误写路径都能真正落盘。
     * 语料库是只读资产，改为显式 `OPEN_READONLY`，从连接层杜绝写入。
     *
     * 注意：`openDatabase` 不会触发 `SQLiteOpenHelper.onConfigure`，
     * 因此在此显式执行 PRAGMA 调优配置（如 mmap_size）。
     */
    private fun getReadableDb(): SQLiteDatabase {
        ensureDatabaseCopied()
        readonlyDb?.let { return it }
        synchronized(this) {
            readonlyDb?.let { return it }
            val path = context.getDatabasePath(DB_NAME).absolutePath
            val db = SQLiteDatabase.openDatabase(
                path,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            applyPragma(db, "PRAGMA mmap_size = 67108864")
            applyPragma(db, "PRAGMA cache_size = -16384")
            applyPragma(db, "PRAGMA temp_store = MEMORY")
            readonlyDb = db
            return db
        }
    }

    /** 释放只读句柄（仅在需要替换数据库文件时调用）。 */
    private fun releaseReadonlyDb() {
        synchronized(this) {
            try {
                readonlyDb?.close()
            } catch (e: Exception) {
                Log.w(TAG, "close readonly db failed", e)
            }
            readonlyDb = null
        }
    }

    @Volatile
    private var cachedChapters: List<Chapter>? = null

    fun getAllChapters(): List<Chapter> {
        cachedChapters?.let { return it }
        synchronized(this) {
            cachedChapters?.let { return it }
            val list = mutableListOf<Chapter>()
            val db = getReadableDb()
            db.rawQuery("SELECT id, category, title, summary, word_count, section_count FROM chapters ORDER BY id ASC", null).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        Chapter(
                            id = cursor.getInt(0),
                            category = cursor.getString(1) ?: "",
                            title = cursor.getString(2) ?: "",
                            summary = cursor.getString(3) ?: "",
                            wordCount = cursor.getInt(4),
                            sectionCount = cursor.getInt(5)
                        )
                    )
                }
            }
            cachedChapters = list
            return list
        }
    }

    fun getChapterById(id: Int): Chapter? {
        cachedChapters?.find { it.id == id }?.let { return it }
        val db = getReadableDb()
        db.rawQuery("SELECT id, category, title, summary, word_count, section_count FROM chapters WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToNext()) {
                return Chapter(
                    id = cursor.getInt(0),
                    category = cursor.getString(1) ?: "",
                    title = cursor.getString(2) ?: "",
                    summary = cursor.getString(3) ?: "",
                    wordCount = cursor.getInt(4),
                    sectionCount = cursor.getInt(5)
                )
            }
        }
        return null
    }

    fun getSectionsByChapter(chapterId: Int): List<Section> {
        val list = mutableListOf<Section>()
        val db = getReadableDb()
        db.rawQuery(
            "SELECT id, chapter_id, pn_index, section_type, heading_level, heading_text, tagged_content, plain_text, translation, order_in_chapter FROM sections WHERE chapter_id = ? ORDER BY order_in_chapter ASC",
            arrayOf(chapterId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    Section(
                        id = cursor.getInt(0),
                        chapterId = cursor.getInt(1),
                        pnIndex = cursor.getString(2) ?: "",
                        sectionType = cursor.getString(3) ?: "",
                        headingLevel = cursor.getInt(4),
                        headingText = cursor.getString(5) ?: "",
                        taggedContent = cursor.getString(6) ?: "",
                        plainText = cursor.getString(7) ?: "",
                        translation = cursor.getString(8),
                        orderInChapter = cursor.getInt(9)
                    )
                )
            }
        }
        return list
    }

    fun getEntityById(entityId: String): EntityItem? {
        return try {
            getEntityByIdInternal(entityId, depth = 0)
        } catch (e: Exception) {
            Log.w(TAG, "getEntityById failed: $entityId", e)
            null
        }
    }

    private fun getEntityByIdInternal(entityId: String, depth: Int): EntityItem? {
        if (depth > 3) return null
        val db = getReadableDb()
        var item: EntityItem? = null

        val exactAliasPattern = "%\"" + entityId.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "\"%"
        db.rawQuery(
            """
            SELECT id, label, type, type_name_zh, aliases, description, tags, occurrences_count
            FROM entities
            WHERE id = ? OR label = ? OR aliases LIKE ? ESCAPE '\' OR aliases LIKE ? ESCAPE '\'
            ORDER BY
                CASE
                    WHEN type = 'redirect' THEN 3
                    WHEN id = ? THEN 0
                    WHEN label = ? THEN 1
                    ELSE 2
                END,
                occurrences_count DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(entityId, entityId, exactAliasPattern, likeArg(entityId), entityId, entityId)
        ).use { cursor ->
            if (cursor.moveToNext()) {
                item = parseEntityItem(cursor)
            }
        }

        if (item != null) {
            val desc = item.description.trim()
            val isRedirect = item.type == "redirect" || desc.startsWith("REDIRECT ") || desc.startsWith("→ 参见 ")
            if (isRedirect) {
                val target = when {
                    desc.startsWith("REDIRECT ") -> desc.removePrefix("REDIRECT ").trim()
                    desc.startsWith("→ 参见 ") -> desc.removePrefix("→ 参见 ").trim().split(" ", "\n")[0]
                    else -> ""
                }
                if (target.isNotEmpty() && target != entityId) {
                    val resolved = getEntityByIdInternal(target, depth + 1)
                    if (resolved != null) {
                        return resolved
                    }
                }
            }
            if (item.type != "redirect") {
                return item
            }
        }

        if (depth > 0) return null

        return EntityItem(
            id = entityId,
            label = entityId,
            type = "custom",
            typeNameZh = "实体索引",
            aliases = emptyList(),
            description = "《史记》关键考据词条",
            tags = emptyList(),
            occurrencesCount = 1
        )
    }

    fun getEntitiesByType(type: String, limit: Int = 100, offset: Int = 0): List<EntityItem> {
        val list = mutableListOf<EntityItem>()
        val query = if (type.isBlank() || type == "all") {
            "SELECT id, label, type, type_name_zh, aliases, description, tags, occurrences_count FROM entities WHERE type != 'redirect' ORDER BY occurrences_count DESC LIMIT ? OFFSET ?"
        } else {
            "SELECT id, label, type, type_name_zh, aliases, description, tags, occurrences_count FROM entities WHERE type = ? AND type != 'redirect' ORDER BY occurrences_count DESC LIMIT ? OFFSET ?"
        }
        val args = if (type.isBlank() || type == "all") {
            arrayOf(limit.toString(), offset.toString())
        } else {
            arrayOf(type, limit.toString(), offset.toString())
        }
        try {
            val db = getReadableDb()
            db.rawQuery(query, args).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(parseEntityItem(cursor))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getEntitiesByType failed: $type", e)
        }
        return list
    }

    fun getEntityOccurrences(entityId: String): List<EntityOccurrence> {
        val list = mutableListOf<EntityOccurrence>()
        val db = getReadableDb()
        val canonicalId = getEntityById(entityId)?.id ?: entityId
        // 内连接 sections 确保出处段落有效，并通过 GROUP BY 去重
        db.rawQuery(
            """
            SELECT MIN(eo.id), eo.entity_id, eo.chapter_id,
                   COALESCE(c.title, '章节 ' || eo.chapter_id), eo.section_pn
            FROM entity_occurrences eo
            INNER JOIN sections s
                    ON s.chapter_id = eo.chapter_id AND s.pn_index = eo.section_pn
            LEFT JOIN chapters c ON eo.chapter_id = c.id
            WHERE eo.entity_id = ? OR eo.entity_id = ? OR eo.entity_id IN (SELECT id FROM entities WHERE label = ? OR label = ?)
            GROUP BY eo.chapter_id, eo.section_pn
            ORDER BY eo.chapter_id ASC, MIN(s.order_in_chapter) ASC
            """,
            arrayOf(entityId, canonicalId, entityId, canonicalId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    EntityOccurrence(
                        id = cursor.getInt(0),
                        entityId = cursor.getString(1) ?: "",
                        chapterId = cursor.getInt(2),
                        chapterTitle = cursor.getString(3) ?: "章节 ${cursor.getInt(2)}",
                        sectionPn = cursor.getString(4) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getAllChengyu(): List<ChengyuItem> {
        val list = mutableListOf<ChengyuItem>()
        val db = getReadableDb()
        db.rawQuery("SELECT id, word, chapter_id, chapter_title, pn, quote, meaning, context FROM chengyu ORDER BY id ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    ChengyuItem(
                        id = cursor.getInt(0),
                        word = cursor.getString(1) ?: "",
                        chapterId = cursor.getInt(2),
                        chapterTitle = cursor.getString(3) ?: "",
                        pn = cursor.getString(4) ?: "",
                        quote = cursor.getString(5) ?: "",
                        meaning = cursor.getString(6) ?: "",
                        context = cursor.getString(7) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getAllWars(): List<WarItem> {
        val list = mutableListOf<WarItem>()
        val db = getReadableDb()
        db.rawQuery("SELECT id, war_id, name, chapter_num, chapter_title, description, full_description FROM wars ORDER BY id ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    WarItem(
                        id = cursor.getInt(0),
                        warId = cursor.getString(1) ?: "",
                        name = cursor.getString(2) ?: "",
                        chapterNum = cursor.getString(3) ?: "",
                        chapterTitle = cursor.getString(4) ?: "",
                        description = cursor.getString(5) ?: "",
                        fullDescription = cursor.getString(6) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getAllTaiShiGongYue(): List<TaiShiGongYueItem> {
        val list = mutableListOf<TaiShiGongYueItem>()
        val db = getReadableDb()
        val sql = """
            SELECT t.id, t.chapter_id, t.chapter_title, t.content, t.plain_content,
                (
                    SELECT s.pn_index FROM sections s
                    WHERE s.chapter_id = t.chapter_id
                      AND (
                          (s.section_type = 'heading2' AND s.heading_text LIKE '%太史公曰%')
                          OR (s.plain_text LIKE '太史公曰%' OR s.plain_text LIKE '太史公曰：%' OR s.tagged_content LIKE '%太史公曰%')
                      )
                    ORDER BY
                      CASE
                        WHEN s.section_type = 'heading2' AND s.heading_text = '太史公曰' THEN 1
                        WHEN s.section_type = 'heading2' AND s.heading_text LIKE '%太史公曰%' THEN 2
                        WHEN s.plain_text LIKE '太史公曰%' THEN 3
                        ELSE 4
                      END ASC,
                      s.order_in_chapter DESC
                    LIMIT 1
                ) AS target_pn
            FROM taishigongyue t
            ORDER BY t.chapter_id ASC
        """.trimIndent()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val content = cursor.getString(3) ?: ""
                var targetPn = cursor.getString(5) ?: ""
                if (targetPn.isBlank()) {
                    targetPn = Regex("^\\[([^\\]]+)\\]").find(content)?.groupValues?.get(1) ?: ""
                }
                list.add(
                    TaiShiGongYueItem(
                        id = cursor.getInt(0),
                        chapterId = cursor.getInt(1),
                        chapterTitle = cursor.getString(2) ?: "",
                        content = content,
                        plainContent = cursor.getString(4) ?: "",
                        targetPn = targetPn
                    )
                )
            }
        }
        return list
    }

    fun getSanJiaZhuNotesByChapter(chapterId: Int): List<SanJiaZhuNote> {
        val list = mutableListOf<SanJiaZhuNote>()
        val db = getReadableDb()
        db.rawQuery(
            "SELECT id, chapter_id, note_id, anchor_text, before_context, after_context, jijie, suoyin, zhengyi, other_notes, sentence_id FROM sanjiazhu_notes WHERE chapter_id = ? ORDER BY id ASC",
            arrayOf(chapterId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    SanJiaZhuNote(
                        id = cursor.getInt(0),
                        chapterId = cursor.getInt(1),
                        noteId = cursor.getString(2) ?: "",
                        anchorText = cursor.getString(3) ?: "",
                        beforeContext = cursor.getString(4) ?: "",
                        afterContext = cursor.getString(5) ?: "",
                        jijie = cursor.getString(6) ?: "",
                        suoyin = cursor.getString(7) ?: "",
                        zhengyi = cursor.getString(8) ?: "",
                        otherNotes = cursor.getString(9) ?: "",
                        sentenceId = cursor.getString(10) ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getBookmarks(): List<BookmarkItem> = userDataHelper().getBookmarks()

    fun addBookmark(chapterId: Int, chapterTitle: String, pnIndex: String, contentSnippet: String, noteComment: String = ""): Long =
        userDataHelper().addBookmark(chapterId, chapterTitle, pnIndex, contentSnippet, noteComment)

    fun deleteBookmark(id: Int): Int = userDataHelper().deleteBookmark(id)

    fun isChapterBookmarked(chapterId: Int): Boolean = userDataHelper().isChapterBookmarked(chapterId)

    fun getBookmarkForChapter(chapterId: Int): BookmarkItem? = userDataHelper().getBookmarkForChapter(chapterId)

    fun deleteBookmarksForChapter(chapterId: Int): Int = userDataHelper().deleteBookmarksForChapter(chapterId)

    /** 用户数据独立库（书签），首次访问时执行旧库一次性迁移。 */
    @Volatile
    private var userDataMigrated = false

    private fun userDataHelper(): UserDataHelper {
        val helper = UserDataHelper.getInstance(context)
        if (!userDataMigrated) {
            synchronized(this) {
                if (!userDataMigrated) {
                    // 仅迁移成功才置位已迁移标记，失败保留重试机会
                    val ok = helper.migrateFromLegacyIfNeeded()
                    if (ok) userDataMigrated = true
                }
            }
        }
        return helper
    }

    /**
     * 全库模糊检索入口，按分类返回匹配结果。
     */
    fun searchAll(query: String, limitPerCategory: Int = 30): SearchResultSet = withReadDb { db ->
        if (query.isBlank()) {
            SearchResultSet(query = query)
        } else {
            searchAllLike(db, query.trim(), limitPerCategory)
        }
    }

    /**
     * 实体专用轻量检索（用于知识索引等场景，避免触发数十万字正文全表扫描）。
     */
    fun searchEntities(query: String, limit: Int = 50): List<SearchResultItem.EntityMatch> = withReadDb { db ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val forms = queryForms(query.trim())
            searchEntitiesLike(db, forms, limit.toString())
        }
    }

    private inline fun <T> withReadDb(block: (SQLiteDatabase) -> T): T {
        val db = getReadableDb()
        return block(db)
    }

    private fun likeArg(field: String): String =
        "%" + field.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private val LIKE_ESCAPE = " ESCAPE '\\' "

    /**
     * 常见简繁一对多异体字映射表，用于扩展搜索召回。
     */
    private val AMBIGUOUS_SEARCH_PAIRS: Map<Char, Char> = mapOf(
        '后' to '後',
        '干' to '幹',
    )

    /**
     * 把查询串展开成去重后的形态集合：原串 / 简体 / 繁体，再各补一层歧义字桥接。
     *
     * 语料本身简繁混排，用户输入又可能是任一形态，三种形态都必须查一次，
     * 否则必然漏召回。转换器未就绪时退化为原串，不会抛异常。
     */
    private fun queryForms(query: String): List<String> {
        val forms = LinkedHashSet<String>(6)
        val simp = runCatching { ChineseConverter.toSimplified(query) }.getOrDefault(query)
        val trad = runCatching { ChineseConverter.toTraditional(query) }.getOrDefault(query)
        for (base in listOf(query, simp, trad)) {
            if (base.isEmpty()) continue
            forms.add(base)
            val bridged = buildString(base.length) {
                for (ch in base) append(AMBIGUOUS_SEARCH_PAIRS[ch] ?: ch)
            }
            if (bridged.isNotEmpty()) forms.add(bridged)
        }
        return forms.toList()
    }

    /**
     * 生成 `(col1 LIKE ? OR col2 LIKE ? ...)` 片段，并把参数按序追加到 [args]。
     * 多种查询形态 × 多个字段做笛卡尔展开。
     */
    private fun likeOrClause(columns: List<String>, forms: List<String>, args: MutableList<String>): String {
        val sb = StringBuilder("(")
        var first = true
        for (form in forms) {
            for (col in columns) {
                if (!first) sb.append(" OR ")
                sb.append(col).append(" LIKE ?").append(LIKE_ESCAPE)
                args.add(likeArg(form))
                first = false
            }
        }
        sb.append(")")
        return sb.toString()
    }

    /**
     * 摘要截取：在任一查询形态命中的位置附近取上下文。
     * 多形态可能命中不同位置，取最靠前的那一处。
     */
    private fun excerpt(text: String, needles: List<String>, radius: Int = 42): String {
        var idx = -1
        var hitLen = 0
        for (needle in needles) {
            if (needle.isEmpty()) continue
            val i = text.indexOf(needle, ignoreCase = true)
            if (i >= 0 && (idx < 0 || i < idx)) {
                idx = i
                hitLen = needle.length
            }
        }
        if (idx < 0) return text.take(radius * 2)
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + hitLen + radius).coerceAtMost(text.length)
        return (if (start > 0) "…" else "") +
            text.substring(start, end) +
            (if (end < text.length) "…" else "")
    }

    // 各分类的 LIKE 查询
    private fun searchSectionsLike(db: SQLiteDatabase, forms: List<String>, limit: String): List<SearchResultItem.SectionMatch> {
        val list = mutableListOf<SearchResultItem.SectionMatch>()
        val args = mutableListOf<String>()
        val where = likeOrClause(listOf("s.plain_text"), forms, args)
        args.add(limit)
        try {
            db.rawQuery(
                """
                SELECT s.id, s.chapter_id, c.title, s.pn_index, s.plain_text
                FROM sections s
                JOIN chapters c ON s.chapter_id = c.id
                WHERE $where
                LIMIT ?
                """,
                args.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(4) ?: ""
                    list.add(
                        SearchResultItem.SectionMatch(
                            sectionId = cursor.getInt(0),
                            chapterId = cursor.getInt(1),
                            chapterTitle = cursor.getString(2) ?: "",
                            pnIndex = cursor.getString(3) ?: "",
                            snippet = excerpt(text, forms)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sections LIKE search failed", e)
        }
        return list
    }

    private fun searchEntitiesLike(db: SQLiteDatabase, forms: List<String>, limit: String): List<SearchResultItem.EntityMatch> {
        val list = mutableListOf<SearchResultItem.EntityMatch>()
        val args = mutableListOf<String>()
        val where = likeOrClause(listOf("label", "aliases", "description"), forms, args)
        args.add(limit)
        try {
            db.rawQuery(
                """
                SELECT id, label, type_name_zh, description, occurrences_count, aliases
                FROM entities
                WHERE $where AND type != 'redirect'
                ORDER BY occurrences_count DESC
                LIMIT ?
                """,
                args.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        SearchResultItem.EntityMatch(
                            entityId = cursor.getString(0) ?: "",
                            label = cursor.getString(1) ?: "",
                            typeNameZh = cursor.getString(2) ?: "",
                            description = cursor.getString(3) ?: "",
                            occurrencesCount = cursor.getInt(4),
                            aliases = parseJsonArray(cursor.getString(5))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "entities LIKE search failed", e)
        }
        return list
    }

    private fun searchChengyuLike(db: SQLiteDatabase, forms: List<String>, limit: String): List<SearchResultItem.ChengyuMatch> {
        val list = mutableListOf<SearchResultItem.ChengyuMatch>()
        val args = mutableListOf<String>()
        val where = likeOrClause(listOf("word", "meaning", "quote"), forms, args)
        args.add(limit)
        try {
            db.rawQuery(
                """
                SELECT id, word, meaning, quote, chapter_title
                FROM chengyu
                WHERE $where
                LIMIT ?
                """,
                args.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        SearchResultItem.ChengyuMatch(
                            id = cursor.getInt(0),
                            word = cursor.getString(1) ?: "",
                            meaning = cursor.getString(2) ?: "",
                            quote = cursor.getString(3) ?: "",
                            chapterTitle = cursor.getString(4) ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "chengyu LIKE search failed", e)
        }
        return list
    }

    private fun searchWarsLike(db: SQLiteDatabase, forms: List<String>, limit: String): List<SearchResultItem.WarMatch> {
        val list = mutableListOf<SearchResultItem.WarMatch>()
        val args = mutableListOf<String>()
        val where = likeOrClause(listOf("name", "description"), forms, args)
        args.add(limit)
        try {
            db.rawQuery(
                """
                SELECT id, name, chapter_title, description
                FROM wars
                WHERE $where
                LIMIT ?
                """,
                args.toTypedArray()
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    list.add(
                        SearchResultItem.WarMatch(
                            id = cursor.getInt(0),
                            name = cursor.getString(1) ?: "",
                            chapterTitle = cursor.getString(2) ?: "",
                            description = cursor.getString(3) ?: ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "wars LIKE search failed", e)
        }
        return list
    }

    private fun searchAllLike(db: SQLiteDatabase, query: String, limitPerCategory: Int): SearchResultSet {
        val limit = limitPerCategory.toString()
        val forms = queryForms(query)

        val sections = searchSectionsLike(db, forms, limit)
        val entities = searchEntitiesLike(db, forms, limit)
        val chengyu = searchChengyuLike(db, forms, limit)
        val wars = searchWarsLike(db, forms, limit)

        return SearchResultSet(
            query = query,
            sections = sections,
            entities = entities,
            chengyu = chengyu,
            wars = wars
        )
    }

    private fun parseEntityItem(cursor: Cursor): EntityItem {
        return EntityItem(
            // EntityItem 的这些字段在 Models.kt 中均为非空 String，NULL 会直接抛 NPE
            id = cursor.getString(0) ?: "",
            label = cursor.getString(1) ?: "",
            type = cursor.getString(2) ?: "",
            typeNameZh = cursor.getString(3) ?: "",
            aliases = parseJsonArray(cursor.getString(4)),
            description = cursor.getString(5) ?: "",
            tags = parseJsonArray(cursor.getString(6)),
            occurrencesCount = cursor.getInt(7)
        )
    }

    /**
     * 解析 JSON 字符串数组。
     *
     * 逐元素容错：语料历史遗留里 aliases 混入过非字符串元素（如 `["五帝本纪", 1]`，
     * 其中数字是卷次），而 org.json 的 `getString(i)` 遇到 Integer 会抛 JSONException——
     * 旧实现把**整条列表**退化成 emptyList()，导致 94 条章节实体的「又称」整行消失。
     * 现在改为跳过非法元素、保留其余合法元素。
     */
    private fun parseJsonArray(jsonStr: String?): List<String> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val value = arr.opt(i)
                if (value is String && value.isNotBlank()) list.add(value)
            }
            list
        } catch (e: Exception) {
            // JSON 本身非法时仍整体降级为空列表（保留原有兜底语义）
            emptyList()
        }
    }
}
