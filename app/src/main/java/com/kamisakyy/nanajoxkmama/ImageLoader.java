package com.kamisakyy.nanajoxkmama;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny image loader with an in-memory cache; remote art is never packaged in the APK. */
public final class ImageLoader {
    private static final int MAX = 48;
    private static final ConcurrentHashMap<String, Bitmap> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);

    private ImageLoader() { }

    public static void load(final ImageView view, final String url, final boolean small) {
        final String key = url == null ? "" : url;
        view.setTag(key);
        view.setImageDrawable(new ColorDrawable(Color.rgb(42, 35, 51)));
        if (key.isEmpty()) return;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                Bitmap bitmap = fetch(key, small ? 320 : 640);
                if (bitmap == null) return;
                if (CACHE.size() >= MAX) {
                    String first = CACHE.keySet().iterator().next();
                    CACHE.remove(first);
                }
                CACHE.put(key, bitmap);
                view.post(new Runnable() {
                    @Override public void run() {
                        if (key.equals(view.getTag())) view.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url, int maxSide) {
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "AniBeat/1.0 Android");
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
            connection.setRequestProperty("User-Agent", "AniBeat/1.0 Android");
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
}
