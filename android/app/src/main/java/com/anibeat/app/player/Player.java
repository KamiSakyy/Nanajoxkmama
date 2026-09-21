package com.anibeat.app.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.exoplayer.ExoPlayer;

import com.anibeat.app.core.Prefs;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Воспроизведение на Media3 (ExoPlayer). Движок живёт в службе PlayerService —
 * звук продолжается, когда приложение свёрнуто, а интерфейс лишь управляет им.
 */
public final class Player {

    public interface Listener {
        void onPlayerChanged();
    }

    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final String TAG = "AniBeatPlayer";
    private static final String PERSIST_KEY = "player.state.v2";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context context;
    private static ExoPlayer engine;
    private static PlaybackService service;
    private static boolean autoConnect = true;

    private static final List<Models.Track> QUEUE = new ArrayList<>();
    private static int index = -1;
    private static boolean shuffle;
    private static int repeat = REPEAT_NONE;
    private static float speed = 1f;
    private static boolean videoMode;
    private static boolean prepared;
    private static long sleepUntil;
    private static Runnable sleepTask;

    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static Pending pending;

    private Player() {
    }

    private static final class Pending {
        final List<Models.Track> queue = new ArrayList<>();
        int index;
        long position;
        boolean play;
    }

    /* ------------------------------ запуск ------------------------------ */

    public static void init(Context ctx) {
        context = ctx.getApplicationContext();
        restore();
    }

    public static Context context() {
        return context;
    }

    public static void setAutoConnect(boolean value) {
        autoConnect = value;
    }

    public static boolean autoConnect() {
        return autoConnect;
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) LISTENERS.add(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static void emit() {
        for (Listener listener : new ArrayList<>(LISTENERS)) {
            try {
                listener.onPlayerChanged();
            } catch (Throwable t) {
                Ui.report(t);
            }
        }
    }

    static void notifyChanged() {
        MAIN.post(Player::emit);
    }

    /* --------------------------- управление ----------------------------- */

    public static void play(List<Models.Track> list, int startIndex) {
        if (list == null || list.isEmpty()) return;
        QUEUE.clear();
        QUEUE.addAll(list);
        index = Math.max(0, Math.min(startIndex, QUEUE.size() - 1));
        request(index, 0L, true);
    }

    public static void playTrack(Models.Track track) {
        if (track == null) return;
        List<Models.Track> one = new ArrayList<>();
        one.add(track);
        play(one, 0);
    }

    /** Воспроизвести трек из списка, продолжая очередь этого списка. */
    public static void playFrom(Models.Track track, List<Models.Track> list, int startIndex) {
        if (list == null || list.isEmpty()) {
            playTrack(track);
            return;
        }
        play(list, startIndex);
    }

    public static void enqueue(Models.Track track) {
        if (track == null) return;
        QUEUE.add(track);
        if (engine != null) {
            engine.addMediaItem(item(track));
        }
        if (index < 0) {
            index = 0;
            request(0, 0L, false);
        } else {
            save();
            notifyChanged();
        }
    }

    public static void playNext(Models.Track track) {
        if (track == null) return;
        int at = Math.max(0, index) + (QUEUE.isEmpty() ? 0 : 1);
        if (QUEUE.isEmpty()) {
            QUEUE.add(track);
            index = 0;
            request(0, 0L, true);
            return;
        }
        at = Math.min(at, QUEUE.size());
        QUEUE.add(at, track);
        if (engine != null) engine.addMediaItem(at, item(track));
        save();
        notifyChanged();
    }

    public static void removeAt(int position) {
        if (position < 0 || position >= QUEUE.size()) return;
        QUEUE.remove(position);
        if (engine != null) engine.removeMediaItem(position);
        if (QUEUE.isEmpty()) {
            stop();
            return;
        }
        if (position < index) index--;
        else if (position == index) index = Math.min(index, QUEUE.size() - 1);
        save();
        notifyChanged();
    }

    public static void moveAt(int from, int to) {
        if (from < 0 || to < 0 || from >= QUEUE.size() || to >= QUEUE.size() || from == to) return;
        Models.Track track = QUEUE.remove(from);
        QUEUE.add(to, track);
        if (engine != null) {
            engine.moveMediaItem(from, to);
        }
        if (index == from) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;
        save();
        notifyChanged();
    }

    public static void toggle() {
        if (engine == null) {
            if (!QUEUE.isEmpty() && index >= 0) request(index, 0L, true);
            return;
        }
        if (engine.isPlaying()) engine.pause();
        else {
            if (engine.getPlaybackState() == androidx.media3.common.Player.STATE_IDLE) engine.prepare();
            engine.play();
        }
        notifyChanged();
    }

    public static void play() {
        if (engine != null) {
            if (engine.getPlaybackState() == androidx.media3.common.Player.STATE_IDLE) engine.prepare();
            engine.play();
        } else if (!QUEUE.isEmpty() && index >= 0) {
            request(index, 0L, true);
        }
        notifyChanged();
    }

    public static void pause() {
        Ui.safe(() -> {
            if (engine != null) engine.pause();
        });
        notifyChanged();
    }

    public static void next(boolean automatic) {
        if (engine != null) {
            if (repeat == REPEAT_ONE && automatic) {
                engine.seekTo(0);
                engine.play();
            } else {
                engine.seekToNextMediaItem();
                engine.play();
            }
        }
        notifyChanged();
    }

    public static void prev() {
        if (engine != null) {
            engine.seekToPreviousMediaItem();
            engine.play();
        }
        notifyChanged();
    }

    public static void jumpTo(int position) {
        if (position < 0 || position >= QUEUE.size()) return;
        index = position;
        if (engine != null) {
            engine.seekTo(position, 0L);
            engine.play();
        } else {
            request(position, 0L, true);
        }
        notifyChanged();
    }

    public static void seekTo(long ms) {
        Ui.safe(() -> {
            if (engine != null) engine.seekTo(Math.max(0L, ms));
        });
        notifyChanged();
    }

    public static void seekBy(long deltaMs) {
        Ui.safe(() -> {
            if (engine != null) engine.seekTo(Math.max(0L, engine.getCurrentPosition() + deltaMs));
        });
        notifyChanged();
    }

    public static void stop() {
        Ui.safe(() -> {
            if (engine != null) {
                engine.stop();
                engine.clearMediaItems();
            }
        });
        if (service != null) {
            try {
                service.stopPlayback();
            } catch (Throwable t) {
                Ui.report(t);
            }
        }
        prepared = false;
        emit();
    }

    /* ----------------------------- состояние ---------------------------- */

    public static boolean hasTrack() {
        return current() != null;
    }

    public static Models.Track current() {
        if (index < 0 || index >= QUEUE.size()) return null;
        return QUEUE.get(index);
    }

    public static Models.Track trackAt(int position) {
        return position >= 0 && position < QUEUE.size() ? QUEUE.get(position) : null;
    }

    public static int currentIndex() {
        return index;
    }

    public static List<Models.Track> queue() {
        return new ArrayList<>(QUEUE);
    }

    public static List<Models.Track> shuffledQueue() {
        List<Models.Track> copy = new ArrayList<>(QUEUE);
        Collections.shuffle(copy);
        return copy;
    }

    public static boolean isPlaying() {
        return engine != null && engine.isPlaying();
    }

    public static boolean isBuffering() {
        return engine != null && engine.getPlaybackState() == androidx.media3.common.Player.STATE_BUFFERING;
    }

    public static boolean isReady() {
        return prepared;
    }

    public static long position() {
        return engine == null ? 0L : Math.max(0L, engine.getCurrentPosition());
    }

    public static long duration() {
        if (engine == null) return 0L;
        long duration = engine.getDuration();
        return duration > 0 ? duration : 0L;
    }

    public static boolean shuffle() {
        return shuffle;
    }

    public static void setShuffle(boolean value) {
        shuffle = value;
        if (engine != null) engine.setShuffleModeEnabled(value);
        save();
        notifyChanged();
    }

    public static int repeat() {
        return repeat;
    }

    public static void setRepeat(int mode) {
        repeat = mode;
        if (engine != null) {
            engine.setRepeatMode(mode == REPEAT_ONE ? androidx.media3.common.Player.REPEAT_MODE_ONE
                    : mode == REPEAT_ALL ? androidx.media3.common.Player.REPEAT_MODE_ALL
                    : androidx.media3.common.Player.REPEAT_MODE_OFF);
        }
        save();
        notifyChanged();
    }

    public static float speed() {
        return speed;
    }

    public static void setSpeed(float value) {
        speed = Math.max(0.5f, Math.min(2f, value));
        if (engine != null) {
            Ui.safe(() -> engine.setPlaybackParameters(new PlaybackParameters(speed)));
        }
        save();
        notifyChanged();
    }

    public static boolean videoMode() {
        return videoMode;
    }

    /** Включает или выключает видеодорожку для текущего трека. */
    public static void toggleVideoMode() {
        videoMode = !videoMode;
        if (index >= 0 && index < QUEUE.size() && engine != null) {
            long position = position();
            List<MediaItem> items = new ArrayList<>();
            for (Models.Track track : QUEUE) items.add(item(track));
            engine.setMediaItems(items, index, position);
            engine.prepare();
            engine.play();
        }
        notifyChanged();
    }

    public static void setSleepTimer(long minutes) {
        if (sleepTask != null) {
            MAIN.removeCallbacks(sleepTask);
            sleepTask = null;
        }
        if (minutes <= 0) {
            sleepUntil = 0L;
            notifyChanged();
            return;
        }
        sleepUntil = System.currentTimeMillis() + minutes * 60_000L;
        sleepTask = () -> {
            sleepUntil = 0L;
            pause();
            emit();
        };
        MAIN.postDelayed(sleepTask, minutes * 60_000L);
        notifyChanged();
    }

    public static long sleepRemaining() {
        if (sleepUntil == 0L) return 0L;
        return Math.max(0L, sleepUntil - System.currentTimeMillis());
    }

    /* --------------------------- связь со службой ------------------------ */

    private static void request(int startIndex, long positionMs, boolean play) {
        Pending task = new Pending();
        task.queue.addAll(QUEUE);
        task.index = Math.max(0, Math.min(startIndex, task.queue.size() - 1));
        task.position = positionMs;
        task.play = play;
        pending = task;
        if (engine != null) {
            applyPending();
        } else {
            startService();
        }
        notifyChanged();
    }

    private static void startService() {
        if (!autoConnect || context == null) return;
        try {
            Intent intent = new Intent(context, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PREPARE);
            context.startService(intent);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public static void setVideoSurface(android.view.Surface surface) {
        Ui.safe(() -> {
            if (engine != null) engine.setVideoSurface(surface);
        });
    }

    /** Подключить окно видео (media3 PlayerView) к текущему плееру. */
    public static void bindVideo(final androidx.media3.ui.PlayerView view) {
        Ui.safe(() -> {
            if (view != null && engine != null && view.getPlayer() != engine) view.setPlayer(engine);
        });
    }

    public static void unbindVideo(final androidx.media3.ui.PlayerView view) {
        Ui.safe(() -> {
            if (view != null) view.setPlayer(null);
        });
    }

    public static void clearVideoSurface() {
        Ui.safe(() -> {
            if (engine != null) engine.clearVideoSurface();
        });
    }

    static ExoPlayer engine() {
        return engine;
    }

    static void attach(PlaybackService owner, ExoPlayer player) {
        service = owner;
        engine = player;
        engine.setShuffleModeEnabled(shuffle);
        engine.setRepeatMode(repeat == REPEAT_ONE ? androidx.media3.common.Player.REPEAT_MODE_ONE
                : repeat == REPEAT_ALL ? androidx.media3.common.Player.REPEAT_MODE_ALL
                : androidx.media3.common.Player.REPEAT_MODE_OFF);
        engine.setPlaybackParameters(new PlaybackParameters(speed));
        applyPending();
        emit();
    }

    static void detach(ExoPlayer player) {
        if (engine == player) {
            engine = null;
            service = null;
            prepared = false;
            emit();
        }
    }

    static Models.Track trackById(String id) {
        for (Models.Track track : QUEUE) {
            if (track.id != null && track.id.equals(id)) return track;
        }
        return null;
    }

    static void syncIndexFromEngine(int position) {
        if (position >= 0 && position < QUEUE.size()) {
            boolean changed = position != index;
            index = position;
            if (changed) {
                save();
                Models.Track track = current();
                if (track != null) {
                    try {
                        Library.addToHistory(track);
                    } catch (Throwable t) {
                        Ui.report(t);
                    }
                }
            }
        }
    }

    static void markPrepared(boolean value) {
        prepared = value;
    }

    static boolean handleServiceCommand(String action) {
        if (action == null) return false;
        switch (action) {
            case PlaybackService.ACTION_PREPARE:
            case PlaybackService.ACTION_PLAY:
                if (pending != null) applyPending();
                return true;
            case PlaybackService.ACTION_TOGGLE:
                toggle();
                return true;
            case PlaybackService.ACTION_NEXT:
                next(false);
                return true;
            case PlaybackService.ACTION_PREV:
                prev();
                return true;
            case PlaybackService.ACTION_STOP:
                stop();
                return true;
            default:
                return false;
        }
    }

    private static void applyPending() {
        Pending task = pending;
        pending = null;
        if (task == null || engine == null || task.queue.isEmpty()) return;
        try {
            List<MediaItem> items = new ArrayList<>();
            for (Models.Track track : task.queue) items.add(item(track));
            engine.setMediaItems(items, task.index, task.position);
            engine.prepare();
            if (task.play) engine.play();
            index = task.index;
            Models.Track track = current();
            if (track != null) {
                try {
                    Library.addToHistory(track);
                } catch (Throwable t) {
                    Ui.report(t);
                }
            }
            save();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private static MediaItem item(Models.Track track) {
        String uri = track.audioUrl;
        if (videoMode && track.videoUrl != null && !track.videoUrl.isEmpty()) uri = track.videoUrl;
        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .setTitle(track.title == null || track.title.isEmpty() ? track.themeSlug : track.title)
                .setArtist(track.artistNames())
                .setAlbumTitle(track.anime == null ? "" : track.anime.name);
        String art = track.cover != null && !track.cover.isEmpty() ? track.cover : track.coverSmall;
        if (art != null && !art.isEmpty()) {
            try {
                meta.setArtworkUri(Uri.parse(art));
            } catch (Throwable t) {
                Ui.report(t);
            }
        }
        return new MediaItem.Builder()
                .setUri(uri == null ? "" : uri)
                .setMediaId(track.id == null ? "" : track.id)
                .setMediaMetadata(meta.build())
                .build();
    }

    /* ------------------------------ память ------------------------------ */

    private static void save() {
        try {
            JSONArray array = new JSONArray();
            for (Models.Track track : QUEUE) array.put(track.toJson());
            JSONObject state = new JSONObject();
            state.put("queue", array);
            state.put("index", index);
            state.put("shuffle", shuffle);
            state.put("repeat", repeat);
            state.put("speed", speed);
            Prefs.put(PERSIST_KEY, state.toString());
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private static void restore() {
        try {
            String raw = Prefs.getString(PERSIST_KEY, "");
            if (raw == null || raw.isEmpty()) return;
            JSONObject state = new JSONObject(raw);
            JSONArray array = state.optJSONArray("queue");
            if (array != null) {
                QUEUE.clear();
                for (int i = 0; i < array.length() && i < 300; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) QUEUE.add(Models.Track.fromJson(item));
                }
            }
            index = state.optInt("index", QUEUE.isEmpty() ? -1 : 0);
            shuffle = state.optBoolean("shuffle");
            repeat = state.optInt("repeat", REPEAT_NONE);
            speed = (float) state.optDouble("speed", 1d);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }
}
