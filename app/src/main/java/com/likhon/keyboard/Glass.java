package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * iOS 27 style liquid glass, painted by hand.
 *
 * <p>Each panel is four layers: a translucent white fill, a lighter top sheen,
 * a diagonal specular rim that catches the light at the top-left, and the soft
 * elevation shadow underneath. The backdrop is a mesh of four huge, smooth
 * colour blobs, so whatever shows through the glass is already soft — that is
 * what sells the frosted look without a real blur pass.
 */
final class Glass {

    static final int CARD = 0;
    static final int BAR = 1;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Palette palette;
    private final float radiusPx;
    private final float strokePx;
    private final int kind;
    private float builtW = -1;
    private float builtH = -1;

    Glass(Palette palette, float radiusPx, float strokePx, int kind) {
        this.palette = palette;
        this.radiusPx = radiusPx;
        this.strokePx = strokePx;
        this.kind = kind;
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(strokePx);
    }

    void draw(Canvas canvas, float w, float h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w != builtW || h != builtH) {
            build(w, h);
        }
        float r = Math.min(radiusPx, Math.min(w, h) / 2f);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, fillPaint);
        canvas.drawRoundRect(rect, r, r, glossPaint);
        float inset = strokePx / 2f;
        rect.set(inset, inset, w - inset, h - inset);
        canvas.drawRoundRect(rect, r, r, rimPaint);
    }

    private void build(float w, float h) {
        builtW = w;
        builtH = h;
        boolean dark = palette.dark;

        int topAlpha = dark ? (kind == BAR ? 0x30 : 0x1E)
                : (kind == BAR ? 0xC8 : 0xA8);
        int bottomAlpha = dark ? (kind == BAR ? 0x1C : 0x0E)
                : (kind == BAR ? 0xAA : 0x82);
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                (topAlpha << 24) | 0x00FFFFFF, (bottomAlpha << 24) | 0x00FFFFFF,
                Shader.TileMode.CLAMP));

        int glossAlpha = dark ? 0x1A : 0x44;
        glossPaint.setShader(new LinearGradient(0, 0, 0, h * 0.68f,
                (glossAlpha << 24) | 0x00FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));

        int[] rimColors = dark
                ? new int[]{0x7CFFFFFF, 0x26FFFFFF, 0x1AFFFFFF, 0x4CFFFFFF}
                : new int[]{0xFFFFFFFF, 0xB6FFFFFF, 0x9CFFFFFF, 0xE8FFFFFF};
        rimPaint.setShader(new LinearGradient(0, 0, w, h, rimColors, null,
                Shader.TileMode.CLAMP));
    }

    // ------------------------------------------------------------- backdrops

    /**
     * Paints the colour mesh that sits behind every glass panel: a soft base
     * gradient and four huge, smooth blobs. Because the blobs are already
     * soft, anything seen through a translucent panel reads as frosted glass
     * without a real blur pass.
     */
    static final class BackdropPainter {
        private final Palette palette;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader base;
        private float builtW = -1;
        private float builtH = -1;

        BackdropPainter(Palette palette) {
            this.palette = palette;
        }

        void draw(Canvas canvas, float w, float h) {
            if (w <= 0 || h <= 0) {
                return;
            }
            if (base == null || w != builtW || h != builtH) {
                builtW = w;
                builtH = h;
                base = new LinearGradient(0, 0, w * 0.35f, h,
                        palette.bgTop, palette.bgBottom, Shader.TileMode.CLAMP);
            }
            paint.setShader(base);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            int accent = palette.accent;
            blob(canvas, w * 0.94f, -h * 0.02f, w * 1.05f, accent,
                    palette.dark ? 0x54 : 0x44);
            blob(canvas, -w * 0.10f, h * 0.44f, w * 1.10f, shiftHue(accent, 46f),
                    palette.dark ? 0x3A : 0x30);
            blob(canvas, w * 0.45f, h * 1.04f, w * 1.00f, shiftHue(accent, -58f),
                    palette.dark ? 0x30 : 0x26);
            blob(canvas, w * 0.22f, h * 0.01f, w * 0.66f, 0xFFFFFF,
                    palette.dark ? 0x18 : 0x64);
        }

        private void blob(Canvas canvas, float cx, float cy, float radius, int color,
                          int alpha) {
            int solid = (alpha << 24) | (color & 0x00FFFFFF);
            paint.setShader(new RadialGradient(cx, cy, radius,
                    new int[]{solid, 0x00000000}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setShader(null);
        }

        static int shiftHue(int color, float degrees) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[0] = (hsv[0] + degrees + 360f) % 360f;
            return Color.HSVToColor(hsv);
        }
    }

    /** Screen-sized mesh backdrop. */
    static final class Backdrop extends View {
        private final BackdropPainter painter;

        Backdrop(Context context, Palette palette) {
            super(context);
            painter = new BackdropPainter(palette);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            painter.draw(canvas, getWidth(), getHeight());
        }
    }

    /** The same mesh as a drawable, for views that only take a background. */
    static final class BackdropDrawable extends Drawable {
        private final BackdropPainter painter;

        BackdropDrawable(Palette palette) {
            painter = new BackdropPainter(palette);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            painter.draw(canvas, bounds.width(), bounds.height());
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ---------------------------------------------------------------- panels

    /** Glass surface that stacks its children vertically (or horizontally). */
    static final class Panel extends LinearLayout {
        private final Glass glass;

        Panel(Context context, Palette palette, float radiusDp, int kind, boolean horizontal) {
            super(context);
            setOrientation(horizontal ? HORIZONTAL : VERTICAL);
            if (horizontal) {
                setGravity(Gravity.CENTER_VERTICAL);
            }
            setWillNotDraw(false);
            glass = new Glass(palette, Ui.dp(context, radiusDp),
                    Math.max(1f, Ui.dp(context, 1.1f)), kind);
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(0x00000000);
            outline.setCornerRadius(Ui.dp(context, radiusDp));
            setBackground(outline);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            glass.draw(canvas, getWidth(), getHeight());
        }
    }

    /** Glass surface for tiles, where a foreground ripple is wanted. */
    static final class Tile extends FrameLayout {
        private final Glass glass;

        Tile(Context context, Palette palette, float radiusDp, int kind) {
            super(context);
            setWillNotDraw(false);
            glass = new Glass(palette, Ui.dp(context, radiusDp),
                    Math.max(1f, Ui.dp(context, 1.1f)), kind);
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(0x00000000);
            outline.setCornerRadius(Ui.dp(context, radiusDp));
            setBackground(outline);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            glass.draw(canvas, getWidth(), getHeight());
        }
    }

    // ------------------------------------------------------- small controls

    /** Glass fill for text views, buttons and chips. */
    static GradientDrawable fill(Context c, Palette p, float radiusDp, int tint) {
        int top;
        int bottom;
        if (tint != 0) {
            top = ((p.dark ? 0xF0 : 0xF6) << 24)
                    | (Palette.mix(tint, 0xFFFFFF, p.dark ? 0.20f : 0.14f) & 0x00FFFFFF);
            bottom = ((p.dark ? 0xDA : 0xE6) << 24) | (tint & 0x00FFFFFF);
        } else {
            top = ((p.dark ? 0x2A : 0xC8) << 24) | 0x00FFFFFF;
            bottom = ((p.dark ? 0x16 : 0xA2) << 24) | 0x00FFFFFF;
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        drawable.setCornerRadius(Ui.dp(c, radiusDp));
        drawable.setStroke(Math.max(1, Ui.dp(c, 1.1f)),
                ((p.dark ? 0x5C : 0xEE) << 24) | 0x00FFFFFF);
        return drawable;
    }

    /** Fill + top sheen, wrapped in a ripple. */
    static Drawable control(Context c, Palette p, float radiusDp, int tint, int rippleColor) {
        GradientDrawable base = fill(c, p, radiusDp, tint);
        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{(p.dark ? 0x16 : 0x3A) << 24 | 0x00FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(Ui.dp(c, Math.max(1f, radiusDp - 1f)));
        LayerDrawable layers = new LayerDrawable(new Drawable[]{base, sheen});
        layers.setLayerInset(1, Ui.dp(c, 1), Ui.dp(c, 1), Ui.dp(c, 1), Ui.dp(c, 1));
        GradientDrawable mask = Ui.rounded(c, 0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), layers, mask);
    }

    /** Flat glass for views that later receive a ripple themselves. */
    static Drawable glass(Context c, Palette p, float radiusDp, int tint) {
        GradientDrawable base = fill(c, p, radiusDp, tint);
        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{(p.dark ? 0x16 : 0x3A) << 24 | 0x00FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(Ui.dp(c, Math.max(1f, radiusDp - 1f)));
        LayerDrawable layers = new LayerDrawable(new Drawable[]{base, sheen});
        layers.setLayerInset(1, Ui.dp(c, 1), Ui.dp(c, 1), Ui.dp(c, 1), Ui.dp(c, 1));
        return layers;
    }
}
