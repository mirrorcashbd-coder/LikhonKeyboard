package com.likhon.keyboard;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The input method service. It owns the input state (language, shift, page) and
 * talks to the editor through {@link InputConnection}.
 *
 * <p>Bangla typing uses a composing region: the raw Latin word is kept here and
 * the whole word is re-transliterated on every keystroke. Committing finishes
 * the composing region, which is why {@code kotha} can become কথা even though
 * the silent “o” is only known once the next letter arrives.
 */
public class LikhonIme extends InputMethodService
        implements KeyboardView.Listener, VoiceInput.Listener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private KeyboardView keyboardView;
    private Prefs prefs;
    private Palette palette;

    private LinearLayout voicePanel;
    private TextView voiceStatus;
    private TextView voiceMode;
    private TextView voiceStopView;
    private VoiceInput voice;
    private boolean voiceListening;
    private boolean voicePreviewing;

    private Suggestions suggestions;
    private LearnedWords learned;
    private HorizontalScrollView candidateScroll;
    private LinearLayout candidateRow;
    private View resizeHandle;
    private View gripView;
    private float baseRowHeightDp = 48f;
    private String lastSuggestionKey = "";

    private static final int MAX_SUGGESTIONS = 18;

    private boolean bangla;
    private boolean shift;
    private boolean caps;
    private boolean numeric;
    private int page = Layouts.PAGE_LETTERS;
    private int emojiPage = 0;

    private final StringBuilder rawWord = new StringBuilder();
    private boolean composing;
    private String lastComposed = "";

    private int inputType;
    private String enterLabel = "";
    private String builtSignature;
    private long lastShiftTap;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        bangla = prefs.bangla();
        palette = Palette.resolve(this, prefs.theme());
        prefs.register(this);
    }

    @Override
    public void onDestroy() {
        if (voice != null) {
            voice.destroy();
            voice = null;
        }
        if (prefs != null) {
            prefs.unregister(this);
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------ input views

    @Override
    public View onCreateInputView() {
        keyboardView = new KeyboardView(this);
        keyboardView.setListener(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        suggestions = new Suggestions(this);
        learned = new LearnedWords(this);
        candidateScroll = buildCandidateBar();
        root.addView(candidateScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        voicePanel = buildVoicePanel();
        root.addView(voicePanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        resizeHandle = buildResizeHandle();
        root.addView(resizeHandle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
        root.addView(keyboardView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        builtSignature = null;
        applyPreferences();
        applyRowHeight();
        refreshLayout();
        // Read the word lists once, off the typing path.
        candidateScroll.post(new Runnable() {
            @Override
            public void run() {
                if (suggestions != null) {
                    suggestions.preload();
                }
            }
        });
        return root;
    }

    /**
     * The small strip above the keys that appears only while the microphone is
     * live: it shows what was heard, the voice mode, and a stop button.
     */
    private LinearLayout buildVoicePanel() {
        int pad = dp(8);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(pad, dp(5), pad, dp(5));
        panel.setVisibility(View.GONE);

        voiceStatus = new TextView(this);
        voiceStatus.setTextSize(14f);
        voiceStatus.setSingleLine(true);
        voiceStatus.setEllipsize(TextUtils.TruncateAt.END);
        voiceStatus.setPadding(0, 0, pad, 0);
        panel.addView(voiceStatus, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        voiceMode = new TextView(this);
        voiceMode.setTextSize(13f);
        voiceMode.setPadding(dp(10), dp(4), dp(10), dp(4));
        voiceMode.setOnClickListener(v -> cycleVoiceMode());
        panel.addView(voiceMode, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        voiceStopView = new TextView(this);
        voiceStopView.setText(R.string.voice_stop);
        voiceStopView.setTextSize(13f);
        voiceStopView.setPadding(dp(10), dp(4), dp(10), dp(4));
        voiceStopView.setOnClickListener(v -> stopVoice(false));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stopParams.leftMargin = dp(6);
        panel.addView(voiceStopView, stopParams);
        return panel;
    }

    /** The scrollable row of word suggestions above the keys. */
    private HorizontalScrollView buildCandidateBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setVisibility(View.GONE);
        candidateRow = new LinearLayout(this);
        candidateRow.setOrientation(LinearLayout.HORIZONTAL);
        candidateRow.setGravity(Gravity.CENTER_VERTICAL);
        candidateRow.setPadding(dp(4), dp(3), dp(4), dp(3));
        scroll.addView(candidateRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /**
     * A thin grip between the suggestions and the keys. Drag it up or down to
     * change the keyboard height; long-press it to go back to the default size.
     * The chosen size survives restarts in {@link Prefs#heightScale()}.
     */
    private View buildResizeHandle() {
        FrameLayout handle = new FrameLayout(this);
        handle.setContentDescription(getString(R.string.resize_hint));
        gripView = new View(this);
        gripView.setBackground(Ui.rounded(this, palette.keySpecial, 2));
        FrameLayout.LayoutParams gripParams = new FrameLayout.LayoutParams(
                dp(46), dp(4), Gravity.CENTER);
        handle.addView(gripView, gripParams);
        handle.setOnTouchListener(new View.OnTouchListener() {
            private float startRawY;
            private float startRowDp;
            private boolean moved;
            private final Runnable reset = new Runnable() {
                @Override
                public void run() {
                    prefs.setHeightScale(1f);
                    applyRowHeight();
                    startRowDp = keyboardView.getRowHeightDp();
                    Toast.makeText(LikhonIme.this, R.string.resize_reset,
                            Toast.LENGTH_SHORT).show();
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawY = event.getRawY();
                        startRowDp = keyboardView.getRowHeightDp();
                        moved = false;
                        v.postDelayed(reset, 700);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dyDp = (event.getRawY() - startRawY)
                                / getResources().getDisplayMetrics().density;
                        if (!moved && Math.abs(dyDp) > 5f) {
                            moved = true;
                            v.removeCallbacks(reset);
                        }
                        if (moved) {
                            float rows = keyboardView.getRowCount();
                            keyboardView.setRowHeightDp(clampRow(startRowDp - dyDp / rows));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(reset);
                        if (moved && baseRowHeightDp > 0f) {
                            prefs.setHeightScale(keyboardView.getRowHeightDp()
                                    / baseRowHeightDp);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        return handle;
    }

    private void applyRowHeight() {
        if (keyboardView == null) {
            return;
        }
        baseRowHeightDp = computeRowHeightDp();
        keyboardView.setRowHeightDp(clampRow(baseRowHeightDp * prefs.heightScale()));
    }

    private static float clampRow(float rowDp) {
        if (rowDp < 26f) {
            return 26f;
        }
        if (rowDp > 92f) {
            return 92f;
        }
        return rowDp;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Never take over the whole screen in landscape.
        return false;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        inputType = attribute == null ? InputType.TYPE_CLASS_TEXT : attribute.inputType;
        numeric = isNumeric(inputType);
        if (!restarting) {
            clearComposing();
            stopVoice(true);
            page = numeric ? Layouts.PAGE_NUMERIC : Layouts.PAGE_LETTERS;
            emojiPage = 0;
            shift = autoShiftForNewField();
            caps = (inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0 && !bangla;
        }
        enterLabel = resolveEnterLabel(attribute);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        applyPreferences();
        if (keyboardView != null) {
            applyRowHeight();
        }
        if (!restarting) {
            shift = autoShiftForNewField() && !bangla;
        }
        refreshLayout();
        updateSuggestions();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        finishWord();
        hideCandidates();
        if (learned != null) {
            learned.flush();
        }
        stopVoice(true);
        if (voice != null) {
            voice.destroy();
            voice = null;
        }
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (keyboardView != null) {
            applyPreferences();
            applyRowHeight();
            builtSignature = null;
            refreshLayout();
        }
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        // The framework reports every commit as a selection change, including
        // our own, so recompute instead of clearing: this keeps suggestions for
        // the word under the cursor when the cursor is moved by the app.
        if (!composing) {
            updateSuggestions();
        }
        // The selection moved away from our composing region (or the app changed
        // the text): stop re-rendering that word.
        if (composing && candidatesStart < 0) {
            // Some apps do not report a candidates region. Only treat this as a
            // cursor move if the composed word is no longer under the cursor.
            InputConnection ic = getCurrentInputConnection();
            CharSequence before = ic == null ? null
                    : ic.getTextBeforeCursor(lastComposed.length(), 0);
            boolean stillThere = before != null
                    && before.toString().equals(lastComposed);
            if (!stillThere) {
                composing = false;
                rawWord.setLength(0);
                lastComposed = "";
                if (ic != null) {
                    ic.finishComposingText();
                }
            }
        }
    }

    private boolean isNumeric(int type) {
        int cls = type & InputType.TYPE_MASK_CLASS;
        return cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE;
    }

    private boolean autoShiftForNewField() {
        if (!prefs.autoCap() || bangla) {
            return false;
        }
        boolean capSentences = (inputType & InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0;
        boolean capWords = (inputType & InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0;
        boolean capChars = (inputType & InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0;
        return capSentences || capWords || capChars;
    }

    private String resolveEnterLabel(EditorInfo info) {
        int action = 0;
        if (info != null) {
            action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (action == EditorInfo.IME_ACTION_NONE) {
                action = info.actionId;
            }
        }
        switch (action) {
            case EditorInfo.IME_ACTION_SEARCH:
                return getString(R.string.enter_search);
            case EditorInfo.IME_ACTION_SEND:
                return getString(R.string.enter_send);
            case EditorInfo.IME_ACTION_GO:
                return getString(R.string.enter_go);
            case EditorInfo.IME_ACTION_DONE:
                return getString(R.string.enter_done);
            case EditorInfo.IME_ACTION_NEXT:
                return getString(R.string.enter_next);
            default:
                return "";
        }
    }

    private float computeRowHeightDp() {
        float density = getResources().getDisplayMetrics().density;
        float screenDp = getResources().getDisplayMetrics().heightPixels / density;
        float available = screenDp * 0.44f - 14f - 18f;
        float row = available / 4f;
        return Math.max(40f, Math.min(56f, row));
    }

    private void applyPreferences() {
        if (keyboardView == null) {
            return;
        }
        palette = Palette.resolve(this, prefs.theme());
        keyboardView.setPalette(palette);
        keyboardView.setFeedback(prefs.haptic(), prefs.sound());
        styleVoicePanel();
        if (resizeHandle != null) {
            resizeHandle.setBackgroundColor(palette.bg);
        }
        if (gripView != null) {
            gripView.setBackground(Ui.rounded(this, palette.keySpecial, 2));
        }
    }

    // ------------------------------------------------------------- the layout

    private void refreshLayout() {
        if (keyboardView == null) {
            return;
        }
        String spaceLabel = bangla ? "বাংলা" : "English";
        String signature = page + "|" + bangla + "|" + shift + "|" + caps + "|"
                + prefs.hints() + "|" + enterLabel + "|" + emojiPage + "|" + numeric;
        if (signature.equals(builtSignature)) {
            return;
        }
        builtSignature = signature;
        List<Layouts.Row> rows = Layouts.build(page, bangla, shift, caps, prefs.hints(),
                enterLabel, spaceLabel, emojiPage);
        keyboardView.setKeys(rows);
    }

    // --------------------------------------------------------------- keypress

    @Override
    public void onKey(Key key) {
        if (key == null) {
            return;
        }
        switch (key.type) {
            case Key.CHAR:
                type(key.output);
                updateSuggestions();
                break;
            case Key.SPACE:
                learnCurrentWord();
                finishWord();
                commit(" ");
                afterTyped(" ");
                updateSuggestions();
                break;
            case Key.DELETE:
                backspace();
                updateSuggestions();
                break;
            case Key.ENTER:
                learnCurrentWord();
                finishWord();
                if (!sendDefaultEditorAction(true)) {
                    commit("\n");
                    afterTyped("\n");
                }
                updateSuggestions();
                break;
            case Key.SHIFT:
                toggleShift();
                break;
            case Key.LANGUAGE:
                toggleLanguage();
                break;
            case Key.SYMBOLS:
                finishWord();
                if (page == Layouts.PAGE_MORE) {
                    page = Layouts.PAGE_SYMBOLS;
                } else if (page == Layouts.PAGE_SYMBOLS) {
                    page = Layouts.PAGE_LETTERS;
                } else {
                    page = Layouts.PAGE_SYMBOLS;
                }
                refreshLayout();
                updateSuggestions();
                break;
            case Key.MORE:
                hideCandidates();
                page = Layouts.PAGE_MORE;
                refreshLayout();
                break;
            case Key.LETTERS:
                finishWord();
                page = Layouts.PAGE_LETTERS;
                refreshLayout();
                updateSuggestions();
                break;
            case Key.EMOJI:
                finishWord();
                hideCandidates();
                page = Layouts.PAGE_EMOJI;
                refreshLayout();
                break;
            case Key.PREV:
                emojiPage = (emojiPage - 1 + Layouts.emojiPageCount()) % Layouts.emojiPageCount();
                refreshLayout();
                break;
            case Key.NEXT:
                emojiPage = (emojiPage + 1) % Layouts.emojiPageCount();
                refreshLayout();
                break;
            case Key.MIC:
                toggleVoice();
                break;
            default:
                break;
        }
    }

    @Override
    public void onKeyLongPress(Key key) {
        if (key != null && key.type == Key.LANGUAGE) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    }

    // ---------------------------------------------------------------- typing

    private void type(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        char first = text.charAt(0);
        if (!Phonetic.isLetter(first)) {
            // A separator ("." "," "!" or a symbol) ends the word before it.
            learnCurrentWord();
        }
        if (bangla && Phonetic.isLetter(first)) {
            rawWord.append(text);
            composing = true;
            setComposing(Phonetic.convert(rawWord.toString()));
            afterTyped(text);
            return;
        }
        finishWord();
        if (bangla && !numeric) {
            text = banglaText(text);
        }
        commit(text);
        afterTyped(text);
    }

    private void commit(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, 1);
        }
    }

    private void setComposing(String text) {
        lastComposed = text;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(text, 1);
        }
    }

    private void backspace() {
        if (bangla && composing) {
            if (rawWord.length() > 0) {
                rawWord.deleteCharAt(rawWord.length() - 1);
            }
            if (rawWord.length() > 0) {
                setComposing(Phonetic.convert(rawWord.toString()));
            } else {
                // Drop the composing region, then delete the glyph that was
                // already drawn for the removed letter.
                composing = false;
                lastComposed = "";
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.finishComposingText();
                }
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            }
            return;
        }
        finishWord();
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void finishWord() {
        if (!composing) {
            return;
        }
        composing = false;
        rawWord.setLength(0);
        lastComposed = "";
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.finishComposingText();
        }
    }

    private void clearComposing() {
        finishWord();
        rawWord.setLength(0);
        composing = false;
        builtSignature = null;
    }

    private void toggleShift() {
        long now = SystemClock.uptimeMillis();
        boolean doubleTap = now - lastShiftTap < 320;
        lastShiftTap = now;
        if (doubleTap) {
            caps = !caps;
            shift = caps;
        } else {
            shift = !shift;
            caps = false;
        }
        refreshLayout();
    }

    private void toggleLanguage() {
        finishWord();
        bangla = !bangla;
        prefs.setBangla(bangla);
        shift = false;
        caps = false;
        page = numeric ? Layouts.PAGE_NUMERIC : Layouts.PAGE_LETTERS;
        emojiPage = 0;
        refreshLayout();
        lastSuggestionKey = "";
        updateSuggestions();
    }

    private void afterTyped(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        char last = text.charAt(text.length() - 1);
        if (!bangla) {
            if (prefs.autoCap() && isSentenceEnd(last)) {
                shift = true;
            } else if (!(text.equals(" ") && shift) && shift && !caps) {
                // A space keeps a capital that was just triggered by "." / "?"
                // but clears a manually pressed shift after a character.
                shift = false;
            }
        } else if (shift && !caps) {
            shift = false;
        }
        refreshLayout();
    }

    private boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '\n' || c == '।';
    }

    /** Characters that differ between the ASCII layout and Bangla typing. */
    private String banglaText(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '.':
                    sb.append('।');
                    break;
                case '~':
                    sb.append('ঁ');
                    break;
                case '^':
                    sb.append('্');
                    break;
                case '$':
                    sb.append('৳');
                    break;
                default:
                    if (c >= '0' && c <= '9') {
                        sb.append((char) ('০' + (c - '0')));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // --------------------------------------------------------------- learning

    /** Adds the word just finished to the learned list. */
    private void learnCurrentWord() {
        if (learned == null || isSensitiveField()) {
            return;
        }
        if (bangla && composing) {
            learned.add(lastComposed, rawWord.toString());
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(40, 0);
        if (before == null || before.length() == 0) {
            return;
        }
        learned.add(lastWordIn(before), null);
    }

    private static String lastWordIn(CharSequence text) {
        int end = text.length();
        int start = end;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isLetter(c) || c == '\'') {
                start--;
            } else {
                break;
            }
        }
        return text.subSequence(start, end).toString();
    }

    // ------------------------------------------------------------------ voice

    private void toggleVoice() {
        if (voiceListening) {
            stopVoice(false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.voice_permission_needed, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(SettingsActivity.EXTRA_REQUEST_MIC, true);
            startActivity(intent);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        finishWord();
        hideCandidates();
        voicePreviewing = false;
        voiceListening = true;
        keyboardView.setVoiceActive(true);
        voicePanel.setVisibility(View.VISIBLE);
        voiceStatus.setText(R.string.voice_listening);
        updateVoiceLabels();
        if (voice == null) {
            voice = new VoiceInput(this, this);
        }
        voice.start(voiceLanguage(), prefs.voiceContinuous());
    }

    /** Asks the recogniser to finish what it heard and closes the strip. */
    private void stopVoice(boolean cancel) {
        if (voice != null) {
            if (cancel) {
                voice.cancel();
            } else {
                voice.stop();
            }
        }
        voiceListening = false;
        voicePreviewing = false;
        if (keyboardView != null) {
            keyboardView.setVoiceActive(false);
        }
        if (voicePanel != null) {
            voicePanel.setVisibility(View.GONE);
        }
        lastSuggestionKey = "";
        updateSuggestions();
    }

    private String voiceLanguage() {
        String configured = prefs.voiceLang();
        if (Prefs.VOICE_AUTO.equals(configured)) {
            return bangla ? "bn-BD" : "en-US";
        }
        return configured;
    }

    private boolean voiceIsBangla() {
        return voiceLanguage().startsWith("bn");
    }

    private String voiceModeLabel() {
        if (!voiceIsBangla()) {
            return getString(R.string.voice_mode_english);
        }
        return prefs.voiceBanglish()
                ? getString(R.string.voice_mode_banglish)
                : getString(R.string.voice_mode_bangla);
    }

    private void updateVoiceLabels() {
        if (voiceMode != null) {
            voiceMode.setText(voiceModeLabel());
            styleVoicePanel();
        }
    }

    /** বাংলা → Banglish → English → বাংলা */
    private void cycleVoiceMode() {
        if (!voiceIsBangla()) {
            prefs.setVoiceLang("bn-BD");
            prefs.setBoolean(Prefs.KEY_VOICE_BANGLISH, false);
        } else if (!prefs.voiceBanglish()) {
            prefs.setBoolean(Prefs.KEY_VOICE_BANGLISH, true);
        } else {
            prefs.setVoiceLang("en-US");
            prefs.setBoolean(Prefs.KEY_VOICE_BANGLISH, false);
        }
        updateVoiceLabels();
        if (voiceListening && voice != null) {
            // Apply the new language to the running session immediately.
            voice.cancel();
            voice.start(voiceLanguage(), prefs.voiceContinuous());
            voiceStatus.setText(R.string.voice_listening);
        }
    }

    /** Applies the cleanup + optional Banglish conversion to raw speech. */
    private String voiceText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = prefs.voiceCleanup() ? VoiceText.clean(raw) : raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        if (prefs.voiceBanglish() && voiceIsBangla()) {
            text = Romanize.toLatin(text);
        }
        return text;
    }

    private void showVoicePreview(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(text, 1);
            voicePreviewing = true;
        }
    }

    private String voiceErrorMessage(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return getString(R.string.voice_err_permission);
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
                return getString(R.string.voice_err_network);
            case SpeechRecognizer.ERROR_AUDIO:
                return getString(R.string.voice_err_audio);
            case 12: // ERROR_LANGUAGE_UNAVAILABLE (API 31)
            case 13: // ERROR_LANGUAGE_NOT_SUPPORTED (API 31)
                return getString(R.string.voice_err_language);
            default:
                return getString(R.string.voice_err_generic);
        }
    }

    private void styleVoicePanel() {
        if (voicePanel == null || palette == null) {
            return;
        }
        voicePanel.setBackgroundColor(palette.keySpecial);
        voiceStatus.setTextColor(palette.text);
        voiceMode.setTextColor(palette.onAccent);
        voiceMode.setBackground(Ui.rounded(this, palette.accent, 10));
        voiceStopView.setTextColor(palette.text);
        voiceStopView.setBackground(Ui.rounded(this, palette.keyBg, 10));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onVoiceListening() {
        if (!voiceListening || voiceStatus == null) {
            return;
        }
        if (!voicePreviewing) {
            voiceStatus.setText(R.string.voice_listening);
        }
        updateVoiceLabels();
    }

    @Override
    public void onVoicePartial(String text) {
        if (!voiceListening) {
            return;
        }
        String converted = voiceText(text);
        if (converted.isEmpty()) {
            return;
        }
        if (voiceStatus != null) {
            voiceStatus.setText(converted);
        }
        showVoicePreview(converted);
    }

    @Override
    public void onVoiceResult(String text) {
        String converted = voiceText(text);
        if (converted.isEmpty()) {
            return;
        }
        voicePreviewing = false;
        commit(converted + " ");
        if (learned != null) {
            for (String word : converted.split("[\\s,.;:!?।]+")) {
                learned.add(word, null);
            }
        }
        if (voiceStatus != null) {
            voiceStatus.setText(converted);
        }
        if (!prefs.voiceContinuous() && voiceListening) {
            stopVoice(false);
        }
    }

    @Override
    public void onVoiceStopped() {
        if (!voiceListening) {
            return;
        }
        voiceListening = false;
        voicePreviewing = false;
        if (keyboardView != null) {
            keyboardView.setVoiceActive(false);
        }
        if (voicePanel != null) {
            voicePanel.setVisibility(View.GONE);
        }
        lastSuggestionKey = "";
        updateSuggestions();
    }

    @Override
    public void onVoiceError(int code) {
        voiceListening = false;
        voicePreviewing = false;
        if (keyboardView != null) {
            keyboardView.setVoiceActive(false);
        }
        if (voicePanel != null) {
            voicePanel.setVisibility(View.GONE);
        }
        Toast.makeText(this, voiceErrorMessage(code), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onVoiceUnavailable() {
        voiceListening = false;
        voicePreviewing = false;
        if (keyboardView != null) {
            keyboardView.setVoiceActive(false);
        }
        if (voicePanel != null) {
            voicePanel.setVisibility(View.GONE);
        }
        Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ suggestions

    /**
     * Rebuilds the strip. It is deliberately never empty on a typing page: with
     * no prefix it shows learned words first and then the most common words, so
     * suggestions are available before the first letter as well.
     */
    private void updateSuggestions() {
        if (candidateRow == null || learned == null) {
            return;
        }
        boolean typingPage = page == Layouts.PAGE_LETTERS || page == Layouts.PAGE_SYMBOLS;
        if (numeric || !typingPage || isSensitiveField()) {
            hideCandidates();
            return;
        }
        String prefix;
        if (bangla) {
            prefix = composing ? rawWord.toString() : "";
        } else {
            prefix = currentWordBeforeCursor();
        }
        String key = (bangla ? "B" : "E") + (shift ? "^" : "") + prefix;
        if (key.equals(lastSuggestionKey) && candidateScroll != null
                && candidateScroll.getVisibility() == View.VISIBLE) {
            return;
        }
        lastSuggestionKey = key;
        if (suggestions == null) {
            suggestions = new Suggestions(this);
        }

        List<String> merged = new ArrayList<>();
        merged.addAll(learned.suggest(prefix, bangla, 8));
        List<String> base;
        if (prefix.isEmpty()) {
            base = suggestions.popular(bangla, MAX_SUGGESTIONS);
        } else if (bangla) {
            base = suggestions.bangla(prefix);
        } else {
            base = suggestions.english(prefix);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(merged);
        unique.addAll(base);
        merged = new ArrayList<>(unique);

        // Never offer the word that is already in the editor.
        String current = bangla ? (composing ? lastComposed : "") : prefix;
        if (!current.isEmpty()) {
            merged.remove(current);
        }
        if (merged.isEmpty()) {
            // Nothing matched: keep the promise that suggestions are always
            // there by falling back to common words.
            merged = suggestions.popular(bangla, MAX_SUGGESTIONS);
        }
        if (!bangla && !merged.isEmpty()) {
            boolean capital = !prefix.isEmpty()
                    ? Character.isUpperCase(prefix.charAt(0))
                    : shift;
            if (capital) {
                List<String> cased = new ArrayList<>(merged.size());
                for (String word : merged) {
                    cased.add(capitalised(word));
                }
                merged = cased;
            }
        }
        if (merged.size() > MAX_SUGGESTIONS) {
            merged = new ArrayList<>(merged.subList(0, MAX_SUGGESTIONS));
        }
        showCandidates(merged);
    }

    private void showCandidates(List<String> words) {
        if (candidateRow == null || candidateScroll == null) {
            return;
        }
        candidateRow.removeAllViews();
        if (words == null || words.isEmpty()) {
            candidateScroll.setVisibility(View.GONE);
            return;
        }
        for (final String word : words) {
            TextView chip = new TextView(this);
            chip.setText(word);
            chip.setTextSize(15f);
            chip.setSingleLine(true);
            chip.setTextColor(palette.text);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            chip.setBackground(Ui.ripple(this, palette.keyBg, 9, palette.accent));
            chip.setOnClickListener(v -> pickSuggestion(word));
            chip.setOnLongClickListener(v -> {
                if (learned.remove(word)) {
                    Toast.makeText(LikhonIme.this, R.string.suggestion_forgotten,
                            Toast.LENGTH_SHORT).show();
                    lastSuggestionKey = "";
                    updateSuggestions();
                }
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = dp(6);
            candidateRow.addView(chip, params);
        }
        candidateScroll.setBackgroundColor(palette.keySpecial);
        candidateScroll.setVisibility(View.VISIBLE);
    }

    private void hideCandidates() {
        lastSuggestionKey = "";
        if (candidateRow != null) {
            candidateRow.removeAllViews();
        }
        if (candidateScroll != null) {
            candidateScroll.setVisibility(View.GONE);
        }
    }

    private void pickSuggestion(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        if (bangla) {
            learned.add(word, composing ? rawWord.toString() : null);
            if (composing) {
                // Replace the composing region with the chosen word, then end
                // composing and only add the space.
                setComposing(word);
                finishWord();
                commit(" ");
            } else {
                commit(word + " ");
            }
        } else {
            String typed = currentWordBeforeCursor();
            if (!typed.isEmpty()) {
                ic.deleteSurroundingText(typed.length(), 0);
            }
            learned.add(word, null);
            ic.commitText(word + " ", 1);
        }
        afterTyped(" ");
        lastSuggestionKey = "";
        updateSuggestions();
    }

    /** Letters (plus apostrophes) immediately before the cursor. */
    private String currentWordBeforeCursor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return "";
        }
        CharSequence before = ic.getTextBeforeCursor(48, 0);
        if (before == null || before.length() == 0) {
            return "";
        }
        int end = before.length();
        int start = end;
        while (start > 0) {
            char c = before.charAt(start - 1);
            if (Character.isLetter(c) || c == '\'') {
                start--;
            } else {
                break;
            }
        }
        return before.subSequence(start, end).toString();
    }

    private boolean isSensitiveField() {
        int cls = inputType & InputType.TYPE_MASK_CLASS;
        if (cls != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private static String capitalised(String word) {
        if (word.isEmpty() || !Character.isLowerCase(word.charAt(0))) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    // ----------------------------------------------------------- preferences

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (keyboardView == null || prefs == null) {
            return;
        }
        if (Prefs.KEY_BANGLA.equals(key)) {
            boolean wanted = prefs.bangla();
            if (wanted != bangla && !composing) {
                bangla = wanted;
                page = numeric ? Layouts.PAGE_NUMERIC : Layouts.PAGE_LETTERS;
                refreshLayout();
            }
            return;
        }
        if (Prefs.KEY_VOICE_LANG.equals(key) || Prefs.KEY_VOICE_BANGLISH.equals(key)) {
            updateVoiceLabels();
            return;
        }
        applyPreferences();
        builtSignature = null;
        refreshLayout();
    }

}
