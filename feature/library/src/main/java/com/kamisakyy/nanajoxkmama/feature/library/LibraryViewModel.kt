package com.kamisakyy.nanajoxkmama.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamisakyy.nanajoxkmama.core.database.LibraryRepository
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.model.Playlist
import com.kamisakyy.nanajoxkmama.core.model.Track
import com.kamisakyy.nanajoxkmama.core.playback.DownloadsRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val downloads: DownloadsRepo,
    val settings: SettingsStore,
) : ViewModel() {

    val history: StateFlow<List<Track>> = library.history()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = library.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadsList = library.downloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() = viewModelScope.launch { library.clearHistory() }

    fun createPlaylist(name: String) = viewModelScope.launch { library.createPlaylist(name) }

    fun renamePlaylist(id: String, name: String) = viewModelScope.launch { library.renamePlaylist(id, name) }

    fun deletePlaylist(id: String) = viewModelScope.launch { library.deletePlaylist(id) }

    fun reorder(playlistId: String, tracks: List<Track>) = viewModelScope.launch {
        library.setPlaylistTracks(playlistId, tracks)
    }

    fun removeFromPlaylist(id: String, trackId: String) = viewModelScope.launch {
        library.removeFromPlaylist(id, trackId)
    }

    fun deleteDownload(key: String, path: String) = viewModelScope.launch {
        downloads.delete(key, path)
    }

    fun clearDownloads() = viewModelScope.launch {
        library.downloadsNow().forEach { (e, _) -> downloads.delete(e.key, e.path) }
        library.clearDownloads()
    }
}
