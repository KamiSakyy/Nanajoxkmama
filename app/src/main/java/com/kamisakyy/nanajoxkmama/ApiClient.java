package com.kamisakyy.nanajoxkmama;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full port of the website's api/animethemes.ts + api/anisongdb.ts:
 * AnimeThemes.moe (primary) and AnisongDB (extended) with the same endpoint
 * quirks, sparse fieldsets and mappers.
 */
public final class ApiClient {
    private static final String ANIME_THEMES = "https://api.animethemes.moe";
    private static final String ANISONG_DB = "https://anisongdb.com/api";
    private static final String ANISONG_MEDIA = "https://naedist.animemusicquiz.com/";

    private ApiClient() { }

    public static final class SearchResult {
        public final ArrayList<AnimeInfo> anime = new ArrayList<>();
        public final ArrayList<Track> tracks = new ArrayList<>();
        public final ArrayList<ArtistInfo> artists = new ArrayList<>();
    }

    public static final class PagedAnime {
        public final ArrayList<AnimeInfo> items = new ArrayList<>();
        public boolean hasMore;
    }

    /* ---------------- theme query params (sparse fieldsets keep payloads tiny) ---------------- */

    private static Map<String, String> fieldsF() {
        Map<String, String> p = new HashMap<>();
        p.put("fields[anime]", "id,name,slug,year,season,media_format");
        p.put("fields[animetheme]", "id,slug,type,sequence");
        p.put("fields[song]", "id,title");
        p.put("fields[artist]", "id,name,slug");
        p.put("fields[animethemeentry]", "id,version,episodes,nsfw,spoiler");
        p.put("fields[video]", "id,link,resolution,tags,nc");
        p.put("fields[audio]", "id,link");
        p.put("fields[image]", "id,facet,link");
        return p;
    }

    private static Map<String, String> fieldsFr() {
        Map<String, String> p = fieldsF();
        p.put("fields[resource]", "site,link,external_id");
        return p;
    }

    private static final String THEME_INCLUDE = "anime.images,song.artists,animethemeentries.videos.audio";
    private static final String ANIME_THEMES_INCLUDE = "images,resources,animethemes.song.artists,animethemes.animethemeentries.videos.audio";
    private static final String ANIME_LIST_INCLUDE = "images,resources";

    /* ---------------- mappers ---------------- */

    private static String pickImage(JSONArray images, String facet) {
        if (images == null) return "";
        String first = "";
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null) continue;
            String link = image.optString("link", "");
            if (first.isEmpty()) first = link;
            if (facet.equals(image.optString("facet", "")) && !link.isEmpty()) return link;
        }
        return first;
    }

    private static int externalId(JSONObject anime, String site) {
        JSONArray resources = anime.optJSONArray("resources");
        if (resources == null) return -1;
        for (int i = 0; i < resources.length(); i++) {
            JSONObject r = resources.optJSONObject(i);
            if (r == null || !site.equals(r.optString("site", ""))) continue;
            if (r.has("external_id") && !r.isNull("external_id")) return r.optInt("external_id", -1);
            String link = r.optString("link", "");
            String[] bits = link.split("/");
            for (int j = bits.length - 1; j >= 0; j--) {
                try { return Integer.parseInt(bits[j]); } catch (NumberFormatException ignored) { }
            }
        }
        return -1;
    }

    static AnimeInfo toAnimeInfo(JSONObject a) {
        AnimeInfo out = new AnimeInfo();
        out.id = a.optInt("id", -1);
        out.name = a.optString("name", "");
        out.slug = a.optString("slug", "");
        out.year = a.has("year") && !a.isNull("year") ? a.optInt("year", -1) : -1;
        out.season = a.optString("season", "");
        out.format = a.optString("media_format", "");
        out.synopsis = a.optString("synopsis", "");
        out.cover = pickImage(a.optJSONArray("images"), "Large Cover");
        out.coverSmall = pickImage(a.optJSONArray("images"), "Small Cover");
        out.malId = externalId(a, "MyAnimeList");
        return out;
    }

    private static ArtistInfo toArtistInfo(JSONObject ar) {
        ArtistInfo out = new ArtistInfo();
        out.id = ar.optInt("id", -1);
        out.name = ar.optString("name", "");
        out.slug = ar.optString("slug", "");
        out.image = pickImage(ar.optJSONArray("images"), "Large Cover");
        out.imageSmall = pickImage(ar.optJSONArray("images"), "Small Cover");
        out.information = ar.optString("information", "");
        return out;
    }

    /** Best video for an entry: creditless first, then highest resolution. */
    private static JSONObject bestVideo(JSONArray videos) {
        if (videos == null || videos.length() == 0) return null;
        JSONObject best = null;
        boolean hasAudio = false;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            JSONObject audio = v == null ? null : v.optJSONObject("audio");
            if (audio != null && !audio.optString("link", "").isEmpty()) hasAudio = true;
        }
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v == null) continue;
            JSONObject audio = v.optJSONObject("audio");
            boolean usable = !hasAudio || (audio != null && !audio.optString("link", "").isEmpty());
            if (!usable) continue;
            if (best == null
                    || (v.optBoolean("nc", false) ? 1 : 0) > (best.optBoolean("nc", false) ? 1 : 0)
                    || v.optInt("resolution", 0) > best.optInt("resolution", 0)) best = v;
        }
        return best;
    }

    private static ArrayList<Track> themeToTracks(JSONObject theme, JSONObject songOverride, boolean allVersions) {
        ArrayList<Track> out = new ArrayList<>();
        if (theme == null) return out;
        JSONObject anime = theme.optJSONObject("anime");
        if (anime == null) return out;
        JSONObject song = songOverride != null ? songOverride : theme.optJSONObject("song");
        JSONArray entries = theme.optJSONArray("animethemeentries");
        if (entries == null) return out;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            JSONObject video = bestVideo(entry.optJSONArray("videos"));
            if (video == null) continue;
            Track t = makeTrack(theme, entry, video, anime, song);
            if (t != null) out.add(t);
            if (!allVersions) break;
        }
        return out;
    }

    private static Track makeTrack(JSONObject theme, JSONObject entry, JSONObject video, JSONObject anime, JSONObject song) {
        JSONObject audio = video.optJSONObject("audio");
        String audioUrl = audio == null ? "" : audio.optString("link", "");
        String videoUrl = video.optString("link", "");
        if (audioUrl.isEmpty()) audioUrl = videoUrl;
        if (audioUrl.isEmpty()) return null;
        Track t = new Track();
        int themeId = theme.optInt("id", -1);
        int entryId = entry.optInt("id", -1);
        int videoId = video.optInt("id", -1);
        t.id = themeId + ":" + entryId + ":" + videoId;
        t.themeId = themeId;
        t.themeSlug = theme.optString("slug", "");
        t.type = theme.optString("type", "OP");
        t.sequence = theme.has("sequence") && !theme.isNull("sequence") ? theme.optInt("sequence", -1) : -1;
        t.title = song == null ? t.themeSlug : song.optString("title", t.themeSlug);
        t.artist = firstArtistName(song);
        t.artistSlug = firstArtistSlug(song);
        t.animeName = anime.optString("name", "Неизвестное аниме");
        t.animeSlug = anime.optString("slug", "");
        t.animeYear = anime.has("year") && !anime.isNull("year") ? anime.optInt("year", -1) : -1;
        t.season = anime.optString("season", "");
        t.malId = externalId(anime, "MyAnimeList");
        t.cover = pickImage(anime.optJSONArray("images"), "Large Cover");
        t.coverSmall = pickImage(anime.optJSONArray("images"), "Small Cover");
        t.audioUrl = audioUrl;
        t.videoUrl = videoUrl.isEmpty() ? audioUrl : videoUrl;
        t.resolution = video.has("resolution") && !video.isNull("resolution") ? video.optInt("resolution", -1) : -1;
        t.tags = video.optString("tags", "");
        t.version = entry.has("version") && !entry.isNull("version") ? entry.optInt("version", -1) : -1;
        t.episodes = entry.optString("episodes", "");
        t.nsfw = entry.optBoolean("nsfw", false);
        t.spoiler = entry.optBoolean("spoiler", false);
        t.source = "primary";
        return t;
    }

    private static String firstArtistName(JSONObject song) {
        if (song == null) return "Неизвестный исполнитель";
        JSONArray artists = song.optJSONArray("artists");
        if (artists != null && artists.length() > 0) {
            String name = artists.optJSONObject(0) == null ? "" : artists.optJSONObject(0).optString("name", "");
            if (!name.isEmpty()) return name;
        }
        return song.optString("artist", "Неизвестный исполнитель");
    }

    private static String firstArtistSlug(JSONObject song) {
        if (song == null) return "";
        JSONArray artists = song.optJSONArray("artists");
        JSONObject a = artists == null ? null : artists.optJSONObject(0);
        return a == null ? "" : a.optString("slug", "");
    }

    private static int typeRank(String type) {
        return "OP".equals(type) ? 0 : "ED".equals(type) ? 1 : 2;
    }

    private static void sortThemeTracks(List<Track> list) {
        java.util.Collections.sort(list, (a, b) -> {
            int type = typeRank(a.type) - typeRank(b.type);
            if (type != 0) return type;
            return Integer.compare(a.sequence < 0 ? 999 : a.sequence, b.sequence < 0 ? 999 : b.sequence);
        });
    }

    private static ArrayList<Track> themesToTracks(JSONArray themes, boolean allVersions, int limit) {
        ArrayList<Track> out = new ArrayList<>();
        if (themes == null) return out;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < themes.length() && out.size() < limit; i++) {
            JSONObject theme = themes.optJSONObject(i);
            if (theme == null) continue;
            ArrayList<Track> tracks = themeToTracks(theme, null, allVersions);
            for (Track t : tracks) {
                if (!seen.add(t.id)) continue;
                out.add(t);
                if (out.size() >= limit) break;
            }
        }
        if (!allVersions) sortThemeTracks(out);
        return out;
    }

    /** All tracks for a whole anime object (sorted OP/ED, sequence). */
    private static ArrayList<Track> animeToTracks(JSONObject a, boolean allVersions) {
        ArrayList<Track> out = new ArrayList<>();
        JSONArray themes = a.optJSONArray("animethemes");
        if (themes == null) return out;
        ArrayList<JSONObject> sorted = new ArrayList<>();
        for (int i = 0; i < themes.length(); i++) {
            JSONObject th = themes.optJSONObject(i);
            if (th != null) sorted.add(th);
        }
        java.util.Collections.sort(sorted, (x, y) -> {
            int type = typeRank(x.optString("type", "IN")) - typeRank(y.optString("type", "IN"));
            if (type != 0) return type;
            int sx = x.has("sequence") && !x.isNull("sequence") ? x.optInt("sequence") : 999;
            int sy = y.has("sequence") && !y.isNull("sequence") ? y.optInt("sequence") : 999;
            if (sx != sy) return Integer.compare(sx, sy);
            return x.optString("slug", "").compareTo(y.optString("slug", ""));
        });
        Set<String> seen = new HashSet<>();
        for (JSONObject th : sorted) {
            ArrayList<Track> tracks = themeToTracks(th, null, allVersions);
            for (Track t : tracks) if (seen.add(t.id)) out.add(t);
        }
        return out;
    }

    private static String query(Map<String, String> params) throws IOException {
        StringBuilder out = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (!first) out.append('&');
            first = false;
            out.append(URLEncoder.encode(e.getKey(), "UTF-8"));
            out.append('=');
            out.append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        return out.toString();
    }

    private static String join(List<String> items, String sep) {
        StringBuilder out = new StringBuilder();
        for (String s : items) {
            if (s == null || s.isEmpty()) continue;
            if (out.length() > 0) out.append(sep);
            out.append(s);
        }
        return out.toString();
    }

    /* ---------------- public API ---------------- */

    public static ArrayList<Track> getRandomTracks(int count, String type) throws IOException {
        Map<String, String> p = fieldsF();
        p.put("sort", "random");
        p.put("page[size]", String.valueOf(Math.min(100, count)));
        p.put("include", THEME_INCLUDE);
        p.put("filter[has]", "animethemeentries.videos");
        if (type != null && !type.isEmpty()) p.put("filter[type]", type);
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/animetheme" + query(p),
                new HttpCache.Policy().ttl(0).fresh(0).maxAge(2 * HttpCache.DAY).noStore());
        ArrayList<Track> out = themesToTracks(root.optJSONArray("animethemes"), false, count);
        MetaApi.warmTracks(out);
        return out;
    }

    public static ArrayList<Track> getLatestTracks(int count) throws IOException {
        Map<String, String> p = fieldsF();
        p.put("sort", "-id");
        p.put("page[size]", String.valueOf(Math.min(100, count + 6)));
        p.put("include", THEME_INCLUDE);
        p.put("filter[has]", "animethemeentries.videos");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/animetheme" + query(p),
                new HttpCache.Policy().fresh(15 * HttpCache.MIN).maxAge(3 * HttpCache.DAY));
        ArrayList<Track> out = themesToTracks(root.optJSONArray("animethemes"), false, count);
        MetaApi.warmTracks(out);
        return out;
    }

    /** Freshly-added themes, newest first; falls back to "all" when a period is empty. */
    public static ArrayList<Track> getFreshTracks(String period, int count) throws IOException {
        java.text.SimpleDateFormat iso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
        iso.setTimeZone(java.util.TimeZone.getDefault());
        Map<String, String> p = fieldsF();
        p.put("sort", "-id");
        p.put("page[size]", String.valueOf(Math.min(100, count * ("all".equals(period) ? 1 : 3))));
        p.put("include", THEME_INCLUDE);
        p.put("filter[has]", "animethemeentries.videos");
        if (!"all".equals(period)) {
            java.util.Calendar since = java.util.Calendar.getInstance();
            if ("today".equals(period)) {
                since.set(java.util.Calendar.HOUR_OF_DAY, 0);
                since.set(java.util.Calendar.MINUTE, 0);
                since.set(java.util.Calendar.SECOND, 0);
                since.set(java.util.Calendar.MILLISECOND, 0);
            } else {
                since.add(java.util.Calendar.DAY_OF_YEAR, -7);
            }
            p.put("filter[created_at-gt]", iso.format(since.getTime()));
        }
        long fresh = "today".equals(period) ? 10 * HttpCache.MIN : HttpCache.HOUR;
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/animetheme" + query(p),
                new HttpCache.Policy().fresh(fresh).maxAge(3 * HttpCache.DAY));
        ArrayList<Track> out = themesToTracks(root.optJSONArray("animethemes"), false, count);
        if (out.isEmpty() && !"all".equals(period)) return getFreshTracks("all", count);
        MetaApi.warmTracks(out);
        return out;
    }

    public static SearchResult search(String queryText) throws IOException {
        SearchResult result = new SearchResult();
        Map<String, String> p = fieldsF();
        p.put("q", queryText);
        p.put("fields[search]", "anime,animethemes,artists");
        p.put("page[limit]", "12");
        p.put("include[anime]", ANIME_LIST_INCLUDE);
        p.put("include[animetheme]", THEME_INCLUDE);
        p.put("include[artist]", "images");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/search" + query(p),
                new HttpCache.Policy().fresh(30 * HttpCache.MIN).maxAge(HttpCache.DAY).timeout(12000));
        JSONObject search = root.optJSONObject("search");
        if (search != null) {
            JSONArray anime = search.optJSONArray("anime");
            if (anime != null) {
                for (int i = 0; i < anime.length(); i++) {
                    AnimeInfo a = toAnimeInfo(anime.optJSONObject(i));
                    if (!a.slug.isEmpty()) result.anime.add(a);
                }
            }
            result.tracks.addAll(themesToTracks(search.optJSONArray("animethemes"), false, 60));
            JSONArray artists = search.optJSONArray("artists");
            if (artists != null) {
                for (int i = 0; i < artists.length(); i++) {
                    JSONObject artist = artists.optJSONObject(i);
                    if (artist != null) {
                        ArtistInfo info = toArtistInfo(artist);
                        if (!info.name.isEmpty()) result.artists.add(info);
                    }
                }
            }
        }
        Map<String, AnimeInfo> known = new HashMap<>();
        for (AnimeInfo a : result.anime) known.put(a.slug, a);
        for (Track t : result.tracks) {
            AnimeInfo a = known.get(t.animeSlug);
            if (a != null && a.malId > 0) t.malId = a.malId;
        }
        List<Integer> ids = new ArrayList<>();
        for (Track t : result.tracks) if (t.malId > 0) ids.add(t.malId);
        for (AnimeInfo a : result.anime) if (a.malId > 0) ids.add(a.malId);
        MetaApi.warm(ids);
        return result;
    }

    public static AnimeInfo.Detail anime(String slug) throws IOException {
        Map<String, String> p = fieldsFr();
        p.put("include", ANIME_THEMES_INCLUDE + ",studios,series");
        p.put("fields[anime]", "id,name,slug,year,season,media_format,synopsis");
        p.put("fields[studio]", "name,slug");
        p.put("fields[series]", "name,slug");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/anime/" + URLEncoder.encode(slug, "UTF-8") + query(p),
                new HttpCache.Policy().fresh(6 * HttpCache.HOUR).maxAge(30 * HttpCache.DAY));
        JSONObject raw = root.optJSONObject("anime");
        if (raw == null) throw new IOException("Аниме не найдено");
        AnimeInfo.Detail out = new AnimeInfo.Detail();
        AnimeInfo base = toAnimeInfo(raw);
        copyInfo(out, base);
        out.tracks.addAll(animeToTracks(raw, true));
        JSONArray studios = raw.optJSONArray("studios");
        if (studios != null) {
            for (int i = 0; i < studios.length(); i++) {
                JSONObject s = studios.optJSONObject(i);
                if (s != null) out.studios.add(s.optString("name", ""));
            }
        }
        JSONArray series = raw.optJSONArray("series");
        if (series != null) {
            for (int i = 0; i < series.length(); i++) {
                JSONObject s = series.optJSONObject(i);
                if (s != null) out.series.add(s.optString("name", ""));
            }
        }
        List<Integer> ids = new ArrayList<>();
        if (out.malId > 0) ids.add(out.malId);
        MetaApi.warm(ids);
        return out;
    }

    private static void copyInfo(AnimeInfo to, AnimeInfo from) {
        to.id = from.id;
        to.name = from.name;
        to.slug = from.slug;
        to.year = from.year;
        to.season = from.season;
        to.format = from.format;
        to.synopsis = from.synopsis;
        to.cover = from.cover;
        to.coverSmall = from.coverSmall;
        to.malId = from.malId;
    }

    public static ArtistInfo artist(String slug) throws IOException {
        Map<String, String> p = fieldsF();
        p.put("include", "images,songs.artists,songs.animethemes.anime.images,songs.animethemes.animethemeentries.videos.audio");
        p.put("fields[artist]", "id,name,slug,information");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/artist/" + URLEncoder.encode(slug, "UTF-8") + query(p),
                new HttpCache.Policy().fresh(6 * HttpCache.HOUR).maxAge(30 * HttpCache.DAY));
        JSONObject artist = root.optJSONObject("artist");
        if (artist == null) throw new IOException("Исполнитель не найден");
        ArtistInfo out = toArtistInfo(artist);
        JSONArray songs = artist.optJSONArray("songs");
        if (songs != null) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < songs.length(); i++) {
                JSONObject song = songs.optJSONObject(i);
                if (song == null) continue;
                JSONArray themes = song.optJSONArray("animethemes");
                if (themes == null) continue;
                for (int j = 0; j < themes.length(); j++) {
                    JSONObject theme = themes.optJSONObject(j);
                    if (theme == null) continue;
                    ArrayList<Track> tracks = themeToTracks(theme, song, false);
                    for (Track t : tracks) if (seen.add(t.id)) out.tracks.add(t);
                }
            }
        }
        java.util.Collections.sort(out.tracks, (a, b) -> Integer.compare(b.animeYear, a.animeYear));
        MetaApi.warmTracks(out.tracks);
        return out;
    }

    public static ArrayList<AnimeInfo> animeBySlugs(List<String> slugs) throws IOException {
        ArrayList<AnimeInfo> out = new ArrayList<>();
        List<String> clean = new ArrayList<>();
        for (String s : slugs) if (s != null && !s.isEmpty()) clean.add(s);
        if (clean.isEmpty()) return out;
        Map<String, String> p = fieldsFr();
        p.put("filter[slug]", join(clean, ","));
        p.put("include", ANIME_LIST_INCLUDE);
        p.put("page[size]", "100");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/anime" + query(p),
                new HttpCache.Policy().fresh(HttpCache.DAY).maxAge(30 * HttpCache.DAY));
        Map<String, AnimeInfo> map = new HashMap<>();
        JSONArray anime = root.optJSONArray("anime");
        if (anime != null) {
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a == null) continue;
                AnimeInfo info = toAnimeInfo(a);
                map.put(info.slug, info);
            }
        }
        for (String s : clean) {
            AnimeInfo info = map.get(s);
            if (info != null) out.add(info);
        }
        List<Integer> ids = new ArrayList<>();
        for (AnimeInfo a : out) if (a.malId > 0) ids.add(a.malId);
        MetaApi.warm(ids);
        return out;
    }

    public static ArrayList<AnimeInfo> animeByMalIds(List<Integer> ids) throws IOException {
        ArrayList<AnimeInfo> out = new ArrayList<>();
        Set<Integer> clean = new java.util.LinkedHashSet<>();
        for (Integer id : ids) if (id != null && id > 0) clean.add(id);
        if (clean.isEmpty()) return out;
        StringBuilder idList = new StringBuilder();
        for (int id : clean) {
            if (idList.length() > 0) idList.append(',');
            idList.append(id);
        }
        Map<String, String> p = new HashMap<>();
        p.put("filter[site]", "MyAnimeList");
        p.put("filter[external_id]", idList.toString());
        p.put("include", "anime");
        p.put("page[size]", "50");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/resource" + query(p),
                new HttpCache.Policy().fresh(7 * HttpCache.DAY).maxAge(30 * HttpCache.DAY));
        Map<Integer, String> slugByMal = new HashMap<>();
        JSONArray resources = root.optJSONArray("resources");
        if (resources != null) {
            for (int i = 0; i < resources.length(); i++) {
                JSONObject r = resources.optJSONObject(i);
                if (r == null) continue;
                int ext = r.has("external_id") && !r.isNull("external_id") ? r.optInt("external_id") : -1;
                JSONArray animes = r.optJSONArray("anime");
                if (ext <= 0 || animes == null || animes.length() == 0) continue;
                JSONObject a = animes.optJSONObject(0);
                if (a == null) continue;
                String slug = a.optString("slug", "");
                if (!slug.isEmpty() && !slugByMal.containsKey(ext)) slugByMal.put(ext, slug);
            }
        }
        List<String> slugs = new ArrayList<>();
        Map<String, Integer> malOfSlug = new HashMap<>();
        for (int id : clean) {
            String slug = slugByMal.get(id);
            if (slug != null) {
                slugs.add(slug);
                malOfSlug.put(slug, id);
            }
        }
        if (slugs.isEmpty()) return out;
        Map<String, String> p2 = fieldsFr();
        p2.put("filter[slug]", join(slugs, ","));
        p2.put("include", ANIME_LIST_INCLUDE);
        p2.put("page[size]", "100");
        JSONObject root2 = HttpCache.getJson(ANIME_THEMES + "/anime" + query(p2),
                new HttpCache.Policy().fresh(HttpCache.DAY).maxAge(30 * HttpCache.DAY));
        Map<String, AnimeInfo> map = new HashMap<>();
        JSONArray anime = root2.optJSONArray("anime");
        if (anime != null) {
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a == null) continue;
                AnimeInfo info = toAnimeInfo(a);
                Integer mal = malOfSlug.get(info.slug);
                if (mal != null) info.malId = mal;
                map.put(info.slug, info);
            }
        }
        for (String s : slugs) {
            AnimeInfo info = map.get(s);
            if (info != null) out.add(info);
        }
        List<Integer> warm = new ArrayList<>();
        for (AnimeInfo a : out) if (a.malId > 0) warm.add(a.malId);
        MetaApi.warm(warm);
        return out;
    }

    public static ArrayList<ArtistInfo> artistsBySlugs(List<String> slugs) throws IOException {
        ArrayList<ArtistInfo> out = new ArrayList<>();
        List<String> clean = new ArrayList<>();
        for (String s : slugs) if (s != null && !s.isEmpty()) clean.add(s);
        if (clean.isEmpty()) return out;
        Map<String, String> p = fieldsF();
        p.put("filter[slug]", join(clean, ","));
        p.put("include", "images");
        p.put("page[size]", "100");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/artist" + query(p),
                new HttpCache.Policy().fresh(HttpCache.DAY).maxAge(30 * HttpCache.DAY));
        Map<String, ArtistInfo> map = new HashMap<>();
        JSONArray artists = root.optJSONArray("artists");
        if (artists != null) {
            for (int i = 0; i < artists.length(); i++) {
                JSONObject a = artists.optJSONObject(i);
                if (a == null) continue;
                ArtistInfo info = toArtistInfo(a);
                map.put(info.slug, info);
            }
        }
        for (String s : clean) {
            ArtistInfo info = map.get(s);
            if (info != null) out.add(info);
        }
        return out;
    }

    public static PagedAnime seasonAnime(int year, String season, int page) throws IOException {
        Map<String, String> p = fieldsFr();
        p.put("filter[year]", String.valueOf(year));
        if (season != null && !season.isEmpty()) p.put("filter[season]", season);
        p.put("filter[has]", "animethemes");
        p.put("include", ANIME_LIST_INCLUDE);
        p.put("sort", "name");
        p.put("page[size]", "30");
        p.put("page[number]", String.valueOf(page));
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/anime" + query(p),
                new HttpCache.Policy().fresh(6 * HttpCache.HOUR).maxAge(30 * HttpCache.DAY));
        PagedAnime out = new PagedAnime();
        JSONArray anime = root.optJSONArray("anime");
        if (anime != null) {
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a != null) out.items.add(toAnimeInfo(a));
            }
        }
        JSONObject links = root.optJSONObject("links");
        out.hasMore = links != null && !links.isNull("next") && links.optString("next", "").length() > 0
                && !"null".equals(links.optString("next", ""));
        List<Integer> ids = new ArrayList<>();
        for (AnimeInfo a : out.items) if (a.malId > 0) ids.add(a.malId);
        MetaApi.warm(ids);
        return out;
    }

    /** All primary tracks for a full season ("play the whole season"). */
    public static ArrayList<Track> seasonTracks(int year, String season) throws IOException {
        Map<String, String> p = fieldsFr();
        p.put("filter[year]", String.valueOf(year));
        p.put("filter[season]", season);
        p.put("filter[has]", "animethemes");
        p.put("include", ANIME_THEMES_INCLUDE);
        p.put("sort", "name");
        p.put("page[size]", "60");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/anime" + query(p),
                new HttpCache.Policy().fresh(6 * HttpCache.HOUR).maxAge(30 * HttpCache.DAY));
        ArrayList<Track> out = new ArrayList<>();
        JSONArray anime = root.optJSONArray("anime");
        if (anime != null) {
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a != null) out.addAll(animeToTracks(a, false));
            }
        }
        MetaApi.warmTracks(out);
        return out;
    }

    /** Tracks for several anime at once (curated mixes). */
    public static ArrayList<Track> tracksForAnimeSlugs(List<String> slugs) throws IOException {
        ArrayList<Track> out = new ArrayList<>();
        if (slugs.isEmpty()) return out;
        Map<String, String> p = fieldsFr();
        p.put("filter[slug]", join(slugs, ","));
        p.put("include", ANIME_THEMES_INCLUDE);
        p.put("page[size]", "100");
        JSONObject root = HttpCache.getJson(ANIME_THEMES + "/anime" + query(p),
                new HttpCache.Policy().fresh(HttpCache.DAY).maxAge(30 * HttpCache.DAY));
        Map<String, JSONObject> map = new HashMap<>();
        JSONArray anime = root.optJSONArray("anime");
        if (anime != null) {
            for (int i = 0; i < anime.length(); i++) {
                JSONObject a = anime.optJSONObject(i);
                if (a != null) map.put(a.optString("slug", ""), a);
            }
        }
        for (String s : slugs) {
            JSONObject a = map.get(s);
            if (a != null) out.addAll(animeToTracks(a, false));
        }
        MetaApi.warmTracks(out);
        return out;
    }

    /* ---------------- AnisongDB (extended base) ---------------- */

    public static ArrayList<Track> anisongSearch(String queryText) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("anime_search_filter", obj("search", queryText, "partial_match", true));
            body.put("song_name_search_filter", obj("search", queryText, "partial_match", true));
            JSONObject artist = new JSONObject();
            artist.put("search", queryText);
            artist.put("partial_match", true);
            artist.put("group_granularity", 0);
            artist.put("max_other_artist", 99);
            body.put("artist_search_filter", artist);
            body.put("and_logic", false);
            addAnisongFilters(body);
        } catch (Exception e) {
            throw new IOException("AnisongDB недоступен", e);
        }
        JSONArray list = HttpCache.getArray(ANISONG_DB + "/search_request",
                new HttpCache.Policy().body(body.toString()).fresh(6 * HttpCache.HOUR).maxAge(7 * HttpCache.DAY).timeout(15000));
        return anisongArrayToTracks(list, 60);
    }

    public static ArrayList<Track> anisongsForAnime(int malId, String name) throws IOException {
        JSONArray list = null;
        if (malId > 0) {
            try {
                JSONObject body = new JSONObject();
                body.put("malIds", new JSONArray().put(malId));
                addAnisongFilters(body);
                list = HttpCache.getArray(ANISONG_DB + "/malIDs_request",
                        new HttpCache.Policy().body(body.toString()).fresh(7 * HttpCache.DAY).maxAge(30 * HttpCache.DAY).timeout(15000));
            } catch (Exception ignored) { }
        }
        if (list == null || list.length() == 0) {
            try {
                JSONObject body = new JSONObject();
                body.put("anime_search_filter", obj("search", name, "partial_match", false));
                body.put("and_logic", false);
                addAnisongFilters(body);
                list = HttpCache.getArray(ANISONG_DB + "/search_request",
                        new HttpCache.Policy().body(body.toString()).fresh(7 * HttpCache.DAY).maxAge(30 * HttpCache.DAY).timeout(15000));
            } catch (Exception e) {
                throw new IOException("AnisongDB недоступен", e);
            }
        }
        ArrayList<Track> tracks = anisongArrayToTracks(list, 200);
        java.util.Collections.sort(tracks, (a, b) -> {
            int type = typeRank(a.type) - typeRank(b.type);
            if (type != 0) return type;
            return Integer.compare(a.sequence < 0 ? 999 : a.sequence, b.sequence < 0 ? 999 : b.sequence);
        });
        return tracks;
    }

    public static ArrayList<Track> randomAnisongs() throws IOException {
        JSONArray list = HttpCache.getArray(ANISONG_DB + "/get_50_random_songs",
                new HttpCache.Policy().ttl(0).body("{}").noStore().timeout(15000));
        return anisongArrayToTracks(list, 50);
    }

    private static void addAnisongFilters(JSONObject body) throws Exception {
        body.put("ignore_duplicate", false);
        body.put("opening_filter", true);
        body.put("ending_filter", true);
        body.put("insert_filter", true);
    }

    private static JSONObject obj(String k1, Object v1, String k2, Object v2) throws Exception {
        JSONObject o = new JSONObject();
        o.put(k1, v1);
        o.put(k2, v2);
        return o;
    }

    private static ArrayList<Track> anisongArrayToTracks(JSONArray list, int limit) {
        ArrayList<Track> out = new ArrayList<>();
        if (list == null) return out;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < list.length() && out.size() < limit; i++) {
            JSONObject s = list.optJSONObject(i);
            if (s == null) continue;
            int id = s.optInt("annSongId", -1);
            if (!seen.add(id)) continue;
            Track t = anisongToTrack(s);
            if (t != null) out.add(t);
        }
        return out;
    }

    private static Track anisongToTrack(JSONObject s) {
        String audio = mediaUrl(s.optString("audio", ""));
        String videoPath = s.optString("HQ", "");
        if (videoPath.isEmpty()) videoPath = s.optString("MQ", "");
        String video = mediaUrl(videoPath);
        if (audio.isEmpty() && video.isEmpty()) return null;
        if (audio.isEmpty()) audio = video;
        if (video.isEmpty()) video = audio;
        String kind = s.optString("songType", "Insert Song");
        String lower = kind.toLowerCase(java.util.Locale.US);
        String type = lower.startsWith("opening") ? "OP" : lower.startsWith("ending") ? "ED" : "IN";
        int sequence = -1;
        String[] bits = kind.split(" ");
        if (bits.length > 1) {
            try { sequence = Integer.parseInt(bits[bits.length - 1]); } catch (NumberFormatException ignored) { }
        }
        Track t = new Track();
        int id = s.optInt("annSongId", -1);
        t.id = "asdb:" + id;
        t.themeId = -id;
        t.themeSlug = type + (sequence > 0 ? sequence : "");
        t.type = type;
        t.sequence = sequence;
        t.title = s.optString("songName", "Без названия");
        JSONArray artists = s.optJSONArray("artists");
        String artistName = s.optString("songArtist", "Неизвестный исполнитель");
        if (artists != null && artists.length() > 0) {
            JSONObject a0 = artists.optJSONObject(0);
            if (a0 != null) {
                JSONArray names = a0.optJSONArray("names");
                String n = names != null && names.length() > 0 ? names.optString(0) : "";
                if (!n.isEmpty()) artistName = n;
            }
        }
        t.artist = artistName;
        t.animeName = s.optString("animeJPName", s.optString("animeENName", "Неизвестное аниме"));
        t.audioUrl = audio;
        t.videoUrl = video;
        t.resolution = s.optString("HQ", "").isEmpty() ? 480 : 720;
        t.tags = s.optString("HQ", "").isEmpty() ? "MQ" : "HQ";
        t.source = "extra";
        JSONObject links = s.optJSONObject("linked_ids");
        t.malId = linkedNumber(links, "mal");
        String vintage = s.optString("animeVintage", "");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(Winter|Spring|Summer|Fall)\\s+(\\d{4})", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(vintage);
        if (m.find()) {
            String se = m.group(1);
            t.season = Character.toUpperCase(se.charAt(0)) + se.substring(1).toLowerCase(java.util.Locale.US);
            try { t.animeYear = Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) { }
        }
        t.animeSlug = t.malId > 0 ? "mal-" + t.malId : "ann-" + s.optInt("annId", id);
        return t;
    }

    private static int linkedNumber(JSONObject links, String wanted) {
        if (links == null) return -1;
        java.util.Iterator<String> keys = links.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.toLowerCase(java.util.Locale.US).contains(wanted)) continue;
            Object value = links.opt(key);
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof JSONArray && ((JSONArray) value).length() > 0) return ((JSONArray) value).optInt(0, -1);
        }
        return -1;
    }

    private static String mediaUrl(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.startsWith("http") ? value : ANISONG_MEDIA + (value.startsWith("/") ? value.substring(1) : value);
    }
}
