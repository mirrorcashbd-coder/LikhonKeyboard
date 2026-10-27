package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Liquid glass as a {@link Drawable}, so ordinary TextViews, chips, fields
 * and buttons get the real material without changing a single layout.
 *
 * <p>The drawable works out which view it is painted on from its callback
 * (also through a wrapping {@code RippleDrawable}), asks that window for its
 * blurred backdrop and samples the fragment under itself.
 */
final class LiquidGlassDrawable extends Drawable {

    private final LiquidGlass.Painter painter = new LiquidGlass.Painter();
    private final LiquidGlass.Config config = new LiquidGlass.Config();
    private final Palette palette;
    private final float density;
    private final int[] location = new int[2];

    LiquidGlassDrawable(Context context, Palette palette, float radiusDp, int tint,
                        float overlayAlpha) {
        this.palette = palette == null ? Palette.light() : palette;
        density = context.getResources().getDisplayMetrics().density;
        config.radiusDp = radiusDp;
        config.tintColor = tint;
        config.overlayColor = tint;
        config.overlayAlpha = overlayAlpha;
        config.tintAlpha = 0.14f;
        config.haloAlpha = 0.24f;
        config.haloWidthDp = 8f;
        config.glossAlpha = 0.26f;
        config.reflectionAlpha = 0.55f;
        // Small controls read better with a gentler lens than big panels.
        config.refraction = 1.04f;
        config.borderWidthDp = 1.1f;
    }

    /** Draws an accent (or any colour) ring just inside the rim. */
    void setEdge(int color, float widthDp) {
        config.edgeColor = color;
        config.edgeWidthDp = widthDp;
        invalidateSelf();
    }

    void setRadiusDp(float radiusDp) {
        config.radiusDp = radiusDp;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        View view = callbackView();
        float x = bounds.left;
        float y = bounds.top;
        if (view != null) {
            view.getLocationInWindow(location);
            x += location[0];
            y += location[1];
        }
        GlassBackdrop backdrop = view == null ? null : Glass.backdropFor(view);
        painter.configure(palette, config, backdrop, density);
        painter.draw(canvas, w, h, x, y, 0f);
    }

    /** Unwraps RippleDrawable / LayerDrawable callbacks down to the view. */
    private View callbackView() {
        Drawable.Callback callback = getCallback();
        int guard = 0;
        while (callback instanceof Drawable && guard++ < 8) {
            callback = ((Drawable) callback).getCallback();
        }
        return callback instanceof View ? (View) callback : null;
    }

    @Override
    public void setAlpha(int alpha) {
        // The painter owns its own alphas; a drawable alpha would double them.
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
