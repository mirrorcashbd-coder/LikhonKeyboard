package com.likhon.keyboard;

import android.content.Context;
import android.content.SharedPreferences;


/** Typed access to the app's settings. */
final class Prefs {

    static final String FILE = "likhon_prefs";

    static final String KEY_BANGLA = "default_bangla";
    static final String KEY_LANGUAGE = "language";
    static final String KEY_LANGS = "enabled_langs";
    static final String KEY_THEME = "theme";
    static final String KEY_HAPTIC = "haptic";
    static final String KEY_SOUND = "sound";
    static final String KEY_AUTOCAP = "autocap";
    static final String KEY_HINTS = "hints";
    static final String KEY_VOICE_LANG = "voice_lang";
    static final String KEY_VOICE_BANGLISH = "voice_banglish";
    static final String KEY_VOICE_CONTINUOUS = "voice_continuous";
    static final String KEY_VOICE_CLEANUP = "voice_cleanup";
    static final String KEY_VOICE_ENGINE = "voice_engine";
    static final String KEY_VOICE_MODE = "voice_mode";
    static final String KEY_HEIGHT = "keyboard_height";
    static final String KEY_POPUP = "key_popup";
    static final String KEY_NUMBER_ROW = "number_row";
    static final String KEY_DOUBLE_SPACE = "double_space";
    static final String KEY_ACCENT = "accent";
    static final String KEY_KEY_SHAPE = "key_shape";
    static final String KEY_LABEL_SIZE = "label_size";
    static final String KEY_KEY_BORDERS = "key_borders";
    static final String KEY_KEY_GAP = "key_gap";
    static final String KEY_SUGGESTIONS = "suggestions";
    static final String KEY_SUGGESTION_COUNT = "suggestion_count";
    static final String KEY_VOICE_TRAILING = "voice_trailing_space";
    static final String KEY_INCOGNITO = "incognito";

    /** Voice language follows the typing language. */
    static final String VOICE_AUTO = "auto";

    /** Voice inside the keyboard, then the phone's speech screen if it stays silent. */
    static final String VOICE_MODE_AUTO = "auto";
    /** Only ever use the recogniser inside the keyboard. */
    static final String VOICE_MODE_INLINE = "inline";
    /** Always use the phone's speech screen (Google voice). */
    static final String VOICE_MODE_SCREEN = "screen";

    static final int THEME_SYSTEM = 0;
    static final int THEME_LIGHT = 1;
    static final int THEME_DARK = 2;
    static final int THEME_AURORA = 3;
    static final int THEME_MIDNIGHT = 4;
    static final int THEME_PAPER = 5;
    static final int THEME_GLACIER = 6;
    static final int THEME_NEON = 7;
    static final int THEME_GRAPHITE = 8;

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
        sp.edit()
                .putBoolean(KEY_BANGLA, value)
                .putString(KEY_LANGUAGE, value ? "bn" : "en")
                .apply();
    }

    /** Current language code, e.g. en, bn, hi, ar. */
    String language() {
        String code = sp.getString(KEY_LANGUAGE, "");
        if (code == null || code.isEmpty()) {
            return bangla() ? "bn" : "en";
        }
        return code;
    }

    void setLanguage(String code) {
        sp.edit()
                .putString(KEY_LANGUAGE, code)
                .putBoolean(KEY_BANGLA, "bn".equals(code))
                .apply();
    }

    /** Comma separated codes the language key cycles through. */
    String enabledLanguages() {
        String value = sp.getString(KEY_LANGS, "");
        if (value == null || value.trim().isEmpty()) {
            return "en,bn";
        }
        return value;
    }

    void setEnabledLanguages(String csv) {
        sp.edit().putString(KEY_LANGS, csv).apply();
    }

    boolean languageEnabled(String code) {
        for (String part : enabledLanguages().split(",")) {
            if (part.trim().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** Enables or disables one language, always keeping at least one. */
    void setLanguageEnabled(String code, boolean on) {
        StringBuilder out = new StringBuilder();
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        for (String part : enabledLanguages().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(code)) {
                codes.add(trimmed);
            }
        }
        if (on) {
            codes.add(code);
        }
        if (codes.isEmpty()) {
            codes.add("en");
        }
        for (String value : codes) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(value);
        }
        setEnabledLanguages(out.toString());
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

    /** Pinned recognition service ("pkg/cls"), or "" for the phone's default. */
    String voiceEngine() {
        return sp.getString(KEY_VOICE_ENGINE, "");
    }

    void setVoiceEngine(String value) {
        sp.edit().putString(KEY_VOICE_ENGINE, value == null ? "" : value).apply();
    }

    String voiceMode() {
        return sp.getString(KEY_VOICE_MODE, VOICE_MODE_AUTO);
    }

    void setVoiceMode(String value) {
        sp.edit().putString(KEY_VOICE_MODE, value).apply();
    }

    int accent() {
        return sp.getInt(KEY_ACCENT, 0);
    }

    int keyShape() {
        return sp.getInt(KEY_KEY_SHAPE, 1);
    }

    void setKeyShape(int value) {
        sp.edit().putInt(KEY_KEY_SHAPE, value).apply();
    }

    int labelSize() {
        return sp.getInt(KEY_LABEL_SIZE, 1);
    }

    void setLabelSize(int value) {
        sp.edit().putInt(KEY_LABEL_SIZE, value).apply();
    }

    /** Keyboard key spacing in dp: 4 = compact, 6 = normal, 9 = airy. */
    float keyGap() {
        int value = sp.getInt(KEY_KEY_GAP, 1);
        return value == 0 ? 4f : (value == 2 ? 9f : 6f);
    }

    void setKeyGap(int value) {
        sp.edit().putInt(KEY_KEY_GAP, value).apply();
    }

    boolean keyBorders() {
        return sp.getBoolean(KEY_KEY_BORDERS, true);
    }

    boolean suggestionsEnabled() {
        return sp.getBoolean(KEY_SUGGESTIONS, true);
    }

    int suggestionCount() {
        int value = sp.getInt(KEY_SUGGESTION_COUNT, 6);
        return value < 3 ? 3 : (value > 12 ? 12 : value);
    }

    void setSuggestionCount(int value) {
        sp.edit().putInt(KEY_SUGGESTION_COUNT, value).apply();
    }

    boolean voiceTrailingSpace() {
        return sp.getBoolean(KEY_VOICE_TRAILING, true);
    }

    boolean incognito() {
        return sp.getBoolean(KEY_INCOGNITO, false);
    }

    void setIncognito(boolean value) {
        sp.edit().putBoolean(KEY_INCOGNITO, value).apply();
    }

    boolean showPopup() {
        return sp.getBoolean(KEY_POPUP, true);
    }

    boolean numberRow() {
        return sp.getBoolean(KEY_NUMBER_ROW, false);
    }

    boolean doubleSpace() {
        return sp.getBoolean(KEY_DOUBLE_SPACE, false);
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
