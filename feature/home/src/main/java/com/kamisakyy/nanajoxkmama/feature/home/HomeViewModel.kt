package com.kamisakyy.nanajoxkmama.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamisakyy.nanajoxkmama.core.common.ApiResult
import com.kamisakyy.nanajoxkmama.core.common.Curated
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.model.Track
import com.kamisakyy.nanajoxkmama.core.network.ContentRepository
import com.kamisakyy.nanajoxkmama.core.network.HomeFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val feed: HomeFeed? = null,
    val error: String? = null,
    val refreshing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ContentRepository,
    val settings: SettingsStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val s = settings.now()
            when (val r = repo.home(s.period, s.extraSources)) {
                is ApiResult.Ok -> _ui.value = HomeUiState(loading = false, feed = r.data)
                is ApiResult.Err -> _ui.value = HomeUiState(loading = false, error = r.message, feed = _ui.value.feed)
            }
        }
    }

    fun refresh() {
        _ui.value = _ui.value.copy(refreshing = true)
        viewModelScope.launch {
            val s = settings.now()
            when (val r = repo.home(s.period, s.extraSources)) {
                is ApiResult.Ok -> _ui.value = HomeUiState(loading = false, feed = r.data)
                is ApiResult.Err -> _ui.value = _ui.value.copy(refreshing = false, error = r.message)
            }
        }
    }

    fun setPeriod(period: String) {
        viewModelScope.launch {
            settings.setPeriod(period)
            refresh()
        }
    }
}
