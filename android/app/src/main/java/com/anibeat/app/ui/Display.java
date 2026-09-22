package com.anibeat.app.ui;

import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;

/** Порт lib/display.ts — выбор названия и обложки с учётом метаданных и экономии трафика. */
public final class Display {

    public String title = "";
    public String original;
    public String cover;
    public String thumb;
    public String banner;
    public String color;
    public Double score;
    public Integer malId;

    private Display() {
    }

    public static Display of(Models.AnimeRef anime, String fallbackCover, String fallbackSmall) {
        Display d = new Display();
        d.malId = anime == null ? null : anime.malId;
        Models.AnimeMeta meta = Meta.peek(d.malId);
        String name = anime == null || anime.name == null ? "" : anime.name;
        String ru = Settings.ruTitles && meta != null ? meta.ru : null;
        d.title = ru != null && !ru.isEmpty() ? ru : (name.isEmpty() && meta != null && meta.name != null ? meta.name : name);
        String large = fallbackCover;
        String small = fallbackSmall;
        String hiMeta = meta == null ? null : (meta.poster != null && !meta.poster.isEmpty() ? meta.poster : meta.posterShiki);
        String hi = hiMeta == null ? null : Meta.fixShikiHost(hiMeta);
        if (Settings.dataSaver) {
            d.cover = first(small, large, hi);
            d.thumb = d.cover;
        } else {
            d.cover = first(hi, large, small);
            d.thumb = first(large, hi, small);
        }
        d.original = ru != null && !ru.isEmpty() && !ru.equals(name) && !name.isEmpty() ? name : null;
        d.banner = meta == null ? null : meta.banner;
        d.color = meta == null ? null : meta.color;
        d.score = meta == null ? null : meta.score;
        return d;
    }

    public static Display track(Models.Track t) {
        if (t == null) return of(null, null, null);
        Display d = of(t.anime, t.cover, t.coverSmall);
        if (t.anime == null || t.anime.name == null || t.anime.name.isEmpty()) {
            d.title = t.anime != null ? t.anime.name : "";
        }
        return d;
    }

    public static Display summary(Models.AnimeSummary a) {
        if (a == null) return of(null, null, null);
        return of(a, a.cover, a.coverSmall);
    }

    private static String first(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v;
        }
        return null;
    }
}
