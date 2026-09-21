package com.anibeat.app.player;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;

import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
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

    private static ExoPlayer engine;
    private static boolean startingService;
    private static boolean restored;
    private static boolean localOnly;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Models.Track> QUEUE = new ArrayList<>();
    private static List<Models.Track> ORIGINAL;
    private static int index;
    private static boolean shuffle;
    private static String repeat = "off";
    private static boolean videoMode;
    private static boolean muted;
    private static float volume = 1f;
    private static boolean ready;
    private static Context appContext;
    /** Отложенный запуск: пользователь нажал play раньше, чем подключился сервис. */
    private static int pendingIndex = -1;
    private static boolean pendingPlay;
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private Player() {
    }

    /* ------------------------------------------------------------------ */
    /* Инициализация                                                       */
    /* ------------------------------------------------------------------ */

    private static boolean autoConnect = true;

    /** Отключает подключение к сервису воспроизведения (нужно только автотестам). */
    public static void setAutoConnect(boolean value) {
        autoConnect = value;
    }

    public static void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
        if (!restored) {
            restored = true;
            restore(context);
        }
        if (engine != null || startingService) return;
        if (!autoConnect) return;
        // Пробуем фон (служба). Если не получится — играем прямо в приложении.
        startingService = true;
        if (!startService()) {
            startingService = false;
            ensureLocalEngine();
            return;
        }
        MAIN.postDelayed(() -> {
            if (engine == null) {
                startingService = false;
                ensureLocalEngine();
            }
        }, 1500);
    }

    private static boolean startService() {
        if (appContext == null) return false;
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
        if (engine != null || localOnly || !autoConnect) return;
        localOnly = true;
        try {
            engine = createEngine(appContext);
            PlayerHolder.attach(engine, false);
            attachEngineListener(engine);
            onEngineReady(appContext);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    /** Создаёт проигрыватель (чистая Java). Используется и службой, и приложением. */
    public static ExoPlayer createEngine(Context context) {
        ExoPlayer player = new ExoPlayer.Builder(context)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.setRepeatMode("one".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ONE
                : "all".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ALL : androidx.media3.common.Player.REPEAT_MODE_OFF);
        player.setVolume(muted ? 0f : volume);
        return player;
    }

    /** Общая часть для службы и приложения: следим за событиями плеера. */
    static void attachEngineListener(ExoPlayer player) {
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                emit();
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                syncIndex();
                Models.Track track = current();
                if (track != null && engine != null && engine.getPlayWhenReady()
                        && reason != androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    Library.addToHistory(track);
                }
                emit();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                emit();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                // Недоступный трек пропускаем, музыка не прерывается.
                try {
                    int next = engine == null ? -1 : engine.getCurrentMediaItemIndex() + 1;
                    if (engine != null && next > 0 && next < engine.getMediaItemCount()) {
                        engine.seekTo(next, 0);
                        engine.prepare();
                        engine.play();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** Служба подключилась (или приложение подняло свой плеер) — продолжаем с очередью. */
    public static void onEngineReady(Context context) {
        MAIN.post(() -> Ui.safe(() -> {
            if (engine == null) engine = PlayerHolder.engine();
            if (engine == null) return;
            engine.setRepeatMode("one".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ONE
                    : "all".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ALL : androidx.media3.common.Player.REPEAT_MODE_OFF);
            engine.setVolume(muted ? 0f : volume);
            if (!QUEUE.isEmpty()) applyQueue(index, pendingPlay);
            if (pendingIndex >= 0) {
                int pIndex = pendingIndex;
                pendingIndex = -1;
                applyQueue(pIndex, pendingPlay);
                pendingPlay = false;
            }
            emit();
        }));
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

    /** Выполняет действие с проигрывателем; при сбое поднимает его заново. */
    private static boolean withEngine(java.util.function.Consumer<ExoPlayer> action) {
        ExoPlayer e = engine;
        if (e == null) return false;
        try {
            action.accept(e);
            return true;
        } catch (Throwable t) {
            Ui.report(t);
            recover();
            return false;
        }
    }

    /** Проигрыватель отказал: забываем его и создаём заново, чтобы тапы продолжали работать. */
    private static void recover() {
        try {
            ExoPlayer broken = engine;
            engine = null;
            if (broken != null) {
                try {
                    broken.release();
                } catch (Throwable ignored) {
                }
            }
            PlayerHolder.detach(broken);
        } catch (Throwable t) {
            Ui.report(t);
        }
        localOnly = false;
        startingService = false;
        if (appContext != null) init(appContext);
    }

    private static void emit() {
        for (Listener l : new ArrayList<>(LISTENERS)) {
            // Сбой одного экрана не должен ронять всё приложение.
            try {
                l.onPlayerChanged();
            } catch (Throwable t) {
                com.anibeat.app.core.Ui.report(t);
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

    public static Models.Track current() {
        if (QUEUE.isEmpty() || index < 0 || index >= QUEUE.size()) return null;
        return QUEUE.get(index);
    }

    public static boolean isPlaying() {
        return engine != null && engine.isPlaying();
    }

    public static boolean isBuffering() {
        return engine != null && engine.getPlaybackState() == androidx.media3.common.Player.STATE_BUFFERING;
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
        return engine == null ? 0 : engine.getCurrentPosition();
    }

    public static long duration() {
        long d = engine == null ? 0 : engine.getDuration();
        return d < 0 ? 0 : d;
    }

    /** Проигрыватель для экрана видео. */
    public static androidx.media3.common.Player controller() {
        return engine;
    }

    /* ------------------------------------------------------------------ */
    /* Управление                                                          */
    /* ------------------------------------------------------------------ */

    /** Есть ли у трека источник звука для ExoPlayer. */
    public static boolean hasSource(Models.Track t) {
        if (t == null) return false;
        return (t.audioUrl != null && !t.audioUrl.isEmpty()) || (t.videoUrl != null && !t.videoUrl.isEmpty());
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
        applyQueue(index, true);
        save();
    }

    public static void playTrack(Models.Track track, List<Models.Track> context) {
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
        if (engine == null) {
            if (QUEUE.isEmpty()) return;
            pendingIndex = index;
            pendingPlay = true;
            if (appContext != null) init(appContext);
            return;
        }
        boolean wasPlaying = engine.isPlaying();
        if (!withEngine(e -> { if (wasPlaying) e.pause(); else e.play(); })) return;
        if (!wasPlaying) {
            Models.Track t = current();
            if (t != null) Library.addToHistory(t);
        }
        PlaybackService.refresh(appContext);
        emit();
    }

    public static void play() {
        if (engine == null) {
            if (QUEUE.isEmpty()) return;
            pendingIndex = index;
            pendingPlay = true;
            if (appContext != null) init(appContext);
            return;
        }
        if (!withEngine(ExoPlayer::play)) return;
        Models.Track t = current();
        if (t != null) Library.addToHistory(t);
        PlaybackService.refresh(appContext);
        emit();
    }

    public static void pause() {
        if (engine != null) withEngine(ExoPlayer::pause);
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
        if (engine != null) withEngine(e -> e.seekTo(Math.max(0, ms)));
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
            if (engine != null) withEngine(e -> e.clearMediaItems());
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
        if (engine != null) {
            final String mode = repeat;
            withEngine(e -> e.setRepeatMode("one".equals(mode) ? androidx.media3.common.Player.REPEAT_MODE_ONE
                    : "all".equals(mode) ? androidx.media3.common.Player.REPEAT_MODE_ALL : androidx.media3.common.Player.REPEAT_MODE_OFF));
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
        if (engine != null) withEngine(e -> e.setVolume(muted ? 0f : volume));
        save();
        emit();
    }

    public static void toggleMute() {
        muted = !muted;
        if (engine != null) withEngine(e -> e.setVolume(muted ? 0f : volume));
        save();
        emit();
    }

    /* ------------------------------------------------------------------ */
    /* Внутреннее                                                          */
    /* ------------------------------------------------------------------ */

    private static void syncIndex() {
        if (engine == null) return;
        String id = engine.getCurrentMediaItem() == null ? null : engine.getCurrentMediaItem().mediaId;
        if (id == null) return;
        int found = indexOf(id);
        if (found >= 0) index = found;
    }

    private static void applyQueue(int startIndex, boolean play) {
        if (engine == null) {
            // Плеер ещё поднимается — запомним и выполним сразу, как он появится.
            pendingIndex = startIndex;
            pendingPlay = play;
            if (appContext != null) init(appContext);
            return;
        }
        List<MediaItem> items = new ArrayList<>();
        for (Models.Track t : QUEUE) items.add(toMediaItem(t));
        final int start = Math.max(0, Math.min(startIndex, Math.max(0, items.size() - 1)));
        boolean ok = withEngine(e -> {
            e.setMediaItems(items, start, 0);
            e.setRepeatMode("one".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ONE
                    : "all".equals(repeat) ? androidx.media3.common.Player.REPEAT_MODE_ALL : androidx.media3.common.Player.REPEAT_MODE_OFF);
            e.setVolume(muted ? 0f : volume);
            e.prepare();
            if (play) e.play();
        });
        if (!ok) {
            pendingIndex = startIndex;
            pendingPlay = play;
            emit();
            return;
        }
        if (play) {
            Models.Track t = current();
            if (t != null) Library.addToHistory(t);
            PlaybackService.refresh(appContext);
        }
        emit();
    }

    private static MediaItem toMediaItem(Models.Track t) {
        String url = (videoMode && t.videoUrl != null && !t.videoUrl.isEmpty()) ? t.videoUrl : t.audioUrl;
        if (url == null || url.isEmpty()) url = t.videoUrl;
        // Скачанный трек играет из файла — как getOfflineUrl() на сайте.
        String kind = videoMode ? Downloads.KIND_VIDEO : Downloads.KIND_AUDIO;
        if (Downloads.hasOffline(t.id, kind)) {
            java.io.File file = Downloads.offlineFile(t.id, kind);
            if (file != null && file.exists()) url = file.toURI().toString();
        }
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
