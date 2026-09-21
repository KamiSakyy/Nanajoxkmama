package com.kamisakyy.nanajoxkmama.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.design.EmptyState
import com.kamisakyy.nanajoxkmama.core.design.SectionHeader
import com.kamisakyy.nanajoxkmama.core.design.ShimmerTrackRow
import com.kamisakyy.nanajoxkmama.core.design.TrackRow
import com.kamisakyy.nanajoxkmama.core.model.Track
import com.kamisakyy.nanajoxkmama.core.common.formatBytes

/** Library — История / Плейлисты / Загрузки (offline-first). */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    currentId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val history by viewModel.history.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val downloads by viewModel.downloadsList.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            listOf("История", "Плейлисты", "Загрузки").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> {
                if (history.isEmpty()) EmptyState("История пуста", "Треки, которые ты играл, появятся тут")
                else {
                    LazyColumn {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearHistory) { Text("Очистить") }
                            }
                        }
                        items(history, key = { it.id }) { t ->
                            com.kamisakyy.nanajoxkmama.core.design.SwipeTrackRow(
                                onPlayNext = { /* queue-next handled by playback layer below */ },
                                onDelete = { viewModel.deleteHistory(t.id) },
                            ) {
                                TrackRow(t, isCurrent = t.id == currentId, onClick = { onPlay(history, history.indexOfFirst { it.id == t.id }) }, onLongClick = {})
                            }
                        }
                        item { Spacer(Modifier.height(120.dp)) }
                    }
                }
            }
            1 -> {
                if (playlists.isEmpty()) {
                    EmptyState(
                        "Плейлистов нет",
                        "Создай плейлист из меню любого трека",
                        actionLabel = "Создать",
                        onAction = { viewModel.createPlaylist("Мой плейлист") },
                    )
                } else {
                    LazyColumn {
                        items(playlists, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenPlaylist(p.id) }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        p.tracks.size.toString() + " треков",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.deletePlaylist(p.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(120.dp)) }
                    }
                }
            }
            2 -> {
                if (downloads.isEmpty()) {
                    EmptyState("Загрузок нет", "Скачай треки, чтобы слушать без интернета")
                } else {
                    LazyColumn {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearDownloads) { Text("Очистить всё") }
                            }
                        }
                        items(downloads, key = { it.first.key }) { (e, t) ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width8())
                                Column(Modifier.weight(1f)) {
                                    Text(t?.title ?: e.trackId, style = MaterialTheme.typography.titleSmall)
                                    Text(formatBytes(e.bytes) + if (e.isVideo) " · видео" else " · аудио", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.deleteDownload(e.key, e.path) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null)
                                }
                            }
                        }
                        item { Spacer(Modifier.height(120.dp)) }
                    }
                }
            }
        }
    }
}

private fun Modifier.width8(): Modifier = this.then(Modifier.padding(start = 8.dp))
