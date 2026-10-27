package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * A small, blurred copy of whatever sits behind the glass.
 *
 * <p>Android has no "blur my siblings" API: {@code RenderEffect} only affects
 * the node it is attached to. The reliable native technique is to keep a
 * downscaled bitmap of the background, blur it and let every glass surface
 * sample the fragment underneath itself. The bitmap is tiny (a few hundred
 * pixels), so drawing a dozen panes costs almost nothing and the blur is done
 * once, never on the main thread per frame.
 */
final class GlassBackdrop {

    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    private float originX;
    private float originY;
    private float mapW;
    private float mapH;

    private GlassBackdrop(Bitmap bitmap, float mapW, float mapH) {
        this.bitmap = bitmap;
        this.mapW = Math.max(1f, mapW);
        this.mapH = Math.max(1f, mapH);
        paint.setFilterBitmap(true);
    }

    /** The phone wallpaper, already blurred by {@link Wallpaper}. */
    static GlassBackdrop wallpaper(Context context) {
        Bitmap photo = Wallpaper.blurred(context);
        if (photo == null || photo.isRecycled()) {
            return null;
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return new GlassBackdrop(photo, metrics.widthPixels, metrics.heightPixels);
    }

    static GlassBackdrop of(Bitmap bitmap, float mapW, float mapH) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        return new GlassBackdrop(bitmap, mapW, mapH);
    }

    /**
     * Captures a background view (only that view, never its children) and
     * blurs it. Call once after layout, not from onDraw.
     */
    static GlassBackdrop capture(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        int smallW = Math.max(48, Math.min(240, width / 6));
        int smallH = Math.max(48, Math.min(480, height / 6));
        Bitmap small = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        canvas.scale(smallW / (float) width, smallH / (float) height);
        view.draw(canvas);
        blur(small);
        return new GlassBackdrop(small, width, height);
    }

    /** Where the bitmap starts in window coordinates, and how big it is there. */
    void setOrigin(float x, float y, float width, float height) {
        originX = x;
        originY = y;
        mapW = Math.max(1f, width);
        mapH = Math.max(1f, height);
    }

    boolean isReady() {
        return bitmap != null && !bitmap.isRecycled();
    }

    /**
     * Draws the part of the background under a view, magnified by {@code zoom}
     * around the view centre. The magnification is a cheap lens: the background
     * is compressed into the pane the way a thick glass slab would bend it.
     */
    void draw(Canvas canvas, float viewX, float viewY, float w, float h, float zoom) {
        if (!isReady() || w <= 0 || h <= 0) {
            return;
        }
        float scaleX = bitmap.getWidth() / mapW;
        float scaleY = bitmap.getHeight() / mapH;
        float zoomFactor = Math.max(1f, Math.min(1.35f, zoom));
        float cx = (viewX - originX + w / 2f) * scaleX;
        float cy = (viewY - originY + h / 2f) * scaleY;
        float halfW = w / (2f * zoomFactor) * scaleX;
        float halfH = h / (2f * zoomFactor) * scaleY;

        float left = Math.max(0f, cx - halfW);
        float top = Math.max(0f, cy - halfH);
        float right = Math.min(bitmap.getWidth(), cx + halfW);
        float bottom = Math.min(bitmap.getHeight(), cy + halfH);
        if (right - left < 1f || bottom - top < 1f) {
            return;
        }
        src.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
        dst.set(0, 0, w, h);
        canvas.drawBitmap(bitmap, src, dst, paint);
    }

    /** Three cheap box passes: plenty for a bitmap this small. */
    static void blur(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] temp = new int[pixels.length];
        int[] out = new int[pixels.length];
        int radius = Math.max(2, Math.min(6, Math.min(width, height) / 40));
        for (int pass = 0; pass < 3; pass++) {
            horizontal(pixels, temp, width, height, radius);
            vertical(temp, out, width, height, radius);
            System.arraycopy(out, 0, pixels, 0, out.length);
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static void horizontal(int[] in, int[] out, int w, int h, int radius) {
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

    private static void vertical(int[] in, int[] out, int w, int h, int radius) {
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
