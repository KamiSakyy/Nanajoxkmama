package com.anibeat.app;

import android.app.Application;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

/**
 * Записывает сбои в файл, чтобы причину можно было найти и устранить.
 * Автоперезапуска нет: приложение не должно выглядеть как бесконечно вылетающее.
 */
public class AniBeatApp extends Application {

    private static final String TAG = "AniBeat";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler fallback = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(thread.getName(), error);
            Log.e(TAG, "Сбой", error);
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
}
