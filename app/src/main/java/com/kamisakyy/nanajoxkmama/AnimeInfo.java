package com.kamisakyy.nanajoxkmama;

import org.json.JSONObject;

import java.util.ArrayList;

/** Anime card data: catalogue fields + metadata enrichment (Shikimori / AniList). */
public class AnimeInfo {
    public int id = -1;
    public String name = "";
    public String slug = "";
    public int year = -1;
    public String season = "";
    public String format = "";
    public String synopsis = "";
    public String cover = "";
    public String coverSmall = "";
    public int malId = -1;
    /* enrichment (MetaApi) */
    public String ruName = "";
    public double score = -1;
    public String kind = "";
    public int episodes = -1;
    public String genres = "";
    public String banner = "";
    public String color = "";

    public String displayTitle() {
        if (Store.isRuTitlesEnabled() && ruName != null && !ruName.isEmpty()) return ruName;
        return name != null && !name.isEmpty() ? name : (ruName == null ? "" : ruName);
    }

    public void applyMeta(MetaApi.AnimeMeta meta) {
        if (meta == null) return;
        if (ruName.isEmpty()) ruName = meta.ru == null ? "" : meta.ru;
        if (score < 0 && meta.score > 0) score = meta.score;
        if (kind.isEmpty()) kind = meta.kind == null ? "" : meta.kind;
        if (episodes < 0) episodes = meta.episodes;
        if (genres.isEmpty()) genres = meta.genres == null ? "" : meta.genres;
        if (banner.isEmpty()) banner = meta.banner == null ? "" : meta.banner;
        if (color.isEmpty()) color = meta.color == null ? "" : meta.color;
        if (cover.isEmpty() && meta.poster != null && !meta.poster.isEmpty()) cover = meta.poster;
    }

    public void fillCoversFrom(Track t) {
        if (cover.isEmpty()) cover = t.cover;
        if (coverSmall.isEmpty()) coverSmall = t.coverSmall;
    }

    /** Full detail returned by the anime page endpoint. */
    public static final class Detail extends AnimeInfo {
        public final ArrayList<Track> tracks = new ArrayList<>();
        public final ArrayList<String> studios = new ArrayList<>();
        public final ArrayList<String> series = new ArrayList<>();
        public String shikiDescription = "";
    }

    public static AnimeInfo fromTrack(Track t) {
        AnimeInfo a = new AnimeInfo();
        a.name = t.animeName;
        a.slug = t.animeSlug;
        a.year = t.animeYear;
        a.season = t.season;
        a.malId = t.malId;
        a.cover = t.cover;
        a.coverSmall = t.coverSmall;
        return a;
    }
}
