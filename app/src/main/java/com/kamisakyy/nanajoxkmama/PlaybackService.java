package com.kamisakyy.nanajoxkmama;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.util.ArrayList;

/** Foreground Android media service: audio continues while the app is backgrounded. */
public final class PlaybackService extends Service {
    public static final String ACTION_PLAY = "com.kamisakyy.nanajoxkmama.PLAY";
    public static final String ACTION_TOGGLE = "com.kamisakyy.nanajoxkmama.TOGGLE";
    public static final String ACTION_NEXT = "com.kamisakyy.nanajoxkmama.NEXT";
    public static final String ACTION_PREVIOUS = "com.kamisakyy.nanajoxkmama.PREVIOUS";
    public static final String ACTION_SEEK = "com.kamisakyy.nanajoxkmama.SEEK";
    public static final String ACTION_STOP = "com.kamisakyy.nanajoxkmama.STOP";
    public static final String BROADCAST_STATE = "com.kamisakyy.nanajoxkmama.PLAYBACK_STATE";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_POSITION = "position";
    private static final String CHANNEL_ID = "anibeat_playback";
    private static final int NOTIFICATION_ID = 412;

    private MediaPlayer player;
    private ArrayList<Track> queue = new ArrayList<>();
    private int index;
    private Track current;
    private boolean prepared;

    public static void play(Context context, ArrayList<Track> tracks, int startIndex) {
        Store.saveQueue(tracks, Math.max(0, Math.min(startIndex, Math.max(0, tracks.size() - 1))));
        Intent intent = new Intent(context, PlaybackService.class).setAction(ACTION_PLAY).putExtra(EXTRA_INDEX, startIndex);
        start(context, intent);
    }

    public static void command(Context context, String action) {
        start(context, new Intent(context, PlaybackService.class).setAction(action));
    }

    public static void seek(Context context, long position) {
        start(context, new Intent(context, PlaybackService.class).setAction(ACTION_SEEK).putExtra(EXTRA_POSITION, position));
    }

    private static void start(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        Store.init(this);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground();
        String action = intent == null ? ACTION_PLAY : intent.getAction();
        if (action == null) action = ACTION_PLAY;
        if (ACTION_PLAY.equals(action)) {
            queue = Store.getQueue();
            int requested = intent == null ? Store.getQueueIndex() : intent.getIntExtra(EXTRA_INDEX, Store.getQueueIndex());
            load(Math.max(0, Math.min(requested, Math.max(0, queue.size() - 1))));
        } else if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_NEXT.equals(action)) {
            next();
        } else if (ACTION_PREVIOUS.equals(action)) {
            previous();
        } else if (ACTION_SEEK.equals(action)) {
            seekTo(intent == null ? 0L : intent.getLongExtra(EXTRA_POSITION, 0L));
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }
        return START_STICKY;
    }

    private void load(int requestedIndex) {
        queue = Store.getQueue();
        if (queue.isEmpty()) {
            stopPlayback();
            return;
        }
        index = requestedIndex;
        if (index < 0 || index >= queue.size()) index = 0;
        Store.setQueueIndex(index);
        current = queue.get(index);
        prepared = false;
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    mp.start();
                    publishState();
                    updateNotification();
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) { next(); }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    sendError();
                    next();
                    return true;
                }
            });
            String source = current.playableUrl();
            if (source == null || source.isEmpty()) throw new IllegalStateException("Нет аудиофайла");
            if (source.startsWith("content:")) player.setDataSource(this, Uri.parse(source));
            else player.setDataSource(source);
            player.prepareAsync();
            Store.setPlayback(current, false, 0L, 0L);
            publishState();
            updateNotification();
        } catch (Exception e) {
            sendError();
            next();
        }
    }

    private void toggle() {
        if (player == null || !prepared) return;
        try {
            if (player.isPlaying()) player.pause(); else player.start();
            publishState();
            updateNotification();
        } catch (IllegalStateException ignored) { }
    }

    private void next() {
        queue = Store.getQueue();
        if (queue.isEmpty()) return;
        if (index + 1 >= queue.size()) {
            stopPlayback();
            return;
        }
        load(index + 1);
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
        if (index > 0) load(index - 1);
        else if (current != null) load(index);
    }

    private void seekTo(long position) {
        if (player == null || !prepared) return;
        try {
            player.seekTo((int) Math.max(0, Math.min(position, player.getDuration())));
            publishState();
        } catch (IllegalStateException ignored) { }
    }

    private void stopPlayback() {
        releasePlayer();
        current = null;
        prepared = false;
        Store.setPlayback(null, false, 0L, 0L);
        sendState();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    private void publishState() {
        long position = 0L;
        long duration = 0L;
        boolean playing = false;
        if (player != null && prepared) {
            try { position = player.getCurrentPosition(); duration = player.getDuration(); playing = player.isPlaying(); } catch (IllegalStateException ignored) { }
        }
        Store.setPlayback(current, playing, position, duration);
        sendState();
    }

    private void sendState() {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("playing", Store.isPlaying());
        intent.putExtra("position", Store.getPosition());
        intent.putExtra("duration", Store.getDuration());
        intent.putExtra("index", index);
        sendBroadcast(intent);
    }

    private void sendError() {
        Intent intent = new Intent(BROADCAST_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra("error", true);
        sendBroadcast(intent);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Воспроизведение AniBeat", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Управление текущей аниме-музыкой");
        channel.setShowBadge(false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void ensureForeground() {
        updateNotification();
    }

    private void updateNotification() {
        String title = current == null ? "AniBeat" : current.title;
        String subtitle = current == null ? "Аниме музыка" : current.displayArtist() + " · " + current.animeName;
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 1, open, pendingFlags());
        PendingIntent previous = PendingIntent.getService(this, 2, new Intent(this, PlaybackService.class).setAction(ACTION_PREVIOUS), pendingFlags());
        PendingIntent toggle = PendingIntent.getService(this, 3, new Intent(this, PlaybackService.class).setAction(ACTION_TOGGLE), pendingFlags());
        PendingIntent next = PendingIntent.getService(this, 4, new Intent(this, PlaybackService.class).setAction(ACTION_NEXT), pendingFlags());
        boolean playing = Store.isPlaying();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setContentIntent(content)
                .setOngoing(current != null)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(null, "Назад", previous).build())
                .addAction(new Notification.Action.Builder(null, playing ? "Пауза" : "Играть", toggle).build())
                .addAction(new Notification.Action.Builder(null, "Далее", next).build());
        startForeground(NOTIFICATION_ID, builder.build());
    }

    private int pendingFlags() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
    }

    @Override public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
