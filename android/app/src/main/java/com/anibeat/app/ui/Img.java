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

    /**
     * Размер запроса в пикселях. Считается от реального размера на экране с запасом
     * под плотность экрана — картинка не растягивается и не выглядит сжатой.
     */
    public static int size(Context context, float dp) {
        return size(context, dp, 1.6f);
    }

    /** Размер запроса в пикселях для крупных поверхностей (обложка в плеере и т. п.). */
    public static int sizeLarge(Context context, float dp) {
        return size(context, dp, 2.4f);
    }

    private static int size(Context context, float dp, float reserve) {
        float px = Theme.dp(context, dp) * reserve;
        return (int) Math.max(96f, px);
    }

    /** Размер по фактической ширине уже отрисованной вьюхи. */
    public static int sizeOf(android.view.View view) {
        if (view == null) return 0;
        int width = view.getWidth(), height = view.getHeight();
        if (width <= 0 || height <= 0) return 0;
        int side = Math.max(width, height);
        return (int) (side * 1.25f);
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

    private static final int TAG_URL = 0x7f0e0001;
    private static final int TAG_SIZE = 0x7f0e0002;

    private static void load(ImageView view, String url, int px, float radiusDp, boolean circle) {
        if (view == null) return;
        try {
            if (url == null || url.isEmpty()) {
                if (!(view.getDrawable() instanceof ColorDrawable)) view.setImageDrawable(empty(view));
                view.setTag(TAG_URL, null);
                return;
            }
            // Та же картинка уже стоит — ничего не трогаем: никакого мигания и лишнего трафика.
            Object same = view.getTag(TAG_URL);
            Object samePx = view.getTag(TAG_SIZE);
            if (url.equals(same) && samePx != null && samePx.equals(px)) return;
            view.setTag(TAG_URL, url);
            view.setTag(TAG_SIZE, px);

            Context context = view.getContext();
            Drawable current = view.getDrawable();
            // Заглушку показываем только там, где картинки ещё нет, иначе будет вспышка.
            Drawable placeholder = current != null ? current : empty(view);
            RequestOptions options = new RequestOptions()
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .error(empty(view));
            if (px > 0) options = options.override(px, px);
            if (circle) {
                options = options.bitmapTransform(new CircleCrop());
            } else if (radiusDp > 0) {
                options = options.bitmapTransform(new RoundedCorners(Theme.dp(context, radiusDp)));
            }
            Glide.with(view)
                    .load(url)
                    .apply(options)
                    .dontAnimate()
                    .into(view);
        } catch (Throwable t) {
            com.anibeat.app.core.Ui.report(t);
        }
    }

    /** Нейтральная подложка вместо чёрного провала. */
    private static Drawable empty(ImageView view) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.SURFACE_4, Theme.SURFACE_3});
        return drawable;
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
