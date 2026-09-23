package com.anibeat.app;

import android.app.Application;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Приложение пишет сбой в файл crash.txt, чтобы причина была видна на экране
 * при следующем запуске, а не пропадала вместе с закрытым окном.
 */
public class AniBeatApp extends Application {

    public static final String CRASH_FILE = "crash.txt";
    private static final String TAG = "AniBeat";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
                try {
                    write(this, thread.getName(), error);
                } catch (Throwable ignored) {
                }
                Log.e(TAG, "Сбой", error);
                if (thread == android.os.Looper.getMainLooper().getThread()) {
                    // Основной поток: приложение снова откроется вместо закрытия.
                    relaunch(AniBeatApp.this, error);
                    return;
                }
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "не удалось включить запись сбоев", t);
        }
    }

    /** Метка времени последнего перезапуска: не даём приложению уйти в бесконечную петлю. */
    private static long lastRestart;

    /** Открыть приложение заново после сбоя вместо закрытия окна. */
    private static void relaunch(final android.content.Context context, final Throwable error) {
        long now = System.currentTimeMillis();
        if (now - lastRestart < 15000) {
            Log.e(TAG, "повторный сбой — перезапуск отменён", error);
            return;
        }
        lastRestart = now;
        try {
            android.content.Intent intent = new android.content.Intent(context, MainActivity.class);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "не удалось перезапустить приложение", t);
            return;
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    public static String describe(Throwable error) {
        if (error == null) return "неизвестная ошибка";
        StringWriter buffer = new StringWriter();
        try {
            error.printStackTrace(new PrintWriter(buffer));
        } catch (Throwable ignored) {
            return String.valueOf(error);
        }
        String text = buffer.toString();
        return text.length() > 6000 ? text.substring(0, 6000) + "\n…" : text;
    }

    public static void write(android.content.Context context, String place, Throwable error) {
        try {
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String text = "=== " + stamp + " [" + place + "]\n" + describe(error) + "\n";
            FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), CRASH_FILE), false);
            out.write(text.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    public static String read(android.content.Context context) {
        try {
            File file = new File(context.getFilesDir(), CRASH_FILE);
            if (!file.exists()) return null;
            byte[] data = new byte[(int) Math.min(file.length(), 20000)];
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            int read = in.read(data);
            in.close();
            if (read <= 0) return null;
            return new String(data, 0, read, "UTF-8");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void clear(android.content.Context context) {
        try {
            new File(context.getFilesDir(), CRASH_FILE).delete();
        } catch (Throwable ignored) {
        }
    }
}
