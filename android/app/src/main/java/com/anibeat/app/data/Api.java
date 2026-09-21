package com.anibeat.app.data;

import com.anibeat.app.core.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * AnimeThemes.moe — публичный REST API с OP/ED аудио и видео.
 * Полный перенос `src/api/animethemes.ts`.
 */
public final class Api {

    /** Склейка слагов через запятую. StringBuilder вместо String.join: String.join есть только с Android 8.0. */
    private static String csv(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private static String csv(String[] values) {
        if (values == null || values.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(values[i]);
        }
        return sb.toString();
    }

    public interface Cb<T> {
        void on(T value, String error);
    }

    public static final String BASE = "https://api.animethemes.moe";
    public static final String SITE_MAL = "MyAnimeList";
    public static final String SITE_AL = "AniList";

    private static final Map<String, String> F = new HashMap<>();
    private static final Map<String, String> FR = new HashMap<>();

    static {
        F.put("fields[anime]", "id,name,slug,year,season,media_format");
        F.put("fields[animetheme]", "id,slug,type,sequence");
        F.put("fields[song]", "id,title");
        F.put("fields[artist]", "id,name,slug");
        F.put("fields[animethemeentry]", "id,version,episodes,nsfw,spoiler");
        F.put("fields[video]", "id,link,resolution,tags,nc");
        F.put("fields[audio]", "id,link");
        F.put("fields[image]", "id,facet,link");
        FR.putAll(F);
        FR.put("fields[resource]", "site,link,external_id");
    }

    private static final String THEME_INCLUDE = "anime.images,song.artists,animethemeentries.videos.audio";
    private static final String ANIME_THEMES_INCLUDE = "images,resources,animethemes.song.artists,animethemes.animethemeentries.videos.audio";
    private static final String ANIME_LIST_INCLUDE = "images,resources";

    private Api() {
    }

    private static Map<String, String> params(Object... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) map.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        return map;
    }

    private static Map<String, String> fields(Map<String, String> base, Object... extra) {
        Map<String, String> map = new LinkedHashMap<>(base);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            if (extra[i + 1] != null) map.put(String.valueOf(extra[i]), String.valueOf(extra[i + 1]));
        }
        return map;
    }

    /* ------------------------------------------------------------------ */
    /* Мапперы                                                             */
    /* ------------------------------------------------------------------ */

    public static String pickImage(JSONArray images, String facet) {
        if (images == null || images.length() == 0) return null;
        String first = null;
        for (int i = 0; i < images.length(); i++) {
            JSONObject img = images.optJSONObject(i);
            if (img == null) continue;
            String link = img.optString("link", null);
            if (first == null) first = link;
            if (facet.equals(img.optString("facet"))) return link;
        }
        return first;
    }

    private static Integer externalId(JSONObject anime, String site) {
        JSONArray resources = anime.optJSONArray("resources");
        if (resources == null) return null;
        for (int i = 0; i < resources.length(); i++) {
            JSONObject r = resources.optJSONObject(i);
            if (r == null || !site.equals(r.optString("site"))) continue;
            if (r.has("external_id") && !r.isNull("external_id")) return r.optInt("external_id");
            String link = r.optString("link", "");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/(\\d+)/?$").matcher(link);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    public static Models.AnimeRef toAnimeRef(JSONObject a) {
        Models.AnimeRef ref = new Models.AnimeRef();
        ref.id = a.optLong("id");
        ref.name = a.optString("name");
        ref.slug = a.optString("slug");
        ref.year = a.has("year") && !a.isNull("year") ? a.optInt("year") : null;
        ref.season = a.has("season") && !a.isNull("season") ? a.optString("season") : null;
        if (a.has("resources")) {
            ref.malId = externalId(a, SITE_MAL);
            ref.anilistId = externalId(a, SITE_AL);
        }
        return ref;
    }

    public static Models.AnimeSummary toAnimeSummary(JSONObject a) {
        Models.AnimeSummary s = new Models.AnimeSummary();
        Models.AnimeRef ref = toAnimeRef(a);
        s.id = ref.id;
        s.name = ref.name;
        s.slug = ref.slug;
        s.year = ref.year;
        s.season = ref.season;
        s.malId = ref.malId;
        s.anilistId = ref.anilistId;
        s.cover = pickImage(a.optJSONArray("images"), "Large Cover");
        s.coverSmall = pickImage(a.optJSONArray("images"), "Small Cover");
        s.mediaFormat = a.isNull("media_format") ? null : a.optString("media_format", null);
        s.synopsis = a.isNull("synopsis") ? null : a.optString("synopsis", null);
        return s;
    }

    /** Лучшее видео записи: creditless, затем максимальное разрешение. */
    private static JSONObject bestVideo(JSONArray videos) {
        if (videos == null || videos.length() == 0) return null;
        List<JSONObject> withAudio = new ArrayList<>();
        List<JSONObject> all = new ArrayList<>();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v == null) continue;
            all.add(v);
            JSONObject audio = v.optJSONObject("audio");
            if (audio != null && audio.optString("link", "").length() > 0) withAudio.add(v);
        }
        List<JSONObject> pool = !withAudio.isEmpty() ? withAudio : all;
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        for (JSONObject v : pool) {
            int score = (v.optBoolean("nc") ? 1_000_000 : 0) + v.optInt("resolution");
            if (score > bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    private static List<Models.Track> themeToTracks(JSONObject theme, JSONObject anime, JSONObject songOverride) {
        List<Models.Track> out = new ArrayList<>();
        JSONObject song = songOverride != null ? songOverride : theme.optJSONObject("song");
        String cover = pickImage(anime.optJSONArray("images"), "Large Cover");
        String coverSmall = pickImage(anime.optJSONArray("images"), "Small Cover");
        Models.AnimeRef ref = toAnimeRef(anime);
        JSONArray entries = theme.optJSONArray("animethemeentries");
        if (entries == null) return out;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            JSONObject video = bestVideo(entry.optJSONArray("videos"));
            if (video == null) continue;
            Models.Track t = new Models.Track();
            t.themeId = theme.optLong("id");
            t.id = t.themeId + ":" + entry.optLong("id") + ":" + video.optLong("id");
            t.themeSlug = theme.optString("slug");
            t.type = theme.optString("type", "OP");
            t.sequence = theme.has("sequence") && !theme.isNull("sequence") ? theme.optInt("sequence") : null;
            String title = song != null ? song.optString("title", null) : null;
            t.title = (title == null || title.isEmpty()) ? t.themeSlug : title;
            if (song != null) {
                JSONArray artists = song.optJSONArray("artists");
                if (artists != null) {
                    for (int j = 0; j < artists.length(); j++) {
                        JSONObject ar = artists.optJSONObject(j);
                        if (ar == null) continue;
                        Models.ArtistRef artist = new Models.ArtistRef();
                        artist.id = ar.optLong("id");
                        artist.name = ar.optString("name");
                        artist.slug = ar.optString("slug");
                        t.artists.add(artist);
                    }
                }
            }
            t.anime = ref;
            t.cover = cover;
            t.coverSmall = coverSmall;
            JSONObject audio = video.optJSONObject("audio");
            String audioLink = audio != null ? audio.optString("link", null) : null;
            t.videoUrl = video.optString("link", "");
            t.audioUrl = audioLink != null && !audioLink.isEmpty() ? audioLink : t.videoUrl;
            t.resolution = video.has("resolution") && !video.isNull("resolution") ? video.optInt("resolution") : null;
            t.tags = video.optString("tags", "");
            t.version = entry.has("version") && !entry.isNull("version") ? entry.optInt("version") : null;
            t.episodes = entry.isNull("episodes") ? null : entry.optString("episodes", null);
            t.nsfw = entry.optBoolean("nsfw");
            t.spoiler = entry.optBoolean("spoiler");
            t.source = "primary";
            out.add(t);
        }
        return out;
    }

    public static List<Models.Track> animeToTracks(JSONObject anime, boolean allVersions) {
        List<Models.Track> out = new ArrayList<>();
        JSONArray themes = anime.optJSONArray("animethemes");
        if (themes == null) return out;
        List<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < themes.length(); i++) {
            JSONObject th = themes.optJSONObject(i);
            if (th != null) sorted.add(th);
        }
        sorted.sort((a, b) -> {
            int oa = order(a.optString("type"));
            int ob = order(b.optString("type"));
            if (oa != ob) return oa - ob;
            int sa = a.has("sequence") && !a.isNull("sequence") ? a.optInt("sequence") : 0;
            int sb = b.has("sequence") && !b.isNull("sequence") ? b.optInt("sequence") : 0;
            if (sa != sb) return sa - sb;
            return a.optString("slug").compareTo(b.optString("slug"));
        });
        for (JSONObject th : sorted) {
            if (allVersions) out.addAll(themeToTracks(th, anime, null));
            else {
                List<Models.Track> list = themeToTracks(th, anime, null);
                if (!list.isEmpty()) out.add(list.get(0));
            }
        }
        return out;
    }

    private static int order(String type) {
        if ("OP".equals(type)) return 0;
        if ("ED".equals(type)) return 1;
        return 2;
    }

    private static List<Models.Track> themesToTracks(JSONArray themes, int limit) {
        List<Models.Track> out = new ArrayList<>();
        if (themes == null) return out;
        for (int i = 0; i < themes.length(); i++) {
            JSONObject th = themes.optJSONObject(i);
            if (th == null) continue;
            JSONObject anime = th.optJSONObject("anime");
            if (anime == null) continue;
            List<Models.Track> list = themeToTracks(th, anime, null);
            if (!list.isEmpty()) out.add(list.get(0));
            if (out.size() >= limit) break;
        }
        return out;
    }

    /* ------------------------------------------------------------------ */
    /* Обогащение MAL-id                                                   */
    /* ------------------------------------------------------------------ */

    private static final Map<String, int[]> ID_CACHE = new HashMap<>();

    /** Разрешает MyAnimeList/AniList id для слагов и прикрепляет их к трекам. */
    public static void attachIds(List<Models.Track> tracks, Runnable done) {
        List<String> need = new ArrayList<>();
        for (Models.Track t : tracks) {
            if (t.anime.malId == null && !need.contains(t.anime.slug)) need.add(t.anime.slug);
        }
        if (need.isEmpty()) {
            if (done != null) done.run();
            return;
        }
        resolveIds(need, (map, err) -> {
            for (Models.Track t : tracks) {
                if (t.anime.malId != null) continue;
                int[] ids = map.get(t.anime.slug);
                t.anime.malId = ids != null && ids[0] > 0 ? ids[0] : null;
                t.anime.anilistId = ids != null && ids[1] > 0 ? ids[1] : null;
            }
            List<Integer> warm = new ArrayList<>();
            for (Models.Track t : tracks) warm.add(t.anime.malId);
            Meta.warm(warm);
            if (done != null) done.run();
        });
    }

    public static void resolveIds(List<String> slugs, Cb<Map<String, int[]>> cb) {
        List<String> need = new ArrayList<>();
        for (String s : slugs) if (s != null && !s.isEmpty() && !ID_CACHE.containsKey(s) && !need.contains(s)) need.add(s);
        if (need.isEmpty()) {
            Map<String, int[]> out = new HashMap<>();
            for (String s : slugs) if (ID_CACHE.containsKey(s)) out.put(s, ID_CACHE.get(s));
            cb.on(out, null);
            return;
        }
        List<String> chunk = need.subList(0, Math.min(80, need.size()));
        String url = Net.buildUrl(BASE, "/anime", params(
                "filter[slug]", csv(chunk),
                "include", "resources",
                "fields[anime]", "id,slug",
                "fields[resource]", "site,link,external_id",
                "page[size]", 100));
        Net.getLow(url, 30 * Net.DAY, 90 * Net.DAY, (json, error) -> {
            JSONArray anime = Net.arr(json, "anime");
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a == null) continue;
                Integer mal = externalId(a, SITE_MAL);
                Integer al = externalId(a, SITE_AL);
                ID_CACHE.put(a.optString("slug"), new int[]{mal == null ? -1 : mal, al == null ? -1 : al});
            }
            for (String s : chunk) if (!ID_CACHE.containsKey(s)) ID_CACHE.put(s, new int[]{-1, -1});
            Map<String, int[]> out = new HashMap<>();
            for (String s : slugs) if (ID_CACHE.containsKey(s)) out.put(s, ID_CACHE.get(s));
            cb.on(out, error);
        });
    }

    /* ------------------------------------------------------------------ */
    /* Публичные методы                                                    */
    /* ------------------------------------------------------------------ */

    public static void searchAll(String query, Cb<Models.SearchResults> cb) {
        String url = Net.buildUrl(BASE, "/search", fields(params(
                "q", query,
                "fields[search]", "anime,animethemes,artists",
                "page[limit]", 12,
                "include[anime]", ANIME_LIST_INCLUDE,
                "include[animetheme]", THEME_INCLUDE,
                "include[artist]", "images"), F));
        Net.get(url, 30 * Net.MIN, Net.DAY, (json, error) -> {
            if (json == null) {
                cb.on(null, error);
                return;
            }
            JSONObject search = json.optJSONObject("search");
            Models.SearchResults results = new Models.SearchResults();
            if (search != null) {
                JSONArray anime = search.optJSONArray("anime");
                if (anime != null) for (int i = 0; i < anime.length(); i++) {
                    JSONObject a = anime.optJSONObject(i);
                    if (a != null) results.anime.add(toAnimeSummary(a));
                }
                results.tracks = themesToTracks(search.optJSONArray("animethemes"), 100);
                Map<String, Models.AnimeSummary> known = new HashMap<>();
                for (Models.AnimeSummary a : results.anime) known.put(a.slug, a);
                for (Models.Track t : results.tracks) {
                    Models.AnimeSummary a = known.get(t.anime.slug);
                    if (a != null && a.malId != null) {
                        t.anime.malId = a.malId;
                        t.anime.anilistId = a.anilistId;
                    }
                }
                JSONArray artists = search.optJSONArray("artists");
                if (artists != null) for (int i = 0; i < artists.length(); i++) {
                    JSONObject ar = artists.optJSONObject(i);
                    if (ar != null) results.artists.add(toArtistSummary(ar));
                }
            }
            attachIds(results.tracks, () -> cb.on(results, error));
        });
    }

    public static Models.ArtistSummary toArtistSummary(JSONObject ar) {
        Models.ArtistSummary s = new Models.ArtistSummary();
        s.id = ar.optLong("id");
        s.name = ar.optString("name");
        s.slug = ar.optString("slug");
        s.image = pickImage(ar.optJSONArray("images"), "Large Cover");
        s.imageSmall = pickImage(ar.optJSONArray("images"), "Small Cover");
        return s;
    }

    public static void getAnime(String slug, Cb<Models.AnimeDetail> cb) {
        String url = Net.buildUrl(BASE, "/anime/" + Net.encode(slug), fields(params(
                "include", ANIME_THEMES_INCLUDE + ",studios,series",
                "fields[anime]", "id,name,slug,year,season,media_format,synopsis",
                "fields[studio]", "name,slug",
                "fields[series]", "name,slug"), FR));
        Net.get(url, 6 * Net.HOUR, 30 * Net.DAY, (json, error) -> {
            JSONObject a = json == null ? null : json.optJSONObject("anime");
            if (a == null) {
                cb.on(null, error != null ? error : "Не найдено");
                return;
            }
            Models.AnimeDetail d = new Models.AnimeDetail();
            Models.AnimeSummary s = toAnimeSummary(a);
            d.id = s.id;
            d.name = s.name;
            d.slug = s.slug;
            d.year = s.year;
            d.season = s.season;
            d.malId = s.malId;
            d.anilistId = s.anilistId;
            d.cover = s.cover;
            d.coverSmall = s.coverSmall;
            d.mediaFormat = s.mediaFormat;
            d.synopsis = a.isNull("synopsis") ? null : a.optString("synopsis", null);
            d.tracks = animeToTracks(a, true);
            JSONArray studios = a.optJSONArray("studios");
            if (studios != null) for (int i = 0; i < studios.length(); i++) {
                JSONObject x = studios.optJSONObject(i);
                if (x != null) d.studios.add(new String[]{x.optString("name"), x.optString("slug")});
            }
            JSONArray series = a.optJSONArray("series");
            if (series != null) for (int i = 0; i < series.length(); i++) {
                JSONObject x = series.optJSONObject(i);
                if (x != null) d.series.add(new String[]{x.optString("name"), x.optString("slug")});
            }
            JSONArray resources = a.optJSONArray("resources");
            if (resources != null) for (int i = 0; i < resources.length(); i++) {
                JSONObject x = resources.optJSONObject(i);
                if (x != null) d.resources.add(new String[]{x.optString("site"), x.optString("link")});
            }
            List<Integer> warm = new ArrayList<>();
            warm.add(d.malId);
            Meta.warm(warm);
            cb.on(d, null);
        });
    }

    public static void getArtist(String slug, Cb<Models.ArtistDetail> cb) {
        String url = Net.buildUrl(BASE, "/artist/" + Net.encode(slug), fields(params(
                "include", "images,songs.artists,songs.animethemes.anime.images,songs.animethemes.animethemeentries.videos.audio",
                "fields[artist]", "id,name,slug,information"), F));
        Net.get(url, 6 * Net.HOUR, 30 * Net.DAY, (json, error) -> {
            JSONObject ar = json == null ? null : json.optJSONObject("artist");
            if (ar == null) {
                cb.on(null, error != null ? error : "Не найдено");
                return;
            }
            Models.ArtistDetail d = new Models.ArtistDetail();
            Models.ArtistSummary s = toArtistSummary(ar);
            d.id = s.id;
            d.name = s.name;
            d.slug = s.slug;
            d.image = s.image;
            d.imageSmall = s.imageSmall;
            d.information = ar.isNull("information") ? null : ar.optString("information", null);
            JSONArray songs = ar.optJSONArray("songs");
            Set<String> seen = new HashSet<>();
            List<Models.Track> tracks = new ArrayList<>();
            if (songs != null) for (int i = 0; i < songs.length(); i++) {
                JSONObject song = songs.optJSONObject(i);
                if (song == null) continue;
                JSONArray themes = song.optJSONArray("animethemes");
                if (themes == null) continue;
                for (int j = 0; j < themes.length(); j++) {
                    JSONObject th = themes.optJSONObject(j);
                    if (th == null) continue;
                    JSONObject anime = th.optJSONObject("anime");
                    if (anime == null) continue;
                    List<Models.Track> list = themeToTracks(th, anime, song);
                    if (!list.isEmpty() && !seen.contains(list.get(0).id)) {
                        seen.add(list.get(0).id);
                        tracks.add(list.get(0));
                    }
                }
            }
            tracks.sort((a, b) -> {
                int ya = a.anime.year == null ? 0 : a.anime.year;
                int yb = b.anime.year == null ? 0 : b.anime.year;
                return yb - ya;
            });
            d.tracks = tracks;
            attachIds(tracks, () -> cb.on(d, error));
        });
    }

    public static void getRandomTracks(int count, String type, boolean refresh, Cb<List<Models.Track>> cb) {
        Map<String, String> p = fields(params(
                "sort", "random",
                "page[size]", Math.min(100, count),
                "include", THEME_INCLUDE,
                "filter[type]", type,
                "filter[has]", "animethemeentries.videos"), F);
        String url = Net.buildUrl(BASE, "/animetheme", p);
        Net.get(url, refresh ? 1 : Net.MIN, 2 * Net.DAY, refresh, (json, error) -> {
            List<Models.Track> tracks = themesToTracks(Net.arr(json, "animethemes"), count);
            attachIds(tracks, () -> cb.on(tracks, error));
        });
    }

    public static void getLatestTracks(int count, Cb<List<Models.Track>> cb) {
        String url = Net.buildUrl(BASE, "/animetheme", fields(params(
                "sort", "-id",
                "page[size]", Math.min(100, count + 6),
                "include", THEME_INCLUDE,
                "filter[has]", "animethemeentries.videos"), F));
        Net.get(url, 15 * Net.MIN, 3 * Net.DAY, (json, error) -> {
            List<Models.Track> tracks = themesToTracks(Net.arr(json, "animethemes"), count);
            attachIds(tracks, () -> cb.on(tracks, error));
        });
    }

    /** Свежие темы: при пустом периоде — автоматический откат к «all» (как на сайте). */
    public static void getFreshTracks(String period, int count, Cb<List<Models.Track>> cb) {
        Map<String, String> p = fields(params(
                "sort", "-id",
                "page[size]", Math.min(100, "all".equals(period) ? count : count * 3),
                "include", THEME_INCLUDE,
                "filter[has]", "animethemeentries.videos"), F);
        if (!"all".equals(period)) {
            Calendar cal = Calendar.getInstance();
            if ("today".equals(period)) {
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
            } else {
                cal.add(Calendar.DAY_OF_YEAR, -7);
            }
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            p.put("filter[created_at-gt]", fmt.format(cal.getTime()));
        }
        String url = Net.buildUrl(BASE, "/animetheme", p);
        Net.get(url, "today".equals(period) ? 10 * Net.MIN : Net.HOUR, 3 * Net.DAY, (json, error) -> {
            List<Models.Track> tracks = themesToTracks(Net.arr(json, "animethemes"), count);
            if (!tracks.isEmpty() || "all".equals(period)) {
                attachIds(tracks, () -> cb.on(tracks, error));
            } else {
                getFreshTracks("all", count, cb);
            }
        });
    }

    public static void getSeasonAnime(int year, String season, int page, Cb<List<Models.AnimeSummary>> cb) {
        Map<String, String> p = fields(params(
                "filter[year]", year,
                "filter[season]", season,
                "filter[has]", "animethemes",
                "include", ANIME_LIST_INCLUDE,
                "sort", "name",
                "page[size]", 30,
                "page[number]", page), FR);
        String url = Net.buildUrl(BASE, "/anime", p);
        Net.get(url, 6 * Net.HOUR, 30 * Net.DAY, (json, error) -> {
            List<Models.AnimeSummary> items = new ArrayList<>();
            JSONArray anime = Net.arr(json, "anime");
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a != null) items.add(toAnimeSummary(a));
            }
            cb.on(items, error);
        });
    }

    /** Аниме по MyAnimeList id (идём через /resource: site + external_id). */
    public static void getAnimeByMalIds(List<Integer> ids, Cb<List<Models.AnimeSummary>> cb) {
        List<Integer> clean = new ArrayList<>();
        for (Integer id : ids) {
            if (id != null && id > 0 && !clean.contains(id)) clean.add(id);
        }
        if (clean.isEmpty()) {
            cb.on(new ArrayList<>(), null);
            return;
        }
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < clean.size(); i++) {
            if (i > 0) list.append(",");
            list.append(clean.get(i));
        }
        String url = Net.buildUrl(BASE, "/resource", params(
                "filter[site]", SITE_MAL,
                "filter[external_id]", list.toString(),
                "include", "anime",
                "page[size]", 50));
        Net.get(url, 7 * Net.DAY, 30 * Net.DAY, (json, error) -> {
            JSONArray resources = Net.arr(json, "resources");
            final Map<Integer, String> slugByMal = new java.util.LinkedHashMap<>();
            for (int i = 0; i < resources.length(); i++) {
                JSONObject r = resources.optJSONObject(i);
                if (r == null) continue;
                int external = r.optInt("external_id");
                if (external <= 0) continue;
                JSONArray anime = r.optJSONArray("anime");
                if (anime == null || anime.length() == 0) continue;
                JSONObject first = anime.optJSONObject(0);
                if (first == null) continue;
                String slug = first.optString("slug");
                if (!slug.isEmpty() && !slugByMal.containsKey(external)) slugByMal.put(external, slug);
            }
            if (slugByMal.isEmpty()) {
                cb.on(new ArrayList<>(), error);
                return;
            }
            getAnimeBySlugs(new ArrayList<>(slugByMal.values()), (found, err2) -> {
                Map<String, Models.AnimeSummary> bySlug = new java.util.HashMap<>();
                for (Models.AnimeSummary a : found) bySlug.put(a.slug, a);
                List<Models.AnimeSummary> out = new ArrayList<>();
                for (Integer id : clean) {
                    String slug = slugByMal.get(id);
                    Models.AnimeSummary a = slug == null ? null : bySlug.get(slug);
                    if (a == null) continue;
                    if (a.malId == null) a.malId = id;
                    out.add(a);
                }
                cb.on(out, err2);
            });
        });
    }

    public static void getAnimeBySlugs(List<String> slugs, Cb<List<Models.AnimeSummary>> cb) {
        if (slugs == null || slugs.isEmpty()) {
            cb.on(new ArrayList<>(), null);
            return;
        }
        String url = Net.buildUrl(BASE, "/anime", fields(params(
                "filter[slug]", csv(slugs),
                "include", ANIME_LIST_INCLUDE,
                "page[size]", 100), FR));
        Net.get(url, Net.DAY, 30 * Net.DAY, (json, error) -> {
            Map<String, Models.AnimeSummary> map = new HashMap<>();
            JSONArray anime = Net.arr(json, "anime");
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a != null) {
                    Models.AnimeSummary s = toAnimeSummary(a);
                    map.put(s.slug, s);
                }
            }
            List<Models.AnimeSummary> out = new ArrayList<>();
            for (String slug : slugs) if (map.containsKey(slug)) out.add(map.get(slug));
            cb.on(out, error);
        });
    }

    public static void getArtistsBySlugs(List<String> slugs, Cb<List<Models.ArtistSummary>> cb) {
        if (slugs == null || slugs.isEmpty()) {
            cb.on(new ArrayList<>(), null);
            return;
        }
        String url = Net.buildUrl(BASE, "/artist", fields(params(
                "filter[slug]", csv(slugs),
                "include", "images",
                "page[size]", 100), F));
        Net.get(url, Net.DAY, 30 * Net.DAY, (json, error) -> {
            Map<String, Models.ArtistSummary> map = new HashMap<>();
            JSONArray artists = Net.arr(json, "artists");
            for (int i = 0; i < artists.length(); i++) {
                JSONObject a = artists.optJSONObject(i);
                if (a != null) {
                    Models.ArtistSummary s = toArtistSummary(a);
                    map.put(s.slug, s);
                }
            }
            List<Models.ArtistSummary> out = new ArrayList<>();
            for (String slug : slugs) if (map.containsKey(slug)) out.add(map.get(slug));
            cb.on(out, error);
        });
    }

    public static void getTracksForAnimeSlugs(List<String> slugs, Cb<List<Models.Track>> cb) {
        if (slugs == null || slugs.isEmpty()) {
            cb.on(new ArrayList<>(), null);
            return;
        }
        String url = Net.buildUrl(BASE, "/anime", fields(params(
                "filter[slug]", csv(slugs),
                "include", ANIME_THEMES_INCLUDE,
                "page[size]", 100), FR));
        Net.get(url, Net.DAY, 30 * Net.DAY, (json, error) -> {
            Map<String, JSONObject> map = new HashMap<>();
            JSONArray anime = Net.arr(json, "anime");
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a != null) map.put(a.optString("slug"), a);
            }
            List<Models.Track> out = new ArrayList<>();
            for (String slug : slugs) {
                JSONObject a = map.get(slug);
                if (a != null) out.addAll(animeToTracks(a, false));
            }
            attachIds(out, () -> cb.on(out, error));
        });
    }

    public static void getSeasonTracks(int year, String season, Cb<List<Models.Track>> cb) {
        String url = Net.buildUrl(BASE, "/anime", fields(params(
                "filter[year]", year,
                "filter[season]", season,
                "filter[has]", "animethemes",
                "include", ANIME_THEMES_INCLUDE,
                "sort", "name",
                "page[size]", 60), FR));
        Net.get(url, 6 * Net.HOUR, 30 * Net.DAY, (json, error) -> {
            List<Models.Track> out = new ArrayList<>();
            JSONArray anime = Net.arr(json, "anime");
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a != null) out.addAll(animeToTracks(a, false));
            }
            attachIds(out, () -> cb.on(out, error));
        });
    }

    /* ------------------------------------------------------------------ */
    /* Каталог: сезоны, жанры, подборки                                    */
    /* ------------------------------------------------------------------ */

    public static int[] currentSeason() {
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH);
        int seasonIndex = month < 3 ? 0 : month < 6 ? 1 : month < 9 ? 2 : 3;
        return new int[]{cal.get(Calendar.YEAR), seasonIndex};
    }

    public static int[] prevSeason(int year, int seasonIndex) {
        return seasonIndex == 0 ? new int[]{year - 1, 3} : new int[]{year, seasonIndex - 1};
    }

    public static final String[] SEASONS = {"Winter", "Spring", "Summer", "Fall"};
    public static final String[] SEASONS_RU = {"Зима", "Весна", "Лето", "Осень"};

    public static String seasonRu(String season) {
        if (season == null) return "";
        for (int i = 0; i < SEASONS.length; i++) if (SEASONS[i].equals(season)) return SEASONS_RU[i];
        return season;
    }

    public static String seasonLabel(String season, Integer year) {
        StringBuilder sb = new StringBuilder();
        if (season != null) sb.append(seasonRu(season));
        if (year != null) sb.append(sb.length() > 0 ? " " : "").append(year);
        return sb.toString();
    }

    public static String typeLabel(String type) {
        if ("OP".equals(type)) return "Опенинг";
        if ("ED".equals(type)) return "Эндинг";
        return "Вставка";
    }

    public static final List<Models.GenreDef> GENRES = new ArrayList<>();
    public static final List<Models.Mix> MIXES = new ArrayList<>();
    public static final List<String> LEGEND_SLUGS = new ArrayList<>();
    public static final List<String> ARTIST_SLUGS = new ArrayList<>();

    static {
        GENRES.add(new Models.GenreDef("action", "Экшен", new String[]{"экшен", "боевик", "action"}));
        GENRES.add(new Models.GenreDef("fantasy", "Фантастика", new String[]{"фантастика", "sci-fi", "фэнтези", "fantasy"}));
        GENRES.add(new Models.GenreDef("romance", "Романтика", new String[]{"романтика", "romance"}));
        GENRES.add(new Models.GenreDef("comedy", "Комедия", new String[]{"комедия", "comedy"}));
        GENRES.add(new Models.GenreDef("drama", "Драма", new String[]{"драма", "drama"}));
        GENRES.add(new Models.GenreDef("adventure", "Приключения", new String[]{"приключения", "adventure"}));
        GENRES.add(new Models.GenreDef("sports", "Спорт", new String[]{"спорт", "sports"}));
        GENRES.add(new Models.GenreDef("music", "Музыка", new String[]{"музыка", "music", "идол", "idol"}));
        GENRES.add(new Models.GenreDef("thriller", "Хоррор", new String[]{"хоррор", "ужасы", "horror", "триллер", "thriller", "мистика", "mystery", "детектив"}));
        GENRES.add(new Models.GenreDef("shoujo", "Сёдзё", new String[]{"сёдзё", "shoujo"}));
        GENRES.add(new Models.GenreDef("seinen", "Сейнен", new String[]{"сейнен", "seinen"}));
        GENRES.add(new Models.GenreDef("shounen", "Сёнэн", new String[]{"сёнэн", "shounen", "shonen"}));
        GENRES.add(new Models.GenreDef("mecha", "Меха", new String[]{"меха", "mecha"}));
        GENRES.add(new Models.GenreDef("slice", "Повседневность", new String[]{"повседневность", "slice of life"}));
        GENRES.add(new Models.GenreDef("school", "Школа", new String[]{"школа", "school"}));
        GENRES.add(new Models.GenreDef("historical", "Исторический", new String[]{"исторический", "historical"}));

        MIXES.add(new Models.Mix("shounen", "Сёнэн-энергия", "Naruto · Bleach · JJK · MHA", new String[]{"naruto", "naruto_shippuuden", "bleach", "jujutsu_kaisen", "boku_no_hero_academia", "kimetsu_no_yaiba", "one_piece", "fairy_tail", "dragon_ball_z", "haikyuu"}));
        MIXES.add(new Models.Mix("epic", "Эпик и драма", "AoT · FMA · Code Geass", new String[]{"shingeki_no_kyojin", "fullmetal_alchemist_brotherhood", "code_geass_hangyaku_no_lelouch", "vinland_saga", "death_note", "psycho_pass", "tokyo_ghoul", "monster", "berserk", "tengen_toppa_gurren_lagann"}));
        MIXES.add(new Models.Mix("chill", "Чилл и романтика", "Toradora · Clannad · Frieren", new String[]{"toradora", "clannad", "horimiya", "sousou_no_frieren", "violet_evergarden", "kaguya_sama_wa_kokurasetai_tensai_tachi_no_renai_zunousen", "k_on", "angel_beats", "nichijou", "suzumiya_haruhi_no_yuuutsu"}));
        MIXES.add(new Models.Mix("modern", "Новая волна", "Chainsaw Man · Oshi no Ko", new String[]{"chainsaw_man", "oshi_no_ko", "dandadan", "bocchi_the_rock", "spy_x_family", "cyberpunk_edgerunners", "kaijuu_8_gou", "ore_dake_level_up_na_ken", "tokyo_revengers", "mushoku_tensei_isekai_ittara_honki_dasu"}));
        MIXES.add(new Models.Mix("classic", "Классика", "Bebop · Evangelion · Champloo", new String[]{"cowboy_bebop", "neon_genesis_evangelion", "samurai_champloo", "serial_experiments_lain", "hunter_x_hunter_2011", "gintama", "soul_eater", "durara", "no_game_no_life"}));

        String[] legends = {"shingeki_no_kyojin", "kimetsu_no_yaiba", "jujutsu_kaisen", "fullmetal_alchemist_brotherhood", "death_note",
                "sousou_no_frieren", "chainsaw_man", "spy_x_family", "boku_no_hero_academia", "hunter_x_hunter_2011",
                "cowboy_bebop", "neon_genesis_evangelion", "code_geass_hangyaku_no_lelouch", "tokyo_ghoul", "one_punch_man",
                "mob_psycho_100", "naruto", "naruto_shippuuden", "bleach", "oshi_no_ko", "bocchi_the_rock", "violet_evergarden",
                "made_in_abyss", "sword_art_online", "no_game_no_life", "toradora", "clannad", "angel_beats", "k_on", "haikyuu",
                "dragon_ball_z", "yakusoku_no_neverland", "dr_stone", "vinland_saga", "cyberpunk_edgerunners", "dandadan",
                "one_piece", "tokyo_revengers", "gintama", "fairy_tail", "noragami", "psycho_pass", "samurai_champloo",
                "ore_dake_level_up_na_ken", "kaguya_sama_wa_kokurasetai_tensai_tachi_no_renai_zunousen", "nanatsu_no_taizai",
                "durara", "soul_eater", "ao_no_exorcist", "akame_ga_kill", "kill_la_kill", "tengen_toppa_gurren_lagann",
                "monster", "berserk", "horimiya", "mushoku_tensei_isekai_ittara_honki_dasu", "tensei_shitara_slime_datta_ken",
                "kaijuu_8_gou", "kuroko_no_basket", "nichijou", "suzumiya_haruhi_no_yuuutsu", "serial_experiments_lain"};
        java.util.Collections.addAll(LEGEND_SLUGS, legends);

        String[] artists = {"lisa", "aimer", "yoasobi", "flow", "kana_boon", "radwimps", "kenshi_yonezu", "eve", "ado", "akfg",
                "uverworld", "man_with_a_mission", "official_hige_dandism", "kessoku_band", "reona", "milet", "egoist",
                "supercell", "claris", "asca", "aimyon", "yoko_kanno", "linked_horizon", "yuki_kajiura", "kalafina", "king_gnu",
                "creepy_nuts", "vaundy", "yorushika", "zutomayo", "mrs_green_apple", "spyair", "the_oral_cigarettes",
                "ikimonogakari", "nano", "masayoshi_ooishi", "yui", "burnout_syndromes", "oresama", "fhana", "mili",
                "porno_graffitti", "orange_range", "granrodeo", "ling_tosite_sigure"};
        java.util.Collections.addAll(ARTIST_SLUGS, artists);
    }
}
