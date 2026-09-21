package com.kamisakyy.nanajoxkmama;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Download queue (max 2 parallel) with progress — the Android port of
 * store/downloads.tsx. Saves an offline copy for the player and optionally
 * exports the file to the device (MediaStore / public Music).
 */
public final class Downloader {
    public static final int QUEUED = 0;
    public static final int DOWNLOADING = 1;
    public static final int DONE = 2;
    public static final int ERROR = 3;
    public static final int CANCELLED = 4;

    public static final class Job {
        public final String key;
        public final Track track;
        public final boolean video;
        public final boolean saveToDevice;
        public volatile long received;
        public volatile long total = -1;
        public volatile int status = QUEUED;
        public volatile String error = "";
        final AtomicBoolean cancel = new AtomicBoolean(false);
        Job(String key, Track track, boolean video, boolean saveToDevice) {
            this.key = key;
            this.track = track;
            this.video = video;
            this.saveToDevice = saveToDevice;
        }
    }

    public interface Listener { void onJobsChanged(); }

    private static final List<Job> JOBS = new ArrayList<>();
    private static final Map<String, Job> ACTIVE = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Context app;
    private static int running;

    private Downloader() { }

    public static void init(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized void addListener(Listener l) { LISTENERS.add(l); }
    public static synchronized void removeListener(Listener l) { LISTENERS.remove(l); }

    private static void notifyChanged() {
        MAIN.post(() -> {
            List<Listener> copy;
            synchronized (Downloader.class) { copy = new ArrayList<>(LISTENERS); }
            for (Listener l : copy) l.onJobsChanged();
        });
    }

    public static synchronized ArrayList<Job> jobs() {
        return new ArrayList<>(JOBS);
    }

    public static synchronized int activeCount() {
        int n = 0;
        for (Job j : JOBS) if (j.status == QUEUED || j.status == DOWNLOADING) n++;
        return n;
    }

    /** Overall progress 0..100 or -1 when unknown. */
    public static synchronized float progress() {
        float sum = 0;
        int known = 0, active = 0;
        for (Job j : JOBS) {
            if (j.status != QUEUED && j.status != DOWNLOADING) continue;
            active++;
            if (j.total > 0) {
                sum += j.received / (float) j.total;
                known++;
            }
        }
        if (active == 0) return 100f;
        return known == 0 ? -1f : sum / active * 100f;
    }

    public static void download(Track track, boolean video, boolean saveToDevice) {
        String key = track.id + (video ? "|video" : "|audio");
        Job existing;
        synchronized (Downloader.class) {
            for (Job j : JOBS) {
                if (j.key.equals(key) && (j.status == QUEUED || j.status == DOWNLOADING)) {
                    notifyChanged();
                    return;
                }
            }
            existing = null;
        }
        boolean offlineOnly = !saveToDevice;
        if (Store.isOffline(track) && offlineOnly) return;
        Job job = new Job(key, track.copy(), video, saveToDevice);
        synchronized (Downloader.class) {
            for (int i = JOBS.size() - 1; i >= 0; i--) if (JOBS.get(i).key.equals(key)) JOBS.remove(i);
            JOBS.add(0, job);
        }
        ACTIVE.put(key, job);
        notifyChanged();
        pump();
    }

    public static void cancel(String key) {
        Job j = ACTIVE.get(key);
        if (j != null) j.cancel.set(true);
        synchronized (Downloader.class) {
            for (Job x : JOBS) {
                if (x.key.equals(key) && x.status == QUEUED) x.status = CANCELLED;
            }
        }
        notifyChanged();
    }

    public static void dismiss(String key) {
        Job j = ACTIVE.remove(key);
        if (j != null) j.cancel.set(true);
        synchronized (Downloader.class) {
            for (int i = JOBS.size() - 1; i >= 0; i--) if (JOBS.get(i).key.equals(key)) JOBS.remove(i);
        }
        notifyChanged();
    }

    public static synchronized void clearFinished() {
        for (int i = JOBS.size() - 1; i >= 0; i--) {
            int st = JOBS.get(i).status;
            if (st != QUEUED && st != DOWNLOADING) JOBS.remove(i);
        }
        notifyChanged();
    }

    private static void pump() {
        synchronized (Downloader.class) {
            while (running < 2) {
                Job next = null;
                for (Job j : JOBS) if (j.status == QUEUED) { next = j; break; }
                if (next == null) break;
                running++;
                final Job task = next;
                POOL.execute(() -> run(task));
            }
        }
    }

    private static void run(Job job) {
        job.status = DOWNLOADING;
        notifyChanged();
        InputStream input = null;
        FileOutputStream out = null;
        try {
            String url = job.video ? job.track.videoUrl : job.track.audioUrl;
            if (job.track.offlinePath != null && !job.track.offlinePath.isEmpty() && !job.video
                    && job.track.offlinePath.equals(job.track.playableUrl())) {
                // already downloaded — just export when asked
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "AniBeat/2.0 Android");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
            long len = (long) connection.getContentLength();
            job.total = len > 0 ? len : -1;
            File local = Store.offlineFile(job.track);
            input = connection.getInputStream();
            out = new FileOutputStream(local);
            byte[] buf = new byte[64 * 1024];
            long received = 0;
            int r;
            long lastTick = System.currentTimeMillis();
            while ((r = input.read(buf)) > 0) {
                if (job.cancel.get()) {
                    out.close();
                    local.delete();
                    job.status = CANCELLED;
                    connection.disconnect();
                    notifyChanged();
                    synchronized (Downloader.class) { running--; }
                    pump();
                    return;
                }
                out.write(buf, 0, r);
                received += r;
                job.received = received;
                if (System.currentTimeMillis() - lastTick > 200) {
                    lastTick = System.currentTimeMillis();
                    notifyChanged();
                }
            }
            out.close();
            out = null;
            input.close();
            input = null;
            connection.disconnect();
            Store.saveOffline(job.track, local);
            if (job.saveToDevice) exportToDevice(local, job.track, job.video);
            job.status = DONE;
            job.received = received;
            if (job.total < 0) job.total = received;
        } catch (Exception e) {
            job.status = job.cancel.get() ? CANCELLED : ERROR;
            job.error = e.getMessage() == null ? "Ошибка" : e.getMessage();
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            synchronized (Downloader.class) { running--; }
            ACTIVE.remove(job.key);
            notifyChanged();
            pump();
        }
    }

    private static void exportToDevice(File local, Track track, boolean video) {
        try {
            String ext = video ? "webm" : "m4a";
            String name = sanitize(track.title) + " — " + sanitize(track.displayArtist()) + "." + ext;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, video ? "video/webm" : "audio/mp4");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_MUSIC) + "/AniBeat");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = app.getContentResolver().insert(
                        video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return;
                java.io.OutputStream os = app.getContentResolver().openOutputStream(uri);
                if (os != null) {
                    java.io.FileInputStream in = new java.io.FileInputStream(local);
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                    in.close();
                    os.close();
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                app.getContentResolver().update(uri, values, null, null);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_MUSIC), "AniBeat");
                if (!dir.exists()) dir.mkdirs();
                File dest = new File(dir, name);
                java.io.FileInputStream in = new java.io.FileInputStream(local);
                FileOutputStream os = new FileOutputStream(dest);
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                in.close();
                os.close();
                MediaScannerConnection.scanFile(app, new String[]{dest.getAbsolutePath()}, null, null);
            }
        } catch (Exception ignored) { }
    }

    private static String sanitize(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
