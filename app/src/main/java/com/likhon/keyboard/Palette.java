package com.likhon.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * Colour palette shared by the keyboard and the settings screen. Keeping the
 * colours in code (instead of resource qualifiers) lets the user override the
 * theme without fighting the system configuration.
 *
 * <p>Besides the plain light/dark pair there are six designed skins. A skin can
 * bring its own vertical gradient ({@link #bgTop} → {@link #bgBottom}), a soft
 * key shadow and an accent glow; the plain themes leave both at zero so the
 * keyboard keeps the flat, fast look it had before.
 */
final class Palette {

    final boolean dark;

    final int bg;
    final int bgTop;
    final int bgBottom;
    final int surface;
    final int surfaceAlt;
    final int keyBg;
    final int keyPressed;
    final int keySpecial;
    final int keySpecialPressed;
    final int keyStroke;
    final int text;
    final int textMuted;
    final int accent;
    final int accentPressed;
    final int onAccent;
    final int popupBg;
    final int divider;
    final int success;
    /** Drawn under every key; zero means no shadow. */
    final int shadow;
    /** Halo around accent keys; zero means none. */
    final int glow;

    private Palette(boolean dark, int bg, int bgTop, int bgBottom, int surface, int surfaceAlt,
                    int keyBg, int keyPressed, int keySpecial, int keySpecialPressed,
                    int keyStroke, int text, int textMuted, int accent, int accentPressed,
                    int onAccent, int popupBg, int divider, int success, int shadow, int glow) {
        this.dark = dark;
        this.bg = bg;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.surface = surface;
        this.surfaceAlt = surfaceAlt;
        this.keyBg = keyBg;
        this.keyPressed = keyPressed;
        this.keySpecial = keySpecial;
        this.keySpecialPressed = keySpecialPressed;
        this.keyStroke = keyStroke;
        this.text = text;
        this.textMuted = textMuted;
        this.accent = accent;
        this.accentPressed = accentPressed;
        this.onAccent = onAccent;
        this.popupBg = popupBg;
        this.divider = divider;
        this.success = success;
        this.shadow = shadow;
        this.glow = glow;
    }

    boolean hasGradient() {
        return bgTop != bgBottom;
    }

    // ------------------------------------------------------------- themes

    static Palette light() {
        return new Palette(false,
                0xFFEEF1F6, 0xFFEEF1F6, 0xFFEEF1F6,
                0xFFFFFFFF, 0xFFF7F9FC,
                0xFFFFFFFF, 0xFFDCE4F0, 0xFFE3E9F2, 0xFFD2DBE8, 0x14000000,
                0xFF121821, 0xFF66717F,
                0xFF2F6FED, 0xFF2158C4, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFE4E9F1, 0xFF1F9D55,
                0x12000000, 0);
    }

    static Palette dark() {
        return new Palette(true,
                0xFF0A0E13, 0xFF0A0E13, 0xFF0A0E13,
                0xFF141A22, 0xFF1A212B,
                0xFF1D242E, 0xFF2E3846, 0xFF151C25, 0xFF232C38, 0x22FFFFFF,
                0xFFEDF2F9, 0xFF8A97A9,
                0xFF6E9BFF, 0xFF5484F0, 0xFF071224,
                0xFF222B37, 0xFF232C38, 0xFF3DD07A,
                0x33000000, 0);
    }

    /** Indigo night with a mint accent and a very soft glow. */
    static Palette aurora() {
        return new Palette(true,
                0xFF0B1026, 0xFF0B1026, 0xFF2A1550,
                0xFF151A33, 0xFF1B2140,
                0xFF1C2140, 0xFF2E3766, 0xFF151A33, 0xFF232A4D, 0x33FFFFFF,
                0xFFF1F5FF, 0xFF9AA6C9,
                0xFF4FD1C5, 0xFF2FB3A8, 0xFF04201E,
                0xFF232A4D, 0x332B3560, 0xFF4ADE80,
                0x55000000, 0x334FD1C5);
    }

    /** Almost black, with a warm gold accent. */
    static Palette midnight() {
        return new Palette(true,
                0xFF05070C, 0xFF05070C, 0xFF0E1626,
                0xFF0F1522, 0xFF141B29,
                0xFF141B29, 0xFF1F293B, 0xFF0F1522, 0xFF1A2333, 0x26FFFFFF,
                0xFFEAF0FA, 0xFF8492A8,
                0xFFF0B04C, 0xFFC98E2F, 0xFF1A1205,
                0xFF1B2333, 0x26202A3D, 0xFF4ADE80,
                0x55000000, 0x33F0B04C);
    }

    /** Warm cream with a terracotta accent. */
    static Palette paper() {
        return new Palette(false,
                0xFFFAF6EF, 0xFFFAF6EF, 0xFFEFE7DA,
                0xFFFFFFFF, 0xFFF6F0E6,
                0xFFFFFFFF, 0xFFE8DFD1, 0xFFF2EBDF, 0xFFE0D5C3, 0x1A6B5B45,
                0xFF2C2620, 0xFF7A6E5E,
                0xFFC8603F, 0xFFA64A2E, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFE6DCCB, 0xFF2E9E5B,
                0x1A6B5B45, 0);
    }

    /** Cool white-blue with an icy accent. */
    static Palette glacier() {
        return new Palette(false,
                0xFFF4F9FF, 0xFFF4F9FF, 0xFFE1ECFA,
                0xFFFFFFFF, 0xFFF1F7FE,
                0xFFFFFFFF, 0xFFD8E6F7, 0xFFEAF2FC, 0xFFD2E2F5, 0x14204060,
                0xFF14263A, 0xFF62748A,
                0xFF1176C9, 0xFF0B5C9E, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFDCE7F4, 0xFF1F9D55,
                0x18204060, 0);
    }

    /** Black with electric lime and a real glow. */
    static Palette neon() {
        return new Palette(true,
                0xFF030503, 0xFF030503, 0xFF0A1206,
                0xFF0A0F0A, 0xFF0E1410,
                0xFF0E1410, 0xFF1A2418, 0xFF090D0A, 0xFF141C12, 0x33B6FF3C,
                0xFFE8FFD6, 0xFF8FA57A,
                0xFFB6FF3C, 0xFF8CD41F, 0xFF0A1400,
                0xFF141C12, 0x33B6FF3C, 0xFFB6FF3C,
                0x66000000, 0x44B6FF3C);
    }

    /** Calm grey with a red accent. */
    static Palette graphite() {
        return new Palette(false,
                0xFFF2F3F5, 0xFFF2F3F5, 0xFFE4E7EC,
                0xFFFFFFFF, 0xFFF5F6F8,
                0xFFFCFCFD, 0xFFDDE1E7, 0xFFEDEFF3, 0xFFD6DAE1, 0x1A1F2937,
                0xFF1B2027, 0xFF6B7280,
                0xFFE0483E, 0xFFB9352D, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFE1E4E9, 0xFF1F9D55,
                0x1A1F2937, 0);
    }

    /** Accent choices, light accent + light pressed + dark accent + dark pressed. */
    private static final int[][] ACCENTS = {
            {0xFF2F6FED, 0xFF2158C4, 0xFF6E9BFF, 0xFF5484F0},
            {0xFF0E9F9F, 0xFF0A7D7D, 0xFF45D4D4, 0xFF2FB3B3},
            {0xFFE0455E, 0xFFB93449, 0xFFFF7C90, 0xFFE85C71},
            {0xFFE08A00, 0xFFB36E00, 0xFFFFB43D, 0xFFE89A1A},
            {0xFF7C5CE0, 0xFF5F41BF, 0xFFA48CFF, 0xFF8E72F0},
            {0xFF4A5568, 0xFF343E4C, 0xFF8FA0B8, 0xFF6E7F96},
    };

    static int accentCount() {
        return ACCENTS.length;
    }

    static int accentColor(int index, boolean dark) {
        int i = Math.max(0, Math.min(ACCENTS.length - 1, index));
        return ACCENTS[i][dark ? 2 : 0];
    }

    private Palette withAccent(int accentColor, int pressedColor) {
        return new Palette(dark, bg, bgTop, bgBottom, surface, surfaceAlt, keyBg, keyPressed,
                keySpecial, keySpecialPressed, keyStroke, text, textMuted, accentColor,
                pressedColor, onAccent, popupBg, divider, success, shadow, glow);
    }

    static Palette resolve(Context context, int themeMode) {
        return resolve(context, themeMode, 0);
    }

    static Palette resolve(Context context, int themeMode, int accentIndex) {
        Palette base;
        switch (themeMode) {
            case Prefs.THEME_LIGHT:
                base = light();
                break;
            case Prefs.THEME_DARK:
                base = dark();
                break;
            case Prefs.THEME_AURORA:
                base = aurora();
                break;
            case Prefs.THEME_MIDNIGHT:
                base = midnight();
                break;
            case Prefs.THEME_PAPER:
                base = paper();
                break;
            case Prefs.THEME_GLACIER:
                base = glacier();
                break;
            case Prefs.THEME_NEON:
                base = neon();
                break;
            case Prefs.THEME_GRAPHITE:
                base = graphite();
                break;
            default:
                int mode = context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                base = mode == Configuration.UI_MODE_NIGHT_YES ? dark() : light();
                break;
        }
        if (accentIndex <= 0) {
            return base;
        }
        int i = Math.min(accentIndex, ACCENTS.length - 1);
        return base.withAccent(ACCENTS[i][base.dark ? 2 : 0], ACCENTS[i][base.dark ? 3 : 1]);
    }

    /** The palette of a named theme, without a context (used by previews). */
    static Palette preview(int themeMode, boolean systemDark, int accentIndex) {
        Palette base;
        switch (themeMode) {
            case Prefs.THEME_LIGHT:
                base = light();
                break;
            case Prefs.THEME_DARK:
                base = dark();
                break;
            case Prefs.THEME_AURORA:
                base = aurora();
                break;
            case Prefs.THEME_MIDNIGHT:
                base = midnight();
                break;
            case Prefs.THEME_PAPER:
                base = paper();
                break;
            case Prefs.THEME_GLACIER:
                base = glacier();
                break;
            case Prefs.THEME_NEON:
                base = neon();
                break;
            case Prefs.THEME_GRAPHITE:
                base = graphite();
                break;
            default:
                base = systemDark ? dark() : light();
                break;
        }
        if (accentIndex <= 0) {
            return base;
        }
        int i = Math.min(accentIndex, ACCENTS.length - 1);
        return base.withAccent(ACCENTS[i][base.dark ? 2 : 0], ACCENTS[i][base.dark ? 3 : 1]);
    }

    /** Slightly darker/lighter version of a colour, used for key press states. */
    static int mix(int color, int other, float amount) {
        float a = 1f - amount;
        return Color.argb(
                Math.round(Color.alpha(color) * a + Color.alpha(other) * amount),
                Math.round(Color.red(color) * a + Color.red(other) * amount),
                Math.round(Color.green(color) * a + Color.green(other) * amount),
                Math.round(Color.blue(color) * a + Color.blue(other) * amount));
    }
}
