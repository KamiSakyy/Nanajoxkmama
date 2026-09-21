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
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "не удалось включить запись сбоев", t);
        }
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
