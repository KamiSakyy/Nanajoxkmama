package com.anibeat.app.player;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.core.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Плеер (store/player.tsx): очередь, перемешивание, повтор, режим видео,
 * воспроизведение в фоне через Media3, сохранение состояния между запусками.
 */
public final class Player {

    public interface Listener {
        void onPlayerChanged();
    }

    private static final String PERSIST_KEY = "player";

    private static MediaController controller;
    private static final List<Models.Track> QUEUE = new ArrayList<>();
    private static List<Models.Track> ORIGINAL;
    private static int index;
    private static boolean shuffle;
    private static String repeat = "off";
    private static boolean videoMode;
    private static boolean muted;
    private static float volume = 1f;
    private static boolean ready;
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private Player() {
    }

    /* ------------------------------------------------------------------ */
    /* Инициализация                                                       */
    /* ------------------------------------------------------------------ */

    public static void init(Context context) {
        if (ready) return;
        restore(context);
        SessionToken token = new SessionToken(context, new ComponentName(context, PlaybackService.class));
        MediaController.Builder builder = new MediaController.Builder(context, token);
        builder.buildAsync().addListener(() -> {
            controller = builder.get();
            controller.addListener(new androidx.media3.common.Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    emit();
                }

                @Override
                public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                    syncIndexFromController();
                    Models.Track track = current();
                    if (track != null && isPlaying()) Library.addToHistory(track);
                    emit();
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    emit();
                }

                @Override
                public void onRepeatModeChanged(int mode) {
                    emit();
                }

                @Override
                public void onShuffleModeEnabledChanged(boolean enabled) {
                    emit();
                }
            });
            if (!QUEUE.isEmpty()) {
                applyQueue(index, false);
            }
            emit();
        }, Runnable::run);
    }

    public static boolean isReady() {
        return controller != null;
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void emit() {
        for (Listener l : new ArrayList<>(LISTENERS)) l.onPlayerChanged();
    }

    /* ------------------------------------------------------------------ */
    /* Состояние                                                           */
    /* ------------------------------------------------------------------ */

    public static List<Models.Track> queue() {
        return new ArrayList<>(QUEUE);
    }

    public static int index() {
        return index;
    }

    public static Models.Track current() {
        if (QUEUE.isEmpty() || index < 0 || index >= QUEUE.size()) return null;
        return QUEUE.get(index);
    }

    public static boolean isPlaying() {
        return controller != null && controller.isPlaying();
    }

    public static boolean isBuffering() {
        return controller != null && controller.getPlaybackState() == androidx.media3.common.Player.STATE_BUFFERING;
    }

    public static boolean shuffle() {
        return shuffle;
    }

    public static String repeat() {
        return repeat;
    }

    public static boolean videoMode() {
        return videoMode;
    }

    public static float volume() {
        return volume;
    }

    public static boolean muted() {
        return muted;
    }

    public static long position() {
        return controller == null ? 0 : controller.getCurrentPosition();
    }

    public static long duration() {
        long d = controller == null ? 0 : controller.getDuration();
        return d < 0 ? 0 : d;
    }

    public static MediaController controller() {
        return controller;
    }

    /* ------------------------------------------------------------------ */
    /* Управление                                                          */
    /* ------------------------------------------------------------------ */

    public static void playTracks(List<Models.Track> tracks, int start, boolean withShuffle) {
        if (tracks == null || tracks.isEmpty()) return;
        videoMode = false;
        List<Models.Track> list = new ArrayList<>(tracks);
        if (withShuffle && list.size() > 1) {
            Models.Track first = list.get(Math.max(0, Math.min(start, list.size() - 1)));
            list.remove(first);
            Collections.shuffle(list, RANDOM);
            list.add(0, first);
            ORIGINAL = new ArrayList<>(tracks);
            shuffle = true;
            start = 0;
        } else {
            ORIGINAL = null;
        }
        QUEUE.clear();
        QUEUE.addAll(list);
        index = Math.max(0, Math.min(start, QUEUE.size() - 1));
        applyQueue(index, true);
        save();
    }

    public static void playTrack(Models.Track track, List<Models.Track> context) {
        if (context != null && !context.isEmpty()) {
            int idx = 0;
            for (int i = 0; i < context.size(); i++) {
                if (context.get(i).id.equals(track.id)) {
                    idx = i;
                    break;
                }
            }
            playTracks(context, idx, false);
            return;
        }
        int existing = indexOf(track.id);
        if (existing >= 0) {
            index = existing;
            applyQueue(index, true);
        } else if (QUEUE.isEmpty()) {
            List<Models.Track> one = new ArrayList<>();
            one.add(track);
            playTracks(one, 0, false);
        } else {
            QUEUE.add(index + 1, track);
            applyQueue(index + 1, true);
        }
        save();
    }

    private static int indexOf(String trackId) {
        for (int i = 0; i < QUEUE.size(); i++) if (QUEUE.get(i).id.equals(trackId)) return i;
        return -1;
    }

    public static void toggle() {
        if (controller == null) return;
        if (controller.isPlaying()) controller.pause();
        else {
            controller.play();
            Models.Track t = current();
            if (t != null) Library.addToHistory(t);
        }
        emit();
    }

    public static void play() {
        if (controller != null) controller.play();
        emit();
    }

    public static void pause() {
        if (controller != null) controller.pause();
        emit();
    }

    public static void next(boolean auto) {
        if (QUEUE.isEmpty()) return;
        if ("one".equals(repeat) && auto) {
            seekTo(0);
            play();
            return;
        }
        if (index + 1 < QUEUE.size()) {
            index++;
        } else if ("all".equals(repeat) || !auto) {
            index = 0;
        } else {
            pause();
            seekTo(0);
            return;
        }
        applyQueue(index, true);
        save();
    }

    public static void prev() {
        if (QUEUE.isEmpty()) return;
        long pos = position();
        if (pos > 4000) {
            seekTo(0);
            return;
        }
        index = index > 0 ? index - 1 : QUEUE.size() - 1;
        applyQueue(index, true);
        save();
    }

    public static void jumpTo(int i) {
        if (i < 0 || i >= QUEUE.size()) return;
        index = i;
        applyQueue(index, true);
        save();
    }

    public static void seekTo(long ms) {
        if (controller != null) controller.seekTo(Math.max(0, ms));
        emit();
    }

    public static void seekBy(long deltaMs) {
        seekTo(position() + deltaMs);
    }

    public static void addToQueue(Models.Track track) {
        if (indexOf(track.id) >= 0) return;
        QUEUE.add(track);
        applyQueue(index, false);
        save();
    }

    public static void playNext(Models.Track track) {
        int existing = indexOf(track.id);
        if (existing >= 0) QUEUE.remove(existing);
        QUEUE.add(Math.min(index + 1, QUEUE.size()), track);
        applyQueue(index, false);
        save();
    }

    public static void removeFromQueue(int i) {
        if (i < 0 || i >= QUEUE.size()) return;
        boolean isCurrent = i == index;
        QUEUE.remove(i);
        if (QUEUE.isEmpty()) {
            if (controller != null) controller.clearMediaItems();
            index = 0;
            emit();
            save();
            return;
        }
        if (i < index) index--;
        else if (isCurrent) index = Math.min(index, QUEUE.size() - 1);
        applyQueue(index, isCurrent);
        save();
    }

    public static void moveInQueue(int from, int to) {
        if (from < 0 || from >= QUEUE.size() || to < 0 || to >= QUEUE.size()) return;
        Models.Track item = QUEUE.remove(from);
        QUEUE.add(to, item);
        if (index == from) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;
        applyQueue(index, false);
        save();
    }

    public static void clearQueue() {
        Models.Track current = current();
        QUEUE.clear();
        if (current != null) QUEUE.add(current);
        index = 0;
        applyQueue(0, false);
        save();
    }

    public static void toggleShuffle() {
        if (!shuffle) {
            if (QUEUE.size() < 2) {
                shuffle = true;
                save();
                emit();
                return;
            }
            Models.Track current = current();
            List<Models.Track> rest = new ArrayList<>(QUEUE);
            if (current != null) rest.remove(current);
            Collections.shuffle(rest, RANDOM);
            ORIGINAL = new ArrayList<>(QUEUE);
            QUEUE.clear();
            if (current != null) QUEUE.add(current);
            QUEUE.addAll(rest);
            index = 0;
            shuffle = true;
        } else {
            Models.Track current = current();
            List<Models.Track> restored = ORIGINAL != null ? new ArrayList<>(ORIGINAL) : new ArrayList<>(QUEUE);
            int idx = 0;
            if (current != null) {
                for (int i = 0; i < restored.size(); i++) {
                    if (restored.get(i).id.equals(current.id)) {
                        idx = i;
                        break;
                    }
                }
            }
            QUEUE.clear();
            QUEUE.addAll(restored);
            index = idx;
            shuffle = false;
            ORIGINAL = null;
        }
        applyQueue(index, false);
        save();
        emit();
    }

    public static void cycleRepeat() {
        repeat = "off".equals(repeat) ? "all" : "all".equals(repeat) ? "one" : "off";
        if (controller != null) {
            controller.setRepeatMode("one".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ONE : "all".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ALL : androidx.media3.common.Player.REPEAT_MODE_OFF);
        }
        save();
        emit();
    }

    public static void setVideoMode(boolean value) {
        if (videoMode == value) return;
        videoMode = value;
        Models.Track t = current();
        if (t != null) applyQueue(index, isPlaying());
        emit();
    }

    public static void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        if (value > 0) muted = false;
        if (controller != null) controller.setVolume(muted ? 0f : volume);
        save();
        emit();
    }

    public static void toggleMute() {
        muted = !muted;
        if (controller != null) controller.setVolume(muted ? 0f : volume);
        save();
        emit();
    }

    /* ------------------------------------------------------------------ */
    /* Внутреннее                                                          */
    /* ------------------------------------------------------------------ */

    private static void syncIndexFromController() {
        if (controller == null) return;
        String id = controller.getCurrentMediaItem() == null ? null : controller.getCurrentMediaItem().mediaId;
        if (id == null) return;
        int found = indexOf(id);
        if (found >= 0) index = found;
    }

    private static void applyQueue(int startIndex, boolean play) {
        if (controller == null) return;
        List<MediaItem> items = new ArrayList<>();
        for (Models.Track t : QUEUE) items.add(toMediaItem(t));
        controller.setMediaItems(items, Math.max(0, Math.min(startIndex, Math.max(0, items.size() - 1))), 0);
        controller.setRepeatMode("one".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ONE : "all".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ALL : androidx.media3.common.Player.REPEAT_MODE_OFF);
        controller.setVolume(muted ? 0f : volume);
        controller.prepare();
        if (play) {
            controller.play();
            Models.Track t = current();
            if (t != null) Library.addToHistory(t);
        }
        emit();
    }

    private static MediaItem toMediaItem(Models.Track t) {
        String url = (videoMode && t.videoUrl != null && !t.videoUrl.isEmpty()) ? t.videoUrl : t.audioUrl;
        if (url == null || url.isEmpty()) url = t.videoUrl;
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artistNames())
                .setAlbumTitle(t.anime.name + " · " + t.themeSlug);
        String art = t.cover != null ? t.cover : t.coverSmall;
        if (art != null) meta.setArtworkUri(Uri.parse(art));
        return new MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(url)
                .setMediaMetadata(meta.build())
                .build();
    }

    /* ------------------------------------------------------------------ */
    /* Сохранение состояния                                                */
    /* ------------------------------------------------------------------ */

    private static void save() {
        try {
            JSONObject o = new JSONObject();
            JSONArray arr = new JSONArray();
            int limit = Math.min(QUEUE.size(), 300);
            for (int i = 0; i < limit; i++) arr.put(QUEUE.get(i).toJson());
            o.put("queue", arr);
            o.put("index", index);
            o.put("shuffle", shuffle);
            o.put("repeat", repeat);
            o.put("volume", volume);
            o.put("muted", muted);
            o.put("ts", System.currentTimeMillis());
            Prefs.put(PERSIST_KEY, o.toString());
        } catch (Exception ignored) {
        }
    }

    private static void restore(Context context) {
        try {
            String raw = Prefs.getString(PERSIST_KEY, "");
            if (!raw.isEmpty()) {
                JSONObject o = new JSONObject(raw);
                JSONArray arr = o.optJSONArray("queue");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject t = arr.optJSONObject(i);
                        if (t != null) QUEUE.add(Models.Track.fromJson(t));
                    }
                }
                index = o.optInt("index", 0);
                shuffle = o.optBoolean("shuffle");
                repeat = o.optString("repeat", "off");
                volume = (float) o.optDouble("volume", 1f);
                muted = o.optBoolean("muted");
            }
        } catch (Exception ignored) {
        }
        SliderVolume.init(context);
    }

    /** Плеер подхватывает настройки звука после рестарта. */
    private static final class SliderVolume {
        static void init(Context context) {
            // nothing: volume применяется при подключении контроллера
        }
    }
}
