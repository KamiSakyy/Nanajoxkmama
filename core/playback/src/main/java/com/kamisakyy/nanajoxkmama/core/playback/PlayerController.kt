package com.kamisakyy.nanajoxkmama.core.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.kamisakyy.nanajoxkmama.core.database.LibraryRepository
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.model.ThemeType
import com.kamisakyy.nanajoxkmama.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class RepeatMode { OFF, ONE, ALL }

data class PlayerUiState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** site `videoMode` — video ONLY streams after the user presses the video button */
    val videoMode: Boolean = false,
    /** site `nowPlayingOpen` — when closed, audio-only again */
    val playerOpen: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val error: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
    val hasQueue: Boolean get() = queue.isNotEmpty()
    val wantVideo: Boolean get() = videoMode && playerOpen
}

/**
 * Playback brain — website `src/store/player.tsx` semantics ported 1:1:
 *  • ONE media element, `preload="none"` equivalent (nothing loads before user plays)
 *  • `wantVideo = videoMode && playerOpen` else AUDIO source
 *  • `applySource(track,{autoplay,keepPosition,restart})` with race-safe loadToken
 *  • ON MEDIA ERROR → retry ONCE with the OTHER url (video↔audio) of that track,
 *    then "Не удалось воспроизвести" + auto-`next` after 1500ms if queue>1
 *  • `next`: index+1 else repeat=ALL→0 else stop; `prev`: restart if pos>4s else back
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private val _activeVideo = MutableStateFlow(false)
    /** true while a video surface is attached — drives PlayerView */
    val activeVideo: StateFlow<Boolean> = _activeVideo.asStateFlow()

    private var controller: MediaController? = null
    private var loadToken = 0
    private var retriedOtherUrl = false
    private var shuffleOrder: List<Int> = emptyList()
    @Volatile private var offlineIndex: Map<String, String> = emptyMap()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            update { it.copy(isPlaying = it.queue.isNotEmpty() && isPlaying) }
            if (isPlaying) {
                persistQueue()
                startTicker()
            } else {
                stopTicker()
                syncPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
            syncPosition()
            if (playbackState == Player.STATE_READY) retriedOtherUrl = false
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            ) {
                val id = mediaItem?.mediaId
                val idx = _state.value.queue.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    update { it.copy(index = idx) }
                    retriedOtherUrl = false
                    onTrackStarted(_state.value.queue[idx], autoplay = true)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handleMediaError(error)
        }
    }

    init {
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({
                val c = future.get()
                controller = c
                c.addListener(listener)
                restoreQueue()
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /* ---------------- state helpers ---------------- */

    private fun update(f: (PlayerUiState) -> PlayerUiState) {
        _state.value = f(_state.value)
    }

    private fun toast(msg: String) {
        _toasts.tryEmit(msg)
    }

    private fun syncPosition() {
        val c = controller ?: return
        update {
            it.copy(
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.contentDuration.takeIf { d -> d > 0 } ?: c.duration.takeIf { d -> d > 0 } ?: 0,
            )
        }
    }

    private var tickJob: kotlinx.coroutines.Job? = null

    /** 350ms heartbeat — the seek bar tracks real playback (was frozen before). */
    private fun startTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                syncPosition()
                kotlinx.coroutines.delay(350)
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    /* ---------------- source logic (site applySource) ---------------- */

    private fun sourceFor(track: Track, wantVideo: Boolean): String {
        // offline-first: local file wins (website hasOffline/getOfflineUrl) — in-memory index
        refreshOfflineIndex()
        val v = offlineIndex["${track.id}:v"]
        val a = offlineIndex["${track.id}:a"]
        if (wantVideo && v != null && java.io.File(v).exists()) return v
        if (a != null && java.io.File(a).exists()) return a
        return if (wantVideo && track.videoUrl.isNotEmpty()) track.videoUrl else track.audioUrl
    }

    private fun refreshOfflineIndex() {
        if (offlineIndex.isEmpty()) {
            scope.launch(Dispatchers.IO) {
                val dl = runCatching { library.downloadsNow() }.getOrDefault(emptyList())
                offlineIndex = dl.associate { (e, _) -> "${e.trackId}:${if (e.isVideo) "v" else "a"}" to e.path }
            }
        }
    }

    fun invalidateOfflineIndex() {
        offlineIndex = emptyMap()
        scope.launch(Dispatchers.IO) {
            val dl = runCatching { library.downloadsNow() }.getOrDefault(emptyList())
            offlineIndex = dl.associate { (e, _) -> "${e.trackId}:${if (e.isVideo) "v" else "a"}" to e.path }
        }
    }

    private fun mediaItem(url: String, track: Track): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.displayArtist)
                    .setAlbumTitle(track.animeName)
                    .setArtworkUri(track.cover?.let(Uri::parse))
                    .build()
            )
            .build()

    /** Website `applySource(track,{autoplay,keepPosition,restart})`. */
    private fun applySource(track: Track, autoplay: Boolean, keepPosition: Boolean = false, restart: Boolean = false) {
        val c = controller ?: return
        val myToken = ++loadToken
        val wantVideo = _state.value.wantVideo
        val url = sourceFor(track, wantVideo)
        val pos = if (restart) 0 else if (keepPosition) c.currentPosition else 0
        c.setMediaItem(mediaItem(url, track), pos)
        c.playWhenReady = autoplay
        c.prepare()
        retriedOtherUrl = false
        if (myToken == loadToken) syncPosition()
    }

    /**
     * Website media `onerror`: retry ONCE with the OTHER url of the same track
     * (video→audio, audio→video), then toast + auto-next after 1500ms.
     */
    private fun handleMediaError(error: PlaybackException) {
        val s = _state.value
        val track = s.current ?: return
        if (!retriedOtherUrl) {
            retriedOtherUrl = true
            val wantVideo = s.wantVideo
            val fallbackUrl = if (wantVideo && track.audioUrl.isNotEmpty()) track.audioUrl
            else if (track.videoUrl.isNotEmpty()) track.videoUrl else null
            if (fallbackUrl != null && !fallbackUrl.startsWith("/") && !fallbackUrl.startsWith("file:")) {
                toast("Переключаю на другой источник…")
                val c = controller ?: return
                c.setMediaItem(mediaItem(fallbackUrl, track), 0)
                c.playWhenReady = true
                c.prepare()
                return
            }
        }
        retriedOtherUrl = false
        toast("Не удалось воспроизвести «${track.title}»")
        if (s.queue.size > 1 && !networkMonitor.isOffline()) {
            scope.launch {
                delay(1500)
                next()
            }
        } else if (s.queue.size > 1) {
            scope.launch {
                delay(1500)
                next()
            }
        } else {
            update { it.copy(isPlaying = false) }
        }
    }

    /* ---------------- queue API ---------------- */

    fun playAll(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val c = controller ?: run {
            update { it.copy(queue = tracks, index = startIndex.coerceIn(0, tracks.size - 1)) }
            return
        }
        update { it.copy(queue = tracks, index = startIndex.coerceIn(0, tracks.size - 1)) }
        buildShuffleOrder()
        applySource(tracks[_state.value.index], autoplay = true)
        onTrackStarted(tracks[_state.value.index], autoplay = true)
    }

    fun playTrack(track: Track) {
        playAll(listOf(track), 0)
    }

    fun enqueueNext(track: Track) {
        val s = _state.value
        val q = s.queue.toMutableList()
        val at = (s.index + 1).coerceIn(0, q.size)
        q.add(at, track)
        update { it.copy(queue = q) }
        toast("Добавлено в очередь")
        persistQueue()
    }

    fun enqueue(track: Track) {
        update { it.copy(queue = it.queue + track) }
        toast("Добавлено в конец очереди")
        persistQueue()
    }

    fun removeFromQueue(index: Int) {
        val s = _state.value
        val q = s.queue.toMutableList()
        if (index !in q.indices) return
        q.removeAt(index)
        var idx = s.index
        if (index < s.index) idx--
        if (index == s.index) {
            update { it.copy(queue = q, index = idx.coerceIn(0, q.size - 1)) }
            if (q.isEmpty()) stop() else applySource(q[_state.value.index], autoplay = s.isPlaying)
        } else {
            update { it.copy(queue = q, index = if (q.isEmpty()) -1 else idx.coerceIn(0, q.size - 1)) }
        }
        persistQueue()
    }

    fun moveInQueue(from: Int, to: Int) {
        val q = _state.value.queue.toMutableList()
        if (from !in q.indices || to !in q.indices) return
        val item = q.removeAt(from)
        q.add(to, item)
        var idx = _state.value.index
        if (idx == from) idx = to
        else if (from < idx && to >= idx) idx--
        else if (from > idx && to <= idx) idx++
        update { it.copy(queue = q, index = idx) }
        persistQueue()
    }

    fun clearQueue() {
        loadToken++
        controller?.stop()
        controller?.clearMediaItems()
        update { PlayerUiState() }
        persistQueue()
    }

    fun seekTo(index: Int) {
        val s = _state.value
        val t = s.queue.getOrNull(index) ?: return
        update { it.copy(index = index) }
        applySource(t, autoplay = true, restart = true)
        onTrackStarted(t, autoplay = true)
    }

    fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val idx = when {
            s.index + 1 < s.queue.size -> s.index + 1
            s.repeatMode == RepeatMode.ALL -> 0
            else -> {
                update { it.copy(isPlaying = false) }
                return
            }
        }
        update { it.copy(index = idx) }
        val t = s.queue[idx]
        applySource(t, autoplay = true, restart = true)
        onTrackStarted(t, autoplay = true)
    }

    fun prev() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if ((controller?.currentPosition ?: 0) > 4000) {
            controller?.seekTo(0)
            return
        }
        val idx = if (s.index > 0) s.index - 1 else if (s.repeatMode == RepeatMode.ALL) s.queue.size - 1 else 0
        update { it.copy(index = idx) }
        val t = s.queue[idx]
        applySource(t, autoplay = true, restart = true)
        onTrackStarted(t, autoplay = true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0 && _state.value.current != null) {
            applySource(_state.value.current!!, autoplay = true, restart = true)
            return
        }
        if (c.isPlaying) c.pause() else {
            c.playWhenReady = true
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.prepare()
        }
    }

    fun seekToPosition(ms: Long) {
        controller?.seekTo(ms)
        syncPosition()
    }

    fun toggleRepeat() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.OFF
        }
        controller?.repeatMode = when (next) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }
        update { it.copy(repeatMode = next) }
    }

    fun toggleShuffle() {
        val on = !_state.value.shuffle
        buildShuffleOrder()
        update { it.copy(shuffle = on) }
    }

    private fun buildShuffleOrder() {
        val n = _state.value.queue.size
        shuffleOrder = (0 until n).shuffled(Random(System.nanoTime()))
    }

    fun stop() {
        controller?.stop()
        controller?.clearMediaItems()
        update { it.copy(isPlaying = false, positionMs = 0, durationMs = 0) }
    }

    /* ---------------- video mode (site semantics) ---------------- */

    /** Called by the mini/full player windows. */
    fun setPlayerOpen(open: Boolean) {
        val was = _state.value.playerOpen
        update { it.copy(playerOpen = open) }
        if (was != open && _state.value.current != null && _state.value.videoMode != _state.value.wantVideo) {
            // wantVideo flipped → swap source with keepPosition (site toggleVideoMode/applySource)
            val track = _state.value.current!!
            val wantVideo = _state.value.wantVideo
            val url = sourceFor(track, wantVideo)
            val c = controller ?: return
            val pos = c.currentPosition
            val playing = c.isPlaying
            c.setMediaItem(mediaItem(url, track), pos)
            c.playWhenReady = playing
            c.prepare()
            retriedOtherUrl = false
        }
    }

    /** The video button: video streams ONLY from this moment. */
    fun setVideoMode(on: Boolean) {
        val was = _state.value.wantVideo
        update { it.copy(videoMode = on, playerOpen = true) }
        val now = _state.value.wantVideo
        if (was != now) {
            val track = _state.value.current ?: return
            val url = sourceFor(track, now)
            val c = controller ?: return
            val pos = c.currentPosition
            val playing = c.isPlaying
            c.setMediaItem(mediaItem(url, track), pos)
            c.playWhenReady = playing
            c.prepare()
            retriedOtherUrl = false
        }
    }

    fun attachSurface(view: Any?) {
        val c = controller ?: return
        when (view) {
            is SurfaceView -> c.setVideoSurfaceView(view)
            is TextureView -> c.setVideoTextureView(view)
            null -> c.clearVideoSurface()
        }
        _activeVideo.value = view != null && _state.value.wantVideo
    }

    /* ---------------- persistence / history ---------------- */

    private fun onTrackStarted(track: Track, autoplay: Boolean) {
        if (autoplay) {
            scope.launch(Dispatchers.IO) {
                runCatching { library.addToHistory(track) }
            }
        }
        syncPosition()
    }

    private fun persistQueue() {
        val s = _state.value
        scope.launch(Dispatchers.IO) {
            runCatching {
                settings.saveQueue(com.kamisakyy.nanajoxkmama.core.database.TrackJson.encodeList(s.queue))
            }
        }
    }

    private fun restoreQueue() {
        scope.launch(Dispatchers.IO) {
            val json = runCatching { settings.loadQueue() }.getOrNull() ?: return@launch
            val tracks = com.kamisakyy.nanajoxkmama.core.database.TrackJson.decodeList(json)
            if (tracks.isEmpty()) return@launch
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                update { it.copy(queue = tracks, index = 0) }
            }
        }
    }
}
