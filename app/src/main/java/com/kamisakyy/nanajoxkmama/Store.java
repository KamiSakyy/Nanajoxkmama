package com.kamisakyy.nanajoxkmama;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SharedPreferences store for the library, queue, settings and offline index —
 * platform-only so the release APK stays dependency-free.
 */
public final class Store {
    private static final String PREFS = "anibeat.store";
    private static final String FAVORITES = "favorites";
    private static final String HISTORY = "history";
    private static final String PLAYLISTS = "playlists";
    private static final String OFFLINE = "offline";
    private static final String QUEUE = "queue";
    private static final String QUEUE_ORIGINAL = "queue_original";
    private static final String QUEUE_INDEX = "queue_index";
    private static final String SHUFFLE = "shuffle";
    private static final String REPEAT = "repeat";
    private static final String MATURE = "mature";
    private static final String EXTRA_SOURCES = "extra_sources";
    private static final String DATA_SAVER = "data_saver";
    private static final String PRELOAD = "preload_next";
    private static final String RU_TITLES = "ru_titles";
    private static final String PERIOD = "period";
    private static final String DOWNLOAD_KIND = "download_kind";
    private static final String RECENT_SEARCH = "recent_search";
    private static final String CURRENT = "current";
    private static final String PLAYING = "playing";
    private static final String POSITION = "position";
    private static final String DURATION = "duration";
    private static final String HOME_CACHE = "home_cache";
    private static final String LAST_RANDOM = "last_random";
    private static final String FRESH_CACHE = "fresh_cache";
    private static final String FRESH_KEY = "fresh_key";

    private static Context appContext;

    private Store() { }

    public static void init(Context context) {
        if (appContext == null) appContext = context.getApplicationContext();
    }

    private static SharedPreferences p() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /* ---------------- library ---------------- */

    public static ArrayList<Track> getFavorites() { return getTracks(FAVORITES); }

    public static boolean isFavorite(String trackId) {
        for (Track t : getFavorites()) if (t.id.equals(trackId)) return true;
        return false;
    }

    /** @return true when the track was added (false = removed). */
    public static boolean toggleFavorite(Track track) {
        ArrayList<Track> list = getTracks(FAVORITES);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(track.id)) {
                list.remove(i);
                saveTracks(FAVORITES, list);
                return false;
            }
        }
        list.add(0, track.copy());
        saveTracks(FAVORITES, list);
        return true;
    }

    public static ArrayList<Track> getHistory() { return getTracks(HISTORY); }

    public static void addToHistory(Track track) {
        ArrayList<Track> list = getTracks(HISTORY);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(track.id)) list.remove(i);
        list.add(0, track.copy());
        while (list.size() > 60) list.remove(list.size() - 1);
        saveTracks(HISTORY, list);
    }

    public static void clearHistory() { saveTracks(HISTORY, new ArrayList<Track>()); }

    public static ArrayList<Playlist> getPlaylists() {
        try {
            return Playlist.fromJsonArray(new JSONArray(p().getString(PLAYLISTS, "[]")));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void savePlaylists(ArrayList<Playlist> list) {
        p().edit().putString(PLAYLISTS, Playlist.toJsonArray(list).toString()).apply();
    }

    public static Playlist createPlaylist(String name, List<Track> seed) {
        Playlist pl = new Playlist();
        pl.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        pl.name = name == null || name.trim().isEmpty() ? "Новый плейлист" : name.trim();
        pl.createdAt = System.currentTimeMillis();
        if (seed != null) pl.tracks.addAll(seed);
        ArrayList<Playlist> list = getPlaylists();
        list.add(0, pl);
        savePlaylists(list);
        return pl;
    }

    public static Playlist getPlaylist(String id) {
        for (Playlist pl : getPlaylists()) if (pl.id.equals(id)) return pl;
        return null;
    }

    public static void renamePlaylist(String playlistId, String name) {
        ArrayList<Playlist> list = getPlaylists();
        for (Playlist pl : list) if (pl.id.equals(playlistId)) pl.name = name.trim();
        savePlaylists(list);
    }

    public static void deletePlaylist(String playlistId) {
        ArrayList<Playlist> list = getPlaylists();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(playlistId)) list.remove(i);
        savePlaylists(list);
    }

    public static boolean addToPlaylist(String playlistId, Track track) {
        ArrayList<Playlist> list = getPlaylists();
        for (Playlist pl : list) {
            if (!pl.id.equals(playlistId)) continue;
            for (Track t : pl.tracks) if (t.id.equals(track.id)) return false;
            pl.tracks.add(track.copy());
            savePlaylists(list);
            return true;
        }
        return false;
    }

    public static void removeFromPlaylist(String playlistId, String trackId) {
        ArrayList<Playlist> list = getPlaylists();
        for (Playlist pl : list) {
            if (!pl.id.equals(playlistId)) continue;
            for (int i = pl.tracks.size() - 1; i >= 0; i--) if (pl.tracks.get(i).id.equals(trackId)) pl.tracks.remove(i);
        }
        savePlaylists(list);
    }

    public static ArrayList<String> getRecentSearches() {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p().getString(RECENT_SEARCH, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (Exception ignored) { }
        return out;
    }

    public static void addRecentSearch(String query) {
        String s = query.trim();
        if (s.isEmpty()) return;
        ArrayList<String> list = getRecentSearches();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).equalsIgnoreCase(s)) list.remove(i);
        list.add(0, s);
        while (list.size() > 10) list.remove(list.size() - 1);
        JSONArray a = new JSONArray();
        for (String x : list) a.put(x);
        p().edit().putString(RECENT_SEARCH, a.toString()).apply();
    }

    public static void clearRecentSearches() { p().edit().putString(RECENT_SEARCH, "[]").apply(); }

    /* ---------------- queue / playback ---------------- */

    public static void saveTracks(String key, List<Track> tracks) {
        p().edit().putString(key, Track.toJsonArray(tracks).toString()).apply();
    }

    public static ArrayList<Track> getTracks(String key) {
        try { return Track.fromJsonArray(new JSONArray(p().getString(key, "[]"))); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

    /* ---------------- offline files ---------------- */

    public static File offlineDirectory() {
        File base = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (base == null) base = new File(appContext.getFilesDir(), "music");
        File directory = new File(base, "offline");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    public static File offlineFile(Track track) {
        String safe = Integer.toHexString(track.id.hashCode()) + "_" + Integer.toHexString(track.title.hashCode()) + ".audio";
        return new File(offlineDirectory(), safe);
    }

    public static boolean isOffline(Track track) {
        for (Track saved : getOfflineTracks()) if (saved.id.equals(track.id)) return true;
        return false;
    }

    public static ArrayList<Track> getOfflineTracks() { return getTracks(OFFLINE); }

    public static void saveOffline(Track track, File file) {
        Track copy = track.copy();
        copy.offlinePath = file.getAbsolutePath();
        ArrayList<Track> list = getTracks(OFFLINE);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(copy.id)) {
            File old = new File(list.get(i).offlinePath);
            if (!old.equals(file)) old.delete();
            list.remove(i);
        }
        list.add(0, copy);
        saveTracks(OFFLINE, list);
    }

    public static void removeOffline(String trackId) {
        ArrayList<Track> list = getTracks(OFFLINE);
        for (int i = list.size() - 1; i >= 0; i--) {
            Track t = list.get(i);
            if (t.id.equals(trackId)) {
                if (t.offlinePath != null && !t.offlinePath.isEmpty()) new File(t.offlinePath).delete();
                list.remove(i);
            }
        }
        saveTracks(OFFLINE, list);
    }

    public static void clearOffline() {
        for (Track t : getOfflineTracks()) {
            if (t.offlinePath != null && !t.offlinePath.isEmpty()) new File(t.offlinePath).delete();
        }
        saveTracks(OFFLINE, new ArrayList<Track>());
    }

    public static long offlineBytes() {
        long n = 0;
        for (Track t : getOfflineTracks()) {
            if (t.offlinePath != null && !t.offlinePath.isEmpty()) n += new File(t.offlinePath).length();
        }
        return n;
    }

    /* ---------------- caches ---------------- */

    public static void saveHomeCache(List<Track> tracks) { saveTracks(HOME_CACHE, tracks); }
    public static ArrayList<Track> getHomeCache() { return getTracks(HOME_CACHE); }
    public static void saveLastRandom(List<Track> tracks) { saveTracks(LAST_RANDOM, tracks); }
    public static ArrayList<Track> getLastRandom() { return getTracks(LAST_RANDOM); }
    public static void saveFreshCache(String key, List<Track> tracks) {
        p().edit().putString(FRESH_KEY, key).apply();
        saveTracks(FRESH_CACHE, tracks);
    }
    public static ArrayList<Track> getFreshCache(String key) {
        return key.equals(p().getString(FRESH_KEY, "")) ? getTracks(FRESH_CACHE) : new ArrayList<Track>();
    }

    /* ---------------- queue state ---------------- */

    public static void saveQueue(List<Track> tracks, int index) {
        p().edit().putString(QUEUE, Track.toJsonArray(tracks).toString()).putInt(QUEUE_INDEX, index).apply();
    }

    public static ArrayList<Track> getQueue() { return getTracks(QUEUE); }
    public static int getQueueIndex() { return p().getInt(QUEUE_INDEX, 0); }
    public static void setQueueIndex(int index) { p().edit().putInt(QUEUE_INDEX, index).apply(); }

    public static void saveOriginalQueue(List<Track> tracks) {
        if (tracks == null) p().edit().remove(QUEUE_ORIGINAL).apply();
        else saveTracks(QUEUE_ORIGINAL, tracks);
    }

    public static ArrayList<Track> getOriginalQueue() { return getTracks(QUEUE_ORIGINAL); }

    public static boolean isShuffle() { return p().getBoolean(SHUFFLE, false); }
    public static void setShuffle(boolean value) { p().edit().putBoolean(SHUFFLE, value).apply(); }

    /** off | all | one */
    public static String getRepeat() { return p().getString(REPEAT, "off"); }
    public static void setRepeat(String value) { p().edit().putString(REPEAT, value).apply(); }

    public static void setPlayback(Track track, boolean playing, long position, long duration) {
        SharedPreferences.Editor editor = p().edit().putBoolean(PLAYING, playing).putLong(POSITION, position).putLong(DURATION, duration);
        if (track == null) editor.remove(CURRENT); else editor.putString(CURRENT, track.toJson().toString());
        editor.apply();
    }

    public static Track getCurrentTrack() {
        try {
            String raw = p().getString(CURRENT, "");
            if (raw == null || raw.isEmpty() || "{}".equals(raw)) return null;
            return Track.fromJson(new JSONObject(raw));
        } catch (Exception ignored) { return null; }
    }

    public static boolean isPlaying() { return p().getBoolean(PLAYING, false); }
    public static long getPosition() { return p().getLong(POSITION, 0L); }
    public static long getDuration() { return p().getLong(DURATION, 0L); }

    /* ---------------- settings ---------------- */

    /** today | week | all */
    public static String getPeriod() { return p().getString(PERIOD, "all"); }
    public static void setPeriod(String value) { p().edit().putString(PERIOD, value).apply(); }

    public static boolean isMatureEnabled() { return p().getBoolean(MATURE, false); }
    public static void setMatureEnabled(boolean value) { p().edit().putBoolean(MATURE, value).apply(); }

    public static boolean isExtraSourcesEnabled() { return p().getBoolean(EXTRA_SOURCES, true); }
    public static void setExtraSourcesEnabled(boolean value) { p().edit().putBoolean(EXTRA_SOURCES, value).apply(); }

    public static boolean isDataSaverEnabled() { return p().getBoolean(DATA_SAVER, false); }
    public static void setDataSaverEnabled(boolean value) { p().edit().putBoolean(DATA_SAVER, value).apply(); }

    public static boolean isPreloadNext() { return p().getBoolean(PRELOAD, true); }
    public static void setPreloadNext(boolean value) { p().edit().putBoolean(PRELOAD, value).apply(); }

    public static boolean isRuTitlesEnabled() { return p().getBoolean(RU_TITLES, true); }
    public static void setRuTitlesEnabled(boolean value) { p().edit().putBoolean(RU_TITLES, value).apply(); }

    /** audio | video */
    public static String getDownloadKind() { return p().getString(DOWNLOAD_KIND, "audio"); }
    public static void setDownloadKind(String value) { p().edit().putString(DOWNLOAD_KIND, value).apply(); }
}
