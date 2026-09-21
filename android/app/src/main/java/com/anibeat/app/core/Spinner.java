package com.anibeat.app.core;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.animation.LinearInterpolator;

/** Крутящийся индикатор загрузки (Spinner из ui.tsx). */
public class Spinner extends android.view.View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ValueAnimator animator;
    private float angle;
    private int trackColor = 0x40FFFFFF;
    private int headColor = 0xFFFFFFFF;

    public Spinner(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(900);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            angle = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void setColors(int track, int head) {
        trackColor = track;
        headColor = head;
        invalidate();
    }

    public void start() {
        if (!animator.isStarted()) animator.start();
    }

    public void stop() {
        animator.cancel();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            drawSpinner(canvas);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void drawSpinner(Canvas canvas) {
        super.onDraw(canvas);
        float inset = Math.max(2f, getWidth() * 0.08f);
        float stroke = Math.max(2f, getWidth() * 0.11f);
        paint.setStrokeWidth(stroke);
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        paint.setColor(trackColor);
        canvas.drawArc(rect, 0f, 360f, false, paint);
        paint.setColor(headColor);
        canvas.drawArc(rect, angle, 90f, false, paint);
    }
}
