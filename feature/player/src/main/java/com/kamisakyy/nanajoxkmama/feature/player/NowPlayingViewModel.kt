package com.kamisakyy.nanajoxkmama.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamisakyy.nanajoxkmama.core.database.LibraryRepository
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.model.Playlist
import com.kamisakyy.nanajoxkmama.core.model.Track
import com.kamisakyy.nanajoxkmama.core.playback.DownloadState
import com.kamisakyy.nanajoxkmama.core.playback.DownloadsRepo
import com.kamisakyy.nanajoxkmama.core.playback.PlayerController
import com.kamisakyy.nanajoxkmama.core.playback.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val player: PlayerController,
    private val library: LibraryRepository,
    private val downloads: DownloadsRepo,
    val settings: SettingsStore,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = player.state

    val playlists: StateFlow<List<Playlist>> = library.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _downloadProgress = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, String>> = _downloadProgress.asStateFlow()

    fun download(track: Track, video: Boolean) {
        viewModelScope.launch {
            downloads.download(track, video).collect { s ->
                val key = track.id + (if (video) ":v" else ":a")
                val map = _downloadProgress.value.toMutableMap()
                when (s) {
                    is DownloadState.Progress -> map[key] = "Скачиваю…"
                    is DownloadState.Done -> map[key] = "Загружено"
                    is DownloadState.Failed -> map[key] = "Ошибка: " + s.message
                }
                _downloadProgress.value = map
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { library.addToPlaylist(playlistId, track) }
    }

    fun createPlaylist(name: String, track: Track) {
        viewModelScope.launch { library.createPlaylist(name, listOf(track)) }
    }
}
