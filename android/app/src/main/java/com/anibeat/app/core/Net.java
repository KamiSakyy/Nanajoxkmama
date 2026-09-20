package com.anibeat.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сетевой слой — аналог {@code src/api/http.ts}:
 *  • горячий кэш в памяти + дисковый кэш (файлы в cacheDir/http)
 *  • дедупликация одновременных одинаковых запросов
 *  • повторные попытки с экспоненциальной задержкой для 429/5xx/сети
 *  • таймауты, приоритеты (высокий = сразу, низкий = в общей очереди)
 */
public final class Net {

    private static final String TAG = "AniBeatNet";
    public static final long MIN = 60_000L;
    public static final long HOUR = 60 * MIN;
    public static final long DAY = 24 * HOUR;

    public interface Callback<T> {
        void onResult(T value, String error);
    }

    private static final class Entry {
        final long ts;
        final String body;
        Entry(long ts, String body) {
            this.ts = ts;
            this.body = body;
        }
    }

    private static final Map<String, Entry> MEM = new ConcurrentHashMap<>();
    private static final Map<String, NetTask<?>> INFLIGHT = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final ExecutorService HIGH = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long[] RETRY = {600, 1500, 3200};
    private static File cacheDir;

    private Net() {
    }

    public static void init(Context context) {
        cacheDir = new File(context.getApplicationContext().getCacheDir(), "http");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    public static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    public static String buildUrl(String base, String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(base).append(path);
        if (params != null && !params.isEmpty()) {
            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);
            boolean first = true;
            for (String k : keys) {
                String v = params.get(k);
                if (v == null || v.isEmpty()) continue;
                sb.append(first ? '?' : '&').append(encode(k)).append('=').append(encode(v));
                first = false;
            }
        }
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /* Публичный API                                                       */
    /* ------------------------------------------------------------------ */

    public static void get(String url, Callback<JSONObject> cb) {
        request(url, null, 0, 0, false, true, cb);
    }

    public static void get(String url, long fresh, long maxAge, Callback<JSONObject> cb) {
        request(url, null, fresh, maxAge, false, true, cb);
    }

    public static void get(String url, long fresh, long maxAge, boolean refresh, Callback<JSONObject> cb) {
        request(url, null, fresh, maxAge, refresh, true, cb);
    }

    public static void post(String url, String body, Callback<JSONObject> cb) {
        request(url, body, 0, DAY, false, false, cb);
    }

    /** Низкий приоритет — фоновое обогащение (метаданные, предзагрузка). */
    public static void getLow(String url, long fresh, long maxAge, Callback<JSONObject> cb) {
        request(url, null, fresh, maxAge, false, false, cb);
    }

    /* ------------------------------------------------------------------ */
    /* Реализация                                                          */
    /* ------------------------------------------------------------------ */

    private static <T> void request(String url, String postBody, long fresh, long maxAge, boolean refresh, boolean highPriority, Callback<JSONObject> cb) {
        final String key = postBody != null ? url + "#" + postBody : url;
        long now = System.currentTimeMillis();

        if (!refresh) {
            Entry hot = MEM.get(key);
            if (hot != null) {
                cb.onResult(parse(hot.body), null);
                if (now - hot.ts > (fresh > 0 ? fresh : Net.MIN)) {
                    // Stale-while-revalidate: данные уже отданы, тихо обновляем.
                    backgroundFetch(key, url, postBody, cb, true);
                }
                return;
            }
            String disk = readDisk(key);
            if (disk != null) {
                Entry diskEntry = new Entry(now, disk);
                MEM.put(key, diskEntry);
                cb.onResult(parse(disk), null);
                backgroundFetch(key, url, postBody, cb, true);
                return;
            }
        }

        backgroundFetch(key, url, postBody, cb, false);
    }

    private static void backgroundFetch(String key, String url, String postBody, Callback<JSONObject> cb, boolean silent) {
        NetTask<?> running = INFLIGHT.get(key);
        if (running != null) {
            if (!silent) running.addCallback(cb);
            return;
        }
        NetTask<JSONObject> task = new NetTask<>(key, cb);
        INFLIGHT.put(key, task);
        POOL.execute(() -> {
            String body = null;
            String error = null;
            for (int attempt = 0; ; attempt++) {
                HttpResult res = fetch(url, postBody, 15000);
                if (res.body != null) {
                    body = res.body;
                    break;
                }
                if ((res.status == 429 || res.status >= 500 || res.status == 0) && attempt < RETRY.length) {
                    sleep(RETRY[attempt]);
                    continue;
                }
                error = res.error != null ? res.error : "Сервер не отвечает. Проверьте соединение";
                break;
            }
            if (body != null) {
                MEM.put(key, new Entry(System.currentTimeMillis(), body));
                writeDisk(key, body);
            }
            final String fBody = body;
            final String fError = error;
            MAIN.post(() -> task.finish(fBody, fError));
            INFLIGHT.remove(key);
        });
    }

    private static JSONObject parse(String body) {
        try {
            Object value = new JSONTokener(body).nextValue();
            if (value instanceof JSONObject) return (JSONObject) value;
            if (value instanceof JSONArray) {
                JSONObject wrap = new JSONObject();
                wrap.put("array", value);
                return wrap;
            }
        } catch (Exception e) {
            Log.w(TAG, "parse: " + e);
        }
        return new JSONObject();
    }

    private static final class HttpResult {
        String body;
        String error;
        int status;
    }

    private static HttpResult fetch(String url, String postBody, int timeout) {
        HttpResult out = new HttpResult();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "AniBeat/1.0 (Android)");
            if (postBody != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(postBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            out.status = c.getResponseCode();
            if (out.status == 429 || out.status >= 500) {
                out.error = out.status == 429 ? "Слишком много запросов — попробуйте чуть позже" : "Сервер временно недоступен (" + out.status + ")";
                return out;
            }
            if (out.status >= 400) {
                out.error = out.status == 404 ? "Не найдено" : "Ошибка API (" + out.status + ")";
                out.status = out.status;
                return out;
            }
            out.body = read(c.getInputStream());
        } catch (Exception e) {
            out.status = 0;
            out.error = "Сервер не отвечает. Проверьте соединение";
        } finally {
            if (c != null) c.disconnect();
        }
        return out;
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toString("UTF-8");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Дисковый кэш                                                        */
    /* ------------------------------------------------------------------ */

    private static File fileFor(String key) {
        if (cacheDir == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return new File(cacheDir, sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String readDisk(String key) {
        File f = fileFor(key);
        if (f == null || !f.exists()) return null;
        try {
            long age = System.currentTimeMillis() - f.lastModified();
            if (age > 7 * DAY) {
                f.delete();
                return null;
            }
            return read(new java.io.FileInputStream(f));
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeDisk(String key, String body) {
        File f = fileFor(key);
        if (f == null) return;
        try (FileOutputStream os = new FileOutputStream(f)) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        pruneDisk();
    }

    private static void pruneDisk() {
        try {
            if (cacheDir == null) return;
            File[] files = cacheDir.listFiles();
            if (files == null || files.length < 400) return;
            long now = System.currentTimeMillis();
            for (File f : files) {
                if (now - f.lastModified() > 3 * DAY) f.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public static void clearCache() {
        MEM.clear();
        if (cacheDir != null) {
            File[] files = cacheDir.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    /** Служебный «тихий» запрос без результата (предзагрузка). */
    public static void prefetch(String url, long fresh, long maxAge) {
        getLow(url, fresh, maxAge, (value, error) -> {
        });
    }

    /** Отменяемая задача «в полёте» — для дедупликации и подписок. */
    private static final class NetTask<T> {
        final String key;
        final Set<Callback<JSONObject>> callbacks = new HashSet<>();

        NetTask(String key, Callback<JSONObject> cb) {
            this.key = key;
            callbacks.add(cb);
        }

        void addCallback(Callback<JSONObject> cb) {
            callbacks.add(cb);
        }

        void finish(String body, String error) {
            JSONObject json = body != null ? parse(body) : null;
            for (Callback<JSONObject> cb : new ArrayList<>(callbacks)) {
                cb.onResult(json, error);
            }
        }
    }

    /** Утилиты JSON. */
    public static JSONObject obj(JSONObject src, String key) {
        return src == null ? null : src.optJSONObject(key);
    }

    public static JSONArray arr(JSONObject src, String key) {
        if (src == null) return new JSONArray();
        JSONArray direct = src.optJSONArray(key);
        if (direct != null) return direct;
        JSONObject nested = src.optJSONObject(key);
        if (nested != null) {
            JSONArray wrapped = nested.optJSONArray("array");
            if (wrapped != null) return wrapped;
        }
        return new JSONArray();
    }

    /** Размер дискового кэша. */
    public static long cacheSize() {
        long total = 0;
        try {
            if (cacheDir == null) return 0;
            File[] files = cacheDir.listFiles();
            if (files != null) for (File f : files) total += f.length();
        } catch (Exception ignored) {
        }
        return total;
    }

    public static String cacheSizeLabel() {
        long n = cacheSize();
        if (n < 1024) return n + " Б";
        double kb = n / 1024.0;
        if (kb < 1024) return Math.round(kb) + " КБ";
        double mb = kb / 1024;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f МБ", mb);
        return String.format(java.util.Locale.US, "%.2f ГБ", mb / 1024);
    }
}
