package com.anibeat.app.core;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Слайдер в стиле сайта (`.ios-slider`): тонкий трек, белое заполнение,
 * круглый бегунок появляется при перетаскивании (как в CSS с --thumb).
 */
public class SliderView extends View {

    public interface OnChange {
        void onScrub(float value);

        void onCommit(float value);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float min = 0f;
    private float max = 1f;
    private float value = 0f;
    private float trackH;
    private float thumbR;
    private int trackColor = 0x29FFFFFF;
    private int fillColor = 0xFFFFFFFF;
    private int thumbColor = 0xFFFFFFFF;
    private boolean showThumb = true;
    private boolean enabled = true;
    private OnChange listener;

    public SliderView(Context context) {
        super(context);
        trackH = Theme.dpF(context, 5f);
        thumbR = Theme.dpF(context, 6f);
        applyLayer();
    }

    public void setRange(float min, float max) {
        this.min = min;
        this.max = Math.max(max, min);
        invalidate();
    }

    public void setValue(float v) {
        this.value = clamp(v);
        invalidate();
    }

    public float getValue() {
        return value;
    }

    public void setTrackHeightDp(float dp) {
        trackH = Theme.dpF(getContext(), dp);
        invalidate();
    }

    public void setThumbRadiusDp(float dp) {
        thumbR = Theme.dpF(getContext(), dp);
        invalidate();
    }

    public void setShowThumb(boolean show) {
        showThumb = show;
        applyLayer();
        invalidate();
    }

    /** Тень бегунка требует программного слоя, но держать его постоянно — тормоза. */
    private void applyLayer() {
        setLayerType(showThumb ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_NONE, null);
    }

    public void setColors(int track, int fill, int thumb) {
        trackColor = track;
        fillColor = fill;
        thumbColor = thumb;
        invalidate();
    }

    public void setOnChange(OnChange l) {
        listener = l;
    }

    @Override
    public void setEnabled(boolean value) {
        enabled = value;
        setAlpha(value ? 1f : 0.4f);
        super.setEnabled(value);
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = (int) Math.max(Theme.dpF(getContext(), 22f), thumbR * 2 + Theme.dpF(getContext(), 6f));
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthSpec), resolveSize(height, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            drawSlider(canvas);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void drawSlider(Canvas canvas) {
        if (getWidth() <= 0) return;
        float w = getWidth();
        float cy = getHeight() / 2f;
        float r = trackH / 2f;
        paint.setColor(trackColor);
        canvas.drawRoundRect(0, cy - r, w, cy + r, r, r, paint);
        float progress = max > min ? (value - min) / (max - min) : 0f;
        if (progress > 0) {
            paint.setColor(fillColor);
            canvas.drawRoundRect(0, cy - r, Math.max(r * 2, w * progress), cy + r, r, r, paint);
        }
        if (showThumb && thumbR > 0) {
            paint.setColor(thumbColor);
            paint.setShadowLayer(Theme.dpF(getContext(), 1.5f), 0, Theme.dpF(getContext(), 1f), 0x66000000);
            canvas.drawCircle(w * progress, cy, thumbR, paint);
            paint.clearShadowLayer();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                android.view.ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                float ratio = Math.max(0f, Math.min(1f, event.getX() / Math.max(1f, getWidth())));
                value = min + ratio * (max - min);
                invalidate();
                if (listener != null) listener.onScrub(value);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (listener != null) listener.onCommit(value);
                android.view.ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
                return true;
            }
            default:
                return true;
        }
    }
}
