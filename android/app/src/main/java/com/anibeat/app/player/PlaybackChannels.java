package com.anibeat.app.player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.anibeat.app.core.Ui;

/**
 * Каналы уведомлений. Отдельный класс нужен, чтобы на старых телефонах
 * (Android 7) классы новых версий вообще не загружались.
 */
final class PlaybackChannels {

    private PlaybackChannels() {
    }

    static void create(Context context, String channelId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (manager.getNotificationChannel(channelId) != null) return;
            NotificationChannel channel = new NotificationChannel(channelId, "Воспроизведение",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    static Notification.Builder builder(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(context, channelId);
        }
        return new Notification.Builder(context);
    }
}
