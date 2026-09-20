package com.anibeat.app.core;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.animation.LinearInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;

/** «Играет» — пять полос с разными периодами, как `PlayingBars` на сайте. */
public class BarsView extends android.view.View {

    private static final long[] DURATIONS = {900, 1200, 800, 1050, 1350};
    private static final float[] SCALES = {0.85f, 1.0f, 0.75f, 0.95f, 1.05f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ValueAnimator[] animators = new ValueAnimator[DURATIONS.length];
    private final float[] progress = new float[DURATIONS.length];
    private int color = Theme.ON;
    private boolean paused;
    private float barWidth;
    private float gap;

    public BarsView(Context context) {
        super(context);
        barWidth = Theme.dpF(context, 2.5f);
        gap = Theme.dpF(context, 2.5f);
        for (int i = 0; i < animators.length; i++) {
            final int index = i;
            final ValueAnimator anim = ValueAnimator.ofFloat(0.25f, 1f);
            anim.setDuration(DURATIONS[i]);
            anim.setStartDelay((long) (i * 90));
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.addUpdateListener(a -> {
                progress[index] = (float) a.getAnimatedValue();
                invalidate();
            });
            animators[i] = anim;
            progress[i] = 0.4f;
        }
    }

    public void setColor(int value) {
        color = value;
        invalidate();
    }

    public void setPaused(boolean value) {
        paused = value;
        for (ValueAnimator a : animators) {
            if (value) a.pause();
            else if (!a.isRunning()) a.resume();
        }
        invalidate();
    }

    public void start() {
        for (ValueAnimator a : animators) {
            if (!a.isRunning()) a.start();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = (int) (barWidth * SCALES.length + gap * (SCALES.length - 1));
        int height = 0;
        for (float s : SCALES) height = Math.max(height, (int) (Theme.dpF(getContext(), s * 0f + 15f)));
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize((int) Theme.dpF(getContext(), 15f), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(color);
        float h = getHeight();
        float center = h / 2f;
        float x = 0;
        for (int i = 0; i < SCALES.length; i++) {
            float scale = SCALES[i] * (paused ? 0.3f : progress[i]);
            float barH = Math.max(barWidth, h * Math.min(1f, scale));
            rect.set(x, center - barH / 2f, x + barWidth, center + barH / 2f);
            float r = barWidth / 2f;
            canvas.drawRoundRect(rect, r, r, paint);
            x += barWidth + gap;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        for (ValueAnimator a : animators) a.cancel();
        super.onDetachedFromWindow();
    }

    public void destroy() {
        for (ValueAnimator a : animators) a.cancel();
    }

    /* живые бары для «live»-режима плавнее */
    static {
        new LinearInterpolator();
    }
}
