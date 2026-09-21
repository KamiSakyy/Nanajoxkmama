package com.anibeat.app.player;

/**
 * Мост между службой воспроизведения и приложением.
 * Здесь живёт проигрыватель — системный android.media.MediaPlayer (чистая Java).
 */
public final class PlayerHolder {

    private static Engine engine;
    private static boolean fromService;

    private PlayerHolder() {
    }

    public static void attach(Engine value, boolean service) {
        engine = value;
        fromService = service;
    }

    public static void detach(Engine value) {
        if (engine == value) {
            engine = null;
            fromService = false;
        }
    }

    public static Engine engine() {
        return engine;
    }

    public static boolean fromService() {
        return fromService;
    }
}
