package com.anibeat.app.ui;

import android.content.Context;

import com.anibeat.app.core.Prefs;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Models;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Превью подборок: мозаика из обложек аниме, входящих в подборку.
 * Обложки запрашиваются один раз пачкой и запоминаются — дальше подборки показываются мгновенно.
 */
public final class MixCovers {

    private static final String STORE_KEY = "mix.covers.v1";
    private static final Map<String, String> COVERS = new HashMap<>();
    private static final List<Runnable> WAITERS = new ArrayList<>();
    private static boolean loading;
    private static boolean restored;

    private MixCovers() {
    }

    private static void restore(Context context) {
        if (restored) return;
        restored = true;
        try {
            String raw = Prefs.getString(STORE_KEY, "");
            if (raw == null || raw.isEmpty()) return;
            JSONObject json = new JSONObject(raw);
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                COVERS.put(key, json.optString(key));
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    /** Превью подборки: до четырёх обложек. Пусто — обложек ещё нет. */
    public static List<String> previewOf(Models.Mix mix) {
        List<String> out = new ArrayList<>();
        if (mix == null || mix.slugs == null) return out;
        for (String slug : mix.slugs) {
            String cover = COVERS.get(slug);
            if (cover != null && !cover.isEmpty() && !out.contains(cover)) out.add(cover);
            if (out.size() >= 4) break;
        }
        return out;
    }

    public static boolean hasPreview(Models.Mix mix) {
        return !previewOf(mix).isEmpty();
    }

    /** Догрузить обложки для подборок и сообщить, когда готово. */
    public static void ensure(Context context, List<Models.Mix> mixes, final Runnable onReady) {
        restore(context);
        if (onReady != null) WAITERS.add(onReady);
        if (loading) return;
        final List<String> need = new ArrayList<>();
        for (Models.Mix mix : mixes) {
            if (mix == null || mix.slugs == null) continue;
            int taken = 0;
            for (String slug : mix.slugs) {
                if (slug == null || slug.isEmpty()) continue;
                if (COVERS.containsKey(slug)) {
                    taken++;
                    continue;
                }
                if (!need.contains(slug)) need.add(slug);
                taken++;
                if (taken >= 4) break;
            }
        }
        if (need.isEmpty()) {
            flush();
            return;
        }
        loading = true;
        // Запрашиваем пачками по 8 слагов: мелкие ответы приходят быстро и не отваливаются по таймауту.
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < need.size(); i += 8) {
            chunks.add(new ArrayList<>(need.subList(i, Math.min(need.size(), i + 8))));
        }
        final java.util.concurrent.atomic.AtomicInteger left =
                new java.util.concurrent.atomic.AtomicInteger(chunks.size());
        for (List<String> chunk : chunks) {
            Api.getAnimeBySlugs(chunk, (list, error) -> Ui.postSafe(() -> {
                Ui.safe(() -> {
                    if (list != null) {
                        for (Models.AnimeSummary anime : list) {
                            if (anime == null) continue;
                            String cover = anime.cover != null && !anime.cover.isEmpty() ? anime.cover : anime.coverSmall;
                            if (anime.slug != null && cover != null && !cover.isEmpty()) COVERS.put(anime.slug, cover);
                        }
                        persist();
                    }
                });
                if (left.decrementAndGet() == 0) {
                    loading = false;
                    flush();
                }
            }));
        }
    }

    private static void persist() {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, String> entry : COVERS.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            Prefs.put(STORE_KEY, json.toString());
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private static void flush() {
        List<Runnable> waiters = new ArrayList<>(WAITERS);
        WAITERS.clear();
        for (Runnable runnable : waiters) {
            if (runnable != null) Ui.safe(runnable);
        }
    }
}
