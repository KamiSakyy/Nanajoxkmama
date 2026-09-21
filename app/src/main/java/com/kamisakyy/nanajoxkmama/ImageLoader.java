package com.kamisakyy.nanajoxkmama;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Image loader with an LRU memory cache and gentle cross-fade; remote art is never packaged. */
public final class ImageLoader {
    private static final int MAX = 96;
    private static final LinkedHashMap<String, Bitmap> CACHE = new LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            return size() > MAX;
        }
    };
    private static final ConcurrentHashMap<String, Bitmap> PENDING = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageLoader() { }

    public static void load(final ImageView view, final String url, final boolean small) {
        load(view, url, small, null);
    }

    public static void load(final ImageView view, final String url, final boolean small, final Runnable onLoaded) {
        final String key = url == null ? "" : url;
        view.setTag(R.id.img_tag_url, key);
        if (key.isEmpty()) {
            view.setImageDrawable(new ColorDrawable(Ui.S3));
            return;
        }
        Bitmap cached;
        synchronized (CACHE) { cached = CACHE.get(key); }
        if (cached != null) {
            view.setImageBitmap(cached);
            if (onLoaded != null) onLoaded.run();
            return;
        }
        view.setImageDrawable(new ColorDrawable(Ui.S3));
        EXECUTOR.execute(() -> {
            Bitmap bitmap = fetch(key, small || Store.isDataSaverEnabled() ? 320 : 720);
            if (bitmap == null) return;
            synchronized (CACHE) { CACHE.put(key, bitmap); }
            MAIN.post(() -> {
                if (key.equals(view.getTag(R.id.img_tag_url))) {
                    view.setImageBitmap(bitmap);
                    view.setAlpha(0f);
                    view.animate().alpha(1f).setDuration(220).start();
                    if (onLoaded != null) onLoaded.run();
                }
            });
        });
    }

    /** Synchronous decode for notifications; runs on a worker thread. */
    public static Bitmap fetchSync(final String url, final int maxSide) {
        if (url == null || url.isEmpty()) return null;
        Bitmap cached;
        synchronized (CACHE) { cached = CACHE.get(url); }
        return cached != null ? cached : fetch(url, maxSide);
    }

    private static Bitmap fetch(String url, int maxSide) {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "AniBeat/2.0 Android");
            input = connection.getInputStream();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            input.close();
            input = null;
            int sample = 1;
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2;
            connection.disconnect();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "AniBeat/2.0 Android");
            input = connection.getInputStream();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(input, null, options);
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            if (connection != null) connection.disconnect();
        }
    }

    /** Dominant vibrant color from a bitmap (Now Playing accent, like the website). */
    public static int extractColor(Bitmap bitmap, int fallback) {
        if (bitmap == null) return fallback;
        try {
            int size = 28;
            Bitmap small = Bitmap.createScaledBitmap(bitmap, size, size, false);
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int c = small.getPixel(x, y);
                    int A = Color.alpha(c);
                    if (A < 128) continue;
                    int R = Color.red(c), G = Color.green(c), B = Color.blue(c);
                    int max = Math.max(R, Math.max(G, B));
                    int min = Math.min(R, Math.min(G, B));
                    double sat = max == 0 ? 0 : (max - (double) min) / max;
                    double lum = (max + (double) min) / 2 / 255;
                    double weight = 0.15 + sat * 2 + (lum > 0.15 && lum < 0.85 ? 0.6 : 0);
                    r += R * weight;
                    g += G * weight;
                    b += B * weight;
                    w += weight;
                }
            }
            if (w == 0) return fallback;
            return Color.rgb((int) (r / w), (int) (g / w), (int) (b / w));
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Mix accent into the surface color like the website's color-mix 34%. */
    public static int mixSurface(int accent, float ratio) {
        int base = Color.rgb(10, 10, 12);
        int r = (int) (Color.red(accent) * ratio + Color.red(base) * (1 - ratio));
        int g = (int) (Color.green(accent) * ratio + Color.green(base) * (1 - ratio));
        int b = (int) (Color.blue(accent) * ratio + Color.blue(base) * (1 - ratio));
        return Color.rgb(r, g, b);
    }
}
