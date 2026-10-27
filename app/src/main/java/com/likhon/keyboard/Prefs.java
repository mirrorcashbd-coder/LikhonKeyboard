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
    static final String KEY_VOICE_LANG = "voice_lang";
    static final String KEY_VOICE_BANGLISH = "voice_banglish";
    static final String KEY_VOICE_CONTINUOUS = "voice_continuous";
    static final String KEY_VOICE_CLEANUP = "voice_cleanup";
    static final String KEY_VOICE_SHEET = "voice_sheet";
    static final String KEY_HEIGHT = "keyboard_height";

    /** Voice language follows the typing language. */
    static final String VOICE_AUTO = "auto";

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

    String voiceLang() {
        return sp.getString(KEY_VOICE_LANG, VOICE_AUTO);
    }

    void setVoiceLang(String value) {
        sp.edit().putString(KEY_VOICE_LANG, value).apply();
    }

    boolean voiceBanglish() {
        return sp.getBoolean(KEY_VOICE_BANGLISH, false);
    }

    boolean voiceContinuous() {
        return sp.getBoolean(KEY_VOICE_CONTINUOUS, true);
    }

    boolean voiceCleanup() {
        return sp.getBoolean(KEY_VOICE_CLEANUP, true);
    }

    /** Use the phone's own voice screen instead of the inline stream. */
    boolean voiceSheet() {
        return sp.getBoolean(KEY_VOICE_SHEET, true);
    }

    /** Keyboard size as a fraction of the calculated height (0.5 – 2.2). */
    float heightScale() {
        return clampScale(sp.getFloat(KEY_HEIGHT, 1f));
    }

    void setHeightScale(float value) {
        sp.edit().putFloat(KEY_HEIGHT, clampScale(value)).apply();
    }

    private static float clampScale(float value) {
        if (value < 0.5f) {
            return 0.5f;
        }
        if (value > 2.2f) {
            return 2.2f;
        }
        return value;
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
