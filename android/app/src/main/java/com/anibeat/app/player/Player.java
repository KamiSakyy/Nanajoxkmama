package com.anibeat.app.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.anibeat.app.core.Prefs;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Плеер (store/player.tsx): очередь, перемешивание, повтор, режим видео,
 * фоновая служба и сохранение состояния. Чистая Java — android.media.MediaPlayer.
 */
public final class Player {

    public interface Listener {
        void onPlayerChanged();
    }

    private static final String PERSIST_KEY = "player";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Engine engine;
    private static Context appContext;
    private static boolean autoConnect = true;
    private static boolean startingService;
    private static boolean restored;
    private static boolean localOnly;
    private static Surface videoSurface;

    private static final List<Models.Track> QUEUE = new ArrayList<>();
    private static List<Models.Track> ORIGINAL;
    private static int index;
    private static boolean shuffle;
    private static String repeat = "off";
    private static boolean videoMode;
    private static boolean muted;
    private static float volume = 1f;
    private static int pendingIndex = -1;
    private static boolean pendingPlay;
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private Player() {
    }

    /* ------------------------------------------------------------------ */
    /* Инициализация                                                       */
    /* ------------------------------------------------------------------ */

    /** Отключает запуск службы воспроизведения (нужно только автотестам). */
    public static void setAutoConnect(boolean value) {
        autoConnect = value;
    }

    public static Context context() {
        return appContext;
    }

    public static void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
        if (!restored && appContext != null) {
            restored = true;
            restore();
        }
        if (engine != null || startingService || appContext == null || !autoConnect) return;
        startingService = true;
        if (!startService()) {
            startingService = false;
            ensureLocalEngine();
            return;
        }
        // Служба не поднялась (например, система не разрешила фон) — играем в приложении.
        MAIN.postDelayed(() -> {
            if (engine == null && PlayerHolder.engine() == null) {
                startingService = false;
                ensureLocalEngine();
            }
        }, 1200);
    }

    private static boolean startService() {
        try {
            Intent intent = new Intent(appContext, PlaybackService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appContext.startForegroundService(intent);
            else appContext.startService(intent);
            return true;
        } catch (Throwable t) {
            Ui.report(t);
            return false;
        }
    }

    private static void ensureLocalEngine() {
        if (engine != null || localOnly || !autoConnect || appContext == null) return;
        localOnly = true;
        try {
            Engine created = Engine.create(appContext);
            attachEngine(created);
            if (!QUEUE.isEmpty()) openCurrent(pendingPlay);
        } catch (Throwable t) {
            Ui.report(t);
            localOnly = false;
        }
    }

    /** Подключает проигрыватель (вызывает служба либо само приложение). */
    static void attachEngine(final Engine created) {
        Engine previous = engine;
        engine = created;
        if (previous != null && previous != created) {
            try {
                previous.release();
            } catch (Throwable ignored) {
            }
        }
        if (videoSurface != null) created.setSurface(videoSurface);
        created.setVolume(muted ? 0f : volume);
        created.setListener(new Engine.Listener() {
            @Override
            public void onReady() {
                emit();
                PlaybackService.notifyState(appContext, engine != null && engine.isPlaying());
            }

            @Override
            public void onCompletion() {
                next(true);
            }

            @Override
            public void onError() {
                // Битый или недоступный трек — переходим к следующему, музыка не прерывается.
                if (QUEUE.size() > 1) next(true);
                else emit();
            }

            @Override
            public void onBuffering(boolean buffering) {
                emit();
            }
        });
        emit();
    }

    /** Служба подключилась — продолжаем с текущей очередью. */
    public static void onEngineReady(Context context) {
        if (engine == null) return;
        if (!QUEUE.isEmpty()) openCurrent(pendingPlay);
        if (pendingIndex >= 0) {
            int pIndex = pendingIndex;
            pendingIndex = -1;
            if (pIndex < QUEUE.size()) {
                index = pIndex;
                openCurrent(pendingPlay);
            }
            pendingPlay = false;
        }
        emit();
    }

    public static boolean isReady() {
        return engine != null;
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void emit() {
        for (Listener l : new ArrayList<>(LISTENERS)) {
            try {
                l.onPlayerChanged();
            } catch (Throwable t) {
                Ui.report(t);
            }
        }
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

    @Nullable
    public static Models.Track current() {
        if (QUEUE.isEmpty() || index < 0 || index >= QUEUE.size()) return null;
        return QUEUE.get(index);
    }

    public static boolean isPlaying() {
        return engine != null && engine.isPlaying();
    }

    public static boolean isBuffering() {
        return engine != null && engine.isBuffering();
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
        return engine == null ? 0 : engine.position();
    }

    public static long duration() {
        return engine == null ? 0 : engine.duration();
    }

    /** Видео-поверхность экрана плеера. */
    public static void setVideoSurface(Surface surface) {
        videoSurface = surface;
        if (engine != null) engine.setSurface(surface);
    }

    public static void clearVideoSurface() {
        videoSurface = null;
        if (engine != null) engine.setSurface(null);
    }

    /* ------------------------------------------------------------------ */
    /* Управление                                                          */
    /* ------------------------------------------------------------------ */

    /** Есть ли у трека источник звука. */
    public static boolean hasSource(Models.Track t) {
        if (t == null) return false;
        return (t.audioUrl != null && !t.audioUrl.isEmpty()) || (t.videoUrl != null && !t.videoUrl.isEmpty());
    }

    /** Адрес источника: видео в видеорежиме, иначе звук; скачанное играет из файла. */
    private static String sourceOf(Models.Track t) {
        String kind = videoMode ? Downloads.KIND_VIDEO : Downloads.KIND_AUDIO;
        if (Downloads.hasOffline(t.id, kind)) {
            File file = Downloads.offlineFile(t.id, kind);
            if (file != null && file.exists()) return file.toURI().toString();
        }
        String url = videoMode && t.videoUrl != null && !t.videoUrl.isEmpty() ? t.videoUrl : t.audioUrl;
        if (url == null || url.isEmpty()) url = t.videoUrl;
        return url;
    }

    public static void playTracks(List<Models.Track> tracks, int start, boolean withShuffle) {
        if (tracks == null || tracks.isEmpty()) return;
        videoMode = false;
        List<Models.Track> playable = new ArrayList<>();
        for (Models.Track t : tracks) if (hasSource(t)) playable.add(t);
        if (playable.isEmpty()) return;
        List<Models.Track> list = playable;
        if (list != tracks) {
            int newStart = 0;
            if (start >= 0 && start < tracks.size()) {
                Models.Track target = tracks.get(start);
                int found = list.indexOf(target);
                newStart = found >= 0 ? found : 0;
            }
            start = newStart;
            withShuffle = withShuffle && list.size() > 1;
        }
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
        openCurrent(true);
        save();
    }

    public static void playTrack(Models.Track track, @Nullable List<Models.Track> context) {
        if (track == null || !hasSource(track)) return;
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
            openCurrent(true);
        } else if (QUEUE.isEmpty()) {
            List<Models.Track> one = new ArrayList<>();
            one.add(track);
            playTracks(one, 0, false);
        } else {
            QUEUE.add(index + 1, track);
            index = index + 1;
            openCurrent(true);
        }
        save();
    }

    private static int indexOf(String trackId) {
        for (int i = 0; i < QUEUE.size(); i++) if (QUEUE.get(i).id.equals(trackId)) return i;
        return -1;
    }

    public static void toggle() {
        if (engine == null) {
            if (QUEUE.isEmpty()) return;
            pendingIndex = index;
            pendingPlay = true;
            init(appContext);
            return;
        }
        if (engine.isPlaying()) engine.pause();
        else {
            engine.play();
            Models.Track t = current();
            if (t != null) Library.addToHistory(t);
        }
        PlaybackService.notifyState(appContext, engine.isPlaying());
        emit();
    }

    public static void play() {
        if (engine == null) {
            if (QUEUE.isEmpty()) return;
            pendingIndex = index;
            pendingPlay = true;
            init(appContext);
            return;
        }
        engine.play();
        Models.Track t = current();
        if (t != null) Library.addToHistory(t);
        PlaybackService.notifyState(appContext, engine.isPlaying());
        emit();
    }

    public static void pause() {
        if (engine != null) engine.pause();
        PlaybackService.notifyState(appContext, false);
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
        openCurrent(true);
        save();
    }

    public static void prev() {
        if (QUEUE.isEmpty()) return;
        if (position() > 4000) {
            seekTo(0);
            return;
        }
        index = index > 0 ? index - 1 : QUEUE.size() - 1;
        openCurrent(true);
        save();
    }

    public static void jumpTo(int i) {
        if (i < 0 || i >= QUEUE.size()) return;
        index = i;
        openCurrent(true);
        save();
    }

    public static void seekTo(long ms) {
        if (engine != null) engine.seekTo((int) Math.max(0, ms));
        emit();
    }

    public static void seekBy(long deltaMs) {
        seekTo(position() + deltaMs);
    }

    public static void addToQueue(Models.Track track) {
        if (indexOf(track.id) >= 0) return;
        QUEUE.add(track);
        save();
        emit();
    }

    public static void playNext(Models.Track track) {
        int existing = indexOf(track.id);
        if (existing >= 0) {
            if (existing == index) return;
            QUEUE.remove(existing);
            if (existing < index) index--;
        }
        int at = Math.min(index + 1, QUEUE.size());
        QUEUE.add(at, track);
        save();
        emit();
    }

    public static void removeFromQueue(int i) {
        if (i < 0 || i >= QUEUE.size()) return;
        boolean wasCurrent = i == index;
        QUEUE.remove(i);
        if (QUEUE.isEmpty()) {
            if (engine != null) engine.stop();
            index = 0;
            emit();
            save();
            return;
        }
        if (i < index) index--;
        else if (wasCurrent) index = Math.min(index, QUEUE.size() - 1);
        if (wasCurrent || i < index) openCurrent(wasCurrent);
        save();
    }

    public static void moveInQueue(int from, int to) {
        if (from < 0 || from >= QUEUE.size() || to < 0 || to >= QUEUE.size()) return;
        Models.Track item = QUEUE.remove(from);
        QUEUE.add(to, item);
        if (index == from) index = to;
        else if (from < index && to >= index) index--;
        else if (from > index && to <= index) index++;
        save();
        emit();
    }

    public static void clearQueue() {
        Models.Track current = current();
        QUEUE.clear();
        if (current != null) QUEUE.add(current);
        index = 0;
        save();
        emit();
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
        openCurrent(isPlaying());
        save();
        emit();
    }

    public static void cycleRepeat() {
        repeat = "off".equals(repeat) ? "all" : "all".equals(repeat) ? "one" : "off";
        save();
        emit();
    }

    public static void setVideoMode(boolean value) {
        if (videoMode == value) return;
        videoMode = value;
        if (current() != null) openCurrent(isPlaying());
        emit();
    }

    public static void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        if (value > 0) muted = false;
        if (engine != null) engine.setVolume(muted ? 0f : volume);
        save();
        emit();
    }

    public static void toggleMute() {
        muted = !muted;
        if (engine != null) engine.setVolume(muted ? 0f : volume);
        save();
        emit();
    }

    /* ------------------------------------------------------------------ */
    /* Внутреннее                                                          */
    /* ------------------------------------------------------------------ */

    /** Открывает текущий трек очереди. */
    private static void openCurrent(boolean play) {
        Models.Track t = current();
        if (t == null) return;
        if (engine == null) {
            pendingIndex = index;
            pendingPlay = play;
            init(appContext);
            return;
        }
        String url = sourceOf(t);
        if (url == null || url.isEmpty()) {
            if (QUEUE.size() > 1) next(true);
            return;
        }
        engine.open(url, play);
        if (play) Library.addToHistory(t);
        PlaybackService.notifyState(appContext, play);
        emit();
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
        } catch (Throwable ignored) {
        }
    }

    private static void restore() {
        try {
            QUEUE.clear();
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
                index = Math.max(0, o.optInt("index", 0));
                shuffle = o.optBoolean("shuffle");
                repeat = o.optString("repeat", "off");
                volume = (float) o.optDouble("volume", 1f);
                muted = o.optBoolean("muted");
            }
        } catch (Throwable ignored) {
        }
    }

    /** Ссылка на файл скачанного трека — нужна другим частям приложения. */
    static Uri offlineUri(String trackId, String kind) {
        File file = Downloads.offlineFile(trackId, kind);
        return file == null ? null : Uri.fromFile(file);
    }
}
