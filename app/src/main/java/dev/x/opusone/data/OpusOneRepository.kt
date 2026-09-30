package dev.x.opusone.data

import android.content.Context
import dev.x.opusone.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpusOneRepository(context: Context) {
    private val dbHelper = OpusOneDatabaseHelper.getInstance(context.applicationContext)

    suspend fun getAllChapters(): List<Chapter> = withContext(Dispatchers.IO) {
        dbHelper.getAllChapters()
    }

    suspend fun getChapterById(id: Int): Chapter? = withContext(Dispatchers.IO) {
        dbHelper.getChapterById(id)
    }

    suspend fun getSectionsByChapter(chapterId: Int): List<Section> = withContext(Dispatchers.IO) {
        dbHelper.getSectionsByChapter(chapterId)
    }

    suspend fun getEntityById(entityId: String): EntityItem? = withContext(Dispatchers.IO) {
        dbHelper.getEntityById(entityId)
    }

    suspend fun getEntitiesByType(type: String, limit: Int = 100, offset: Int = 0): List<EntityItem> = withContext(Dispatchers.IO) {
        dbHelper.getEntitiesByType(type, limit, offset)
    }

    suspend fun getEntityOccurrences(entityId: String): List<EntityOccurrence> = withContext(Dispatchers.IO) {
        dbHelper.getEntityOccurrences(entityId)
    }

    suspend fun getAllChengyu(): List<ChengyuItem> = withContext(Dispatchers.IO) {
        dbHelper.getAllChengyu()
    }

    suspend fun getAllWars(): List<WarItem> = withContext(Dispatchers.IO) {
        dbHelper.getAllWars()
    }

    suspend fun getAllTaiShiGongYue(): List<TaiShiGongYueItem> = withContext(Dispatchers.IO) {
        dbHelper.getAllTaiShiGongYue()
    }

    suspend fun getSanJiaZhuNotesByChapter(chapterId: Int): List<SanJiaZhuNote> = withContext(Dispatchers.IO) {
        dbHelper.getSanJiaZhuNotesByChapter(chapterId)
    }

    suspend fun getBookmarks(): List<BookmarkItem> = withContext(Dispatchers.IO) {
        dbHelper.getBookmarks()
    }

    suspend fun addBookmark(chapterId: Int, chapterTitle: String, pnIndex: String, contentSnippet: String, noteComment: String = ""): Long = withContext(Dispatchers.IO) {
        dbHelper.addBookmark(chapterId, chapterTitle, pnIndex, contentSnippet, noteComment)
    }

    suspend fun deleteBookmark(id: Int): Int = withContext(Dispatchers.IO) {
        dbHelper.deleteBookmark(id)
    }

    suspend fun isChapterBookmarked(chapterId: Int): Boolean = withContext(Dispatchers.IO) {
        dbHelper.isChapterBookmarked(chapterId)
    }

    suspend fun getBookmarkForChapter(chapterId: Int): BookmarkItem? = withContext(Dispatchers.IO) {
        dbHelper.getBookmarkForChapter(chapterId)
    }

    suspend fun deleteBookmarksForChapter(chapterId: Int): Int = withContext(Dispatchers.IO) {
        dbHelper.deleteBookmarksForChapter(chapterId)
    }

    suspend fun searchAll(query: String, limitPerCategory: Int = 30): SearchResultSet = withContext(Dispatchers.IO) {
        // 不在此处做简繁归一：检索层会把查询展开成「原 / 简 / 繁」三形态分别匹配，
        // 预先归一只会丢掉用户输入本身的形态，反而造成漏召回。
        dbHelper.searchAll(query.trim(), limitPerCategory)
    }

    suspend fun searchEntities(query: String, limit: Int = 50): List<SearchResultItem.EntityMatch> = withContext(Dispatchers.IO) {
        dbHelper.searchEntities(query.trim(), limit)
    }
}
