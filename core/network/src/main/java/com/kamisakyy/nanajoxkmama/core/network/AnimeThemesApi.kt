package com.kamisakyy.nanajoxkmama.core.network

import com.kamisakyy.nanajoxkmama.core.common.ApiException
import com.kamisakyy.nanajoxkmama.core.common.DAY_MS
import com.kamisakyy.nanajoxkmama.core.common.HOUR_MS
import com.kamisakyy.nanajoxkmama.core.common.MINUTE_MS
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
import org.json.JSONObject
import com.kamisakyy.nanajoxkmama.core.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AnimeThemes.moe — public, key-less REST API with full OP/ED audio & video.
 * Query shapes copy the website 1:1 (src/api/animethemes.ts).
 */
@Singleton
class AnimeThemesApi @Inject constructor(
    private val http: HttpEngine,
    private val meta: MetaApi,
) {
    companion object {
        private const val BASE = "https://api.animethemes.moe"
        private val F = linkedMapOf(
            "fields[anime]" to "id,name,slug,year,season,media_format",
            "fields[animetheme]" to "id,slug,type,sequence",
            "fields[song]" to "id,title",
            "fields[artist]" to "id,name,slug",
            "fields[animethemeentry]" to "id,version,episodes,nsfw,spoiler",
            "fields[video]" to "id,link,resolution,tags,nc",
            "fields[audio]" to "id,link",
            "fields[image]" to "id,facet,link",
        )
        private val FR = F + ("fields[resource]" to "site,link,external_id")
        private const val THEME_INCLUDE = "anime.images,song.artists,animethemeentries.videos.audio"
        private const val ANIME_THEMES_INCLUDE = "images,resources,animethemes.song.artists,animethemes.animethemeentries.videos.audio"
        private const val ANIME_LIST_INCLUDE = "images,resources"
        private const val SITE_MAL = "MyAnimeList"
        private const val SITE_AL = "AniList"
    }

    private fun url(path: String, params: Map<String, Any?>): String = http.buildUrl(BASE, path, params)

    /* ------------------------- mappers ------------------------- */

    private fun pickImage(images: JSONArray?, facet: String): String? {
        if (images == null || images.length() == 0) return null
        for (i in 0 until images.length()) {
            val im = images.optJSONObject(i) ?: continue
            if (im.optString("facet") == facet) return im.optString("link").ifEmpty { null }
        }
        return images.optJSONObject(0)?.optString("link")?.ifEmpty { null }
    }

    private fun externalId(a: JSONObject, site: String): Int? {
        val res = a.optJSONArray("resources") ?: return null
        for (i in 0 until res.length()) {
            val r = res.optJSONObject(i) ?: continue
            if (r.optString("site") != site) continue
            val ext = if (r.has("external_id") && !r.isNull("external_id")) r.optInt("external_id", -1) else -1
            if (ext > 0) return ext
            val link = r.optString("link")
            val m = Regex("/(\\d+)/?$").find(link)
            return m?.groupValues?.get(1)?.toIntOrNull()
        }
        return null
    }

    private fun animeRef(a: JSONObject): AnimeRef {
        val year = if (a.isNull("year")) null else a.optInt("year", -1).takeIf { it > 0 }
        val season = if (a.isNull("season")) null else a.optString("season").ifEmpty { null }
        return AnimeRef(
            id = a.optInt("id"),
            name = a.optString("name"),
            slug = a.optString("slug"),
            year = year,
            season = season,
            malId = null,
            anilistId = null,
        )
    }

    private fun toAnimeSummary(a: JSONObject): AnimeSummary {
        val images = a.optJSONArray("images")
        return AnimeSummary(
            id = a.optInt("id"),
            name = a.optString("name"),
            ruName = null,
            slug = a.optString("slug"),
            year = if (a.isNull("year")) null else a.optInt("year", -1).takeIf { it > 0 },
            season = if (a.isNull("season")) null else a.optString("season").ifEmpty { null },
            mediaFormat = a.optString("media_format").ifEmpty { null },
            cover = pickImage(images, "Large Cover"),
            // ЧЁРНЫЙ КВАДРАТ = AVIF не декодируется. Только не-AVIF Small, иначе Large.
            coverSmall = pickImage(images, "Small Cover")?.takeUnless { it.endsWith(".avif", true) }
                ?: pickImage(images, "Large Cover"),
            banner = null,
            color = null,
            malId = externalId(a, SITE_MAL),
            synopsis = a.optString("synopsis").ifEmpty { null },
        )
    }

    /** Best video for an entry: creditless first, then highest resolution (site-exact). */
    private fun bestVideo(videos: JSONArray?): JSONObject? {
        if (videos == null || videos.length() == 0) return null
        var hasAudio = false
        for (i in 0 until videos.length()) {
            val v = videos.optJSONObject(i) ?: continue
            val audio = v.optJSONObject("audio")
            if (audio != null && audio.optString("link").isNotEmpty()) hasAudio = true
        }
        var best: JSONObject? = null
        for (i in 0 until videos.length()) {
            val v = videos.optJSONObject(i) ?: continue
            val audio = v.optJSONObject("audio")
            val usable = !hasAudio || (audio != null && audio.optString("link").isNotEmpty())
            if (!usable) continue
            val b = best
            if (b == null) {
                best = v
                continue
            }
            val vNc = if (v.optBoolean("nc", false)) 1 else 0
            val bNc = if (b.optBoolean("nc", false)) 1 else 0
            if (vNc > bNc || (vNc == bNc && v.optInt("resolution", 0) > b.optInt("resolution", 0))) best = v
        }
        return best
    }

    private fun themeToTracks(theme: JSONObject, anime: JSONObject, songOverride: JSONObject? = null, allVersions: Boolean = false): List<Track> {
        val out = ArrayList<Track>()
        val song = songOverride ?: theme.optJSONObject("song")
        val cover = pickImage(anime.optJSONArray("images"), "Large Cover")
        val coverSmall = pickImage(anime.optJSONArray("images"), "Small Cover")?.takeUnless { it.endsWith(".avif", true) } ?: cover
        val ref = animeRef(anime)
        val entries = theme.optJSONArray("animethemeentries") ?: return out
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val video = bestVideo(entry.optJSONArray("videos")) ?: continue
            val audio = video.optJSONObject("audio")
            val audioUrl = (audio?.optString("link") ?: "").ifEmpty { video.optString("link") }
            val videoUrl = video.optString("link")
            if (audioUrl.isEmpty()) continue
            val artists = ArrayList<ArtistRef>()
            val sa = song?.optJSONArray("artists")
            if (sa != null) for (j in 0 until sa.length()) {
                val ar = sa.optJSONObject(j) ?: continue
                artists.add(ArtistRef(ar.optInt("id"), ar.optString("name"), ar.optString("slug").ifEmpty { null }))
            }
            out.add(
                Track(
                    id = "${theme.optInt("id")}:${entry.optInt("id")}:${video.optInt("id")}",
                    themeId = theme.optInt("id"),
                    themeSlug = theme.optString("slug"),
                    type = parseType(theme.optString("type")),
                    sequence = if (theme.isNull("sequence")) null else theme.optInt("sequence"),
                    title = song?.optString("title")?.ifEmpty { null } ?: theme.optString("slug"),
                    artists = artists,
                    anime = ref,
                    cover = cover,
                    coverSmall = coverSmall,
                    audioUrl = audioUrl,
                    videoUrl = videoUrl,
                    resolution = video.optInt("resolution", -1).takeIf { it > 0 },
                    tags = video.optString("tags").ifEmpty { null },
                    version = entry.optInt("version", -1).takeIf { it > 0 },
                    episodes = entry.optString("episodes").ifEmpty { null },
                    nsfw = entry.optBoolean("nsfw", false),
                    spoiler = entry.optBoolean("spoiler", false),
                    source = "primary",
                )
            )
            if (!allVersions) break
        }
        return out
    }

    private fun parseType(s: String): ThemeType = when (s.uppercase()) {
        "OP" -> ThemeType.OP
        "ED" -> ThemeType.ED
        else -> ThemeType.IN
    }

    private fun themePrimaryTrack(theme: JSONObject, anime: JSONObject, song: JSONObject? = null): Track? =
        themeToTracks(theme, anime, song, false).firstOrNull()

    private fun themesToTracks(themes: JSONArray?, limit: Int = Int.MAX_VALUE): List<Track> {
        val out = ArrayList<Track>()
        if (themes == null) return out
        for (i in 0 until themes.length()) {
            val th = themes.optJSONObject(i) ?: continue
            val anime = th.optJSONObject("anime") ?: continue
            themePrimaryTrack(th, anime)?.let { out.add(it) }
            if (out.size >= limit) break
        }
        return out
    }

    private fun sortThemes(themes: JSONArray?): List<JSONObject> {
        val list = ArrayList<JSONObject>()
        if (themes != null) for (i in 0 until themes.length()) themes.optJSONObject(i)?.let { list.add(it) }
        val order = mapOf("OP" to 0, "ED" to 1)
        return list.sortedWith(
            compareBy({ order[it.optString("type").uppercase()] ?: 2 },
                { if (it.isNull("sequence")) 999 else it.optInt("sequence") },
                { it.optString("slug") })
        )
    }

    private fun animeToTracks(a: JSONObject, allVersions: Boolean = false): List<Track> {
        val out = ArrayList<Track>()
        for (th in sortThemes(a.optJSONArray("animethemes"))) {
            if (allVersions) out.addAll(themeToTracks(th, a, null, true))
            else themePrimaryTrack(th, a)?.let { out.add(it) }
        }
        return out
    }

    /* ------------------- MAL id enrichment ------------------- */

    private val idCache = ConcurrentHashMap<String, Pair<Int?, Int?>>()

    private suspend fun resolveIds(slugs: List<String>): Map<String, Pair<Int?, Int?>> {
        val need = slugs.distinct().filter { it.isNotEmpty() && !idCache.containsKey(it) }
        for (chunkStart in need.indices step 80) {
            val chunk = need.subList(chunkStart, minOf(chunkStart + 80, need.size))
            try {
                val u = url("/anime", mapOf(
                    "filter[slug]" to chunk.joinToString(","),
                    "include" to "resources",
                    "fields[anime]" to "id,slug",
                    "fields[resource]" to "site,link,external_id",
                    "page[size]" to 100,
                ))
                val d = http.getJson(u, HttpCachePolicy.default().fresh(30 * DAY_MS).maxAge(90 * DAY_MS).retries(1))
                val arr = d.optJSONArray("anime")
                if (arr != null) for (i in 0 until arr.length()) {
                    val a = arr.optJSONObject(i) ?: continue
                    idCache[a.optString("slug")] = (externalId(a, SITE_MAL) to externalId(a, SITE_AL))
                }
                chunk.forEach { idCache.putIfAbsent(it, (null to null)) }
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
            }
        }
        val outMap = LinkedHashMap<String, Pair<Int?, Int?>>()
        for (sl in slugs) {
            val v = idCache[sl]
            if (v != null) outMap[sl] = v
        }
        return outMap
    }

    suspend fun attachIds(tracks: List<Track>): List<Track> {
        val missing = tracks.filter { it.anime.malId == null && it.anime.slug.isNotEmpty() }.map { it.anime.slug }
        if (missing.isEmpty()) return tracks
        val map = resolveIds(missing)
        val out = tracks.map { t ->
            val withId = if (t.anime.malId != null) t
            else {
                val v = map[t.anime.slug]
                t.copy(anime = t.anime.copy(malId = v?.first, anilistId = v?.second))
            }
            if (withId.anime.ruName != null) withId
            else withId.copy(anime = withId.anime.copy(ruName = meta.ruNameOf(withId.anime.malId)))
        }
        meta.warmNow(out.mapNotNull { it.anime.malId })
        return out.map { t ->
            if (t.anime.ruName != null) t
            else t.copy(anime = t.anime.copy(ruName = meta.ruNameOf(t.anime.malId)))
        }
    }

    /* ----------------------- public API ----------------------- */

    suspend fun searchAll(q: String): SearchResults {
        val d = http.getJson(url("/search", mapOf(
            "q" to q,
            "fields[search]" to "anime,animethemes,artists",
            "page[limit]" to 20,
            "include[anime]" to ANIME_LIST_INCLUDE,
            "include[animetheme]" to THEME_INCLUDE,
            "include[artist]" to "images",
        ) + F), HttpCachePolicy.default().fresh(30 * MINUTE_MS).maxAge(DAY_MS))
        val search = d.optJSONObject("search") ?: JSONObject()
        val animeArr = search.optJSONArray("anime")
        val anime = ArrayList<AnimeSummary>()
        if (animeArr != null) for (i in 0 until animeArr.length()) animeArr.optJSONObject(i)?.let { anime.add(toAnimeSummary(it)) }
        val tracks = themesToTracks(search.optJSONArray("animethemes"))
        val known = anime.associateBy { it.slug }
        val merged = tracks.map { t ->
            val a = known[t.anime.slug]
            if (a != null) t.copy(anime = t.anime.copy(malId = a.malId ?: t.anime.malId))
            else t
        }
        val artistsArr = search.optJSONArray("artists")
        val artists = ArrayList<ArtistSummary>()
        if (artistsArr != null) for (i in 0 until artistsArr.length()) {
            val ar = artistsArr.optJSONObject(i) ?: continue
            val images = ar.optJSONArray("images")
            artists.add(ArtistSummary(ar.optInt("id"), ar.optString("name"), ar.optString("slug"), pickImage(images, "Large Cover"), pickImage(images, "Small Cover")))
        }
        return SearchResults(anime, attachIds(merged), artists)
    }

    suspend fun getAnime(slug: String): AnimeDetail {
        val d = http.getJson(url("/anime/${java.net.URLEncoder.encode(slug, "UTF-8")}", mapOf(
            "include" to "$ANIME_THEMES_INCLUDE,studios,series",
            "fields[anime]" to "id,name,slug,year,season,media_format,synopsis",
            "fields[studio]" to "name,slug",
            "fields[series]" to "name,slug",
        ) + FR), HttpCachePolicy.default().fresh(6 * HOUR_MS).maxAge(30 * DAY_MS))
        val a = d.optJSONObject("anime") ?: throw ApiException("Не найдено", 404)
        val summary = toAnimeSummary(a)
        val studios = a.optJSONArray("studios").names()
        val series = a.optJSONArray("series").names()
        return AnimeDetail(
            summary = summary,
            synopsis = a.optString("synopsis").ifEmpty { null },
            shikiDescription = null, score = null, kind = null, episodes = null, status = null,
            genres = emptyList(), studios = studios, series = series,
            tracks = animeToTracks(a, true),
        )
    }

    suspend fun getArtist(slug: String): ArtistDetail {
        val d = http.getJson(url("/artist/${java.net.URLEncoder.encode(slug, "UTF-8")}", mapOf(
            "include" to "images,songs.artists,songs.animethemes.anime.images,songs.animethemes.animethemeentries.videos.audio",
            "fields[artist]" to "id,name,slug,information",
        ) + F), HttpCachePolicy.default().fresh(6 * HOUR_MS).maxAge(30 * DAY_MS))
        val ar = d.optJSONObject("artist") ?: throw ApiException("Не найдено", 404)
        val images = ar.optJSONArray("images")
        val tracks = ArrayList<Track>()
        val seen = HashSet<String>()
        val songs = ar.optJSONArray("songs")
        if (songs != null) for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val ths = song.optJSONArray("animethemes") ?: continue
            for (j in 0 until ths.length()) {
                val th = ths.optJSONObject(j) ?: continue
                val anime = th.optJSONObject("anime") ?: continue
                val songArtists = song.optJSONArray("artists")
                val t = themePrimaryTrack(th, anime, song)
                if (t != null && seen.add(t.id)) {
                    tracks.add(if (songArtists == null || songArtists.length() == 0) t.copy(artists = listOf(ArtistRef(ar.optInt("id"), ar.optString("name"), slug))) else t)
                }
            }
        }
        val sorted = tracks.sortedByDescending { it.anime.year ?: 0 }
        return ArtistDetail(
            summary = ArtistSummary(ar.optInt("id"), ar.optString("name"), slug, pickImage(images, "Large Cover"), pickImage(images, "Small Cover")),
            information = ar.optString("information").ifEmpty { null },
            tracks = attachIds(sorted),
        )
    }

    suspend fun getRandomTracks(count: Int = 20, type: String? = null): List<Track> {
        val d = http.getJson(url("/animetheme", buildMap {
            put("sort", "random")
            put("page[size]", minOf(100, count))
            put("include", THEME_INCLUDE)
            if (type != null) put("filter[type]", type)
            put("filter[has]", "animethemeentries.videos")
            putAll(F)
        }), HttpCachePolicy.default().fresh(0).maxAge(2 * DAY_MS).noStore().ttl(0))
        return attachIds(themesToTracks(d.optJSONArray("animethemes"), count))
    }

    suspend fun getLatestTracks(count: Int = 20): List<Track> {
        val d = http.getJson(url("/animetheme", buildMap {
            put("sort", "-id")
            put("page[size]", minOf(100, count + 6))
            put("include", THEME_INCLUDE)
            put("filter[has]", "animethemeentries.videos")
            putAll(F)
        }), HttpCachePolicy.default().fresh(15 * MINUTE_MS).maxAge(3 * DAY_MS))
        return attachIds(themesToTracks(d.optJSONArray("animethemes"), count))
    }

    suspend fun getFreshTracks(period: String = "all", count: Int = 28): List<Track> {
        val since = java.util.Calendar.getInstance()
        when (period) {
            "today" -> {
                since.set(java.util.Calendar.HOUR_OF_DAY, 0)
                since.set(java.util.Calendar.MINUTE, 0)
                since.set(java.util.Calendar.SECOND, 0)
                since.set(java.util.Calendar.MILLISECOND, 0)
            }
            "week" -> since.add(java.util.Calendar.DAY_OF_YEAR, -7)
        }
        val params = buildMap {
            put("sort", "-id")
            put("page[size]", minOf(100, if (period == "all") count else count * 3))
            put("include", THEME_INCLUDE)
            put("filter[has]", "animethemeentries.videos")
            if (period != "all") {
                put("filter[created_at-gt]", isoLocal(since.timeInMillis))
            }
            putAll(F)
        }
        suspend fun load(): List<Track> {
            val d = http.getJson(url("/animetheme", params), HttpCachePolicy.default()
                .fresh(if (period == "today") 10 * MINUTE_MS else HOUR_MS).maxAge(3 * DAY_MS))
            return attachIds(themesToTracks(d.optJSONArray("animethemes"), count))
        }
        if (period == "all") return load()
        val tracks = load()
        return if (tracks.isNotEmpty()) tracks else getFreshTracks("all", count)
    }

    private fun isoLocal(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getDefault()
        return sdf.format(java.util.Date(ms))
    }

    suspend fun getSeasonAnime(year: Int, season: String?, page: Int = 1): Paged<AnimeSummary> {
        val d = http.getJson(url("/anime", buildMap {
            put("filter[year]", year)
            if (season != null) put("filter[season]", season)
            put("filter[has]", "animethemes")
            put("include", ANIME_LIST_INCLUDE)
            put("sort", "name")
            put("page[size]", 30)
            put("page[number]", page)
            putAll(FR)
        }), HttpCachePolicy.default().fresh(6 * HOUR_MS).maxAge(30 * DAY_MS))
        val arr = d.optJSONArray("anime")
        val items = ArrayList<AnimeSummary>()
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { items.add(toAnimeSummary(it)) }
        val hasMore = d.optJSONObject("links")?.opt("next") != null && !d.optJSONObject("links").isNull("next")
        return Paged(items, hasMore, page)
    }

    suspend fun getSeasonTracks(year: Int, season: String?): List<Track> {
        val d = http.getJson(url("/anime", buildMap {
            put("filter[year]", year)
            if (season != null) put("filter[season]", season)
            put("filter[has]", "animethemes")
            put("include", ANIME_THEMES_INCLUDE)
            put("sort", "name")
            put("page[size]", 60)
            putAll(FR)
        }), HttpCachePolicy.default().fresh(6 * HOUR_MS).maxAge(30 * DAY_MS))
        val arr = d.optJSONArray("anime") ?: return emptyList()
        val out = ArrayList<Track>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.addAll(animeToTracks(it, false)) }
        return out
    }

    suspend fun getTracksForAnimeSlugs(slugs: List<String>): List<Track> {
        if (slugs.isEmpty()) return emptyList()
        val d = http.getJson(url("/anime", buildMap {
            put("filter[slug]", slugs.joinToString(","))
            put("include", ANIME_THEMES_INCLUDE)
            put("page[size]", 100)
            putAll(FR)
        }), HttpCachePolicy.default().fresh(DAY_MS).maxAge(30 * DAY_MS))
        val arr = d.optJSONArray("anime") ?: return emptyList()
        val bySlug = HashMap<String, JSONObject>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            bySlug[a.optString("slug")] = a
        }
        val out = ArrayList<Track>()
        for (s in slugs) bySlug[s]?.let { out.addAll(animeToTracks(it, false)) }
        return out
    }

    suspend fun getAnimeBySlugs(slugs: List<String>): List<AnimeSummary> {
        if (slugs.isEmpty()) return emptyList()
        val d = http.getJson(url("/anime", buildMap {
            put("filter[slug]", slugs.joinToString(","))
            put("include", ANIME_LIST_INCLUDE)
            put("page[size]", 100)
            putAll(FR)
        }), HttpCachePolicy.default().fresh(DAY_MS).maxAge(30 * DAY_MS))
        val arr = d.optJSONArray("anime") ?: return emptyList()
        val map = HashMap<String, AnimeSummary>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { map[it.optString("slug")] = toAnimeSummary(it) }
        return slugs.mapNotNull { map[it] }
    }

    suspend fun getArtistsBySlugs(slugs: List<String>): List<ArtistSummary> {
        if (slugs.isEmpty()) return emptyList()
        val d = http.getJson(url("/artist", buildMap {
            put("filter[slug]", slugs.joinToString(","))
            put("include", "images")
            put("page[size]", 100)
            putAll(F)
        }), HttpCachePolicy.default().fresh(DAY_MS).maxAge(30 * DAY_MS))
        val arr = d.optJSONArray("artists") ?: return emptyList()
        val map = HashMap<String, ArtistSummary>()
        for (i in 0 until arr.length()) {
            val ar = arr.optJSONObject(i) ?: continue
            val images = ar.optJSONArray("images")
            map[ar.optString("slug")] = ArtistSummary(ar.optInt("id"), ar.optString("name"), ar.optString("slug"), pickImage(images, "Large Cover"), pickImage(images, "Small Cover"))
        }
        return slugs.mapNotNull { map[it] }
    }

    suspend fun getAnimeByMalIds(ids: List<Int>): List<AnimeSummary> {
        val clean = ids.distinct().filter { it > 0 }
        if (clean.isEmpty()) return emptyList()
        val d = http.getJson(url("/resource", mapOf(
            "filter[site]" to SITE_MAL,
            "filter[external_id]" to clean.joinToString(","),
            "include" to "anime",
            "page[size]" to 50,
        )), HttpCachePolicy.default().fresh(7 * DAY_MS).maxAge(30 * DAY_MS))
        val res = d.optJSONArray("resources")
        val slugByMal = HashMap<Int, String>()
        if (res != null) for (i in 0 until res.length()) {
            val r = res.optJSONObject(i) ?: continue
            val ext = if (r.isNull("external_id")) -1 else r.optInt("external_id", -1)
            val anime = r.optJSONArray("anime")?.optJSONObject(0) ?: continue
            if (ext > 0 && !slugByMal.containsKey(ext)) slugByMal[ext] = anime.optString("slug")
        }
        val slugs = clean.mapNotNull { slugByMal[it] }
        if (slugs.isEmpty()) return emptyList()
        val list = getAnimeBySlugs(slugs)
        val bySlug = list.associateBy { it.slug }
        return clean.mapNotNull { id ->
            val s = slugByMal[id]
            val a = s?.let { bySlug[it] }
            a?.let { if (it.malId == null) it.copy(malId = id) else it }
        }
    }
}

private fun JSONArray?.names(): List<String> {
    val out = ArrayList<String>()
    if (this == null) return out
    for (i in 0 until length()) optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
    return out
}
