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

    static Palette resolve(Context context, int themeMode) {
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
        return dark ? dark() : light();
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
