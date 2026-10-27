package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * A glass button that holds one vector icon. The shape is configurable: a
 * circle by default, or any corner radius through {@link #setShapeRadius}.
 */
class LiquidGlassIconButton extends GlassSurfaceView {

    private Drawable icon;
    private int iconTint = 0xFFFFFFFF;
    private float iconSizeDp = 24f;
    private float buttonSizeDp = 56f;
    private float shapeRadiusDp = 999f;

    LiquidGlassIconButton(Context context) {
        super(context);
        config.radiusDp = 999f;
        config.tintAlpha = 0.16f;
        config.reflectionAlpha = 0.6f;
        config.refraction = 1.08f;
        setGlassOutline(999f);
    }

    void setIcon(int res) {
        Drawable drawable = getResources().getDrawable(res, getContext().getTheme());
        icon = drawable.mutate();
        icon.setTint(iconTint);
        invalidate();
    }

    void setIconTint(int color) {
        iconTint = color;
        if (icon != null) {
            icon.setTint(color);
            invalidate();
        }
    }

    void setIconSizeDp(float dp) {
        iconSizeDp = dp;
        invalidate();
    }

    void setButtonSizeDp(float dp) {
        buttonSizeDp = dp;
        requestLayout();
    }

    /** Corner radius in dp; 999 or more draws a circle. */
    void setShapeRadius(float radiusDp) {
        shapeRadiusDp = radiusDp;
        config.radiusDp = radiusDp;
        setGlassOutline(radiusDp);
        invalidateOutline();
        invalidate();
    }

    /** Extra colour laid into the glass, for a tinted button. */
    void setGlassTint(int color) {
        config.tintColor = color;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(dp(buttonSizeDp));
        setMeasuredDimension(resolveSize(size, widthMeasureSpec),
                resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void drawContent(Canvas canvas, float w, float h) {
        if (icon == null) {
            return;
        }
        float size = dp(iconSizeDp);
        int left = Math.round(w / 2f - size / 2f);
        int top = Math.round(h / 2f - size / 2f);
        icon.setBounds(left, top, Math.round(left + size), Math.round(top + size));
        icon.draw(canvas);
    }
}
