package com.kamisakyy.nanajoxkmama;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Anime metadata enrichment keyed by MyAnimeList id — the Android port of api/meta.ts:
 *  • Shikimori — Russian titles, score, kind, description & genres (mirror race)
 *  • AniList   — full-resolution posters, banners, accent color
 * Best-effort only: failures never block the AnimeThemes core.
 */
public final class MetaApi {
    public static final class AnimeMeta {
        public int malId;
        public String ru = "";
        public String name = "";
        public String poster = "";
        public String posterShiki = "";
        public String banner = "";
        public String color = "";
        public double score = -1;
        public String kind = "";
        public int episodes = -1;
        public String status = "";
        public String genres = "";
        public String shikiUrl = "";
        public long ts;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("malId", malId);
                o.put("ru", ru);
                o.put("name", name);
                o.put("poster", poster);
                o.put("posterShiki", posterShiki);
                o.put("banner", banner);
                o.put("color", color);
                o.put("score", score);
                o.put("kind", kind);
                o.put("episodes", episodes);
                o.put("status", status);
                o.put("genres", genres);
                o.put("shikiUrl", shikiUrl);
                o.put("ts", ts);
            } catch (Exception ignored) { }
            return o;
        }

        public static AnimeMeta fromJson(JSONObject o) {
            AnimeMeta m = new AnimeMeta();
            m.malId = o.optInt("malId");
            m.ru = o.optString("ru", "");
            m.name = o.optString("name", "");
            m.poster = o.optString("poster", "");
            m.posterShiki = o.optString("posterShiki", "");
            m.banner = o.optString("banner", "");
            m.color = o.optString("color", "");
            m.score = o.optDouble("score", -1);
            m.kind = o.optString("kind", "");
            m.episodes = o.optInt("episodes", -1);
            m.status = o.optString("status", "");
            m.genres = o.optString("genres", "");
            m.shikiUrl = o.optString("shikiUrl", "");
            m.ts = o.optLong("ts", 0L);
            return m;
        }
    }

    /** Shikimori details for the anime page (description, studios, rating…). */
    public static final class ShikiDetails {
        public int malId;
        public String ru = "";
        public String name = "";
        public String description = "";
        public String genres = "";
        public String studios = "";
        public double score = -1;
        public String rating = "";
        public int duration = -1;
        public int episodes = -1;
        public String kind = "";
        public String status = "";
        public String airedOn = "";
        public String url = "";
        public String poster = "";
    }

    public static final class ShikiHit {
        public int malId;
        public String ru = "";
        public String name = "";
        public String poster = "";
        public String kind = "";
        public double score = -1;
    }

    public static final Map<String, String> KIND_RU = new LinkedHashMap<>();
    public static final Map<String, String> STATUS_RU = new LinkedHashMap<>();
    public static final Map<String, String> RATING_RU = new LinkedHashMap<>();
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

    private static final String ANILIST = "https://graphql.anilist.co";
    private static final String[] SHIKI_HOSTS = {
        "https://shikimori.tv", "https://shikimori.one", "https://shikimori.io", "https://shikimori.me"
    };
    private static final long HOST_TTL = 12 * HttpCache.HOUR;

    private static final Map<Integer, AnimeMeta> STORE = new ConcurrentHashMap<>();
    private static final Set<Integer> REQUESTED = new HashSet<>();
    private static final List<Integer> PENDING = new ArrayList<>();
    private static final ExecutorService FLUSH = Executors.newSingleThreadExecutor();
    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static volatile String shikiHost;
    private static volatile long shikiHostTs;
    private static volatile File metaDir;

    private MetaApi() { }

    public static void init(File dir) {
        metaDir = dir;
    }

    /* ---------------- mirror discovery ---------------- */

    public static synchronized String resolveShikiHost() {
        long now = System.currentTimeMillis();
        if (shikiHost != null && now - shikiHostTs < HOST_TTL) return shikiHost;
        final AtomicReference<String> winner = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        for (String host : SHIKI_HOSTS) {
            IO.execute(() -> {
                try {
                    HttpCache.Policy p = new HttpCache.Policy().timeout(6000).retries(0).noStore();
                    JSONArray probe = HttpCache.getArray(host + "/api/animes?limit=1", p);
                    if (probe != null && winner.compareAndSet(null, host)) latch.countDown();
                } catch (Exception ignored) { }
            });
        }
        try { latch.await(7, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        shikiHost = winner.get() != null ? winner.get() : SHIKI_HOSTS[0];
        shikiHostTs = winner.get() != null ? System.currentTimeMillis() : 0L;
        return shikiHost;
    }

    /** Rewrite any shikimori.* url to the currently chosen mirror. */
    public static String fixShikiUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        return url.replaceAll("(?i)^https?://([a-z0-9-]+\\.)?shikimori\\.(one|io|me|org|tv|cc)",
                java.util.regex.Matcher.quoteReplacement((shikiHost == null ? SHIKI_HOSTS[0] : shikiHost)));
    }

    private static String shikiImg(String path) {
        if (path == null || path.isEmpty() || path.contains("missing_")) return "";
        if (path.startsWith("http")) return fixShikiUrl(path);
        return (shikiHost == null ? SHIKI_HOSTS[0] : shikiHost) + path;
    }

    /* ---------------- store ---------------- */

    public static AnimeMeta getMeta(int malId) {
        if (malId <= 0) return null;
        AnimeMeta m = STORE.get(malId);
        if (m == null) {
            AnimeMeta disk = readMeta(malId);
            if (disk != null && System.currentTimeMillis() - disk.ts < 14 * HttpCache.DAY) {
                STORE.put(malId, disk);
                return disk;
            }
            schedule(malId);
            return disk;
        }
        return m;
    }

    /** Request metadata for many ids at once (after a list loads). */
    public static void warm(List<Integer> ids) {
        List<Integer> add = new ArrayList<>();
        synchronized (REQUESTED) {
            for (Integer id : ids) {
                if (id == null || id <= 0) continue;
                if (REQUESTED.contains(id) || STORE.containsKey(id)) continue;
                REQUESTED.add(id);
                PENDING.add(id);
            }
            add.addAll(PENDING);
        }
        if (!add.isEmpty()) {
            PENDING.clear();
            FLUSH.execute(() -> flush(new ArrayList<>(add)));
        }
    }

    private static void schedule(int malId) {
        warm(java.util.Collections.singletonList(malId));
    }

    public static void warmTracks(List<Track> tracks) {
        List<Integer> ids = new ArrayList<>();
        for (Track t : tracks) if (t.malId > 0) ids.add(t.malId);
        warm(ids);
    }

    private static void flush(List<Integer> ids) {
        try {
            List<Integer> stale = new ArrayList<>();
            for (int id : ids) {
                AnimeMeta cached = STORE.get(id);
                if (cached == null) cached = readMeta(id);
                if (cached != null && System.currentTimeMillis() - cached.ts < 14 * HttpCache.DAY) {
                    STORE.put(id, cached);
                } else stale.add(id);
            }
            if (stale.isEmpty()) return;
            Map<Integer, JSONObject> shiki = fetchShiki(stale);
            Map<Integer, JSONObject> anilist = fetchAniList(stale);
            for (int id : stale) {
                AnimeMeta m = STORE.get(id);
                JSONObject s = shiki.get(id);
                if (s != null) m = fromShiki(s, m);
                JSONObject a = anilist.get(id);
                if (a != null) m = mergeAniList(m, a, id);
                if (m != null) {
                    m.malId = id;
                    m.ts = System.currentTimeMillis();
                    STORE.put(id, m);
                    writeMeta(id, m);
                } else if (!shiki.isEmpty() || !anilist.isEmpty()) {
                    AnimeMeta none = new AnimeMeta();
                    none.malId = id;
                    none.ts = System.currentTimeMillis();
                    writeMeta(id, none);
                } else {
                    synchronized (REQUESTED) { REQUESTED.remove(id); } // both failed → retry later
                }
            }
        } catch (Exception ignored) { }
    }

    private static Map<Integer, JSONObject> fetchShiki(List<Integer> ids) {
        Map<Integer, JSONObject> out = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i += 50) {
            List<Integer> chunk = ids.subList(i, Math.min(ids.size(), i + 50));
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < chunk.size(); k++) {
                if (k > 0) sb.append(',');
                sb.append(chunk.get(k));
            }
            try {
                String host = resolveShikiHost();
                JSONArray list = HttpCache.getArray(host + "/api/animes?ids=" + sb + "&limit=50",
                        new HttpCache.Policy().fresh(7 * HttpCache.DAY).maxAge(60 * HttpCache.DAY).retries(1).timeout(10000));
                for (int j = 0; j < list.length(); j++) {
                    JSONObject a = list.optJSONObject(j);
                    if (a != null) out.put(a.optInt("id"), a);
                }
            } catch (Exception e) {
                break;
            }
        }
        return out;
    }

    private static final String ANILIST_QUERY =
            "query($ids:[Int]){Page(perPage:50){media(idMal_in:$ids,type:ANIME){id idMal coverImage{extraLarge large color} bannerImage averageScore genres}}}";

    private static Map<Integer, JSONObject> fetchAniList(List<Integer> ids) {
        Map<Integer, JSONObject> out = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i += 50) {
            List<Integer> chunk = new ArrayList<>(ids.subList(i, Math.min(ids.size(), i + 50)));
            java.util.Collections.sort(chunk);
            try {
                JSONObject variables = new JSONObject();
                JSONArray arr = new JSONArray();
                for (int id : chunk) arr.put(id);
                variables.put("ids", arr);
                JSONObject body = new JSONObject();
                body.put("query", ANILIST_QUERY);
                body.put("variables", variables);
                JSONObject res = HttpCache.getJson(ANILIST,
                        new HttpCache.Policy().body(body.toString()).fresh(7 * HttpCache.DAY).maxAge(60 * HttpCache.DAY).timeout(12000).retries(1));
                JSONArray media = res.optJSONObject("data") == null ? null
                        : res.optJSONObject("data").optJSONObject("Page") == null ? null
                        : res.optJSONObject("data").optJSONObject("Page").optJSONArray("media");
                if (media != null) {
                    for (int j = 0; j < media.length(); j++) {
                        JSONObject m = media.optJSONObject(j);
                        if (m != null && m.has("idMal") && !m.isNull("idMal")) out.put(m.optInt("idMal"), m);
                    }
                }
            } catch (Exception e) {
                break;
            }
        }
        return out;
    }

    private static AnimeMeta fromShiki(JSONObject a, AnimeMeta prev) {
        AnimeMeta m = prev != null ? prev : new AnimeMeta();
        m.malId = a.optInt("id", m.malId);
        String russian = a.optString("russian", "");
        if (!russian.isEmpty()) m.ru = russian;
        String name = a.optString("name", "");
        if (!name.isEmpty()) m.name = name;
        m.posterShiki = shikiImg(a.optJSONObject("image") == null ? "" : a.optJSONObject("image").optString("original", ""));
        if (m.poster.isEmpty() && !m.posterShiki.isEmpty()) m.poster = m.posterShiki;
        JSONArray genres = a.optJSONArray("genres");
        if (genres != null && genres.length() > 0) {
            StringBuilder g = new StringBuilder();
            for (int i = 0; i < genres.length(); i++) {
                JSONObject gobj = genres.optJSONObject(i);
                if (gobj == null) continue;
                String gn = gobj.optString("russian", "");
                if (gn.isEmpty()) gn = gobj.optString("name", "");
                if (gn.isEmpty()) continue;
                if (g.length() > 0) g.append(" · ");
                g.append(gn);
            }
            m.genres = g.toString();
        }
        String score = a.optString("score", "");
        if (!score.isEmpty() && !"0".equals(score)) {
            try { m.score = Double.parseDouble(score); } catch (Exception ignored) { }
        }
        String kind = a.optString("kind", "");
        if (!kind.isEmpty()) m.kind = kind;
        m.episodes = a.optInt("episodes", m.episodes);
        String status = a.optString("status", "");
        if (!status.isEmpty()) m.status = status;
        String url = a.optString("url", "");
        m.shikiUrl = url.startsWith("http") ? fixShikiUrl(url) : (shikiHost == null ? SHIKI_HOSTS[0] : shikiHost) + url;
        return m;
    }

    private static AnimeMeta mergeAniList(AnimeMeta m, JSONObject media, int malId) {
        AnimeMeta base = m != null ? m : new AnimeMeta();
        base.malId = malId;
        JSONObject cover = media.optJSONObject("coverImage");
        String xl = cover == null ? "" : cover.optString("extraLarge", "");
        if (xl.isEmpty() && cover != null) xl = cover.optString("large", "");
        if (!xl.isEmpty()) base.poster = xl;
        String banner = media.optString("bannerImage", "");
        if (!banner.isEmpty()) base.banner = banner;
        String color = cover == null ? "" : cover.optString("color", "");
        if (!color.isEmpty()) base.color = color;
        double avg = media.optDouble("averageScore", -1);
        if (base.score < 0 && avg > 0) base.score = avg / 10.0;
        return base;
    }

    /* ---------------- disk ---------------- */

    private static File metaFile(int malId) {
        File dir = new File(metaDir, "meta");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, malId + ".json");
    }

    private static AnimeMeta readMeta(int malId) {
        try {
            File f = metaFile(malId);
            if (!f.exists()) return null;
            byte[] raw = readAll(f);
            AnimeMeta m = AnimeMeta.fromJson(new JSONObject(new String(raw, StandardCharsets.UTF_8)));
            return m.malId == 0 ? null : m;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeMeta(int malId, AnimeMeta m) {
        try {
            FileOutputStream out = new FileOutputStream(metaFile(malId));
            out.write(m.toJson().toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    private static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        int off = 0;
        while (off < data.length) {
            int r = in.read(data, off, data.length - off);
            if (r < 0) break;
            off += r;
        }
        in.close();
        return data;
    }

    /* ---------------- search & details ---------------- */

    public static ArrayList<ShikiHit> searchShikimori(String query) throws java.io.IOException {
        String host = resolveShikiHost();
        JSONArray list = HttpCache.getArray(host + "/api/animes?search=" +
                        java.net.URLEncoder.encode(query, "UTF-8") + "&limit=10",
                new HttpCache.Policy().fresh(HttpCache.HOUR).maxAge(7 * HttpCache.DAY).timeout(12000));
        ArrayList<ShikiHit> out = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject a = list.optJSONObject(i);
            if (a == null) continue;
            ShikiHit h = new ShikiHit();
            h.malId = a.optInt("id");
            h.ru = a.optString("russian", "");
            h.name = a.optString("name", "");
            h.poster = shikiImg(a.optJSONObject("image") == null ? "" : a.optJSONObject("image").optString("original", ""));
            h.kind = a.optString("kind", "");
            String score = a.optString("score", "");
            if (!score.isEmpty() && !"0".equals(score)) {
                try { h.score = Double.parseDouble(score); } catch (Exception ignored) { }
            }
            out.add(h);
            synchronized (REQUESTED) {
                if (!STORE.containsKey(h.malId)) {
                    STORE.put(h.malId, fromShiki(a, null));
                    REQUESTED.add(h.malId);
                }
            }
        }
        return out;
    }

    public static ShikiDetails getShikiDetails(int malId) throws java.io.IOException {
        String host = resolveShikiHost();
        JSONObject a = HttpCache.getJson(host + "/api/animes/" + malId,
                new HttpCache.Policy().fresh(7 * HttpCache.DAY).maxAge(60 * HttpCache.DAY).timeout(12000));
        if (!a.has("id")) return null;
        AnimeMeta prev = STORE.get(malId);
        AnimeMeta merged = fromShiki(a, prev);
        merged.malId = malId;
        merged.ts = System.currentTimeMillis();
        STORE.put(malId, merged);
        writeMeta(malId, merged);

        ShikiDetails d = new ShikiDetails();
        d.malId = malId;
        d.ru = a.optString("russian", "");
        d.name = a.optString("name", "");
        d.description = cleanShikiText(a.optString("description", ""));
        JSONArray genres = a.optJSONArray("genres");
        StringBuilder g = new StringBuilder();
        if (genres != null) {
            for (int i = 0; i < genres.length(); i++) {
                JSONObject gobj = genres.optJSONObject(i);
                if (gobj == null) continue;
                String gn = gobj.optString("russian", "");
                if (gn.isEmpty()) gn = gobj.optString("name", "");
                if (gn.isEmpty()) continue;
                if (g.length() > 0) g.append(" · ");
                g.append(gn);
            }
        }
        d.genres = g.toString();
        JSONArray studios = a.optJSONArray("studios");
        StringBuilder st = new StringBuilder();
        if (studios != null) {
            for (int i = 0; i < studios.length(); i++) {
                JSONObject s = studios.optJSONObject(i);
                if (s == null) continue;
                if (st.length() > 0) st.append(", ");
                st.append(s.optString("name", ""));
            }
        }
        d.studios = st.toString();
        String score = a.optString("score", "");
        if (!score.isEmpty() && !"0".equals(score)) {
            try { d.score = Double.parseDouble(score); } catch (Exception ignored) { }
        }
        d.rating = a.optString("rating", "");
        d.duration = a.optInt("duration", -1);
        d.episodes = a.optInt("episodes", -1);
        d.kind = a.optString("kind", "");
        d.status = a.optString("status", "");
        d.airedOn = a.optString("aired_on", "");
        String url = a.optString("url", "");
        d.url = url.startsWith("http") ? fixShikiUrl(url) : host + url;
        d.poster = shikiImg(a.optJSONObject("image") == null ? "" : a.optJSONObject("image").optString("original", ""));
        return d;
    }

    /** Strip Shikimori bb-code ([character=1]Name[/character], [[wiki]], [spoiler]…). */
    public static String cleanShikiText(String s) {
        if (s == null || s.isEmpty()) return "";
        String out = s
                .replaceAll("(?is)\\[spoiler[^\\]]*\\].*?\\[/spoiler\\]", "")
                .replaceAll("(?is)\\[(character|person|anime|manga|ranobe|url|entry|comment|topic|user|image|poster|div|span|color|size)[^\\]]*\\].*?\\[/\\1\\]", "$2")
                .replaceAll("(?i)\\[/?(b|i|u|s|br|hr|center|right|quote|list|\\*)\\]", "")
                .replaceAll("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?\\]\\]", "$1")
                .replaceAll("\\[[^\\]]{1,40}\\]", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return out;
    }
}
