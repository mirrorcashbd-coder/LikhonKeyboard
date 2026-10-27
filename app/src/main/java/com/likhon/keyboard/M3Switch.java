package com.likhon.keyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Material 3 switch: a sunken pill track, a floating thumb with its own shadow
 * and a short spring on toggle. Drawn by hand so it matches the theme exactly.
 */
final class M3Switch extends View {

    interface Listener {
        void onChanged(boolean checked);
    }

    private static final float WIDTH_DP = 46f;
    private static final float HEIGHT_DP = 27f;
    private static final float THUMB_DP = 21f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Palette palette;
    private boolean checked;
    private float progress;
    private Listener listener;
    private ValueAnimator animator;

    M3Switch(Context context, Palette palette, boolean checked) {
        super(context);
        this.palette = palette;
        this.checked = checked;
        this.progress = checked ? 1f : 0f;
        setClickable(true);
        setFocusable(true);
    }

    void setListener(Listener value) {
        listener = value;
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean value) {
        if (checked == value) {
            return;
        }
        checked = value;
        animateTo(value ? 1f : 0f);
        if (listener != null) {
            listener.onChanged(value);
        }
    }

    void toggle() {
        setChecked(!checked);
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(170);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(dp(WIDTH_DP)), Math.round(dp(HEIGHT_DP)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        int offTrack = M3.surfaceHighest(palette);
        int onTrack = palette.accent;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Palette.mix(offTrack, onTrack, progress));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, paint);

        if (progress < 0.5f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(M3.outline(palette));
            rect.set(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f));
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float thumb = dp(THUMB_DP);
        float travel = w - thumb - dp(5);
        float left = dp(2.5f) + travel * progress;
        float cy = h / 2f;

        paint.setColor(0x33000000);
        canvas.drawCircle(left + thumb / 2f, cy + dp(1.2f), thumb / 2f, paint);

        int thumbColor = palette.dark ? Palette.mix(palette.surface, palette.text, 0.9f)
                : 0xFFFFFFFF;
        if (progress > 0.02f) {
            thumbColor = Palette.mix(thumbColor, palette.onAccent, progress);
        }
        paint.setColor(thumbColor);
        canvas.drawCircle(left + thumb / 2f, cy, thumb / 2f, paint);

        if (isPressed()) {
            paint.setColor((palette.accent & 0x00FFFFFF) | 0x22000000);
            canvas.drawCircle(left + thumb / 2f, cy, thumb * 0.78f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                invalidate();
                if (event.getX() >= 0 && event.getX() <= getWidth()
                        && event.getY() >= 0 && event.getY() <= getHeight()) {
                    toggle();
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
