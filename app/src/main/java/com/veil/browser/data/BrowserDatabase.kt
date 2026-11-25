package com.veil.browser.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folderPath: String,
    val createdAt: Long
)

@Fts4(contentEntity = BookmarkEntity::class)
@Entity(tableName = "bookmarks_fts")
data class BookmarkFtsEntity(
    val title: String,
    val url: String,
    val folderPath: String
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long,
    val visitCount: Int
)

@Entity(tableName = "download_chunks", primaryKeys = ["downloadId", "chunkIndex"])
data class DownloadChunkEntity(
    val downloadId: String,
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long
)

@Dao
interface BookmarkDao {
    // BM-2: Sub-15 ms full-text omnibox autocomplete query
    @Query(
        """
        SELECT bookmarks.* FROM bookmarks
        JOIN bookmarks_fts ON bookmarks.id = bookmarks_fts.rowid
        WHERE bookmarks_fts MATCH :ftsQuery
        LIMIT 8
        """
    )
    suspend fun searchAutocomplete(ftsQuery: String): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)
}

@Dao
interface DownloadChunkDao {
    @Query("SELECT * FROM download_chunks WHERE downloadId = :downloadId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDownload(downloadId: String): List<DownloadChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(chunks: List<DownloadChunkEntity>)

    @Query("UPDATE download_chunks SET downloadedBytes = :downloadedBytes WHERE downloadId = :downloadId AND chunkIndex = :chunkIndex")
    suspend fun updateProgress(downloadId: String, chunkIndex: Int, downloadedBytes: Long)
}

@Database(
    entities = [BookmarkEntity::class, BookmarkFtsEntity::class, HistoryEntity::class, DownloadChunkEntity::class],
    version = 1
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadChunkDao(): DownloadChunkDao
}
