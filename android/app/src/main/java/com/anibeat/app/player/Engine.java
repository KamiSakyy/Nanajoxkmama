package com.anibeat.app.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.net.Uri;
import android.view.Surface;

import com.anibeat.app.core.Ui;

/**
 * Проигрыватель на системном Android-API (android.media.MediaPlayer) — чистая Java,
 * без сторонних библиотек и без Kotlin.
 */
public final class Engine {

    public interface Listener {
        void onReady();

        void onCompletion();

        void onError();

        void onBuffering(boolean buffering);
    }

    private final Context context;
    private final MediaPlayer player = new MediaPlayer();
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            try {
                playAfterPrepare = false;
                if (prepared && player.isPlaying()) player.pause();
                if (listener != null) listener.onBuffering(false);
                com.anibeat.app.player.Player.onFocusLost();
            } catch (Throwable t) {
                Ui.report(t);
            }
        }
    };
    private Listener listener;
    private Surface surface;
    private boolean preparing;
    private boolean prepared;
    private boolean buffering;
    private int seekAfterPrepare = -1;
    private boolean playAfterPrepare;

    private Engine(Context context) {
        this.context = context;
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        try {
            player.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK);
        } catch (Throwable ignored) {
        }
        player.setOnPreparedListener(mp -> {
            preparing = false;
            prepared = true;
            if (seekAfterPrepare > 0) {
                try {
                    mp.seekTo(seekAfterPrepare);
                } catch (Throwable t) {
                    Ui.report(t);
                }
            }
            seekAfterPrepare = -1;
            if (playAfterPrepare) {
                playAfterPrepare = false;
                try {
                    mp.start();
                } catch (Throwable t) {
                    Ui.report(t);
                }
            }
            notifyReady();
        });
        player.setOnCompletionListener(mp -> {
            if (listener != null) listener.onCompletion();
        });
        player.setOnErrorListener((mp, what, extra) -> {
            preparing = false;
            prepared = false;
            if (listener != null) listener.onError();
            return true;
        });
        player.setOnBufferingUpdateListener((mp, percent) -> {
            boolean now = percent < 100 && mp.isPlaying() && !isNearEnd(mp);
            if (now != buffering) {
                buffering = now;
                if (listener != null) listener.onBuffering(now);
            }
        });
        player.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                buffering = true;
                if (listener != null) listener.onBuffering(true);
                return true;
            }
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                buffering = false;
                if (listener != null) listener.onBuffering(false);
                return true;
            }
            return false;
        });
    }

    private static boolean isNearEnd(MediaPlayer mp) {
        try {
            int duration = mp.getDuration();
            return duration > 0 && mp.getCurrentPosition() > duration - 1500;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Engine create(Context context) {
        return new Engine(context);
    }

    public void setListener(Listener value) {
        listener = value;
    }

    private void notifyReady() {
        if (listener != null) listener.onReady();
    }

    /** Открывает источник; play — начать воспроизведение сразу после подготовки. */
    public void open(String url, boolean play) {
        try {
            player.reset();
            prepared = false;
            preparing = true;
            buffering = false;
            playAfterPrepare = play;
            if (surface != null) player.setSurface(surface);
            Context context = Player.context();
            if (url.startsWith("file:") || url.startsWith("content:")) {
                player.setDataSource(context, Uri.parse(url));
            } else {
                player.setDataSource(url);
            }
            player.prepareAsync();
        } catch (Throwable t) {
            preparing = false;
            Ui.report(t);
            if (listener != null) listener.onError();
        }
    }

    /** Просит фокус аудио, чтобы звонок или другой плеер корректно ставили нас на паузу. */
    private void requestFocus() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            }
            if (audioManager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest == null) {
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build())
                            .setOnAudioFocusChangeListener(focusListener)
                            .build();
                }
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void abandonFocus() {
        try {
            if (audioManager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(focusListener);
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public void play() {
        try {
            requestFocus();
            if (prepared) {
                player.start();
            } else if (preparing) {
                playAfterPrepare = true;
            }
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public void pause() {
        try {
            playAfterPrepare = false;
            if (prepared && player.isPlaying()) player.pause();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public boolean isPlaying() {
        try {
            return prepared && player.isPlaying();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isPreparing() {
        return preparing;
    }

    public boolean isBuffering() {
        return buffering || preparing;
    }

    public int position() {
        try {
            return prepared ? player.getCurrentPosition() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    public int duration() {
        try {
            int d = prepared ? player.getDuration() : 0;
            return d < 0 ? 0 : d;
        } catch (Throwable t) {
            return 0;
        }
    }

    public void seekTo(int ms) {
        try {
            if (prepared) player.seekTo(Math.max(0, ms));
            else if (preparing) seekAfterPrepare = Math.max(0, ms);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public void setVolume(float value) {
        try {
            player.setVolume(value, value);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public void setSurface(Surface value) {
        surface = value;
        try {
            player.setSurface(value);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public void stop() {
        try {
            playAfterPrepare = false;
            preparing = false;
            prepared = false;
            player.reset();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public void release() {
        try {
            abandonFocus();
            player.reset();
            player.release();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public boolean isPrepared() {
        return prepared;
    }
}
