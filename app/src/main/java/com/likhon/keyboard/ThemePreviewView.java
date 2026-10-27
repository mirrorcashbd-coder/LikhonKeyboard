package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * A small, live drawing of the keyboard in the selected theme. It is painted
 * with the same palette the keyboard uses, so the appearance card shows the
 * real thing (gradient, shadow, glow and accent) instead of a coloured square.
 */
final class ThemePreviewView extends View {

    private static final String[] ROW1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
    private static final String[] ROW2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
    private static final String[] ROW3 = {"z", "x", "c", "v", "b", "n", "m"};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF shadowRect = new RectF();
    private Palette palette;

    ThemePreviewView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        label.setTextAlign(Paint.Align.CENTER);
        label.setFakeBoldText(true);
    }

    void setPalette(Palette value) {
        palette = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (palette == null) {
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float radius = dp(16);

        if (palette.hasGradient()) {
            fill.setShader(new LinearGradient(0, 0, 0, h,
                    palette.bgTop, palette.bgBottom, Shader.TileMode.CLAMP));
        } else {
            fill.setColor(palette.bg);
        }
        canvas.drawRoundRect(0, 0, w, h, radius, radius, fill);
        fill.setShader(null);

        // suggestion strip
        rect.set(dp(8), dp(8), w - dp(8), dp(23));
        fill.setColor(palette.keySpecial);
        canvas.drawRoundRect(rect, dp(7), dp(7), fill);

        float side = dp(8);
        float top = dp(29);
        float keyH = dp(18);
        float gap = dp(4);

        drawRow(canvas, ROW1, side, top, w - side, keyH, gap, palette.keyBg, palette.text);
        top += keyH + gap;
        drawRow(canvas, ROW2, side + dp(10), top, w - side - dp(10), keyH, gap,
                palette.keyBg, palette.text);
        top += keyH + gap;

        // third row: shift, letters, delete
        float shiftW = dp(26);
        drawKey(canvas, side, top, side + shiftW, top + keyH, palette.keySpecial, false);
        drawRow(canvas, ROW3, side + shiftW + gap, top, w - side - shiftW - gap, keyH, gap,
                palette.keyBg, palette.text);
        top += keyH + gap;

        // bottom row: ?123, globe, space, enter
        float small = dp(30);
        float enter = dp(40);
        drawKey(canvas, side, top, side + small, top + keyH, palette.keySpecial, false);
        float globeLeft = side + small + gap;
        drawKey(canvas, globeLeft, top, globeLeft + small, top + keyH, palette.accent, true);
        float spaceLeft = globeLeft + small + gap;
        float spaceRight = w - side - enter - gap;
        drawKey(canvas, spaceLeft, top, spaceRight, top + keyH, palette.keyBg, false);
        drawKey(canvas, spaceRight + gap, top, w - side, top + keyH, palette.accent, true);

        stroke.setColor(palette.keyStroke);
        stroke.setStrokeWidth(dp(1));
        rect.set(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f));
        canvas.drawRoundRect(rect, radius, radius, stroke);
    }

    private void drawRow(Canvas canvas, String[] keys, float left, float top, float right,
                         float keyH, float gap, int bg, int fg) {
        float usable = right - left - gap * (keys.length - 1);
        float keyW = usable / keys.length;
        for (int i = 0; i < keys.length; i++) {
            float x = left + i * (keyW + gap);
            drawKey(canvas, x, top, x + keyW, top + keyH, bg, false);
            float size = Math.min(keyH * 0.5f, keyW * 0.62f);
            label.setTextSize(size);
            label.setColor(fg);
            float baseline = (top + top + keyH) / 2f
                    - (label.ascent() + label.descent()) / 2f;
            canvas.drawText(keys[i], x + keyW / 2f, baseline, label);
        }
    }

    private void drawKey(Canvas canvas, float left, float top, float right, float bottom,
                         int bg, boolean glow) {
        float radius = dp(5.5f);
        if (palette.shadow != 0) {
            shadowRect.set(left, top + dp(1.2f), right, bottom + dp(1.2f));
            fill.setColor(palette.shadow);
            canvas.drawRoundRect(shadowRect, radius, radius, fill);
        }
        if (glow && palette.glow != 0) {
            rect.set(left - dp(1.2f), top - dp(1.2f), right + dp(1.2f), bottom + dp(1.2f));
            fill.setColor(palette.glow);
            canvas.drawRoundRect(rect, radius + dp(1.2f), radius + dp(1.2f), fill);
        }
        rect.set(left, top, right, bottom);
        fill.setColor(bg);
        canvas.drawRoundRect(rect, radius, radius, fill);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
