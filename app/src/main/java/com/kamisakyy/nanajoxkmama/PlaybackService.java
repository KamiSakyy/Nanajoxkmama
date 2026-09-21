package com.kamisakyy.nanajoxkmama;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Foreground media service: queue, shuffle / repeat modes, play-next and a
 * Material notification with cover art and transport actions. Audio keeps
 * playing while the app is backgrounded.
 */
public final class PlaybackService extends Service {
    public static final String ACTION_PLAY = "com.kamisakyy.nanajoxkmama.PLAY";
    public static final String ACTION_REFRESH = "com.kamisakyy.nanajoxkmama.REFRESH";
    public static final String ACTION_TOGGLE = "com.kamisakyy.nanajoxkmama.TOGGLE";
    public static final String ACTION_NEXT = "com.kamisakyy.nanajoxkmama.NEXT";
    public static final String ACTION_PREVIOUS = "com.kamisakyy.nanajoxkmama.PREVIOUS";
    public static final String ACTION_SEEK = "com.kamisakyy.nanajoxkmama.SEEK";
    public static final String ACTION_SEEK_BY = "com.kamisakyy.nanajoxkmama.SEEK_BY";
    public static final String ACTION_STOP = "com.kamisakyy.nanajoxkmama.STOP";
    public static final String ACTION_SHUFFLE = "com.kamisakyy.nanajoxkmama.SHUFFLE";
    public static final String ACTION_REPEAT = "com.kamisakyy.nanajoxkmama.REPEAT";
    public static final String BROADCAST_STATE = "com.kamisakyy.nanajoxkmama.PLAYBACK_STATE";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_DELTA = "delta";
    public static final String EXTRA_KEEP_ID = "keepId";
    private static final String CHANNEL_ID = "anibeat_playback";
    private static final int NOTIFICATION_ID = 412;

    private MediaPlayer player;
    private ArrayList<Track> queue = new ArrayList<>();
    private int index;
    private Track current;
    private boolean prepared;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Handler main = new Handler(Looper.getMainLooper());

    /* ---------------- static entry points ---------------- */

    public static void play(Context context, List<Track> tracks, int startIndex) {
        Store.saveQueue(tracks, Math.max(0, Math.min(startIndex, Math.max(0, tracks.size() - 1))));
        Store.saveOriginalQueue(new ArrayList<>(tracks));
        start(context, new Intent(context, PlaybackService.class).setAction(ACTION_PLAY)
                .putExtra(EXTRA_INDEX, startIndex));
    }

    public static void command(Context context, String action) {
        start(context, new Intent(context, PlaybackService.class).setAction(action));
    }

    public static void seek(Context context, long positionMs) {
        start(context, new Intent(context, PlaybackService.class).setAction(ACTION_SEEK)
                .putExtra(EXTRA_POSITION, positionMs));
    }

    public static void seekBy(Context context, long deltaMs) {
        start(context, new Intent(context, PlaybackService.class).setAction(ACTION_SEEK_BY)
                .putExtra(EXTRA_DELTA, deltaMs));
    }

    /** Re-read the queue after UI-side edits (add / remove / move / clear). */
    public static void refresh(Context context, String keepId) {
        start(context, new Intent(context, PlaybackService.class).setAction(ACTION_REFRESH)
                .putExtra(EXTRA_KEEP_ID, keepId == null ? "" : keepId));
    }

    private static void start(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    /* ---------------- lifecycle ---------------- */

    @Override public void onCreate() {
        super.onCreate();
        Store.init(this);
        Downloader.init(this);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        String action = intent == null ? null : intent.getAction();
        if (action == null) action = ACTION_PLAY;
        try {
        switch (action) {
            case ACTION_PLAY: {
                queue = Store.getQueue();
                int requested = intent.getIntExtra(EXTRA_INDEX, Store.getQueueIndex());
                load(Math.max(0, Math.min(requested, Math.max(0, queue.size() - 1))));
                break;
            }
            case ACTION_REFRESH: {
                String keepId = intent.getStringExtra(EXTRA_KEEP_ID);
                queue = Store.getQueue();
                int at = Store.getQueueIndex();
                if (keepId != null && !keepId.isEmpty()) {
                    for (int i = 0; i < queue.size(); i++) {
                        if (queue.get(i).id.equals(keepId)) { at = i; break; }
                    }
                }
                if (current != null && queue.isEmpty()) {
                    stopPlayback();
                } else if (current != null && at < queue.size() && queue.get(at).id.equals(current.id)) {
                    index = at;
                    publishState();
                    updateNotification();
                } else {
                    load(Math.max(0, Math.min(at, Math.max(0, queue.size() - 1))));
                }
                break;
            }
            case ACTION_TOGGLE: toggle(); break;
            case ACTION_NEXT: next(false); break;
            case ACTION_PREVIOUS: previous(); break;
            case ACTION_SEEK: seekTo(intent.getLongExtra(EXTRA_POSITION, 0L)); break;
            case ACTION_SEEK_BY: {
                long pos = 0;
                if (player != null && prepared) {
                    try { pos = player.getCurrentPosition(); } catch (IllegalStateException ignored) { }
                }
                seekTo(pos + intent.getLongExtra(EXTRA_DELTA, 0L));
                break;
            }
            case ACTION_STOP: stopPlayback(); break;
            case ACTION_SHUFFLE: toggleShuffle(); break;
            case ACTION_REPEAT: cycleRepeat(); break;
        }
        } catch (Throwable t) {
            android.util.Log.e("AniBeat", "command failed: " + action, t);
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        ticker.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    /* ---------------- queue ops ---------------- */

    private void load(int requestedIndex) {
        queue = Store.getQueue();
        if (queue.isEmpty()) {
            stopPlayback();
            return;
        }
        index = Math.max(0, Math.min(requestedIndex, queue.size() - 1));
        Store.setQueueIndex(index);
        current = queue.get(index);
        prepared = false;
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
                Store.addToHistory(current);
                publishState();
                updateNotification();
                scheduleTicker();
            });
            player.setOnCompletionListener(mp -> next(true));
            player.setOnErrorListener((mp, what, extra) -> {
                sendError();
                next(true);
                return true;
            });
            String source = current.playableUrl();
            if (source == null || source.isEmpty()) throw new IllegalStateException("Нет аудиофайла");
            if (source.startsWith("content:")) player.setDataSource(this, Uri.parse(source));
            else if (source.startsWith("http")) {
                player.setDataSource(source);
            } else {
                player.setDataSource(source);
            }
            player.prepareAsync();
            Store.setPlayback(current, false, 0L, 0L);
            publishState();
            updateNotification();
        } catch (Exception e) {
            sendError();
            next(true);
        }
    }

    private void toggle() {
        if (player == null || !prepared) return;
        try {
            if (player.isPlaying()) player.pause();
            else player.start();
            publishState();
            updateNotification();
            scheduleTicker();
        } catch (IllegalStateException ignored) { }
    }

    private void next(boolean fromCompletion) {
        queue = Store.getQueue();
        if (queue.isEmpty()) return;
        String repeat = Store.getRepeat();
        if (fromCompletion && "one".equals(repeat)) {
            load(index);
            return;
        }
        if (index + 1 < queue.size()) {
            load(index + 1);
        } else if ("all".equals(repeat) || (!fromCompletion && queue.size() > 1)) {
            load(0);
        } else if (fromCompletion) {
            stopPlayback();
        }
    }

    private void previous() {
        if (player != null && prepared) {
            try {
                if (player.getCurrentPosition() > 4000) {
                    player.seekTo(0);
                    publishState();
                    return;
                }
            } catch (IllegalStateException ignored) { }
        }
        queue = Store.getQueue();
        if (index > 0) load(index - 1);
        else if ("all".equals(Store.getRepeat()) && !queue.isEmpty()) load(queue.size() - 1);
        else if (current != null) load(index);
    }

    private void seekTo(long position) {
        if (player == null || !prepared) return;
        try {
            player.seekTo((int) Math.max(0, Math.min(position, player.getDuration())));
            publishState();
        } catch (IllegalStateException ignored) { }
    }

    private void toggleShuffle() {
        ArrayList<Track> q = Store.getQueue();
        if (q.isEmpty()) {
            Store.setShuffle(!Store.isShuffle());
            publishState();
            return;
        }
        String currentId = current == null ? "" : current.id;
        if (!Store.isShuffle()) {
            Store.saveOriginalQueue(new ArrayList<>(q));
            ArrayList<Track> rest = new ArrayList<>();
            Track first = null;
            for (Track t : q) {
                if (first == null && t.id.equals(currentId)) first = t;
                else rest.add(t);
            }
            Collections.shuffle(rest, new Random());
            ArrayList<Track> shuffled = new ArrayList<>();
            if (first != null) shuffled.add(first);
            shuffled.addAll(rest);
            Store.saveQueue(shuffled, first == null ? 0 : 0);
            Store.setShuffle(true);
        } else {
            ArrayList<Track> original = Store.getOriginalQueue();
            if (original.isEmpty()) original = q;
            int at = 0;
            for (int i = 0; i < original.size(); i++) {
                if (original.get(i).id.equals(currentId)) { at = i; break; }
            }
            Store.saveQueue(original, at);
            Store.saveOriginalQueue(null);
            Store.setShuffle(false);
        }
        queue = Store.getQueue();
        index = Store.getQueueIndex();
        publishState();
        updateNotification();
    }

    private void cycleRepeat() {
        String r = Store.getRepeat();
        String nextMode = "off".equals(r) ? "all" : "all".equals(r) ? "one" : "off";
        Store.setRepeat(nextMode);
        publishState();
        updateNotification();
    }

    private void stopPlayback() {
        releasePlayer();
        current = null;
        prepared = false;
        Store.setPlayback(null, false, 0L, 0L);
        sendState();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        ticker.removeCallbacksAndMessages(null);
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    /* ---------------- state broadcast ---------------- */

    private void publishState() {
        long position = 0L;
        long duration = 0L;
        boolean playing = false;
        if (player != null && prepared) {
            try {
                position = player.getCurrentPosition();
                duration = player.getDuration();
                playing = player.isPlaying();
            } catch (IllegalStateException ignored) { }
        }
        Store.setPlayback(current, playing, position, duration);
        sendState();
    }

    private void sendState() {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("playing", Store.isPlaying());
        intent.putExtra("index", index);
        intent.putExtra("position", Store.getPosition());
        intent.putExtra("duration", Store.getDuration());
        intent.putExtra("shuffle", Store.isShuffle());
        intent.putExtra("repeat", Store.getRepeat());
        sendBroadcast(intent);
    }

    private void sendError() {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("error", true);
        sendBroadcast(intent);
    }

    private void scheduleTicker() {
        ticker.removeCallbacksAndMessages(null);
        ticker.postDelayed(new Runnable() {
            @Override public void run() {
                if (player != null && prepared) {
                    try {
                        if (player.isPlaying()) publishState();
                    } catch (IllegalStateException ignored) { }
                }
                ticker.postDelayed(this, 500);
            }
        }, 500);
    }

    /* ---------------- notification ---------------- */

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Воспроизведение",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Управление плеером AniBeat");
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void ensureForeground() {
        startForeground(NOTIFICATION_ID, buildNotification(null));
    }

    private void updateNotification() {
        final Track t = current;
        new Thread(() -> {
            Bitmap art = null;
            if (t != null) {
                String url = t.coverSmall.isEmpty() ? t.cover : t.coverSmall;
                art = ImageLoader.fetchSync(url, 128);
            }
            final Bitmap artFinal = art;
            main.post(() -> {
                try {
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(artFinal));
                } catch (Exception ignored) { }
            });
        }, "notif-art").start();
    }

    private PendingIntent actionIntent(String action, int req) {
        Intent i = new Intent(this, PlaybackService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, req, i, flags);
    }

    private PendingIntent contentIntent() {
        Intent i = new Intent(this, MainActivity.class).setAction("anibeat.OPEN_PLAYER");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(this, 10, i, flags);
    }

    private Notification buildNotification(Bitmap art) {
        boolean playing = Store.isPlaying();
        RemoteViews small = new RemoteViews(getPackageName(), R.layout.notif_player);
        if (current != null) {
            small.setTextViewText(R.id.notif_title, current.title);
            small.setTextViewText(R.id.notif_subtitle, current.displayArtist() + " · " + current.animeName);
            small.setTextViewText(R.id.notif_tag, current.themeTag());
            if (art != null) small.setImageViewBitmap(R.id.notif_cover, art);
        } else {
            small.setTextViewText(R.id.notif_title, "AniBeat");
            small.setTextViewText(R.id.notif_subtitle, "");
        }
        small.setImageViewResource(R.id.notif_toggle, playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        small.setOnClickPendingIntent(R.id.notif_prev, actionIntent(ACTION_PREVIOUS, 1));
        small.setOnClickPendingIntent(R.id.notif_toggle, actionIntent(ACTION_TOGGLE, 2));
        small.setOnClickPendingIntent(R.id.notif_next, actionIntent(ACTION_NEXT, 3));
        small.setOnClickPendingIntent(R.id.notif_stop, actionIntent(ACTION_STOP, 4));

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentIntent())
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(playing)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 24) {
            builder.setCustomContentView(small)
                    .setStyle(new Notification.DecoratedCustomViewStyle())
                    .setCustomBigContentView(small);
        } else {
            builder.setContent(small);
        }
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_LOW);
        }
        return builder.build();
    }
}
