package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** A floating glass pill that holds a row of icon actions. */
class LiquidGlassFloatingToolbar extends LinearLayout {

    private final LiquidGlass.Painter painter = new LiquidGlass.Painter();
    private final LiquidGlass.Config config = new LiquidGlass.Config();
    private final int[] location = new int[2];
    private final float density;
    private final boolean motionOff;

    private Palette palette;
    private GlassBackdrop backdrop;

    LiquidGlassFloatingToolbar(Context context, Palette palette) {
        super(context);
        this.palette = palette == null ? Palette.light() : palette;
        density = getResources().getDisplayMetrics().density;
        motionOff = LiquidGlass.motionDisabled(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Math.round(dp(7)), Math.round(dp(7)),
                Math.round(dp(7)), Math.round(dp(7)));
        setWillNotDraw(false);
        setElevation(dp(20));
        setClipToPadding(false);

        config.radiusDp = 999f;
        config.tintAlpha = 0.18f;
        config.haloAlpha = 0.30f;
        config.haloWidthDp = 10f;
        config.glossAlpha = 0.30f;
        config.refraction = 1.06f;

        GradientDrawable outline = new GradientDrawable();
        outline.setColor(0x00000000);
        outline.setCornerRadius(dp(999));
        setBackground(outline);
    }

    void setPalette(Palette palette) {
        this.palette = palette == null ? Palette.light() : palette;
        refreshTints();
        invalidate();
    }

    void setBackdrop(GlassBackdrop backdrop) {
        this.backdrop = backdrop;
        invalidate();
    }

    ImageView addAction(int iconRes, CharSequence description, Runnable action) {
        ImageView view = new ImageView(getContext());
        view.setImageResource(iconRes);
        view.setColorFilter(palette.dark ? 0xFFFFFFFF : palette.text);
        view.setContentDescription(description);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(Math.round(dp(12)), Math.round(dp(12)),
                Math.round(dp(12)), Math.round(dp(12)));
        view.setBackground(M3.rippleMask(getContext(),
                (palette.accent & 0x00FFFFFF) | 0x33000000, 24f));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> {
            if (!motionOff) {
                v.animate().cancel();
                v.setScaleX(0.82f);
                v.setScaleY(0.82f);
                v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(new OvershootInterpolator(2.6f))
                        .start();
            }
            if (action != null) {
                action.run();
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(dp(48)), Math.round(dp(48)));
        params.leftMargin = Math.round(dp(2));
        params.rightMargin = Math.round(dp(2));
        addView(view, params);
        return view;
    }

    private void refreshTints() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(palette.dark ? 0xFFFFFFFF : palette.text);
            }
        }
    }

    /** Fades and lifts the bar into place. */
    void show() {
        if (motionOff) {
            setAlpha(1f);
            setTranslationY(0f);
            setScaleX(1f);
            setScaleY(1f);
            return;
        }
        setAlpha(0f);
        setTranslationY(dp(26));
        setScaleX(0.94f);
        setScaleY(0.94f);
        animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(420)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    void hide() {
        if (motionOff) {
            setAlpha(0f);
            return;
        }
        animate().alpha(0f).translationY(dp(18f)).scaleX(0.96f).scaleY(0.96f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private float dp(float value) {
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
        painter.configure(palette, config, backdrop, density);
        painter.draw(canvas, w, h, location[0], location[1], 0f);
    }
}
