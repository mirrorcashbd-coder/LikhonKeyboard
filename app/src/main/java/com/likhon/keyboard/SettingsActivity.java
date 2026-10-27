package com.likhon.keyboard;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Setup + settings screen. Deliberately one short screen: enable the keyboard,
 * pick it, try it, then adjust the few options that matter.
 */
public class SettingsActivity extends Activity {

    /** When the keyboard sends the user here to grant the mic permission. */
    public static final String EXTRA_REQUEST_MIC = "request_mic";
    private static final int REQUEST_MIC = 41;
    private static final int REQUEST_EXPORT = 42;
    private static final int REQUEST_IMPORT = 43;

    private Prefs prefs;
    private Palette palette;

    private View statusDot;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView enableButton;
    private TextView chooseButton;

    /** Opens settings straight at a section (the gear in the keyboard toolbar). */
    static final String EXTRA_PANEL = "panel";
    static final String EXTRA_OPEN = "open_screen";
    static final String PANEL_EDITING = "editing";

    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_APPEARANCE = "appearance";
    private static final String SCREEN_TYPING = "typing";
    private static final String SCREEN_LANGUAGES = "languages";
    private static final String SCREEN_VOICE = "voice";
    private static final String SCREEN_TOOLS = "tools";
    private static final String SCREEN_STICKERS = "stickers";
    private static final String SCREEN_BACKUP = "backup";
    private static final String SCREEN_ABOUT = "about";

    private FrameLayout screenHost;
    private final java.util.LinkedHashMap<String, View> screens = new java.util.LinkedHashMap<>();
    private final java.util.ArrayList<String> stack = new java.util.ArrayList<>();

    private TextView chipEnglish;
    private TextView chipBangla;
    private LinearLayout themeChips;
    private LinearLayout voiceLangChips;
    private LinearLayout voiceEngineChips;
    private LinearLayout voiceModeChips;
    private TextView micStatus;
    private TextView micButton;
    private TextView micTestResult;
    private TextView voiceTestResult;
    private TextView voiceLogView;
    private TextView clipStoreCount;
    private EditText shortcutKeyInput;
    private EditText shortcutValueInput;
    private LinearLayout shortcutList;
    private TextView learnedCount;
    private LinearLayout languageChips;
    private TextView languageNow;

    /** Sticker screen state: selected pack id (null = all) and query. */
    private String stickerFilter;
    private String stickerQuery = "";
    private LinearLayout stickerChips;
    private LinearLayout stickerGrid;
    private TextView stickerCountView;
    private EditText stickerSearch;
    private StickerStore stickerStore;
    private SpeechRecognizer speechTest;
    private final Handler testHandler = new Handler(Looper.getMainLooper());
    private final Runnable speechTimeout = () ->
            finishSpeechTest(getString(R.string.voice_test_empty));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = new Prefs(this);
        palette = Palette.resolve(this, prefs.theme(), prefs.accent());
        setTheme(palette.dark ? R.style.Theme_Likhon_Dark : R.style.Theme_Likhon_Light);
        super.onCreate(savedInstanceState);
        configureWindow();
        screenHost = new FrameLayout(this);
        screenHost.setBackgroundColor(palette.bgTop);
        setContentView(screenHost);
        buildScreens();
        stack.add(SCREEN_HOME);
        show(SCREEN_HOME, false);
        String requested = getIntent().getStringExtra(EXTRA_OPEN);
        getIntent().removeExtra(EXTRA_OPEN);
        if (requested != null && screens.containsKey(requested)) {
            open(requested);
        } else if (PANEL_EDITING.equals(getIntent().getStringExtra(EXTRA_PANEL))) {
            open(SCREEN_TYPING);
        }
        refreshStatus();
        refreshVoiceStatus();
        if (getIntent().getBooleanExtra(EXTRA_REQUEST_MIC, false) && !hasMicPermission()) {
            requestMicPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshVoiceStatus();
        refreshVoiceLog();
        refreshClipStore();
    }

    private void refreshClipStore() {
        if (clipStoreCount == null) {
            return;
        }
        int count = new ClipStore(this).size();
        clipStoreCount.setText(count == 0
                ? getString(R.string.settings_clipboard_empty)
                : getString(R.string.settings_clipboard_count, count));
    }

    private void confirmClearClipStore() {
        final ClipStore store = new ClipStore(this);
        if (store.size() == 0) {
            return;
        }
        AppDialog.show(this, palette, getString(R.string.settings_clipboard),
                getString(R.string.clip_clear_confirm, store.size()),
                getString(R.string.clip_clear), () -> {
                    store.clear();
                    refreshClipStore();
                },
                getString(android.R.string.cancel), null);
    }

    // ------------------------------------------------------- new settings bits

    private interface IntAction {
        void run(int value);
    }

    private void addAccentChips(LinearLayout parent) {
        LinearLayout row = Ui.horizontal(this);
        for (int i = 0; i < Palette.accentCount(); i++) {
            final int index = i;
            boolean selected = prefs.accent() == i;
            int color = Palette.accentColor(i, palette.dark);

            FrameLayout cell = new FrameLayout(this);
            FrameLayout dot = new FrameLayout(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            circle.setStroke(dp(selected ? 3 : 1), selected ? 0xFFFFFFFF : 0x99FFFFFF);
            GradientDrawable ovalMask = new GradientDrawable();
            ovalMask.setShape(GradientDrawable.OVAL);
            ovalMask.setColor(0xFFFFFFFF);
            dot.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x44FFFFFF), circle, ovalMask));
            dot.setElevation(dp(selected ? 3 : 1));

            if (selected) {
                double lum = 0.2126 * android.graphics.Color.red(color)
                        + 0.7152 * android.graphics.Color.green(color)
                        + 0.0722 * android.graphics.Color.blue(color);
                TextView check = Ui.text(this, "\u2713", 15,
                        lum > 140 ? 0xFF111111 : 0xFFFFFFFF, true);
                check.setGravity(Gravity.CENTER);
                dot.addView(check, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }

            dot.setClickable(true);
            dot.setOnClickListener(v -> {
                prefs.raw().edit().putInt(Prefs.KEY_ACCENT, index).apply();
                recreateAt(SCREEN_APPEARANCE);
            });
            cell.addView(dot, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            row.addView(cell, new LinearLayout.LayoutParams(0, dp(46), 1f));
        }
        addWithTopMargin(parent, row, 4);
    }

    private void addChipGrid(LinearLayout parent, String[] labels, int selected,
                             IntAction action, int perRow, int topMarginDp) {
        CharSequence[] items = new CharSequence[labels.length];
        System.arraycopy(labels, 0, items, 0, labels.length);
        LinearLayout track = M3.segmented(this, palette, items, selected, action::run);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(Math.max(0, topMarginDp));
        parent.addView(track, params);
    }

    private EditText smallField(String hint) {
        EditText field = new EditText(this);
        field.setTextSize(13);
        field.setTextColor(palette.text);
        field.setHintTextColor(palette.textMuted);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setBackground(Surfaces.inset(this, palette, 14));
        field.setPadding(dp(12), dp(10), dp(12), dp(10));
        return field;
    }

    private void addShortcut() {
        if (shortcutKeyInput == null || shortcutValueInput == null) {
            return;
        }
        String key = shortcutKeyInput.getText().toString().trim();
        String value = shortcutValueInput.getText().toString().trim();
        if (key.isEmpty() || value.isEmpty()) {
            Toast.makeText(this, R.string.shortcuts_need_both, Toast.LENGTH_SHORT).show();
            return;
        }
        new Shortcuts(this).put(key, value);
        shortcutKeyInput.setText("");
        shortcutValueInput.setText("");
        rebuildShortcutList();
    }

    private void rebuildShortcutList() {
        if (shortcutList == null) {
            return;
        }
        shortcutList.removeAllViews();
        Map<String, String> map = new Shortcuts(this).all();
        if (map.isEmpty()) {
            shortcutList.addView(Ui.text(this, getString(R.string.shortcuts_empty), 12,
                    palette.textMuted, false));
            return;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            LinearLayout row = Ui.horizontal(this);
            TextView text = Ui.text(this, entry.getKey() + "  \u2192  " + entry.getValue(),
                    13, palette.text, false);
            text.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(text);
            TextView delete = Ui.text(this, "\u2715", 14, palette.textMuted, true);
            delete.setPadding(dp(10), dp(6), dp(4), dp(6));
            delete.setOnClickListener(v -> {
                new Shortcuts(this).remove(entry.getKey());
                rebuildShortcutList();
            });
            row.addView(delete);
            addWithTopMargin(shortcutList, row, 6);
        }
    }

    private void rebuildLanguageChips() {
        if (languageChips == null) {
            return;
        }
        Language current = Languages.byCode(prefs.language());
        if (languageNow != null) {
            languageNow.setText(getString(R.string.languages_now, current.nativeName,
                    current.english));
        }
        languageChips.removeAllViews();
        List<Language> all = Languages.all();
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % 2 == 0) {
                row = Ui.horizontal(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(8);
                }
                languageChips.addView(row, rowParams);
            }
            final Language language = all.get(i);
            boolean enabled = prefs.languageEnabled(language.code);
            boolean active = language.code.equals(current.code);
            String label = language.nativeName + (enabled ? "  \u2713" : "");
            TextView chip = makeChip(label, enabled, () -> {
                boolean on = prefs.languageEnabled(language.code);
                if (on && prefs.enabledLanguages().split(",").length <= 1) {
                    Toast.makeText(this, R.string.languages_keep_one,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.setLanguageEnabled(language.code, !on);
                if (!on) {
                    prefs.setLanguage(language.code);
                }
                rebuildLanguageChips();
                updateLanguageChips();
            });
            chip.setTextSize(12);
            chip.setSingleLine(true);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (active) {
                chip.setTextColor(palette.onAccent);
                chip.setBackground(Ui.ripple(this, palette.accent, 15, 0x33FFFFFF));
                chip.setElevation(dp(2));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 != 0) {
                params.leftMargin = dp(8);
            }
            row.addView(chip, params);
        }
    }

    private void refreshLearnedCount() {
        if (learnedCount == null) {
            return;
        }
        int count = new LearnedWords(this).size();
        learnedCount.setText(getString(R.string.learned_count, count));
    }

    private void confirmClearLearned() {
        AppDialog.show(this, palette, getString(R.string.section_learned),
                getString(R.string.learned_clear_confirm),
                getString(R.string.learned_clear), () -> {
                    new LearnedWords(this).clear();
                    refreshLearnedCount();
                },
                getString(android.R.string.cancel), null);
    }

    private void confirmResetAll() {
        AppDialog.show(this, palette, getString(R.string.reset_all),
                getString(R.string.reset_all_confirm),
                getString(R.string.reset_all), () -> {
                    prefs.raw().edit().clear().apply();
                    recreate();
                },
                getString(android.R.string.cancel), null);
    }

    private void configureWindow() {
        Window window = getWindow();
        // Edge to edge: the blurred wallpaper runs under the bars, exactly as
        // on the showcase screen. The screens add the insets themselves.
        window.setStatusBarColor(0x00000000);
        window.setNavigationBarColor(0x00000000);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (palette.dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    // ------------------------------------------------------- screens & flow

    private View subScreen(int titleRes, int subtitleRes, LinearLayout body) {
        return M3.screen(this, palette, M3.topBar(this, palette,
                getString(titleRes), getString(subtitleRes), this::goBack, false), body);
    }

    private void addCard(LinearLayout body, View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        body.addView(card, params);
    }

    private void addTileRow(LinearLayout grid, View left, View right) {
        LinearLayout row = Ui.horizontal(this);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, dp(126), 1f);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, dp(126), 1f);
        rightParams.leftMargin = dp(10);
        row.addView(left, leftParams);
        row.addView(right, rightParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (grid.getChildCount() > 0) {
            rowParams.topMargin = dp(10);
        }
        grid.addView(row, rowParams);
    }

    private void buildScreens() {
        screens.put(SCREEN_HOME, buildHomeScreen());
        screens.put(SCREEN_APPEARANCE, buildAppearanceScreen());
        screens.put(SCREEN_TYPING, buildTypingScreen());
        screens.put(SCREEN_LANGUAGES, buildLanguagesScreen());
        screens.put(SCREEN_VOICE, buildVoiceScreen());
        screens.put(SCREEN_TOOLS, buildToolsScreen());
        screens.put(SCREEN_STICKERS, buildStickersScreen());
        screens.put(SCREEN_BACKUP, buildBackupScreen());
        screens.put(SCREEN_ABOUT, buildAboutScreen());
    }

    private void show(String key, boolean animate) {
        View next = screens.get(key);
        if (next == null) {
            return;
        }
        screenHost.removeAllViews();
        screenHost.addView(next, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (animate) {
            next.setAlpha(0f);
            next.setTranslationX(dp(26));
            next.animate().alpha(1f).translationX(0f).setDuration(190).start();
        }
    }

    private void open(String key) {
        if (!stack.isEmpty() && stack.get(stack.size() - 1).equals(key)) {
            return;
        }
        stack.add(key);
        show(key, true);
    }

    /** Rebuild after a preference change, then come back to the same screen. */
    private void recreateAt(String screen) {
        getIntent().putExtra(EXTRA_OPEN, screen);
        recreate();
    }

    private void goBack() {
        if (stack.size() <= 1) {
            finish();
            return;
        }
        stack.remove(stack.size() - 1);
        View previous = screens.get(stack.get(stack.size() - 1));
        if (previous == null) {
            return;
        }
        screenHost.removeAllViews();
        screenHost.addView(previous, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        previous.setAlpha(0f);
        previous.setTranslationX(-dp(20));
        previous.animate().alpha(1f).translationX(0f).setDuration(170).start();
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            goBack();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------ home screen

    private View buildHomeScreen() {
        LinearLayout body = M3.body(this);

        // ------------------------------------------------------- status card
        LinearLayout statusCard = M3.card(this, palette);
        LinearLayout statusTop = Ui.horizontal(this);
        statusDot = Ui.dot(this, palette.textMuted, 10);
        statusTop.addView(statusDot);
        statusTitle = Ui.text(this, "", 16, palette.text, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(10);
        statusTop.addView(statusTitle, titleParams);
        statusCard.addView(statusTop);

        statusBody = Ui.text(this, "", 12.5f, palette.textMuted, false);
        statusBody.setPadding(0, dp(8), 0, 0);
        statusCard.addView(statusBody);

        LinearLayout buttons = Ui.horizontal(this);
        enableButton = M3.tonalButton(this, palette,
                getString(R.string.action_enable), this::openKeyboardSettings);
        chooseButton = M3.filledButton(this, palette,
                getString(R.string.action_choose), this::showKeyboardPicker);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        first.rightMargin = dp(8);
        buttons.addView(enableButton, first);
        buttons.addView(chooseButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(16);
        statusCard.addView(buttons, buttonsParams);
        body.addView(statusCard);

        // ------------------------------------------------------ quick tiles
        body.addView(M3.sectionLabel(this, palette, getString(R.string.home_quick)));
        LinearLayout grid = Ui.vertical(this);
        addTileRow(grid,
                M3.tile(this, palette, R.drawable.ic_droplet,
                        getString(R.string.section_appearance),
                        getString(R.string.tile_appearance_sub),
                        () -> open(SCREEN_APPEARANCE)),
                M3.tile(this, palette, R.drawable.ic_keyboard,
                        getString(R.string.section_typing),
                        getString(R.string.tile_typing_sub),
                        () -> open(SCREEN_TYPING)));
        addTileRow(grid,
                M3.tile(this, palette, R.drawable.ic_globe,
                        getString(R.string.section_languages),
                        getString(R.string.tile_languages_sub),
                        () -> open(SCREEN_LANGUAGES)),
                M3.tile(this, palette, R.drawable.ic_mic,
                        getString(R.string.section_voice),
                        getString(R.string.tile_voice_sub),
                        () -> open(SCREEN_VOICE)));
        addTileRow(grid,
                M3.tile(this, palette, R.drawable.ic_tune,
                        getString(R.string.screen_tools),
                        getString(R.string.tile_tools_sub),
                        () -> open(SCREEN_TOOLS)),
                M3.tile(this, palette, R.drawable.ic_sticker,
                        getString(R.string.section_stickers),
                        getString(R.string.tile_stickers_sub),
                        () -> open(SCREEN_STICKERS)));
        addTileRow(grid,
                M3.tile(this, palette, R.drawable.ic_archive,
                        getString(R.string.section_backup),
                        getString(R.string.tile_backup_sub),
                        () -> open(SCREEN_BACKUP)),
                M3.tile(this, palette, R.drawable.ic_info,
                        getString(R.string.section_about),
                        getString(R.string.about_short),
                        () -> open(SCREEN_ABOUT)));
        body.addView(grid);

        // ---------------------------------------------------------- more rows
        body.addView(M3.sectionLabel(this, palette, getString(R.string.home_more)));
        LinearLayout more = M3.bareCard(this, palette);
        more.setPadding(dp(6), dp(6), dp(6), dp(6));
        more.addView(M3.navRow(this, palette, R.drawable.ic_info,
                getString(R.string.guide_title), getString(R.string.home_guide_sub),
                this::showGuide));
        more.addView(M3.divider(this, palette));
        more.addView(M3.navRow(this, palette, R.drawable.ic_keyboard,
                getString(R.string.home_keyboard), getString(R.string.home_keyboard_sub),
                this::openKeyboardSettings));
        addCard(body, more);

        // ------------------------------------------------------- try-it card
        LinearLayout tryCard = M3.card(this, palette);
        TextView tryTitle = Ui.text(this, getString(R.string.try_title), 14.5f,
                palette.text, true);
        tryCard.addView(tryTitle);

        EditText field = new EditText(this);
        field.setTextSize(16);
        field.setTextColor(palette.text);
        field.setHintTextColor(palette.textMuted);
        field.setHint(getString(R.string.try_hint));
        field.setSingleLine(false);
        field.setMaxLines(4);
        field.setMinHeight(dp(58));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setBackground(Surfaces.inset(this, palette, 14));
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(12);
        tryCard.addView(field, fieldParams);
        addCard(body, tryCard);

        return M3.screen(this, palette, M3.topBar(this, palette,
                getString(R.string.app_name), getString(R.string.tagline), null, true), body);
    }

    // ------------------------------------------------------ appearance screen

    private View buildAppearanceScreen() {
        LinearLayout body = M3.body(this);

        LinearLayout themeCard = M3.card(this, palette);
        themeChips = Ui.vertical(this);
        themeCard.addView(themeChips);

        ThemePreviewView preview = new ThemePreviewView(this);
        preview.setPalette(palette);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(118));
        previewParams.topMargin = dp(14);
        themeCard.addView(preview, previewParams);
        rebuildThemeChips();
        body.addView(M3.sectionLabel(this, palette, getString(R.string.theme_label)));
        body.addView(themeCard);

        LinearLayout accentCard = M3.card(this, palette);
        addAccentChips(accentCard);
        addSection(body, getString(R.string.accent_section), null, accentCard);

        LinearLayout keysCard = M3.card(this, palette);
        TextView shapeLabel = Ui.text(this, getString(R.string.key_shape), 13,
                palette.text, true);
        keysCard.addView(shapeLabel);
        addChipGrid(keysCard, new String[]{
                getString(R.string.shape_square),
                getString(R.string.shape_rounded),
                getString(R.string.shape_pill)}, prefs.keyShape(), value -> {
            prefs.setKeyShape(value);
            recreateAt(SCREEN_APPEARANCE);
        }, 3, 10);

        TextView sizeLabel = Ui.text(this, getString(R.string.label_size), 13,
                palette.text, true);
        sizeLabel.setPadding(0, dp(18), 0, 0);
        keysCard.addView(sizeLabel);
        addChipGrid(keysCard, new String[]{
                getString(R.string.size_small),
                getString(R.string.size_normal),
                getString(R.string.size_large)}, prefs.labelSize(), value -> {
            prefs.setLabelSize(value);
            recreateAt(SCREEN_APPEARANCE);
        }, 3, 10);

        TextView gapLabel = Ui.text(this, getString(R.string.key_gap), 13,
                palette.text, true);
        gapLabel.setPadding(0, dp(18), 0, 0);
        keysCard.addView(gapLabel);
        int gapIndex = prefs.keyGap() < 5f ? 0 : (prefs.keyGap() > 7.5f ? 2 : 1);
        addChipGrid(keysCard, new String[]{
                getString(R.string.gap_compact),
                getString(R.string.gap_normal),
                getString(R.string.gap_airy)}, gapIndex, value -> {
            prefs.setKeyGap(value);
            recreateAt(SCREEN_APPEARANCE);
        }, 3, 10);

        addSwitchRow(keysCard, R.string.opt_borders, R.string.opt_borders_desc,
                Prefs.KEY_KEY_BORDERS, prefs.keyBorders());
        addSection(body, getString(R.string.keys_label), null, keysCard);

        return subScreen(R.string.screen_appearance, R.string.screen_appearance_desc, body);
    }

    // --------------------------------------------------------- typing screen

    private View buildTypingScreen() {
        LinearLayout body = M3.body(this);

        LinearLayout feedback = M3.card(this, palette);
        addSwitchRow(feedback, R.string.opt_haptic, R.string.opt_haptic_desc,
                Prefs.KEY_HAPTIC, prefs.haptic());
        addSwitchRow(feedback, R.string.opt_sound, R.string.opt_sound_desc,
                Prefs.KEY_SOUND, prefs.sound());
        addSection(body, getString(R.string.typing_feedback), null, feedback);

        LinearLayout typing = M3.card(this, palette);
        addSwitchRow(typing, R.string.opt_autocap, R.string.opt_autocap_desc,
                Prefs.KEY_AUTOCAP, prefs.autoCap());
        addSwitchRow(typing, R.string.opt_hints, R.string.opt_hints_desc,
                Prefs.KEY_HINTS, prefs.hints());
        addSwitchRow(typing, R.string.opt_popup, R.string.opt_popup_desc,
                Prefs.KEY_POPUP, prefs.showPopup());
        addSwitchRow(typing, R.string.opt_number_row, R.string.opt_number_row_desc,
                Prefs.KEY_NUMBER_ROW, prefs.numberRow());
        addSwitchRow(typing, R.string.opt_double_space, R.string.opt_double_space_desc,
                Prefs.KEY_DOUBLE_SPACE, prefs.doubleSpace());
        addSection(body, getString(R.string.typing_behavior), null, typing);

        LinearLayout suggestions = M3.card(this, palette);
        addSwitchRow(suggestions, R.string.opt_suggestions, R.string.opt_suggestions_desc,
                Prefs.KEY_SUGGESTIONS, prefs.suggestionsEnabled());
        TextView countLabel = Ui.text(this, getString(R.string.suggestion_count), 13,
                palette.text, true);
        countLabel.setPadding(0, dp(18), 0, 0);
        suggestions.addView(countLabel);
        addChipGrid(suggestions, new String[]{"4", "6", "8"}, prefs.suggestionCount() == 4
                ? 0 : (prefs.suggestionCount() == 8 ? 2 : 1), value -> {
            prefs.setSuggestionCount(value == 0 ? 4 : (value == 2 ? 8 : 6));
            recreateAt(SCREEN_TYPING);
        }, 3, 10);
        addSection(body, getString(R.string.typing_suggestions), null, suggestions);

        LinearLayout height = M3.card(this, palette);
        TextView heightLabel = Ui.text(this, getString(R.string.height_preset), 13,
                palette.text, true);
        height.addView(heightLabel);
        float scale = prefs.heightScale();
        int preset = scale < 0.94f ? 0 : (scale > 1.06f ? 2 : 1);
        addChipGrid(height, new String[]{
                getString(R.string.size_compact),
                getString(R.string.size_normal),
                getString(R.string.size_tall)}, preset, value -> {
            prefs.setHeightScale(value == 0 ? 0.88f : (value == 2 ? 1.14f : 1f));
            Toast.makeText(this, R.string.height_applied, Toast.LENGTH_SHORT).show();
        }, 3, 10);

        LinearLayout heightRow = Ui.horizontal(this);
        TextView resetLabel = Ui.text(this, getString(R.string.reset_height), 13,
                palette.textMuted, false);
        resetLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heightRow.addView(resetLabel);
        heightRow.addView(M3.tonalButton(this, palette,
                getString(R.string.reset_height), () -> prefs.setHeightScale(1f)));
        addWithTopMargin(height, heightRow, 14);
        addSection(body, getString(R.string.typing_height), null, height);

        LinearLayout privacy = M3.card(this, palette);
        addSwitchRow(privacy, R.string.opt_incognito, R.string.opt_incognito_desc,
                Prefs.KEY_INCOGNITO, prefs.incognito());
        addSection(body, getString(R.string.typing_privacy), null, privacy);

        return subScreen(R.string.screen_typing, R.string.screen_typing_desc, body);
    }

    // ------------------------------------------------------ languages screen

    private View buildLanguagesScreen() {
        LinearLayout body = M3.body(this);

        LinearLayout inputCard = M3.card(this, palette);
        LinearLayout languageRow = Ui.horizontal(this);
        chipEnglish = M3.chip(this, palette, getString(R.string.lang_english),
                "en".equals(prefs.language()), () -> {
                    prefs.setLanguage("en");
                    prefs.setLanguageEnabled("en", true);
                    updateLanguageChips();
                    rebuildLanguageChips();
                });
        chipBangla = M3.chip(this, palette, getString(R.string.lang_bangla),
                "bn".equals(prefs.language()), () -> {
                    prefs.setLanguage("bn");
                    prefs.setLanguageEnabled("bn", true);
                    updateLanguageChips();
                    rebuildLanguageChips();
                });
        LinearLayout.LayoutParams chipFirst = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        chipFirst.rightMargin = dp(8);
        languageRow.addView(chipEnglish, chipFirst);
        languageRow.addView(chipBangla, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        inputCard.addView(languageRow);

        TextView languageHint = Ui.text(this, getString(R.string.lang_hint), 12,
                palette.textMuted, false);
        languageHint.setPadding(0, dp(12), 0, 0);
        inputCard.addView(languageHint);
        addSection(body, getString(R.string.section_input),
                getString(R.string.section_input_desc), inputCard);

        LinearLayout langCard = M3.card(this, palette);
        languageNow = Ui.text(this, "", 13, palette.text, true);
        langCard.addView(languageNow);
        langCard.addView(Ui.text(this, getString(R.string.languages_desc), 12,
                palette.textMuted, false));
        languageChips = Ui.vertical(this);
        addWithTopMargin(langCard, languageChips, 12);
        addSection(body, getString(R.string.section_languages), null, langCard);
        rebuildLanguageChips();

        return subScreen(R.string.screen_languages, R.string.screen_languages_desc, body);
    }

    // ---------------------------------------------------------- voice screen

    private View buildVoiceScreen() {
        LinearLayout body = M3.body(this);
        addSection(body, getString(R.string.section_voice),
                getString(R.string.section_voice_desc), buildVoiceCard());
        return subScreen(R.string.screen_voice, R.string.screen_voice_desc, body);
    }

    // ---------------------------------------------------------- tools screen

    private View buildToolsScreen() {
        LinearLayout body = M3.body(this);

        // ---------------------------------------------------- shortcuts card
        LinearLayout shortcutCard = M3.card(this, palette);
        shortcutCard.addView(Ui.text(this, getString(R.string.shortcuts_desc), 12,
                palette.textMuted, false));
        LinearLayout shortcutRow = Ui.horizontal(this);
        shortcutKeyInput = smallField(getString(R.string.shortcuts_key_hint));
        shortcutValueInput = smallField(getString(R.string.shortcuts_value_hint));
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        shortcutKeyInput.setLayoutParams(keyParams);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f);
        valueParams.leftMargin = dp(8);
        shortcutValueInput.setLayoutParams(valueParams);
        shortcutRow.addView(shortcutKeyInput);
        shortcutRow.addView(shortcutValueInput);
        shortcutRow.addView(M3.filledButton(this, palette,
                getString(R.string.shortcuts_add), this::addShortcut));
        addWithTopMargin(shortcutCard, shortcutRow, 12);
        shortcutList = Ui.vertical(this);
        addWithTopMargin(shortcutCard, shortcutList, 12);
        addSection(body, getString(R.string.section_shortcuts), null, shortcutCard);
        rebuildShortcutList();

        // --------------------------------------------------- clipboard card
        LinearLayout clipCard = M3.card(this, palette);
        TextView clipNote = Ui.text(this, getString(R.string.settings_clipboard_desc), 12,
                palette.textMuted, false);
        clipCard.addView(clipNote);
        LinearLayout clipRow = Ui.horizontal(this);
        clipStoreCount = Ui.text(this, "", 13, palette.text, true);
        clipStoreCount.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        clipRow.addView(clipStoreCount);
        clipRow.addView(M3.tonalButton(this, palette,
                getString(R.string.settings_clipboard_clear), this::confirmClearClipStore));
        addWithTopMargin(clipCard, clipRow, 14);
        addSection(body, getString(R.string.settings_clipboard), null, clipCard);
        refreshClipStore();

        // ------------------------------------------------- learned words card
        LinearLayout learnedCard = M3.card(this, palette);
        LinearLayout learnedRow = Ui.horizontal(this);
        learnedCount = Ui.text(this, "", 13, palette.text, true);
        learnedCount.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        learnedRow.addView(learnedCount);
        learnedRow.addView(M3.tonalButton(this, palette, getString(R.string.learned_clear),
                this::confirmClearLearned));
        learnedCard.addView(learnedRow);
        learnedCard.addView(Ui.text(this, getString(R.string.learned_desc), 12,
                palette.textMuted, false));
        addSection(body, getString(R.string.section_learned), null, learnedCard);
        refreshLearnedCount();

        // ---------------------------------------------------- editing card
        LinearLayout editingCard = M3.card(this, palette);
        editingCard.addView(Ui.text(this, getString(R.string.settings_editing_desc), 12.5f,
                palette.textMuted, false));
        addSection(body, getString(R.string.settings_editing), null, editingCard);

        return subScreen(R.string.screen_tools, R.string.screen_tools_desc, body);
    }

    // --------------------------------------------------------- backup screen

    // --------------------------------------------------------------- stickers

    private View buildStickersScreen() {
        final StickerLibrary library = StickerLibrary.get(this);
        LinearLayout body = M3.body(this);

        // ------------------------------------------------------------- hero
        LinearLayout hero = M3.card(this, palette);
        hero.addView(Ui.text(this, getString(R.string.sticker_pack_count,
                library.items().size(), library.packs().size()), 20, palette.text, true));
        TextView description = Ui.text(this, getString(R.string.sticker_screen_desc),
                12.5f, palette.textMuted, false);
        description.setPadding(0, dp(8), 0, 0);
        hero.addView(description);

        List<StickerLibrary.Item> all = library.items();
        LinearLayout strip = Ui.horizontal(this);
        for (int i = 0; i < 5 && i < all.size(); i++) {
            StickerLibrary.Item item = all.get((i * 31 + 4) % all.size());
            ImageView art = new ImageView(this);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageBitmap(library.bitmap(item, 2));
            LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                    0, dp(46), 1f);
            if (i > 0) {
                artParams.leftMargin = dp(6);
            }
            strip.addView(art, artParams);
        }
        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        stripParams.topMargin = dp(14);
        hero.addView(strip, stripParams);
        addCard(body, hero);

        // ----------------------------------------------------------- search
        stickerSearch = new EditText(this);
        stickerSearch.setSingleLine(true);
        stickerSearch.setTextSize(14);
        stickerSearch.setTextColor(palette.text);
        stickerSearch.setHintTextColor(palette.textMuted);
        stickerSearch.setHint(getString(R.string.sticker_search_hint));
        stickerSearch.setBackground(Surfaces.control(this, palette, 14,
                Surfaces.muted(palette), Surfaces.border(palette),
                (palette.accent & 0x00FFFFFF) | 0x24000000));
        stickerSearch.setPadding(dp(12), dp(10), dp(12), dp(10));
        stickerSearch.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        stickerSearch.setCompoundDrawablePadding(dp(9));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            stickerSearch.setCompoundDrawableTintList(
                    ColorStateList.valueOf(palette.textMuted));
        }
        stickerSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                stickerQuery = s == null ? "" : s.toString();
                rebuildStickerGrid();
            }
        });
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(14);
        body.addView(stickerSearch, searchParams);

        // ------------------------------------------------------------ chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        stickerChips = Ui.horizontal(this);
        chipScroll.addView(stickerChips, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        chipParams.topMargin = dp(12);
        body.addView(chipScroll, chipParams);

        stickerCountView = Ui.text(this, "", 11.5f, palette.textMuted, true);
        stickerCountView.setPadding(dp(2), dp(14), 0, dp(2));
        body.addView(stickerCountView);

        stickerGrid = Ui.vertical(this);
        body.addView(stickerGrid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView license = Ui.text(this, getString(R.string.sticker_license),
                11, palette.textMuted, false);
        license.setPadding(0, dp(20), 0, 0);
        body.addView(license);

        if (stickerFilter == null && !library.packs().isEmpty()) {
            stickerFilter = library.packs().get(0).id;
        }
        rebuildStickerChips();
        rebuildStickerGrid();

        return subScreen(R.string.screen_stickers, R.string.tile_stickers_sub, body);
    }

    private void rebuildStickerChips() {
        if (stickerChips == null) {
            return;
        }
        stickerChips.removeAllViews();
        StickerLibrary library = StickerLibrary.get(this);
        boolean bangla = banglaUi();
        for (StickerLibrary.Pack pack : library.packs()) {
            final String id = pack.id;
            boolean selected = pack.id.equals(stickerFilter);
            TextView chip = M3.chip(this, palette, pack.name(bangla), selected, () -> {
                stickerFilter = id;
                if (stickerQuery != null && !stickerQuery.isEmpty() && stickerSearch != null) {
                    // Clears the field; the text watcher rebuilds the grid.
                    stickerSearch.setText("");
                }
                rebuildStickerChips();
                rebuildStickerGrid();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(6);
            stickerChips.addView(chip, params);
        }
    }

    /** Rebuilds the visible sticker grid; called by chips and the search box. */
    private void rebuildStickerGrid() {
        if (stickerGrid == null || stickerCountView == null) {
            return;
        }
        StickerLibrary library = StickerLibrary.get(this);
        List<StickerLibrary.Item> found;
        if (stickerQuery != null && !stickerQuery.trim().isEmpty()) {
            found = library.search(stickerQuery);
        } else if (stickerFilter == null) {
            found = library.items();
        } else {
            found = library.ofPack(stickerFilter);
        }

        int total = found.size();
        int limit = 48;
        boolean clipped = total > limit;
        List<StickerLibrary.Item> shown = clipped ? found.subList(0, limit) : found;

        stickerCountView.setText(getString(R.string.sticker_count, total));
        stickerGrid.removeAllViews();

        int columns = 5;
        LinearLayout row = null;
        for (int i = 0; i < shown.size(); i++) {
            if (i % columns == 0) {
                row = Ui.horizontal(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(6);
                }
                stickerGrid.addView(row, rowParams);
            }
            final StickerLibrary.Item item = shown.get(i);
            FrameLayout cell = new FrameLayout(this);
            cell.setBackground(Surfaces.control(this, palette, 14,
                    Palette.mix(palette.surface, palette.bg, palette.dark ? 0.3f : 0.45f),
                    0, (palette.text & 0x00FFFFFF) | 0x16000000));
            cell.setPadding(dp(5), dp(5), dp(5), dp(5));

            ImageView art = new ImageView(this);
            art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            art.setImageBitmap(library.bitmap(item, 2));
            cell.addView(art, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            if (stickerFavorites().isFavorite(item.id)) {
                View dot = new View(this);
                dot.setBackground(Ui.rounded(this, palette.accent, 2.5f));
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                        dp(5), dp(5), Gravity.TOP | Gravity.END);
                dotParams.topMargin = dp(3);
                dotParams.rightMargin = dp(3);
                cell.addView(dot, dotParams);
            }

            cell.setContentDescription(item.name(banglaUi()));
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setOnClickListener(v -> showStickerPreview(item));
            M3.pressScale(cell);

            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(
                    0, dp(58), 1f);
            if (i % columns < columns - 1) {
                cellParams.rightMargin = dp(6);
            }
            row.addView(cell, cellParams);
        }
        if (row != null) {
            for (int i = shown.size() % columns; i > 0 && i < columns; i++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            }
        }
        if (clipped) {
            TextView note = Ui.text(this, getString(R.string.sticker_more, limit),
                    11.5f, palette.textMuted, false);
            note.setGravity(Gravity.CENTER);
            note.setPadding(0, dp(10), 0, 0);
            stickerGrid.addView(note);
        }
    }

    private StickerStore stickerFavorites() {
        if (stickerStore == null) {
            stickerStore = new StickerStore(this);
        }
        return stickerStore;
    }

    /** Big preview with favourite and copy actions. */
    private void showStickerPreview(StickerLibrary.Item item) {
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        final boolean bangla = banglaUi();
        StickerLibrary library = StickerLibrary.get(this);

        LinearLayout panel = Ui.vertical(this);
        panel.setBackground(Surfaces.surface(this, palette, 26));
        panel.setPadding(dp(20), dp(16), dp(20), dp(16));
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout head = Ui.horizontal(this);
        TextView packName = Ui.text(this,
                library.pack(item.pack) == null ? "" : library.pack(item.pack).name(bangla),
                11.5f, palette.textMuted, true);
        head.addView(packName, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView close = M3.icon(this, R.drawable.ic_close, palette.textMuted);
        close.setContentDescription(getString(R.string.sticker_preview_close));
        close.setPadding(dp(6), dp(6), dp(6), dp(6));
        close.setBackground(Ui.ripple(this, Color.TRANSPARENT, 14,
                (palette.text & 0x00FFFFFF) | 0x1A000000));
        close.setOnClickListener(v -> dialog.dismiss());
        head.addView(close, new LinearLayout.LayoutParams(dp(28), dp(28)));
        panel.addView(head, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setImageBitmap(library.bitmap(item));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                dp(136), dp(136));
        artParams.topMargin = dp(6);
        panel.addView(art, artParams);

        TextView name = Ui.text(this, item.name(bangla), 18, palette.text, true);
        name.setGravity(Gravity.CENTER);
        panel.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = Ui.horizontal(this);
        final TextView fav = M3.tonalButton(this, palette,
                getString(stickerFavorites().isFavorite(item.id)
                        ? R.string.sticker_preview_unfav : R.string.sticker_preview_fav),
                () -> { });
        fav.setOnClickListener(v -> {
            boolean now = stickerFavorites().toggleFavorite(item.id);
            fav.setText(getString(now
                    ? R.string.sticker_preview_unfav : R.string.sticker_preview_fav));
            rebuildStickerGrid();
        });
        TextView copy = M3.filledButton(this, palette,
                getString(R.string.sticker_preview_copy), () -> copySticker(item));
        LinearLayout.LayoutParams favParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        favParams.rightMargin = dp(8);
        actions.addView(fav, favParams);
        actions.addView(copy, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(16);
        panel.addView(actions, actionsParams);

        FrameLayout root = new FrameLayout(this);
        int side = dp(24);
        root.setPadding(side, side, side, side);
        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dialog.setContentView(root);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.55f;
            window.setAttributes(params);
            window.setWindowAnimations(0);
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
        if (!motionDisabled()) {
            panel.setAlpha(0f);
            panel.setScaleX(0.94f);
            panel.setScaleY(0.94f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    private void copySticker(StickerLibrary.Item item) {
        try {
            StickerProvider.fileFor(this, item);
        } catch (java.io.IOException ignored) {
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(),
                item.name(banglaUi()), StickerProvider.uriFor(item)));
        Toast.makeText(this, R.string.sticker_copied, Toast.LENGTH_SHORT).show();
    }

    private boolean banglaUi() {
        Locale locale = Locale.getDefault();
        return locale != null && "bn".equals(locale.getLanguage());
    }

    private boolean motionDisabled() {
        try {
            float scale = Settings.Global.getFloat(getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private View buildBackupScreen() {
        LinearLayout body = M3.body(this);

        LinearLayout backupCard = M3.card(this, palette);
        backupCard.addView(Ui.text(this, getString(R.string.backup_desc), 12.5f,
                palette.textMuted, false));
        LinearLayout backupRow = Ui.horizontal(this);
        backupRow.addView(M3.filledButton(this, palette,
                getString(R.string.backup_export), this::exportBackup));
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        importParams.leftMargin = dp(8);
        backupRow.addView(M3.tonalButton(this, palette,
                getString(R.string.backup_import), this::importBackup), importParams);
        addWithTopMargin(backupCard, backupRow, 14);
        addSection(body, getString(R.string.section_backup), null, backupCard);

        LinearLayout resetCard = M3.card(this, palette);
        LinearLayout resetRow = Ui.horizontal(this);
        TextView resetNote = Ui.text(this, getString(R.string.reset_all_desc), 12,
                palette.textMuted, false);
        LinearLayout.LayoutParams resetNoteParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        resetNoteParams.rightMargin = dp(12);
        resetRow.addView(resetNote, resetNoteParams);
        resetRow.addView(M3.tonalButton(this, palette,
                getString(R.string.reset_all), this::confirmResetAll));
        resetCard.addView(resetRow);
        addSection(body, getString(R.string.screen_backup_reset), null, resetCard);

        return subScreen(R.string.screen_backup, R.string.screen_backup_desc, body);
    }

    // ---------------------------------------------------------- about screen

    private View buildAboutScreen() {
        LinearLayout body = M3.body(this);

        LinearLayout aboutCard = M3.card(this, palette);
        LinearLayout identity = Ui.horizontal(this);
        FrameLayout badge = new FrameLayout(this);
        M3.applySurface(this, badge, palette, 18, 2f);
        ImageView app = new ImageView(this);
        app.setImageResource(R.mipmap.ic_launcher);
        badge.addView(app, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(58), dp(58));
        badgeParams.rightMargin = dp(14);
        identity.addView(badge, badgeParams);
        LinearLayout names = Ui.vertical(this);
        names.addView(Ui.text(this, getString(R.string.app_name), 17, palette.text, true));
        names.addView(Ui.text(this, getString(R.string.about_version) + "  ·  "
                + getString(R.string.footer), 11.5f, palette.textMuted, false));
        identity.addView(names);
        aboutCard.addView(identity);

        TextView about = Ui.text(this, getString(R.string.about_body), 12.5f,
                palette.textMuted, false);
        about.setPadding(0, dp(16), 0, 0);
        aboutCard.addView(about);

        TextView libraries = Ui.text(this, getString(R.string.about_libraries), 11.5f,
                palette.textMuted, false);
        libraries.setPadding(0, dp(10), 0, 0);
        aboutCard.addView(libraries);

        TextView stickers = Ui.text(this, getString(R.string.sticker_license), 11.5f,
                palette.textMuted, false);
        stickers.setPadding(0, dp(6), 0, 0);
        aboutCard.addView(stickers);
        addSection(body, getString(R.string.section_about), null, aboutCard);

        LinearLayout links = M3.bareCard(this, palette);
        links.setPadding(dp(6), dp(6), dp(6), dp(6));
        links.addView(M3.navRow(this, palette, R.drawable.ic_info,
                getString(R.string.guide_title), getString(R.string.home_guide_sub),
                this::showGuide));
        links.addView(M3.divider(this, palette));
        links.addView(M3.navRow(this, palette, R.drawable.ic_keyboard,
                getString(R.string.home_keyboard), getString(R.string.home_keyboard_sub),
                this::openKeyboardSettings));
        addCard(body, links);

        return subScreen(R.string.screen_about, R.string.screen_about_desc, body);
    }

    private void addWithTopMargin(LinearLayout parent, View child, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        parent.addView(child, params);
    }

    private void addSection(LinearLayout parent, String title, String subtitle, View card) {
        parent.addView(M3.sectionLabel(this, palette, title));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = Ui.text(this, subtitle, 11.5f, palette.textMuted, false);
            sub.setPadding(dp(4), 0, dp(4), dp(6));
            parent.addView(sub);
        }
        parent.addView(card);
    }

    private TextView makeChip(String label, boolean selected, Runnable action) {
        return M3.chip(this, palette, label, selected, action);
    }

    private void updateLanguageChips() {
        String code = prefs.language();
        styleChip(chipEnglish, "en".equals(code));
        styleChip(chipBangla, "bn".equals(code));
    }

    private void styleChip(TextView chip, boolean selected) {
        M3.applyChip(chip, this, palette, selected);
    }

    private void rebuildThemeChips() {
        themeChips.removeAllViews();
        int[] values = {Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK,
                Prefs.THEME_AURORA, Prefs.THEME_MIDNIGHT, Prefs.THEME_PAPER,
                Prefs.THEME_GLACIER, Prefs.THEME_NEON, Prefs.THEME_GRAPHITE};
        int[] labels = {R.string.theme_system, R.string.theme_light, R.string.theme_dark,
                R.string.theme_aurora, R.string.theme_midnight, R.string.theme_paper,
                R.string.theme_glacier, R.string.theme_neon, R.string.theme_graphite};
        boolean systemDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        LinearLayout row = null;
        for (int i = 0; i < values.length; i++) {
            if (i % 3 == 0) {
                row = Ui.horizontal(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(9);
                }
                themeChips.addView(row, rowParams);
            }
            final int value = values[i];
            Palette themePalette = Palette.preview(value, systemDark, prefs.accent());
            boolean selected = prefs.theme() == value;

            LinearLayout chip = Ui.horizontal(this);
            chip.setPadding(dp(7), dp(10), dp(9), dp(10));
            GradientDrawable base = Surfaces.surface(this, palette, 18);
            if (selected) {
                base.setStroke(dp(2), palette.accent);
            }
            chip.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x1A000000), base,
                    Ui.rounded(this, 0xFFFFFFFF, 18)));
            chip.setElevation(dp(selected ? 2 : 0));

            View swatch = new View(this);
            GradientDrawable swatchBg = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{themePalette.bgTop, themePalette.bgBottom});
            swatchBg.setCornerRadius(dp(8));
            swatchBg.setStroke(dp(1), 0x66FFFFFF);
            swatch.setBackground(swatchBg);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                    dp(22), dp(22));
            swatchParams.rightMargin = dp(7);
            chip.addView(swatch, swatchParams);

            TextView name = Ui.text(this, getString(labels[i]), 10.5f,
                    palette.text, true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.addView(name, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                prefs.setTheme(value);
                recreateAt(SCREEN_APPEARANCE);
            });
            M3.pressScale(chip);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 3 != 0) {
                params.leftMargin = dp(9);
            }
            row.addView(chip, params);
        }
    }

    private LinearLayout buildVoiceCard() {
        LinearLayout card = Ui.card(this, palette);

        LinearLayout permRow = Ui.horizontal(this);
        LinearLayout texts = Ui.vertical(this);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        micStatus = Ui.text(this, "", 14, palette.text, true);
        TextView permHint = Ui.text(this, getString(R.string.voice_permission_desc), 12,
                palette.textMuted, false);
        permHint.setPadding(0, dp(3), 0, 0);
        texts.addView(micStatus);
        texts.addView(permHint);
        permRow.addView(texts);
        micButton = Ui.button(this, palette, getString(R.string.voice_permission_action), true,
                this::requestMicPermission);
        permRow.addView(micButton);
        card.addView(permRow);

        TextView language = Ui.text(this, getString(R.string.voice_language), 13,
                palette.text, true);
        language.setPadding(0, dp(20), 0, dp(8));
        card.addView(language);

        voiceLangChips = Ui.vertical(this);
        card.addView(voiceLangChips);
        rebuildVoiceLangChips();

        TextView methodLabel = Ui.text(this, getString(R.string.voice_method), 13,
                palette.text, true);
        methodLabel.setPadding(0, dp(20), 0, dp(8));
        card.addView(methodLabel);
        voiceModeChips = Ui.vertical(this);
        card.addView(voiceModeChips);
        rebuildVoiceModeChips();
        TextView methodNote = Ui.text(this, getString(R.string.voice_method_note), 12,
                palette.textMuted, false);
        methodNote.setPadding(0, dp(6), 0, 0);
        card.addView(methodNote);

        TextView engineLabel = Ui.text(this, getString(R.string.voice_engine), 13,
                palette.text, true);
        engineLabel.setPadding(0, dp(20), 0, dp(8));
        card.addView(engineLabel);
        voiceEngineChips = Ui.vertical(this);
        card.addView(voiceEngineChips);
        rebuildVoiceEngineChips();
        TextView engineNote = Ui.text(this, getString(R.string.voice_engine_note), 12,
                palette.textMuted, false);
        engineNote.setPadding(0, dp(6), 0, 0);
        card.addView(engineNote);

        if (!hasPackage("com.google.android.googlequicksearchbox")) {
            LinearLayout googleRow = Ui.horizontal(this);
            TextView googleNote = Ui.text(this, getString(R.string.voice_google_missing),
                    12, palette.textMuted, false);
            googleNote.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            googleRow.addView(googleNote);
            googleRow.addView(Ui.button(this, palette,
                    getString(R.string.voice_google_install), false, this::openGoogleApp));
            addWithTopMargin(card, googleRow, 10);
        }

        addSwitchRow(card, R.string.opt_banglish, R.string.opt_banglish_desc,
                Prefs.KEY_VOICE_BANGLISH, prefs.voiceBanglish());
        addSwitchRow(card, R.string.opt_voice_continuous, R.string.opt_voice_continuous_desc,
                Prefs.KEY_VOICE_CONTINUOUS, prefs.voiceContinuous());
        addSwitchRow(card, R.string.opt_voice_cleanup, R.string.opt_voice_cleanup_desc,
                Prefs.KEY_VOICE_CLEANUP, prefs.voiceCleanup());
        addSwitchRow(card, R.string.opt_voice_trailing, R.string.opt_voice_trailing_desc,
                Prefs.KEY_VOICE_TRAILING, prefs.voiceTrailingSpace());

        LinearLayout micRow = Ui.horizontal(this);
        TextView micLabel = Ui.text(this, getString(R.string.mic_test_title), 13,
                palette.text, true);
        micLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        micRow.addView(micLabel);
        micRow.addView(Ui.button(this, palette, getString(R.string.mic_test_action), false,
                this::testMicrophone));
        addWithTopMargin(card, micRow, 14);
        micTestResult = Ui.text(this, "", 12, palette.textMuted, false);
        card.addView(micTestResult);

        LinearLayout speechRow = Ui.horizontal(this);
        TextView speechLabel = Ui.text(this, getString(R.string.voice_test_title), 13,
                palette.text, true);
        speechLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        speechRow.addView(speechLabel);
        speechRow.addView(Ui.button(this, palette, getString(R.string.voice_test_action), false,
                this::testSpeech));
        addWithTopMargin(card, speechRow, 14);
        voiceTestResult = Ui.text(this, "", 12, palette.textMuted, false);
        card.addView(voiceTestResult);

        LinearLayout logRow = Ui.horizontal(this);
        TextView logLabel = Ui.text(this, getString(R.string.voice_log_title), 13,
                palette.text, true);
        logLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        logRow.addView(logLabel);
        logRow.addView(Ui.button(this, palette, getString(R.string.voice_log_copy), false,
                this::copyVoiceLog));
        addWithTopMargin(card, logRow, 14);
        voiceLogView = Ui.text(this, "", 11, palette.textMuted, false);
        voiceLogView.setTypeface(Typeface.MONOSPACE);
        voiceLogView.setTextIsSelectable(true);
        card.addView(voiceLogView);
        refreshVoiceLog();

        TextView note = Ui.text(this, getString(R.string.voice_note), 12,
                palette.textMuted, false);
        note.setPadding(0, dp(12), 0, 0);
        card.addView(note);
        return card;
    }

    private void rebuildVoiceModeChips() {
        if (voiceModeChips == null) {
            return;
        }
        voiceModeChips.removeAllViews();
        String selected = prefs.voiceMode();
        String[][] options = {
                {Prefs.VOICE_MODE_AUTO, getString(R.string.voice_mode_auto)},
                {Prefs.VOICE_MODE_INLINE, getString(R.string.voice_mode_inline)},
                {Prefs.VOICE_MODE_SCREEN, getString(R.string.voice_mode_screen)},
        };
        for (String[] option : options) {
            TextView chip = makeChip(option[1], selected.equals(option[0]), () -> {
                prefs.setVoiceMode(option[0]);
                rebuildVoiceModeChips();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(6);
            voiceModeChips.addView(chip, params);
        }
    }

    private void rebuildVoiceLangChips() {
        if (voiceLangChips == null) {
            return;
        }
        voiceLangChips.removeAllViews();
        String selected = prefs.voiceLang();
        String[][] options = {
                {Prefs.VOICE_AUTO, getString(R.string.voice_lang_auto)},
                {"bn-BD", getString(R.string.voice_lang_bangla)},
                {"en-IN", "English (IN)"},
                {"en-US", "English (US)"},
        };
        LinearLayout row = null;
        for (int i = 0; i < options.length; i++) {
            if (i % 2 == 0) {
                row = Ui.horizontal(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(8);
                }
                voiceLangChips.addView(row, rowParams);
            }
            final String tag = options[i][0];
            TextView chip = makeChip(options[i][1], selected.equals(tag), () -> {
                prefs.setVoiceLang(tag);
                rebuildVoiceLangChips();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 2 == 1) {
                params.leftMargin = dp(8);
            }
            row.addView(chip, params);
        }
    }

    /**
     * The recognisers installed on the phone. Pinning one is the manual way
     * out of a device whose default service refuses the keyboard.
     */
    private void rebuildVoiceEngineChips() {
        if (voiceEngineChips == null) {
            return;
        }
        voiceEngineChips.removeAllViews();
        String selected = prefs.voiceEngine();
        List<String[]> options = new ArrayList<>();
        options.add(new String[]{"", getString(R.string.voice_engine_auto)});
        for (ComponentName name : VoiceInput.availableEngines(this)) {
            options.add(new String[]{name.flattenToString(), engineName(name)});
        }
        for (String[] option : options) {
            TextView chip = makeChip(option[1], selected.equals(option[0]), () -> {
                prefs.setVoiceEngine(option[0]);
                rebuildVoiceEngineChips();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(6);
            voiceEngineChips.addView(chip, params);
        }
    }

    private boolean hasPackage(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openGoogleApp() {
        Intent market = new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=com.google.android.googlequicksearchbox"));
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(market);
        } catch (ActivityNotFoundException e) {
            Intent web = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                    "https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox"));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(web);
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
            }
        }
    }

    private String engineName(ComponentName name) {
        try {
            PackageManager pm = getPackageManager();
            CharSequence app = pm.getApplicationLabel(
                    pm.getApplicationInfo(name.getPackageName(), 0));
            String service = name.getClassName();
            int dot = service.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < service.length()) {
                service = service.substring(dot + 1);
            }
            return app + " · " + service;
        } catch (Exception e) {
            return name.flattenToShortString();
        }
    }

    private boolean hasMicPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMicPermission() {
        if (hasMicPermission()) {
            refreshVoiceStatus();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
    }

    private void refreshVoiceLog() {
        if (voiceLogView == null) {
            return;
        }
        String log = VoiceLog.dump();
        voiceLogView.setText(log.isEmpty() ? getString(R.string.voice_log_empty) : log);
    }

    private void copyVoiceLog() {
        String log = VoiceLog.dump();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.voice_log_title),
                log.isEmpty() ? getString(R.string.voice_log_empty) : log));
        Toast.makeText(this, R.string.voice_log_copied, Toast.LENGTH_SHORT).show();
    }

    private void refreshVoiceStatus() {
        if (micStatus == null || micButton == null) {
            return;
        }
        boolean granted = hasMicPermission();
        micStatus.setText(granted ? R.string.voice_permission_granted
                : R.string.voice_permission_missing);
        micStatus.setTextColor(granted ? palette.success : palette.text);
        micButton.setText(granted ? R.string.voice_permission_ok
                : R.string.voice_permission_action);
        micButton.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------- voice self-checks

    /** Records 1.5 s and reports the peak level, so a dead or blocked mic shows. */
    private void testMicrophone() {
        if (!hasMicPermission()) {
            requestMicPermission();
            return;
        }
        if (micTestResult == null) {
            return;
        }
        micTestResult.setText(R.string.mic_test_running);
        new Thread(() -> {
            int peak = -1;
            int rate = 16000;
            AudioRecord record = null;
            try {
                int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                record = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(min, rate * 2));
                if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                    record.startRecording();
                    short[] buffer = new short[rate / 10];
                    int max = 0;
                    long end = SystemClock.elapsedRealtime() + 1500;
                    while (SystemClock.elapsedRealtime() < end) {
                        int read = record.read(buffer, 0, buffer.length);
                        for (int i = 0; i < read; i++) {
                            int value = Math.abs(buffer[i]);
                            if (value > max) {
                                max = value;
                            }
                        }
                    }
                    record.stop();
                    peak = max;
                }
            } catch (RuntimeException ignored) {
                peak = -1;
            } finally {
                if (record != null) {
                    record.release();
                }
            }
            final int level = peak;
            runOnUiThread(() -> {
                if (micTestResult == null) {
                    return;
                }
                if (level < 0) {
                    micTestResult.setText(R.string.mic_test_failed);
                    VoiceLog.add("mic test: could not open the microphone");
                } else if (level < 200) {
                    micTestResult.setText(getString(R.string.mic_test_silent, level));
                    VoiceLog.add("mic test: silence (peak " + level + ")");
                } else {
                    micTestResult.setText(getString(R.string.mic_test_ok, level));
                    VoiceLog.add("mic test: ok (peak " + level + ")");
                }
                refreshVoiceLog();
            });
        }).start();
    }

    /** Runs one recognition from this activity, where speech always has focus. */
    private void testSpeech() {
        if (!hasMicPermission()) {
            requestMicPermission();
            return;
        }
        if (voiceTestResult == null) {
            return;
        }
        stopSpeechTest();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceTestResult.setText(R.string.voice_test_unavailable);
            return;
        }
        voiceTestResult.setText(R.string.voice_test_running);
        final SpeechRecognizer recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechTest = recognizer;
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }

            @Override
            public void onError(int error) {
                finishSpeechTest(getString(R.string.voice_test_failed, VoiceInput.errorName(error)));
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results == null ? null
                        : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = matches == null || matches.isEmpty()
                        ? "" : matches.get(0).trim();
                finishSpeechTest(text.isEmpty()
                        ? getString(R.string.voice_test_empty)
                        : getString(R.string.voice_test_ok, text));
            }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, testLanguage());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            recognizer.startListening(intent);
        } catch (RuntimeException e) {
            finishSpeechTest(getString(R.string.voice_test_failed,
                    e.getClass().getSimpleName()));
            return;
        }
        testHandler.postDelayed(speechTimeout, 8000);
    }

    private String testLanguage() {
        String configured = prefs.voiceLang();
        if (Prefs.VOICE_AUTO.equals(configured)) {
            return prefs.bangla() ? "bn-BD" : "en-US";
        }
        return configured;
    }

    private void finishSpeechTest(String message) {
        testHandler.removeCallbacks(speechTimeout);
        if (speechTest != null) {
            try {
                speechTest.destroy();
            } catch (RuntimeException ignored) {
                // Already disconnected.
            }
            speechTest = null;
        }
        if (voiceTestResult != null) {
            voiceTestResult.setText(message);
        }
        refreshVoiceLog();
    }

    private void stopSpeechTest() {
        testHandler.removeCallbacks(speechTimeout);
        if (speechTest != null) {
            try {
                speechTest.cancel();
                speechTest.destroy();
            } catch (RuntimeException ignored) {
                // Already disconnected.
            }
            speechTest = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSpeechTest();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MIC) {
            return;
        }
        refreshVoiceStatus();
        if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            AppDialog.show(this, palette,
                    getString(R.string.voice_permission_denied),
                    getString(R.string.voice_permission_denied_body));
        }
    }

    // ---------------------------------------------------------------- backup

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "likhon-backup.json");
        try {
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.backup_no_files, Toast.LENGTH_SHORT).show();
        }
    }

    private void importBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.backup_no_files, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    throw new IllegalStateException("no stream");
                }
                out.write(Backup.toJson(this).getBytes(StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, R.string.backup_saved, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.backup_failed, e.getClass().getSimpleName()),
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_IMPORT) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IllegalStateException("no stream");
                }
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                }
                Backup.Restored restored = Backup.restore(this,
                        new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                Toast.makeText(this, getString(R.string.backup_restored, restored.settings,
                        restored.shortcuts, restored.clips), Toast.LENGTH_LONG).show();
                recreate();
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.backup_failed, e.getClass().getSimpleName()),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void addSwitchRow(LinearLayout card, int titleRes, int descriptionRes,
                              String key, boolean value) {
        card.addView(M3.switchRow(this, palette, getString(titleRes),
                getString(descriptionRes), value,
                checked -> prefs.setBoolean(key, checked)));
    }

    private void showGuide() {
        AppDialog.show(this, palette, getString(R.string.guide_title),
                getString(R.string.guide_body));
    }

    private void openKeyboardSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void showKeyboardPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private boolean isImeEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) {
            return false;
        }
        List<InputMethodInfo> enabled = imm.getEnabledInputMethodList();
        for (InputMethodInfo info : enabled) {
            if (getPackageName().equals(info.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isImeSelected() {
        String current = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);
        return current != null && current.startsWith(getPackageName() + "/");
    }

    private void refreshStatus() {
        if (statusTitle == null) {
            return;
        }
        boolean enabled = isImeEnabled();
        boolean selected = isImeSelected();
        statusTitle.setText(selected ? R.string.status_active
                : enabled ? R.string.status_enabled : R.string.status_off);
        statusBody.setText(selected ? R.string.status_active_body
                : enabled ? R.string.status_enabled_body : R.string.status_off_body);
        statusDot.setBackground(Ui.rounded(this,
                selected ? palette.success : enabled ? palette.accent : palette.textMuted, 5));
        enableButton.setText(enabled ? R.string.action_settings : R.string.action_enable);
        chooseButton.setText(selected ? R.string.action_change : R.string.action_choose);
        chooseButton.setBackground(Surfaces.control(this, palette, 22, palette.accent,
                0, 0x33FFFFFF));
        chooseButton.setTextColor(palette.onAccent);

        if (chipEnglish != null) {
            updateLanguageChips();
        }
    }
}
