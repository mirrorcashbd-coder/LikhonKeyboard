package com.likhon.keyboard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Invisible window that opens the phone's own voice-input screen and hands the
 * sentence back to the running keyboard.
 *
 * <p>Some phones refuse to deliver speech to a background service such as an
 * input method, but the same speech service works fine for an activity. This
 * activity is that activity: it starts {@link RecognizerIntent#ACTION_RECOGNIZE_SPEECH},
 * waits for the result, forwards the text, and closes without ever showing a
 * window of its own.
 */
public final class VoiceSheetActivity extends Activity {

    interface Callback {
        /** The recognised sentence, or {@code null} when the person cancelled. */
        void onSheetText(String text);
    }

    private static final int REQUEST_SPEECH = 71;

    private static Callback callback;

    private boolean asked;
    private boolean answered;
    private boolean firstResume = true;

    static void setCallback(Callback value) {
        callback = value;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language());
        speech.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language());
        speech.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_sheet_prompt));
        speech.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speech.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(speech, REQUEST_SPEECH);
            asked = true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
            report(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        // Came back without a result (for example the speech screen was
        // cancelled with Back): do not linger invisibly.
        if (asked && !answered) {
            report(null);
        }
    }

    private String language() {
        Prefs prefs = new Prefs(this);
        String configured = prefs.voiceLang();
        if (Prefs.VOICE_AUTO.equals(configured)) {
            return prefs.bangla() ? "bn-BD" : "en-US";
        }
        return configured;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQUEST_SPEECH) {
            return;
        }
        answered = true;
        String text = null;
        if (result == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                text = matches.get(0);
            }
        }
        report(text);
    }

    private void report(String text) {
        Callback target = callback;
        callback = null;
        if (target != null) {
            target.onSheetText(text);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
