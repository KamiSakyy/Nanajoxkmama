package com.kamisakyy.nanajoxkmama.feature.player

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kamisakyy.nanajoxkmama.core.design.Artwork
import com.kamisakyy.nanajoxkmama.core.design.TagChip
import com.kamisakyy.nanajoxkmama.core.model.Playlist
import com.kamisakyy.nanajoxkmama.core.model.Track

/** Long-press context sheet — queue ops, playlist add, download (site context sheet port). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: Track,
    playlists: List<Playlist>,
    downloadProgress: String?,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDownloadAudio: () -> Unit,
    onDownloadVideo: () -> Unit,
    onOpenAnime: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.coverSmall ?: track.cover, Modifier.size(56.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagChip(track.themeTag)
                    Spacer(Modifier.width(8.dp))
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(track.displayArtist + " · " + track.animeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (showPlaylistPicker) {
            Column(Modifier.padding(20.dp)) {
                Text("Добавить в плейлист", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                playlists.forEach { p ->
                    Text(
                        p.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { onAddToPlaylist(p.id) }.padding(vertical = 10.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, placeholder = { Text("Новый плейлист") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = { if (newName.isNotBlank()) onCreatePlaylist(newName.trim()) }, enabled = newName.isNotBlank()) { Text("Создать") }
                }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Column(Modifier.padding(vertical = 8.dp)) {
                SheetAction("Слушать") { onPlay(); onDismiss() }
                SheetAction("Играть следующим") { onPlayNext(); onDismiss() }
                SheetAction("Добавить в очередь") { onEnqueue(); onDismiss() }
                SheetAction("Добавить в плейлист") { showPlaylistPicker = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SheetAction(if (downloadProgress != null) downloadProgress else "Скачать аудио") { onDownloadAudio() }
                SheetAction("Скачать видео (если есть)") { onDownloadVideo() }
                if (onOpenAnime != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SheetAction("Перейти к аниме") { onOpenAnime(); onDismiss() }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
    )
}
