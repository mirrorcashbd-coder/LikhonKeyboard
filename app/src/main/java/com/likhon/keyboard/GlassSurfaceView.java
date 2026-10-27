package com.likhon.keyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Base for every hand-drawn glass control: sampling, press spring and the
 * glass layers live here, subclasses only draw their icon or label on top.
 */
abstract class GlassSurfaceView extends View {

    protected final LiquidGlass.Config config = new LiquidGlass.Config();
    protected final LiquidGlass.Painter painter = new LiquidGlass.Painter();
    protected final float density;
    protected Palette palette = Palette.light();
    protected GlassBackdrop backdrop;

    private final int[] location = new int[2];
    private final boolean motionOff;
    private float press;
    private ValueAnimator pressAnim;

    GlassSurfaceView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        motionOff = LiquidGlass.motionDisabled(context);
        setClickable(true);
        setFocusable(true);
        setClipToOutline(false);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float radius = Math.min(config.radiusDp * density,
                        Math.min(view.getWidth(), view.getHeight()) / 2f);
                if (view.getWidth() <= 0 || view.getHeight() <= 0) {
                    return;
                }
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        Math.max(0f, radius));
            }
        });
    }

    void setPalette(Palette palette) {
        this.palette = palette == null ? Palette.light() : palette;
        invalidate();
    }

    void setBackdrop(GlassBackdrop backdrop) {
        this.backdrop = backdrop;
        invalidate();
    }

    /** A rounded background so the platform can draw a soft elevation shadow. */
    protected void setGlassOutline(float radiusDp) {
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(0x00000000);
        outline.setCornerRadius(dp(radiusDp));
        setBackground(outline);
        invalidateOutline();
    }

    protected void setGlassElevation(float dp) {
        setElevation(dp(dp));
    }

    protected float dp(float value) {
        return value * density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        getLocationInWindow(location);

        float scale = 1f - (1f - config.pressScale) * Math.max(0f, press);
        int save = canvas.save();
        canvas.scale(scale, scale, w / 2f, h / 2f);
        painter.configure(palette, config, backdrop, density);
        painter.draw(canvas, w, h, location[0], location[1], press);
        drawContent(canvas, w, h);
        canvas.restoreToCount(save);
    }

    /** Everything painted on top of the glass (icons, labels). */
    protected abstract void drawContent(Canvas canvas, float w, float h);

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animatePress(1f, 120, true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animatePress(0f, 260, false);
                break;
            default:
                break;
        }
        // Let View track the press and fire performClick() on release.
        super.onTouchEvent(event);
        return true;
    }

    void animatePress(float target, long duration, boolean down) {
        if (pressAnim != null) {
            pressAnim.cancel();
        }
        if (motionOff) {
            press = target;
            invalidate();
            return;
        }
        pressAnim = ValueAnimator.ofFloat(press, target);
        pressAnim.setDuration(duration);
        pressAnim.setInterpolator(down ? new DecelerateInterpolator(1.8f)
                : new OvershootInterpolator(2.4f));
        pressAnim.addUpdateListener(animation -> {
            press = (float) animation.getAnimatedValue();
            invalidate();
        });
        pressAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pressAnim != null) {
            pressAnim.cancel();
            pressAnim = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
