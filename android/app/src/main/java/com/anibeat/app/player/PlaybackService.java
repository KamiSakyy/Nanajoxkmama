package com.anibeat.app.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.anibeat.app.MainActivity;
import com.anibeat.app.R;
import com.anibeat.app.core.Image;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Models;

/**
 * Фоновое воспроизведение на чистой Java: обычная служба, системный MediaPlayer,
 * системное уведомление и экран блокировки. Без сторонних библиотек и без Kotlin.
 */
public class PlaybackService extends Service {

    public static final String ACTION_TOGGLE = "com.anibeat.app.TOGGLE";
    public static final String ACTION_NEXT = "com.anibeat.app.NEXT";
    public static final String ACTION_PREV = "com.anibeat.app.PREV";
    public static final String ACTION_STOP = "com.anibeat.app.STOP";

    private static final String CHANNEL_ID = "anibeat_playback";
    private static final int NOTIFICATION_ID = 42;

    private Engine engine;
    private MediaSession session;
    private final Player.Listener playerListener = this::updateNotification;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        engine = Engine.create(this);
        PlayerHolder.attach(engine, true);
        session = new MediaSession(this, "AniBeat");
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                Player.play();
            }

            @Override
            public void onPause() {
                Player.pause();
            }

            @Override
            public void onSkipToNext() {
                Player.next(false);
            }

            @Override
            public void onSkipToPrevious() {
                Player.prev();
            }

            @Override
            public void onSeekTo(long pos) {
                Player.seekTo(pos);
            }

            @Override
            public void onStop() {
                Player.pause();
            }
        });
        session.setActive(true);
        Player.addListener(playerListener);
        startForeground(NOTIFICATION_ID, buildNotification());
        Player.attachEngine(engine);
        Player.onEngineReady(this);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) Player.toggle();
        else if (ACTION_NEXT.equals(action)) Player.next(false);
        else if (ACTION_PREV.equals(action)) Player.prev();
        else if (ACTION_STOP.equals(action)) {
            Player.pause();
            stopSelf();
            return START_NOT_STICKY;
        }
        // Каждый startForegroundService требует перевести службу в foreground-режим.
        startForeground(NOTIFICATION_ID, buildNotification());
        updateNotification();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Player.removeListener(playerListener);
        PlayerHolder.detach(engine);
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        if (engine != null) {
            engine.release();
            engine = null;
        }
        super.onDestroy();
    }

    /* ------------------------------------------------------------------ */
    /* Уведомление и экран блокировки                                      */
    /* ------------------------------------------------------------------ */

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Воспроизведение",
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private PendingIntent action(String value) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(value);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, value.hashCode(), intent, flags);
    }

    private Notification buildNotification() {
        boolean playing = Player.isPlaying();
        Models.Track track = Player.current();
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = track == null ? "AniBeat" : track.title;
        String text = track == null ? "Готов к воспроизведению" : track.artistNames();

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_play_arrow)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .addAction(android.R.drawable.ic_media_previous, "Назад", action(ACTION_PREV))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Пауза" : "Играть", action(ACTION_TOGGLE))
                .addAction(android.R.drawable.ic_media_next, "Дальше", action(ACTION_NEXT));
        if (session != null) {
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(session.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
        }
        return builder.build();
    }

    private void updateNotification() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
            updateSession();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    /** Метаданные и состояние для экрана блокировки, наушников и Bluetooth. */
    private void updateSession() {
        if (session == null) return;
        try {
            Models.Track track = Player.current();
            if (track != null) {
                MediaMetadata.Builder meta = new MediaMetadata.Builder();
                meta.putString(MediaMetadata.METADATA_KEY_TITLE, track.title);
                meta.putString(MediaMetadata.METADATA_KEY_ARTIST, track.artistNames());
                meta.putString(MediaMetadata.METADATA_KEY_ALBUM,
                        (track.anime == null || track.anime.name == null ? "" : track.anime.name) + " · " + track.themeSlug);
                long duration = Player.duration();
                if (duration > 0) meta.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
                String art = track.cover != null ? track.cover : track.coverSmall;
                if (art != null) {
                    Bitmap bitmap = Image.cached(art);
                    if (bitmap != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);
                }
                session.setMetadata(meta.build());
            }
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                    | PlaybackState.ACTION_STOP;
            int state = Player.isPlaying() ? PlaybackState.STATE_PLAYING
                    : Player.isBuffering() ? PlaybackState.STATE_BUFFERING
                    : Player.current() == null ? PlaybackState.STATE_STOPPED
                    : PlaybackState.STATE_PAUSED;
            session.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, Player.position(), 1f)
                    .build());
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    /** Обновляет уведомление по запросу приложения. */
    public static void notifyState(Context context, boolean playing) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, PlaybackService.class);
            intent.putExtra("playing", playing);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable ignored) {
        }
    }
}
