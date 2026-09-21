package com.kamisakyy.nanajoxkmama.feature.player

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kamisakyy.nanajoxkmama.core.design.Artwork
import com.kamisakyy.nanajoxkmama.core.design.SegmentedControl
import com.kamisakyy.nanajoxkmama.core.design.TagChip
import com.kamisakyy.nanajoxkmama.core.playback.RepeatMode
import java.util.Locale

/**
 * Full player window — website NowPlayingView + player.tsx semantics:
 *  • video surface attaches ONLY in videoMode (button-driven, never preloaded)
 *  • closing the window drops video back to audio with keepPosition
 *  • controls drive playback; window stays open (never "closes on pause")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onClose: () -> Unit,
    onOpenAnime: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val track = state.current
    var showQueue by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val haptics = LocalHapticFeedback.current

    // window open/closed semantics (site nowPlayingOpen)
    LaunchedEffect(Unit) { viewModel.player.setPlayerOpen(true) }
    DisposableEffect(Unit) {
        onDispose { viewModel.player.setPlayerOpen(false) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClose()
            }) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "Свернуть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Сейчас играет", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { showActions = true }) {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Действия", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Очередь", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (track == null) {
            Spacer(Modifier.weight(1f))
            Text("Ничего не играет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            return@Column
        }

        Spacer(Modifier.height(8.dp))

        // ===== ARTWORK / VIDEO =====
        if (state.wantVideo && track.hasVideo) {
            VideoSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp)),
                controller = viewModel.player,
            )
        } else {
            var big by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(if (big) 1.04f else 1f, tween(400), label = "art")
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Artwork(
                    track.cover ?: track.coverSmall,
                    Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(26.dp)),
                )
                if (track.hasVideo) {
                    com.kamisakyy.nanajoxkmama.core.design.VideoThumb(
                        track.videoUrl,
                        Modifier
                            .align(androidx.compose.ui.Alignment.BottomEnd)
                            .padding(28.dp)
                            .size(92.dp),
                        RoundedCornerShape(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TagChip(track.themeTag + track.versionLabel)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.displayArtist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.animeName + (track.anime.year?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null) { onOpenAnime(track.anime.slug) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // ===== SEEK =====
        val pos = scrubbing ?: (if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f)
        Slider(
            value = pos,
            onValueChange = { scrubbing = it },
            onValueChangeFinished = {
                scrubbing?.let { viewModel.player.seekToPosition((it * state.durationMs).toLong()) }
                scrubbing = null
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shownMs = (scrubbing?.let { it * state.durationMs } ?: state.positionMs.toFloat()).toLong()
            Text(formatMs(shownMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(10.dp))

        // ===== CONTROLS =====
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.player.toggleShuffle()
            }) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Перемешать", tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { viewModel.player.prev() }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Предыдущий", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(34.dp))
            }
            FilledTonalIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.player.togglePlay()
                },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Пауза" else "Играть",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { viewModel.player.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Следующий", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.player.toggleRepeat()
            }) {
                val icon = when (state.repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                }
                Icon(icon, contentDescription = "Повтор", tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ===== VIDEO MODE TOGGLE — the ONLY trigger for video streaming =====
        SegmentedControl(
            options = listOf("Аудио", "Видео"),
            selectedIndex = if (state.videoMode) 1 else 0,
            onSelect = { i ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.player.setVideoMode(i == 1)
            },
        )
        Spacer(Modifier.height(20.dp))
    }

    if (showActions && track != null) {
        TrackActionSheet(
            track = track,
            playlists = playlists,
            downloadProgress = downloadProgress[track.id + ":a"] ?: downloadProgress[track.id + ":v"],
            onPlay = { viewModel.player.playTrack(track) },
            onPlayNext = { viewModel.player.enqueueNext(track) },
            onEnqueue = { viewModel.player.enqueue(track) },
            onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
            onCreatePlaylist = { viewModel.createPlaylist(it, track) },
            onDownloadAudio = { viewModel.download(track, false) },
            onDownloadVideo = { viewModel.download(track, true) },
            onOpenAnime = { onOpenAnime(track.anime.slug) },
            onDismiss = { showActions = false },
        )
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Text("Очередь", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(420.dp)) {
                items(count = state.queue.size) { i ->
                    val t = state.queue[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.player.seekTo(i)
                                showQueue = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (i == state.index) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.animeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TagChip(t.themeTag)
                        Spacer(Modifier.width(8.dp))
                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { viewModel.player.removeFromQueue(i) }.padding(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun VideoSurface(modifier: Modifier, controller: com.kamisakyy.nanajoxkmama.core.playback.PlayerController) {
    DisposableEffect(controller) {
        onDispose { controller.attachSurface(null) }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).also { controller.attachSurface(it) }
        },
    )
}

private fun formatMs(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
