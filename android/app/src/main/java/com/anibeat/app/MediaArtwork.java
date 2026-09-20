package com.anibeat.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny async artwork loader for the media notification / lockscreen. */
final class MediaArtwork {

    interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(12);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MediaArtwork() {
    }

    static void load(Context context, String url, Callback callback) {
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            callback.onLoaded(cached);
            return;
        }
        POOL.execute(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "image/*");
                int code = connection.getResponseCode();
                if (code == 200) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream in = connection.getInputStream()) {
                        BitmapFactory.decodeStream(in, null, bounds);
                    }
                    int sample = 1;
                    while (bounds.outWidth / sample > 512 && sample < 16) sample *= 2;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sample;
                    HttpURLConnection second = (HttpURLConnection) new URL(url).openConnection();
                    second.setConnectTimeout(8000);
                    second.setReadTimeout(8000);
                    second.setRequestProperty("Accept", "image/*");
                    try (InputStream in = second.getInputStream()) {
                        bitmap = BitmapFactory.decodeStream(in, null, options);
                    }
                    second.disconnect();
                    if (bitmap != null) CACHE.put(url, bitmap);
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            Bitmap result = bitmap;
            MAIN.post(() -> callback.onLoaded(result));
        });
    }
}
