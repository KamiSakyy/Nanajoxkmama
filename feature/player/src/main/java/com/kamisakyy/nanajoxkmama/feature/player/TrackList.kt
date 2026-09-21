package com.kamisakyy.nanajoxkmama.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.design.PulseBars
import com.kamisakyy.nanajoxkmama.core.design.TrackRow
import com.kamisakyy.nanajoxkmama.core.model.Playlist
import com.kamisakyy.nanajoxkmama.core.model.Track
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

/**
 * Reusable track list with long-press action sheet (queue ops / playlists / downloads).
 * Every feature screen that shows songs feeds it.
 */
@Composable
fun TrackColumnList(
    tracks: List<Track>,
    currentId: String?,
    playlists: List<Playlist>,
    downloadProgress: Map<String, String>,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onAddToPlaylist: (String, Track) -> Unit,
    onCreatePlaylist: (String, Track) -> Unit,
    onDownload: (Track, Boolean) -> Unit,
    onOpenAnime: (Track) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    emptyMessage: String = "Треков пока нет",
) {
    var sheetTrack by remember { mutableStateOf<Track?>(null) }

    if (tracks.isEmpty()) {
        Column {
            header?.invoke()
            Text(emptyMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        }
        return
    }

    LazyColumn(modifier) {
        header?.let { item { it() } }
        items(tracks, key = { it.id }) { t ->
            val isCurrent = t.id == currentId
            TrackRow(
                track = t,
                isCurrent = isCurrent,
                onClick = { onPlay(tracks, tracks.indexOfFirst { x -> x.id == t.id }) },
                onLongClick = { sheetTrack = t },
                trailing = {
                    if (isCurrent) PulseBars()
                    // ВИДИМАЯ кнопка скачивания в каждой строке (не только в меню долгого нажатия)
                    IconButton(onClick = { onDownload(t, false) }) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Скачать аудио",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
        footer?.let { item { it() } }
    }

    sheetTrack?.let { t ->
        TrackActionSheet(
            track = t,
            playlists = playlists,
            downloadProgress = downloadProgress[t.id + ":a"] ?: downloadProgress[t.id + ":v"],
            onPlay = { onPlay(tracks, tracks.indexOfFirst { x -> x.id == t.id }) },
            onPlayNext = { onPlayNext(t) },
            onEnqueue = { onEnqueue(t) },
            onAddToPlaylist = { id -> onAddToPlaylist(id, t) },
            onCreatePlaylist = { name -> onCreatePlaylist(name, t) },
            onDownloadAudio = { onDownload(t, false) },
            onDownloadVideo = { onDownload(t, true) },
            onOpenAnime = { onOpenAnime(t) },
            onDismiss = { sheetTrack = null },
        )
    }
}
