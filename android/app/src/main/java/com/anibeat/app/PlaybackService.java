package com.anibeat.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import java.lang.ref.WeakReference;

/**
 * Foreground service that keeps the WebView audio alive in the background and
 * mirrors the page's {@code navigator.mediaSession} state to the lockscreen and
 * the notification shade (play / pause / next / previous / seek).
 */
public class PlaybackService extends Service {

    private static final String TAG = "AniBeatPlayback";
    private static final String CHANNEL_ID = "anibeat-playback";
    private static final int NOTIFICATION_ID = 4242;

    private static final String ACTION_METADATA = "com.anibeat.app.METADATA";
    private static final String ACTION_STATE = "com.anibeat.app.STATE";
    private static final String ACTION_STOP = "com.anibeat.app.STOP";
    private static final String ACTION_CMD = "com.anibeat.app.CMD";

    private static WeakReference<WebView> webRef = new WeakReference<>(null);

    private MediaSessionCompat session;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean foreground;
    private boolean playing;
    private long positionMs;
    private long durationMs;
    private String title = "";
    private String artist = "";
    private String album = "";
    private Bitmap artwork;

    /* ------------------------------------------------------------------ */
    /* Static entry points used by the JS bridge                           */
    /* ------------------------------------------------------------------ */

    static void attachWebView(WebView web) {
        webRef = new WeakReference<>(web);
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Воспроизведение", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Управление плеером AniBeat");
        channel.setShowBadge(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(channel);
    }

    static void updateMetadata(Context context, String title, String artist, String album, String artworkUrl) {
        Intent intent = new Intent(context, PlaybackService.class);
        intent.setAction(ACTION_METADATA);
        intent.putExtra("title", title);
        intent.putExtra("artist", artist);
        intent.putExtra("album", album);
        intent.putExtra("art", artworkUrl);
        start(context, intent);
    }

    static void updateState(Context context, boolean playing, long positionMs, long durationMs) {
        Intent intent = new Intent(context, PlaybackService.class);
        intent.setAction(ACTION_STATE);
        intent.putExtra("playing", playing);
        intent.putExtra("position", positionMs);
        intent.putExtra("duration", durationMs);
        start(context, intent);
    }

    /** Called once the page finished booting. */
    static void syncFromWeb(Context context) {
        Log.d(TAG, "web ready");
    }

    private static void start(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Exception e) {
            Log.w(TAG, "start " + e);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        session = new MediaSessionCompat(this, "AniBeat");
        session.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                dispatch("play", "null");
            }

            @Override
            public void onPause() {
                dispatch("pause", "null");
            }

            @Override
            public void onSkipToNext() {
                dispatch("nexttrack", "null");
            }

            @Override
            public void onSkipToPrevious() {
                dispatch("previoustrack", "null");
            }

            @Override
            public void onSeekTo(long pos) {
                dispatch("seekto", String.valueOf(pos / 1000.0));
            }

            @Override
            public void onStop() {
                dispatch("pause", "null");
                stopSelf();
            }
        });
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_METADATA.equals(action)) {
            title = intent.getStringExtra("title");
            artist = intent.getStringExtra("artist");
            album = intent.getStringExtra("album");
            loadArtwork(intent.getStringExtra("art"));
            updateSessionMetadata();
            pushNotification();
        } else if (ACTION_STATE.equals(action)) {
            playing = intent.getBooleanExtra("playing", false);
            positionMs = intent.getLongExtra("position", positionMs);
            durationMs = intent.getLongExtra("duration", durationMs);
            updateSessionPlaybackState();
            if (playing) {
                requestFocus();
                startForegroundCompat();
                pushNotification();
            } else if (foreground) {
                pushNotification();
            }
        } else if (ACTION_CMD.equals(action)) {
            dispatch(intent.getStringExtra("cmd"), intent.getStringExtra("value") == null ? "null" : intent.getStringExtra("value"));
        } else if (ACTION_STOP.equals(action)) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        abandonFocus();
        if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        super.onDestroy();
    }

    /* ------------------------------------------------------------------ */
    /* Internals                                                           */
    /* ------------------------------------------------------------------ */

    private void dispatch(String action, String value) {
        WebView web = webRef.get();
        if (web == null) return;
        WebAppBridge.dispatchMediaAction(web, action, value);
    }

    private void updateSessionMetadata() {
        MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        if (artwork != null) b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork);
        session.setMetadata(b.build());
    }

    private void updateSessionPlaybackState() {
        PlaybackStateCompat.Builder b = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SEEK_TO);
        b.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, positionMs,
                playing ? 1f : 0f);
        session.setPlaybackState(b.build());
    }

    private void loadArtwork(String url) {
        if (url == null || url.isEmpty()) return;
        if (artwork != null && artwork.getWidth() > 0) {
            // keep the current bitmap until the new one is decoded
        }
        MediaArtwork.load(this, url, bitmap -> {
            if (bitmap != null) {
                artwork = bitmap;
                updateSessionMetadata();
                pushNotification();
            }
        });
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foreground = true;
    }

    private void pushNotification() {
        Notification notification = buildNotification();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (foreground) nm.notify(NOTIFICATION_ID, notification);
        else {
            // not a foreground service (paused): keep the notification alive so the
            // user can resume from the shade, exactly like the web player would.
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        ensureChannel(this);
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_anibeat)
                .setContentTitle(title.isEmpty() ? "AniBeat" : title)
                .setContentText(artist)
                .setSubText(album)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(playing)
                .setSilent(true);

        if (artwork != null) b.setLargeIcon(artwork);

        b.addAction(action(R.drawable.ic_prev, "Назад", "previoustrack"));
        if (playing) b.addAction(action(R.drawable.ic_pause, "Пауза", "pause"));
        else b.addAction(action(R.drawable.ic_play, "Играть", "play"));
        b.addAction(action(R.drawable.ic_next, "Дальше", "nexttrack"));

        b.setStyle(new MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        return b.build();
    }

    private NotificationCompat.Action action(int icon, String title, String command) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(ACTION_CMD);
        intent.putExtra("cmd", command);
        int code = command.hashCode();
        PendingIntent pi = PendingIntent.getService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action(icon, title, pi);
    }

    private void requestFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest == null) {
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build())
                            .setOnAudioFocusChangeListener(this::onFocusChange, new android.os.Handler(Looper.getMainLooper()))
                            .build();
                }
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(focus -> onFocusChange(focus), AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception e) {
            Log.w(TAG, "focus " + e);
        }
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) {
        }
    }

    private void onFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            dispatch("pause", "null");
        }
    }
}
