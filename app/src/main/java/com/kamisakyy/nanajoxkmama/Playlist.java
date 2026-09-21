package com.kamisakyy.nanajoxkmama;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Playlist {
    public String id = "";
    public String name = "Новый плейлист";
    public long createdAt;
    public final ArrayList<Track> tracks = new ArrayList<>();

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("createdAt", createdAt);
            o.put("tracks", Track.toJsonArray(tracks));
        } catch (Exception ignored) { }
        return o;
    }

    public static Playlist fromJson(JSONObject o) {
        if (o == null) return null;
        Playlist p = new Playlist();
        p.id = o.optString("id", "");
        p.name = o.optString("name", "Новый плейлист");
        p.createdAt = o.optLong("createdAt", 0L);
        JSONArray a = o.optJSONArray("tracks");
        p.tracks.addAll(Track.fromJsonArray(a));
        return p;
    }

    public static JSONArray toJsonArray(List<Playlist> list) {
        JSONArray a = new JSONArray();
        if (list == null) return a;
        for (Playlist p : list) if (p != null) a.put(p.toJson());
        return a;
    }

    public static ArrayList<Playlist> fromJsonArray(JSONArray a) {
        ArrayList<Playlist> list = new ArrayList<>();
        if (a == null) return list;
        for (int i = 0; i < a.length(); i++) {
            Playlist p = fromJson(a.optJSONObject(i));
            if (p != null && !p.id.isEmpty()) list.add(p);
        }
        return list;
    }
}
