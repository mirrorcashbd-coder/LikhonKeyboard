package com.likhon.keyboard;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * Thin, restartable wrapper around {@link SpeechRecognizer}.
 *
 * <p>The recogniser itself is provided by the phone (usually the Google app),
 * so recognition works in Bangla and English without this app talking to the
 * network. A session is kept alive until {@link #stop()} / {@link #cancel()}:
 * after every final result it starts listening again, which is what makes
 * continuous dictation possible.
 */
final class VoiceInput implements RecognitionListener {

    interface Listener {
        /** The microphone is live and the recogniser is waiting for speech. */
        void onVoiceListening();

        /** Unstable preview of what is being said. */
        void onVoicePartial(String text);

        /** Microphone level, 0 (silence) … 1 (loud), for the level bar. */
        void onVoiceLevel(float level);

        /** A finished phrase, ready to be inserted. */
        void onVoiceResult(String text);

        /** A recoverable problem (nothing heard, service busy); still listening. */
        void onVoiceHint(int code);

        /** All listening has stopped (after stop, or after a fatal error). */
        void onVoiceStopped();

        /** Something went wrong; {@code code} is a SpeechRecognizer ERROR_*. */
        void onVoiceError(int code);

        /** No speech service is installed on this phone. */
        void onVoiceUnavailable();
    }

    private static final long RESTART_DELAY_MS = 320;
    private static final long RESULT_RESTART_MS = 140;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private int startFailures;
    private boolean onDevice;
    private boolean wantListening;
    private boolean continuous;
    private String language = "bn-BD";
    private String lastPartial = "";

    private final Runnable restart = new Runnable() {
        @Override
        public void run() {
            if (wantListening) {
                begin();
            }
        }
    };

    VoiceInput(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    boolean available() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    boolean isListening() {
        return wantListening;
    }

    boolean isOnDevice() {
        return onDevice;
    }

    /**
     * Switches the running session to Android's on-device recogniser. Used as a
     * last resort when the phone's normal speech service never answers.
     */
    void preferOnDevice() {
        if (onDevice) {
            return;
        }
        onDevice = true;
        VoiceLog.add("switch to on-device recogniser");
        destroyRecognizer();
        handler.removeCallbacks(restart);
        begin();
    }

    static boolean onDeviceAvailable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        try {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Starts a session. When {@code continuous} is true the recogniser restarts
     * after each phrase until {@link #stop()} is called.
     */
    void start(String languageTag, boolean continuous) {
        this.language = languageTag == null || languageTag.isEmpty() ? "bn-BD" : languageTag;
        this.continuous = continuous;
        this.onDevice = false;
        this.wantListening = true;
        VoiceLog.add("start " + this.language + (continuous ? ", continuous" : ""));
        this.lastPartial = "";
        if (!available()) {
            wantListening = false;
            listener.onVoiceUnavailable();
            return;
        }
        begin();
    }

    private void begin() {
        handler.removeCallbacks(restart);
        if (!wantListening) {
            return;
        }
        if (recognizer == null) {
            try {
                if (onDevice && onDeviceAvailable(context)) {
                    recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(
                            context.getApplicationContext());
                } else {
                    onDevice = false;
                    recognizer = SpeechRecognizer.createSpeechRecognizer(
                            context.getApplicationContext());
                }
                recognizer.setRecognitionListener(this);
            } catch (RuntimeException e) {
                recognizer = null;
                wantListening = false;
                listener.onVoiceUnavailable();
                return;
            }
        }
        try {
            recognizer.startListening(intent());
            startFailures = 0;
            VoiceLog.add("listening");
            listener.onVoiceListening();
        } catch (RuntimeException e) {
            // Busy, unbound, or the service died underneath us.
            startFailures++;
            VoiceLog.add("start failed: " + e);
            destroyRecognizer();
            if (startFailures >= 3) {
                // Never spin forever behind a microphone that records nothing:
                // surface the failure instead.
                wantListening = false;
                VoiceLog.add("gave up after " + startFailures + " start failures");
                listener.onVoiceError(SpeechRecognizer.ERROR_CLIENT);
                listener.onVoiceStopped();
                return;
            }
            handler.postDelayed(restart, RESTART_DELAY_MS);
        }
    }

    private Intent intent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        // Shorten the end-of-phrase wait; the defaults leave the strip silent
        // for seconds after a sentence and look like a frozen microphone.
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1200);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                900);
        return intent;
    }

    /** Asks the recogniser to finish the current phrase, then stop. */
    void stop() {
        wantListening = false;
        handler.removeCallbacks(restart);
        if (recognizer != null) {
            try {
                recognizer.stopListening();
            } catch (RuntimeException ignored) {
                // Already gone; nothing to finalise.
            }
        }
        lastPartial = "";
    }

    /** Stops immediately and throws away the current phrase. */
    void cancel() {
        wantListening = false;
        handler.removeCallbacks(restart);
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (RuntimeException ignored) {
                // Nothing to cancel.
            }
        }
        lastPartial = "";
    }

    /** Releases the recogniser; call from onDestroy / when the keyboard hides. */
    void destroy() {
        cancel();
        destroyRecognizer();
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (RuntimeException ignored) {
                // Some services throw when they are already disconnected.
            }
            recognizer = null;
        }
    }

    // ------------------------------------------------------- RecognitionListener

    @Override
    public void onReadyForSpeech(Bundle params) {
        VoiceLog.add("ready");
        if (wantListening) {
            listener.onVoiceListening();
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        VoiceLog.add("speech started");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        float level = (rmsdB + 2f) / 12f;
        if (level < 0f) {
            level = 0f;
        }
        if (level > 1f) {
            level = 1f;
        }
        listener.onVoiceLevel(level);
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        VoiceLog.add("speech ended");
    }

    @Override
    public void onError(int error) {
        if (!wantListening) {
            // Cancelled or stopped by us; the error is just the shutdown.
            return;
        }
        VoiceLog.add("error " + errorName(error) + (onDevice ? " (on-device)" : ""));
        if (onDevice && (error == 12 || error == 13)) {
            // The on-device model has no such language; go back to the phone's
            // normal service instead of stopping.
            onDevice = false;
            destroyRecognizer();
            listener.onVoiceHint(error);
            if (continuous) {
                handler.postDelayed(restart, RESTART_DELAY_MS);
                return;
            }
        }
        boolean recoverable = error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_CLIENT;
        if (recoverable) {
            listener.onVoiceHint(error);
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    || error == SpeechRecognizer.ERROR_CLIENT) {
                // These leave the instance unusable on several phones.
                destroyRecognizer();
            }
            if (continuous) {
                handler.postDelayed(restart, RESTART_DELAY_MS);
                return;
            }
            listener.onVoiceStopped();
            return;
        }
        wantListening = false;
        listener.onVoiceError(error);
        listener.onVoiceStopped();
    }

    @Override
    public void onResults(Bundle results) {
        String text = best(results);
        VoiceLog.add("result: " + (text.isEmpty() ? "(empty)" : shorten(text)));
        if (!text.isEmpty()) {
            lastPartial = "";
            listener.onVoiceResult(text);
        } else {
            listener.onVoiceHint(SpeechRecognizer.ERROR_NO_MATCH);
        }
        if (wantListening && continuous) {
            handler.postDelayed(restart, RESULT_RESTART_MS);
            listener.onVoiceListening();
        } else if (wantListening) {
            wantListening = false;
            listener.onVoiceStopped();
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        String text = best(partialResults);
        if (!text.isEmpty() && !text.equals(lastPartial)) {
            lastPartial = text;
            listener.onVoicePartial(text);
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }

    static String errorName(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "NETWORK_TIMEOUT";
            case SpeechRecognizer.ERROR_NETWORK:
                return "NETWORK";
            case SpeechRecognizer.ERROR_AUDIO:
                return "AUDIO";
            case SpeechRecognizer.ERROR_SERVER:
                return "SERVER";
            case SpeechRecognizer.ERROR_CLIENT:
                return "CLIENT";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "SPEECH_TIMEOUT";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "NO_MATCH";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "BUSY";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "PERMISSIONS";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS:
                return "TOO_MANY_REQUESTS";
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED:
                return "SERVER_DISCONNECTED";
            case 12:
                return "LANGUAGE_NOT_SUPPORTED";
            case 13:
                return "LANGUAGE_UNAVAILABLE";
            default:
                return "ERROR_" + error;
        }
    }

    private static String shorten(String text) {
        return text.length() <= 70 ? text : text.substring(0, 70) + "…";
    }

    private String best(Bundle bundle) {
        if (bundle == null) {
            return "";
        }
        ArrayList<String> matches = bundle.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return "";
        }
        String text = matches.get(0);
        return text == null ? "" : text.trim();
    }
}
