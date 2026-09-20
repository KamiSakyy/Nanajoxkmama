package com.anibeat.app.core;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Строительные блоки интерфейса: 1:1 повторяют компоненты сайта (ui.tsx / cards.tsx / index.css). */
public final class Ui {

    public static final float HAIRLINE = 0.5f;

    public interface Click {
        void onClick();
    }

    public interface Toggle {
        void onChange(boolean value);
    }

    private Ui() {
    }

    /* ------------------------------------------------------------------ */
    /* Примитивы                                                           */
    /* ------------------------------------------------------------------ */

    public static Context ctx(View view) {
        return view.getContext();
    }

    public static int dp(Context c, float v) {
        return Theme.dp(c, v);
    }

    public static float dpF(Context c, float v) {
        return Theme.dpF(c, v);
    }

    public static TextView text(Context c, String value, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(false);
        tv.setLetterSpacing(-0.01f);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        tv.setLineSpacing(0f, 1.08f);
        return tv;
    }

    public static TextView text(Context c, String value, float sizeSp, int color) {
        return text(c, value, sizeSp, color, false);
    }

    /** Заголовок крупным шрифтом (tight tracking как у h1/h2 на сайте). */
    public static TextView heading(Context c, String value, float sizeSp, int color) {
        TextView tv = text(c, value, sizeSp, color, true);
        tv.setLetterSpacing(sizeSp >= 24 ? -0.03f : -0.02f);
        return tv;
    }

    public static LinearLayout row(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        return ll;
    }

    public static LinearLayout column(Context c) {
        LinearLayout ll = new LinearLayout(c);
        ll.setOrientation(LinearLayout.VERTICAL);
        return ll;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = weight;
        return p;
    }

    public static LinearLayout.LayoutParams lpw(float weight, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, h);
        p.weight = weight;
        return p;
    }

    public static View spacer(Context c, int height) {
        View v = new View(c);
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    public static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.SEPARATOR);
        v.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, HAIRLINE))));
        return v;
    }

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable rounded(int color, float radiusPx, int strokeWidth, int strokeColor) {
        GradientDrawable d = rounded(color, radiusPx);
        if (strokeWidth > 0) d.setStroke(strokeWidth, strokeColor);
        return d;
    }

    /** Аналог @utility tap — плавное затемнение при нажатии. */
    public static void tap(View view) {
        view.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().alpha(0.6f).setDuration(120).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().alpha(1f).setDuration(160).start();
                    break;
            }
            return false;
        });
    }

    /** Аналог @utility tap-scale — сжатие 0.97 при нажатии. */
    public static void tapScale(final View view) {
        view.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(160).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
                    break;
            }
            return false;
        });
    }

    /* ------------------------------------------------------------------ */
    /* Иконки                                                              */
    /* ------------------------------------------------------------------ */

    public static ImageView icon(Context c, String name, int sizeDp, int tint) {
        ImageView iv = new ImageView(c);
        int res = c.getResources().getIdentifier("ic_" + name, "drawable", c.getPackageName());
        if (res != 0) iv.setImageResource(res);
        iv.setColorFilter(tint);
        int size = dp(c, sizeDp);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    public static ImageView logo(Context c, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(c.getResources().getIdentifier("ic_logo", "drawable", c.getPackageName()));
        int size = dp(c, sizeDp);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return iv;
    }

    /** Круглая иконочная кнопка 44×44dp (как IconButton на сайте). */
    public static FrameLayout iconButton(Context c, String name, int iconDp, int tint, Click click) {
        FrameLayout fl = new FrameLayout(c);
        fl.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44)));
        ImageView iv = icon(c, name, iconDp, tint);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(c, iconDp), dp(c, iconDp));
        ip.gravity = Gravity.CENTER;
        fl.addView(iv, ip);
        if (click != null) {
            fl.setClickable(true);
            fl.setOnClickListener(v -> click.onClick());
            tapScale(fl);
        }
        return fl;
    }

    /* ------------------------------------------------------------------ */
    /* Кнопки, чипы, теги                                                  */
    /* ------------------------------------------------------------------ */

    public static TextView button(Context c, String label, String style, Click click) {
        boolean filled = "filled".equals(style);
        boolean white = "white".equals(style);
        boolean tinted = "tinted".equals(style);
        boolean gray = "gray".equals(style);
        TextView tv = text(c, label, 15f, filled ? Theme.ON : white ? Theme.ON_PRIMARY : tinted ? Theme.PRIMARY : Theme.ON, true);
        tv.setGravity(Gravity.CENTER);
        int padH = dp(c, 20);
        tv.setPadding(padH, dp(c, 12), padH, dp(c, 12));
        tv.setBackground(rounded(filled ? Theme.PRIMARY_CONTAINER : white ? 0xFFFFFFFF : tinted ? Theme.alpha(Theme.PRIMARY, 0.16f) : gray ? Theme.SURFACE_3 : 0x00000000, dpF(c, 22)));
        if (click != null) {
            tv.setOnClickListener(v -> click.onClick());
            tapScale(tv);
        }
        return tv;
    }

    public static TextView primaryButton(Context c, String label, Click click) {
        TextView tv = text(c, label, 15f, Theme.ON_PRIMARY, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        tv.setBackground(rounded(0xFFFFFFFF, dpF(c, 22)));
        if (click != null) {
            tv.setOnClickListener(v -> click.onClick());
            tapScale(tv);
        }
        return tv;
    }

    public static TextView tintedButton(Context c, String label, Click click) {
        TextView tv = text(c, label, 15f, Theme.PRIMARY, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 18), dp(c, 11), dp(c, 18), dp(c, 11));
        tv.setBackground(rounded(Theme.alpha(Theme.PRIMARY, 0.16f), dpF(c, 22)));
        if (click != null) {
            tv.setOnClickListener(v -> click.onClick());
            tapScale(tv);
        }
        return tv;
    }

    /** Компактный маркер темы OP1 / ED2 / IN. */
    public static TextView tag(Context c, String label, String tone) {
        TextView tv = text(c, label, 10.5f, toneColor(tone), true);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.02f);
        tv.setPadding(dp(c, 6), 1, dp(c, 6), 1);
        tv.setBackground(rounded(toneBg(tone), dpF(c, 5)));
        return tv;
    }

    public static int toneColor(String tone) {
        if ("OP".equals(tone)) return Theme.PRIMARY;
        if ("ED".equals(tone)) return Theme.SECONDARY;
        if ("IN".equals(tone)) return Theme.TERTIARY;
        if ("warn".equals(tone)) return Theme.ERROR;
        return Theme.ON_VARIANT;
    }

    public static int toneBg(String tone) {
        if ("OP".equals(tone)) return Theme.alpha(Theme.PRIMARY, 0.18f);
        if ("ED".equals(tone)) return Theme.alpha(Theme.SECONDARY, 0.18f);
        if ("IN".equals(tone)) return Theme.alpha(Theme.TERTIARY, 0.18f);
        if ("warn".equals(tone)) return Theme.alpha(Theme.ERROR, 0.18f);
        return 0x1AFFFFFF;
    }

    public static TextView chip(Context c, String label, String iconName, boolean active, Click click) {
        TextView tv = text(c, label, 14f, active ? Theme.ON_PRIMARY : Theme.ON, true);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        int padH = dp(c, 16);
        tv.setPadding(padH, dp(c, 9), padH, dp(c, 9));
        tv.setBackground(rounded(active ? 0xFFFFFFFF : Theme.SURFACE_2, dpF(c, 18)));
        if (click != null) {
            tv.setOnClickListener(v -> click.onClick());
            tapScale(tv);
        }
        return tv;
    }

    /* ------------------------------------------------------------------ */
    /* Сегментированный контрол (iOS)                                      */
    /* ------------------------------------------------------------------ */

    public static class Segmented extends FrameLayout {
        private final LinearLayout track;
        private final View indicator;
        private final LinearLayout buttons;
        private int count;
        private int selected;
        public interface OnSelect {
            void onSelect(int index);
        }

        public Segmented(Context c, String[] items, int selected, OnSelect listener) {
            super(c);
            this.count = items.length;
            this.selected = selected;

            track = new LinearLayout(c);
            track.setOrientation(LinearLayout.HORIZONTAL);
            track.setBackground(rounded(0x1FFFFFFF, dpF(c, 9)));
            track.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));
            addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 32)));

            indicator = new View(c);
            indicator.setBackground(rounded(Theme.SURFACE_5, dpF(c, 7)));
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(0, dp(c, 28));
            ip.topMargin = dp(c, 2);
            addView(indicator, ip);

            buttons = row(c);
            buttons.setWeightSum(count);
            for (int i = 0; i < count; i++) {
                final int index = i;
                boolean active = i == selected;
                TextView tv = text(c, items[i], 13f, active ? Theme.ON : Theme.ON_VARIANT, true);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setOnClickListener(v -> {
                    if (this.selected == index) return;
                    this.selected = index;
                    if (listener != null) listener.onSelect(index);
                    moveIndicator(true);
                    refreshLabels();
                });
                buttons.addView(tv, lpw(1f, dp(c, 32)));
            }
            addView(buttons, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 32)));
        }

        public int getSelected() {
            return selected;
        }

        public void setSelected(int index) {
            selected = index;
            moveIndicator(false);
            refreshLabels();
        }

        private void refreshLabels() {
            for (int i = 0; i < buttons.getChildCount(); i++) {
                TextView tv = (TextView) buttons.getChildAt(i);
                tv.setTextColor(i == selected ? Theme.ON : Theme.ON_VARIANT);
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            moveIndicator(false);
        }

        private void moveIndicator(boolean animate) {
            if (getWidth() == 0) return;
            float width = (getWidth() - dp(getContext(), 4f)) / (float) count;
            float target = dp(getContext(), 2f) + selected * width;
            if (!animate) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) indicator.getLayoutParams();
                p.leftMargin = Math.round(target);
                p.width = Math.round(width);
                indicator.setLayoutParams(p);
                return;
            }
            int start = indicator.getLeft();
            ValueAnimator anim = ValueAnimator.ofFloat(start, target);
            anim.setDuration(Theme.DUR_SEGMENT);
            anim.setInterpolator(Theme.EASE_SHEET);
            anim.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) indicator.getLayoutParams();
                p.leftMargin = Math.round(value);
                p.width = Math.round(width);
                indicator.setLayoutParams(p);
            });
            anim.start();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Списки, группы, строки                                              */
    /* ------------------------------------------------------------------ */

    public static LinearLayout sectionHeader(Context c, String title, String action, Click onAction) {
        LinearLayout row = row(c);
        row.setPadding(dp(c, 16), 0, dp(c, 16), dp(c, 10));
        TextView t = heading(c, title, 20f, Theme.ON);
        row.addView(t, lpw(1f));
        if (action != null && onAction != null) {
            TextView a = text(c, action, 15f, Theme.PRIMARY, true);
            a.setOnClickListener(v -> onAction.onClick());
            tap(a);
            row.addView(a);
        }
        return row;
    }

    /** Группа-инсет в стиле iOS (ListGroup). */
    public static LinearLayout listGroup(Context c, String header, String footer) {
        LinearLayout col = column(c);
        int padH = dp(c, 16);
        col.setPadding(padH, 0, padH, 0);
        if (header != null) {
            TextView h = text(c, header, 13f, Theme.ON_VARIANT, true);
            h.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 6));
            col.addView(h);
        }
        LinearLayout card = column(c);
        card.setBackground(rounded(Theme.SURFACE_2, dpF(c, 16)));
        card.setClipToOutline(true);
        col.addView(card);
        if (footer != null) {
            TextView f = text(c, footer, 12.5f, Theme.ON_DIM);
            f.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), 0);
            col.addView(f);
        }
        col.setTag(card);
        return col;
    }

    public static LinearLayout groupBody(LinearLayout group) {
        return (LinearLayout) group.getTag();
    }

    public static LinearLayout listRow(Context c, String iconName, String label, String sub, boolean first, Click click) {
        return listRow(c, iconName, label, sub, first, click, 0x14FFFFFF);
    }

    /** Как ListRow на сайте: иконка в квадрате 28×28 r8, без шеврона. */
    public static LinearLayout listRow(Context c, String iconName, String label, String sub, boolean first, Click click, int iconColor) {
        LinearLayout row = row(c);
        row.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        if (!first) {
            row.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    rounded(0x00000000, 0),
                    hairlineDrawable(c)
            }));
        }
        if (iconName != null) {
            FrameLayout box = new FrameLayout(c);
            box.setBackground(rounded(danger ? 0x26FF6B62 : iconColor, dpF(c, 8)));
            ImageView iv = icon(c, iconName, 16, danger ? Theme.ERROR : Theme.ON);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(c, 16), dp(c, 16));
            ip.gravity = Gravity.CENTER;
            box.addView(iv, ip);
            row.addView(box, lp(dp(c, 28), dp(c, 28)));
            LinearLayout.LayoutParams boxLp = (LinearLayout.LayoutParams) box.getLayoutParams();
            boxLp.rightMargin = dp(c, 14);
            box.setLayoutParams(boxLp);
        }
        LinearLayout texts = column(c);
        texts.addView(text(c, label, 15.5f, danger ? Theme.ERROR : Theme.ON));
        if (sub != null) {
            TextView s = text(c, sub, 12.5f, Theme.ON_VARIANT);
            s.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, lpw(1f));
        row.setTag(new Object[]{iconName, texts, null});
        if (click != null) {
            row.setOnClickListener(v -> click.onClick());
            row.setClickable(true);
            tap(row);
        }
        return row;
    }

    /** Строка списка со значением справа (iOS «value row»). */
    public static LinearLayout listRow(Context c, String iconName, String label, String sub, boolean first, Click click, java.util.function.Supplier<String> value) {
        return listRow(c, iconName, label, sub, first, click, value, false);
    }

    public static LinearLayout listRow(Context c, String iconName, String label, String sub, boolean first, Click click, java.util.function.Supplier<String> value, boolean danger) {
        LinearLayout row = listRow(c, iconName, label, sub, first, click, danger ? 0x26FF6B62 : 0x14FFFFFF);
        annotate(row, c, value, danger);
        return row;
    }

    /** Строка со значением и цветом иконки (Storage-группа настроек). */
    public static LinearLayout listRow(Context c, String iconName, String label, String sub, boolean first, Click click, java.util.function.Supplier<String> value, boolean danger, int iconColor) {
        LinearLayout row = listRow(c, iconName, label, sub, first, click, iconColor);
        annotate(row, c, value, danger);
        return row;
    }

    private static void annotate(LinearLayout row, Context c, java.util.function.Supplier<String> value, boolean danger) {
        if (value != null) {
            TextView tv = text(c, value.get(), 14.5f, Theme.ON_VARIANT);
            row.addView(tv);
        }
    }


    private static android.graphics.drawable.Drawable hairlineDrawable(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Theme.SEPARATOR);
        d.setSize(1, Math.max(1, dp(c, HAIRLINE)));
        android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(d, 0, 0, 0, 0);
        inset.setTint(Theme.SEPARATOR);
        return inset;
    }

    public static LinearLayout switchRow(Context c, String iconName, String label, String sub, boolean checked, boolean first, Toggle toggle) {
        LinearLayout row = row(c);
        row.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        if (iconName != null) {
            FrameLayout box = new FrameLayout(c);
            box.setBackground(rounded(0x14FFFFFF, dpF(c, 8)));
            ImageView iv = icon(c, iconName, 16, Theme.ON);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(c, 16), dp(c, 16));
            ip.gravity = Gravity.CENTER;
            box.addView(iv, ip);
            row.addView(box, lp(dp(c, 28), dp(c, 28)));
            LinearLayout.LayoutParams boxLp = (LinearLayout.LayoutParams) box.getLayoutParams();
            boxLp.rightMargin = dp(c, 14);
            box.setLayoutParams(boxLp);
        }
        LinearLayout texts = column(c);
        texts.addView(text(c, label, 15.5f, Theme.ON));
        if (sub != null) {
            TextView s = text(c, sub, 12.5f, Theme.ON_VARIANT);
            s.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, lpw(1f));
        final boolean[] state = {checked};
        FrameLayout sw = new FrameLayout(c);
        View track = new View(c);
        track.setBackground(rounded(checked ? 0xFFFFFFFF : 0x33FFFFFF, dpF(c, 14)));
        sw.addView(track, new FrameLayout.LayoutParams(dp(c, 46), dp(c, 28)));
        View thumb = new View(c);
        thumb.setBackground(rounded(checked ? 0xFF000000 : 0xFFFFFFFF, dpF(c, 11)));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(c, 22), dp(c, 22));
        tp.topMargin = dp(c, 3);
        tp.leftMargin = checked ? dp(c, 21) : dp(c, 3);
        sw.addView(thumb, tp);
        sw.setOnClickListener(v -> {
            state[0] = !state[0];
            track.setBackground(rounded(state[0] ? 0xFFFFFFFF : 0x33FFFFFF, dpF(c, 14)));
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) thumb.getLayoutParams();
            p.leftMargin = state[0] ? dp(c, 21) : dp(c, 3);
            thumb.setLayoutParams(p);
            thumb.setBackground(rounded(state[0] ? 0xFF000000 : 0xFFFFFFFF, dpF(c, 11)));
            if (toggle != null) toggle.onChange(state[0]);
        });
        row.addView(sw, lp(dp(c, 46), dp(c, 28)));
        row.setClickable(true);
        row.setOnClickListener(v -> sw.performClick());
        return row;
    }

    /* ------------------------------------------------------------------ */
    /* Прогресс, скелетоны, состояния                                      */
    /* ------------------------------------------------------------------ */

    public static FrameLayout progressBar(Context c, float heightDp) {
        FrameLayout fl = new FrameLayout(c);
        View track = new View(c);
        track.setBackground(rounded(0x1FFFFFFF, dpF(c, heightDp / 2f)));
        fl.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, heightDp)));
        View fill = new View(c);
        fill.setBackground(rounded(0xFFFFFFFF, dpF(c, heightDp / 2f)));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(0, dp(c, heightDp));
        fl.addView(fill, fp);
        fl.setTag(fill);
        return fl;
    }

    public static void setProgress(FrameLayout bar, float percent) {
        View fill = (View) bar.getTag();
        View parent = (View) bar;
        int width = parent.getWidth();
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) fill.getLayoutParams();
        p.width = Math.round(width * Math.max(0f, Math.min(1f, percent / 100f)));
        fill.setLayoutParams(p);
    }

    /** Шиммер-заглушка (@utility skeleton). */
    public static View skeleton(Context c, int width, int height, float radiusDp) {
        View v = new View(c);
        v.setBackground(rounded(Theme.SURFACE_2, dpF(c, radiusDp)));
        v.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return v;
    }

    public static LinearLayout emptyState(Context c, String iconName, String title, String text, String action, Click onAction) {
        LinearLayout col = column(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(c, 32), dp(c, 56), dp(c, 32), dp(c, 56));
        col.addView(icon(c, iconName, 44, Theme.ON_DIM));
        LinearLayout.LayoutParams ip = (LinearLayout.LayoutParams) col.getChildAt(0).getLayoutParams();
        ip.bottomMargin = dp(c, 16);
        TextView t = heading(c, title, 19f, Theme.ON);
        col.addView(t);
        if (text != null) {
            TextView tx = text(c, text, 14f, Theme.ON_VARIANT);
            tx.setGravity(Gravity.CENTER);
            tx.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), 0);
            col.addView(tx);
        }
        if (action != null && onAction != null) {
            TextView b = tintedButton(c, action, onAction);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(c, 20);
            col.addView(b, bp);
        }
        return col;
    }

    public static LinearLayout errorState(Context c, String message, Click retry) {
        return emptyState(c, "wifi_off", "Нет соединения", message != null ? message : "Проверьте подключение и попробуйте снова.", "Повторить", retry);
    }

    /* ------------------------------------------------------------------ */
    /* Скролл                                                              */
    /* ------------------------------------------------------------------ */

    public static ScrollView scroller(Context c, LinearLayout content) {
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    public static LinearLayout horizontalScroll(Context c, int padH) {
        LinearLayout row = row(c);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row.setPadding(dp(c, padH), 0, dp(c, padH), 0);
        hs.addView(row);
        hs.setTag(row);
        return row;
    }

    /** Обёртка, чтобы HorizontalScrollView можно было вернуть как один View. */
    public static android.widget.HorizontalScrollView hscroll(Context c, LinearLayout row, int padH) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        row.setPadding(dp(c, padH), 0, dp(c, padH), 0);
        hs.addView(row);
        return hs;
    }

    /** Точка при пульсации — индикатор LIVE. */
    public static View liveDot(Context c) {
        final View dot = new View(c);
        dot.setBackground(rounded(Theme.LIVE, dpF(c, 4)));
        dot.setLayoutParams(lp(dp(c, 8), dp(c, 8)));
        ValueAnimator anim = ValueAnimator.ofFloat(0.5f, 1f);
        anim.setDuration(1000);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.addUpdateListener(a -> dot.setAlpha((float) a.getAnimatedValue()));
        anim.start();
        return dot;
    }

    public static View card(Context c, int radiusDp, int color) {
        View v = new View(c);
        v.setBackground(rounded(color, dpF(c, radiusDp)));
        return v;
    }

    public static String joinArtists(List<String> names) {
        if (names == null || names.isEmpty()) return "Неизвестный исполнитель";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        return sb.toString();
    }
}
