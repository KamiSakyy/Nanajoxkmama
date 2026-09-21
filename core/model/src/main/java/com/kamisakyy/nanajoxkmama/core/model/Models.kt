package com.kamisakyy.nanajoxkmama.core.model

/** One playable song — an opening / ending / insert theme with audio AND video URLs. */
data class Track(
    val id: String,
    val themeId: Int,
    val themeSlug: String,
    val type: ThemeType,
    val sequence: Int?,
    val title: String,
    val artists: List<ArtistRef>,
    val anime: AnimeRef,
    val cover: String?,
    val coverSmall: String?,
    /** Never preloaded: only attached when playback starts. */
    val audioUrl: String,
    /** Streamed ONLY after the user presses the video button. */
    val videoUrl: String,
    val resolution: Int?,
    val tags: String?,
    val version: Int?,
    val episodes: String?,
    val nsfw: Boolean,
    val spoiler: Boolean,
    val source: String,
) {
    val displayArtist: String get() = artists.joinToString(", ").ifEmpty { "Неизвестный исполнитель" }

    val animeName: String get() = anime.ruName ?: anime.name

    val themeTag: String
        get() = when (type) {
            ThemeType.OP -> "OP" + (sequence?.toString() ?: "")
            ThemeType.ED -> "ED" + (sequence?.toString() ?: "")
            ThemeType.IN -> if (sequence != null) "IN$sequence" else "IN"
        }

    val versionLabel: String get() = if (version != null && version > 1) "v$version" else ""

    /** true when a distinct video stream exists (site behaviour) */
    val hasVideo: Boolean get() = videoUrl.isNotEmpty() && videoUrl != audioUrl

    fun withOffline(path: String, video: Boolean): Track =
        if (video) copy(videoUrl = path) else copy(audioUrl = path)
}

enum class ThemeType { OP, ED, IN }

data class ArtistRef(val id: Int, val name: String, val slug: String?)

data class AnimeRef(
    val id: Int,
    val name: String,
    val slug: String,
    val year: Int?,
    val season: String?,
    val malId: Int?,
    val anilistId: Int?,
    val ruName: String? = null,
)

data class AnimeSummary(
    val id: Int,
    val name: String,
    val ruName: String?,
    val slug: String,
    val year: Int?,
    val season: String?,
    val mediaFormat: String?,
    val cover: String?,
    val coverSmall: String?,
    val banner: String?,
    val color: Int?,
    val malId: Int?,
    val synopsis: String?,
)

data class AnimeDetail(
    val summary: AnimeSummary,
    val synopsis: String?,
    val shikiDescription: String?,
    val score: Double?,
    val kind: String?,
    val episodes: Int?,
    val status: String?,
    val genres: List<String>,
    val studios: List<String>,
    val series: List<String>,
    val tracks: List<Track>,
    val screenshots: List<String> = emptyList(),
)

data class ArtistSummary(
    val id: Int,
    val name: String,
    val slug: String,
    val image: String?,
    val imageSmall: String?,
)

data class ArtistDetail(
    val summary: ArtistSummary,
    val information: String?,
    val tracks: List<Track>,
)

data class SearchResults(
    val anime: List<AnimeSummary>,
    val tracks: List<Track>,
    val artists: List<ArtistSummary>,
)

data class Paged<T>(val items: List<T>, val hasMore: Boolean, val page: Int)

data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val tracks: List<Track>,
)

data class DownloadEntry(
    val trackId: String,
    val video: Boolean,
    val path: String,
    val bytes: Long,
    val createdAt: Long,
)
