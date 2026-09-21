package com.anibeat.app.player;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Мост между службой воспроизведения и приложением.
 * Здесь живёт сам проигрыватель — обычный ExoPlayer (чистая Java, без Kotlin).
 */
public final class PlayerHolder {

    /** Откуда взят проигрыватель: служба (фон) или приложение. */
    public interface Owner {
        void onOwnerChanged(boolean fromService);
    }

    private static ExoPlayer engine;
    private static boolean fromService;
    private static Owner owner;

    private PlayerHolder() {
    }

    static void attach(ExoPlayer player, boolean service) {
        engine = player;
        fromService = service;
        Owner o = owner;
        if (o != null) {
            try {
                o.onOwnerChanged(service);
            } catch (Throwable t) {
                com.anibeat.app.core.Ui.report(t);
            }
        }
    }

    static void detach(ExoPlayer player) {
        if (engine == player) {
            engine = null;
            fromService = false;
        }
    }

    public static void setOwnerListener(Owner value) {
        owner = value;
    }

    public static ExoPlayer engine() {
        return engine;
    }

    /** Проигрыватель в виде общего интерфейса — нужен экрану видео. */
    public static Player player() {
        return engine;
    }

    public static boolean fromService() {
        return fromService;
    }
}
