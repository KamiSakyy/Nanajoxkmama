package com.anibeat.app.data;

import com.anibeat.app.core.Net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AnisongDB — вставки и редкие темы (перенос `src/api/anisongdb.ts`).
 * Медиа отдаётся с naedist.animemusicquiz.com (MP3 / WEBM).
 */
public final class AnisongDb {

    private static final String BASE = "https://anisongdb.com/api";
    private static final String MEDIA = "https://naedist.animemusicquiz.com";

    private AnisongDb() {
    }

    private static JSONObject filters() {
        JSONObject o = new JSONObject();
        try {
            o.put("ignore_duplicate", false);
            o.put("opening_filter", true);
            o.put("ending_filter", true);
            o.put("insert_filter", true);
        } catch (Exception ignored) {
        }
        return o;
    }

    private static JSONObject bodyWith(JSONObject extra) {
        JSONObject body = new JSONObject();
        try {
            java.util.Iterator<String> keys = extra.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                body.put(k, extra.get(k));
            }
            java.util.Iterator<String> f = filters().keys();
            while (f.hasNext()) {
                String k = f.next();
                body.put(k, filters().get(k));
            }
        } catch (Exception ignored) {
        }
        return body;
    }

    public static Models.Track toTrack(JSONObject s) {
        try {
            String audioPath = s.isNull("audio") ? null : s.optString("audio", null);
            String hq = s.isNull("HQ") ? null : s.optString("HQ", null);
            String mq = s.isNull("MQ") ? null : s.optString("MQ", null);
            String audio = audioPath != null && !audioPath.isEmpty() ? MEDIA + audioPath : null;
            String video = hq != null && !hq.isEmpty() ? MEDIA + hq : (mq != null && !mq.isEmpty() ? MEDIA + mq : null);
            if (audio == null && video == null) return null;

            String songType = s.optString("songType", "");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(Opening|Ending|Insert)\\s*(\\d+)?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(songType);
            String type = "IN";
            String slug = "IN";
            Integer sequence = null;
            if (m.find()) {
                String kind = m.group(1).toLowerCase();
                Integer n = m.group(2) != null ? Integer.parseInt(m.group(2)) : null;
                if ("opening".equals(kind)) {
                    type = "OP";
                    slug = "OP" + (n == null ? "" : n);
                    sequence = n;
                } else if ("ending".equals(kind)) {
                    type = "ED";
                    slug = "ED" + (n == null ? "" : n);
                    sequence = n;
                } else {
                    type = "IN";
                    slug = n == null ? "IN" : "IN" + n;
                    sequence = n;
                }
            }

            Models.Track t = new Models.Track();
            int annSongId = s.optInt("annSongId");
            t.id = "asdb:" + annSongId;
            t.themeId = -annSongId;
            t.themeSlug = slug;
            t.type = type;
            t.sequence = sequence;
            t.title = s.optString("songName", slug);
            JSONArray artists = s.optJSONArray("artists");
            if (artists != null && artists.length() > 0) {
                for (int i = 0; i < artists.length(); i++) {
                    JSONObject a = artists.optJSONObject(i);
                    if (a == null) continue;
                    Models.ArtistRef ref = new Models.ArtistRef();
                    ref.id = -a.optInt("id");
                    JSONArray names = a.optJSONArray("names");
                    ref.name = names != null && names.length() > 0 ? names.optString(0) : s.optString("songArtist", "");
                    ref.slug = "";
                    t.artists.add(ref);
                }
            } else {
                Models.ArtistRef ref = new Models.ArtistRef();
                ref.id = 0;
                ref.name = s.optString("songArtist", "Неизвестный исполнитель");
                ref.slug = "";
                t.artists.add(ref);
            }

            Models.AnimeRef anime = new Models.AnimeRef();
            int annId = s.optInt("annId");
            anime.id = -annId;
            String jp = s.optString("animeJPName", "");
            String en = s.optString("animeENName", "");
            anime.name = jp != null && !jp.isEmpty() ? jp : en;
            Object linked = s.opt("linked_ids");
            Integer malId = null;
            Integer anilistId = null;
            if (linked instanceof JSONObject) {
                JSONObject l = (JSONObject) linked;
                malId = first(l.opt("myanimelist"));
                anilistId = first(l.opt("anilist"));
            }
            anime.malId = malId;
            anime.anilistId = anilistId;
            anime.slug = malId != null ? "mal-" + malId : "ann-" + annId;
            String vintage = s.isNull("animeVintage") ? null : s.optString("animeVintage", null);
            if (vintage != null) {
                java.util.regex.Matcher vm = java.util.regex.Pattern.compile("(Winter|Spring|Summer|Fall)\\s+(\\d{4})", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(vintage);
                if (vm.find()) {
                    String season = vm.group(1);
                    season = season.substring(0, 1).toUpperCase() + season.substring(1).toLowerCase();
                    anime.season = season;
                    anime.year = Integer.parseInt(vm.group(2));
                }
            }
            t.anime = anime;

            // Музыка — только аудио: видео-ссылку в аудио не подставляем.
            t.audioUrl = audio != null ? audio : "";
            t.videoUrl = video != null ? video : "";
            t.resolution = hq != null && !hq.isEmpty() ? 720 : (mq != null && !mq.isEmpty() ? 480 : null);
            t.tags = hq != null && !hq.isEmpty() ? "HQ" : (mq != null && !mq.isEmpty() ? "MQ" : "");
            t.source = "extra";
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer first(Object value) {
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            return arr.length() > 0 ? arr.optInt(0) : null;
        }
        if (value instanceof Number) return ((Number) value).intValue();
        return null;
    }

    private static List<Models.Track> mapAll(JSONArray array) {
        List<Models.Track> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.optJSONObject(i);
            if (o == null) continue;
            int id = o.optInt("annSongId");
            if (!seen.add(id)) continue;
            Models.Track t = toTrack(o);
            if (t != null) out.add(t);
            if (out.size() >= 60) break;
        }
        return out;
    }

    /** Поиск по базе (расширенный режим поиска). */
    public static void search(String query, Api.Cb<List<Models.Track>> cb) {
        JSONObject params = new JSONObject();
        try {
            JSONObject anime = new JSONObject();
            anime.put("search", query);
            anime.put("partial_match", true);
            JSONObject song = new JSONObject();
            song.put("search", query);
            song.put("partial_match", true);
            JSONObject artist = new JSONObject();
            artist.put("search", query);
            artist.put("partial_match", true);
            artist.put("group_granularity", 0);
            artist.put("max_other_artist", 99);
            params.put("anime_search_filter", anime);
            params.put("song_name_search_filter", song);
            params.put("artist_search_filter", artist);
            params.put("and_logic", false);
        } catch (Exception ignored) {
        }
        Net.post(BASE + "/search_request", bodyWith(params).toString(), (json, error) -> {
            if (json == null) {
                cb.on(new ArrayList<>(), error);
                return;
            }
            JSONArray array = json.optJSONArray("array");
            cb.on(mapAll(array), error);
        });
    }

    /** Все песни аниме: сначала по MAL id, затем точным совпадением имени. */
    public static void forAnime(Integer malId, List<String> names, Api.Cb<List<Models.Track>> cb) {
        if (malId != null) {
            JSONObject body = new JSONObject();
            try {
                JSONArray ids = new JSONArray();
                ids.put(malId);
                body.put("malIds", ids);
            } catch (Exception ignored) {
            }
            Net.post(BASE + "/malIDs_request", bodyWith(body).toString(), (json, error) -> {
                JSONArray array = json == null ? null : json.optJSONArray("array");
                List<Models.Track> tracks = mapAll(array);
                if (!tracks.isEmpty()) {
                    sortInPlace(tracks);
                    cb.on(tracks, error);
                } else {
                    byNames(names, 0, cb);
                }
            });
        } else {
            byNames(names, 0, cb);
        }
    }

    private static void byNames(List<String> names, int index, Api.Cb<List<Models.Track>> cb) {
        if (names == null || index >= Math.min(2, names.size())) {
            cb.on(new ArrayList<>(), null);
            return;
        }
        String name = names.get(index);
        if (name == null || name.isEmpty()) {
            byNames(names, index + 1, cb);
            return;
        }
        JSONObject params = new JSONObject();
        try {
            JSONObject anime = new JSONObject();
            anime.put("search", name);
            anime.put("partial_match", false);
            params.put("anime_search_filter", anime);
            params.put("and_logic", false);
        } catch (Exception ignored) {
        }
        Net.post(BASE + "/search_request", bodyWith(params).toString(), (json, error) -> {
            JSONArray array = json == null ? null : json.optJSONArray("array");
            List<Models.Track> tracks = mapAll(array);
            if (!tracks.isEmpty()) {
                sortInPlace(tracks);
                cb.on(tracks, error);
            } else {
                byNames(names, index + 1, cb);
            }
        });
    }

    private static void sortInPlace(List<Models.Track> tracks) {
        tracks.sort((a, b) -> {
            int oa = order(a.type);
            int ob = order(b.type);
            if (oa != ob) return oa - ob;
            int sa = a.sequence == null ? 0 : a.sequence;
            int sb = b.sequence == null ? 0 : b.sequence;
            return sa - sb;
        });
    }

    private static int order(String type) {
        if ("OP".equals(type)) return 0;
        if ("ED".equals(type)) return 1;
        return 2;
    }

    /** 50 случайных песен — для «Радио». */
    public static void random(Api.Cb<List<Models.Track>> cb) {
        Net.post(BASE + "/get_50_random_songs", "{}", (json, error) -> {
            JSONArray array = json == null ? null : json.optJSONArray("array");
            cb.on(mapAll(array), error);
        });
    }
}
