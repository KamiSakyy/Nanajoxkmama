package com.anibeat.app.ui;

import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Жанровый фильтр каталога — порт core/catalog.ts (GENRES + trackGenreIds). */
public final class Genres {

    private static final String[][] DB = {
            {"экшен", "боевик", "action", "action"},
            {"фантастика", "sci-fi", "фэнтези", "fantasy", "fantasy"},
            {"романтика", "romance", "romance"},
            {"комедия", "comedy", "comedy"},
            {"драма", "drama", "drama"},
            {"приключения", "adventure", "adventure"},
            {"спорт", "sports", "sports"},
            {"музыка", "music", "идол", "idol", "music"},
            {"хоррор", "ужасы", "horror", "триллер", "thriller", "мистика", "mystery", "детектив", "thriller"},
            {"сёдзё", "shoujo", "shoujo"},
            {"сейнен", "seinen", "seinen"},
            {"сёнэн", "shounen", "shonen", "shounen"},
            {"меха", "mecha", "mecha"},
            {"повседневность", "slice of life", "slice"},
            {"школа", "school", "school"},
            {"исторический", "historical", "historical"},
    };

    public static final List<Models.GenreDef> ALL = new ArrayList<>();

    static {
        for (String[] row : DB) {
            String id = row[row.length - 1];
            String label = row[0].substring(0, 1).toUpperCase() + row[0].substring(1);
            String[] needles = new String[row.length - 1];
            System.arraycopy(row, 0, needles, 0, row.length - 1);
            ALL.add(new Models.GenreDef(id, label, needles));
        }
    }

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
