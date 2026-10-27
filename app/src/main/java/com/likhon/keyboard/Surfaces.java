package com.likhon.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

/**
 * The app's surface language: opaque, tonal, quietly elevated.
 *
 * <p>Everything is a solid fill with a hairline outline, so the interface
 * stays crisp on every wallpaper, in sunlight and on cheap panels. No blur,
 * no transparency, no background sampling.
 */
final class Surfaces {

    private Surfaces() {
    }

    // ------------------------------------------------------------------ tones

    /** Hairline that separates a surface from the page. */
    static int border(Palette p) {
        return Palette.mix(p.surface, p.text, p.dark ? 0.16f : 0.10f);
    }

    /** A quiet fill between the page and a raised surface (tracks, chips). */
    static int muted(Palette p) {
        return Palette.mix(p.surface, p.bg, p.dark ? 0.5f : 0.65f);
    }

    /** Tinted container for icons and tonal buttons. */
    static int container(Palette p) {
        return Palette.mix(p.surface, p.accent, p.dark ? 0.28f : 0.13f);
    }

    // -------------------------------------------------------------- drawables

    /** Opaque page background; skins keep their vertical gradient. */
    static Drawable background(Context c, Palette p) {
        if (p.bgTop == p.bgBottom) {
            return new ColorDrawable(p.bg);
        }
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{p.bgTop, p.bgBottom});
    }

    static GradientDrawable rounded(Context c, int fill, float radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(Ui.dp(c, radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(Math.max(1, Ui.dp(c, 1f)), strokeColor);
        }
        return drawable;
    }

    /** The standard card / bar surface. */
    static GradientDrawable surface(Context c, Palette p, float radiusDp) {
        return rounded(c, p.surface, radiusDp, border(p));
    }

    /** A field, a track or an inset panel. */
    static GradientDrawable inset(Context c, Palette p, float radiusDp) {
        return rounded(c, muted(p), radiusDp, border(p));
    }

    /** Solid background with a ripple and a matching mask. */
    static RippleDrawable control(Context c, Palette p, float radiusDp, int fill,
                                  int strokeColor, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                rounded(c, fill, radiusDp, strokeColor),
                Ui.rounded(c, 0xFFFFFFFF, radiusDp));
    }

    /** Ripple-only layer for rows and transparent containers. */
    static RippleDrawable ripple(Context c, float radiusDp, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                Ui.rounded(c, 0x00000000, radiusDp),
                Ui.rounded(c, 0xFFFFFFFF, radiusDp));
    }

    /** Bottom sheet: rounded at the top, flush with the screen edge below. */
    static GradientDrawable sheet(Context c, Palette p, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(p.surface);
        float r = Ui.dp(c, radiusDp);
        drawable.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        drawable.setStroke(Math.max(1, Ui.dp(c, 1f)), border(p));
        return drawable;
    }
}
