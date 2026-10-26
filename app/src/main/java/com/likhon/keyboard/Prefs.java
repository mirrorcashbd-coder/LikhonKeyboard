package com.likhon.keyboard;

import android.content.Context;
import android.content.SharedPreferences;


/** Typed access to the app's settings. */
final class Prefs {

    static final String FILE = "likhon_prefs";

    static final String KEY_BANGLA = "default_bangla";
    static final String KEY_THEME = "theme";
    static final String KEY_HAPTIC = "haptic";
    static final String KEY_SOUND = "sound";
    static final String KEY_AUTOCAP = "autocap";
    static final String KEY_HINTS = "hints";

    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;

    private final SharedPreferences sp;

    Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean bangla() {
        if (!sp.contains(KEY_BANGLA)) {
            // First run: follow a Bangla system language.
            return "bn".equals(java.util.Locale.getDefault().getLanguage());
        }
        return sp.getBoolean(KEY_BANGLA, false);
    }

    void setBangla(boolean value) {
        sp.edit().putBoolean(KEY_BANGLA, value).apply();
    }

    int theme() {
        return sp.getInt(KEY_THEME, THEME_SYSTEM);
    }

    void setTheme(int value) {
        sp.edit().putInt(KEY_THEME, value).apply();
    }

    boolean haptic() {
        return sp.getBoolean(KEY_HAPTIC, true);
    }

    boolean sound() {
        return sp.getBoolean(KEY_SOUND, false);
    }

    boolean autoCap() {
        return sp.getBoolean(KEY_AUTOCAP, true);
    }

    boolean hints() {
        return sp.getBoolean(KEY_HINTS, true);
    }

    boolean getBoolean(String key, boolean fallback) {
        return sp.getBoolean(key, fallback);
    }

    void setBoolean(String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
    }

    SharedPreferences raw() {
        return sp;
    }

    void register(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sp.registerOnSharedPreferenceChangeListener(listener);
    }

    void unregister(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
