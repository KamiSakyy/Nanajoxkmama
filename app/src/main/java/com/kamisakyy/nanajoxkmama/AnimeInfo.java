package com.kamisakyy.nanajoxkmama;

import org.json.JSONObject;

/** Lightweight anime card used by search and the detail screen. */
public final class AnimeInfo {
    public int id = -1;
    public String name = "";
    public String slug = "";
    public int year = -1;
    public String season = "";
    public String format = "";
    public String studio = "";
    public String synopsis = "";
    public String cover = "";
    public String coverSmall = "";
    public int malId = -1;

    public static AnimeInfo fromJson(JSONObject a) {
        AnimeInfo out = new AnimeInfo();
        if (a == null) return out;
        out.id = a.optInt("id", -1);
        out.name = a.optString("name", "");
        out.slug = a.optString("slug", "");
        out.year = a.has("year") && !a.isNull("year") ? a.optInt("year", -1) : -1;
        out.season = a.optString("season", "");
        out.format = a.optString("media_format", "");
        org.json.JSONArray studios = a.optJSONArray("studios");
        if (studios != null && studios.length() > 0) {
            JSONObject s = studios.optJSONObject(0);
            if (s != null) out.studio = s.optString("name", "");
        }
        out.synopsis = a.optString("synopsis", "");
        out.cover = ApiClient.pickImage(a.optJSONArray("images"), "Large Cover");
        out.coverSmall = ApiClient.pickImage(a.optJSONArray("images"), "Small Cover");
        out.malId = ApiClient.externalId(a.optJSONArray("resources"), "MyAnimeList");
        return out;
    }
}
