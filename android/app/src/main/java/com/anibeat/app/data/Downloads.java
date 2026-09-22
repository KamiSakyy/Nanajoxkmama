package com.anibeat.app.data;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import com.anibeat.app.core.Prefs;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Офлайн-загрузки (lib/offline.ts + store/downloads.tsx):
 *  • файл кладётся в filesDir/offline — доступен без интернета;
 *  • отдельная копия сохраняется в общую папку «Загрузки» (как в браузере);
 *  • прогресс, отмена, очередь не более двух параллельных задач.
 */
public final class Downloads {

    public static final String KIND_AUDIO = "audio";
    public static final String KIND_VIDEO = "video";

    public enum Status { QUEUED, DOWNLOADING, DONE, ERROR, CANCELLED }

    public static class Job {
        public String key;
        public Models.Track track;
        public String kind;
        public long received;
        public long total;
        public Status status = Status.QUEUED;
        public String error;
        public boolean saveToDevice = true;
    }

    public interface Listener {
        void onJobsChanged();
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Job> JOBS = new ArrayList<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final List<JSONObject> OFFLINE_META = new ArrayList<>();
    private static Context context;
    private static int running;

    private Downloads() {
    }

    public static void init(Context ctx) {
        context = ctx.getApplicationContext();
        File dir = offlineDir();
        if (!dir.exists()) dir.mkdirs();
        OFFLINE_META.clear();
        OFFLINE_META.addAll(Prefs.getObjectList("offline.meta"));
        // чистим метаданные без файлов
        List<JSONObject> keep = new ArrayList<>();
        for (JSONObject o : OFFLINE_META) {
            File f = fileFor(o.optString("key"));
            if (f.exists()) keep.add(o);
        }
        if (keep.size() != OFFLINE_META.size()) {
            OFFLINE_META.clear();
            OFFLINE_META.addAll(keep);
            persistMeta();
        }
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
                l.onJobsChanged();
            } catch (Throwable t) {
                com.anibeat.app.core.Ui.report(t);
            }
        }
    }

    private static File offlineDir() {
        return new File(context.getFilesDir(), "offline");
    }

    private static File fileFor(String key) {
        return new File(offlineDir(), key.replace("|", "_").replace(":", "_"));
    }

    public static String key(String trackId, String kind) {
        return trackId + "|" + kind;
    }

    /* ------------------------- офлайн-состояние ------------------------- */

    public static boolean hasOffline(String trackId, String kind) {
        return fileFor(key(trackId, kind)).exists();
    }

    public static File offlineFile(String trackId, String kind) {
        File f = fileFor(key(trackId, kind));
        return f.exists() ? f : null;
    }

    public static List<JSONObject> offlineList() {
        return new ArrayList<>(OFFLINE_META);
    }

    public static List<Models.Track> offlineTracks() {
        List<Models.Track> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (JSONObject o : OFFLINE_META) {
            JSONObject t = o.optJSONObject("track");
            if (t == null) continue;
            Models.Track track = Models.Track.fromJson(t);
            if (!seen.contains(track.id)) {
                seen.add(track.id);
                out.add(track);
            }
        }
        return out;
    }

    public static long offlineSizeOf(String trackId, String kind) {
        File f = offlineFile(trackId, kind);
        return f == null ? 0 : f.length();
    }

    public static long offlineTotalSize() {
        long total = 0;
        File dir = offlineDir();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    public static void removeOffline(String trackId, String kind) {
        String k = key(trackId, kind);
        File f = fileFor(k);
        if (f.exists()) f.delete();
        for (int i = 0; i < OFFLINE_META.size(); i++) {
            if (k.equals(OFFLINE_META.get(i).optString("key"))) {
                OFFLINE_META.remove(i);
                break;
            }
        }
        persistMeta();
        emit();
    }

    public static void clearOffline() {
        File dir = offlineDir();
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
        OFFLINE_META.clear();
        persistMeta();
        emit();
    }

    private static void persistMeta() {
        Prefs.putObjectList("offline.meta", OFFLINE_META);
    }

    public static String formatBytes(long n) {
        if (n <= 0) return "0 Б";
        if (n < 1024) return n + " Б";
        double kb = n / 1024.0;
        if (kb < 1024) return Math.round(kb) + " КБ";
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f МБ", mb);
        return String.format(java.util.Locale.US, "%.2f ГБ", mb / 1024.0);
    }

    public static String filename(Models.Track track, String kind) {
        String url = KIND_AUDIO.equals(kind) ? track.audioUrl : track.videoUrl;
        String ext = "ogg";
        int dot = url.lastIndexOf('.');
        if (dot > 0) {
            String candidate = url.substring(dot + 1).replaceAll("[^a-zA-Z0-9]", "");
            if (candidate.length() >= 2 && candidate.length() <= 4) ext = candidate.toLowerCase();
        }
        String animeName = track.anime == null ? "" : track.anime.name;
        String raw = track.artistNames() + " - " + track.title + " [" + animeName + " " + track.themeSlug + "]";
        raw = raw.replaceAll("[\\\\/:*?\"<>|]+", "").replaceAll("\\s+", " ").trim();
        if (raw.length() > 150) raw = raw.substring(0, 150);
        return raw + "." + ext;
    }

    /* ------------------------- очередь загрузок ------------------------- */

    public static List<Job> jobs() {
        return new ArrayList<>(JOBS);
    }

    public static int activeCount() {
        int n = 0;
        for (Job j : JOBS) if (j.status == Status.QUEUED || j.status == Status.DOWNLOADING) n++;
        return n;
    }

    /** Процент загрузки трека: -1 — не качается, 0..100 — идёт загрузка. */
    public static int progressOf(String trackId, String kind) {
        if (trackId == null) return -1;
        String k = key(trackId, kind);
        for (Job j : JOBS) {
            if (!k.equals(j.key)) continue;
            if (j.status == Status.QUEUED) return 0;
            if (j.status == Status.DOWNLOADING) {
                return j.total > 0 ? (int) Math.min(100, j.received * 100 / j.total) : 1;
            }
            if (j.status == Status.DONE) return 100;
            return -1;
        }
        return -1;
    }

    public static void download(Models.Track track, String kind, boolean saveToDevice) {
        final String k = key(track.id, kind);
        for (Job j : JOBS) {
            if (j.key.equals(k) && (j.status == Status.QUEUED || j.status == Status.DOWNLOADING)) return;
        }
        Job job = new Job();
        job.key = k;
        job.track = track;
        job.kind = kind;
        job.saveToDevice = saveToDevice;
        JOBS.add(0, job);
        emit();
        pump();
    }

    public static void cancel(String key) {
        for (Job j : JOBS) {
            if (j.key.equals(key) && (j.status == Status.QUEUED || j.status == Status.DOWNLOADING)) {
                j.status = Status.CANCELLED;
            }
        }
        emit();
    }

    public static void dismiss(String key) {
        for (int i = 0; i < JOBS.size(); i++) {
            if (JOBS.get(i).key.equals(key)) {
                JOBS.remove(i);
                break;
            }
        }
        emit();
    }

    public static void clearFinished() {
        for (int i = JOBS.size() - 1; i >= 0; i--) {
            Status s = JOBS.get(i).status;
            if (s == Status.DONE || s == Status.ERROR || s == Status.CANCELLED) JOBS.remove(i);
        }
        emit();
    }

    private static void pump() {
        while (running < 2) {
            Job next = null;
            for (Job j : JOBS) {
                if (j.status == Status.QUEUED) {
                    next = j;
                    break;
                }
            }
            if (next == null) break;
            final Job job = next;
            job.status = Status.DOWNLOADING;
            running++;
            emit();
            POOL.execute(() -> run(job));
        }
    }

    private static void run(Job job) {
        String url = KIND_AUDIO.equals(job.kind) ? job.track.audioUrl : job.track.videoUrl;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            if (c.getResponseCode() >= 400) throw new Exception("HTTP " + c.getResponseCode());
            long total = c.getContentLengthLong();
            File target = fileFor(job.key);
            File temp = new File(target.getAbsolutePath() + ".part");
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buf = new byte[65536];
                int n;
                long received = 0;
                long lastEmit = 0;
                while ((n = in.read(buf)) > 0) {
                    if (job.status == Status.CANCELLED) throw new InterruptedException("cancelled");
                    out.write(buf, 0, n);
                    received += n;
                    job.received = received;
                    job.total = total;
                    long now = System.currentTimeMillis();
                    if (now - lastEmit > 500) {
                        lastEmit = now;
                        MAIN.post(Downloads::emit);
                    }
                }
            }
            if (target.exists()) target.delete();
            if (!temp.renameTo(target)) throw new Exception("rename failed");
            job.received = job.total > 0 ? job.total : job.received;
            job.status = Status.DONE;
            recordOfflineMeta(job);
            if (job.saveToDevice) saveToPublicDownloads(target, filename(job.track, job.kind));
        } catch (InterruptedException e) {
            job.status = Status.CANCELLED;
        } catch (Exception e) {
            job.status = Status.ERROR;
            job.error = e.getMessage() == null ? "Ошибка" : e.getMessage();
        } finally {
            if (c != null) c.disconnect();
            running--;
            MAIN.post(() -> {
                emit();
                pump();
            });
        }
    }

    private static void recordOfflineMeta(Job job) {
        for (int i = 0; i < OFFLINE_META.size(); i++) {
            if (job.key.equals(OFFLINE_META.get(i).optString("key"))) OFFLINE_META.remove(i);
        }
        try {
            JSONObject o = new JSONObject();
            o.put("key", job.key);
            o.put("trackId", job.track.id);
            o.put("kind", job.kind);
            o.put("size", offlineSizeOf(job.track.id, job.kind));
            o.put("savedAt", System.currentTimeMillis());
            o.put("track", job.track.toJson());
            OFFLINE_META.add(0, o);
            persistMeta();
        } catch (Exception ignored) {
        }
    }

    /** Копия в общую «Загрузки» — ровно как <a download> в браузере. */
    private static void saveToPublicDownloads(File source, String name) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                // На новых Android запись идёт через MediaStore (отдельный класс — только API 29+).
                DownloadsPublic.save(context, source, name, mimeFor(name));
                return;
            }
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) return;
            File target = new File(dir, name);
            try (InputStream in = new java.io.FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()}, null, null);
        } catch (Throwable t) {
            com.anibeat.app.core.Ui.report(t);
        }
    }

    private static String mimeFor(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) return "audio/ogg";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".opus")) return "audio/opus";
        return "application/octet-stream";
    }

    public static Uri publicUriOf(Models.Track track, String kind) {
        File f = offlineFile(track.id, kind);
        if (f == null) return null;
        return Uri.fromFile(f);
    }
}
