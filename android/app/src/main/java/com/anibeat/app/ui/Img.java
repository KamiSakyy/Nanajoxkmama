package com.anibeat.app.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.anibeat.app.core.Theme;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;

/**
 * Обложки на Glide: картинка грузится только когда её плитка реально на экране,
 * ровно под размер плитки, и берётся из дискового кэша при повторном показе.
 */
public final class Img {

    private static boolean dataSaver;
    private static final long LIMIT_NORMAL = 120L * 1024 * 1024;
    private static final long LIMIT_SAVER = 40L * 1024 * 1024;

    private Img() {
    }

    public static void setDataSaver(boolean value) {
        dataSaver = value;
        if (value) {
            // При экономии трафика ужимаем кэш картинок.
            trim(null);
        }
    }

    public static boolean dataSaver() {
        return dataSaver;
    }

    /** Размер запроса в пикселях: при экономии трафика картинка качается мельче. */
    public static int size(Context context, float dp) {
        float value = dataSaver ? dp * 0.72f : dp;
        return Math.max(48, Theme.dp(context, value));
    }

    public static void load(ImageView view, String url, int px) {
        load(view, url, px, 0f, false);
    }

    public static void loadRounded(ImageView view, String url, int px, float radiusDp) {
        load(view, url, px, radiusDp, false);
    }

    public static void loadCircle(ImageView view, String url, int px) {
        load(view, url, px, 0f, true);
    }

    private static void load(ImageView view, String url, int px, float radiusDp, boolean circle) {
        if (view == null) return;
        Drawable placeholder = new ColorDrawable(Theme.SURFACE_4);
        try {
            if (url == null || url.isEmpty()) {
                view.setImageDrawable(placeholder);
                return;
            }
            Context context = view.getContext();
            RequestOptions options = new RequestOptions()
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(placeholder)
                    .error(placeholder);
            if (px > 0) options = options.override(px, px);
            if (circle) {
                options = options.bitmapTransform(new CircleCrop());
            } else if (radiusDp > 0) {
                options = options.bitmapTransform(new RoundedCorners(Theme.dp(context, radiusDp)));
            }
            Glide.with(view)
                    .load(url)
                    .apply(options)
                    .transition(DrawableTransitionOptions.withCrossFade(120))
                    .into(view);
        } catch (Throwable t) {
            com.anibeat.app.core.Ui.report(t);
            try {
                view.setImageDrawable(placeholder);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void clear(ImageView view) {
        try {
            if (view != null) Glide.with(view).clear(view);
        } catch (Throwable ignored) {
        }
    }

    public static long cacheSize(Context context) {
        if (context == null) return 0L;
        return folderSize(new File(context.getApplicationContext().getCacheDir(), "image_manager_disk_cache"));
    }

    /** Ужимает кэш картинок, если он разросся. Работает в фоне. */
    public static void trim(final Context context) {
        final Context ctx = context;
        new Thread(() -> {
            try {
                Context target = ctx;
                if (target == null) target = appContext;
                if (target == null) return;
                long limit = dataSaver ? LIMIT_SAVER : LIMIT_NORMAL;
                if (cacheSize(target) > limit) {
                    Glide.get(target.getApplicationContext()).clearDiskCache();
                }
            } catch (Throwable ignored) {
            }
        }, "img-trim").start();
    }

    private static Context appContext;

    public static void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    private static long folderSize(File dir) {
        long size = 0L;
        try {
            File[] files = dir.listFiles();
            if (files == null) return 0L;
            for (File file : files) {
                size += file.isDirectory() ? folderSize(file) : file.length();
            }
        } catch (Throwable ignored) {
        }
        return size;
    }
}
