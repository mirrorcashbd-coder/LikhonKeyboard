package com.likhon.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.WeakHashMap;

/**
 * The app's glass: every panel, card, bar, chip, button and field is painted
 * with {@link LiquidGlass} over a blurred copy of the real background.
 *
 * <p>The backdrop is rendered once per window and size (wallpaper, theme wash
 * and accent blobs), downscaled, blurred and published for the window. Glass
 * surfaces then sample the part under themselves, so the material behaves the
 * same here as on the showcase screen.
 */
final class Glass {

    static final int CARD = 0;
    static final int BAR = 1;

    /** Blurred backdrop per window (the key is the decor view). */
    private static final WeakHashMap<View, GlassBackdrop> BACKDROPS = new WeakHashMap<>();
    /** Last capture, reused by the other settings screens (same size/palette). */
    private static GlassBackdrop shared;
    private static int sharedW = -1;
    private static int sharedH = -1;
    private static int sharedAccent;
    private static boolean sharedDark;

    private Glass() {
    }

    static GlassBackdrop backdropFor(View view) {
        if (view == null) {
            return null;
        }
        return BACKDROPS.get(view.getRootView());
    }

    static void publish(View view, GlassBackdrop backdrop) {
        if (view != null && backdrop != null) {
            BACKDROPS.put(view.getRootView(), backdrop);
        }
    }

    // ------------------------------------------------------------- backdrops

    /**
     * Paints what sits behind the glass: a soft base gradient, the blurred
     * wallpaper (centre-cropped, with a theme wash) and four huge accent blobs.
     */
    static final class BackdropPainter {
        private final Context context;
        private final Palette palette;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG);
        private Shader base;
        private float builtW = -1;
        private float builtH = -1;

        BackdropPainter(Context context, Palette palette) {
            this.context = context;
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

            Bitmap photo = Wallpaper.blurred(context);
            if (photo != null && !photo.isRecycled()) {
                float scale = Math.max(w / (float) photo.getWidth(),
                        h / (float) photo.getHeight());
                canvas.save();
                canvas.translate((w - photo.getWidth() * scale) / 2f,
                        (h - photo.getHeight() * scale) / 2f);
                canvas.scale(scale, scale);
                canvas.drawBitmap(photo, 0, 0, photoPaint);
                canvas.restore();

                // A theme wash over the photo: white for light themes, black
                // for dark ones, so labels stay readable on any wallpaper.
                paint.setColor(((palette.dark ? 0x52 : 0x60) << 24)
                        | (palette.dark ? 0x000000 : 0xFFFFFF));
                canvas.drawRect(0, 0, w, h, paint);
            }

            int accent = palette.accent;
            blob(canvas, w * 0.94f, -h * 0.02f, w * 1.05f, accent,
                    palette.dark ? 0x4A : 0x32);
            blob(canvas, -w * 0.10f, h * 0.44f, w * 1.10f, shiftHue(accent, 46f),
                    palette.dark ? 0x30 : 0x24);
            blob(canvas, w * 0.45f, h * 1.04f, w * 1.00f, shiftHue(accent, -58f),
                    palette.dark ? 0x28 : 0x1C);
            blob(canvas, w * 0.22f, h * 0.01f, w * 0.66f, 0xFFFFFF,
                    palette.dark ? 0x16 : 0x3A);
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

    /**
     * Renders a backdrop painter into a small blurred bitmap tied to a view's
     * position. Never call this from inside that view's own onDraw loop more
     * than once per size.
     */
    static GlassBackdrop capture(View view, BackdropPainter painter) {
        int w = view.getWidth();
        int h = view.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int smallW = Math.max(64, Math.min(240, w / 6));
        int smallH = Math.max(64, Math.min(480, h / 6));
        Bitmap bitmap = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(smallW / (float) w, smallH / (float) h);
        painter.draw(canvas, w, h);
        GlassBackdrop.blur(bitmap);

        GlassBackdrop backdrop = GlassBackdrop.of(bitmap, w, h);
        if (backdrop == null) {
            return null;
        }
        int[] location = new int[2];
        view.getLocationInWindow(location);
        backdrop.setOrigin(location[0], location[1], w, h);
        return backdrop;
    }

    private static GlassBackdrop reusable(int w, int h, Palette palette) {
        if (shared != null && sharedW == w && sharedH == h
                && sharedAccent == palette.accent && sharedDark == palette.dark) {
            return shared;
        }
        return null;
    }

    private static void remember(GlassBackdrop backdrop, int w, int h, Palette palette) {
        shared = backdrop;
        sharedW = w;
        sharedH = h;
        sharedAccent = palette.accent;
        sharedDark = palette.dark;
    }

    /** Screen-sized backdrop view: paints the background and publishes it. */
    static final class Backdrop extends View {
        private final BackdropPainter painter;
        private final Palette palette;
        private GlassBackdrop image;
        private GlassBackdrop published;

        Backdrop(Context context, Palette palette) {
            super(context);
            this.palette = palette;
            painter = new BackdropPainter(context, palette);
            setWillNotDraw(false);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            image = null;
            published = null;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            painter.draw(canvas, w, h);

            if (image == null) {
                image = reusable(w, h, palette);
                if (image == null) {
                    image = capture(this, painter);
                    if (image != null) {
                        remember(image, w, h, palette);
                    }
                }
            }
            if (image != null && published != image) {
                publish(this, image);
                published = image;
                // Panels earlier in the tree may have drawn with the old
                // backdrop; one more pass aligns everything.
                postInvalidateOnAnimation();
            }
        }
    }

    /** The same backdrop as a drawable, for views that only take a background. */
    static final class BackdropDrawable extends Drawable {
        private final BackdropPainter painter;

        BackdropDrawable(Context context, Palette palette) {
            painter = new BackdropPainter(context, palette);
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

    /** Shared glass painting for our container views. */
    static final class Surface {
        private final LiquidGlass.Painter painter = new LiquidGlass.Painter();
        private final LiquidGlass.Config config = new LiquidGlass.Config();
        private final Palette palette;
        private final float density;
        private final int[] location = new int[2];

        Surface(Context context, Palette palette, float radiusDp, int kind) {
            this.palette = palette;
            density = context.getResources().getDisplayMetrics().density;
            config.radiusDp = radiusDp;
            config.borderWidthDp = 1.1f;
            config.reflectionAlpha = 0.6f;
            config.refraction = 1.05f;
            if (kind == BAR) {
                config.tintAlpha = 0.20f;
                config.haloAlpha = 0.30f;
                config.haloWidthDp = 10f;
                config.glossAlpha = 0.30f;
            } else {
                config.tintAlpha = 0.15f;
                config.haloAlpha = 0.26f;
                config.haloWidthDp = 9f;
                config.glossAlpha = 0.28f;
            }
        }

        void draw(Canvas canvas, View view) {
            int w = view.getWidth();
            int h = view.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            view.getLocationInWindow(location);
            painter.configure(palette, config, Glass.backdropFor(view), density);
            painter.draw(canvas, w, h, location[0], location[1], 0f);
        }
    }

    /** Glass surface that stacks its children vertically (or horizontally). */
    static final class Panel extends LinearLayout {
        private final Surface surface;

        Panel(Context context, Palette palette, float radiusDp, int kind, boolean horizontal) {
            super(context);
            setOrientation(horizontal ? HORIZONTAL : VERTICAL);
            if (horizontal) {
                setGravity(Gravity.CENTER_VERTICAL);
            }
            setWillNotDraw(false);
            surface = new Surface(context, palette, radiusDp, kind);
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(0x00000000);
            outline.setCornerRadius(Ui.dp(context, radiusDp));
            setBackground(outline);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            surface.draw(canvas, this);
        }
    }

    /** Glass surface for tiles, where a foreground ripple is wanted. */
    static final class Tile extends FrameLayout {
        private final Surface surface;

        Tile(Context context, Palette palette, float radiusDp, int kind) {
            super(context);
            setWillNotDraw(false);
            surface = new Surface(context, palette, radiusDp, kind);
            GradientDrawable outline = new GradientDrawable();
            outline.setColor(0x00000000);
            outline.setCornerRadius(Ui.dp(context, radiusDp));
            setBackground(outline);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            surface.draw(canvas, this);
        }
    }

    // ------------------------------------------------------- small controls

    /** Glass fill for text views, buttons and chips. */
    static Drawable fill(Context c, Palette p, float radiusDp, int tint) {
        return new LiquidGlassDrawable(c, p, radiusDp, tint,
                tint == 0 ? 0f : (p.dark ? 0.84f : 0.80f));
    }

    /** Fill + top sheen, wrapped in a ripple. */
    static Drawable control(Context c, Palette p, float radiusDp, int tint, int rippleColor) {
        Drawable base = fill(c, p, radiusDp, tint);
        GradientDrawable mask = Ui.rounded(c, 0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), base, mask);
    }

    /** Flat glass for views that later receive a ripple themselves. */
    static Drawable glass(Context c, Palette p, float radiusDp, int tint) {
        return fill(c, p, radiusDp, tint);
    }
}
