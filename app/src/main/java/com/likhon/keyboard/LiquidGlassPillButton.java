package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The wide glass pill from the reference: a nested glass disc, a label and a chevron. */
class LiquidGlassPillButton extends GlassSurfaceView {

    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron = new Path();

    private String label = "";
    private Drawable icon;
    private int iconTint = 0xFFFFFFFF;
    private float iconSizeDp = 26f;
    private float heightDp = 68f;
    private float badgeDp = 50f;
    private boolean showChevron = true;

    LiquidGlassPillButton(Context context) {
        super(context);
        config.radiusDp = 999f;
        config.tintAlpha = 0.16f;
        config.haloAlpha = 0.28f;
        config.haloWidthDp = 10f;
        config.glossAlpha = 0.30f;
        config.refraction = 1.08f;
        setGlassOutline(999f);
        setGlassElevation(16f);

        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(dp(18f));
        textPaint.setShadowLayer(dp(3f), 0, dp(1f), 0x80000000);
        badgeRim.setStyle(Paint.Style.STROKE);
    }

    void setLabel(CharSequence text) {
        label = text == null ? "" : text.toString();
        requestLayout();
        invalidate();
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

    void setHeightDp(float dp) {
        heightDp = dp;
        requestLayout();
    }

    void setBadgeSizeDp(float dp) {
        badgeDp = dp;
        requestLayout();
    }

    void setShowChevron(boolean show) {
        showChevron = show;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = Math.round(dp(heightDp));
        int textWidth = label.isEmpty() ? 0 : Math.round(textPaint.measureText(label));
        int width = Math.round(dp(9) + dp(badgeDp) + dp(14)
                + textWidth + (showChevron ? dp(14) + dp(18) : 0) + dp(18));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void drawContent(Canvas canvas, float w, float h) {
        float badge = Math.min(dp(badgeDp), h - dp(16));
        float bx = dp(9);
        float by = (h - badge) / 2f;
        float cx = bx + badge / 2f;
        float cy = by + badge / 2f;

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(palette.dark ? 0x33FFFFFF : 0x59FFFFFF);
        canvas.drawCircle(cx, cy, badge / 2f, badgePaint);
        badgeRim.setColor(palette.dark ? 0x8CFFFFFF : 0xE6FFFFFF);
        badgeRim.setStrokeWidth(dp(1.1f));
        canvas.drawCircle(cx, cy, badge / 2f - dp(0.6f), badgeRim);
        badgeRim.setColor(0x30FFFFFF);
        badgeRim.setStrokeWidth(dp(3f));
        canvas.drawCircle(cx, cy, badge / 2f - dp(1.8f), badgeRim);

        if (icon != null) {
            float size = Math.min(dp(iconSizeDp), badge * 0.58f);
            int left = Math.round(cx - size / 2f);
            int top = Math.round(cy - size / 2f);
            icon.setBounds(left, top, Math.round(left + size), Math.round(top + size));
            icon.draw(canvas);
        }

        textPaint.setColor(palette.dark ? 0xFFFFFFFF : palette.text);
        textPaint.setTextSize(dp(18f));
        float textX = bx + badge + dp(14);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = h / 2f - (metrics.ascent + metrics.descent) / 2f;
        if (!label.isEmpty()) {
            canvas.drawText(label, textX, baseline, textPaint);
        }

        if (showChevron) {
            float size = dp(11);
            float x = w - dp(20);
            float y = h / 2f;
            chevron.reset();
            chevron.moveTo(x - size * 0.5f, y - size * 0.32f);
            chevron.lineTo(x, y + size * 0.34f);
            chevron.lineTo(x + size * 0.5f, y - size * 0.32f);
            badgeRim.setStyle(Paint.Style.STROKE);
            badgeRim.setStrokeWidth(dp(2f));
            badgeRim.setStrokeCap(Paint.Cap.ROUND);
            badgeRim.setColor(palette.dark ? 0xD0FFFFFF : 0xB0000000);
            canvas.drawPath(chevron, badgeRim);
        }
    }
}
