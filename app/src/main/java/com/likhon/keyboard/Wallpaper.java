package com.likhon.keyboard;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * The phone's own wallpaper, shrunk to a few hundred pixels and blurred once.
 *
 * <p>That small bitmap is then stretched over the whole screen, so the blur
 * costs almost nothing: the shrinking itself throws away most of the detail,
 * and three cheap box passes on the tiny image make it as soft as the frosted
 * photo behind iOS glass. Loading is attempted once per process; when the
 * wallpaper is unavailable the colour mesh is used instead.
 */
final class Wallpaper {

    private static Bitmap blurred;
    private static boolean tried;

    private Wallpaper() {
    }

    /** The blurred wallpaper, or null when it cannot be read. */
    static Bitmap blurred(Context context) {
        if (!tried) {
            tried = true;
            try {
                Context app = context.getApplicationContext();
                WallpaperManager manager = WallpaperManager.getInstance(app);
                Drawable drawable = manager.getDrawable();
                if (drawable == null) {
                    drawable = manager.getBuiltInDrawable(160, 320, false, 0.5f, 0.5f);
                }
                if (drawable != null) {
                    blurred = shrink(drawable);
                }
            } catch (Throwable ignored) {
                // A live wallpaper, a locked-down OEM or no wallpaper at all.
            }
        }
        return blurred;
    }

    private static Bitmap shrink(Drawable drawable) {
        int width = 132;
        int height = drawable.getIntrinsicHeight() > 0 && drawable.getIntrinsicWidth() > 0
                ? Math.round(width * (float) drawable.getIntrinsicHeight()
                        / drawable.getIntrinsicWidth())
                : Math.round(width * 2.1f);
        height = Math.max(48, Math.min(420, height));

        Bitmap small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);

        int[] pixels = new int[width * height];
        small.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] temp = new int[pixels.length];
        int[] out = new int[pixels.length];
        for (int pass = 0; pass < 3; pass++) {
            boxHorizontal(pixels, temp, width, height, 4);
            boxVertical(temp, out, width, height, 4);
            System.arraycopy(out, 0, pixels, 0, out.length);
        }
        small.recycle();
        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888);
    }

    private static void boxHorizontal(int[] in, int[] out, int w, int h, int radius) {
        int span = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = in[row + clamp(i, w)];
                a += pixel >>> 24;
                r += (pixel >> 16) & 0xFF;
                g += (pixel >> 8) & 0xFF;
                b += pixel & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((a / span) << 24) | ((r / span) << 16)
                        | ((g / span) << 8) | (b / span);
                int remove = in[row + clamp(x - radius, w)];
                int add = in[row + clamp(x + radius + 1, w)];
                a += (add >>> 24) - (remove >>> 24);
                r += ((add >> 16) & 0xFF) - ((remove >> 16) & 0xFF);
                g += ((add >> 8) & 0xFF) - ((remove >> 8) & 0xFF);
                b += (add & 0xFF) - (remove & 0xFF);
            }
        }
    }

    private static void boxVertical(int[] in, int[] out, int w, int h, int radius) {
        int span = radius * 2 + 1;
        for (int x = 0; x < w; x++) {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            for (int i = -radius; i <= radius; i++) {
                int pixel = in[clamp(i, h) * w + x];
                a += pixel >>> 24;
                r += (pixel >> 16) & 0xFF;
                g += (pixel >> 8) & 0xFF;
                b += pixel & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((a / span) << 24) | ((r / span) << 16)
                        | ((g / span) << 8) | (b / span);
                int remove = in[clamp(y - radius, h) * w + x];
                int add = in[clamp(y + radius + 1, h) * w + x];
                a += (add >>> 24) - (remove >>> 24);
                r += ((add >> 16) & 0xFF) - ((remove >> 16) & 0xFF);
                g += ((add >> 8) & 0xFF) - ((remove >> 8) & 0xFF);
                b += (add & 0xFF) - (remove & 0xFF);
            }
        }
    }

    private static int clamp(int value, int size) {
        return value < 0 ? 0 : (value >= size ? size - 1 : value);
    }
}
