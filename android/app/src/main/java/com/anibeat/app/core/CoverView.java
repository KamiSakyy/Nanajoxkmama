package com.anibeat.app.core;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.animation.LinearInterpolator;

/**
 * Обложка трека/аниме: скруглённый контейнер, скелетон-шиммер до загрузки
 * и иконка-заглушка, если картинки нет — как `Cover` на сайте.
 */
public class CoverView extends android.view.View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();
    private final RectF rect = new RectF();
    private Bitmap bitmap;
    private float radius;
    private boolean loading;
    private float shimmer = -1f;
    private ValueAnimator shimmerAnim;
    private float iconSize;
    private String url;
    private float aspect;
    private int requestedPx;

    public CoverView(Context context) {
        super(context);
        radius = Theme.dpF(context, 12f);
        iconSize = Theme.dpF(context, 24f);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        buildMusicNote();
    }

    /** Соотношение сторон (ширина/высота); 0 — размеры задаёт родитель. */
    public void setAspect(float value) {
        aspect = value;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (aspect > 0f) {
            int width = MeasureSpec.getSize(widthSpec);
            if (width <= 0) width = getSuggestedMinimumWidth();
            int height = Math.round(width / aspect);
            setMeasuredDimension(width, height);
            return;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int target = Math.max(w, h);
        if (url != null && target > Math.max(requestedPx, 1) * 3 / 2 && target > 0) {
            String again = url;
            this.url = null;
            setUrl(again, null);
        }
    }

    public void setRadiusDp(float dp) {
        radius = Theme.dpF(getContext(), dp);
        invalidate();
    }

    public void setIconSizeDp(float dp) {
        iconSize = Theme.dpF(getContext(), dp);
        buildMusicNote();
        invalidate();
    }

    public void setUrl(String url, String fallback) {
        if (url == null || url.isEmpty()) {
            if (fallback != null && !fallback.isEmpty()) {
                setUrl(fallback, null);
                return;
            }
            this.url = null;
            this.bitmap = null;
            stopShimmer();
            invalidate();
            return;
        }
        if (url.equals(this.url) && bitmap != null) return;
        this.url = url;
        Bitmap cached = Image.cached(url);
        if (cached != null) {
            this.bitmap = cached;
            stopShimmer();
            invalidate();
            return;
        }
        this.bitmap = null;
        startShimmer();
        final int target = Math.max(getWidth(), getHeight());
        final int requestPx = Math.max(target, Theme.dp(getContext(), 120f));
        requestedPx = requestPx;
        Image.load(url, requestPx, new Image.Listener() {
            @Override
            public void onBitmap(Bitmap b) {
                if (!url.equals(CoverView.this.url)) return;
                bitmap = b;
                stopShimmer();
                invalidate();
            }

            @Override
            public void onError() {
                if (!url.equals(CoverView.this.url)) return;
                if (fallback != null && !fallback.isEmpty()) {
                    CoverView.this.url = null;
                    setUrl(fallback, null);
                    return;
                }
                stopShimmer();
                invalidate();
            }
        });
    }

    private void buildMusicNote() {
        iconPath.reset();
        float s = iconSize / 24f;
        iconPath.moveTo(9.6f * s, 17.4f * s);
        iconPath.addCircle(6.9f * s, 17.4f * s, 2.7f * s, Path.Direction.CW);
        iconPath.moveTo(9.6f * s, 17.4f * s);
        iconPath.lineTo(9.6f * s, 6.2f * s);
        iconPath.lineTo(20f * s, 3.9f * s);
        iconPath.lineTo(20f * s, 15f * s);
        iconPath.addCircle(17.3f * s, 15f * s, 2.7f * s, Path.Direction.CW);
        iconPath.moveTo(9.6f * s, 17.4f * s);
        iconPath.close();
    }

    private void startShimmer() {
        if (shimmerAnim != null) return;
        loading = true;
        shimmerAnim = ValueAnimator.ofFloat(-1f, 1f);
        shimmerAnim.setDuration(1400);
        shimmerAnim.setRepeatCount(ValueAnimator.INFINITE);
        shimmerAnim.setInterpolator(new LinearInterpolator());
        shimmerAnim.addUpdateListener(a -> {
            shimmer = (float) a.getAnimatedValue();
            invalidate();
        });
        shimmerAnim.start();
    }

    private void stopShimmer() {
        loading = false;
        if (shimmerAnim != null) {
            shimmerAnim.cancel();
            shimmerAnim = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        canvas.save();
        Path clip = new Path();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);

        boolean drawn = false;
        if (bitmap != null && !bitmap.isRecycled()) {
            float scale = Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            float left = (getWidth() - w) / 2f;
            float top = (getHeight() - h) / 2f;
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + w, top + h), paint);
            drawn = true;
        }

        if (!drawn) {
            paint.setColor(Theme.SURFACE_2);
            canvas.drawRect(rect, paint);
            if (loading) {
                float w = getWidth();
                LinearGradient g = new LinearGradient(
                        w * shimmer, 0, w * (shimmer + 1f), 0,
                        new int[]{Theme.SURFACE_2, Theme.SURFACE_3, Theme.SURFACE_2},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                paint.setShader(g);
                canvas.drawRect(rect, paint);
                paint.setShader(null);
            } else {
                iconPaint.setColor(Theme.ON_DIM);
                iconPaint.setStyle(Paint.Style.FILL);
                canvas.drawPath(iconPath, iconPaint);
            }
        }
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopShimmer();
        super.onDetachedFromWindow();
    }
}
