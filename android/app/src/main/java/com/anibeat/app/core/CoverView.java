package com.anibeat.app.core;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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
    private BitmapShader shader;
    private Bitmap shaderSource;
    private final Matrix shaderMatrix = new Matrix();
    private float radius;
    private boolean loading;
    private float shimmer = -1f;
    private ValueAnimator shimmerAnim;
    private float iconSize;
    private String url;
    private String waitingUrl;
    private String waitingFallback;
    private boolean loadRequested;
    private float aspect;
    private int requestedPx;

    public CoverView(Context context) {
        super(context);
        radius = Theme.dpF(context, 12f);
        iconSize = Theme.dpF(context, 24f);
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
            this.waitingUrl = null;
            this.bitmap = null;
            stopShimmer();
            invalidate();
            return;
        }
        if (url.equals(this.url) && bitmap != null) return;
        this.url = url;
        this.waitingUrl = url;
        this.waitingFallback = fallback;
        this.loadRequested = false;
        Bitmap cached = Image.cached(url);
        if (cached != null) {
            this.bitmap = cached;
            stopShimmer();
            invalidate();
            return;
        }
        this.bitmap = null;
        startShimmer();
        invalidate();
    }

    /**
     * Загружает обложку, только когда она действительно видна на экране:
     * на сайте картинки тоже подгружаются по мере появления, а не все сразу.
     */
    public void ensureLoading() {
        if (loadRequested || waitingUrl == null || getWidth() <= 0 || getHeight() <= 0) return;
        loadRequested = true;
        startLoad(waitingUrl, waitingFallback);
    }

    private void startLoad(final String url, final String fallback) {
        final int target = Math.max(getWidth(), getHeight());
        // При экономии трафика декодируем мельче — памяти и сети меньше.
        final int requestPx = Math.max(target, Theme.dp(getContext(), Image.dataSaver() ? 90f : 120f));
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
        try {
            drawCover(canvas);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void drawCover(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        rect.set(0, 0, w, h);
        // Рисуется только то, что видно на экране — значит именно здесь и запрашиваем обложку.
        if (bitmap == null) ensureLoading();

        if (bitmap != null && !bitmap.isRecycled()) {
            // Обложка рисуется шейдером с круглыми углами: без программного слоя и без обрезки —
            // так сетки карточек прокручиваются плавно, а углы остаются скруглёнными.
            if (shader == null || shaderSource != bitmap) {
                shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                shaderSource = bitmap;
            }
            float scale = Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
            float left = (w - bitmap.getWidth() * scale) / 2f;
            float top = (h - bitmap.getHeight() * scale) / 2f;
            shaderMatrix.reset();
            shaderMatrix.setScale(scale, scale);
            shaderMatrix.postTranslate(left, top);
            shader.setLocalMatrix(shaderMatrix);
            paint.setShader(shader);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
            return;
        }

        paint.setColor(Theme.SURFACE_2);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (loading) {
            LinearGradient g = new LinearGradient(
                    w * shimmer, 0, w * (shimmer + 1f), 0,
                    new int[]{Theme.SURFACE_2, Theme.SURFACE_3, Theme.SURFACE_2},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(g);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
        } else {
            iconPaint.setColor(Theme.ON_DIM);
            iconPaint.setStyle(Paint.Style.FILL);
            canvas.save();
            canvas.translate((w - iconSize) / 2f, (h - iconSize) / 2f);
            canvas.drawPath(iconPath, iconPaint);
            canvas.restore();
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        // В невидимых списках шиммер не крутится — не тратит кадры.
        if (!isVisible && loading) {
            if (shimmerAnim != null) shimmerAnim.pause();
        } else if (isVisible && loading && shimmerAnim != null && shimmerAnim.isPaused()) {
            shimmerAnim.resume();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopShimmer();
        super.onDetachedFromWindow();
    }
}
