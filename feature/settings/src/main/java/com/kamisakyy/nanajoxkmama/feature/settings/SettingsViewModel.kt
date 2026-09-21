package com.kamisakyy.nanajoxkmama.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kamisakyy.nanajoxkmama.core.database.SettingsStore
import com.kamisakyy.nanajoxkmama.core.database.ThemeMode
import com.kamisakyy.nanajoxkmama.core.playback.DownloadsRepo
import com.kamisakyy.nanajoxkmama.core.network.HttpEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settings: SettingsStore,
    private val http: HttpEngine,
    private val downloads: DownloadsRepo,
) : ViewModel() {

    fun setPeriod(v: String) = viewModelScope.launch { settings.setPeriod(v) }
    fun setMature(v: Boolean) = viewModelScope.launch { settings.setMature(v) }
    fun setRuTitles(v: Boolean) = viewModelScope.launch { settings.setRuTitles(v) }
    fun setExtraSources(v: Boolean) = viewModelScope.launch { settings.setExtraSources(v) }
    fun setDataSaver(v: Boolean) = viewModelScope.launch { settings.setDataSaver(v) }
    fun setPreload(v: Boolean) = viewModelScope.launch { settings.setPreloadNext(v) }
    fun setThemeMode(m: ThemeMode) = viewModelScope.launch { settings.setThemeMode(m) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { settings.setDynamicColor(v) }
    fun setDownloadVideo(v: Boolean) = viewModelScope.launch { settings.setDownloadVideo(v) }
    fun clearCache() = viewModelScope.launch { http.clearAsync() }
    fun cacheBytes(): Long = http.diskBytes()
}
