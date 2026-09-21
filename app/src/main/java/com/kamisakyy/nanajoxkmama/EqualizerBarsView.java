package com.kamisakyy.nanajoxkmama;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Animated 4-bar equalizer matching the website's animated playing indicator.
 */
public final class EqualizerBarsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean isPlaying = false;
    private long startTime = 0;

    public EqualizerBarsView(Context context) {
        super(context);
        init();
    }

    public EqualizerBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;
        if (playing) {
            startTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        } else {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int bars = 4;
        float gap = w * 0.12f;
        float barWidth = (w - (bars - 1) * gap) / bars;
        float radius = barWidth / 2f;
        float minH = h * 0.2f;

        long elapsed = isPlaying ? (SystemClock.uptimeMillis() - startTime) : 0;

        // Individual frequencies & phase offsets for organic bounce
        float[] speeds = { 0.007f, 0.009f, 0.006f, 0.008f };
        float[] phases = { 0.0f, 1.2f, 2.4f, 0.6f };

        for (int i = 0; i < bars; i++) {
            float barHeight;
            if (isPlaying) {
                float wave = (float) Math.sin(elapsed * speeds[i] + phases[i]);
                // Normalized to 0.25 .. 1.0
                float factor = 0.25f + 0.75f * ((wave + 1f) / 2f);
                barHeight = Math.max(minH, h * factor);
            } else {
                barHeight = minH;
            }

            float left = i * (barWidth + gap);
            float top = h - barHeight;
            float right = left + barWidth;
            float bottom = h;

            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        if (isPlaying && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isPlaying) {
            postInvalidateOnAnimation();
        }
    }
}
