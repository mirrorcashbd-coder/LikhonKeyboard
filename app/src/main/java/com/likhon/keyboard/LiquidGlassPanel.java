package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;

/**
 * A container whose own background is liquid glass. Children are drawn on top;
 * use padding to keep content off the rim.
 */
class LiquidGlassPanel extends FrameLayout {

    private final LiquidGlass.Painter painter = new LiquidGlass.Painter();
    private final LiquidGlass.Config config = new LiquidGlass.Config();
    private final int[] location = new int[2];
    private final float density;

    private Palette palette;
    private GlassBackdrop backdrop;
    private float radiusDp = 30f;

    LiquidGlassPanel(Context context, Palette palette) {
        super(context);
        this.palette = palette == null ? Palette.light() : palette;
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClipToPadding(false);
        setElevation(dp(18));
        setOutline(radiusDp);
    }

    void setPalette(Palette palette) {
        this.palette = palette == null ? Palette.light() : palette;
        invalidate();
    }

    void setBackdrop(GlassBackdrop backdrop) {
        this.backdrop = backdrop;
        invalidate();
    }

    void setRadiusDp(float radiusDp) {
        this.radiusDp = radiusDp;
        config.radiusDp = radiusDp;
        setOutline(radiusDp);
        invalidate();
    }

    LiquidGlass.Config config() {
        return config;
    }

    private void setOutline(float radiusDp) {
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(0x00000000);
        outline.setCornerRadius(dp(radiusDp));
        setBackground(outline);
        invalidateOutline();
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
