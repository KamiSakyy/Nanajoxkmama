package com.kamisakyy.nanajoxkmama.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.design.AnimeCard
import com.kamisakyy.nanajoxkmama.core.design.EmptyState
import com.kamisakyy.nanajoxkmama.core.design.ErrorState
import com.kamisakyy.nanajoxkmama.core.design.SectionHeader
import com.kamisakyy.nanajoxkmama.core.design.ShimmerTrackRow
import com.kamisakyy.nanajoxkmama.core.model.Playlist
import com.kamisakyy.nanajoxkmama.core.model.Track
import com.kamisakyy.nanajoxkmama.feature.player.TrackColumnList

/** Global search — anime RU/EN + song + artist + AnisongDB extras (site search). */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    playlists: List<Playlist>,
    downloadProgress: Map<String, String>,
    currentId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onAddToPlaylist: (String, Track) -> Unit,
    onCreatePlaylist: (String, Track) -> Unit,
    onDownload: (Track, Boolean) -> Unit,
    onOpenAnime: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Поиск: аниме, песня, исполнитель") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (ui.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Очистить") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        when {
            ui.loading && ui.results == null -> {
                LazyColumn { items(6) { ShimmerTrackRow() } }
            }
            ui.error != null && ui.results == null -> ErrorState(ui.error!!, onRetry = viewModel::retry)
            ui.results == null -> {
                LazyColumn {
                    if (ui.recents.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader("Недавние запросы", Modifier.weight(1f))
                                Text("Очистить", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable { viewModel.clearRecents() })
                            }
                        }
                        items(ui.recents) { r ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.setQuery(r) }.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(r, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else {
                        item { EmptyState("Что ищем?", "Найди аниме, песню или исполнителя") }
                    }
                    item { Spacer(Modifier.height(120.dp)) }
                }
            }
            else -> {
                val r = ui.results!!
                if (r.anime.isEmpty() && r.tracks.isEmpty() && r.artists.isEmpty()) {
                    EmptyState("Ничего не найдено", "Попробуй изменить запрос — например, OP или имя исполнителя")
                } else {
                    LazyColumn {
                        if (r.anime.isNotEmpty()) {
                            item { SectionHeader("Аниме") }
                            item {
                                androidx.compose.foundation.lazy.LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(r.anime.size) { i ->
                                        val a = r.anime[i]
                                        AnimeCard(
                                            a,
                                            subtitle = listOfNotNull(a.year?.toString(), a.season).joinToString(" "),
                                            onClick = { onOpenAnime(a.slug) },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        if (r.tracks.isNotEmpty()) {
                            item { SectionHeader("Темы") }
                            items(r.tracks, key = { it.id }) { t ->
                                TrackColumnListRow(
                                    t, r.tracks, currentId, playlists, downloadProgress,
                                    onPlay, onPlayNext, onEnqueue, onAddToPlaylist, onCreatePlaylist, onDownload, onOpenAnime,
                                )
                            }
                        }
                        if (r.artists.isNotEmpty()) {
                            item { SectionHeader("Исполнители") }
                            items(r.artists) { ar ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(ar.name, style = MaterialTheme.typography.bodyLarge)
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

/* one track row via shared sheet actions */
@Composable
private fun TrackColumnListRow(
    t: Track,
    all: List<Track>,
    currentId: String?,
    playlists: List<Playlist>,
    downloadProgress: Map<String, String>,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onAddToPlaylist: (String, Track) -> Unit,
    onCreatePlaylist: (String, Track) -> Unit,
    onDownload: (Track, Boolean) -> Unit,
    onOpenAnime: (String) -> Unit,
) {
    var sheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    com.kamisakyy.nanajoxkmama.core.design.TrackRow(
        track = t,
        isCurrent = t.id == currentId,
        onClick = { onPlay(all, all.indexOfFirst { it.id == t.id }) },
        onLongClick = { sheet = true },
    )
    if (sheet) {
        com.kamisakyy.nanajoxkmama.feature.player.TrackActionSheet(
            track = t,
            playlists = playlists,
            downloadProgress = downloadProgress[t.id + ":a"] ?: downloadProgress[t.id + ":v"],
            onPlay = { onPlay(all, all.indexOfFirst { it.id == t.id }) },
            onPlayNext = { onPlayNext(t) },
            onEnqueue = { onEnqueue(t) },
            onAddToPlaylist = { onAddToPlaylist(it, t) },
            onCreatePlaylist = { onCreatePlaylist(it, t) },
            onDownloadAudio = { onDownload(t, false) },
            onDownloadVideo = { onDownload(t, true) },
            onOpenAnime = { onOpenAnime(t.anime.slug) },
            onDismiss = { sheet = false },
        )
    }
}
