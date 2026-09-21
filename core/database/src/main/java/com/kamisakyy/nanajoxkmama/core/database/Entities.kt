package com.kamisakyy.nanajoxkmama.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "position"])
data class PlaylistTrackEntity(
    val playlistId: String,
    val position: Int,
    val trackJson: String,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val trackId: String,
    val playedAt: Long,
    val trackJson: String,
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val key: String,
    val trackId: String,
    val isVideo: Boolean,
    val path: String,
    val bytes: Long,
    val createdAt: Long,
    val trackJson: String,
)
