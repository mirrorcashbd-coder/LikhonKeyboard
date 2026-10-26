package com.likhon.keyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
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

import java.util.List;

/**
 * Setup + settings screen. Deliberately one short screen: enable the keyboard,
 * pick it, try it, then adjust the few options that matter.
 */
public class SettingsActivity extends Activity {

    private Prefs prefs;
    private Palette palette;

    private View statusDot;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView enableButton;
    private TextView chooseButton;

    private TextView chipEnglish;
    private TextView chipBangla;
    private LinearLayout themeChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = new Prefs(this);
        palette = Palette.resolve(this, prefs.theme());
        setTheme(palette.dark ? R.style.Theme_Likhon_Dark : R.style.Theme_Likhon_Light);
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildContent());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
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
        chipEnglish = makeChip(getString(R.string.lang_english), !prefs.bangla(), () -> {
            prefs.setBangla(false);
            updateLanguageChips();
        });
        chipBangla = makeChip(getString(R.string.lang_bangla), prefs.bangla(), () -> {
            prefs.setBangla(true);
            updateLanguageChips();
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
        languageHint.setPadding(0, dp(10), 0, 0);
        inputCard.addView(languageHint);
        addSection(root, getString(R.string.section_input),
                getString(R.string.section_input_desc), inputCard);

        // -------------------------------------------------- appearance card
        LinearLayout themeCard = Ui.card(this, palette);
        themeChips = Ui.horizontal(this);
        themeCard.addView(themeChips);
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
        addSection(root, getString(R.string.section_typing), null, typingCard);

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

        TextView version = Ui.text(this, getString(R.string.about_version) + "  ·  "
                + getString(R.string.footer), 12, palette.textMuted, false);
        version.setPadding(0, dp(10), 0, 0);
        aboutCard.addView(version);
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
        boolean bangla = prefs.bangla();
        styleChip(chipEnglish, !bangla);
        styleChip(chipBangla, bangla);
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
