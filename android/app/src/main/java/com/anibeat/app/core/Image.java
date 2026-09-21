package com.anibeat.app.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Загрузчик обложек: память → диск → сеть, с дедупликацией и фоновым декодированием. */
public final class Image {

    public interface Listener {
        void onBitmap(Bitmap bitmap);
        void onError();
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(5);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, java.util.List<Listener>> WAITERS = new ConcurrentHashMap<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static LruCache<String, Bitmap> mem;
    private static File dir;
    private static boolean dataSaver;

    private Image() {
    }

    public static void init(Context context) {
        int kb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        mem = new LruCache<String, Bitmap>(kb / 6) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        dir = new File(context.getApplicationContext().getCacheDir(), "images");
        if (!dir.exists()) dir.mkdirs();
    }

    public static void setDataSaver(boolean value) {
        dataSaver = value;
    }

    public static Bitmap cached(String url) {
        if (url == null || mem == null) return null;
        return mem.get(url);
    }

    public static void load(String url, int targetPx, Listener listener) {
        if (listener == null) return;
        if (url == null || url.isEmpty()) {
            deliver(listener, null);
            return;
        }
        Bitmap ready = cached(url);
        if (ready != null) {
            deliver(listener, ready);
            return;
        }
        // все, кто ждёт одну и ту же обложку, получат её — никто не потеряется
        java.util.List<Listener> waiters = WAITERS.get(url);
        if (waiters == null) {
            java.util.List<Listener> fresh = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            waiters = WAITERS.putIfAbsent(url, fresh);
            if (waiters == null) waiters = fresh;
        }
        boolean first;
        synchronized (waiters) {
            first = waiters.isEmpty();
            waiters.add(listener);
        }
        if (!first) return;
        final java.util.List<Listener> queue = waiters;
        final int px = Math.max(64, targetPx);
        POOL.execute(() -> {
            Bitmap bitmap = fromDisk(url, px);
            if (bitmap == null) bitmap = fromNetwork(url, px);
            final Bitmap result = bitmap;
            MAIN.post(() -> {
                java.util.List<Listener> waiting = WAITERS.remove(url);
                if (waiting == null) waiting = queue;
                java.util.List<Listener> copy;
                synchronized (waiting) {
                    copy = new java.util.ArrayList<>(waiting);
                    waiting.clear();
                }
                for (Listener l : copy) deliver(l, result);
            });
        });
    }

    /** Отдаёт результат слушателю; ошибка внутри отрисовки не закрывает приложение. */
    private static void deliver(Listener listener, Bitmap bitmap) {
        try {
            if (bitmap != null) listener.onBitmap(bitmap);
            else listener.onError();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private static Bitmap fromDisk(String url, int px) {
        File f = file(url);
        if (f == null || !f.exists()) return null;
        Bitmap b = decode(f.getAbsolutePath(), px);
        if (b != null) mem.put(url, b);
        return b;
    }

    private static Bitmap fromNetwork(String url, int px) {
        if (FAILED.contains(url)) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "image/*");
            c.setRequestProperty("User-Agent", "AniBeat/1.0 (Android)");
            if (c.getResponseCode() != 200) {
                FAILED.add(url);
                return null;
            }
            File f = file(url);
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            Bitmap b = decode(f.getAbsolutePath(), px);
            if (b != null) mem.put(url, b);
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static Bitmap decode(String path, int px) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > px * 1.4 && sample < 16) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = dataSaver ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(path, opts);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static File file(String url) {
        if (dir == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return new File(dir, sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearDisk() {
        FAILED.clear();
        if (mem != null) mem.evictAll();
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    public static long diskSize() {
        long total = 0;
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) total += f.length();
        }
        return total;
    }
}
