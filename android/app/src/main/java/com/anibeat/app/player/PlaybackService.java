package com.anibeat.app.player;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.anibeat.app.MainActivity;
import com.anibeat.app.R;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Models;

/** Служба воспроизведения: ExoPlayer, медиа-сессия и уведомление плеера. */
public class PlaybackService extends Service {

    public static final String ACTION_PREPARE = "com.anibeat.app.action.PREPARE";
    public static final String ACTION_PLAY = "com.anibeat.app.action.PLAY";
    public static final String ACTION_TOGGLE = "com.anibeat.app.action.TOGGLE";
    public static final String ACTION_NEXT = "com.anibeat.app.action.NEXT";
    public static final String ACTION_PREV = "com.anibeat.app.action.PREV";
    public static final String ACTION_STOP = "com.anibeat.app.action.STOP";
    public static final String ACTION_SEEK = "com.anibeat.app.action.SEEK";
    public static final String EXTRA_POSITION = "position";

    private static final String CHANNEL_ID = "anibeat-playback";
    private static final int NOTIFICATION_ID = 4711;
    private static final long ACTIONS = PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_SEEK_TO
            | PlaybackStateCompat.ACTION_STOP;

    private ExoPlayer engine;
    private MediaSessionCompat session;
    private boolean foreground;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createChannel();
            engine = new ExoPlayer.Builder(this).build();
            engine.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true);
            engine.setWakeMode(C.WAKE_MODE_NETWORK);
            engine.setHandleAudioBecomingNoisy(true);
            engine.addListener(new EngineListener());
            session = new MediaSessionCompat(this, "AniBeat");
            session.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            session.setCallback(new SessionCallback());
            session.setActive(true);
            Player.attach(this, engine);
        } catch (Throwable t) {
            Ui.report(t);
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            String action = intent == null || intent.getAction() == null ? ACTION_PREPARE : intent.getAction();
            if (ACTION_SEEK.equals(action)) {
                Player.seekTo(intent.getLongExtra(EXTRA_POSITION, 0L));
            } else if (!Player.handleServiceCommand(action)) {
                Player.play();
            }
            update(true);
        } catch (Throwable t) {
            Ui.report(t);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try {
            Player.detach(engine);
        } catch (Throwable t) {
            Ui.report(t);
        }
        try {
            if (engine != null) engine.release();
        } catch (Throwable t) {
            Ui.report(t);
        }
        try {
            if (session != null) {
                session.setActive(false);
                session.release();
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
        engine = null;
        session = null;
        stopForegroundNow();
        super.onDestroy();
    }

    /** Остановить воспроизведение и убрать уведомление. */
    public void stopPlayback() {
        try {
            if (engine != null) {
                engine.stop();
                engine.clearMediaItems();
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
        stopForegroundNow();
        stopSelf();
    }

    /* ------------------------------ внутреннее --------------------------- */

    private final class EngineListener implements androidx.media3.common.Player.Listener {

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            update(false);
        }

        @Override
        public void onPlaybackStateChanged(int state) {
            try {
                Player.markPrepared(state == androidx.media3.common.Player.STATE_READY
                        || state == androidx.media3.common.Player.STATE_BUFFERING);
                if (state == androidx.media3.common.Player.STATE_ENDED && Player.repeat() == Player.REPEAT_NONE) {
                    Player.markPrepared(false);
                    stopForegroundNow();
                    return;
                }
            } catch (Throwable t) {
                Ui.report(t);
            }
            update(false);
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            try {
                if (engine != null) Player.syncIndexFromEngine(engine.getCurrentMediaItemIndex());
            } catch (Throwable t) {
                Ui.report(t);
            }
            update(true);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Ui.report(error);
            try {
                if (engine != null && engine.hasNextMediaItem()) {
                    engine.seekToNextMediaItem();
                    engine.play();
                } else {
                    Player.markPrepared(false);
                    stopForegroundNow();
                }
            } catch (Throwable t) {
                Ui.report(t);
            }
            update(true);
        }
    }

    private final class SessionCallback extends MediaSessionCompat.Callback {
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
        public void onSeekTo(long position) {
            Player.seekTo(position);
        }

        @Override
        public void onStop() {
            Player.stop();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            for (int i = 0; i < Player.queue().size(); i++) {
                Models.Track track = Player.trackAt(i);
                if (track != null && track.id != null && track.id.equals(mediaId)) {
                    Player.jumpTo(i);
                    return;
                }
            }
        }
    }

    private void update(boolean withMetadata) {
        try {
            Models.Track track = Player.current();
            if (session != null && withMetadata && track != null) {
                String cover = track.cover != null && !track.cover.isEmpty() ? track.cover : track.coverSmall;
                MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                                track.title == null || track.title.isEmpty() ? track.themeSlug : track.title)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artistNames())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.anime == null ? "" : track.anime.name)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Player.duration());
                if (cover != null && !cover.isEmpty()) {
                    meta.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, cover);
                }
                session.setMetadata(meta.build());
            }
            if (session != null) {
                int state = Player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING
                        : Player.isBuffering() ? PlaybackStateCompat.STATE_BUFFERING
                        : PlaybackStateCompat.STATE_PAUSED;
                session.setPlaybackState(new PlaybackStateCompat.Builder()
                        .setActions(ACTIONS)
                        .setState(state, Player.position(), Player.speed())
                        .build());
            }
            if (Player.current() != null) {
                startForegroundNow();
            }
            Player.notifyChanged();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void startForegroundNow() {
        Notification notification = buildNotification();
        try {
            if (!foreground) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                foreground = true;
            } else {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void stopForegroundNow() {
        try {
            if (foreground) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                foreground = false;
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private Notification buildNotification() {
        Models.Track track = Player.current();
        String title = track == null ? "AniBeat"
                : (track.title == null || track.title.isEmpty() ? track.themeSlug : track.title);
        StringBuilder text = new StringBuilder();
        if (track != null) {
            text.append(track.artistNames());
            if (track.anime != null && track.anime.name != null && !track.anime.name.isEmpty()) {
                if (text.length() > 0) text.append(" · ");
                text.append(track.anime.name);
            }
        }
        if (text.length() == 0) text.append("Готовим воспроизведение");
        boolean playing = Player.isPlaying();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_anibeat)
                .setContentTitle(title)
                .setContentText(text.toString())
                .setContentIntent(activityIntent())
                .setDeleteIntent(serviceIntent(ACTION_STOP))
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSilent(true)
                .setOngoing(playing)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(R.drawable.ic_skip_previous, "Назад", serviceIntent(ACTION_PREV))
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play,
                        playing ? "Пауза" : "Играть", serviceIntent(ACTION_TOGGLE))
                .addAction(R.drawable.ic_skip_next, "Вперёд", serviceIntent(ACTION_NEXT));
        if (track != null) {
            androidx.media.app.NotificationCompat.MediaStyle style = new androidx.media.app.NotificationCompat.MediaStyle();
            style.setShowActionsInCompactView(0, 1, 2);
            if (session != null) style.setMediaSession(session.getSessionToken());
            builder.setStyle(style);
        }
        return builder.build();
    }

    private PendingIntent activityIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent serviceIntent(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel() {
        NotificationChannelCompat channel = new NotificationChannelCompat.Builder(CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Воспроизведение")
                .setDescription("Управление музыкой AniBeat")
                .build();
        NotificationManagerCompat.from(this).createNotificationChannel(channel);
    }
}
