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
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;

import com.anibeat.app.MainActivity;
import com.anibeat.app.R;
import com.anibeat.app.core.Image;
import com.anibeat.app.core.Ui;

/**
 * Фоновое воспроизведение на чистой Java: обычная служба, обычный ExoPlayer,
 * системное уведомление и экран блокировки через android.media.session (без Kotlin).
 */
public class PlaybackService extends Service {

    public static final String ACTION_TOGGLE = "com.anibeat.app.TOGGLE";
    public static final String ACTION_NEXT = "com.anibeat.app.NEXT";
    public static final String ACTION_PREV = "com.anibeat.app.PREV";
    public static final String ACTION_STOP = "com.anibeat.app.STOP";

    private static final String CHANNEL_ID = "anibeat_playback";
    private static final int NOTIFICATION_ID = 42;

    private ExoPlayer engine;
    private MediaSession session;
    private final androidx.media3.common.Player.Listener engineListener = new androidx.media3.common.Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            updateNotification();
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            updateNotification();
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            updateNotification();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            // Битый трек не должен прерывать музыку — переходим к следующему.
            try {
                int next = engine.getCurrentMediaItemIndex() + 1;
                if (next < engine.getMediaItemCount()) {
                    engine.seekTo(next, 0);
                    engine.prepare();
                    engine.play();
                }
            } catch (Throwable t) {
                Ui.report(t);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        engine = Player.createEngine(this);
        Player.attachEngineListener(engine);
        engine.addListener(engineListener);
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
        PlayerHolder.attach(engine, true);
        startForeground(NOTIFICATION_ID, buildNotification());
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
        if (engine != null) {
            updateNotification();
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        PlayerHolder.detach(engine);
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        if (engine != null) {
            engine.removeListener(engineListener);
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
        boolean playing = engine != null && engine.isPlaying();
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        MediaItem item = engine == null ? null : engine.getCurrentMediaItem();
        String title = "AniBeat";
        String text = "Готов к воспроизведению";
        if (item != null && item.mediaMetadata != null) {
            String t = item.mediaMetadata.title == null ? null : item.mediaMetadata.title.toString();
            String a = item.mediaMetadata.artist == null ? null : item.mediaMetadata.artist.toString();
            String album = item.mediaMetadata.albumTitle == null ? null : item.mediaMetadata.albumTitle.toString();
            if (t != null && !t.isEmpty()) title = t;
            StringBuilder sb = new StringBuilder();
            if (a != null && !a.isEmpty()) sb.append(a);
            if (album != null && !album.isEmpty()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(album);
            }
            if (sb.length() > 0) text = sb.toString();
        }

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
            if (engine == null) return;
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
            updateSession();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    /** Метаданные и состояние для экрана блокировки и Bluetooth-пульта. */
    private void updateSession() {
        if (session == null || engine == null) return;
        try {
            MediaItem item = engine.getCurrentMediaItem();
            if (item != null && item.mediaMetadata != null) {
                MediaMetadata.Builder meta = new MediaMetadata.Builder();
                CharSequence title = item.mediaMetadata.title;
                CharSequence artist = item.mediaMetadata.artist;
                CharSequence album = item.mediaMetadata.albumTitle;
                meta.putString(MediaMetadata.METADATA_KEY_TITLE, title == null ? "AniBeat" : title.toString());
                meta.putString(MediaMetadata.METADATA_KEY_ARTIST, artist == null ? "" : artist.toString());
                meta.putString(MediaMetadata.METADATA_KEY_ALBUM, album == null ? "" : album.toString());
                long duration = engine.getDuration();
                if (duration > 0) meta.putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
                if (item.mediaMetadata.artworkUri != null) {
                    Bitmap art = Image.cached(item.mediaMetadata.artworkUri.toString());
                    if (art != null) meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
                }
                session.setMetadata(meta.build());
            }
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                    | PlaybackState.ACTION_STOP;
            int state = engine.isPlaying() ? PlaybackState.STATE_PLAYING
                    : engine.getPlaybackState() == androidx.media3.common.Player.STATE_BUFFERING ? PlaybackState.STATE_BUFFERING
                    : engine.getPlaybackState() == androidx.media3.common.Player.STATE_ENDED ? PlaybackState.STATE_STOPPED
                    : PlaybackState.STATE_PAUSED;
            session.setPlaybackState(new PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, engine.getCurrentPosition(), 1f)
                    .build());
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    /** Обновление уведомления по запросу приложения. */
    public static void refresh(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, PlaybackService.class);
            context.startService(intent);
        } catch (Throwable ignored) {
        }
    }
}
