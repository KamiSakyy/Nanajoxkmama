package com.anibeat.app.ui;

import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Жанровый фильтр каталога — порт core/catalog.ts (GENRES + trackGenreIds). */
public final class Genres {

    /** Каталог жанров — единый источник Api.GENRES (core/catalog.ts). */
    public static final List<Models.GenreDef> ALL = com.anibeat.app.data.Api.GENRES;

    private Genres() {
    }

    public static List<String> idsFor(Integer malId) {
        List<String> out = new ArrayList<>();
        List<String> genres = Meta.genresOf(malId);
        if (genres.isEmpty()) return out;
        for (Models.GenreDef def : ALL) {
            for (String needle : def.needles) {
                boolean hit = false;
                for (String g : genres) {
                    if (g != null && g.toLowerCase().contains(needle)) {
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    out.add(def.id);
                    break;
                }
            }
        }
        return out;
    }

    /** Фильтр с мягким откатом: без метаданных трек остаётся видимым. */
    public static List<Models.Track> filter(List<Models.Track> tracks, String genre) {
        if (genre == null || genre.isEmpty()) return tracks;
        List<Models.Track> out = new ArrayList<>();
        for (Models.Track t : tracks) {
            List<String> ids = idsFor(t.anime == null ? null : t.anime.malId);
            if (ids.isEmpty() || ids.contains(genre)) out.add(t);
        }
        return out;
    }
}
