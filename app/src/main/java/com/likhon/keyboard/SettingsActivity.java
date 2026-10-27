package com.likhon.keyboard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
    static final String PANEL_EDITING = "editing";

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
        setContentView(buildContent());
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
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_clipboard)
                .setMessage(getString(R.string.clip_clear_confirm, store.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clip_clear, (dialog, which) -> {
                    store.clear();
                    refreshClipStore();
                })
                .show();
    }

    // ------------------------------------------------------- new settings bits

    private interface IntAction {
        void run(int value);
    }

    private void addAccentChips(LinearLayout parent) {
        LinearLayout holder = Ui.vertical(this);
        LinearLayout row = null;
        for (int i = 0; i < Palette.accentCount(); i++) {
            if (i % 3 == 0) {
                row = Ui.horizontal(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(8);
                }
                holder.addView(row, rowParams);
            }
            final int index = i;
            boolean selected = prefs.accent() == i;
            int color = Palette.accentColor(i, palette.dark);
            TextView chip = Ui.text(this, selected ? "\u2713" : "", 15,
                    palette.dark ? 0xFF0A0E13 : 0xFFFFFFFF, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(12), 0, dp(12));
            chip.setBackground(Ui.ripple(this, color, 12, 0x55FFFFFF));
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                prefs.raw().edit().putInt(Prefs.KEY_ACCENT, index).apply();
                recreate();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % 3 != 0) {
                params.leftMargin = dp(8);
            }
            row.addView(chip, params);
        }
        addWithTopMargin(parent, holder, 10);
    }

    private void addChipGrid(LinearLayout parent, String[] labels, int selected,
                             IntAction action, int perRow, int topMarginDp) {
        LinearLayout holder = Ui.vertical(this);
        LinearLayout row = null;
        for (int i = 0; i < labels.length; i++) {
            if (i % perRow == 0) {
                row = Ui.horizontal(this);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                if (i > 0) {
                    rowParams.topMargin = dp(8);
                }
                holder.addView(row, rowParams);
            }
            final int value = i;
            TextView chip = makeChip(labels[i], selected == i, () -> action.run(value));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i % perRow != 0) {
                params.leftMargin = dp(8);
            }
            row.addView(chip, params);
        }
        addWithTopMargin(parent, holder, Math.max(0, topMarginDp));
    }

    private EditText smallField(String hint) {
        EditText field = new EditText(this);
        field.setTextSize(13);
        field.setTextColor(palette.text);
        field.setHintTextColor(palette.textMuted);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setBackground(Ui.roundedStroke(this, palette.surfaceAlt, 10, palette.divider, 1));
        field.setPadding(dp(10), dp(8), dp(10), dp(8));
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
            if (active) {
                chip.setTextColor(palette.onAccent);
                chip.setBackground(Ui.ripple(this, palette.accent, 12, 0x40FFFFFF));
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
        new AlertDialog.Builder(this)
                .setTitle(R.string.section_learned)
                .setMessage(R.string.learned_clear_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.learned_clear, (dialog, which) -> {
                    new LearnedWords(this).clear();
                    refreshLearnedCount();
                })
                .show();
    }

    private void confirmResetAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_all)
                .setMessage(R.string.reset_all_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reset_all, (dialog, which) -> {
                    prefs.raw().edit().clear().apply();
                    recreate();
                })
                .show();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(palette.bg);
        window.setNavigationBarColor(palette.bg);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
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

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.bg);
        scroll.setClipToPadding(false);

        LinearLayout root = Ui.vertical(this);
        root.setPadding(dp(18), dp(20), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ------------------------------------------------------------ header
        LinearLayout header = Ui.horizontal(this);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        header.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout headings = Ui.vertical(this);
        headings.setPadding(dp(14), 0, 0, 0);
        TextView name = Ui.text(this, getString(R.string.app_name), 21, palette.text, true);
        TextView tagline = Ui.text(this, getString(R.string.tagline), 12.5f, palette.textMuted, false);
        tagline.setPadding(0, dp(4), 0, 0);
        headings.addView(name);
        headings.addView(tagline);
        header.addView(headings);
        addWithTopMargin(root, header, 0);

        // ------------------------------------------------------ status card
        LinearLayout statusCard = Ui.card(this, palette);
        LinearLayout statusTop = Ui.horizontal(this);
        statusDot = Ui.dot(this, palette.textMuted, 10);
        statusTop.addView(statusDot);
        statusTitle = Ui.text(this, "", 16, palette.text, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(10);
        statusTop.addView(statusTitle, titleParams);
        statusCard.addView(statusTop);

        statusBody = Ui.text(this, "", 13, palette.textMuted, false);
        statusBody.setPadding(0, dp(8), 0, 0);
        statusCard.addView(statusBody);

        LinearLayout buttons = Ui.horizontal(this);
        enableButton = Ui.button(this, palette, getString(R.string.action_enable), false,
                this::openKeyboardSettings);
        chooseButton = Ui.button(this, palette, getString(R.string.action_choose), true,
                this::showKeyboardPicker);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        first.rightMargin = dp(8);
        buttons.addView(enableButton, first);
        buttons.addView(chooseButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(16);
        statusCard.addView(buttons, buttonsParams);
        addWithTopMargin(root, statusCard, 18);

        // --------------------------------------------------------- try card
        LinearLayout tryCard = Ui.card(this, palette);
        TextView tryTitle = Ui.text(this, getString(R.string.try_title), 15, palette.text, true);
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
        field.setBackground(Ui.roundedStroke(this, palette.surfaceAlt, 14, palette.divider, 1));
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(12);
        tryCard.addView(field, fieldParams);
        addWithTopMargin(root, tryCard, 12);

        // ------------------------------------------------------- input card
        LinearLayout inputCard = Ui.card(this, palette);
        LinearLayout languageRow = Ui.horizontal(this);
        chipEnglish = makeChip(getString(R.string.lang_english), "en".equals(prefs.language()), () -> {
            prefs.setLanguage("en");
            prefs.setLanguageEnabled("en", true);
            updateLanguageChips();
            rebuildLanguageChips();
        });
        chipBangla = makeChip(getString(R.string.lang_bangla), "bn".equals(prefs.language()), () -> {
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

        // ------------------------------------------------------ languages card
        LinearLayout langCard = Ui.card(this, palette);
        languageNow = Ui.text(this, "", 13, palette.text, true);
        langCard.addView(languageNow);
        langCard.addView(Ui.text(this, getString(R.string.languages_desc), 12,
                palette.textMuted, false));
        languageChips = Ui.vertical(this);
        addWithTopMargin(langCard, languageChips, 10);
        addSection(root, getString(R.string.section_languages), null, langCard);
        rebuildLanguageChips();

        TextView languageHint = Ui.text(this, getString(R.string.lang_hint), 12,
                palette.textMuted, false);
        languageHint.setPadding(0, dp(10), 0, 0);
        inputCard.addView(languageHint);
        addSection(root, getString(R.string.section_input),
                getString(R.string.section_input_desc), inputCard);

        // -------------------------------------------------------- voice card
        addSection(root, getString(R.string.section_voice),
                getString(R.string.section_voice_desc), buildVoiceCard());

        // ----------------------------------------------------- editing card
        LinearLayout editingCard = Ui.card(this, palette);
        TextView editingNote = Ui.text(this, getString(R.string.settings_editing_desc), 13,
                palette.textMuted, false);
        editingCard.addView(editingNote);
        addSection(root, getString(R.string.settings_editing), null, editingCard);

        // --------------------------------------------------- clipboard card
        LinearLayout clipCard = Ui.card(this, palette);
        TextView clipNote = Ui.text(this, getString(R.string.settings_clipboard_desc), 13,
                palette.textMuted, false);
        clipCard.addView(clipNote);
        LinearLayout clipRow = Ui.horizontal(this);
        clipStoreCount = Ui.text(this, "", 13, palette.text, true);
        clipStoreCount.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        clipRow.addView(clipStoreCount);
        clipRow.addView(Ui.button(this, palette,
                getString(R.string.settings_clipboard_clear), false,
                this::confirmClearClipStore));
        addWithTopMargin(clipCard, clipRow, 14);
        addSection(root, getString(R.string.settings_clipboard), null, clipCard);
        refreshClipStore();

        // ---------------------------------------------------- shortcuts card
        LinearLayout shortcutCard = Ui.card(this, palette);
        shortcutCard.addView(Ui.text(this, getString(R.string.shortcuts_desc), 13,
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
        shortcutRow.addView(Ui.button(this, palette,
                getString(R.string.shortcuts_add), true, this::addShortcut));
        addWithTopMargin(shortcutCard, shortcutRow, 12);
        shortcutList = Ui.vertical(this);
        addWithTopMargin(shortcutCard, shortcutList, 12);
        addSection(root, getString(R.string.section_shortcuts), null, shortcutCard);
        rebuildShortcutList();

        // ------------------------------------------------- learned words card
        LinearLayout learnedCard = Ui.card(this, palette);
        LinearLayout learnedRow = Ui.horizontal(this);
        learnedCount = Ui.text(this, "", 13, palette.text, true);
        learnedCount.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        learnedRow.addView(learnedCount);
        learnedRow.addView(Ui.button(this, palette, getString(R.string.learned_clear),
                false, this::confirmClearLearned));
        learnedCard.addView(learnedRow);
        learnedCard.addView(Ui.text(this, getString(R.string.learned_desc), 12,
                palette.textMuted, false));
        addSection(root, getString(R.string.section_learned), null, learnedCard);
        refreshLearnedCount();

        // -------------------------------------------------- appearance card
        LinearLayout themeCard = Ui.card(this, palette);
        themeChips = Ui.horizontal(this);
        themeCard.addView(themeChips);

        TextView accentLabel = Ui.text(this, getString(R.string.accent), 13,
                palette.text, true);
        themeCard.addView(accentLabel);
        addAccentChips(themeCard);

        TextView shapeLabel = Ui.text(this, getString(R.string.key_shape), 13,
                palette.text, true);
        shapeLabel.setPadding(0, dp(18), 0, dp(8));
        themeCard.addView(shapeLabel);
        addChipGrid(themeCard, new String[]{
                getString(R.string.shape_square),
                getString(R.string.shape_rounded),
                getString(R.string.shape_pill)}, prefs.keyShape(), value -> {
            prefs.setKeyShape(value);
            recreate();
        }, 3, 0);

        TextView sizeLabel = Ui.text(this, getString(R.string.label_size), 13,
                palette.text, true);
        sizeLabel.setPadding(0, dp(18), 0, dp(8));
        themeCard.addView(sizeLabel);
        addChipGrid(themeCard, new String[]{
                getString(R.string.size_small),
                getString(R.string.size_normal),
                getString(R.string.size_large)}, prefs.labelSize(), value -> {
            prefs.setLabelSize(value);
            recreate();
        }, 3, 0);

        addSwitchRow(themeCard, R.string.opt_borders, R.string.opt_borders_desc,
                Prefs.KEY_KEY_BORDERS, prefs.keyBorders());
        addSection(root, getString(R.string.section_appearance), null, themeCard);
        rebuildThemeChips();

        // ------------------------------------------------------ typing card
        LinearLayout typingCard = Ui.card(this, palette);
        addSwitchRow(typingCard, R.string.opt_haptic, R.string.opt_haptic_desc,
                Prefs.KEY_HAPTIC, prefs.haptic());
        addSwitchRow(typingCard, R.string.opt_sound, R.string.opt_sound_desc,
                Prefs.KEY_SOUND, prefs.sound());
        addSwitchRow(typingCard, R.string.opt_autocap, R.string.opt_autocap_desc,
                Prefs.KEY_AUTOCAP, prefs.autoCap());
        addSwitchRow(typingCard, R.string.opt_hints, R.string.opt_hints_desc,
                Prefs.KEY_HINTS, prefs.hints());
        addSwitchRow(typingCard, R.string.opt_popup, R.string.opt_popup_desc,
                Prefs.KEY_POPUP, prefs.showPopup());
        addSwitchRow(typingCard, R.string.opt_number_row, R.string.opt_number_row_desc,
                Prefs.KEY_NUMBER_ROW, prefs.numberRow());
        addSwitchRow(typingCard, R.string.opt_double_space, R.string.opt_double_space_desc,
                Prefs.KEY_DOUBLE_SPACE, prefs.doubleSpace());
        addSwitchRow(typingCard, R.string.opt_suggestions, R.string.opt_suggestions_desc,
                Prefs.KEY_SUGGESTIONS, prefs.suggestionsEnabled());
        addSwitchRow(typingCard, R.string.opt_incognito, R.string.opt_incognito_desc,
                Prefs.KEY_INCOGNITO, prefs.incognito());

        TextView countLabel = Ui.text(this, getString(R.string.suggestion_count), 13,
                palette.text, true);
        countLabel.setPadding(0, dp(18), 0, dp(8));
        typingCard.addView(countLabel);
        addChipGrid(typingCard, new String[]{"4", "6", "8"}, prefs.suggestionCount() == 4
                ? 0 : (prefs.suggestionCount() == 8 ? 2 : 1), value -> {
            prefs.setSuggestionCount(value == 0 ? 4 : (value == 2 ? 8 : 6));
            recreate();
        }, 3, 0);

        TextView heightPreset = Ui.text(this, getString(R.string.height_preset), 13,
                palette.text, true);
        heightPreset.setPadding(0, dp(18), 0, dp(8));
        typingCard.addView(heightPreset);
        float scale = prefs.heightScale();
        int preset = scale < 0.94f ? 0 : (scale > 1.06f ? 2 : 1);
        addChipGrid(typingCard, new String[]{
                getString(R.string.size_compact),
                getString(R.string.size_normal),
                getString(R.string.size_tall)}, preset, value -> {
            prefs.setHeightScale(value == 0 ? 0.88f : (value == 2 ? 1.14f : 1f));
            Toast.makeText(this, R.string.height_applied, Toast.LENGTH_SHORT).show();
        }, 3, 0);

        LinearLayout heightRow = Ui.horizontal(this);
        TextView heightLabel = Ui.text(this, getString(R.string.reset_height), 13,
                palette.text, true);
        heightLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        heightRow.addView(heightLabel);
        heightRow.addView(Ui.button(this, palette, getString(R.string.reset_height), false,
                () -> prefs.setHeightScale(1f)));
        addWithTopMargin(typingCard, heightRow, 14);
        addSection(root, getString(R.string.section_typing), null, typingCard);

        // ------------------------------------------------------ backup card
        LinearLayout backupCard = Ui.card(this, palette);
        backupCard.addView(Ui.text(this, getString(R.string.backup_desc), 13,
                palette.textMuted, false));
        LinearLayout backupRow = Ui.horizontal(this);
        backupRow.addView(Ui.button(this, palette, getString(R.string.backup_export),
                true, this::exportBackup));
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        importParams.leftMargin = dp(8);
        TextView importButton = Ui.button(this, palette, getString(R.string.backup_import),
                false, this::importBackup);
        backupRow.addView(importButton, importParams);
        addWithTopMargin(backupCard, backupRow, 12);
        addSection(root, getString(R.string.section_backup), null, backupCard);

        // ------------------------------------------------------- about card
        LinearLayout aboutCard = Ui.card(this, palette);
        TextView about = Ui.text(this, getString(R.string.about_body), 13,
                palette.textMuted, false);
        aboutCard.addView(about);

        TextView guide = Ui.text(this, getString(R.string.about_guide), 14, palette.accent, true);
        guide.setPadding(0, dp(16), 0, dp(4));
        guide.setClickable(true);
        guide.setBackground(Ui.ripple(this, Color.TRANSPARENT, 10, palette.keyStroke));
        guide.setOnClickListener(v -> showGuide());
        aboutCard.addView(guide);

        LinearLayout resetRow = Ui.horizontal(this);
        TextView resetLabel = Ui.text(this, getString(R.string.reset_all), 13,
                palette.text, true);
        resetLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        resetRow.addView(resetLabel);
        resetRow.addView(Ui.button(this, palette, getString(R.string.reset_all), false,
                this::confirmResetAll));
        addWithTopMargin(aboutCard, resetRow, 18);

        TextView version = Ui.text(this, getString(R.string.about_version) + "  ·  "
                + getString(R.string.footer), 12, palette.textMuted, false);
        version.setPadding(0, dp(10), 0, 0);
        aboutCard.addView(version);

        TextView libraries = Ui.text(this, getString(R.string.about_libraries), 12,
                palette.textMuted, false);
        libraries.setPadding(0, dp(4), 0, 0);
        aboutCard.addView(libraries);
        addSection(root, getString(R.string.section_about), null, aboutCard);

        return scroll;
    }

    private void addWithTopMargin(LinearLayout parent, View child, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        parent.addView(child, params);
    }

    private void addSection(LinearLayout parent, String title, String subtitle, View card) {
        TextView titleView = Ui.text(this, title, 13, palette.textMuted, true);
        titleView.setLetterSpacing(0.04f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(24);
        titleParams.bottomMargin = dp(8);
        parent.addView(titleView, titleParams);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = Ui.text(this, subtitle, 12, palette.textMuted, false);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subParams.bottomMargin = dp(8);
            parent.addView(sub, subParams);
        }
        parent.addView(card);
    }

    private TextView makeChip(String label, boolean selected, Runnable action) {
        TextView chip = Ui.text(this, label, 14, selected ? palette.onAccent : palette.text, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(0, dp(11), 0, dp(11));
        chip.setBackground(Ui.ripple(this, selected ? palette.accent : palette.keySpecial,
                12, selected ? 0x40FFFFFF : palette.keyStroke));
        chip.setClickable(true);
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    private void updateLanguageChips() {
        String code = prefs.language();
        styleChip(chipEnglish, "en".equals(code));
        styleChip(chipBangla, "bn".equals(code));
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? palette.onAccent : palette.text);
        chip.setBackground(Ui.ripple(this, selected ? palette.accent : palette.keySpecial,
                12, selected ? 0x40FFFFFF : palette.keyStroke));
    }

    private void rebuildThemeChips() {
        themeChips.removeAllViews();
        int[] values = {Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK};
        int[] labels = {R.string.theme_system, R.string.theme_light, R.string.theme_dark};
        for (int i = 0; i < values.length; i++) {
            final int value = values[i];
            TextView chip = makeChip(getString(labels[i]), prefs.theme() == value, () -> {
                prefs.setTheme(value);
                recreate();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                params.leftMargin = dp(8);
            }
            themeChips.addView(chip, params);
        }
    }

    // ----------------------------------------------------------- voice typing

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
            new AlertDialog.Builder(this, palette.dark ? R.style.Theme_Likhon_Dialog_Dark
                    : R.style.Theme_Likhon_Dialog_Light)
                    .setTitle(R.string.voice_permission_denied)
                    .setMessage(R.string.voice_permission_denied_body)
                    .setPositiveButton(R.string.action_done, null)
                    .show();
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
        LinearLayout row = Ui.settingRow(this, palette, getString(titleRes), getString(descriptionRes));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(Ui.ripple(this, Color.TRANSPARENT, 12, palette.keyStroke));

        Switch toggle = new Switch(this);
        toggle.setChecked(value);
        toggle.setShowText(false);
        toggle.setPadding(dp(8), 0, 0, 0);
        toggle.setOnCheckedChangeListener((buttonView, checked) -> prefs.setBoolean(key, checked));
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());
        card.addView(row);
    }

    private void showGuide() {
        new AlertDialog.Builder(this, palette.dark ? R.style.Theme_Likhon_Dialog_Dark
                : R.style.Theme_Likhon_Dialog_Light)
                .setTitle(R.string.guide_title)
                .setMessage(R.string.guide_body)
                .setPositiveButton(R.string.action_done, null)
                .show();
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
        chooseButton.setBackground(Ui.ripple(this, palette.accent, 12, 0x40FFFFFF));
        chooseButton.setTextColor(palette.onAccent);

        if (chipEnglish != null) {
            updateLanguageChips();
        }
    }
}
