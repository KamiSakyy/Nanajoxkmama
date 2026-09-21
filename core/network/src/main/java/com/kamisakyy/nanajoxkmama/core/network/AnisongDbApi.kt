package com.kamisakyy.nanajoxkmama.core.network

import com.kamisakyy.nanajoxkmama.core.common.DAY_MS
import com.kamisakyy.nanajoxkmama.core.common.HOUR_MS
import com.kamisakyy.nanajoxkmama.core.common.themeTypeOrder
import com.kamisakyy.nanajoxkmama.core.model.*
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AnisongDB (anisongdb.com) — the database behind Anime Music Quiz: openings,
 * endings AND insert songs; media served from naedist.animemusicquiz.com.
 * Requests copy the website 1:1 (src/api/anisongdb.ts).
 */
@Singleton
class AnisongDbApi @Inject constructor(
    private val http: HttpEngine,
) {
    companion object {
        private const val BASE = "https://anisongdb.com/api"
        private const val MEDIA = "https://naedist.animemusicquiz.com/"
        private val FILTERS = mapOf(
            "ignore_duplicate" to false,
            "opening_filter" to true,
            "ending_filter" to true,
            "insert_filter" to true,
        )
    }

    private fun toTrack(s: JSONObject): Track? {
        val audioRaw = s.optString("audio").ifEmpty { null }
        val videoRaw = s.optString("HQ").ifEmpty { null } ?: s.optString("MQ").ifEmpty { null }
        val audio = audioRaw?.let { MEDIA + it.removePrefix("/") }
        val video = videoRaw?.let { MEDIA + it.removePrefix("/") }
        if (audio == null && video == null) return null
        val songType = s.optString("songType")
        val m = Regex("^(Opening|Ending|Insert)\\s*(\\d+)?", RegexOption.IGNORE_CASE).find(songType)
        val kind = m?.groupValues?.get(1)?.lowercase()
        val n = m?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        val type = when (kind) {
            "opening" -> ThemeType.OP
            "ending" -> ThemeType.ED
            else -> ThemeType.IN
        }
        val slug = when (type) {
            ThemeType.OP -> "OP${n ?: ""}"
            ThemeType.ED -> "ED${n ?: ""}"
            else -> if (n != null) "IN$n" else "IN"
        }
        val links = s.optJSONObject("linked_ids")
        val malId = first(links?.get("myanimelist"))
        val anilistId = first(links?.get("anilist"))
        val vintage = s.optString("animeVintage")
        var year: Int? = null
        var season: String? = null
        Regex("(Winter|Spring|Summer|Fall)\\s+(\\d{4})", RegexOption.IGNORE_CASE).find(vintage)?.let {
            season = it.groupValues[1].replaceFirstChar { c -> c.uppercase() }
            year = it.groupValues[2].toIntOrNull()
        }
        val artists = ArrayList<ArtistRef>()
        val rawArtists = s.optJSONArray("artists")
        if (rawArtists != null && rawArtists.length() > 0) {
            for (i in 0 until rawArtists.length()) {
                val a = rawArtists.optJSONObject(i) ?: continue
                val names = a.optJSONArray("names")
                val name = if (names != null && names.length() > 0) names.optString(0) else s.optString("songArtist")
                artists.add(ArtistRef(-a.optInt("id"), name.ifEmpty { s.optString("songArtist") }, null))
            }
        }
        if (artists.isEmpty()) artists.add(ArtistRef(0, s.optString("songArtist").ifEmpty { "Неизвестный исполнитель" }, null))
        val mal = malId
        return Track(
            id = "asdb:${s.optInt("annSongId")}",
            themeId = -s.optInt("annSongId"),
            themeSlug = slug,
            type = type,
            sequence = n,
            title = s.optString("songName").ifEmpty { "Без названия" },
            artists = artists,
            anime = AnimeRef(
                id = -s.optInt("annId"),
                name = s.optString("animeJPName").ifEmpty { s.optString("animeENName").ifEmpty { "Неизвестное аниме" } },
                slug = if (mal != null) "mal-$mal" else "ann-${s.optInt("annId")}",
                year = year,
                season = season,
                malId = mal,
                anilistId = anilistId,
            ),
            cover = null,
            coverSmall = null,
            audioUrl = audio ?: video!!,
            videoUrl = video ?: audio!!,
            resolution = if (s.optString("HQ").isNotEmpty()) 720 else if (s.optString("MQ").isNotEmpty()) 480 else null,
            tags = if (s.optString("HQ").isNotEmpty()) "HQ" else if (s.optString("MQ").isNotEmpty()) "MQ" else null,
            version = null,
            episodes = null,
            nsfw = false,
            spoiler = false,
            source = "extra",
        )
    }

    private fun first(v: Any?): Int? = when (v) {
        is Number -> v.toInt()
        is JSONArray -> if (v.length() > 0) v.optInt(0, -1).takeIf { it > 0 } else null
        else -> null
    }

    private fun dedupe(list: JSONArray): List<JSONObject> {
        val seen = HashSet<Int>()
        val out = ArrayList<JSONObject>()
        for (i in 0 until list.length()) {
            val s = list.optJSONObject(i) ?: continue
            if (seen.add(s.optInt("annSongId"))) out.add(s)
        }
        return out
    }

    private fun mapAll(list: JSONArray, limit: Int = 60): List<Track> =
        dedupe(list).mapNotNull { toTrack(it) }.sortedWith(
            compareBy({ themeTypeOrder(it.type.name) }, { it.sequence ?: 999 })
        ).take(limit)

    suspend fun search(q: String): List<Track> {
        val body = JSONObject()
            .put("anime_search_filter", JSONObject().put("search", q).put("partial_match", true))
            .put("song_name_search_filter", JSONObject().put("search", q).put("partial_match", true))
            .put("artist_search_filter", JSONObject().put("search", q).put("partial_match", true).put("group_granularity", 0).put("max_other_artist", 99))
            .put("and_logic", false)
            .apply { FILTERS.forEach { (k, v) -> put(k, v) } }
            .toString()
        val list = http.getArray("$BASE/search_request",
            HttpCachePolicy.default().body(body).fresh(6 * HOUR_MS).maxAge(7 * DAY_MS).timeout(15_000))
        return mapAll(list, 60)
    }

    /** All songs for an anime: MAL-id lookup first, then exact-name search (up to 2 names). */
    suspend fun forAnime(malId: Int?, names: List<String>): List<Track> {
        var list: JSONArray? = null
        if (malId != null && malId > 0) {
            try {
                val body = JSONObject().put("malIds", JSONArray().put(malId))
                    .apply { FILTERS.forEach { (k, v) -> put(k, v) } }.toString()
                list = http.getArray("$BASE/malIDs_request",
                    HttpCachePolicy.default().body(body).fresh(7 * DAY_MS).maxAge(30 * DAY_MS).timeout(15_000))
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                list = null
            }
        }
        if (list == null || list.length() == 0) {
            for (name in names.filter { it.isNotBlank() }.take(2)) {
                try {
                    val body = JSONObject()
                        .put("anime_search_filter", JSONObject().put("search", name).put("partial_match", false))
                        .put("and_logic", false)
                        .apply { FILTERS.forEach { (k, v) -> put(k, v) } }
                        .toString()
                    val r = http.getArray("$BASE/search_request",
                        HttpCachePolicy.default().body(body).fresh(7 * DAY_MS).maxAge(30 * DAY_MS).timeout(15_000))
                    if (r.length() > 0) {
                        list = r
                        break
                    }
                } catch (e: Exception) {
                    if (e is java.util.concurrent.CancellationException) throw e
                }
            }
        }
        return mapAll(list ?: JSONArray(), Int.MAX_VALUE)
    }

    suspend fun random(): List<Track> {
        val list = http.getArray("$BASE/get_50_random_songs",
            HttpCachePolicy.default().body("{}").noStore().ttl(0).timeout(15_000))
        return mapAll(list, 50)
    }
}
