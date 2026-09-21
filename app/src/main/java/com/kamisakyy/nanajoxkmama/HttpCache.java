package com.kamisakyy.nanajoxkmama;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-tier HTTP cache (memory + disk) with stale-while-revalidate semantics,
 * request de-duplication and retry with exponential backoff — the Android twin
 * of the website's api/http.ts. All calls are synchronous; run them off the main thread.
 */
public final class HttpCache {
    public static final long MIN = 60_000L;
    public static final long HOUR = 60 * MIN;
    public static final long DAY = 24 * HOUR;
    private static final String USER_AGENT = "AniBeat/2.0 (Android; Anime music player)";
    private static final long[] RETRY_DELAYS = {600, 1500, 3200};

    public static final class Policy {
        long ttl = 5 * MIN;      // memory hot-cache
        long fresh = HOUR;       // serve without revalidation
        long maxAge = 7 * DAY;   // serve stale + revalidate
        boolean noStore;
        boolean refresh;
        int timeout = 15_000;
        int retries = 3;
        String body;             // POST payload

        public Policy ttl(long v) { ttl = v; return this; }
        public Policy fresh(long v) { fresh = v; return this; }
        public Policy maxAge(long v) { maxAge = v; return this; }
        public Policy noStore() { noStore = true; return this; }
        public Policy refresh() { refresh = true; return this; }
        public Policy timeout(int v) { timeout = v; return this; }
        public Policy retries(int v) { retries = v; return this; }
        public Policy body(String v) { body = v; return this; }
    }

    private static final class Entry {
        long ts;
        String data;
    }

    private static final Map<String, Entry> MEM = new ConcurrentHashMap<>();
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();
    private static volatile File cacheDir;

    private HttpCache() { }

    public static void init(File dir) {
        cacheDir = dir;
    }

    public static String request(String url, Policy p) throws java.io.IOException {
        if (p == null) p = new Policy();
        String key = p.body == null ? url : url + "#" + p.body;
        Object lock = LOCKS.get(key);
        if (lock == null) {
            Object freshLock = new Object();
            lock = LOCKS.putIfAbsent(key, freshLock);
            if (lock == null) lock = freshLock;
        }
        synchronized (lock) {
            long now = System.currentTimeMillis();
            if (!p.refresh) {
                Entry hot = MEM.get(key);
                if (hot != null && now - hot.ts < p.ttl) return hot.data;
            }
            Entry disk = p.noStore || p.refresh ? null : readDisk(key);
            if (disk != null && now - disk.ts < p.maxAge) {
                MEM.put(key, disk);
                if (now - disk.ts >= p.fresh) {
                    // stale-while-revalidate: return instantly, refresh quietly
                    final String k2 = key;
                    final Policy fp = p;
                    new Thread(() -> {
                        try {
                            String fresh = fetch(url, fp);
                            store(k2, fresh, fp);
                        } catch (Exception ignored) { }
                    }, "http-revalidate").start();
                }
                return disk.data;
            }
            String data = fetch(url, p);
            store(key, data, p);
            return data;
        }
    }

    public static JSONObject getJson(String url, Policy p) throws java.io.IOException {
        try {
            return new JSONObject(request(url, p));
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new java.io.IOException("Неверный ответ сервера", e);
        }
    }

    public static org.json.JSONArray getArray(String url, Policy p) throws java.io.IOException {
        try {
            return new org.json.JSONArray(request(url, p));
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new java.io.IOException("Неверный ответ сервера", e);
        }
    }

    private static void store(String key, String data, Policy p) {
        Entry e = new Entry();
        e.ts = System.currentTimeMillis();
        e.data = data;
        MEM.put(key, e);
        if (!p.noStore) writeDisk(key, e);
    }

    private static String fetch(String url, Policy p) throws java.io.IOException {
        int max = Math.min(p.retries, RETRY_DELAYS.length);
        for (int attempt = 0; ; attempt++) {
            try {
                return once(url, p);
            } catch (RetryableIOException r) {
                if (attempt < max) {
                    sleep(r.retryAfter > 0 ? Math.min(r.retryAfter, 8000) : RETRY_DELAYS[attempt]);
                    continue;
                }
                throw r;
            } catch (java.io.IOException e) {
                if (attempt < max) {
                    sleep(RETRY_DELAYS[attempt]);
                    continue;
                }
                throw e;
            }
        }
    }

    private static String once(String url, Policy p) throws java.io.IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(p.body == null ? "GET" : "POST");
            connection.setConnectTimeout(p.timeout);
            connection.setReadTimeout(p.timeout);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (p.body != null) {
                byte[] payload = p.body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                OutputStream out = connection.getOutputStream();
                out.write(payload);
                out.flush();
                out.close();
            }
            int code = connection.getResponseCode();
            if (code == 429 || code >= 500) {
                int after = 0;
                try { after = Integer.parseInt(connection.getHeaderField("Retry-After")); } catch (Exception ignored) { }
                throw new RetryableIOException(code == 429 ? "Слишком много запросов — попробуйте чуть позже"
                        : "Сервер временно недоступен (" + code + ")", after * 1000);
            }
            if (code < 200 || code >= 300) {
                throw new java.io.IOException(code == 404 ? "Не найдено" : "Ошибка API (" + code + ")");
            }
            return read(connection.getInputStream());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 429 / 5xx — retried with backoff. */
    private static final class RetryableIOException extends java.io.IOException {
        final int retryAfter;
        RetryableIOException(String message, int retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private static String read(InputStream input) throws java.io.IOException {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
        reader.close();
        return out.toString();
    }

    private static File diskFile(String key) {
        File dir = new File(cacheDir, "http");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, sha1(key) + ".json");
    }

    private static Entry readDisk(String key) {
        try {
            File f = diskFile(key);
            if (!f.exists() || f.length() > 4_000_000) return null;
            String raw = read(new FileInputStream(f));
            JSONObject o = new JSONObject(raw);
            Entry e = new Entry();
            e.ts = o.optLong("ts", 0L);
            e.data = o.optString("data", null);
            return e.data == null ? null : e;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeDisk(String key, Entry e) {
        try {
            JSONObject o = new JSONObject();
            o.put("ts", e.ts);
            o.put("data", e.data);
            FileOutputStream out = new FileOutputStream(diskFile(key));
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception ignored) { }
    }

    public static void clear() {
        MEM.clear();
        try {
            File dir = new File(cacheDir, "http");
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
        } catch (Exception ignored) { }
    }

    /** Housekeeping: drop entries older than 7 days. */
    public static void prune() {
        try {
            File dir = new File(cacheDir, "http");
            File[] files = dir.listFiles();
            if (files == null) return;
            long now = System.currentTimeMillis();
            for (File f : files) if (now - f.lastModified() > 7 * DAY) f.delete();
        } catch (Exception ignored) { }
    }

    private static String sha1(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] h = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : h) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
