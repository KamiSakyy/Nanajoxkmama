package com.kamisakyy.nanajoxkmama.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.common.formatBytes
import com.kamisakyy.nanajoxkmama.core.common.seasonLabel
import com.kamisakyy.nanajoxkmama.core.design.Artwork
import com.kamisakyy.nanajoxkmama.core.design.EmptyState
import com.kamisakyy.nanajoxkmama.core.design.ErrorState
import com.kamisakyy.nanajoxkmama.core.design.ShimmerTrackRow
import com.kamisakyy.nanajoxkmama.core.model.Playlist
import com.kamisakyy.nanajoxkmama.core.model.Track
import com.kamisakyy.nanajoxkmama.feature.player.TrackColumnList

/** Anime / artist / mix / decade detail — banner, meta, themes list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    viewModel: DetailsViewModel,
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

    when {
        ui.loading -> LazyColumn { items(8) { ShimmerTrackRow() } }
        ui.error != null -> ErrorState(ui.error!!, onRetry = { /* loader id re-fired by nav */ })
        ui.data == null -> EmptyState("Ничего нет", "Попробуй открыть раздел заново")
        else -> when (val d = ui.data!!) {
            is DetailsData.Anime -> {
                val a = d.detail
                TrackColumnList(
                    tracks = a.tracks,
                    currentId = currentId,
                    playlists = playlists,
                    downloadProgress = downloadProgress,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onEnqueue = onEnqueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onCreatePlaylist = onCreatePlaylist,
                    onDownload = onDownload,
                    onOpenAnime = { onOpenAnime(it.anime.slug) },
                    header = {
                        Column {
                            Box {
                                Artwork(a.summary.banner ?: a.summary.cover, Modifier.fillMaxWidth().height(190.dp), RoundedCornerShape(0.dp))
                            }
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(a.summary.coverSmall ?: a.summary.cover, Modifier.size(84.dp), RoundedCornerShape(16.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.summary.ruName ?: a.summary.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            a.kind?.let { if (it.isNotBlank()) it else null },
                                            seasonLabel(a.summary.year, a.summary.season),
                                            a.episodes?.let { "$it эп." },
                                            a.score?.let { String.format(java.util.Locale.US, "★ %.2f", it) },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = { if (a.tracks.isNotEmpty()) onPlay(a.tracks, 0) }) {
                                    Icon2()
                                    Text("Играть")
                                }
                            }
                            if (a.genres.isNotEmpty() || a.studios.isNotEmpty()) {
                                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (a.genres + a.studios).take(10).forEach { g ->
                                        SuggestionChip(onClick = {}, label = { Text(g) })
                                    }
                                }
                            }
                            val desc = a.shikiDescription ?: a.synopsis
                            if (!desc.isNullOrBlank()) {
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                            if (a.summary.malId != null) {
                                Row(Modifier.padding(horizontal = 16.dp)) {
                                    Text(
                                        "MyAnimeList: ${a.summary.malId}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Text("Темы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    },
                )
            }
            is DetailsData.Artist -> {
                val ar = d.detail
                TrackColumnList(
                    tracks = ar.tracks,
                    currentId = currentId,
                    playlists = playlists,
                    downloadProgress = downloadProgress,
                    onPlay = onPlay,
                    onPlayNext = onPlayNext,
                    onEnqueue = onEnqueue,
                    onAddToPlaylist = onAddToPlaylist,
                    onCreatePlaylist = onCreatePlaylist,
                    onDownload = onDownload,
                    onOpenAnime = { onOpenAnime(it.anime.slug) },
                    header = {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(ar.summary.imageSmall ?: ar.summary.image, Modifier.size(84.dp), RoundedCornerShape(16.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(ar.summary.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            }
                            ar.information?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp), maxLines = 8, overflow = TextOverflow.Ellipsis)
                            }
                            Text("Темы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    },
                )
            }
            is DetailsData.Mix -> MixDecadeList(d.title, d.subtitle, d.tracks, currentId, playlists, downloadProgress, onPlay, onPlayNext, onEnqueue, onAddToPlaylist, onCreatePlaylist, onDownload, onOpenAnime)
            is DetailsData.Decade -> MixDecadeList("${d.year}-е", "Хиты десятилетия", d.tracks, currentId, playlists, downloadProgress, onPlay, onPlayNext, onEnqueue, onAddToPlaylist, onCreatePlaylist, onDownload, onOpenAnime)
        }
    }
}

@Composable
private fun MixDecadeList(
    title: String,
    subtitle: String,
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
    onOpenAnime: (String) -> Unit,
) {
    TrackColumnList(
        tracks = tracks,
        currentId = currentId,
        playlists = playlists,
        downloadProgress = downloadProgress,
        onPlay = onPlay,
        onPlayNext = onPlayNext,
        onEnqueue = onEnqueue,
        onAddToPlaylist = onAddToPlaylist,
        onCreatePlaylist = onCreatePlaylist,
        onDownload = onDownload,
        onOpenAnime = { onOpenAnime(it.anime.slug) },
        header = {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0) }) {
                    Icon2()
                    Text("Слушать подборку")
                }
                Spacer(Modifier.height(8.dp))
            }
        },
    )
}

@Composable
private fun Icon2() {
    androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
}
