package com.likhon.keyboard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Invisible hop to the phone's own speech screen.
 *
 * <p>Some phones — the vivo that prompted this — refuse inline recognition to
 * every caller but their own, while the Google voice screen keeps working
 * because it runs inside the Google app. An input method cannot receive an
 * activity result, so this activity starts that screen, hands the recognised
 * text to {@link VoiceScreenBridge} and closes straight away.
 *
 * <p>It draws nothing: the speech screen covers it immediately, and it finishes
 * before the user can see it.
 */
public final class VoiceScreenActivity extends Activity {

    static final String EXTRA_LANGUAGE = "language";

    private static final int REQUEST_SPEECH = 91;

    private boolean launched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        if (savedInstanceState != null) {
            return;
        }
        getWindow().getDecorView().post(this::launch);
    }

    private void launch() {
        String language = getIntent().getStringExtra(EXTRA_LANGUAGE);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (language != null && !language.isEmpty()) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        }
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.voice_screen_prompt));
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        try {
            launched = true;
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.voice_screen_missing, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SPEECH) {
            return;
        }
        // Google sometimes reports RESULT_CANCELED even though text came back.
        if (data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                VoiceScreenBridge.deliver(results.get(0),
                        getIntent().getStringExtra(EXTRA_LANGUAGE));
            }
        }
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (launched && isFinishing()) {
            overridePendingTransition(0, 0);
        }
    }
}
