package com.kamisakyy.nanajoxkmama.core.database

import com.kamisakyy.nanajoxkmama.core.model.Playlist
import com.kamisakyy.nanajoxkmama.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

object TrackJson {
    fun encode(t: Track): String = JSONObject().apply {
        put("id", t.id); put("themeId", t.themeId); put("themeSlug", t.themeSlug)
        put("type", t.type.name); put("sequence", t.sequence ?: -1)
        put("title", t.title); put("animeName", t.animeName)
        put("artists", JSONArray(t.artists.map { JSONObject().put("id", it.id).put("name", it.name).put("slug", it.slug ?: "") }))
        put("anime", JSONObject()
            .put("id", t.anime.id).put("name", t.anime.name).put("slug", t.anime.slug)
            .put("year", t.anime.year ?: -1).put("season", t.anime.season ?: "")
            .put("malId", t.anime.malId ?: -1).put("anilistId", t.anime.anilistId ?: -1))
        put("cover", t.cover ?: ""); put("coverSmall", t.coverSmall ?: "")
        put("audioUrl", t.audioUrl); put("videoUrl", t.videoUrl)
        put("resolution", t.resolution ?: -1); put("tags", t.tags ?: "")
        put("version", t.version ?: -1); put("episodes", t.episodes ?: "")
        put("nsfw", t.nsfw); put("spoiler", t.spoiler); put("source", t.source)
    }.toString()

    fun decode(s: String): Track? = try {
        val o = JSONObject(s)
        val artistsArr = o.optJSONArray("artists")
        val artists = ArrayList<com.kamisakyy.nanajoxkmama.core.model.ArtistRef>()
        if (artistsArr != null) for (i in 0 until artistsArr.length()) {
            val a = artistsArr.optJSONObject(i) ?: continue
            artists.add(com.kamisakyy.nanajoxkmama.core.model.ArtistRef(a.optInt("id"), a.optString("name"), a.optString("slug").ifEmpty { null }))
        }
        val an = o.optJSONObject("anime") ?: JSONObject()
        com.kamisakyy.nanajoxkmama.core.model.Track(
            id = o.optString("id"),
            themeId = o.optInt("themeId"),
            themeSlug = o.optString("themeSlug"),
            type = runCatching { com.kamisakyy.nanajoxkmama.core.model.ThemeType.valueOf(o.optString("type")) }.getOrDefault(com.kamisakyy.nanajoxkmama.core.model.ThemeType.OP),
            sequence = o.optInt("sequence", -1).takeIf { it > 0 },
            title = o.optString("title"),
            artists = artists,
            anime = com.kamisakyy.nanajoxkmama.core.model.AnimeRef(
                id = an.optInt("id"), name = an.optString("name"), slug = an.optString("slug"),
                year = an.optInt("year", -1).takeIf { it > 0 },
                season = an.optString("season").ifEmpty { null },
                malId = an.optInt("malId", -1).takeIf { it > 0 },
                anilistId = an.optInt("anilistId", -1).takeIf { it > 0 },
            ),
            cover = o.optString("cover").ifEmpty { null },
            coverSmall = o.optString("coverSmall").ifEmpty { null },
            audioUrl = o.optString("audioUrl"),
            videoUrl = o.optString("videoUrl").ifEmpty { o.optString("audioUrl") },
            resolution = o.optInt("resolution", -1).takeIf { it > 0 },
            tags = o.optString("tags").ifEmpty { null },
            version = o.optInt("version", -1).takeIf { it > 0 },
            episodes = o.optString("episodes").ifEmpty { null },
            nsfw = o.optBoolean("nsfw", false),
            spoiler = o.optBoolean("spoiler", false),
            source = o.optString("source").ifEmpty { "primary" },
        )
    } catch (_: Exception) { null }

    fun encodeList(list: List<Track>): String = JSONArray(list.map { JSONObject(encode(it)) }).toString()
    fun decodeList(s: String?): List<Track> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).mapNotNull { decode(arr.getString(it)) }
        } catch (_: Exception) { emptyList() }
    }
}

@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
) {
    fun playlists(): Flow<List<Playlist>> = dao.playlists().map { list ->
        list.map { p ->
            Playlist(p.id, p.name, p.createdAt, dao.playlistTracks(p.id).mapNotNull { TrackJson.decode(it.trackJson) })
        }
    }

    suspend fun playlist(id: String): Playlist? {
        val p = dao.playlist(id) ?: return null
        return Playlist(p.id, p.name, p.createdAt, dao.playlistTracks(id).mapNotNull { TrackJson.decode(it.trackJson) })
    }

    suspend fun createPlaylist(name: String, tracks: List<Track> = emptyList()): Playlist {
        val id = UUID.randomUUID().toString()
        dao.upsertPlaylist(PlaylistEntity(id, name, System.currentTimeMillis()))
        if (tracks.isNotEmpty()) {
            dao.insertPlaylistTracks(tracks.mapIndexed { i, t -> PlaylistTrackEntity(id, i, TrackJson.encode(t)) })
        }
        return Playlist(id, name, System.currentTimeMillis(), tracks)
    }

    suspend fun renamePlaylist(id: String, name: String) {
        val p = dao.playlist(id) ?: return
        dao.upsertPlaylist(p.copy(name = name))
    }

    suspend fun deletePlaylist(id: String) {
        dao.deletePlaylist(id)
        dao.clearPlaylistTracks(id)
    }

    suspend fun addToPlaylist(id: String, track: Track) {
        val items = dao.playlistTracks(id)
        if (items.any { TrackJson.decode(it.trackJson)?.id == track.id }) return
        dao.insertPlaylistTracks(listOf(PlaylistTrackEntity(id, items.size, TrackJson.encode(track))))
    }

    suspend fun removeFromPlaylist(id: String, trackId: String) {
        val items = dao.playlistTracks(id).filter { TrackJson.decode(it.trackJson)?.id != trackId }
        dao.clearPlaylistTracks(id)
        if (items.isNotEmpty()) dao.insertPlaylistTracks(items.mapIndexed { i, t -> t.copy(position = i) })
    }

    suspend fun setPlaylistTracks(id: String, tracks: List<Track>) {
        dao.clearPlaylistTracks(id)
        dao.insertPlaylistTracks(tracks.mapIndexed { i, t -> PlaylistTrackEntity(id, i, TrackJson.encode(t)) })
    }

    fun history(): Flow<List<Track>> = dao.history().map { l -> l.mapNotNull { TrackJson.decode(it.trackJson) } }

    suspend fun addToHistory(t: Track) {
        dao.upsertHistory(HistoryEntity(t.id, System.currentTimeMillis(), TrackJson.encode(t)))
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun removeHistory(trackId: String) = dao.removeHistory(trackId)

    fun downloads(): Flow<List<Pair<DownloadEntity, Track?>>> =
        dao.downloads().map { l -> l.map { it to TrackJson.decode(it.trackJson) } }

    suspend fun downloadsNow(): List<Pair<DownloadEntity, Track?>> =
        dao.downloadsNow().map { it to TrackJson.decode(it.trackJson) }

    suspend fun upsertDownload(track: Track, video: Boolean, path: String, bytes: Long) {
        dao.upsertDownload(DownloadEntity("${track.id}:${if (video) "v" else "a"}", track.id, video, path, bytes, System.currentTimeMillis(), TrackJson.encode(track)))
    }

    suspend fun deleteDownload(key: String) = dao.deleteDownload(key)
    suspend fun clearDownloads() = dao.clearDownloads()
}
