package com.kamisakyy.nanajoxkmama.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamisakyy.nanajoxkmama.core.common.ApiResult
import com.kamisakyy.nanajoxkmama.core.common.Curated
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.model.AnimeSummary
import com.kamisakyy.nanajoxkmama.core.network.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val mode: String = "season", // season | decade | genre
    val loading: Boolean = true,
    val year: Int = 0,
    val season: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
    val items: List<AnimeSummary> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repo: ContentRepository,
    val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(BrowseUiState())
    val ui: StateFlow<BrowseUiState> = _ui.asStateFlow()

    init {
        val (y, s) = com.kamisakyy.nanajoxkmama.core.common.currentSeasonSite()
        _ui.value = _ui.value.copy(year = y, season = s)
        loadSeason(reset = true)
    }

    fun loadSeason(reset: Boolean) {
        _ui.value = _ui.value.copy(loading = true, error = null, mode = "season")
        viewModelScope.launch {
            val page = if (reset) 1 else _ui.value.page + 1
            when (val r = repo.season(_ui.value.year, _ui.value.season, page)) {
                is ApiResult.Ok -> _ui.value = _ui.value.copy(
                    loading = false,
                    items = if (reset) r.data.items else _ui.value.items + r.data.items,
                    hasMore = r.data.hasMore,
                    page = page,
                )
                is ApiResult.Err -> _ui.value = _ui.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun setSeason(year: Int, season: String?) {
        _ui.value = _ui.value.copy(year = year, season = season)
        loadSeason(reset = true)
    }

    fun loadDecade(year: Int) {
        _ui.value = _ui.value.copy(loading = true, error = null, mode = "decade", year = year, items = emptyList())
        viewModelScope.launch {
            when (val r = repo.decade(year)) {
                is ApiResult.Ok -> {
                    val covers = r.data.map { t ->
                        AnimeSummary(
                            id = t.anime.id, name = t.anime.name, ruName = null, slug = t.anime.slug,
                            year = t.anime.year, season = t.anime.season, mediaFormat = null,
                            cover = t.cover, coverSmall = t.coverSmall, banner = null, color = null,
                            malId = t.anime.malId, synopsis = null,
                        )
                    }.distinctBy { it.slug }
                    _ui.value = _ui.value.copy(loading = false, items = covers, hasMore = false)
                }
                is ApiResult.Err -> _ui.value = _ui.value.copy(loading = false, error = r.message)
            }
        }
    }

    fun loadGenre(genreId: String) {
        _ui.value = _ui.value.copy(loading = true, error = null, mode = "genre", items = emptyList())
        viewModelScope.launch {
            val g = Curated.GENRES.firstOrNull { it.id == genreId } ?: return@launch
            val (y, _) = com.kamisakyy.nanajoxkmama.core.common.currentSeasonSite()
            val acc = LinkedHashMap<String, AnimeSummary>()
            for (yr in listOf(y, y - 1, y - 2)) {
                val r = repo.season(yr, null, 1)
                if (r is ApiResult.Ok) {
                    for (a in r.data.items) {
                        if (g.needles.any { n ->
                                (a.name + " " + (a.synopsis ?: "")).lowercase().contains(n.lowercase()) ||
                                    (a.mediaFormat ?: "").lowercase().contains(n.lowercase())
                            }
                        ) acc.putIfAbsent(a.slug, a)
                    }
                }
                if (acc.size >= 30) break
            }
            _ui.value = _ui.value.copy(loading = false, items = acc.values.toList(), hasMore = false)
        }
    }
}
