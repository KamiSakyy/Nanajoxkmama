package com.kamisakyy.nanajoxkmama;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small keyless client for the same sources as the supplied web app.
 * No JSON or networking dependency is bundled: Android's platform classes are enough.
 */
public final class ApiClient {
    public static final String ANIME_THEMES = "https://api.animethemes.moe";
    private static final String ANISONG_DB = "https://anisongdb.com/api";
    private static final String ANISONG_MEDIA = "https://naedist.animemusicquiz.com/";
    private static final String USER_AGENT = "AniBeat/1.0 (Android; Anime music player)";

    private ApiClient() { }

    public static final class SearchResult {
        public final ArrayList<AnimeInfo> anime = new ArrayList<>();
        public final ArrayList<Track> tracks = new ArrayList<>();
        public final ArrayList<String> artists = new ArrayList<>();
    }

    public static final class AnimeResult {
        public AnimeInfo info;
        public final ArrayList<Track> tracks = new ArrayList<>();
    }

    public static final class ArtistResult {
        public String name = "";
        public String information = "";
        public final ArrayList<Track> tracks = new ArrayList<>();
    }

    public static ArrayList<Track> latest(int count, String type) throws IOException {
        Map<String, String> p = commonThemeParams();
        p.put("sort", "-id");
        p.put("page[size]", String.valueOf(Math.min(100, count + 6)));
        if (type != null && !type.isEmpty()) p.put("filter[type]", type);
        JSONObject root = get(ANIME_THEMES + "/animetheme", p);
        return themesToTracks(root.optJSONArray("animethemes"), false, count);
    }

    public static ArrayList<Track> random(int count, String type) throws IOException {
        Map<String, String> p = commonThemeParams();
        p.put("sort", "random");
        p.put("page[size]", String.valueOf(Math.min(100, count)));
        if (type != null && !type.isEmpty()) p.put("filter[type]", type);
        JSONObject root = get(ANIME_THEMES + "/animetheme", p);
        return themesToTracks(root.optJSONArray("animethemes"), false, count);
    }

    public static SearchResult search(String query) throws IOException {
        SearchResult result = new SearchResult();
        Map<String, String> p = commonThemeParams();
        p.put("q", query);
        p.put("fields[search]", "anime,animethemes,artists");
        p.put("page[limit]", "20");
        p.put("include[anime]", "images,resources");
        p.put("include[animetheme]", "anime.images,song.artists,animethemeentries.videos.audio");
        p.put("include[artist]", "images");
        p.remove("include");
        JSONObject root = get(ANIME_THEMES + "/search", p);
        JSONObject search = root.optJSONObject("search");
        if (search != null) {
            JSONArray anime = search.optJSONArray("anime");
            if (anime != null) {
                for (int i = 0; i < anime.length(); i++) {
                    AnimeInfo a = AnimeInfo.fromJson(anime.optJSONObject(i));
                    if (!a.slug.isEmpty()) result.anime.add(a);
                }
            }
            result.tracks.addAll(themesToTracks(search.optJSONArray("animethemes"), false, 60));
            JSONArray artists = search.optJSONArray("artists");
            if (artists != null) {
                for (int i = 0; i < artists.length(); i++) {
                    JSONObject artist = artists.optJSONObject(i);
                    if (artist != null) result.artists.add(artist.optString("name", ""));
                }
            }
        }
        // The website's expanded source is deliberately best-effort; AnimeThemes remains primary.
        try {
            ArrayList<Track> extra = anisongSearch(query);
            Set<String> seen = new HashSet<>();
            for (Track t : result.tracks) seen.add(t.id);
            for (Track t : extra) if (seen.add(t.id)) result.tracks.add(t);
        } catch (IOException ignored) { }
        return result;
    }

    public static AnimeResult anime(String slug) throws IOException {
        Map<String, String> p = commonAnimeParams();
        p.put("include", "images,resources,animethemes.song.artists,animethemes.animethemeentries.videos.audio,studios,series");
        p.put("fields[anime]", "id,name,slug,year,season,media_format,synopsis");
        JSONObject root = get(ANIME_THEMES + "/anime/" + encodePath(slug), p);
        JSONObject raw = root.optJSONObject("anime");
        if (raw == null) throw new IOException("Аниме не найдено");
        AnimeResult out = new AnimeResult();
        out.info = AnimeInfo.fromJson(raw);
        out.tracks.addAll(themesToTracks(raw.optJSONArray("animethemes"), true, 200));
        // Insert songs and rare versions from the same AnisongDB source used by the website.
        try {
            ArrayList<Track> extra = anisongForAnime(out.info.malId, out.info.name);
            Set<String> seen = new HashSet<>();
            for (Track t : out.tracks) seen.add(t.type + ":" + t.sequence);
            for (Track t : extra) {
                if ("IN".equals(t.type) || !seen.contains(t.type + ":" + t.sequence)) {
                    t.animeName = out.info.name;
                    t.animeSlug = out.info.slug;
                    t.cover = out.info.cover;
                    t.coverSmall = out.info.coverSmall;
                    t.malId = out.info.malId;
                    out.tracks.add(t);
                }
            }
        } catch (IOException ignored) { }
        return out;
    }

    public static ArtistResult artist(String slug) throws IOException {
        Map<String, String> p = commonThemeParams();
        p.put("include", "images,songs.artists,songs.animathemes.anime.images,songs.animathemes.animethemeentries.videos.audio");
        p.put("fields[artist]", "id,name,slug,information");
        JSONObject root = get(ANIME_THEMES + "/artist/" + encodePath(slug), p);
        JSONObject artist = root.optJSONObject("artist");
        if (artist == null) throw new IOException("Исполнитель не найден");
        ArtistResult out = new ArtistResult();
        out.name = artist.optString("name", "Исполнитель");
        out.information = artist.optString("information", "");
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
        Collections.sort(out.tracks, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) { return Integer.compare(b.animeYear, a.animeYear); }
        });
        return out;
    }

    private static Map<String, String> commonThemeParams() {
        Map<String, String> p = new HashMap<>();
        p.put("fields[anime]", "id,name,slug,year,season,media_format");
        p.put("fields[animetheme]", "id,slug,type,sequence");
        p.put("fields[song]", "id,title");
        p.put("fields[artist]", "id,name,slug");
        p.put("fields[animethemeentry]", "id,version,episodes,nsfw,spoiler");
        p.put("fields[video]", "id,link,resolution,tags,nc");
        p.put("fields[audio]", "id,link");
        p.put("fields[image]", "id,facet,link");
        p.put("include", "anime.images,song.artists,animethemeentries.videos.audio");
        p.put("filter[has]", "animethemeentries.videos");
        return p;
    }

    private static Map<String, String> commonAnimeParams() {
        Map<String, String> p = new HashMap<>();
        p.put("fields[image]", "id,facet,link");
        p.put("fields[resource]", "site,link,external_id");
        p.put("fields[animetheme]", "id,slug,type,sequence");
        p.put("fields[song]", "id,title");
        p.put("fields[artist]", "id,name,slug");
        p.put("fields[animethemeentry]", "id,version,episodes,nsfw,spoiler");
        p.put("fields[video]", "id,link,resolution,tags,nc");
        p.put("fields[audio]", "id,link");
        return p;
    }

    private static ArrayList<Track> themesToTracks(JSONArray themes, boolean allVersions, int limit) {
        ArrayList<Track> out = new ArrayList<>();
        if (themes == null) return out;
        for (int i = 0; i < themes.length() && out.size() < limit; i++) {
            JSONObject theme = themes.optJSONObject(i);
            if (theme == null) continue;
            ArrayList<Track> tracks = themeToTracks(theme, theme.optJSONObject("song"), allVersions);
            for (Track t : tracks) {
                out.add(t);
                if (out.size() >= limit) break;
            }
        }
        Collections.sort(out, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) {
                int type = typeRank(a.type) - typeRank(b.type);
                if (type != 0) return type;
                return Integer.compare(a.sequence < 0 ? 999 : a.sequence, b.sequence < 0 ? 999 : b.sequence);
            }
        });
        return out;
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

    private static JSONObject bestVideo(JSONArray videos) {
        if (videos == null || videos.length() == 0) return null;
        JSONObject best = null;
        boolean hasAudio = false;
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v == null) continue;
            JSONObject audio = v.optJSONObject("audio");
            if (audio != null && !audio.optString("link", "").isEmpty()) hasAudio = true;
        }
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v == null) continue;
            JSONObject audio = v.optJSONObject("audio");
            boolean usable = !hasAudio || (audio != null && !audio.optString("link", "").isEmpty());
            if (!usable) continue;
            if (best == null || (v.optBoolean("nc", false) ? 1 : 0) > (best.optBoolean("nc", false) ? 1 : 0)
                    || v.optInt("resolution", 0) > best.optInt("resolution", 0)) best = v;
        }
        return best;
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
        t.malId = externalId(anime.optJSONArray("resources"), "MyAnimeList");
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

    public static String pickImage(JSONArray images, String facet) {
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

    public static int externalId(JSONArray resources, String site) {
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

    private static ArrayList<Track> anisongSearch(String query) throws IOException {
        try {
            JSONObject body = new JSONObject();
            jsonPut(body, "anime_search_filter", object("search", query, "partial_match", true));
            jsonPut(body, "song_name_search_filter", object("search", query, "partial_match", true));
            JSONObject artist = new JSONObject();
            jsonPut(artist, "search", query);
            jsonPut(artist, "partial_match", true);
            jsonPut(artist, "group_granularity", 0);
            jsonPut(artist, "max_other_artist", 99);
            jsonPut(body, "artist_search_filter", artist);
            jsonPut(body, "and_logic", false);
            addAnisongFilters(body);
            JSONArray list = postArray(ANISONG_DB + "/search_request", body.toString());
            return anisongArrayToTracks(list);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("AnisongDB недоступен", e);
        }
    }

    private static ArrayList<Track> anisongForAnime(int malId, String name) throws IOException {
        try {
            JSONArray list = null;
            if (malId > 0) {
                JSONObject body = new JSONObject();
                JSONArray ids = new JSONArray();
                ids.put(malId);
                jsonPut(body, "malIds", ids);
                addAnisongFilters(body);
                try { list = postArray(ANISONG_DB + "/malIDs_request", body.toString()); } catch (IOException ignored) { }
            }
            if (list == null || list.length() == 0) {
                JSONObject body = new JSONObject();
                jsonPut(body, "anime_search_filter", object("search", name, "partial_match", false));
                jsonPut(body, "and_logic", false);
                addAnisongFilters(body);
                list = postArray(ANISONG_DB + "/search_request", body.toString());
            }
            return anisongArrayToTracks(list);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("AnisongDB недоступен", e);
        }
    }

    private static void addAnisongFilters(JSONObject body) throws Exception {
        jsonPut(body, "ignore_duplicate", false);
        jsonPut(body, "opening_filter", true);
        jsonPut(body, "ending_filter", true);
        jsonPut(body, "insert_filter", true);
    }

    private static JSONObject object(String key1, Object value1, String key2, Object value2) throws Exception {
        JSONObject o = new JSONObject();
        jsonPut(o, key1, value1);
        jsonPut(o, key2, value2);
        return o;
    }

    private static void jsonPut(JSONObject object, String key, Object value) throws Exception {
        object.put(key, value);
    }

    private static ArrayList<Track> anisongArrayToTracks(JSONArray list) {
        ArrayList<Track> out = new ArrayList<>();
        if (list == null) return out;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject s = list.optJSONObject(i);
            if (s == null) continue;
            int id = s.optInt("annSongId", -1);
            if (!seen.add(id)) continue;
            Track t = anisongToTrack(s);
            if (t != null) out.add(t);
        }
        Collections.sort(out, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) { return typeRank(a.type) - typeRank(b.type); }
        });
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
        String lower = kind.toLowerCase(Locale.US);
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
        t.artist = s.optString("songArtist", "Неизвестный исполнитель");
        t.animeName = s.optString("animeJPName", s.optString("animeENName", "Неизвестное аниме"));
        t.audioUrl = audio;
        t.videoUrl = video;
        t.resolution = s.optString("HQ", "").isEmpty() ? 480 : 720;
        t.tags = s.optString("HQ", "").isEmpty() ? "MQ" : "HQ";
        t.source = "extra";
        JSONObject links = s.optJSONObject("linked_ids");
        t.malId = linkedNumber(links, "mal");
        if (t.malId < 0) t.malId = linkedNumber(links, "myanimelist");
        t.animeSlug = t.malId > 0 ? "mal-" + t.malId : "ann-" + s.optInt("annId", id);
        return t;
    }

    private static int linkedNumber(JSONObject links, String wanted) {
        if (links == null) return -1;
        java.util.Iterator<String> keys = links.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!key.toLowerCase(Locale.US).contains(wanted.toLowerCase(Locale.US))) continue;
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

    private static int typeRank(String type) {
        return "OP".equals(type) ? 0 : "ED".equals(type) ? 1 : 2;
    }

    private static JSONObject get(String endpoint, Map<String, String> params) throws IOException {
        try {
            return new JSONObject(requestText(endpoint + buildQuery(params), "GET", null));
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Неверный ответ сервера", e);
        }
    }

    private static JSONArray postArray(String endpoint, String body) throws IOException {
        try {
            return new JSONArray(requestText(endpoint, "POST", body));
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Неверный ответ AnisongDB", e);
        }
    }

    private static String requestText(String endpoint, String method, String body) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(22000);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (body != null) {
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                OutputStream out = connection.getOutputStream();
                out.write(payload);
                out.flush();
                out.close();
            }
            int code = connection.getResponseCode();
            InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String text = read(input);
            if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
            return text;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Сеть недоступна", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream input) throws IOException {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        return out.toString();
    }

    private static String encodePath(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private static String buildQuery(Map<String, String> params) throws IOException {
        if (params == null || params.isEmpty()) return "";
        StringBuilder out = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (!first) out.append('&');
            first = false;
            out.append(URLEncoder.encode(e.getKey(), "UTF-8"));
            out.append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }
        return out.toString();
    }
}
