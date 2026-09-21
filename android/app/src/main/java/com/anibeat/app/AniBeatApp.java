package com.anibeat.app;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

/**
 * Последняя линия защиты: если что-то всё-таки выбросило ошибку,
 * приложение не «исчезает» — оно записывает сбой и само открывается заново.
 */
public class AniBeatApp extends Application {

    private static final String TAG = "AniBeat";
    private static final long RESTART_WINDOW = 5 * 60 * 1000L;
    private static final int RESTART_LIMIT = 3;

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler fallback = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            boolean main = thread == Looper.getMainLooper().getThread();
            write(thread.getName(), error);
            Log.e(TAG, "Сбой", error);
            if (main && canRestart()) scheduleRestart();
            if (fallback != null) {
                fallback.uncaughtException(thread, error);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    private void write(String threadName, Throwable error) {
        try {
            File file = new File(getFilesDir(), "problems.log");
            FileWriter writer = new FileWriter(file, true);
            writer.write("=== " + new Date() + " [" + threadName + "]\n" + Log.getStackTraceString(error) + "\n");
            writer.close();
        } catch (Throwable ignored) {
        }
    }

    /** Не даём приложению зациклиться на перезапуске, если ошибка повторяется. */
    private boolean canRestart() {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("anibeat_recovery", Context.MODE_PRIVATE);
            long windowStart = prefs.getLong("window", 0L);
            int count = prefs.getInt("count", 0);
            long now = System.currentTimeMillis();
            if (now - windowStart > RESTART_WINDOW) {
                prefs.edit().putLong("window", now).putInt("count", 1).apply();
                return true;
            }
            if (count >= RESTART_LIMIT) return false;
            prefs.edit().putInt("count", count + 1).apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Будильник поднимает приложение заново уже в новом процессе. */
    private void scheduleRestart() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pending = PendingIntent.getActivity(this, 7, intent, flags);
            AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.set(AlarmManager.RTC, System.currentTimeMillis() + 400L, pending);
        } catch (Throwable ignored) {
        }
    }
}
