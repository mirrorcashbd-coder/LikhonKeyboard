package com.likhon.keyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.List;

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
        implements KeyboardView.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    private KeyboardView keyboardView;
    private Prefs prefs;
    private Palette palette;

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
        builtSignature = null;
        applyPreferences();
        refreshLayout();
        return keyboardView;
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
            keyboardView.setRowHeightDp(computeRowHeightDp());
        }
        if (!restarting) {
            shift = autoShiftForNewField() && !bangla;
        }
        refreshLayout();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        finishWord();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (keyboardView != null) {
            applyPreferences();
            keyboardView.setRowHeightDp(computeRowHeightDp());
            builtSignature = null;
            refreshLayout();
        }
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
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
                break;
            case Key.SPACE:
                finishWord();
                commit(" ");
                afterTyped(" ");
                break;
            case Key.DELETE:
                backspace();
                break;
            case Key.ENTER:
                finishWord();
                if (!sendDefaultEditorAction(true)) {
                    commit("\n");
                    afterTyped("\n");
                }
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
                break;
            case Key.MORE:
                page = Layouts.PAGE_MORE;
                refreshLayout();
                break;
            case Key.LETTERS:
                finishWord();
                page = Layouts.PAGE_LETTERS;
                refreshLayout();
                break;
            case Key.EMOJI:
                finishWord();
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
        applyPreferences();
        builtSignature = null;
        refreshLayout();
    }

}
