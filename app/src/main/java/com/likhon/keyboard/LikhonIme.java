package com.likhon.keyboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
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
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private View voiceLevel;
    private LinearLayout.LayoutParams voiceLevelParams;
    private int voiceMisses;
    private int voiceEngineErrors;
    private boolean voiceHelpShown;
    private boolean voiceOnDeviceTried;
    /** Language tried after Bangla (India) when bn-BD hears nothing. */
    private String voiceLangOverride;

    private Suggestions suggestions;
    private LearnedWords learned;
    private HorizontalScrollView candidateScroll;
    private LinearLayout candidateRow;
    private View resizeHandle;
    private View gripView;
    private float baseRowHeightDp = 48f;
    private String lastSuggestionKey = "";

    // Editing tools: the toolbar above the suggestions, the clipboard history
    // and the undo stack. All of it is local to this keyboard.
    private LinearLayout toolBar;
    private View toolDivider;
    private ImageView undoView;
    private ImageView redoView;
    private LinearLayout panelHost;
    private LinearLayout clipPanel;
    private LinearLayout clipList;
    private TextView clipCount;
    private LinearLayout translatePanel;
    private TextView translateTitle;
    private TextView translateSourceView;
    private TextView translateResultView;
    private ClipStore clips;
    private UndoStack undoStack;
    private Handler clipHandler;
    private Runnable clipPoll;
    private String lastClipSeen = "";
    private String translateSourceText = "";
    private String translateResultText = "";
    private boolean translateFromSelection;
    private long clearArmedAt;
    private long lastSpaceAt;

    // Voice: a handler for the screen hand-off and the "inline stayed silent"
    // timer.
    private Handler uiHandler;
    private boolean voiceGotText;
    private final Runnable voiceScreenFallback = new Runnable() {
        @Override
        public void run() {
            if (!voiceListening || voiceGotText) {
                return;
            }
            VoiceLog.add("inline silent, opening voice screen");
            stopVoice(true);
            openVoiceScreen();
        }
    };

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
        uiHandler = new Handler(Looper.getMainLooper());
        VoiceScreenBridge.listen(() -> uiHandler.postDelayed(this::flushVoiceScreenText, 400));
    }

    @Override
    public void onDestroy() {
        VoiceScreenBridge.listen(null);
        if (uiHandler != null) {
            uiHandler.removeCallbacksAndMessages(null);
        }
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
        clips = new ClipStore(this);
        undoStack = new UndoStack();
        toolBar = buildToolBar();
        root.addView(toolBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
        toolDivider = new View(this);
        toolDivider.setBackgroundColor(palette.divider);
        root.addView(toolDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f))));
        candidateScroll = buildCandidateBar();
        root.addView(candidateScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panelHost = new LinearLayout(this);
        panelHost.setOrientation(LinearLayout.VERTICAL);
        panelHost.setVisibility(View.GONE);
        root.addView(panelHost, new LinearLayout.LayoutParams(
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

        voiceLevel = new View(this);
        voiceLevel.setBackground(Ui.rounded(this, palette.accent, 2));
        voiceLevelParams = new LinearLayout.LayoutParams(dp(3), dp(6));
        voiceLevelParams.rightMargin = dp(8);
        panel.addView(voiceLevel, voiceLevelParams);

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
    public void onWindowShown() {
        super.onWindowShown();
        if (uiHandler != null) {
            uiHandler.postDelayed(this::flushVoiceScreenText, 320);
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (uiHandler != null) {
            uiHandler.postDelayed(this::flushVoiceScreenText, 320);
        }
        if (undoStack != null) {
            undoStack.reset();
            refreshToolbar();
        }
        hidePanels();
        startClipWatch();
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
        stopClipWatch();
        hidePanels();
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
        // The editing toolbar (36dp) now sits above the suggestions, so the
        // rows give back most of that height to keep the keyboard the same size.
        float available = screenDp * 0.44f - 14f - 18f - 30f;
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
        keyboardView.setShowPreview(prefs.showPopup());
        styleVoicePanel();
        if (resizeHandle != null) {
            resizeHandle.setBackgroundColor(palette.bg);
        }
        if (gripView != null) {
            gripView.setBackground(Ui.rounded(this, palette.keySpecial, 2));
        }
        if (voiceLevel != null) {
            voiceLevel.setBackground(Ui.rounded(this, palette.accent, 2));
        }
        if (toolBar != null) {
            toolBar.setBackgroundColor(palette.bg);
            styleToolBar();
        }
        if (toolDivider != null) {
            toolDivider.setBackgroundColor(palette.divider);
        }
        if (panelHost != null) {
            panelHost.setBackgroundColor(palette.surfaceAlt);
        }
    }

    // ------------------------------------------------------------- the layout

    private void refreshLayout() {
        if (keyboardView == null) {
            return;
        }
        String spaceLabel = bangla ? "বাংলা" : "English";
        String signature = page + "|" + bangla + "|" + shift + "|" + caps + "|"
                + prefs.hints() + "|" + prefs.numberRow() + "|" + enterLabel + "|"
                + emojiPage + "|" + numeric;
        if (signature.equals(builtSignature)) {
            return;
        }
        builtSignature = signature;
        List<Layouts.Row> rows = Layouts.build(page, bangla, shift, caps, prefs.hints(),
                prefs.numberRow(), enterLabel, spaceLabel, emojiPage);
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
                long now = SystemClock.uptimeMillis();
                if (prefs.doubleSpace() && lastSpaceAt > 0 && now - lastSpaceAt < 450) {
                    recordUndo(false);
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) {
                        ic.deleteSurroundingText(1, 0);
                        ic.commitText(bangla ? "। " : ". ", 1);
                    }
                    lastSpaceAt = 0;
                    afterTyped(". ");
                } else {
                    recordUndo(false);
                    commit(" ");
                    afterTyped(" ");
                    lastSpaceAt = now;
                }
                updateSuggestions();
                break;
            case Key.DELETE:
                backspace();
                updateSuggestions();
                break;
            case Key.ENTER:
                learnCurrentWord();
                finishWord();
                recordUndo(false);
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
            openSettings();
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(SettingsActivity.EXTRA_PANEL, SettingsActivity.PANEL_EDITING);
        startActivity(intent);
    }

    // ---------------------------------------------------------------- typing

    private void type(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        recordUndo(true);
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
        recordUndo(false);
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

    // -------------------------------------------------------- editing tools

    /** The small always-on row above the suggestions: undo … translate. */
    private LinearLayout buildToolBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(3), 0, dp(3), 0);
        bar.setBackgroundColor(palette.bg);
        undoView = toolButton(bar, R.drawable.ic_undo, R.string.action_undo, this::performUndo);
        redoView = toolButton(bar, R.drawable.ic_redo, R.string.action_redo, this::performRedo);
        toolButton(bar, R.drawable.ic_clipboard, R.string.action_clipboard, this::toggleClipPanel);
        toolButton(bar, R.drawable.ic_copy, R.string.action_copy, this::copyToClipboard);
        toolButton(bar, R.drawable.ic_paste, R.string.action_paste, this::pasteFromClipboard);
        toolButton(bar, R.drawable.ic_translate, R.string.action_translate, this::toggleTranslatePanel);
        toolButton(bar, R.drawable.ic_settings, R.string.action_settings, this::openSettings);
        refreshToolbar();
        return bar;
    }

    /**
     * One Material icon in the toolbar. A short press animation makes the tap
     * feel answered without any extra drawable or library.
     */
    private ImageView toolButton(LinearLayout bar, int iconRes, int description,
                                 Runnable action) {
        ImageView view = new ImageView(this);
        view.setImageResource(iconRes);
        view.setColorFilter(palette.text);
        view.setContentDescription(getString(description));
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        view.setBackground(Ui.ripple(this, palette.bg, 10, palette.keyStroke));
        view.setClickable(true);
        view.setFocusable(true);
        int pad = dp(7);
        view.setPadding(pad, pad, pad, pad);
        view.setOnClickListener(v -> {
            pulse(v);
            action.run();
        });
        bar.addView(view, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        return view;
    }

    /** A short pivot that shows which way the history just moved. */
    private static void spin(View view, float degrees) {
        if (view == null) {
            return;
        }
        view.setRotation(0f);
        view.animate()
                .rotation(degrees)
                .setDuration(110)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> view.animate()
                        .rotation(0f)
                        .setDuration(160)
                        .setInterpolator(new OvershootInterpolator(1.6f))
                        .start())
                .start();
    }

    /** Quick squash-and-settle, the animation every modern toolbar has. */
    private static void pulse(View view) {
        view.animate().cancel();
        view.animate()
                .scaleX(0.78f).scaleY(0.78f)
                .setDuration(80)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(190)
                        .setInterpolator(new OvershootInterpolator(2.4f))
                        .start())
                .start();
    }

    private void styleToolBar() {
        if (toolBar == null) {
            return;
        }
        for (int i = 0; i < toolBar.getChildCount(); i++) {
            View child = toolBar.getChildAt(i);
            if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(palette.text);
                child.setBackground(Ui.ripple(this, palette.bg, 10, palette.keyStroke));
            }
        }
        refreshToolbar();
    }

    private void refreshToolbar() {
        boolean canUndo = undoStack != null && undoStack.canUndo();
        boolean canRedo = undoStack != null && undoStack.canRedo();
        if (undoView != null) {
            undoView.setAlpha(canUndo ? 1f : 0.3f);
        }
        if (redoView != null) {
            redoView.setAlpha(canRedo ? 1f : 0.3f);
        }
    }

    private void hidePanels() {
        if (panelHost != null) {
            panelHost.removeAllViews();
            panelHost.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------- undo

    /**
     * Remembers the field as it is now, before an edit. Only plain text fields
     * that hand out their text through {@link ExtractedText} can be undone;
     * everywhere else the undo button simply stays dim.
     */
    private void recordUndo(boolean typing) {
        if (undoStack == null || isSensitiveField()) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        try {
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et == null || et.text == null || et.startOffset != 0) {
                return;
            }
            String text = et.text.toString();
            if (text.length() > 20000) {
                return;
            }
            undoStack.record(text, et.selectionStart, et.selectionEnd, typing);
            refreshToolbar();
        } catch (RuntimeException ignored) {
            // The app does not support extracted text; no undo there.
        }
    }

    private void performUndo() {
        spin(undoView, -28f);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || undoStack == null) {
            return;
        }
        ExtractedText et = extracted(ic);
        String current = et != null && et.text != null ? et.text.toString() : null;
        if (current == null) {
            Toast.makeText(this, R.string.undo_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        UndoStack.Snap snap = undoStack.undo(current,
                et.selectionStart, et.selectionEnd);
        if (snap != null) {
            restore(ic, current, snap);
        }
        refreshToolbar();
    }

    private void performRedo() {
        spin(redoView, 28f);
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || undoStack == null) {
            return;
        }
        ExtractedText et = extracted(ic);
        String current = et != null && et.text != null ? et.text.toString() : null;
        if (current == null) {
            return;
        }
        UndoStack.Snap snap = undoStack.redo(current,
                et.selectionStart, et.selectionEnd);
        if (snap != null) {
            restore(ic, current, snap);
        }
        refreshToolbar();
    }

    private void restore(InputConnection ic, String current, UndoStack.Snap snap) {
        finishWord();
        try {
            ic.beginBatchEdit();
            ic.setSelection(0, current.length());
            ic.commitText(snap.text, 1);
            int length = snap.text.length();
            ic.setSelection(Math.min(snap.start, length), Math.min(snap.end, length));
            ic.endBatchEdit();
        } catch (RuntimeException ignored) {
            // Nothing sensible to do if the editor refuses.
        }
        lastSuggestionKey = "";
        updateSuggestions();
    }

    private ExtractedText extracted(InputConnection ic) {
        try {
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et == null || et.text == null || et.startOffset != 0) {
                return null;
            }
            return et;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // -------------------------------------------------------------- clipboard

    /** Keeps watch for new copies while the keyboard is on screen. */
    private void startClipWatch() {
        stopClipWatch();
        clipHandler = new Handler(Looper.getMainLooper());
        clipPoll = new Runnable() {
            @Override
            public void run() {
                captureClipboard();
                if (clipHandler != null) {
                    clipHandler.postDelayed(this, 1500);
                }
            }
        };
        clipHandler.postDelayed(clipPoll, 500);
    }

    private void stopClipWatch() {
        if (clipHandler != null && clipPoll != null) {
            clipHandler.removeCallbacks(clipPoll);
        }
        clipHandler = null;
        clipPoll = null;
    }

    private void captureClipboard() {
        if (clips == null || isSensitiveField()) {
            return;
        }
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) {
                return;
            }
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            if (text == null) {
                return;
            }
            String value = text.toString().trim();
            if (value.isEmpty() || value.equals(lastClipSeen)) {
                return;
            }
            lastClipSeen = value;
            clips.add(value);
            if (clipPanel != null && clipPanel.getVisibility() == View.VISIBLE) {
                rebuildClipList();
            }
        } catch (RuntimeException ignored) {
            // Clipboard access can be denied while the field is not focused.
        }
    }

    private void copyToClipboard() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        CharSequence selection = ic.getSelectedText(0);
        String text = selection == null ? "" : selection.toString();
        boolean whole = false;
        if (text.trim().isEmpty()) {
            ExtractedText et = extracted(ic);
            if (et != null && et.text != null) {
                text = et.text.toString();
                whole = true;
            } else {
                CharSequence before = ic.getTextBeforeCursor(1000, 0);
                text = before == null ? "" : before.toString();
            }
        }
        if (text.trim().isEmpty()) {
            Toast.makeText(this, R.string.clip_nothing, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("Likhon", text));
        }
        if (clips != null) {
            clips.add(text);
            lastClipSeen = text.trim();
        }
        Toast.makeText(this, whole ? R.string.clip_copied_all : R.string.clip_copied,
                Toast.LENGTH_SHORT).show();
    }

    private String systemClipText() {
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) {
                return "";
            }
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                return "";
            }
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            return text == null ? "" : text.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void pasteFromClipboard() {
        String text = systemClipText();
        if (text.trim().isEmpty() && clips != null) {
            List<ClipStore.Entry> entries = clips.entries();
            if (!entries.isEmpty()) {
                text = entries.get(0).text;
            }
        }
        if (text.trim().isEmpty()) {
            Toast.makeText(this, R.string.paste_nothing, Toast.LENGTH_SHORT).show();
            return;
        }
        insertText(text);
    }

    private void insertText(String text) {
        recordUndo(false);
        finishWord();
        commit(text);
        updateSuggestions();
    }

    private void toggleClipPanel() {
        if (clipPanel != null && clipPanel.getParent() != null) {
            hidePanels();
            return;
        }
        hidePanels();
        if (clipPanel == null) {
            clipPanel = buildClipPanel();
        }
        rebuildClipList();
        panelHost.addView(clipPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panelHost.setVisibility(View.VISIBLE);
    }

    private LinearLayout buildClipPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        clipCount = Ui.text(this, getString(R.string.clip_title), 12, palette.textMuted, true);
        clipCount.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(clipCount);
        header.addView(panelButton(getString(R.string.clip_clear), this::confirmClearClips));
        header.addView(panelButton("✕", this::hidePanels));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(140)));
        clipList = new LinearLayout(this);
        clipList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(clipList);
        panel.addView(scroll);
        return panel;
    }

    private TextView panelButton(String label, Runnable action) {
        TextView view = Ui.text(this, label, 12, palette.text, true);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setBackground(Ui.ripple(this, palette.keySpecial, 8, palette.keyStroke));
        view.setClickable(true);
        view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    private void rebuildClipList() {
        if (clipList == null || clips == null) {
            return;
        }
        clipList.removeAllViews();
        List<ClipStore.Entry> entries = clips.entries();
        if (clipCount != null) {
            clipCount.setText(getString(R.string.clip_title_count, entries.size()));
        }
        if (entries.isEmpty()) {
            clipList.addView(Ui.text(this, getString(R.string.clip_empty), 12,
                    palette.textMuted, false));
            return;
        }
        for (final ClipStore.Entry entry : entries) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView text = Ui.text(this, preview(entry.text), 13, palette.text, false);
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setPadding(dp(10), dp(9), dp(10), dp(9));
            text.setBackground(Ui.ripple(this, palette.surface, 8, palette.keyStroke));
            text.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            text.setOnClickListener(v -> {
                insertText(entry.text);
                hidePanels();
            });
            row.addView(text);
            TextView delete = Ui.text(this, "✕", 13, palette.textMuted, true);
            delete.setPadding(dp(12), dp(9), dp(6), dp(9));
            delete.setContentDescription(getString(R.string.clip_delete));
            delete.setOnClickListener(v -> {
                clips.remove(entry.id);
                rebuildClipList();
            });
            row.addView(delete);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(4);
            clipList.addView(row, rowParams);
        }
    }

    /**
     * Two taps instead of a dialog: an input method cannot reliably open a
     * dialog window, so the first tap only arms the button.
     */
    private void confirmClearClips() {
        if (clips == null || clips.size() == 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - clearArmedAt > 4000) {
            clearArmedAt = now;
            Toast.makeText(this, getString(R.string.clip_clear_confirm, clips.size()),
                    Toast.LENGTH_LONG).show();
            return;
        }
        clearArmedAt = 0;
        clips.clear();
        rebuildClipList();
        Toast.makeText(this, R.string.clip_cleared, Toast.LENGTH_SHORT).show();
    }

    private static String preview(String text) {
        String oneLine = text.replace('\n', ' ').trim();
        return oneLine.length() <= 80 ? oneLine : oneLine.substring(0, 80) + "…";
    }

    // -------------------------------------------------------------- translate

    private void toggleTranslatePanel() {
        if (translatePanel != null && translatePanel.getParent() != null) {
            hidePanels();
            return;
        }
        hidePanels();
        if (translatePanel == null) {
            translatePanel = buildTranslatePanel();
        }
        String source = translateSource();
        if (source.isEmpty()) {
            Toast.makeText(this, R.string.translate_nothing, Toast.LENGTH_SHORT).show();
            return;
        }
        translateSourceText = source;
        translateResultText = "";
        if (translateSourceView != null) {
            translateSourceView.setText(source);
        }
        if (translateResultView != null) {
            translateResultView.setText(R.string.translate_loading);
        }
        panelHost.addView(translatePanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        panelHost.setVisibility(View.VISIBLE);
        runTranslation();
    }

    private String translateSource() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return "";
        }
        translateFromSelection = false;
        CharSequence selection = ic.getSelectedText(0);
        if (selection != null && !selection.toString().trim().isEmpty()) {
            translateFromSelection = true;
            return selection.toString().trim();
        }
        ExtractedText et = extracted(ic);
        if (et != null && et.text != null) {
            String all = et.text.toString().trim();
            if (!all.isEmpty() && all.length() <= 400) {
                return all;
            }
        }
        CharSequence before = ic.getTextBeforeCursor(200, 0);
        return before == null ? "" : before.toString().trim();
    }

    private LinearLayout buildTranslatePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        translateTitle = Ui.text(this, getString(R.string.action_translate), 12,
                palette.textMuted, true);
        translateTitle.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(translateTitle);
        header.addView(panelButton("✕", this::hidePanels));
        panel.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        translateSourceView = Ui.text(this, "", 12, palette.textMuted, false);
        translateSourceView.setMaxLines(2);
        translateSourceView.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(translateSourceView);

        translateResultView = Ui.text(this, "", 15, palette.text, true);
        translateResultView.setPadding(0, dp(4), 0, dp(6));
        panel.addView(translateResultView);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(panelButton(getString(R.string.translate_insert),
                this::insertTranslation));
        actions.addView(panelButton(getString(R.string.translate_replace),
                this::replaceTranslation));
        actions.addView(panelButton(getString(R.string.translate_copy),
                this::copyTranslation));
        panel.addView(actions);
        return panel;
    }

    private void runTranslation() {
        final String source = translateSourceText;
        final boolean toBangla = !Translator.isBangla(source);
        if (translateTitle != null) {
            translateTitle.setText(getString(toBangla
                    ? R.string.translate_to_bangla : R.string.translate_to_english));
        }
        if (translateResultView != null) {
            translateResultView.setText(R.string.translate_loading);
        }
        Translator.translate(source, toBangla ? "en" : "bn", toBangla ? "bn" : "en",
                (translated, error) -> {
                    if (translated == null) {
                        translateResultText = "";
                        if (translateResultView != null) {
                            translateResultView.setText(R.string.translate_error);
                        }
                        return;
                    }
                    translateResultText = translated;
                    if (translateResultView != null) {
                        translateResultView.setText(translated);
                    }
                });
    }

    private void insertTranslation() {
        if (translateResultText.isEmpty()) {
            return;
        }
        insertText(translateResultText);
        hidePanels();
    }

    private void replaceTranslation() {
        if (translateResultText.isEmpty()) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        recordUndo(false);
        finishWord();
        if (translateFromSelection) {
            ic.commitText(translateResultText, 1);
        } else {
            ExtractedText et = extracted(ic);
            if (et != null && et.text != null && et.text.length() <= 400) {
                ic.setSelection(0, et.text.length());
                ic.commitText(translateResultText, 1);
            } else {
                ic.commitText(translateResultText, 1);
            }
        }
        hidePanels();
        lastSuggestionKey = "";
        updateSuggestions();
    }

    private void copyTranslation() {
        if (translateResultText.isEmpty()) {
            return;
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("Likhon", translateResultText));
        }
        if (clips != null) {
            clips.add(translateResultText);
            lastClipSeen = translateResultText.trim();
        }
        Toast.makeText(this, R.string.clip_copied, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ voice

    private void toggleVoice() {
        VoiceLog.add("mic tapped");
        if (voiceListening) {
            stopVoice(false);
            return;
        }
        String mode = prefs.voiceMode();
        if (Prefs.VOICE_MODE_SCREEN.equals(mode)) {
            VoiceLog.add("voice screen (always)");
            openVoiceScreen();
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
            VoiceLog.add("no recognition service");
            if (Prefs.VOICE_MODE_AUTO.equals(mode)) {
                openVoiceScreen();
                return;
            }
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        finishWord();
        hideCandidates();
        resetVoiceLevel();
        voicePreviewing = false;
        voiceListening = true;
        voiceGotText = false;
        voiceMisses = 0;
        voiceEngineErrors = 0;
        voiceHelpShown = false;
        voiceOnDeviceTried = false;
        voiceLangOverride = null;
        keyboardView.setVoiceActive(true);
        voicePanel.setVisibility(View.VISIBLE);
        voiceStatus.setText(R.string.voice_listening);
        updateVoiceLabels();
        if (voice == null) {
            voice = new VoiceInput(this, this);
        }
        voice.start(voiceLanguage(), prefs.voiceContinuous());
        if (Prefs.VOICE_MODE_AUTO.equals(mode) && uiHandler != null) {
            // On phones whose recognisers ignore the keyboard, give the inline
            // attempt a few seconds and then use the phone's own speech screen.
            uiHandler.removeCallbacks(voiceScreenFallback);
            uiHandler.postDelayed(voiceScreenFallback, 5000);
        }
    }

    /** Opens the phone's speech screen (Google voice) and inserts its result. */
    private void openVoiceScreen() {
        Intent intent = new Intent(this, VoiceScreenActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(VoiceScreenActivity.EXTRA_LANGUAGE, voiceLanguage());
        Toast.makeText(this, R.string.voice_screen_opening, Toast.LENGTH_SHORT).show();
        startActivity(intent);
    }

    /** Puts text recognised by the speech screen into the editor, once. */
    private void flushVoiceScreenText() {
        String text = VoiceScreenBridge.takeText();
        String language = VoiceScreenBridge.takeLanguage();
        if (text == null) {
            return;
        }
        if (language != null && !language.isEmpty()) {
            voiceLangOverride = language;
        }
        String converted = voiceText(text);
        voiceLangOverride = null;
        if (converted.isEmpty()) {
            return;
        }
        recordUndo(false);
        commit(converted + " ");
        VoiceLog.add("screen insert: " + converted);
        learnSentence(converted);
        lastSuggestionKey = "";
        updateSuggestions();
    }

    private void resetVoiceLevel() {
        if (voiceLevel != null && voiceLevelParams != null) {
            voiceLevelParams.width = dp(3);
            voiceLevel.setLayoutParams(voiceLevelParams);
        }
    }

    /** Asks the recogniser to finish what it heard and closes the strip. */
    private void stopVoice(boolean cancel) {
        if (uiHandler != null) {
            uiHandler.removeCallbacks(voiceScreenFallback);
        }
        if (voice != null) {
            if (cancel) {
                voice.cancel();
            } else {
                voice.stop();
            }
        }
        voiceListening = false;
        voicePreviewing = false;
        voiceLangOverride = null;
        resetVoiceLevel();
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
        if (voiceLangOverride != null) {
            return voiceLangOverride;
        }
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
        voiceLangOverride = null;
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
        // Spoken punctuation, as in Gboard's Rambler: "hello comma world".
        text = VoiceText.commands(text, voiceIsBangla());
        if (text.isEmpty()) {
            return "";
        }
        if (prefs.voiceBanglish() && voiceIsBangla()) {
            text = Romanize.toLatin(text).replace('।', '.');
        }
        return text;
    }

    /** Removes the live preview from the editor without leaving text behind. */
    private void clearVoicePreview() {
        if (!voicePreviewing) {
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText("", 1);
            ic.finishComposingText();
        }
        voicePreviewing = false;
    }

    /** Deletes the word before the cursor, including the space after it. */
    private void deleteLastWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        CharSequence before = ic.getTextBeforeCursor(64, 0);
        if (before == null || before.length() == 0) {
            ic.deleteSurroundingText(1, 0);
            return;
        }
        int end = before.length();
        while (end > 0 && Character.isWhitespace(before.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(before.charAt(start - 1))) {
            start--;
        }
        int words = end - start;
        int trailing = before.length() - end;
        if (words <= 0) {
            ic.deleteSurroundingText(1, 0);
        } else {
            ic.deleteSurroundingText(words + trailing, 0);
        }
    }

    private void showVoicePreview(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.setComposingText(text, 1);
            voicePreviewing = true;
            if (text != null && !text.trim().isEmpty()) {
                voiceGotText = true;
            }
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
    public void onVoiceLevel(float level) {
        if (voiceLevel == null || voiceLevelParams == null) {
            return;
        }
        int width = dp(3 + Math.round(level * 42f));
        if (voiceLevelParams.width != width) {
            voiceLevelParams.width = width;
            voiceLevel.setLayoutParams(voiceLevelParams);
        }
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
        if (VoiceText.isDeleteCommand(text)) {
            if (voiceStatus != null) {
                voiceStatus.setText(R.string.voice_deleting);
            }
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
        if (VoiceText.isDeleteCommand(text)) {
            clearVoicePreview();
            deleteLastWord();
            voicePreviewing = false;
            if (voiceStatus != null) {
                voiceStatus.setText(R.string.voice_deleted);
            }
            if (!prefs.voiceContinuous() && voiceListening) {
                stopVoice(false);
            }
            return;
        }
        String converted = voiceText(text);
        if (converted.isEmpty()) {
            return;
        }
        voicePreviewing = false;
        voiceGotText = true;
        recordUndo(false);
        commit(converted + " ");
        VoiceLog.add("insert: " + converted);
        learnSentence(converted);
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
        resetVoiceLevel();
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
    public void onVoiceHint(int code) {
        if (!voiceListening || voiceStatus == null) {
            return;
        }
        if (code == SpeechRecognizer.ERROR_NO_MATCH
                || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            voiceMisses++;
            if (tryBanglaFallback()) {
                return;
            }
            if (tryOnDeviceFallback()) {
                return;
            }
            if (voiceMisses >= 6 && !voiceHelpShown) {
                voiceHelpShown = true;
                Toast.makeText(this, R.string.voice_check_hint, Toast.LENGTH_LONG).show();
            }
        } else {
            voiceMisses = 0;
            if (code != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                voiceEngineErrors++;
            }
        }
        voiceStatus.setText(voiceHintText(code));
    }

    /**
     * Some phones have bn-IN but not bn-BD. After two silent attempts in
     * automatic mode, try the other Bangla model before giving up.
     */
    private boolean tryBanglaFallback() {
        if (voiceLangOverride != null || voice == null || voiceMisses < 2) {
            return false;
        }
        // Silence is not proof that the model is wrong: only switch models
        // once the recogniser actually heard something and still missed it.
        if (!voice.hasHeardSpeech()) {
            return false;
        }
        if (!voiceIsBangla() || !Prefs.VOICE_AUTO.equals(prefs.voiceLang())) {
            return false;
        }
        if (!"bn-BD".equals(voiceLanguage())) {
            return false;
        }
        voiceLangOverride = "bn-IN";
        VoiceLog.add("retry with bn-IN");
        voice.cancel();
        voice.start("bn-IN", prefs.voiceContinuous());
        if (voiceStatus != null) {
            voiceStatus.setText(R.string.voice_trying_bn_in);
        }
        return true;
    }

    /** Android's own offline model, when the phone's speech service stays mute. */
    private boolean tryOnDeviceFallback() {
        if (voiceOnDeviceTried || voice == null) {
            return false;
        }
        boolean heard = voice != null && voice.hasHeardSpeech();
        if (voiceEngineErrors < 2 && (!heard || voiceMisses < 4)) {
            return false;
        }
        if (!VoiceInput.onDeviceAvailable(this)) {
            return false;
        }
        voiceOnDeviceTried = true;
        VoiceLog.add("retry with on-device recogniser");
        voice.preferOnDevice();
        if (voiceStatus != null) {
            voiceStatus.setText(R.string.voice_trying_on_device);
        }
        return true;
    }

    private String voiceHintText(int code) {
        if (code == SpeechRecognizer.ERROR_NO_MATCH
                || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            return getString(R.string.voice_heard_nothing, VoiceInput.errorName(code));
        }
        if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || code == SpeechRecognizer.ERROR_CLIENT) {
            return getString(R.string.voice_retrying);
        }
        return getString(R.string.voice_listening);
    }

    @Override
    public void onVoiceError(int code) {
        voiceListening = false;
        voicePreviewing = false;
        resetVoiceLevel();
        if (keyboardView != null) {
            keyboardView.setVoiceActive(false);
        }
        // Keep the strip on screen with the reason: a panel that vanishes
        // without a word is what made the first inline version look dead.
        if (voicePanel != null) {
            voicePanel.setVisibility(View.VISIBLE);
        }
        VoiceLog.add("stopped: " + VoiceInput.errorName(code));
        if (Prefs.VOICE_MODE_AUTO.equals(prefs.voiceMode()) && !voiceGotText) {
            // The phone's recogniser gave up on the keyboard: use its own
            // speech screen rather than leaving the user with no voice at all.
            VoiceLog.add("inline failed, opening voice screen");
            if (uiHandler != null) {
                uiHandler.removeCallbacks(voiceScreenFallback);
            }
            if (voicePanel != null) {
                voicePanel.setVisibility(View.GONE);
            }
            openVoiceScreen();
            return;
        }
        String message = voiceErrorMessage(code) + " (" + VoiceInput.errorName(code) + ")";
        if (voiceStatus != null) {
            voiceStatus.setText(message);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
        if (Prefs.VOICE_MODE_AUTO.equals(prefs.voiceMode())) {
            VoiceLog.add("unavailable inline, opening voice screen");
            openVoiceScreen();
            return;
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

    /** Every word of a dictated sentence becomes a suggestion for later. */
    private void learnSentence(String text) {
        if (learned == null || isSensitiveField()) {
            return;
        }
        for (String word : text.split("[\\s,.;:!?।]+")) {
            if (!word.isEmpty()) {
                learned.add(word, null);
            }
        }
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
