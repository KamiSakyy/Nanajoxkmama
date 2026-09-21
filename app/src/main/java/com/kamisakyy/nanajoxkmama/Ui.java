package com.kamisakyy.nanajoxkmama;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Design system: the website's monochrome minimalism (pure black, rounded cards,
 * Material icons, tap-scale ripples, shimmer skeletons, equalizer bars) built from
 * platform primitives only — no AppCompat/Compose, keeping the APK tiny.
 */
public final class Ui {
    /* palette — exact website colors */
    public static final int BG = Color.rgb(0, 0, 0);
    public static final int S1 = Color.rgb(11, 11, 12);
    public static final int S2 = Color.rgb(20, 20, 22);
    public static final int S3 = Color.rgb(30, 30, 33);
    public static final int S4 = Color.rgb(40, 40, 44);
    public static final int S5 = Color.rgb(51, 51, 56);
    public static final int ON = Color.WHITE;
    public static final int VAR = Color.rgb(154, 154, 162);
    public static final int DIM = Color.rgb(99, 99, 107);
    public static final int OUTLINE = Color.rgb(58, 58, 64);
    public static final int SEPARATOR = Color.rgb(38, 38, 42);
    public static final int ACCENT = Color.rgb(138, 180, 248);
    public static final int TAG_OP = Color.rgb(138, 180, 248);
    public static final int TAG_ED = Color.rgb(142, 142, 150);
    public static final int TAG_IN = Color.rgb(126, 224, 192);
    public static final int ERR = Color.rgb(255, 107, 98);
    public static final int WARN = Color.rgb(255, 179, 64);
    public static final int GREEN = Color.rgb(48, 209, 88);
    public static final int LIVE = Color.rgb(255, 95, 87);
    public static final int STAR = Color.rgb(255, 159, 10);

    private Ui() { }

    /* ---------------- primitives ---------------- */

    public static int dp(float v) {
        return (int) (v * Resources.getSystem().getDisplayMetrics().density + 0.5f);
    }

    public static int sp(float v) {
        return (int) (v * Resources.getSystem().getDisplayMetrics().scaledDensity + 0.5f);
    }

    public static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radiusDp));
        d.setColor(color);
        return d;
    }

    public static GradientDrawable stroke(int strokeColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radiusDp));
        d.setColor(Color.TRANSPARENT);
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    /** Content + ripple (Material touch feedback). */
    public static RippleDrawable ripple(GradientDrawable content) {
        GradientDrawable mask = rounded(Color.WHITE, 20);
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF), content, mask);
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void tapScale(View v) {
        v.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.9f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150)
                            .setInterpolator(new DecelerateInterpolator()).start();
                    break;
            }
            return false;
        });
    }

    public static TextView text(Activity a, CharSequence s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static ImageView icon(Activity a, String name, float sizeDp, int color) {
        ImageView v = new ImageView(a);
        int id = a.getResources().getIdentifier("ic_" + name, "drawable", a.getPackageName());
        if (id != 0) v.setImageResource(id);
        int size = dp(sizeDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        v.setLayoutParams(lp);
        v.setColorFilter(color);
        return v;
    }

    public static View iconBtn(Activity a, String name, float sizeDp, int color, View.OnClickListener click) {
        ImageView v = icon(a, name, sizeDp, color);
        FrameLayout btn = new FrameLayout(a);
        int btnSize = dp(42);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
        btn.setLayoutParams(lp);
        btn.setBackground(ripple(oval(Color.TRANSPARENT)));
        btn.addView(v, new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER));
        btn.setOnClickListener(click);
        tapScale(btn);
        return btn;
    }

    public static View line(Activity a) {
        View v = new View(a);
        v.setBackgroundColor(SEPARATOR);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0.6f)));
        return v;
    }

    public static LinearLayout hstack(int gravity) {
        LinearLayout l = new LinearLayout(null);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(gravity);
        return l;
    }

    public static LinearLayout vstack() {
        LinearLayout l = new LinearLayout(null);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /* ---------------- cover ---------------- */

    /** Rounded cover with placeholder + fade, follows the website's Cover component. */
    public static class CoverView extends FrameLayout {
        private final ImageView img;
        private final ImageView ph;
        private float radiusDp = 12f;

        public CoverView(Activity a) {
            super(a);
            img = new ImageView(a);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ph = new ImageView(a);
            int placeholder = a.getResources().getIdentifier("ic_music_placeholder", "drawable", a.getPackageName());
            if (placeholder != 0) ph.setImageResource(placeholder);
            ph.setColorFilter(DIM);
            ph.setScaleType(ImageView.ScaleType.CENTER);
            addView(img, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(ph, new LayoutParams(dp(28), dp(28), Gravity.CENTER));
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radiusDp));
                }
            });
            setClipToOutline(true);
            setBackgroundColor(S2);
        }

        public CoverView radius(float r) {
            radiusDp = r;
            invalidateOutline();
            return this;
        }

        public CoverView circle() {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            setClipToOutline(true);
            return this;
        }

        public void load(String url) {
            load(url, false);
        }

        public void load(String url, boolean small) {
            ph.setVisibility(View.VISIBLE);
            img.setImageDrawable(null);
            if (url == null || url.isEmpty()) return;
            ImageLoader.load(img, url, small, () -> ph.setVisibility(View.GONE));
        }
    }

    public static CoverView cover(Activity a, int sizeDp, float radiusDp) {
        CoverView c = new CoverView(a);
        c.radius(radiusDp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        c.setLayoutParams(lp);
        return c;
    }

    /* ---------------- chips / buttons ---------------- */

    public static View chip(Activity a, String label, boolean active, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(16), 0, dp(16), 0);
        l.setMinimumHeight(dp(36));
        l.setBackground(ripple(rounded(active ? ON : S2, 20)));
        TextView t = text(a, label, 13.5f, active ? Color.BLACK : ON, false);
        l.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        l.setOnClickListener(click);
        tapScale(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        lp.rightMargin = dp(8);
        l.setLayoutParams(lp);
        return l;
    }

    /** Small theme marker: OP1 / ED2 / IN. */
    public static View tag(Activity a, String label, int toneColor) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(10f);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(toneColor);
        t.setPadding(dp(7), dp(1), dp(7), dp(1));
        GradientDrawable bg = rounded(Color.argb(0x2E, Color.red(toneColor), Color.green(toneColor), Color.blue(toneColor)), 5);
        t.setBackground(bg);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public static int tagColor(String type) {
        if ("OP".equals(type)) return TAG_OP;
        if ("ED".equals(type)) return TAG_ED;
        return TAG_IN;
    }

    /** Big white pill button ("Слушать"). */
    public static View button(Activity a, String label, String iconName, boolean filled, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        l.setMinimumHeight(dp(44));
        l.setPadding(dp(22), 0, dp(22), 0);
        l.setBackground(ripple(rounded(filled ? ON : S2, 24)));
        if (iconName != null) {
            ImageView iv = icon(a, iconName, 18, filled ? Color.BLACK : ON);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
            lp.rightMargin = dp(6);
            l.addView(iv);
        }
        l.addView(text(a, label, 15, filled ? Color.BLACK : ON, true));
        l.setOnClickListener(click);
        tapScale(l);
        l.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        return l;
    }

    /** Gray surface button used across play bars. */
    public static View surfaceButton(Activity a, String label, String iconName, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        l.setMinimumHeight(dp(40));
        l.setBackground(ripple(rounded(S2, 12)));
        if (iconName != null) {
            ImageView iv = icon(a, iconName, 18, ON);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
            lp.rightMargin = dp(6);
            l.addView(iv);
        }
        l.addView(text(a, label, 14.5f, ON, true));
        l.setOnClickListener(click);
        tapScale(l);
        return l;
    }

    /* ---------------- segmented control ---------------- */

    public static class Segmented extends FrameLayout {
        private final LinearLayout labels;
        private final View pill;
        private int selected = 0;
        private String[] values;

        public Segmented(Activity a, String[] items, String current, Consumer onChange) {
            this(a, items, null, current, onChange);
        }

        public Segmented(Activity a, String[] items, String[] labelsArr, String current, Consumer onChange) {
            super(a);
            values = items;
            setBackground(rounded(0x1FFFFFFF, 10));
            labels = new LinearLayout(a);
            labels.setOrientation(LinearLayout.HORIZONTAL);
            addView(labels, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            pill = new View(a);
            pill.setBackground(rounded(S5, 8));
            for (int i = 0; i < items.length; i++) {
                if (items[i].equals(current)) selected = i;
            }
            addView(pill);
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                String text = labelsArr != null ? labelsArr[i] : label(items[i]);
                TextView t = text(a, text, 12.5f, ON, true);
                t.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
                labels.addView(t, lp);
                t.setOnClickListener(v -> {
                    if (selected == idx) return;
                    selected = idx;
                    refreshColors();
                    animatePill();
                    onChange.accept(values[idx]);
                });
            }
            post(this::animatePill);
        }

        private String label(String v) {
            if ("today".equals(v)) return "Сегодня";
            if ("week".equals(v)) return "Неделя";
            if ("all".equals(v)) return "Всё время";
            return v;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            animatePill();
        }

        private void animatePill() {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0 || values.length == 0) return;
            int seg = w / values.length;
            int pw = seg - dp(4);
            ViewGroup.LayoutParams lp = pill.getLayoutParams();
            lp.width = pw;
            lp.height = h - dp(4);
            pill.setLayoutParams(lp);
            pill.animate().x(dp(2) + selected * seg).y(dp(2))
                    .setDuration(250).setInterpolator(new DecelerateInterpolator(1.4f)).start();
            refreshColors();
        }

        private void refreshColors() {
            for (int i = 0; i < labels.getChildCount(); i++) {
                TextView t = (TextView) labels.getChildAt(i);
                t.setTextColor(i == selected ? ON : VAR);
            }
        }

        public String selected() {
            return values[selected];
        }
    }

    public interface Consumer { void accept(String value); }

    /* ---------------- slider (website ios-slider look) ---------------- */

    public static class SliderView extends View {
        private float progress;       // 0..1
        private float shown = -1;     // scrub preview
        private boolean scrubbing;
        private float trackH = 5f;
        private float thumb = 0f;
        public interface OnSeek { void onSeek(float fraction); void onScrub(float fraction); }
        private OnSeek seekListener;

        public SliderView(Activity a) {
            super(a);
            setMinimumHeight(dp(28));
        }

        public SliderView compact() {
            trackH = 3.5f;
            thumb = 5f;
            return this;
        }

        public void setListener(OnSeek l) { seekListener = l; }

        public void setProgress(float p) {
            progress = Math.max(0f, Math.min(1f, p));
            if (!scrubbing) invalidate();
        }

        private float shownProgress() {
            return scrubbing && shown >= 0 ? shown : progress;
        }

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override protected void onDraw(Canvas canvas) {
            float cy = getHeight() / 2f;
            float th = dp(trackH);
            RectF track = new RectF(0, cy - th / 2, getWidth(), cy + th / 2);
            trackPaint.setColor(0x29FFFFFF);
            canvas.drawRoundRect(track, th / 2, th / 2, trackPaint);
            float w = getWidth() * shownProgress();
            RectF fill = new RectF(0, cy - th / 2, Math.max(th, w), cy + th / 2);
            fillPaint.setColor(Color.WHITE);
            canvas.drawRoundRect(fill, th / 2, th / 2, fillPaint);
            if (thumb > 0f) {
                thumbPaint.setColor(Color.WHITE);
                canvas.drawCircle(w, cy, dp(thumb), thumbPaint);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isEnabled()) return false;
            float frac = Math.max(0f, Math.min(1f, event.getX() / Math.max(1, getWidth())));
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scrubbing = true;
                    shown = frac;
                    if (seekListener != null) seekListener.onScrub(frac);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    shown = frac;
                    if (seekListener != null) seekListener.onScrub(frac);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scrubbing = false;
                    if (seekListener != null) seekListener.onSeek(frac);
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    /* ---------------- equalizer bars ---------------- */

    public static class EqView extends View {
        private final List<ValueAnimator> anims = new ArrayList<>();
        private final float[] phase = {0.3f, 1f, 0.55f, 0.85f, 0.25f};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean running = true;

        public EqView(Activity a) {
            super(a);
            paint.setColor(Color.WHITE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                ValueAnimator anim = ValueAnimator.ofFloat(0.25f, 1f, 0.25f);
                anim.setDuration(800 + i * 130L);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.setRepeatMode(ValueAnimator.REVERSE);
                anim.setStartDelay(i * 100L);
                anim.addUpdateListener(v -> {
                    phase[idx] = (float) v.getAnimatedValue();
                    invalidate();
                });
                anim.start();
                anims.add(anim);
            }
        }

        public void setPaused(boolean paused) {
            running = !paused;
            for (ValueAnimator a : anims) {
                if (paused) a.pause();
                else a.resume();
            }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth() / 5f;
            float h = getHeight();
            paint.setStrokeWidth(dp(2.4f));
            for (int i = 0; i < 5; i++) {
                float bh = h * (running ? phase[i] : 0.3f);
                float x = w * i + w / 2;
                canvas.drawLine(x, h, x, h - bh, paint);
            }
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            for (ValueAnimator a : anims) a.cancel();
        }
    }

    /* ---------------- shimmer skeleton ---------------- */

    public static class ShimmerView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float offset;
        private float radiusDp = 12;
        private final ValueAnimator anim;

        public ShimmerView(Activity a) {
            super(a);
            setBackgroundColor(S2);
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1400);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(v -> {
                offset = (float) v.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        public ShimmerView radius(float r) {
            radiusDp = r;
            return this;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            float x = offset * (w * 2) - w * 0.6f;
            LinearGradient g = new LinearGradient(x, 0, x + w * 0.55f, h,
                    new int[]{0x001D1D20, 0x55232326, 0x001D1D20}, null, Shader.TileMode.CLAMP);
            paint.setShader(g);
            RectF r = new RectF(0, 0, w, h);
            canvas.drawRoundRect(r, dp(radiusDp), dp(radiusDp), paint);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            anim.cancel();
        }
    }

    public static ShimmerView skeleton(Activity a, int wDp, int hDp, float radius) {
        ShimmerView v = new ShimmerView(a);
        v.radius(radius);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(hDp));
        v.setLayoutParams(lp);
        return v;
    }

    /* ---------------- section header ---------------- */

    public static View sectionHeader(Activity a, String title, String action, View.OnClickListener onAction) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(16), dp(6), dp(12), dp(8));
        TextView t = text(a, title, 19, ON, true);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (action != null) {
            TextView act = text(a, action, 14, ACCENT, true);
            act.setPadding(dp(8), dp(8), dp(4), dp(8));
            act.setOnClickListener(onAction);
            l.addView(act);
        }
        return l;
    }

    /* ---------------- hscroll ---------------- */

    public static HorizontalScrollView hscroll(Activity a) {
        HorizontalScrollView h = new HorizontalScrollView(a);
        h.setHorizontalScrollBarEnabled(false);
        h.setFillViewport(false);
        h.setClipToPadding(false);
        h.setPadding(dp(12), dp(2), dp(12), dp(4));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        h.addView(row);
        return h;
    }

    public static LinearLayout hscrollRow(HorizontalScrollView h) {
        return (LinearLayout) h.getChildAt(0);
    }

    /* ---------------- cards ---------------- */

    public static View animeCard(Activity a, Ui.Host host, AnimeInfo info) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(116), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        l.setLayoutParams(lp);
        CoverView cover = cover(a, 116, 12);
        l.addView(cover);
        cover.load(Store.isDataSaverEnabled() && !info.coverSmall.isEmpty() ? info.coverSmall : info.cover);
        TextView title = text(a, info.displayTitle(), 12.5f, ON, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setSingleLine(false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        l.addView(title, tp);
        l.setOnClickListener(v -> host.openAnime(info.slug, info.displayTitle()));
        tapScale(l);
        return l;
    }

    public static View artistCard(Activity a, Ui.Host host, ArtistInfo info) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        l.setLayoutParams(lp);
        CoverView cover = cover(a, 84, 42).circle();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(84), dp(84));
        cover.setLayoutParams(cp);
        l.addView(cover);
        cover.load(!info.imageSmall.isEmpty() ? info.imageSmall : info.image);
        TextView name = text(a, info.name, 12f, ON, false);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setMaxLines(2);
        name.setSingleLine(false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        l.addView(name, tp);
        if (info.slug != null && !info.slug.isEmpty()) {
            l.setOnClickListener(v -> host.openArtist(info.slug));
        }
        tapScale(l);
        return l;
    }

    /** Gradient-free minimal mix card (title + subtitle + play). */
    public static View mixCard(Activity a, Ui.Host host, Util.Mix mix, Runnable onPlay) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        int w = dp(200);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, dp(92));
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        l.setLayoutParams(lp);
        l.setPadding(dp(16), dp(12), dp(10), dp(12));
        l.setBackground(ripple(rounded(S2, 16)));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textCol = new LinearLayout(a);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(a, mix.title, 16f, ON, true);
        TextView sub = text(a, mix.subtitle, 12f, VAR, false);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.topMargin = dp(3);
        textCol.addView(title);
        textCol.addView(sub, sp2);
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(iconBtn(a, "play_arrow", 20, ON, v -> onPlay.run()));
        l.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        l.setOnClickListener(v -> onPlay.run());
        tapScale(l);
        return l;
    }

    /** Square track card for hscroll rails. */
    public static View trackCard(Activity a, Ui.Host host, Track t, List<Track> context) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(138), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        l.setLayoutParams(lp);
        CoverView cover = cover(a, 138, 14);
        cover.load(!t.coverSmall.isEmpty() ? t.coverSmall : t.cover);
        l.addView(cover);
        TextView title = text(a, t.title, 13f, ON, true);
        title.setMaxLines(1);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(6);
        l.addView(title, tp);
        TextView sub = text(a, t.animeName, 11.5f, VAR, false);
        sub.setMaxLines(1);
        l.addView(sub, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        l.setOnClickListener(v -> host.playAll(context, indexOf(context, t), false));
        l.setOnLongClickListener(v -> {
            host.openTrackMenu(t, context);
            return true;
        });
        tapScale(l);
        return l;
    }

    private static int indexOf(List<Track> list, Track t) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(t.id)) return i;
        return 0;
    }

    /* ---------------- track row ---------------- */

    private static final CopyOnWriteArrayList<WeakReference<TrackRow>> ROWS = new CopyOnWriteArrayList<>();

    public static void refreshPlayingRows() {
        for (WeakReference<TrackRow> ref : ROWS) {
            TrackRow row = ref.get();
            if (row == null) ROWS.remove(ref);
            else row.refreshPlaying();
        }
    }

    public static class TrackRow extends LinearLayout {
        private final TrackRowData data;
        private final EqView eq;
        private final ImageView coverImg;
        private final CoverView coverView;

        public TrackRow(Activity a, Ui.Host host, Track t, List<Track> context, boolean showAnime, boolean showVersion, Runnable onRemove) {
            super(a);
            data = new TrackRowData();
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(16), dp(8), dp(6), dp(8));
            setBackground(ripple(rounded(Color.TRANSPARENT, 12)));

            coverView = cover(a, 46, 9);
            LayoutParams cp = new LayoutParams(dp(46), dp(46));
            addView(coverView, cp);
            coverImg = null;
            coverView.load(!t.coverSmall.isEmpty() ? t.coverSmall : t.cover, true);

            LinearLayout mid = new LinearLayout(a);
            mid.setOrientation(VERTICAL);
            LayoutParams mp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mp.leftMargin = dp(12);
            mp.rightMargin = dp(6);
            addView(mid, mp);

            LinearLayout titleRow = new LinearLayout(a);
            titleRow.setOrientation(HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = text(a, t.title, 15f, ON, false);
            LayoutParams tp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleRow.addView(title, tp);
            eq = new EqView(a);
            LayoutParams ep = new LayoutParams(dp(14), dp(14));
            ep.leftMargin = dp(8);
            eq.setLayoutParams(ep);
            eq.setVisibility(GONE);
            titleRow.addView(eq);
            mid.addView(titleRow);

            String sub = t.displayArtist();
            if (showAnime) sub += " · " + t.animeName;
            TextView subView = text(a, sub, 12.5f, VAR, false);
            LayoutParams spp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            spp.topMargin = dp(1);
            mid.addView(subView, spp);

            LinearLayout right = new LinearLayout(a);
            right.setOrientation(HORIZONTAL);
            right.setGravity(Gravity.CENTER_VERTICAL);
            addView(right);

            if (showVersion && t.version > 0) {
                right.addView(tag(a, "v" + t.version, VAR));
            }
            String themeLabel = t.themeTag();
            right.addView(tag(a, themeLabel, tagColor(t.type)));

            if (onRemove != null) {
                right.addView(iconBtn(a, "close", 16, DIM, v -> onRemove.run()));
            } else {
                right.addView(iconBtn(a, "more_horiz", 18, DIM, v -> host.openTrackMenu(t, context)));
            }

            data.track = t;
            data.context = context;
            data.host = host;
            setOnClickListener(v -> {
                if (Store.getCurrentTrack() != null && Store.getCurrentTrack().id.equals(t.id)) {
                    host.openNowPlaying();
                } else {
                    host.playAll(data.context, indexOf(data.context, data.track), false);
                }
            });
            setOnLongClickListener(v -> {
                host.openTrackMenu(t, context);
                return true;
            });
            tapScale(this);
            ROWS.add(new WeakReference<>(this));
            refreshPlaying();
        }

        public void refreshPlaying() {
            Track current = Store.getCurrentTrack();
            boolean playing = current != null && current.id.equals(data.track.id);
            eq.setVisibility(playing ? VISIBLE : GONE);
            eq.setPaused(!Store.isPlaying());
        }
    }

    private static final class TrackRowData {
        Track track;
        List<Track> context;
        Ui.Host host;
    }

    /* ---------------- states ---------------- */

    public static View emptyState(Activity a, String iconName, String title, String text, String action, View.OnClickListener onAction) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(32), dp(48), dp(32), dp(32));
        ImageView iv = icon(a, iconName, 42, DIM);
        l.addView(iv);
        TextView t1 = text(a, title, 17, ON, true);
        t1.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p1.topMargin = dp(14);
        l.addView(t1, p1);
        TextView t2 = text(a, text, 13.5f, VAR, false);
        t2.setGravity(Gravity.CENTER_HORIZONTAL);
        t2.setMaxLines(3);
        t2.setSingleLine(false);
        t2.setEllipsize(null);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p2.topMargin = dp(6);
        l.addView(t2, p2);
        if (action != null) {
            View b = button(a, action, null, false, onAction);
            LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p3.topMargin = dp(18);
            l.addView(b, p3);
        }
        return l;
    }

    public static View errorState(Activity a, String message, View.OnClickListener onRetry) {
        return emptyState(a, "wifi_off", "Что-то пошло не так",
                message == null || message.isEmpty() ? "Проверьте соединение с интернетом." : message,
                "Повторить", onRetry);
    }

    /* ---------------- menus (bottom-sheet rows) ---------------- */

    public static View menuItem(Activity a, String iconName, String label, String sub, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(20), dp(12), dp(16), dp(12));
        l.setBackground(ripple(rounded(Color.TRANSPARENT, 10)));
        ImageView iv = icon(a, iconName, 21, ON);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(21), dp(21));
        ip.rightMargin = dp(14);
        l.addView(iv, ip);
        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.addView(text(a, label, 15.5f, ON, false));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(a, sub, 12.5f, VAR, false);
            s.setMaxLines(1);
            mid.addView(s);
        }
        l.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        l.setOnClickListener(click);
        tapScale(l);
        return l;
    }

    /* ---------------- host contract for views ---------------- */

    public interface Host {
        Activity activity();
        void openAnime(String slug, String title);
        void openArtist(String slug);
        void openYear(int year, String season);
        void openPlaylist(String id);
        void openSearch(String query);
        void openNowPlaying();
        void openTrackMenu(Track t, List<Track> context);
        void openPlaylistPicker(Track t);
        void openQueue();
        void openSettings();
        void playAll(List<Track> tracks, int index, boolean shuffle);
        void toast(String message);
        void switchTab(int tab);
        void runIo(Runnable r);
    }
}
