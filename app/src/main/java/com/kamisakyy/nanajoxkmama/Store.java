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

/** Small SharedPreferences store; keeping it platform-only avoids a database dependency. */
public final class Store {
    private static final String PREFS = "anibeat.store";
    private static final String FAVORITES = "favorites";
    private static final String HISTORY = "history";
    private static final String PLAYLISTS = "playlists";
    private static final String OFFLINE = "offline";
    private static final String QUEUE = "queue";
    private static final String QUEUE_INDEX = "queue_index";
    private static final String CURRENT = "current";
    private static final String PLAYING = "playing";
    private static final String POSITION = "position";
    private static final String DURATION = "duration";
    private static final String HOME_CACHE = "home_cache";
    private static final String MATURE = "mature";
    private static final String EXTRA_SOURCES = "extra_sources";
    private static final String DATA_SAVER = "data_saver";

    private static SharedPreferences preferences;
    private static Context appContext;

    private Store() { }

    public static void init(Context context) {
        if (preferences == null) {
            appContext = context.getApplicationContext();
            preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    private static SharedPreferences p() {
        if (preferences == null) throw new IllegalStateException("Store.init() first");
        return preferences;
    }

    public static ArrayList<Track> getFavorites() { return getTracks(FAVORITES); }
    public static ArrayList<Track> getHistory() { return getTracks(HISTORY); }
    public static ArrayList<Track> getOfflineTracks() {
        ArrayList<Track> saved = getTracks(OFFLINE);
        ArrayList<Track> live = new ArrayList<>();
        for (Track track : saved) {
            if (track.offlinePath != null && !track.offlinePath.isEmpty() && new File(track.offlinePath).exists()) live.add(track);
        }
        if (live.size() != saved.size()) saveTracks(OFFLINE, live);
        return live;
    }

    public static boolean isFavorite(String id) {
        for (Track t : getFavorites()) if (t.id.equals(id)) return true;
        return false;
    }

    public static boolean toggleFavorite(Track track) {
        ArrayList<Track> list = getFavorites();
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(track.id)) {
                list.remove(i);
                removed = true;
            }
        }
        if (!removed) list.add(0, track.copy());
        saveTracks(FAVORITES, list);
        return !removed;
    }

    public static void addHistory(Track track) {
        ArrayList<Track> list = getHistory();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(track.id)) list.remove(i);
        list.add(0, track.copy());
        while (list.size() > 60) list.remove(list.size() - 1);
        saveTracks(HISTORY, list);
    }

    public static void clearHistory() { saveTracks(HISTORY, new ArrayList<Track>()); }

    public static ArrayList<Playlist> getPlaylists() {
        try { return Playlist.fromJsonArray(new JSONArray(p().getString(PLAYLISTS, "[]"))); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

    public static Playlist createPlaylist(String name) {
        Playlist playlist = new Playlist();
        playlist.id = UUID.randomUUID().toString();
        playlist.name = name == null || name.trim().isEmpty() ? "Новый плейлист" : name.trim();
        playlist.createdAt = System.currentTimeMillis();
        ArrayList<Playlist> list = getPlaylists();
        list.add(0, playlist);
        savePlaylists(list);
        return playlist;
    }

    public static void savePlaylists(List<Playlist> list) {
        p().edit().putString(PLAYLISTS, Playlist.toJsonArray(list).toString()).apply();
    }

    public static boolean addToPlaylist(String playlistId, Track track) {
        ArrayList<Playlist> list = getPlaylists();
        for (Playlist playlist : list) {
            if (!playlist.id.equals(playlistId)) continue;
            for (Track existing : playlist.tracks) if (existing.id.equals(track.id)) return false;
            playlist.tracks.add(track.copy());
            savePlaylists(list);
            return true;
        }
        return false;
    }

    public static void removeFromPlaylist(String playlistId, String trackId) {
        ArrayList<Playlist> list = getPlaylists();
        for (Playlist playlist : list) {
            if (playlist.id.equals(playlistId)) {
                for (int i = playlist.tracks.size() - 1; i >= 0; i--) if (playlist.tracks.get(i).id.equals(trackId)) playlist.tracks.remove(i);
            }
        }
        savePlaylists(list);
    }

    public static void renamePlaylist(String playlistId, String name) {
        ArrayList<Playlist> list = getPlaylists();
        for (Playlist playlist : list) if (playlist.id.equals(playlistId) && name != null && !name.trim().isEmpty()) playlist.name = name.trim();
        savePlaylists(list);
    }

    public static void deletePlaylist(String playlistId) {
        ArrayList<Playlist> list = getPlaylists();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(playlistId)) list.remove(i);
        savePlaylists(list);
    }

    public static void saveTracks(String key, List<Track> tracks) {
        p().edit().putString(key, Track.toJsonArray(tracks).toString()).apply();
    }

    public static ArrayList<Track> getTracks(String key) {
        try { return Track.fromJsonArray(new JSONArray(p().getString(key, "[]"))); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }

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

    public static void saveOffline(Track track, File file) {
        Track copy = track.copy();
        copy.offlinePath = file.getAbsolutePath();
        ArrayList<Track> list = getOfflineTracks();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(copy.id)) list.remove(i);
        list.add(0, copy);
        saveTracks(OFFLINE, list);
    }

    public static void removeOffline(String trackId) {
        ArrayList<Track> list = getTracks(OFFLINE);
        for (int i = list.size() - 1; i >= 0; i--) {
            Track t = list.get(i);
            if (t.id.equals(trackId)) {
                if (t.offlinePath != null) new File(t.offlinePath).delete();
                list.remove(i);
            }
        }
        saveTracks(OFFLINE, list);
    }

    public static void saveHomeCache(List<Track> tracks) { saveTracks(HOME_CACHE, tracks); }
    public static ArrayList<Track> getHomeCache() { return getTracks(HOME_CACHE); }

    public static void saveQueue(List<Track> tracks, int index) {
        p().edit().putString(QUEUE, Track.toJsonArray(tracks).toString()).putInt(QUEUE_INDEX, index).apply();
    }
    public static ArrayList<Track> getQueue() { return getTracks(QUEUE); }
    public static int getQueueIndex() { return p().getInt(QUEUE_INDEX, 0); }
    public static void setQueueIndex(int index) { p().edit().putInt(QUEUE_INDEX, index).apply(); }

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

    public static boolean isMatureEnabled() { return p().getBoolean(MATURE, false); }
    public static void setMatureEnabled(boolean value) { p().edit().putBoolean(MATURE, value).apply(); }
    public static boolean isExtraSourcesEnabled() { return p().getBoolean(EXTRA_SOURCES, true); }
    public static void setExtraSourcesEnabled(boolean value) { p().edit().putBoolean(EXTRA_SOURCES, value).apply(); }
    public static boolean isDataSaverEnabled() { return p().getBoolean(DATA_SAVER, false); }
    public static void setDataSaverEnabled(boolean value) { p().edit().putBoolean(DATA_SAVER, value).apply(); }

    public static ArrayList<Track> filterMature(List<Track> source) {
        ArrayList<Track> out = new ArrayList<>();
        for (Track t : source) if (Store.isMatureEnabled() || !t.nsfw) out.add(t);
        return out;
    }
}
