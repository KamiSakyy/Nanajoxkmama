package com.anibeat.app.core;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Interpolator;


/** Design tokens — точная копия палитры и кривых из сайта (index.css / @theme). */
public final class Theme {

    /* surfaces */
    public static final int BG = 0xFF000000;
    public static final int SURFACE = 0xFF000000;
    public static final int SURFACE_1 = 0xFF0B0B0C;
    public static final int SURFACE_2 = 0xFF141416;
    public static final int SURFACE_3 = 0xFF1E1E21;
    public static final int SURFACE_4 = 0xFF28282C;
    public static final int SURFACE_5 = 0xFF333338;

    /* text */
    public static final int ON = 0xFFFFFFFF;
    public static final int ON_VARIANT = 0xFF9A9AA2;
    public static final int ON_DIM = 0xFF63636B;
    public static final int OUTLINE = 0xFF3A3A40;
    public static final int OUTLINE_VARIANT = 0xFF232326;
    public static final int SEPARATOR = 0xFF26262A;

    /* accents */
    public static final int PRIMARY = 0xFFFFFFFF;
    public static final int PRIMARY_DIM = 0xFFDCDCDC;
    public static final int ON_PRIMARY = 0xFF000000;
    public static final int PRIMARY_CONTAINER = 0xFF2B2B2F;
    public static final int ACCENT = 0xFF8AB4F8;
    public static final int SECONDARY = 0xFF8E8E96;
    public static final int TERTIARY = 0xFF7EE0C0;
    public static final int ERROR = 0xFFFF6B62;
    public static final int WARNING = 0xFFFFB340;
    public static final int LIVE = 0xFFFF5F57;

    /* motion — кривые сайта из index.css / tailwind */
    public static final Interpolator EASE_OUT = bezier(0.2f, 0f, 0f, 1f);
    public static final Interpolator EASE_SPRING = bezier(0.2f, 0f, 0f, 1.2f);
    public static final Interpolator EASE_STANDARD = bezier(0.25f, 0.1f, 0.25f, 1f);
    /** cubic-bezier(0.32,0.72,0,1) — шторки, сегменты, мини-плеер, NowPlaying. */
    public static final Interpolator EASE_SHEET = bezier(0.32f, 0.72f, 0f, 1f);
    public static final long DUR_FAST = 200;
    public static final long DUR = 300;
    public static final long DUR_SEGMENT = 250;
    public static final long DUR_SHEET = 320;
    public static final long DUR_NOWPLAYING = 420;

    /** Кубическая кривая Безье как Android-интерполятор (порт CSS cubic-bezier). */
    public static Interpolator bezier(final float x1, final float y1, final float x2, final float y2) {
        return new Interpolator() {
            @Override
            public float getInterpolation(float t) {
                if (t <= 0f) return 0f;
                if (t >= 1f) return 1f;
                float lo = 0f, hi = 1f, u = t;
                for (int i = 0; i < 12; i++) {
                    u = (lo + hi) / 2f;
                    float x = curve(x1, x2, u);
                    if (x < t) lo = u; else hi = u;
                }
                return curve(y1, y2, u);
            }

            private float curve(float p1, float p2, float u) {
                float v = 1f - u;
                return 3f * v * v * u * p1 + 3f * v * u * u * p2 + u * u * u;
            }
        };
    }

    private Theme() {
    }

    public static int alpha(int color, float factor) {
        int a = Math.round(Color.alpha(color) * factor);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int mix(int base, int color, float amount) {
        int r = Math.round(Color.red(base) * (1 - amount) + Color.red(color) * amount);
        int g = Math.round(Color.green(base) * (1 - amount) + Color.green(color) * amount);
        int b = Math.round(Color.blue(base) * (1 - amount) + Color.blue(color) * amount);
        return Color.argb(255, r, g, b);
    }

    public static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics()));
    }

    public static float dpF(Context c, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics());
    }

    /** Ripple-эффект в стиле сайта (tap-scale + затемнение). */
    public static void ripple(View view) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), view.getBackground(), null));
    }

    public static int color(Context c, int res) {
        // Системный API вместо androidx.core (в библиотеке появился Kotlin).
        return c.getResources().getColor(res, c.getTheme());
    }
}
