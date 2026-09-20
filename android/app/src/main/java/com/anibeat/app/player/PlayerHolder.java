package com.anibeat.app.player;

import androidx.media3.session.MediaSession;

/** Мост между сервисом воспроизведения и приложением. */
public final class PlayerHolder {

    private static MediaSession session;

    private PlayerHolder() {
    }

    static void attach(MediaSession value) {
        session = value;
    }

    static void detach() {
        session = null;
    }

    public static MediaSession session() {
        return session;
    }
}
