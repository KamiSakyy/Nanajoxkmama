package com.anibeat.app.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Модели данных — точное соответствие `src/types.ts`. */
public final class Models {

    private Models() {
    }

    public static class ArtistRef {
        public long id;
        public String name = "";
        public String slug = "";

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("slug", slug);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static ArtistRef fromJson(JSONObject o) {
            ArtistRef a = new ArtistRef();
            a.id = o.optLong("id");
            a.name = o.optString("name");
            a.slug = o.optString("slug");
            return a;
        }
    }

    public static class AnimeRef {
        public long id;
        public String name = "";
        public String slug = "";
        public Integer year;
        public String season;
        /** MyAnimeList id == Shikimori id; null — ещё не разрешён. */
        public Integer malId;
        public Integer anilistId;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("slug", slug);
                if (year != null) o.put("year", (int) year);
                if (season != null) o.put("season", season);
                if (malId != null) o.put("malId", (int) malId);
                if (anilistId != null) o.put("anilistId", (int) anilistId);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static AnimeRef fromJson(JSONObject o) {
            AnimeRef a = new AnimeRef();
            a.id = o.optLong("id");
            a.name = o.optString("name");
            a.slug = o.optString("slug");
            a.year = o.has("year") ? o.optInt("year") : null;
            a.season = o.has("season") ? o.optString("season") : null;
            a.malId = o.has("malId") && !o.isNull("malId") ? o.optInt("malId") : null;
            a.anilistId = o.has("anilistId") && !o.isNull("anilistId") ? o.optInt("anilistId") : null;
            return a;
        }
    }

    public static class Track {
        public String id = "";
        public long themeId;
        public String themeSlug = "";
        public String type = "OP";
        public Integer sequence;
        public String title = "";
        public List<ArtistRef> artists = new ArrayList<>();
        public AnimeRef anime = new AnimeRef();
        public String cover;
        public String coverSmall;
        public String audioUrl = "";
        public String videoUrl = "";
        public Integer resolution;
        public String tags = "";
        public Integer version;
        public String episodes;
        public boolean nsfw;
        public boolean spoiler;
        /** "primary" (AnimeThemes) или "extra" (AnisongDB). */
        public String source = "primary";

        public String artistNames() {
            if (artists.isEmpty()) return "Неизвестный исполнитель";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < artists.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(artists.get(i).name);
            }
            return sb.toString();
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("themeId", themeId);
                o.put("themeSlug", themeSlug);
                o.put("type", type);
                if (sequence != null) o.put("sequence", (int) sequence);
                o.put("title", title);
                JSONArray ar = new JSONArray();
                for (ArtistRef a : artists) ar.put(a.toJson());
                o.put("artists", ar);
                o.put("anime", anime.toJson());
                o.put("cover", cover == null ? JSONObject.NULL : cover);
                o.put("coverSmall", coverSmall == null ? JSONObject.NULL : coverSmall);
                o.put("audioUrl", audioUrl);
                o.put("videoUrl", videoUrl);
                if (resolution != null) o.put("resolution", (int) resolution);
                o.put("tags", tags);
                if (version != null) o.put("version", (int) version);
                o.put("episodes", episodes == null ? JSONObject.NULL : episodes);
                o.put("nsfw", nsfw);
                o.put("spoiler", spoiler);
                o.put("source", source);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static Track fromJson(JSONObject o) {
            Track t = new Track();
            t.id = o.optString("id");
            t.themeId = o.optLong("themeId");
            t.themeSlug = o.optString("themeSlug");
            t.type = o.optString("type", "OP");
            t.sequence = o.has("sequence") ? o.optInt("sequence") : null;
            t.title = o.optString("title");
            JSONArray ar = o.optJSONArray("artists");
            if (ar != null) for (int i = 0; i < ar.length(); i++) t.artists.add(ArtistRef.fromJson(ar.optJSONObject(i)));
            JSONObject an = o.optJSONObject("anime");
            if (an != null) t.anime = AnimeRef.fromJson(an);
            t.cover = o.isNull("cover") ? null : o.optString("cover", null);
            t.coverSmall = o.isNull("coverSmall") ? null : o.optString("coverSmall", null);
            t.audioUrl = o.optString("audioUrl");
            t.videoUrl = o.optString("videoUrl");
            t.resolution = o.has("resolution") && !o.isNull("resolution") ? o.optInt("resolution") : null;
            t.tags = o.optString("tags");
            t.version = o.has("version") && !o.isNull("version") ? o.optInt("version") : null;
            t.episodes = o.isNull("episodes") ? null : o.optString("episodes", null);
            t.nsfw = o.optBoolean("nsfw");
            t.spoiler = o.optBoolean("spoiler");
            t.source = o.optString("source", "primary");
            return t;
        }
    }

    public static class AnimeSummary extends AnimeRef {
        public String cover;
        public String coverSmall;
        public String mediaFormat;
        public String synopsis;
    }

    public static class AnimeDetail extends AnimeSummary {
        public List<Track> tracks = new ArrayList<>();
        public List<String[]> studios = new ArrayList<>();
        public List<String[]> series = new ArrayList<>();
        public List<String[]> resources = new ArrayList<>();
    }

    public static class ArtistSummary extends ArtistRef {
        public String image;
        public String imageSmall;
    }

    public static class ArtistDetail extends ArtistSummary {
        public String information;
        public List<Track> tracks = new ArrayList<>();
    }

    public static class SearchResults {
        public List<AnimeSummary> anime = new ArrayList<>();
        public List<Track> tracks = new ArrayList<>();
        public List<ArtistSummary> artists = new ArrayList<>();
    }

    /** Метаданные из Shikimori/AniList (русские названия, постеры, жанры). */
    public static class AnimeMeta {
        public int malId;
        public String ru;
        public String name;
        public String poster;
        public String posterShiki;
        public String banner;
        public String color;
        public Double score;
        public String kind;
        public String status;
        public Integer episodes;
        public List<String> genres = new ArrayList<>();
        public long ts;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("malId", malId);
                o.put("ru", ru == null ? JSONObject.NULL : ru);
                o.put("name", name == null ? JSONObject.NULL : name);
                o.put("poster", poster == null ? JSONObject.NULL : poster);
                o.put("posterShiki", posterShiki == null ? JSONObject.NULL : posterShiki);
                o.put("banner", banner == null ? JSONObject.NULL : banner);
                o.put("color", color == null ? JSONObject.NULL : color);
                o.put("score", score == null ? JSONObject.NULL : score);
                o.put("kind", kind == null ? JSONObject.NULL : kind);
                o.put("status", status == null ? JSONObject.NULL : status);
                o.put("episodes", episodes == null ? JSONObject.NULL : episodes);
                JSONArray g = new JSONArray();
                for (String s : genres) g.put(s);
                o.put("genres", g);
                o.put("ts", ts);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static AnimeMeta fromJson(JSONObject o) {
            AnimeMeta m = new AnimeMeta();
            m.malId = o.optInt("malId");
            m.ru = o.isNull("ru") ? null : o.optString("ru", null);
            m.name = o.isNull("name") ? null : o.optString("name", null);
            m.poster = o.isNull("poster") ? null : o.optString("poster", null);
            m.posterShiki = o.isNull("posterShiki") ? null : o.optString("posterShiki", null);
            m.banner = o.isNull("banner") ? null : o.optString("banner", null);
            m.color = o.isNull("color") ? null : o.optString("color", null);
            m.score = o.isNull("score") ? null : o.optDouble("score");
            m.kind = o.isNull("kind") ? null : o.optString("kind", null);
            m.status = o.isNull("status") ? null : o.optString("status", null);
            m.episodes = o.has("episodes") && !o.isNull("episodes") ? o.optInt("episodes") : null;
            JSONArray g = o.optJSONArray("genres");
            if (g != null) for (int i = 0; i < g.length(); i++) m.genres.add(g.optString(i));
            m.ts = o.optLong("ts");
            return m;
        }
    }

    public static class Playlist {
        public String id;
        public String name;
        public long createdAt;
        public List<Track> tracks = new ArrayList<>();

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("createdAt", createdAt);
                JSONArray arr = new JSONArray();
                for (Track t : tracks) arr.put(t.toJson());
                o.put("tracks", arr);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static Playlist fromJson(JSONObject o) {
            Playlist p = new Playlist();
            p.id = o.optString("id");
            p.name = o.optString("name");
            p.createdAt = o.optLong("createdAt");
            JSONArray arr = o.optJSONArray("tracks");
            if (arr != null) for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.optJSONObject(i);
                if (t != null) p.tracks.add(Track.fromJson(t));
            }
            return p;
        }
    }

    /** Определение жанра из каталога сайта (core/catalog.ts). */
    public static class GenreDef {
        public final String id;
        public final String label;
        public final String[] needles;

        public GenreDef(String id, String label, String[] needles) {
            this.id = id;
            this.label = label;
            this.needles = needles;
        }
    }

    public static class Mix {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String[] slugs;

        public Mix(String id, String title, String subtitle, String[] slugs) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.slugs = slugs;
        }
    }
}
