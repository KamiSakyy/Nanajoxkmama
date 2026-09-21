package com.anibeat.app.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheKeyFactory;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;

/**
 * Кэш воспроизведения: благодаря ему следующий трек готовится заранее,
 * и переключение идёт без паузы — как «Готовить следующий трек» на сайте.
 */
public final class MediaCache {

    private static final long MAX_BYTES = 400L * 1024 * 1024;
    private static SimpleCache cache;
    private static boolean failed;

    private MediaCache() {
    }

    /** Ключ кэша: сетевые ссылки кэшируются, локальные файлы — нет. */
    public static final CacheKeyFactory KEY_FACTORY = dataSpec -> {
        String scheme = dataSpec.uri.getScheme();
        boolean network = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        return network ? dataSpec.uri.toString() : null;
    };

    @Nullable
    public static synchronized SimpleCache get(Context context) {
        if (failed) return null;
        if (cache != null) return cache;
        if (context == null) return null;
        try {
            File dir = new File(context.getApplicationContext().getCacheDir(), "media");
            cache = new SimpleCache(dir, new LeastRecentlyUsedCacheEvictor(MAX_BYTES));
        } catch (Throwable t) {
            failed = true;
            cache = null;
            com.anibeat.app.core.Ui.report(t);
        }
        return cache;
    }

    /** Скачивает дорожку в кэш заранее — при переходе она прозвучит сразу. */
    public static void preload(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return;
        SimpleCache ready = get(context);
        if (ready == null) return;
        try {
            new CacheWriter(ready, new DataSpec(uri), null, null).cache();
        } catch (Throwable ignored) {
            // предзагрузка — необязательная оптимизация, ошибка не влияет на воспроизведение
        }
    }
}
