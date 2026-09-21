package com.kamisakyy.nanajoxkmama.core.database

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "anibeat_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object K {
        val PERIOD = stringPreferencesKey("period")
        val MATURE = booleanPreferencesKey("mature")
        val RU_TITLES = booleanPreferencesKey("ru_titles")
        val EXTRA = booleanPreferencesKey("extra_sources")
        val DATA_SAVER = booleanPreferencesKey("data_saver")
        val PRELOAD = booleanPreferencesKey("preload_next")
        val THEME = intPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val DL_VIDEO = booleanPreferencesKey("download_video")
        val QUEUE = stringPreferencesKey("queue_json")
        val RECENTS = stringPreferencesKey("recent_searches")
    }

    data class Snapshot(
        val period: String,
        val mature: Boolean,
        val ruTitles: Boolean,
        val extraSources: Boolean,
        val dataSaver: Boolean,
        val preloadNext: Boolean,
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
        val downloadVideo: Boolean,
    )

    val snapshot: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            period = p[K.PERIOD] ?: "week",
            mature = p[K.MATURE] ?: false,
            ruTitles = p[K.RU_TITLES] ?: true,
            extraSources = p[K.EXTRA] ?: true,
            dataSaver = p[K.DATA_SAVER] ?: false,
            preloadNext = p[K.PRELOAD] ?: true,
            themeMode = ThemeMode.entries[p[K.THEME] ?: 0],
            dynamicColor = p[K.DYNAMIC] ?: false,
            downloadVideo = p[K.DL_VIDEO] ?: false,
        )
    }

    suspend fun now(): Snapshot = snapshot.first()

    suspend fun setPeriod(v: String) = context.dataStore.edit { it[K.PERIOD] = v }
    suspend fun setMature(v: Boolean) = context.dataStore.edit { it[K.MATURE] = v }
    suspend fun setRuTitles(v: Boolean) = context.dataStore.edit { it[K.RU_TITLES] = v }
    suspend fun setExtraSources(v: Boolean) = context.dataStore.edit { it[K.EXTRA] = v }
    suspend fun setDataSaver(v: Boolean) = context.dataStore.edit { it[K.DATA_SAVER] = v }
    suspend fun setPreloadNext(v: Boolean) = context.dataStore.edit { it[K.PRELOAD] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[K.THEME] = v.ordinal }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[K.DYNAMIC] = v }
    suspend fun setDownloadVideo(v: Boolean) = context.dataStore.edit { it[K.DL_VIDEO] = v }

    suspend fun saveQueue(json: String) = context.dataStore.edit { it[K.QUEUE] = json }
    suspend fun loadQueue(): String? = context.dataStore.data.first()[K.QUEUE]

    suspend fun recentSearches(): List<String> =
        (context.dataStore.data.first()[K.RECENTS] ?: "").split('\n').filter { it.isNotBlank() }

    suspend fun addRecentSearch(q: String) = context.dataStore.edit { p ->
        val old = (p[K.RECENTS] ?: "").split('\n').filter { it.isNotBlank() && it != q }
        p[K.RECENTS] = (listOf(q) + old).take(12).joinToString("\n")
    }

    suspend fun clearRecentSearches() = context.dataStore.edit { it.remove(K.RECENTS) }
}
