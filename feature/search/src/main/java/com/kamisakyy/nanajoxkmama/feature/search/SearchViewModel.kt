package com.kamisakyy.nanajoxkmama.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamisakyy.nanajoxkmama.core.common.ApiResult
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.model.SearchResults
import com.kamisakyy.nanajoxkmama.core.network.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: SearchResults? = null,
    val error: String? = null,
    val recents: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: ContentRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()
    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(recents = settings.recentSearches())
        }
        viewModelScope.launch {
            queryFlow
                .debounce(150)
                .distinctUntilChanged()
                .collectLatest { q -> doSearch(q) }
        }
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q, suggestions = emptyList())
        queryFlow.value = q
    }

    fun submit() {
        val q = _ui.value.query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch { settings.addRecentSearch(q) }
        queryFlow.value = q
        _ui.value = _ui.value.copy(recents = (listOf(q) + _ui.value.recents.filter { it != q }).take(12))
    }

    fun clearRecents() {
        viewModelScope.launch {
            settings.clearRecentSearches()
            _ui.value = _ui.value.copy(recents = emptyList())
        }
    }

    private suspend fun doSearch(q: String) {
        if (q.isBlank()) {
            _ui.value = _ui.value.copy(results = null, error = null, loading = false)
            return
        }
        _ui.value = _ui.value.copy(loading = true, error = null)
        val s = settings.now()
        when (val r = repo.search(q, s.extraSources)) {
            is ApiResult.Ok -> _ui.value = _ui.value.copy(loading = false, results = r.data)
            is ApiResult.Err -> _ui.value = _ui.value.copy(loading = false, error = r.message)
        }
    }

    fun retry() {
        queryFlow.value = _ui.value.query
    }
}
