package dev.x.opusone.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.x.opusone.data.model.BookmarkItem

/**
 * 用户数据存储库（user_data.db），独立持久化存储用户书签与阅读进度数据。
 */
class UserDataHelper private constructor(private val context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "user_data.db"
        private const val DB_VERSION = 1

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: UserDataHelper? = null

        fun getInstance(context: Context): UserDataHelper {
            return instance ?: synchronized(this) {
                instance ?: UserDataHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                chapter_id INTEGER NOT NULL,
                chapter_title TEXT NOT NULL,
                pn_index TEXT NOT NULL,
                content_snippet TEXT NOT NULL,
                note_comment TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 目前仅 v1，未来版本在此做 ALTER 迁移
    }

    /**
     * 一次性迁移：若旧 opusone.db 存在 bookmarks 表且本库为空，则整表搬入。
     * 必须在 IO 线程调用（由 Repository 的 suspend 方法保证）。
     *
     * @return true 表示迁移成功或无需迁移；false 表示迁移失败（旧库书签仍在，允许下次重试）。
     */
    fun migrateFromLegacyIfNeeded(): Boolean {
        val legacyDbFile = context.getDatabasePath("opusone.db")
        if (!legacyDbFile.exists()) return true // 无旧库，无需迁移

        val localCount = writableDatabase.rawQuery("SELECT COUNT(*) FROM bookmarks", null).use {
            it.moveToFirst(); it.getInt(0)
        }
        if (localCount > 0) return true // 已有数据，跳过迁移，避免重复插入

        try {
            SQLiteDatabase.openDatabase(
                legacyDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { legacy ->
                // 旧库可能根本没有这张表（更早期版本）
                val tableExists = legacy.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='bookmarks'", null
                ).use { it.moveToFirst() }
                if (!tableExists) return true

                legacy.rawQuery(
                    "SELECT id, chapter_id, chapter_title, pn_index, content_snippet, note_comment, created_at FROM bookmarks ORDER BY id ASC",
                    null
                ).use { cursor ->
                    val db = writableDatabase
                    db.beginTransaction()
                    try {
                        while (cursor.moveToNext()) {
                            val values = ContentValues().apply {
                                put("chapter_id", cursor.getInt(1))
                                put("chapter_title", cursor.getString(2) ?: "")
                                put("pn_index", cursor.getString(3) ?: "")
                                put("content_snippet", cursor.getString(4) ?: "")
                                put("note_comment", cursor.getString(5) ?: "")
                                put("created_at", cursor.getLong(6))
                            }
                            db.insert("bookmarks", null, values)
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
            return true
        } catch (e: Exception) {
            // 迁移失败不致命：旧库书签仍在，下次启动可重试
            return false
        }
    }

    fun getBookmarks(): List<BookmarkItem> {
        val list = mutableListOf<BookmarkItem>()
        writableDatabase.rawQuery(
            "SELECT id, chapter_id, chapter_title, pn_index, content_snippet, note_comment, created_at FROM bookmarks ORDER BY created_at DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    BookmarkItem(
                        id = cursor.getInt(0),
                        chapterId = cursor.getInt(1),
                        chapterTitle = cursor.getString(2) ?: "",
                        pnIndex = cursor.getString(3) ?: "",
                        contentSnippet = cursor.getString(4) ?: "",
                        noteComment = cursor.getString(5) ?: "",
                        createdAt = cursor.getLong(6)
                    )
                )
            }
        }
        return list
    }

    fun addBookmark(
        chapterId: Int,
        chapterTitle: String,
        pnIndex: String,
        contentSnippet: String,
        noteComment: String = ""
    ): Long {
        val values = ContentValues().apply {
            put("chapter_id", chapterId)
            put("chapter_title", chapterTitle)
            put("pn_index", pnIndex)
            put("content_snippet", contentSnippet)
            put("note_comment", noteComment)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("bookmarks", null, values)
    }

    fun deleteBookmark(id: Int): Int {
        return writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    fun isChapterBookmarked(chapterId: Int): Boolean {
        writableDatabase.rawQuery(
            "SELECT 1 FROM bookmarks WHERE chapter_id = ? LIMIT 1",
            arrayOf(chapterId.toString())
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun getBookmarkForChapter(chapterId: Int): BookmarkItem? {
        writableDatabase.rawQuery(
            "SELECT id, chapter_id, chapter_title, pn_index, content_snippet, note_comment, created_at FROM bookmarks WHERE chapter_id = ? ORDER BY created_at DESC LIMIT 1",
            arrayOf(chapterId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return BookmarkItem(
                    id = cursor.getInt(0),
                    chapterId = cursor.getInt(1),
                    chapterTitle = cursor.getString(2) ?: "",
                    pnIndex = cursor.getString(3) ?: "",
                    contentSnippet = cursor.getString(4) ?: "",
                    noteComment = cursor.getString(5) ?: "",
                    createdAt = cursor.getLong(6)
                )
            }
        }
        return null
    }

    fun deleteBookmarksForChapter(chapterId: Int): Int {
        return writableDatabase.delete("bookmarks", "chapter_id = ?", arrayOf(chapterId.toString()))
    }
}
