package com.kamisakyy.nanajoxkmama;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Compact, serialisable track model shared by the catalogue, library and player. */
public final class Track {
    public String id = "";
    public int themeId = -1;
    public String themeSlug = "";
    public String type = "OP";
    public int sequence = -1;
    public String title = "Без названия";
    public String artist = "Неизвестный исполнитель";
    public String artistSlug = "";
    public String animeName = "Неизвестное аниме";
    public String animeSlug = "";
    public int animeYear = -1;
    public String season = "";
    public int malId = -1;
    public String cover = "";
    public String coverSmall = "";
    public String audioUrl = "";
    public String videoUrl = "";
    public int resolution = -1;
    public String tags = "";
    public int version = -1;
    public String episodes = "";
    public boolean nsfw;
    public boolean spoiler;
    public String source = "primary";
    /** Set only for a downloaded local copy; never sent back to a remote API. */
    public String offlinePath = "";

    public Track copy() {
        Track t = new Track();
        t.id = id;
        t.themeId = themeId;
        t.themeSlug = themeSlug;
        t.type = type;
        t.sequence = sequence;
        t.title = title;
        t.artist = artist;
        t.artistSlug = artistSlug;
        t.animeName = animeName;
        t.animeSlug = animeSlug;
        t.animeYear = animeYear;
        t.season = season;
        t.malId = malId;
        t.cover = cover;
        t.coverSmall = coverSmall;
        t.audioUrl = audioUrl;
        t.videoUrl = videoUrl;
        t.resolution = resolution;
        t.tags = tags;
        t.version = version;
        t.episodes = episodes;
        t.nsfw = nsfw;
        t.spoiler = spoiler;
        t.source = source;
        t.offlinePath = offlinePath;
        return t;
    }

    public String displayArtist() {
        return artist == null || artist.trim().isEmpty() ? "Неизвестный исполнитель" : artist;
    }

    /** Compact theme marker: OP1 / ED2 / IN. */
    public String themeTag() {
        return themeSlug == null || themeSlug.isEmpty() ? type : themeSlug;
    }

    public String displayTheme() {
        String label = themeTag();
        if ("OP".equals(type)) return "Опенинг · " + label;
        if ("ED".equals(type)) return "Эндинг · " + label;
        return "Вставка · " + label;
    }

    public static String typeRu(String type) {
        if ("OP".equals(type)) return "Опенинг";
        if ("ED".equals(type)) return "Эндинг";
        return "Вставка";
    }

    public boolean isExtra() {
        return "extra".equals(source);
    }

    public String playableUrl() {
        return offlinePath != null && !offlinePath.isEmpty() ? offlinePath : audioUrl;
    }

    public String versionLabel() {
        return version > 0 ? "v" + version : "";
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("themeId", themeId);
            o.put("themeSlug", themeSlug);
            o.put("type", type);
            o.put("sequence", sequence);
            o.put("title", title);
            o.put("artist", artist);
            o.put("artistSlug", artistSlug);
            o.put("animeName", animeName);
            o.put("animeSlug", animeSlug);
            o.put("animeYear", animeYear);
            o.put("season", season);
            o.put("malId", malId);
            o.put("cover", cover);
            o.put("coverSmall", coverSmall);
            o.put("audioUrl", audioUrl);
            o.put("videoUrl", videoUrl);
            o.put("resolution", resolution);
            o.put("tags", tags);
            o.put("version", version);
            o.put("episodes", episodes);
            o.put("nsfw", nsfw);
            o.put("spoiler", spoiler);
            o.put("source", source);
            o.put("offlinePath", offlinePath);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Track fromJson(JSONObject o) {
        if (o == null) return null;
        Track t = new Track();
        t.id = text(o, "id", "");
        t.themeId = number(o, "themeId", -1);
        t.themeSlug = text(o, "themeSlug", "");
        t.type = text(o, "type", "OP");
        t.sequence = number(o, "sequence", -1);
        t.title = text(o, "title", "Без названия");
        t.artist = text(o, "artist", "Неизвестный исполнитель");
        t.artistSlug = text(o, "artistSlug", "");
        t.animeName = text(o, "animeName", "Неизвестное аниме");
        t.animeSlug = text(o, "animeSlug", "");
        t.animeYear = number(o, "animeYear", -1);
        t.season = text(o, "season", "");
        t.malId = number(o, "malId", -1);
        t.cover = text(o, "cover", "");
        t.coverSmall = text(o, "coverSmall", "");
        t.audioUrl = text(o, "audioUrl", "");
        t.videoUrl = text(o, "videoUrl", "");
        if (t.videoUrl.isEmpty()) t.videoUrl = t.audioUrl;
        t.resolution = number(o, "resolution", -1);
        t.tags = text(o, "tags", "");
        t.version = number(o, "version", -1);
        t.episodes = text(o, "episodes", "");
        t.nsfw = o.optBoolean("nsfw", false);
        t.spoiler = o.optBoolean("spoiler", false);
        t.source = text(o, "source", "primary");
        t.offlinePath = text(o, "offlinePath", "");
        return t;
    }

    public static JSONArray toJsonArray(List<Track> tracks) {
        JSONArray a = new JSONArray();
        if (tracks == null) return a;
        for (Track t : tracks) if (t != null) a.put(t.toJson());
        return a;
    }

    public static ArrayList<Track> fromJsonArray(JSONArray a) {
        ArrayList<Track> list = new ArrayList<>();
        if (a == null) return list;
        for (int i = 0; i < a.length(); i++) {
            Track t = fromJson(a.optJSONObject(i));
            if (t != null && !t.id.isEmpty()) list.add(t);
        }
        return list;
    }

    private static String text(JSONObject o, String key, String fallback) {
        String value = o.optString(key, fallback);
        return value == null || "null".equals(value) ? fallback : value;
    }

    private static int number(JSONObject o, String key, int fallback) {
        Object value = o.opt(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value != null) {
            try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }
}
