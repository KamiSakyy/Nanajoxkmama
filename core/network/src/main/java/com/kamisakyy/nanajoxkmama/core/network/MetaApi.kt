package com.kamisakyy.nanajoxkmama.core.network

import com.kamisakyy.nanajoxkmama.core.common.DAY_MS
import com.kamisakyy.nanajoxkmama.core.common.HOUR_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ShikiHit(
    val malId: Int,
    val name: String,
    val ru: String?,
    val poster: String?,
    val kind: String?,
    val score: Double?,
    val episodes: Int?,
    val status: String?,
    val rating: String?,
)

data class ShikiDetails(
    val malId: Int,
    val name: String,
    val ru: String?,
    val poster: String?,
    val description: String?,
    val score: Double?,
    val kind: String?,
    val episodes: Int?,
    val status: String?,
    val genres: List<String>,
    val studios: List<String>,
    val url: String?,
)

data class AniListMeta(val banner: String?, val color: Int?, val cover: String?)

/**
 * Russian metadata — website `src/api/meta.ts` port:
 *  • Shikimori — Russian titles, score, kind, description, genres (MAL id == Shikimori id)
 *  • AniList   — extraLarge posters, banners, accent colour
 *
 * Shikimori mirrors get blocked periodically (shikimori.tv is home since Jan 2026).
 * We race all known mirrors once, remember the winner, and re-probe on failure.
 */
@Singleton
class MetaApi @Inject constructor(
    private val http: HttpEngine,
) {
    companion object {
        private val SHIKI_HOSTS = listOf(
            "https://shikimori.tv",
            "https://shikimori.one",
            "https://shikimori.io",
            "https://shikimori.me",
        )
        private val SHIKI_RE = Regex("^https?://([a-z0-9-]+\\.)?shikimori\\.(one|io|me|org|tv|cc)", RegexOption.IGNORE_CASE)
        const val ANILIST = "https://graphql.anilist.co"

        val KIND_RU = mapOf(
            "tv" to "TV-сериал", "movie" to "Фильм", "ova" to "OVA", "ona" to "ONA",
            "special" to "Спешл", "music" to "Клип", "tv_13" to "TV-сериал", "tv_24" to "TV-сериал",
            "tv_4" to "TV-сериал", "tv_special" to "TV-спецвыпуск", "music_video" to "Клип",
        )
        val STATUS_RU = mapOf(
            "anons" to "Анонс", "ongoing" to "Онгоинг", "released" to "Вышел",
        )
    }

    @Volatile private var shikiHost: String? = null
    @Volatile private var shikiDead = false
    private val detailsCache = ConcurrentHashMap<Int, ShikiDetails>()
    private val warmScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private suspend fun ping(host: String): String? = withTimeoutOrNull(6000) {
        try {
            val arr = http.getArray("$host/api/animes?limit=1", HttpCachePolicy.default().ttl(0).noStore().timeout(5000).retries(0))
            if (arr is JSONArray) host else null
        } catch (_: Exception) { null }
    }

    /** First mirror that answers wins (website resolveShikiHost). */
    suspend fun resolveShikiHost(force: Boolean = false): String? {
        if (!force) {
            shikiHost?.let { return it }
            if (shikiDead) return null
        }
        val winner = coroutineScope {
            SHIKI_HOSTS.map { h -> async { ping(h) } }.awaitAll().firstOrNull { it != null }
        }
        shikiHost = winner
        shikiDead = winner == null
        return winner
    }

    /** Rewrite any shikimori.* URL to the currently reachable mirror. */
    fun fixShikiUrl(url: String?): String? {
        if (url == null || url.isEmpty()) return null
        val host = shikiHost ?: return if (url.startsWith("/")) null else url
        if (url.startsWith("/")) return host + url
        val m = SHIKI_RE.find(url) ?: return url
        val tld = host.substringAfterLast('.')
        return SHIKI_RE.replace(url, "https://${m.groupValues[1]}shikimori.$tld")
    }

    private suspend fun shikiRaw(path: String, fresh: Long, maxAge: Long): String {
        val host = resolveShikiHost() ?: throw java.io.IOException("Shikimori недоступен")
        return try {
            http.getString(host + path, HttpCachePolicy.default().cacheKey("shiki:$path").fresh(fresh).maxAge(maxAge).timeout(9000).retries(0))
        } catch (e: java.io.IOException) {
            val next = resolveShikiHost(true)
            if (next != null && next != host) {
                http.getString(next + path, HttpCachePolicy.default().cacheKey("shiki:$path").fresh(fresh).maxAge(maxAge).timeout(9000).retries(0))
            } else throw e
        }
    }

    private suspend fun shikiRequest(path: String, fresh: Long, maxAge: Long): JSONArray {
        val raw = shikiRaw(path, fresh, maxAge).trim()
        return if (raw.startsWith("[")) JSONArray(raw) else JSONArray().put(JSONObject(raw))
    }

    suspend fun searchShikimori(q: String): List<ShikiHit> {
        val arr = shikiRequest("/api/animes?limit=12&search=${java.net.URLEncoder.encode(q, "UTF-8")}", HOUR_MS, 7 * DAY_MS)
        val out = ArrayList<ShikiHit>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", -1)
            if (id <= 0) continue
            val image = o.optJSONObject("image")
            out.add(
                ShikiHit(
                    malId = id,
                    name = o.optString("name"),
                    ru = o.optString("russian").ifEmpty { null },
                    poster = fixShikiUrl(image?.optString("original")?.ifEmpty { image.optString("preview") }),
                    kind = o.optString("kind").ifEmpty { null },
                    score = o.optString("score").toDoubleOrNull(),
                    episodes = o.optInt("episodes", -1).takeIf { it > 0 },
                    status = o.optString("status").ifEmpty { null },
                    rating = o.optString("rating").ifEmpty { null },
                )
            )
        }
        return out
    }

    suspend fun fetchShikiDetails(malId: Int): ShikiDetails {
        detailsCache[malId]?.let { return it }
        val arr = shikiRequest("/api/animes/$malId", 7 * DAY_MS, 60 * DAY_MS)
        val o = arr.optJSONObject(0) ?: JSONObject()
        val genres = ArrayList<String>()
        o.optJSONArray("genres")?.let { g ->
            for (i in 0 until g.length()) {
                val g0 = g.optJSONObject(i) ?: continue
                genres.add(g0.optString("russian").ifEmpty { g0.optString("name") })
            }
        }
        val studios = ArrayList<String>()
        o.optJSONArray("studios")?.let { g ->
            for (i in 0 until g.length()) g.optJSONObject(i)?.optString("name")?.takeIf { it.isNotEmpty() }?.let { studios.add(it) }
        }
        val details = ShikiDetails(
            malId = malId,
            name = o.optString("name"),
            ru = o.optString("russian").ifEmpty { null },
            poster = fixShikiUrl(o.optJSONObject("image")?.optString("original")),
            description = cleanShikiText(o.optString("description").ifEmpty { null }),
            score = o.optString("score").toDoubleOrNull(),
            kind = o.optString("kind").ifEmpty { null },
            episodes = o.optInt("episodes", -1).takeIf { it > 0 },
            status = o.optString("status").ifEmpty { null },
            genres = genres,
            studios = studios,
            url = fixShikiUrl(o.optString("url").ifEmpty { null }),
        )
        detailsCache[malId] = details
        return details
    }

    suspend fun fetchAniList(malIds: List<Int>): Map<Int, AniListMeta> {
        if (malIds.isEmpty()) return emptyMap()
        val ids = malIds.distinct().take(50)
        val query = "{${ids.joinToString(",") { "a$it:Media(idMal:$it,type:ANIME){id bannerImage coverImage{extraLarge large} color}" }}}"
        val body = JSONObject().put("query", query).toString()
        return try {
            val d = http.getJson(ANILIST, HttpCachePolicy.default()
                .body(body)
                .cacheKey("anilist:" + ids.sorted().joinToString(","))
                .fresh(7 * DAY_MS).maxAge(60 * DAY_MS).timeout(12_000))
            val data = d.optJSONObject("data") ?: return emptyMap()
            val out = HashMap<Int, AniListMeta>()
            for (id in ids) {
                val m = data.optJSONObject("a$id") ?: continue
                val colorHex = m.optString("color").ifEmpty { null }
                val color = colorHex?.removePrefix("#")?.let { h -> h.toLongOrNull(16)?.toInt() }
                val coverObj = m.optJSONObject("coverImage")
                out[id] = AniListMeta(
                    banner = m.optString("bannerImage").ifEmpty { null },
                    color = color,
                    cover = coverObj?.optString("extraLarge")?.ifEmpty { coverObj.optString("large").ifEmpty { null } },
                )
            }
            out
        } catch (_: Exception) { emptyMap() }
    }

    fun warm(malIds: List<Int?>) {
        warmScope.launch {
            val ids = malIds.filterNotNull().distinct().take(24)
            ids.map { id -> async { runCatching { fetchShikiDetails(id) } } }.awaitAll()
            runCatching { fetchAniList(ids) }
        }
    }

    fun kindRu(kind: String?): String = if (kind == null) "" else KIND_RU[kind] ?: kind
    fun statusRu(status: String?): String = if (status == null) "" else STATUS_RU[status] ?: status

    private fun cleanShikiText(s: String?): String? {
        if (s == null) return null
        return s.replace(Regex("<[^>]*>"), "").replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}
