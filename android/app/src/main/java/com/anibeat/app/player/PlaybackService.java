package com.anibeat.app.player;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Фоновое воспроизведение: ExoPlayer живёт в foreground-сервисе Media3,
 * уведомление и экран блокировки — штатные (MediaStyle + MediaSession).
 */
public class PlaybackService extends MediaSessionService {

    private ExoPlayer player;
    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        // Источник данных с кэшем: предзагруженный трек играет сразу, без ожидания сети.
        androidx.media3.datasource.DataSource.Factory upstream = new androidx.media3.datasource.DefaultDataSource.Factory(this);
        androidx.media3.datasource.cache.SimpleCache mediaCache = MediaCache.get(this);
        androidx.media3.exoplayer.source.MediaSource.Factory sourceFactory;
        if (mediaCache != null) {
            androidx.media3.datasource.cache.CacheDataSource.Factory cacheFactory =
                    new androidx.media3.datasource.cache.CacheDataSource.Factory()
                            .setCache(mediaCache)
                            .setCacheKeyFactory(MediaCache.KEY_FACTORY)
                            .setUpstreamDataSourceFactory(upstream)
                            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            sourceFactory = new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheFactory);
        } else {
            sourceFactory = new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(upstream);
        }

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(sourceFactory)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();

        Intent open = new Intent(this, com.anibeat.app.MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        session = new MediaSession.Builder(this, player)
                .setSessionActivity(contentIntent)
                .build();
        // Ошибка отдельного потока не должна останавливать музыку и закрывать приложение.
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                try {
                    int next = player.getCurrentMediaItemIndex() + 1;
                    if (next < player.getMediaItemCount()) {
                        player.seekTo(next, 0);
                        player.prepare();
                        player.play();
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        PlayerHolder.attach(session);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onTaskRemoved(@Nullable Intent rootIntent) {
        // Плеер продолжает играть в фоне, как и сайт; сервис остановится вместе с паузой.
        if (player == null || !player.getPlayWhenReady()) stopSelf();
    }

    @Override
    public void onDestroy() {
        PlayerHolder.detach();
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
