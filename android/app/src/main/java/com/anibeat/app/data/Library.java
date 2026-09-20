package com.anibeat.app.data;

import static com.anibeat.app.data.Models.Playlist;
import static com.anibeat.app.data.Models.Track;

import com.anibeat.app.core.Prefs;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Медиатека: избранное, история, плейлисты (store/library.tsx). */
public final class Library {

    public interface Listener {
        void onChanged();
    }

    private static final String KEY_FAV = "library.favorites";
    private static final String KEY_HISTORY = "library.history";
    private static final String KEY_PLAYLISTS = "library.playlists";

    private static final List<Track> FAVORITES = new ArrayList<>();
    private static final List<Track> HISTORY = new ArrayList<>();
    private static final List<Playlist> PLAYLISTS = new ArrayList<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final List<String> RECENT = new ArrayList<>();

    private Library() {
    }

    public static void init() {
        FAVORITES.clear();
        HISTORY.clear();
        PLAYLISTS.clear();
        RECENT.clear();
        RECENT.addAll(Prefs.getStringList("library.recentSearches"));
        for (JSONObject o : Prefs.getObjectList(KEY_FAV)) FAVORITES.add(Track.fromJson(o));
        for (JSONObject o : Prefs.getObjectList(KEY_HISTORY)) HISTORY.add(Track.fromJson(o));
        for (JSONObject o : Prefs.getObjectList(KEY_PLAYLISTS)) PLAYLISTS.add(Playlist.fromJson(o));
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void emit() {
        for (Listener l : new ArrayList<>(LISTENERS)) l.onChanged();
    }

    private static void persistFavorites() {
        List<JSONObject> out = new ArrayList<>();
        for (Track t : FAVORITES) out.add(t.toJson());
        Prefs.putObjectList(KEY_FAV, out);
    }

    private static void persistHistory() {
        List<JSONObject> out = new ArrayList<>();
        for (Track t : HISTORY) out.add(t.toJson());
        Prefs.putObjectList(KEY_HISTORY, out);
    }

    private static void persistPlaylists() {
        List<JSONObject> out = new ArrayList<>();
        for (Playlist p : PLAYLISTS) out.add(p.toJson());
        Prefs.putObjectList(KEY_PLAYLISTS, out);
    }

    /* ------------------------- избранное ------------------------- */

    public static List<Track> favorites() {
        return new ArrayList<>(FAVORITES);
    }

    public static boolean isFavorite(String trackId) {
        for (Track t : FAVORITES) if (t.id.equals(trackId)) return true;
        return false;
    }

    /** @return true, если трек добавлен; false — если убран. */
    public static boolean toggleFavorite(Track track) {
        for (int i = 0; i < FAVORITES.size(); i++) {
            if (FAVORITES.get(i).id.equals(track.id)) {
                FAVORITES.remove(i);
                persistFavorites();
                emit();
                return false;
            }
        }
        FAVORITES.add(0, track);
        persistFavorites();
        emit();
        return true;
    }

    /* ------------------------- история ------------------------- */

    public static List<Track> history() {
        return new ArrayList<>(HISTORY);
    }

    public static void addToHistory(Track track) {
        for (int i = 0; i < HISTORY.size(); i++) {
            if (HISTORY.get(i).id.equals(track.id)) {
                HISTORY.remove(i);
                break;
            }
        }
        HISTORY.add(0, track);
        while (HISTORY.size() > 100) HISTORY.remove(HISTORY.size() - 1);
        persistHistory();
        emit();
    }

    public static void clearHistory() {
        HISTORY.clear();
        persistHistory();
        emit();
    }

    /* ------------------------- плейлисты ------------------------- */

    public static List<Playlist> playlists() {
        return new ArrayList<>(PLAYLISTS);
    }

    public static Playlist playlist(String id) {
        for (Playlist p : PLAYLISTS) if (p.id.equals(id)) return p;
        return null;
    }

    public static Playlist createPlaylist(String name, List<Track> tracks) {
        Playlist p = new Playlist();
        p.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        p.name = name;
        p.createdAt = System.currentTimeMillis();
        if (tracks != null) {
            for (Track t : tracks) {
                boolean exists = false;
                for (Track x : p.tracks) if (x.id.equals(t.id)) exists = true;
                if (!exists) p.tracks.add(t);
            }
        }
        PLAYLISTS.add(0, p);
        persistPlaylists();
        emit();
        return p;
    }

    public static void addToPlaylist(String playlistId, Track track) {
        Playlist p = playlist(playlistId);
        if (p == null) return;
        for (Track t : p.tracks) if (t.id.equals(track.id)) return;
        p.tracks.add(track);
        persistPlaylists();
        emit();
    }

    public static void removeFromPlaylist(String playlistId, String trackId) {
        Playlist p = playlist(playlistId);
        if (p == null) return;
        for (int i = 0; i < p.tracks.size(); i++) {
            if (p.tracks.get(i).id.equals(trackId)) {
                p.tracks.remove(i);
                break;
            }
        }
        persistPlaylists();
        emit();
    }

    public static void moveInPlaylist(String playlistId, int from, int to) {
        Playlist p = playlist(playlistId);
        if (p == null || from < 0 || from >= p.tracks.size() || to < 0 || to >= p.tracks.size()) return;
        Track item = p.tracks.remove(from);
        p.tracks.add(to, item);
        persistPlaylists();
        emit();
    }

    public static void renamePlaylist(String playlistId, String name) {
        Playlist p = playlist(playlistId);
        if (p == null) return;
        p.name = name;
        persistPlaylists();
        emit();
    }

    public static void deletePlaylist(String playlistId) {
        for (int i = 0; i < PLAYLISTS.size(); i++) {
            if (PLAYLISTS.get(i).id.equals(playlistId)) {
                PLAYLISTS.remove(i);
                break;
            }
        }
        persistPlaylists();
        emit();
    }

    /* ------------------------- поиск ------------------------- */

    public static List<String> recentSearches() {
        return new ArrayList<>(RECENT);
    }

    public static void addRecentSearch(String query) {
        if (query == null || query.trim().isEmpty()) return;
        String value = query.trim();
        RECENT.remove(value);
        RECENT.add(0, value);
        while (RECENT.size() > 8) RECENT.remove(RECENT.size() - 1);
        Prefs.putStringList("library.recentSearches", RECENT);
        emit();
    }

    public static void clearRecentSearches() {
        RECENT.clear();
        Prefs.putStringList("library.recentSearches", RECENT);
        emit();
    }
}
