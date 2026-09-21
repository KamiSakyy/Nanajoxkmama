package com.kamisakyy.nanajoxkmama.feature.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamisakyy.nanajoxkmama.core.common.ApiResult
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.model.AnimeDetail
import com.kamisakyy.nanajoxkmama.core.model.ArtistDetail
import com.kamisakyy.nanajoxkmama.core.network.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailsData {
    data class Anime(val detail: AnimeDetail) : DetailsData
    data class Artist(val detail: ArtistDetail) : DetailsData
    data class Mix(val title: String, val subtitle: String, val tracks: List<com.kamisakyy.nanajoxkmama.core.model.Track>) : DetailsData
    data class Decade(val year: Int, val tracks: List<com.kamisakyy.nanajoxkmama.core.model.Track>) : DetailsData
}

data class DetailsUiState(
    val loading: Boolean = true,
    val data: DetailsData? = null,
    val error: String? = null,
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repo: ContentRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailsUiState())
    val ui: StateFlow<DetailsUiState> = _ui.asStateFlow()

    fun loadAnime(slug: String) {
        _ui.value = DetailsUiState(loading = true)
        viewModelScope.launch {
            val s = settings.now()
            when (val r = repo.animeDetail(slug, s.extraSources)) {
                is ApiResult.Ok -> _ui.value = DetailsUiState(loading = false, data = DetailsData.Anime(r.data))
                is ApiResult.Err -> _ui.value = DetailsUiState(loading = false, error = r.message)
            }
        }
    }

    fun loadArtist(slug: String) {
        _ui.value = DetailsUiState(loading = true)
        viewModelScope.launch {
            when (val r = repo.artistDetail(slug)) {
                is ApiResult.Ok -> _ui.value = DetailsUiState(loading = false, data = DetailsData.Artist(r.data))
                is ApiResult.Err -> _ui.value = DetailsUiState(loading = false, error = r.message)
            }
        }
    }

    fun loadMix(id: String) {
        _ui.value = DetailsUiState(loading = true)
        viewModelScope.launch {
            val s = settings.now()
            when (val r = repo.mix(id, s.extraSources)) {
                is ApiResult.Ok -> {
                    val m = com.kamisakyy.nanajoxkmama.core.common.Curated.MIXES.firstOrNull { it.id == id }
                    _ui.value = DetailsUiState(loading = false, data = DetailsData.Mix(m?.title ?: id, m?.subtitle ?: "", r.data))
                }
                is ApiResult.Err -> _ui.value = DetailsUiState(loading = false, error = r.message)
            }
        }
    }

    fun loadDecade(year: Int) {
        _ui.value = DetailsUiState(loading = true)
        viewModelScope.launch {
            when (val r = repo.decade(year)) {
                is ApiResult.Ok -> _ui.value = DetailsUiState(loading = false, data = DetailsData.Decade(year, r.data))
                is ApiResult.Err -> _ui.value = DetailsUiState(loading = false, error = r.message)
            }
        }
    }
}
