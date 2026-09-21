package com.kamisakyy.nanajoxkmama.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    /* playlists */
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun playlist(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun playlistTracks(id: String): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(p: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(items: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearPlaylistTracks(id: String)

    /* history */
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 60")
    fun history(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(h: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    /* downloads */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun downloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun downloadsNow(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(d: DownloadEntity)

    @Query("DELETE FROM downloads WHERE `key` = :key")
    suspend fun deleteDownload(key: String)

    @Query("DELETE FROM downloads")
    suspend fun clearDownloads()
}
