package com.kamisakyy.nanajoxkmama.core.network

import com.kamisakyy.nanajoxkmama.core.common.ApiException
import com.kamisakyy.nanajoxkmama.core.common.ApiResult
import com.kamisakyy.nanajoxkmama.core.common.Curated
import com.kamisakyy.nanajoxkmama.core.common.currentSeasonSite
import com.kamisakyy.nanajoxkmama.core.common.runApi
import com.kamisakyy.nanajoxkmama.core.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

data class HomeFeed(
    val fresh: List<Track>,
    val random: List<Track>,
    val latest: List<Track>,
    val season: List<Track>,
    val mixes: List<Curated.Mix>,
    val mixCovers: Map<String, String?>,
    val decadeCovers: Map<Int, String?>,
)

/**
 * Website content aggregator: AnimeThemes (primary) + AnisongDB (extras) +
 * Shikimori/AniList (RU meta) — same merge order as the site.
 */
@Singleton
class ContentRepository @Inject constructor(
    private val animethemes: AnimeThemesApi,
    private val anisongdb: AnisongDbApi,
    val meta: MetaApi,
) {
    /* ------------------------------ Home ------------------------------ */

    suspend fun home(period: String, extras: Boolean): ApiResult<HomeFeed> = runApi {
        coroutineScope {
            val fresh = async { animethemes.getFreshTracks(period, 28) }
            val latest = async { animethemes.getLatestTracks(20) }
            val random = async {
                val primary = animethemes.getRandomTracks(if (extras) 10 else 20, "OP")
                val extra = if (extras) anisongdb.random().shuffled().take(10) else emptyList()
                (primary + extra).shuffled().take(20)
            }
            val season = async {
                val (y, s) = currentSeasonSite()
                animethemes.getSeasonTracks(y, s).shuffled().take(28)
            }
            val mixCovers = async {
                Curated.MIXES.associate { m ->
                    val a = animethemes.getAnimeBySlugs(m.slugs.take(1)).firstOrNull()
                    m.id to (a?.cover ?: a?.coverSmall)
                }
            }
            val decadeCovers = async {
                Curated.DECADES.associateWith { y ->
                    animethemes.getSeasonTracks(y, null).firstOrNull()?.cover
                        ?: animethemes.getSeasonAnime(y, null, 1).items.firstOrNull()?.cover
                }
            }
            HomeFeed(
                fresh = fresh.await(),
                random = random.await(),
                latest = latest.await(),
                season = season.await(),
                mixes = Curated.MIXES,
                mixCovers = mixCovers.await(),
                decadeCovers = decadeCovers.await(),
            )
        }
    }

    /* ------------------------------ Search ------------------------------ */

    suspend fun search(q: String, extras: Boolean): ApiResult<SearchResults> = runApi {
        coroutineScope {
            val primary = async { animethemes.searchAll(q) }
            val extraTracks = async {
                if (extras) {
                    try { anisongdb.search(q) } catch (_: Exception) { emptyList() }
                } else emptyList()
            }
            val p = primary.await()
            val merged = (p.tracks + extraTracks.await())
                .distinctBy { t -> t.id }
            p.copy(tracks = merged)
        }
    }

    /* ------------------------------ Anime ------------------------------ */

    suspend fun animeDetail(slug: String, extras: Boolean): ApiResult<AnimeDetail> = runApi {
        val base = animethemes.getAnime(slug)
        var detail = base
        val malId = base.summary.malId
        val names = listOfNotNull(base.summary.name, base.summary.ruName)
        val extraTracks = if (extras) {
            try {
                anisongdb.forAnime(malId, names).filter { x ->
                    base.tracks.none { it.title.equals(x.title, true) && it.type == x.type }
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        var shiki: ShikiDetails? = null
        var al: AniListMeta? = null
        if (malId != null) {
            shiki = try { meta.fetchShikiDetails(malId) } catch (_: Exception) { null }
            al = meta.fetchAniList(listOf(malId))[malId]
        }
        detail = base.copy(
            synopsis = shiki?.description ?: base.synopsis,
            shikiDescription = shiki?.description,
            score = shiki?.score,
            kind = shiki?.kind?.let { meta.kindRu(it) } ?: base.summary.mediaFormat,
            episodes = shiki?.episodes,
            status = shiki?.status?.let { meta.statusRu(it) },
            genres = shiki?.genres ?: emptyList(),
            studios = if (base.studios.isNotEmpty()) base.studios else shiki?.studios ?: emptyList(),
            summary = base.summary.copy(
                ruName = shiki?.ru ?: base.summary.ruName,
                cover = al?.cover ?: base.summary.cover,
                coverSmall = base.summary.coverSmall ?: al?.cover,
                banner = al?.banner,
                color = al?.color,
                malId = base.summary.malId ?: shiki?.malId,
            ),
            tracks = (base.tracks + extraTracks),
        )
        detail
    }

    suspend fun artistDetail(slug: String): ApiResult<ArtistDetail> = runApi {
        animethemes.getArtist(slug)
    }

    suspend fun random(extras: Boolean, count: Int = 24): ApiResult<List<Track>> = runApi {
        coroutineScope {
            val p = async { animethemes.getRandomTracks(count / 2 + 2, "OP") }
            val e = async { if (extras) anisongdb.random() else emptyList() }
            (p.await() + e.await()).shuffled().take(count)
        }
    }

    suspend fun mix(slug: String, extras: Boolean): ApiResult<List<Track>> = runApi {
        val m = Curated.MIXES.firstOrNull { it.id == slug } ?: throw ApiException("Микс не найден")
        val primary = animethemes.getTracksForAnimeSlugs(m.slugs)
        val covers = animethemes.getAnimeBySlugs(m.slugs).associate { it.slug to it.malId }
        val extra = if (extras) {
            val withMal = m.slugs.mapNotNull { s -> covers[s]?.let { it to s } }
            withMal.flatMap { (mal, _) ->
                try { anisongdb.forAnime(mal, emptyList()) } catch (_: Exception) { emptyList() }
            }
        } else emptyList()
        (primary + extra).distinctBy { it.id }
    }

    suspend fun decade(year: Int): ApiResult<List<Track>> = runApi {
        animethemes.getSeasonTracks(year, null).shuffled().take(48)
    }

    suspend fun season(year: Int, season: String?, page: Int): ApiResult<Paged<AnimeSummary>> = runApi {
        animethemes.getSeasonAnime(year, season, page)
    }

    suspend fun seasonPlay(year: Int, season: String, extras: Boolean): ApiResult<List<Track>> = runApi {
        val primary = animethemes.getSeasonTracks(year, season)
        if (!extras) return@runApi primary
        val withMal = primary.mapNotNull { t -> t.anime.malId }.distinct().take(8)
        val extra = withMal.flatMap { mal ->
            try { anisongdb.forAnime(mal, emptyList()).take(4) } catch (_: Exception) { emptyList() }
        }
        (primary + extra).distinctBy { it.id }
    }

    suspend fun tracksForAnime(malId: Int?, names: List<String>, extras: Boolean): ApiResult<List<Track>> = runApi {
        if (!extras) return@runApi emptyList()
        anisongdb.forAnime(malId, names)
    }
}
