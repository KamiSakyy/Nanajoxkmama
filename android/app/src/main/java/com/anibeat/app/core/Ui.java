package com.anibeat.app.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Мелкие утилиты: безопасный запуск кода, отчёт о сбоях, тосты, скругления. */
public final class Ui {

    private static final String TAG = "AniBeat";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;

    private Ui() {
    }

    /** Запоминаем контекст приложения: отчёт о сбое пишется в его папку. */
    public static void attach(Context context) {
        if (context != null) app = context.getApplicationContext();
    }

    public static Handler main() {
        return MAIN;
    }

    public static void post(Runnable action) {
        MAIN.post(action);
    }

    public static void safe(Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            report(t);
        }
    }

    public static void postSafe(Runnable action) {
        MAIN.post(() -> safe(action));
    }

    /** Задержанный запуск: задача не должна ронять приложение при отмене. */
    public static void postDelayed(Runnable action, long delayMs) {
        MAIN.postDelayed(() -> safe(action), delayMs);
    }

    public static void report(Throwable error) {
        try {
            Log.e(TAG, "Сбой", error);
            Context context = app;
            if (context == null) return;
            File file = new File(context.getFilesDir(), "problems.log");
            FileOutputStream out = new FileOutputStream(file, true);
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            out.write(("=== " + stamp + "\n" + Log.getStackTraceString(error) + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    public static void toast(Context context, String message) {
        if (context == null || message == null) return;
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            report(t);
        }
    }

    public static Drawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(Theme.dpF(context, radiusDp));
        return drawable;
    }

    public static Drawable roundedStroke(Context context, int color, int strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setStroke(Theme.dp(context, 1), strokeColor);
        drawable.setCornerRadius(Theme.dpF(context, radiusDp));
        return drawable;
    }

    public static Drawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** Мягкий градиент сверху вниз — фон полноэкранного плеера. */
    public static Drawable gradient(int from, int to) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{from, to});
    }

    public static void ripple(View view) {
        try {
            Drawable background = view.getBackground();
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), background, null));
        } catch (Throwable t) {
            report(t);
        }
    }

    public static void selectable(View view, boolean selected) {
        try {
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                    new ColorDrawable(selected ? Theme.SURFACE_4 : Color.TRANSPARENT), null));
        } catch (Throwable t) {
            report(t);
        }
    }
}
