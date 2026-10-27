package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.provider.Settings;

/**
 * The liquid-glass material.
 *
 * <p>Every glass surface is layered, never a flat translucent card:
 *
 * <ol>
 *   <li>a <b>backdrop sample</b> — the blurred background under the control,
 *       magnified a little to fake the lens of a real glass slab;</li>
 *   <li>a white <b>wash</b>, stronger at the top;</li>
 *   <li>four very soft <b>colour reflections</b> (cyan, blue, violet, pink);</li>
 *   <li>a <b>gloss</b> sheet fading down from the top edge;</li>
 *   <li>a <b>halo</b> of light trapped inside the edge;</li>
 *   <li>a bright diagonal <b>specular hairline</b>.</li>
 * </ol>
 *
 * <p>Android cannot blur whatever happens to be behind an arbitrary View, so
 * the glass never guesses: it samples a real blurred bitmap of the background
 * (the wallpaper, or a capture of the view behind it) at its own position.
 */
final class LiquidGlass {

    static final int CYAN = 0xFF57C7FF;
    static final int BLUE = 0xFF3B82F6;
    static final int VIOLET = 0xFFA855F7;
    static final int PINK = 0xFFF472B6;

    private LiquidGlass() {
    }

    /** Everything about a glass surface, all in dp / 0..1. */
    static final class Config {
        /** Corner radius in dp; anything huge becomes a circle. */
        float radiusDp = 28f;
        /** Overall white wash; light themes want more of it. */
        float tintAlpha = 0.14f;
        /** Tint colour laid over the sample; 0 means "the palette accent". */
        int tintColor;
        /** Strength of the cyan/blue/violet/pink reflections. */
        float reflectionAlpha = 0.55f;
        /** Top gloss sheet opacity. */
        float glossAlpha = 0.26f;
        /** Inner ring of light: alpha at its brightest. */
        float haloAlpha = 0.24f;
        /** Width of that ring in dp. */
        float haloWidthDp = 9f;
        float borderWidthDp = 1.1f;
        float borderAlpha = 0.9f;
        /** 1 = flat sample, 1.08 = slightly magnified like a lens. */
        float refraction = 1.07f;
        /** Solid tint laid over the sample, for filled buttons (0..1 alpha). */
        int overlayColor;
        float overlayAlpha;
        /** Extra edge line, e.g. the accent ring on a selected chip. */
        int edgeColor;
        float edgeWidthDp;
        boolean colorful = true;
        /** Pressed scale; 1 disables the spring. */
        float pressScale = 0.945f;

        Config copy() {
            Config c = new Config();
            c.radiusDp = radiusDp;
            c.tintAlpha = tintAlpha;
            c.tintColor = tintColor;
            c.reflectionAlpha = reflectionAlpha;
            c.glossAlpha = glossAlpha;
            c.haloAlpha = haloAlpha;
            c.haloWidthDp = haloWidthDp;
            c.borderWidthDp = borderWidthDp;
            c.borderAlpha = borderAlpha;
            c.refraction = refraction;
            c.overlayColor = overlayColor;
            c.overlayAlpha = overlayAlpha;
            c.edgeColor = edgeColor;
            c.edgeWidthDp = edgeWidthDp;
            c.colorful = colorful;
            c.pressScale = pressScale;
            return c;
        }
    }

    /** True when the user asked the system to remove animations. */
    static boolean motionDisabled(Context context) {
        try {
            float scale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            if (scale == 0f) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            float scale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
            return scale == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Draws one glass surface inside the current view bounds. */
    static final class Painter {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF rect = new RectF();

        private Palette palette = Palette.light();
        private Config config = new Config();
        private GlassBackdrop backdrop;
        private float density = 3f;

        private float builtW = -1;
        private float builtH = -1;
        private Palette builtPalette;
        private int builtTint;
        private Shader wash;
        private Shader gloss;
        private Shader rimShader;
        private Shader[] reflections = new Shader[0];

        void configure(Palette palette, Config config, GlassBackdrop backdrop, float density) {
            this.palette = palette == null ? Palette.light() : palette;
            this.config = config == null ? new Config() : config;
            this.backdrop = backdrop;
            this.density = density;
        }

        void draw(Canvas canvas, float w, float h, float viewX, float viewY, float press) {
            if (w <= 0 || h <= 0) {
                return;
            }
            boolean stale = w != builtW || h != builtH || palette != builtPalette
                    || config.tintColor != builtTint;
            if (stale) {
                build(w, h);
            }

            float radius = Math.min(config.radiusDp * density, Math.min(w, h) / 2f);
            clip.reset();
            clip.addRoundRect(0, 0, w, h, radius, radius, Path.Direction.CW);

            int save = canvas.save();
            canvas.clipPath(clip);

            if (backdrop != null && backdrop.isReady()) {
                backdrop.draw(canvas, viewX, viewY, w, h, config.refraction);
            } else {
                paint.setShader(null);
                paint.setColor(palette.dark ? 0x2E101820 : 0x5CFFFFFF);
                canvas.drawRect(0, 0, w, h, paint);
            }

            paint.setShader(wash);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            int tint = config.tintColor != 0 ? config.tintColor : palette.accent;
            int tintAlpha = clampAlpha(config.tintAlpha * 255f * 0.42f);
            paint.setColor((tintAlpha << 24) | (tint & 0x00FFFFFF));
            canvas.drawRect(0, 0, w, h, paint);

            if (config.colorful) {
                for (Shader reflection : reflections) {
                    blobPaint.setShader(reflection);
                    canvas.drawRect(0, 0, w, h, blobPaint);
                }
                blobPaint.setShader(null);
            }

            if (config.overlayAlpha > 0.001f) {
                int alpha = clampAlpha(config.overlayAlpha * 255f);
                paint.setColor((alpha << 24) | (config.overlayColor & 0x00FFFFFF));
                canvas.drawRect(0, 0, w, h, paint);
            }

            glossPaint.setShader(gloss);
            canvas.drawRect(0, 0, w, h, glossPaint);
            glossPaint.setShader(null);

            if (press > 0.02f) {
                int flash = (int) Math.min(255, 40 * press);
                paint.setColor((flash << 24) | 0x00FFFFFF);
                canvas.drawRect(0, 0, w, h, paint);
            }

            canvas.restoreToCount(save);

            drawEdge(canvas, w, h, radius);
        }

        private void drawEdge(Canvas canvas, float w, float h, float radius) {
            // Light caught inside the glass, brightest at the rim.
            haloPaint.setStyle(Paint.Style.STROKE);
            haloPaint.setShader(null);
            int halo = clampAlpha(config.haloAlpha * 255f);
            // Several overlapping rings, each wider and fainter than the last,
            // blend into one smooth glow instead of visible bands.
            int rings = 7;
            for (int ring = rings; ring >= 1; ring--) {
                float width = config.haloWidthDp * density * ring / rings;
                if (width <= 0.1f) {
                    continue;
                }
                haloPaint.setStrokeWidth(width);
                haloPaint.setColor((halo * (rings - ring + 1) / (rings + 2) << 24)
                        | 0x00FFFFFF);
                float inset = width / 2f;
                rect.set(inset, inset, w - inset, h - inset);
                canvas.drawRoundRect(rect, Math.max(1f, radius - inset),
                        Math.max(1f, radius - inset), haloPaint);
            }

            rimPaint.setStyle(Paint.Style.STROKE);
            rimPaint.setShader(rimShader);
            rimPaint.setStrokeWidth(Math.max(1f, config.borderWidthDp * density));
            float inset = Math.max(1f, config.borderWidthDp * density) / 2f;
            rect.set(inset, inset, w - inset, h - inset);
            canvas.drawRoundRect(rect, Math.max(1f, radius - inset),
                    Math.max(1f, radius - inset), rimPaint);

            if (config.edgeColor != 0 && config.edgeWidthDp > 0.1f) {
                float width = Math.max(1f, config.edgeWidthDp * density);
                rimPaint.setShader(null);
                rimPaint.setColor(config.edgeColor);
                rimPaint.setStrokeWidth(width);
                float edgeInset = width / 2f;
                rect.set(edgeInset, edgeInset, w - edgeInset, h - edgeInset);
                canvas.drawRoundRect(rect, Math.max(1f, radius - edgeInset),
                        Math.max(1f, radius - edgeInset), rimPaint);
            }
            rimPaint.setShader(null);
        }

        private void build(float w, float h) {
            builtW = w;
            builtH = h;
            builtPalette = palette;
            builtTint = config.tintColor;
            boolean dark = palette.dark;

            float washAlpha = config.tintAlpha * (dark ? 0.85f : 1.7f);
            int top = clampAlpha(washAlpha * 1.55f);
            int bottom = clampAlpha(washAlpha * 0.6f);
            wash = new LinearGradient(0, 0, 0, h,
                    (top << 24) | 0x00FFFFFF, (bottom << 24) | 0x00FFFFFF,
                    Shader.TileMode.CLAMP);

            int glossAlpha = clampAlpha(config.glossAlpha * (dark ? 0.8f : 1f));
            gloss = new LinearGradient(0, 0, 0, h * 0.62f,
                    (glossAlpha << 24) | 0x00FFFFFF, 0x00FFFFFF,
                    Shader.TileMode.CLAMP);

            int[] rimColors = dark
                    ? new int[]{0xB4FFFFFF, 0x46FFFFFF, 0x36FFFFFF, 0x7AFFFFFF}
                    : new int[]{0xFFFFFFFF, 0xD4FFFFFF, 0xC0FFFFFF, 0xF2FFFFFF};
            rimShader = new LinearGradient(0, 0, w, h, rimColors, null,
                    Shader.TileMode.CLAMP);

            int base = clampAlpha(0x20 * config.reflectionAlpha * (dark ? 1f : 0.82f));
            reflections = new Shader[]{
                    radial(w * 0.18f, h * 0.10f, w * 0.72f, CYAN, base),
                    radial(w * 0.86f, h * 0.16f, w * 0.68f, BLUE, base),
                    radial(w * 0.10f, h * 0.94f, w * 0.72f, VIOLET, base - 5),
                    radial(w * 0.84f, h * 0.90f, w * 0.78f, PINK, base - 7),
                    radial(w * 0.30f, h * 0.06f, w * 0.42f, 0xFFFFFF,
                            clampAlpha(0x12 * config.reflectionAlpha)),
            };
        }

        private static Shader radial(float cx, float cy, float radius, int color, int alpha) {
            if (alpha <= 0) {
                alpha = 0;
            }
            return new RadialGradient(cx, cy, Math.max(1f, radius),
                    new int[]{(alpha << 24) | (color & 0x00FFFFFF), 0x00000000},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        }

        private static int clampAlpha(float value) {
            int alpha = Math.round(value);
            return Math.max(0, Math.min(255, alpha));
        }
    }
}
