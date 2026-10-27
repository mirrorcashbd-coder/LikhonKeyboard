package com.likhon.keyboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;

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
    /**
     * Recognisers to try, in order. A null entry means "whatever the phone
     * picked as its default", which is tried first unless one is pinned in
     * settings.
     */
    private final List<ComponentName> engines = new ArrayList<>();
    private int engineIndex;
    private int cycles;
    private int consecutiveFailures;
    private boolean heardSpeech;
    private boolean alternateLanguageTried;
    /** Per-engine signals: did this engine hear a voice or receive any audio? */
    private boolean engineHeard;
    private boolean engineAudio;
    private int engineMisses;
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

    /** True once the current session actually heard a voice. */
    boolean hasHeardSpeech() {
        return heardSpeech;
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

    /**
     * Whether it is worth asking for Android's on-device recogniser. The
     * system's own check is optimistic on some phones (it says no while a
     * service is there), so on Android 12+ it is simply tried: if the phone
     * has none, creating it throws and the next engine is used.
     */
    static boolean onDeviceAvailable(Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /**
     * Starts a session. When {@code continuous} is true the recogniser restarts
     * after each phrase until {@link #stop()} is called.
     */
    void start(String languageTag, boolean continuous) {
        this.language = languageTag == null || languageTag.isEmpty() ? "bn-BD" : languageTag;
        this.continuous = continuous;
        this.onDevice = false;
        this.consecutiveFailures = 0;
        this.cycles = 0;
        this.engineIndex = 0;
        this.heardSpeech = false;
        this.alternateLanguageTried = false;
        this.engineHeard = false;
        this.engineAudio = false;
        this.engineMisses = 0;
        buildEngines();
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
                    ComponentName engine = engineIndex >= 0 && engineIndex < engines.size()
                            ? engines.get(engineIndex) : null;
                    if (engine == null) {
                        recognizer = SpeechRecognizer.createSpeechRecognizer(
                                context.getApplicationContext());
                    } else {
                        recognizer = SpeechRecognizer.createSpeechRecognizer(
                                context.getApplicationContext(), engine);
                    }
                }
                recognizer.setRecognitionListener(this);
            } catch (RuntimeException e) {
                recognizer = null;
                onDevice = false;
                VoiceLog.add("engine create failed: " + e);
                if (nextEngine()) {
                    handler.postDelayed(restart, RESTART_DELAY_MS);
                    return;
                }
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

    /**
     * Lists the recognisers installed on the phone, the phone's own default
     * first and a pinned engine (settings) before everything else.
     */
    private void buildEngines() {
        engines.clear();
        String pinned = new Prefs(context).voiceEngine();
        if (pinned != null && !pinned.isEmpty()) {
            ComponentName chosen = ComponentName.unflattenFromString(pinned);
            if (chosen != null) {
                engines.add(chosen);
                VoiceLog.add("engine pinned: " + chosen.flattenToShortString());
            }
        }
        boolean google = addIfInstalled("com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService");
        if (engines.isEmpty() && !google) {
            // Null is the phone's current default recogniser.
            engines.add(null);
        }
        String defaultName = android.provider.Settings.Secure.getString(
                context.getContentResolver(), "voice_recognition_service");
        VoiceLog.add("default engine: " + (defaultName == null ? "(none)" : defaultName));
        for (ComponentName name : availableEngines(context)) {
            VoiceLog.add("engine found: " + name.flattenToShortString()
                    + (name.flattenToString().equals(defaultName) ? " (default)" : ""));
            if (!engines.contains(name)) {
                engines.add(name);
            }
        }
    }

    private boolean addIfInstalled(String pkg, String cls) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
        } catch (Exception e) {
            return false;
        }
        ComponentName name = new ComponentName(pkg, cls);
        if (!engines.contains(name)) {
            engines.add(name);
            VoiceLog.add("engine added: " + name.flattenToShortString());
        }
        return true;
    }

    /** Every recognition service the phone exposes to this keyboard. */
    static List<ComponentName> availableEngines(Context context) {
        List<ComponentName> found = new ArrayList<>();
        try {
            List<ResolveInfo> list = context.getPackageManager().queryIntentServices(
                    new Intent(android.speech.RecognitionService.SERVICE_INTERFACE), 0);
            if (list != null) {
                for (ResolveInfo info : list) {
                    if (info.serviceInfo != null) {
                        found.add(new ComponentName(
                                info.serviceInfo.packageName, info.serviceInfo.name));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A locked-down package manager simply means "no engines".
        }
        return found;
    }

    /**
     * Moves to the next recogniser. The list is walked twice before giving up,
     * so a service that was briefly busy gets a second chance but a phone with
     * no working engine does not spin forever.
     */
    private boolean nextEngine() {
        if (engines.isEmpty()) {
            return false;
        }
        if (engineIndex + 1 < engines.size()) {
            engineIndex++;
        } else if (cycles < 1) {
            cycles++;
            engineIndex = 0;
        } else {
            return false;
        }
        engineHeard = false;
        engineAudio = false;
        engineMisses = 0;
        ComponentName name = engines.get(engineIndex);
        VoiceLog.add("engine -> " + (name == null ? "default" : name.flattenToShortString()));
        return true;
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
        heardSpeech = true;
        engineHeard = true;
        consecutiveFailures = 0;
        VoiceLog.add("speech started");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (rmsdB > -1f) {
            engineAudio = true;
        }
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
        // The engine does not know this language: try the other Bangla tag on
        // the same engine before blaming the engine itself.
        if ((error == 12 || error == 13) && language.startsWith("bn")
                && !alternateLanguageTried) {
            alternateLanguageTried = true;
            language = "bn-BD".equals(language) ? "bn-IN" : "bn-BD";
            VoiceLog.add("language -> " + language);
            destroyRecognizer();
            listener.onVoiceHint(error);
            handler.postDelayed(restart, RESTART_DELAY_MS);
            return;
        }
        boolean recoverable = error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS;
        boolean engineProblem = error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_AUDIO
                || error == SpeechRecognizer.ERROR_SERVER
                || error == SpeechRecognizer.ERROR_NETWORK
                || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED;
        if (error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            engineMisses++;
            // An engine that never reports a voice or even a sound level is
            // not receiving audio at all (some phones bind a stub): move on.
            if (engineMisses == 6 && !engineHeard && !engineAudio
                    && !new Prefs(context).voiceEngine().isEmpty()) {
                // A pinned engine that never even hears a sound is a dead end:
                // forget the pin so the phone's own order is used again.
                new Prefs(context).setVoiceEngine("");
                VoiceLog.add("unpinned silent engine");
                buildEngines();
                engineIndex = 0;
                destroyRecognizer();
                listener.onVoiceHint(error);
                handler.postDelayed(restart, RESTART_DELAY_MS);
                return;
            }
            if (engineMisses >= 6 && !engineHeard && !engineAudio && nextEngine()) {
                destroyRecognizer();
                listener.onVoiceHint(error);
                handler.postDelayed(restart, RESTART_DELAY_MS);
                return;
            }
        }
        if (recoverable) {
            if (engineProblem) {
                consecutiveFailures++;
                if (consecutiveFailures >= 2) {
                    consecutiveFailures = 0;
                    destroyRecognizer();
                    if (nextEngine()) {
                        listener.onVoiceHint(error);
                        handler.postDelayed(restart, RESTART_DELAY_MS);
                        return;
                    }
                    // Every engine on the phone refused: say so instead of
                    // retrying a dead microphone for ever.
                    wantListening = false;
                    VoiceLog.add("no engine answered, stopping");
                    listener.onVoiceError(error);
                    return;
                }
            }
            listener.onVoiceHint(error);
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    || error == SpeechRecognizer.ERROR_CLIENT) {
                // These leave the instance unusable on several phones.
                destroyRecognizer();
            }
            // Google's service rate-limits rapid restarts; give it room.
            long delay = error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
                    ? 3500 : RESTART_DELAY_MS;
            if (continuous) {
                handler.postDelayed(restart, delay);
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
        consecutiveFailures = 0;
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
