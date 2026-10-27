package com.likhon.keyboard;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * Colour palette shared by the keyboard and the settings screen. Keeping the
 * colours in code (instead of resource qualifiers) lets the user override the
 * theme without fighting the system configuration.
 */
final class Palette {

    final boolean dark;

    final int bg;
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

    private Palette(boolean dark, int bg, int surface, int surfaceAlt, int keyBg, int keyPressed,
                    int keySpecial, int keySpecialPressed, int keyStroke, int text, int textMuted,
                    int accent, int accentPressed, int onAccent, int popupBg, int divider, int success) {
        this.dark = dark;
        this.bg = bg;
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
    }

    static Palette light() {
        return new Palette(false,
                0xFFEEF1F6, 0xFFFFFFFF, 0xFFF7F9FC,
                0xFFFFFFFF, 0xFFDCE4F0, 0xFFE3E9F2, 0xFFD2DBE8, 0x14000000,
                0xFF121821, 0xFF66717F,
                0xFF2F6FED, 0xFF2158C4, 0xFFFFFFFF,
                0xFFFFFFFF, 0xFFE4E9F1, 0xFF1F9D55);
    }

    static Palette dark() {
        return new Palette(true,
                0xFF0A0E13, 0xFF141A22, 0xFF1A212B,
                0xFF1D242E, 0xFF2E3846, 0xFF151C25, 0xFF232C38, 0x22FFFFFF,
                0xFFEDF2F9, 0xFF8A97A9,
                0xFF6E9BFF, 0xFF5484F0, 0xFF071224,
                0xFF222B37, 0xFF232C38, 0xFF3DD07A);
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
        return new Palette(dark, bg, surface, surfaceAlt, keyBg, keyPressed, keySpecial,
                keySpecialPressed, keyStroke, text, textMuted, accentColor, pressedColor,
                onAccent, popupBg, divider, success);
    }

    static Palette resolve(Context context, int themeMode) {
        return resolve(context, themeMode, 0);
    }

    static Palette resolve(Context context, int themeMode, int accentIndex) {
        boolean dark;
        if (themeMode == Prefs.THEME_LIGHT) {
            dark = false;
        } else if (themeMode == Prefs.THEME_DARK) {
            dark = true;
        } else {
            int mode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            dark = mode == Configuration.UI_MODE_NIGHT_YES;
        }
        Palette base = dark ? dark() : light();
        if (accentIndex <= 0) {
            return base;
        }
        int i = Math.min(accentIndex, ACCENTS.length - 1);
        return base.withAccent(ACCENTS[i][dark ? 2 : 0], ACCENTS[i][dark ? 3 : 1]);
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
