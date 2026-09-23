package com.anibeat.app.data;

import static com.anibeat.app.data.Models.AnimeMeta;

import com.anibeat.app.core.Net;
import com.anibeat.app.core.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Обогащение метаданными по MyAnimeList id (== Shikimori id):
 *  • Shikimori — русские названия, оценка, жанры, описание, постеры;
 *  • AniList   — постеры extraLarge, баннеры, акцентный цвет, жанры.
 * Полный перенос `src/api/meta.ts` (с зеркалами Shikimori и кэшем на 14 дней).
 */
public final class Meta {

    public interface Listener {
        void onMetaChanged();
    }

    private static final String ANILIST = "https://graphql.anilist.co";
    private static final String M = "shikimori";
    public static final String[] SHIKI_HOSTS = {"https://" + M + ".tv", "https://" + M + ".one", "https://" + M + ".io", "https://" + M + ".me"};
    private static final String ANILIST_QUERY = "query($ids:[Int]){Page(perPage:50){media(idMal_in:$ids,type:ANIME){id idMal coverImage{extraLarge large color} bannerImage averageScore genres}}}";

    private static final Map<Integer, AnimeMeta> STORE = new HashMap<>();
    private static final Set<Integer> REQUESTED = new HashSet<>();
    private static final Set<Integer> PENDING = new HashSet<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Set<String> NONE = new HashSet<>();

    private static String shikiHost;
    private static boolean probing;
    private static final List<Runnable> PROBE_WAITERS = new ArrayList<>();
    private static long lastFlush;

    private Meta() {
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void emit() {
        for (Listener l : new ArrayList<>(LISTENERS)) {
            try {
                l.onMetaChanged();
            } catch (Throwable t) {
                com.anibeat.app.core.Ui.report(t);
            }
        }
    }

    /** Метаданные только из памяти: без обращения к сети. Для отрисовки списков. */
    public static AnimeMeta peek(Integer malId) {
        if (malId == null) return null;
        return STORE.get(malId);
    }

    /** Прогреть метаданные пачкой (русские названия подтянутся фоном, без тормозов списка). */
    public static void warmAll(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<Integer> fresh = new ArrayList<>();
        for (Integer id : ids) {
            if (id == null || id <= 0) continue;
            if (REQUESTED.contains(id) || STORE.containsKey(id) || NONE.contains(String.valueOf(id))) continue;
            fresh.add(id);
        }
        if (!fresh.isEmpty()) warm(fresh);
    }

    /** Метаданные из памяти; null — ещё не загружены. */
    public static AnimeMeta get(Integer malId) {
        if (malId == null) return null;
        AnimeMeta meta = STORE.get(malId);
        if (meta == null && !REQUESTED.contains(malId) && !NONE.contains(String.valueOf(malId))) {
            List<Integer> one = new ArrayList<>();
            one.add(malId);
            warm(one);
        }
        return meta;
    }

    public static void warm(List<Integer> ids) {
        boolean added = false;
        for (Integer id : ids) {
            if (id == null || id <= 0) continue;
            if (REQUESTED.contains(id) || STORE.containsKey(id)) continue;
            REQUESTED.add(id);
            PENDING.add(id);
            added = true;
        }
        if (added) flushSoon();
    }

    private static void flushSoon() {
        long now = System.currentTimeMillis();
        if (now - lastFlush < 40) {
            return;
        }
        lastFlush = now;
        flush();
    }

    private static void flush() {
        final List<Integer> ids = new ArrayList<>(PENDING);
        PENDING.clear();
        if (ids.isEmpty()) return;

        List<Integer> stale = new ArrayList<>();
        for (Integer id : ids) {
            JSONObject cached = Prefs.getString("meta." + id, "").isEmpty() ? null : parse(Prefs.getString("meta." + id, ""));
            if (cached != null && System.currentTimeMillis() - cached.optLong("ts") < 14 * Net.DAY) {
                if ("none".equals(cached.optString("kind"))) {
                    NONE.add(String.valueOf(id));
                } else {
                    STORE.put(id, AnimeMeta.fromJson(cached));
                }
            } else {
                stale.add(id);
            }
        }
        emit();
        if (stale.isEmpty()) return;

        fetchShiki(stale, shiki -> fetchAniList(stale, anilist -> {
            for (Integer id : stale) {
                AnimeMeta meta = STORE.get(id);
                JSONObject shikiHit = shiki.get(id);
                if (shikiHit != null) meta = fromShiki(shikiHit, meta);
                JSONObject al = anilist.get(id);
                if (al != null) meta = mergeAniList(meta, al, id);
                if (meta != null) STORE.put(id, meta);
                if (meta != null) {
                    Prefs.put("meta." + id, meta.toJson().toString());
                } else if (!shiki.isEmpty() || !anilist.isEmpty()) {
                    JSONObject none = new JSONObject();
                    try {
                        none.put("kind", "none");
                        none.put("ts", System.currentTimeMillis());
                    } catch (Exception ignored) {
                    }
                    NONE.add(String.valueOf(id));
                    Prefs.put("meta." + id, none.toString());
                } else {
                    REQUESTED.remove(id); // оба провайдера упали — разрешаем повтор позже
                }
            }
            emit();
        }));
    }

    private static JSONObject parse(String raw) {
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Поиск по русскому названию: Shikimori отдаёт список аниме, из него берём MyAnimeList id.
     * Дальше id превращаются в тайтлы AniThemes — так работает русский поиск.
     */
    public static void searchShikimori(final String query, final Net.Callback<List<Integer>> cb) {
        if (query == null || query.trim().isEmpty()) {
            cb.onResult(new ArrayList<>(), null);
            return;
        }
        resolveHost(host -> {
            if (host == null) {
                cb.onResult(new ArrayList<>(), null);
                return;
            }
            String url = host + "/api/animes?search=" + Net.encode(query.trim())
                    + "&limit=20&order=popularity&kind=tv,movie,ova,ona,special";
            Net.getLow(url, 6 * Net.HOUR, 7 * Net.DAY, (json, error) -> {
                List<Integer> ids = new ArrayList<>();
                JSONArray list = json == null ? null : json.optJSONArray("array");
                if (list == null && json != null) list = json.optJSONArray("animes");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject a = list.optJSONObject(i);
                        if (a == null) continue;
                        int id = a.optInt("id");
                        if (id > 0 && !ids.contains(id)) ids.add(id);
                    }
                }
                cb.onResult(ids, ids.isEmpty() ? error : null);
            });
        });
    }

    /* ------------------------------------------------------------------ */
    /* Shikimori                                                           */
    /* ------------------------------------------------------------------ */

    public static String shikiHostCached() {
        String cached = Prefs.getString("anibeat:mhost", "");
        if (!cached.isEmpty()) {
            JSONObject o = parse(cached);
            if (o != null && System.currentTimeMillis() - o.optLong("ts") < 12 * Net.HOUR) {
                String host = o.optString("host");
                for (String h : SHIKI_HOSTS) if (h.equals(host)) return host;
            }
        }
        return null;
    }

    private static void resolveHost(java.util.function.Consumer<String> cb) {
        if (shikiHost != null) {
            cb.accept(shikiHost);
            return;
        }
        String cached = shikiHostCached();
        if (cached != null) {
            shikiHost = cached;
            cb.accept(cached);
            return;
        }
        if (probing) {
            PROBE_WAITERS.add(() -> cb.accept(shikiHost));
            return;
        }
        probing = true;
        final int[] left = {SHIKI_HOSTS.length};
        final boolean[] done = {false};
        for (String host : SHIKI_HOSTS) {
            final String h = host;
            String url = h + "/api/animes?limit=1";
            Net.getLow(url, Net.HOUR, Net.DAY, (json, error) -> {
                if (!done[0] && json != null) {
                    done[0] = true;
                    shikiHost = h;
                    try {
                        JSONObject o = new JSONObject();
                        o.put("host", h);
                        o.put("ts", System.currentTimeMillis());
                        Prefs.put("anibeat:mhost", o.toString());
                    } catch (Exception ignored) {
                    }
                    probing = false;
                    for (Runnable r : new ArrayList<>(PROBE_WAITERS)) r.run();
                    PROBE_WAITERS.clear();
                    cb.accept(h);
                    return;
                }
                left[0]--;
                if (left[0] == 0 && !done[0]) {
                    probing = false;
                    cb.accept(null);
                }
            });
        }
    }

    private interface ShikiCb {
        void on(Map<Integer, JSONObject> hits);
    }

    private static void fetchShiki(List<Integer> ids, ShikiCb cb) {
        resolveHost(host -> {
            Map<Integer, JSONObject> out = new HashMap<>();
            if (host == null) {
                cb.on(out);
                return;
            }
            String url = host + "/api/animes?ids=" + join(ids) + "&limit=50";
            Net.getLow(url, 7 * Net.DAY, 60 * Net.DAY, (json, error) -> {
                if (json != null) {
                    JSONArray array = json.optJSONArray("array");
                    if (array == null) {
                        // одиночный объект
                        JSONObject single = json.optJSONObject("anime");
                        if (single != null) out.put(single.optInt("id"), single);
                    } else {
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject a = array.optJSONObject(i);
                            if (a != null) out.put(a.optInt("id"), a);
                        }
                    }
                }
                cb.on(out);
            });
        });
    }

    private static String join(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private static boolean missing(String url) {
        return url == null || url.isEmpty() || url.contains("missing_");
    }

    private static String shikiImg(String path) {
        if (missing(path)) return null;
        if (path.startsWith("http")) return path;
        String host = shikiHost != null ? shikiHost : shikiHostCached();
        return (host == null ? SHIKI_HOSTS[0] : host) + path;
    }

    private static AnimeMeta fromShiki(JSONObject a, AnimeMeta prev) {
        AnimeMeta meta = prev != null ? prev : new AnimeMeta();
        meta.malId = a.optInt("id");
        String ru = a.isNull("russian") ? null : a.optString("russian", null);
        meta.ru = ru != null && !ru.isEmpty() ? ru : (prev != null ? prev.ru : null);
        meta.name = a.optString("name", null);
        meta.posterShiki = shikiImg(a.optJSONObject("image") != null ? a.optJSONObject("image").optString("original", null) : null);
        if (meta.poster == null) meta.poster = meta.posterShiki;
        if (a.has("score") && !a.isNull("score")) {
            try {
                meta.score = Double.parseDouble(a.optString("score"));
            } catch (Exception ignored) {
            }
        }
        meta.kind = a.isNull("kind") ? null : a.optString("kind", null);
        meta.status = a.isNull("status") ? null : a.optString("status", null);
        meta.episodes = a.has("episodes") && !a.isNull("episodes") ? a.optInt("episodes") : null;
        JSONArray genres = a.optJSONArray("genres");
        List<String> list = new ArrayList<>();
        if (genres != null) {
            for (int i = 0; i < genres.length(); i++) {
                JSONObject g = genres.optJSONObject(i);
                if (g == null) continue;
                String value = g.optString("russian", null);
                if (value == null || value.isEmpty()) value = g.optString("name", null);
                if (value != null && !value.isEmpty()) list.add(value.trim());
            }
        }
        if (!list.isEmpty()) meta.genres = list;
        meta.ts = System.currentTimeMillis();
        return meta;
    }

    private static AnimeMeta mergeAniList(AnimeMeta meta, JSONObject media, int malId) {
        AnimeMeta base = meta != null ? meta : new AnimeMeta();
        base.malId = malId;
        JSONObject cover = media.optJSONObject("coverImage");
        String xl = cover != null && !cover.isNull("extraLarge") ? cover.optString("extraLarge", null) : null;
        String large = cover != null && !cover.isNull("large") ? cover.optString("large", null) : null;
        if (xl != null) base.poster = xl;
        else if (base.poster == null) base.poster = large;
        if (base.posterShiki == null) base.posterShiki = large;
        if (!media.isNull("bannerImage")) base.banner = media.optString("bannerImage", null);
        if (cover != null && !cover.isNull("color")) base.color = cover.optString("color", null);
        if (base.score == null && media.has("averageScore") && !media.isNull("averageScore")) {
            base.score = media.optDouble("averageScore") / 10.0;
        }
        JSONArray genres = media.optJSONArray("genres");
        if ((base.genres == null || base.genres.isEmpty()) && genres != null) {
            List<String> list = new ArrayList<>();
            for (int i = 0; i < genres.length(); i++) list.add(genres.optString(i));
            base.genres = list;
        }
        base.ts = System.currentTimeMillis();
        return base;
    }

    private static void fetchAniList(List<Integer> ids, ShikiCb cb) {
        final Map<Integer, JSONObject> out = new HashMap<>();
        final int chunkSize = 50;
        final int[] index = {0};
        final Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (index[0] >= ids.size()) {
                cb.on(out);
                return;
            }
            int from = index[0];
            int to = Math.min(ids.size(), from + chunkSize);
            List<Integer> chunk = new ArrayList<>(ids.subList(from, to));
            java.util.Collections.sort(chunk);
            index[0] = to;
            JSONObject body = new JSONObject();
            try {
                body.put("query", ANILIST_QUERY);
                JSONObject vars = new JSONObject();
                JSONArray arr = new JSONArray();
                for (Integer id : chunk) arr.put(id);
                vars.put("ids", arr);
                body.put("variables", vars);
            } catch (Exception ignored) {
            }
            String url = ANILIST;
            Net.post(url, body.toString(), (json, error) -> {
                if (json != null) {
                    JSONObject data = json.optJSONObject("data");
                    JSONObject page = data == null ? null : data.optJSONObject("Page");
                    JSONArray media = page == null ? null : page.optJSONArray("media");
                    if (media != null) {
                        for (int i = 0; i < media.length(); i++) {
                            JSONObject m = media.optJSONObject(i);
                            if (m == null) continue;
                            int mal = m.optInt("idMal");
                            if (mal > 0) out.put(mal, m);
                        }
                    }
                }
                step[0].run();
            });
        };
        step[0].run();
    }

    /** Тексты с Shikimori приходят в HTML — вырезаем теги. */
    public static String cleanText(String value) {
        if (value == null) return null;
        String text = value.replaceAll("\\[.*?]", "").replaceAll("<[^>]+>", "").replace("&quot;", "\"").replace("&amp;", "&").trim();
        return text.isEmpty() ? null : text;
    }

    /** Жанры аниме для фильтра каталога. */
    public static List<String> genresOf(Integer malId) {
        AnimeMeta meta = get(malId);
        if (meta == null || meta.genres == null) return new ArrayList<>();
        return meta.genres;
    }

    /* ------------------------------------------------------------------ */
    /* Поиск и детали (Shikimori) — порт searchShikimori / getShikiDetails */
    /* ------------------------------------------------------------------ */

    /** Результат поиска на Shikimori. */
    public static class ShikiHit {
        public int malId;
        public String ru;
        public String name = "";
        public String poster;
        public String kind;
        public Double score;

        public String title() {
            return ru != null && !ru.isEmpty() ? ru : name;
        }
    }

    /** Расширенные данные аниме из Shikimori. */
    public static class ShikiDetails {
        public int malId;
        public String ru;
        public String name = "";
        public String description;
        public List<String> genres = new ArrayList<>();
        public List<String> studios = new ArrayList<>();
        public Double score;
        public String rating;
        public Integer episodes;
        public String kind;
        public String status;
        public String airedOn;
        public String url = "";
        public String poster;
    }

    public static final Map<String, String> KIND_RU = new HashMap<>();
    public static final Map<String, String> STATUS_RU = new HashMap<>();
    public static final Map<String, String> RATING_RU = new HashMap<>();
    static {
        KIND_RU.put("tv", "TV-сериал");
        KIND_RU.put("movie", "Фильм");
        KIND_RU.put("ova", "OVA");
        KIND_RU.put("ona", "ONA");
        KIND_RU.put("special", "Спешл");
        KIND_RU.put("music", "Клип");
        KIND_RU.put("tv_special", "TV-спешл");
        KIND_RU.put("pv", "PV");
        KIND_RU.put("cm", "Реклама");
        STATUS_RU.put("anons", "Анонс");
        STATUS_RU.put("ongoing", "Онгоинг");
        STATUS_RU.put("released", "Вышло");
        RATING_RU.put("g", "0+");
        RATING_RU.put("pg", "6+");
        RATING_RU.put("pg_13", "13+");
        RATING_RU.put("r", "17+");
        RATING_RU.put("r_plus", "17+");
        RATING_RU.put("rx", "18+");
    }

    private static final java.util.regex.Pattern SHIKI_RE = java.util.regex.Pattern.compile("https://shikimori\\.(tv|one|io|me)");

    /** Подменяет host у ссылок Shikimori на найденное рабочее зеркало. */
    public static String fixShikiHost(String url) {
        if (url == null || url.isEmpty()) return url;
        String host = shikiHost != null ? shikiHost : shikiHostCached();
        if (host == null) return url;
        java.util.regex.Matcher m = SHIKI_RE.matcher(url);
        if (!m.find()) return url;
        String tld = host.replaceFirst("^https://shikimori\\.", "");
        return m.replaceFirst("https://shikimori." + tld);
    }

    /** Чистит bbcode/тэги в описаниях Shikimori. */
    public static String cleanShikiText(String value) {
        if (value == null || value.isEmpty()) return null;
        String s = value;
        s = s.replaceAll("\\[spoiler[^\\]]*\\][\\s\\S]*?\\[/spoiler\\]", "");
        s = s.replaceAll("\\[(character|person|anime|manga|ranobe|url|entry|comment|topic|user|image|poster|div|span|color|size)[^\\]]*\\]([\\s\\S]*?)\\[/\\1\\]", "$2");
        s = s.replaceAll("\\[/?(b|i|u|s|br|hr|center|right|quote|list|\\*)]", "");
        s = s.replaceAll("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?\\]\\]", "$1");
        s = s.replaceAll("\\[[^\\]]{1,40}\\]", "");
        s = s.replaceAll("\\n{3,}", "\\n\\n");
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    /** Поиск аниме по названию (русскому или латиницей). */
    public static void searchShikimori(String query, Api.Cb<List<ShikiHit>> cb) {
        if (query == null || query.trim().isEmpty()) {
            cb.on(new ArrayList<>(), null);
            return;
        }
        resolveHost(host -> {
            if (host == null) {
                cb.on(new ArrayList<>(), null);
                return;
            }
            String url = host + "/api/animes?search=" + Net.encode(query.trim()) + "&limit=10";
            Net.getLow(url, Net.HOUR, 7 * Net.DAY, (json, error) -> {
                JSONArray list = json == null ? null : json.optJSONArray("array");
                if (list == null && json != null) list = json.optJSONArray("json");
                if (list == null && json != null) list = json.optJSONArray("data");
                List<ShikiHit> out = new ArrayList<>();
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject a = list.optJSONObject(i);
                        if (a == null) continue;
                        int id = a.optInt("id");
                        if (id <= 0) continue;
                        ShikiHit hit = new ShikiHit();
                        hit.malId = id;
                        hit.ru = emptyToNull(a.optString("russian"));
                        hit.name = a.optString("name");
                        JSONObject image = a.optJSONObject("image");
                        hit.poster = image == null ? null : shikiImg(image.optString("original"));
                        hit.kind = emptyToNull(a.optString("kind"));
                        String score = a.optString("score");
                        if (score != null && !score.isEmpty() && !"0.0".equals(score)) {
                            try {
                                hit.score = Double.parseDouble(score);
                            } catch (Exception ignored) {
                            }
                        }
                        if (!STORE.containsKey(id)) {
                            AnimeMeta meta = new AnimeMeta();
                            meta.malId = id;
                            meta.ru = hit.ru;
                            meta.name = hit.name;
                            meta.posterShiki = hit.poster;
                            meta.kind = hit.kind;
                            meta.score = hit.score;
                            meta.ts = System.currentTimeMillis();
                            STORE.put(id, meta);
                            REQUESTED.add(id);
                            emit();
                        }
                        out.add(hit);
                    }
                }
                cb.on(out, error);
            });
        });
    }

    /** Полные данные аниме (описание, жанры, оценка) одним запросом. */
    public static void getShikiDetails(int malId, Api.Cb<ShikiDetails> cb) {
        resolveHost(host -> {
            if (host == null) {
                cb.on(null, null);
                return;
            }
            String url = host + "/api/animes/" + malId;
            Net.getLow(url, 7 * Net.DAY, 60 * Net.DAY, (json, error) -> {
                if (json == null) {
                    cb.on(null, error);
                    return;
                }
                ShikiDetails d = new ShikiDetails();
                d.malId = json.optInt("id", malId);
                d.ru = emptyToNull(json.optString("russian"));
                d.name = json.optString("name");
                d.description = cleanShikiText(json.optString("description"));
                JSONArray genres = json.optJSONArray("genres");
                if (genres != null) {
                    for (int i = 0; i < genres.length(); i++) {
                        JSONObject g = genres.optJSONObject(i);
                        if (g == null) continue;
                        String label = g.optString("russian");
                        if (label.isEmpty()) label = g.optString("name");
                        if (!label.isEmpty()) d.genres.add(label);
                    }
                }
                JSONArray studios = json.optJSONArray("studios");
                if (studios != null) {
                    for (int i = 0; i < studios.length(); i++) {
                        JSONObject st = studios.optJSONObject(i);
                        if (st != null && !st.optString("name").isEmpty()) d.studios.add(st.optString("name"));
                    }
                }
                String score = json.optString("score");
                if (score != null && !score.isEmpty()) {
                    try {
                        d.score = Double.parseDouble(score);
                    } catch (Exception ignored) {
                    }
                }
                d.rating = emptyToNull(json.optString("rating"));
                d.episodes = json.isNull("episodes") ? null : json.optInt("episodes");
                if (d.episodes != null && d.episodes <= 0) d.episodes = null;
                d.kind = emptyToNull(json.optString("kind"));
                d.status = emptyToNull(json.optString("status"));
                d.airedOn = emptyToNull(json.optString("aired_on"));
                String link = json.optString("url");
                d.url = link.isEmpty() ? host + "/animes/" + malId : host + link;
                JSONObject image = json.optJSONObject("image");
                d.poster = image == null ? null : shikiImg(image.optString("original"));

                AnimeMeta meta = STORE.get(d.malId);
                if (meta == null) meta = new AnimeMeta();
                meta.malId = d.malId;
                meta.ru = d.ru;
                meta.name = d.name;
                if (d.poster != null) meta.posterShiki = d.poster;
                meta.kind = d.kind;
                meta.status = d.status;
                meta.score = d.score;
                meta.episodes = d.episodes;
                if (!d.genres.isEmpty()) meta.genres = new ArrayList<>(d.genres);
                meta.ts = System.currentTimeMillis();
                STORE.put(d.malId, meta);
                REQUESTED.add(d.malId);
                emit();
                cb.on(d, null);
            });
        });
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() || "null".equals(value) ? null : value;
    }
}
