package com.likhon.keyboard;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * On-device dictation: records 16 kHz audio and decodes it with sherpa-onnx.
 *
 * <p>No audio leaves the phone. The model is downloaded once from
 * {@link VoiceModels} and then opened straight from the app's files directory.
 * Recording, endpointing and decoding all happen off the main thread; only the
 * short callbacks are posted back to it.
 */
final class OfflineVoice {

    interface Listener {
        /** The model is being opened (may take a second or two). */
        void onOfflineLoading();

        /** Microphone is live. */
        void onOfflineReady();

        /** Input level, 0 … 1. */
        void onOfflineLevel(float level);

        /** "Decoding" or another one-line status. */
        void onOfflineStatus(int stringRes);

        /** A finished phrase. */
        void onOfflineResult(String text);

        /** Heard nothing usable; still listening when continuous. */
        void onOfflineHint(int stringRes);

        /** The session ended because of a problem. */
        void onOfflineError(int stringRes, String detail);

        /** No more listening (after stop, or after the last phrase). */
        void onOfflineStopped();
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME = 1600;                 // 100 ms
    private static final int MAX_SECONDS = 30;
    private static final int WAIT_SPEECH_SECONDS = 12;
    /** Below this RMS the frame is silence even in a very quiet room. */
    private static final float ABSOLUTE_FLOOR = 0.0032f;
    /** Speech must be this many times louder than the room's noise floor. */
    private static final float NOISE_FACTOR = 2.2f;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "likhon-offline-voice");
        thread.setDaemon(true);
        return thread;
    });

    private final Object engineLock = new Object();
    private OfflineRecognizer recognizer;
    private String engineModelId = "";
    private String engineLanguage = "";

    private volatile boolean active;
    private volatile boolean recording;
    private volatile boolean stopRequested;
    private volatile int session;
    /** 0 idle, 1 preparing/waiting, 2 recording, 3 decoding. */
    private volatile int phase;

    private final Runnable idleRelease = () -> {
        if (phase == 0) {
            worker.execute(this::releaseEngine);
        }
    };

    private AudioRecord recorder;
    private Thread recordThread;
    private boolean continuous;
    private String language = "bn";

    private final FloatBuf samples = new FloatBuf();
    private int speechStart = -1;
    private int silenceSamples;
    private int voicedFrames;
    private float noiseFloor = 0.0035f;
    private long lastLevelPost;

    OfflineVoice(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** True while a session is open (loading, recording or decoding). */
    boolean isActive() {
        return phase != 0;
    }

    /**
     * Opens the selected model (reusing it when it is already in memory) and
     * starts listening.
     */
    void start(String languageTag, boolean continuousSession) {
        language = shortLanguage(languageTag);
        continuous = continuousSession;
        session++;
        active = true;
        stopRequested = false;
        phase = 1;
        main.removeCallbacks(idleRelease);
        final int token = session;
        main.post(() -> {
            if (token == session && active) {
                listener.onOfflineLoading();
            }
        });
        worker.execute(() -> {
            try {
                ensureEngine();
            } catch (Throwable e) {
                fail(token, R.string.voice_offline_load_failed, e.toString());
                return;
            }
            main.post(() -> {
                if (token != session || !active) {
                    return;
                }
                phase = 1;
                beginRecording();
            });
        });
    }

    /** Switches language mid-session without telling the UI the session ended. */
    void restart(String languageTag, boolean continuousSession) {
        session++;
        active = false;
        stopRequested = true;
        recording = false;
        phase = 0;
        stopRecorder();
        start(languageTag, continuousSession);
    }

    /** Finishes the current phrase and stops after its result. */
    void stop() {
        if (phase == 2) {
            // The recording thread finishes the phrase, decodes it, then stops.
            active = false;
            stopRequested = true;
            return;
        }
        if (phase == 3) {
            // A phrase is already being decoded; its result ends the session.
            active = false;
            return;
        }
        active = false;
        stopSession();
    }

    /** Drops everything immediately. */
    void cancel() {
        active = false;
        stopRequested = true;
        session++;
        phase = 0;
        stopRecorder();
        main.post(listener::onOfflineStopped);
    }

    /** Frees a large model after a few idle minutes; a new session reloads it. */
    void releaseIdle() {
        main.removeCallbacks(idleRelease);
        main.postDelayed(idleRelease, 180000);
    }

    void destroy() {
        main.removeCallbacks(idleRelease);
        cancel();
        // Free the native model after any decode that is already queued.
        worker.execute(this::releaseEngine);
        worker.shutdown();
    }

    // --------------------------------------------------------------- engine

    private void ensureEngine() {
        VoiceModels.Model model = VoiceModels.selected(context);
        if (model == null) {
            throw new IllegalStateException("no installed voice model");
        }
        boolean sameLanguage = model.kind != VoiceModels.KIND_WHISPER
                || language.equals(engineLanguage);
        synchronized (engineLock) {
            if (recognizer != null && model.id.equals(engineModelId) && sameLanguage) {
                return;
            }
        }
        long started = SystemClock.elapsedRealtime();
        OfflineRecognizer built = create(context, model, language);
        VoiceLog.add("offline engine " + model.id + " in "
                + (SystemClock.elapsedRealtime() - started) + " ms");
        synchronized (engineLock) {
            releaseEngineLocked();
            recognizer = built;
            engineModelId = model.id;
            engineLanguage = model.kind == VoiceModels.KIND_WHISPER ? language : "";
        }
    }

    /**
     * Builds the sherpa-onnx recogniser for one model. Shared with the voice
     * check in Settings, so a test exercises exactly the same code path as the
     * keyboard.
     */
    static OfflineRecognizer create(Context context, VoiceModels.Model model, String language) {
        OfflineModelConfig modelConfig = new OfflineModelConfig();
        modelConfig.setTokens(VoiceModels.tokens(context, model));
        modelConfig.setProvider("cpu");
        modelConfig.setNumThreads(threads());
        if (model.kind == VoiceModels.KIND_WHISPER) {
            OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
            whisper.setEncoder(VoiceModels.encoder(context, model));
            whisper.setDecoder(VoiceModels.decoder(context, model));
            whisper.setLanguage(language);
            whisper.setTask("transcribe");
            whisper.setTailPaddings(1000);
            modelConfig.setWhisper(whisper);
            modelConfig.setModelType("whisper");
        } else {
            OfflineTransducerModelConfig transducer = new OfflineTransducerModelConfig();
            transducer.setEncoder(VoiceModels.encoder(context, model));
            transducer.setDecoder(VoiceModels.decoder(context, model));
            transducer.setJoiner(VoiceModels.joiner(context, model));
            modelConfig.setTransducer(transducer);
            modelConfig.setModelType("transducer");
        }

        OfflineRecognizerConfig config = new OfflineRecognizerConfig();
        config.setFeatConfig(new FeatureConfig(SAMPLE_RATE, 80, 0f));
        config.setModelConfig(modelConfig);
        try {
            return new OfflineRecognizer(null, config);
        } catch (Throwable e) {
            VoiceLog.add("offline engine failed: " + e);
            throw e;
        }
    }

    private static int threads() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(4, cores - 1));
    }

    private void releaseEngine() {
        synchronized (engineLock) {
            releaseEngineLocked();
        }
    }

    private void releaseEngineLocked() {
        if (recognizer != null) {
            try {
                recognizer.release();
            } catch (Throwable ignored) {
                // Nothing useful to do while tearing down.
            }
            recognizer = null;
            engineModelId = "";
        }
    }

    private String decode(float[] audio) {
        synchronized (engineLock) {
            OfflineRecognizer engine = recognizer;
            if (engine == null) {
                throw new IllegalStateException("engine released");
            }
            OfflineStream stream = engine.createStream();
            try {
                stream.acceptWaveform(audio, SAMPLE_RATE);
                engine.decode(stream);
                OfflineRecognizerResult result = engine.getResult(stream);
                String text = result == null ? "" : result.getText();
                return text == null ? "" : text.trim();
            } finally {
                stream.release();
            }
        }
    }

    // ------------------------------------------------------------ recording

    private void beginRecording() {
        if (!active || recording) {
            return;
        }
        samples.clear();
        speechStart = -1;
        silenceSamples = 0;
        voicedFrames = 0;
        noiseFloor = 0.0035f;
        stopRequested = false;
        lastLevelPost = 0;

        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            fail(session, R.string.voice_offline_mic_failed, "getMinBufferSize=" + min);
            return;
        }
        int buffer = Math.max(min, FRAME * 2 * 4);
        AudioRecord created = null;
        String source = "MIC";
        try {
            // Raw microphone first: unlike VOICE_RECOGNITION it never applies
            // an OEM noise suppressor that can gate quiet speech to silence,
            // and the model expects unprocessed 16 kHz audio anyway.
            created = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, buffer);
            if (created.getState() != AudioRecord.STATE_INITIALIZED) {
                created.release();
                created = null;
            }
        } catch (Throwable e) {
            VoiceLog.add("mic source failed: " + e);
        }
        if (created == null) {
            source = "VOICE_RECOGNITION";
            try {
                created = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, buffer);
            } catch (Throwable e) {
                fail(session, R.string.voice_offline_mic_failed, e.toString());
                return;
            }
        }
        if (created == null || created.getState() != AudioRecord.STATE_INITIALIZED) {
            if (created != null) {
                created.release();
            }
            fail(session, R.string.voice_offline_mic_failed, "not initialized");
            return;
        }
        recorder = created;
        try {
            recorder.startRecording();
        } catch (Throwable e) {
            stopRecorder();
            fail(session, R.string.voice_offline_mic_failed, e.toString());
            return;
        }
        VoiceLog.add("mic " + source + " @ " + SAMPLE_RATE + " Hz");
        recording = true;
        phase = 2;
        recordThread = new Thread(this::readLoop, "likhon-offline-mic");
        recordThread.setDaemon(true);
        recordThread.start();
        listener.onOfflineReady();
    }

    private void readLoop() {
        short[] buffer = new short[FRAME];
        float[] frame = new float[FRAME];
        int token = session;
        int framesSeen = 0;
        float peak = 0f;
        while (recording && token == session && !stopRequested) {
            int count;
            try {
                count = recorder.read(buffer, 0, buffer.length);
            } catch (Throwable e) {
                VoiceLog.add("offline read failed: " + e);
                break;
            }
            if (count <= 0) {
                if (count == AudioRecord.ERROR_INVALID_OPERATION
                        || count == AudioRecord.ERROR_DEAD_OBJECT
                        || count == AudioRecord.ERROR_BAD_VALUE) {
                    break;
                }
                continue;
            }
            for (int i = 0; i < count; i++) {
                frame[i] = buffer[i] / 32768f;
            }
            samples.add(frame, count);
            float level = rms(frame, count);
            postLevel(token, level);
            framesSeen++;
            peak = Math.max(peak, level);
            if (framesSeen == 10) {
                VoiceLog.add("mic level " + String.format(java.util.Locale.US, "%.4f", peak)
                        + " after 1 s");
            }

            if (speechStart < 0) {
                float threshold = Math.max(ABSOLUTE_FLOOR, noiseFloor * NOISE_FACTOR);
                if (level > threshold) {
                    voicedFrames++;
                } else {
                    voicedFrames = 0;
                    noiseFloor = noiseFloor * 0.94f + level * 0.06f;
                }
                if (voicedFrames >= 2) {
                    speechStart = Math.max(0, samples.size() - (FRAME * 3) - (SAMPLE_RATE / 2));
                    VoiceLog.add("speech start, level "
                            + String.format(java.util.Locale.US, "%.4f", level));
                } else if (samples.size() >= SAMPLE_RATE * WAIT_SPEECH_SECONDS) {
                    recording = false;
                    phase = 1;
                    stopRecorder();
                    VoiceLog.add("no speech in " + WAIT_SPEECH_SECONDS + " s, peak "
                            + String.format(java.util.Locale.US, "%.4f", peak));
                    if (active) {
                        main.post(() -> listener.onOfflineHint(R.string.voice_offline_heard_nothing));
                        if (continuous) {
                            main.postDelayed(() -> {
                                if (active) {
                                    beginRecording();
                                }
                            }, 400);
                        } else {
                            stopSession();
                        }
                    } else {
                        stopSession();
                    }
                    return;
                }
            } else {
                float threshold = Math.max(ABSOLUTE_FLOOR, noiseFloor * NOISE_FACTOR);
                if (level > threshold) {
                    silenceSamples = 0;
                } else {
                    silenceSamples += count;
                    noiseFloor = noiseFloor * 0.99f + level * 0.01f;
                }
                boolean silent = silenceSamples > SAMPLE_RATE * 11 / 10;
                boolean tooLong = samples.size() > SAMPLE_RATE * MAX_SECONDS;
                if (silent || tooLong) {
                    recording = false;
                    stopRecorder();
                    finishUtterance(token);
                    return;
                }
            }
        }
        if (token != session) {
            return;
        }
        if (recording) {
            recording = false;
            stopRecorder();
            if (speechStart >= 0 && samples.size() > SAMPLE_RATE / 5) {
                finishUtterance(token);
            } else if (active) {
                main.post(() -> listener.onOfflineHint(R.string.voice_offline_heard_nothing));
                stopSession();
            } else {
                stopSession();
            }
        }
    }

    private void finishUtterance(int token) {
        if (token != session) {
            return;
        }
        int start = Math.max(0, speechStart - (SAMPLE_RATE / 4));
        final float[] audio = samples.copyFrom(start);
        if (audio.length < SAMPLE_RATE / 8) {
            phase = 1;
            main.post(() -> listener.onOfflineHint(R.string.voice_offline_heard_nothing));
            if (continuous && active) {
                main.postDelayed(() -> {
                    if (active) {
                        beginRecording();
                    }
                }, 350);
            } else {
                stopSession();
            }
            return;
        }
        phase = 3;
        main.post(() -> listener.onOfflineStatus(R.string.voice_offline_decoding));
        worker.execute(() -> {
            String text;
            long started = SystemClock.elapsedRealtime();
            try {
                text = decode(audio);
            } catch (Throwable e) {
                fail(token, R.string.voice_offline_decode_failed, e.toString());
                return;
            }
            VoiceLog.add("decoded " + String.format(java.util.Locale.US, "%.1f",
                    audio.length / (float) SAMPLE_RATE) + " s in "
                    + (SystemClock.elapsedRealtime() - started) + " ms: "
                    + (text.isEmpty() ? "(empty)" : text));
            final String result = text;
            main.post(() -> {
                if (token != session) {
                    return;
                }
                if (result.isEmpty()) {
                    listener.onOfflineHint(R.string.voice_offline_heard_nothing);
                } else {
                    listener.onOfflineResult(result);
                }
                if (continuous && active && !stopRequested) {
                    phase = 1;
                    main.postDelayed(() -> {
                        if (active) {
                            beginRecording();
                        }
                    }, 260);
                } else {
                    stopSession();
                }
            });
        });
    }

    private void postLevel(int token, float level) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastLevelPost < 80) {
            return;
        }
        lastLevelPost = now;
        float shown = Math.min(1f, level * 12f);
        main.post(() -> {
            if (token == session) {
                listener.onOfflineLevel(shown);
            }
        });
    }

    private void stopSession() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::stopSession);
            return;
        }
        active = false;
        recording = false;
        phase = 0;
        stopRecorder();
        listener.onOfflineStopped();
    }

    private void stopRecorder() {
        AudioRecord current = recorder;
        recorder = null;
        if (current != null) {
            try {
                if (current.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    current.stop();
                }
            } catch (Throwable ignored) {
                // Already stopped.
            }
            try {
                current.release();
            } catch (Throwable ignored) {
                // Already released.
            }
        }
    }

    private void fail(int token, int messageRes, String detail) {
        if (token != session) {
            return;
        }
        active = false;
        recording = false;
        phase = 0;
        stopRecorder();
        VoiceLog.add("offline error: " + detail);
        main.post(() -> listener.onOfflineError(messageRes, detail));
    }

    private static float rms(float[] frame, int count) {
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += frame[i] * frame[i];
        }
        return (float) Math.sqrt(sum / Math.max(1, count));
    }

    private static String shortLanguage(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "bn";
        }
        int dash = tag.indexOf('-');
        return dash > 0 ? tag.substring(0, dash) : tag;
    }

    /** Growable float buffer; only touched by the recording thread. */
    private static final class FloatBuf {
        private float[] data = new float[SAMPLE_RATE * 12];
        private int size;

        void clear() {
            size = 0;
        }

        int size() {
            return size;
        }

        void add(float[] frame, int count) {
            if (size + count > data.length) {
                int wanted = Math.max(size + count, data.length + SAMPLE_RATE * 4);
                float[] bigger = new float[wanted];
                System.arraycopy(data, 0, bigger, 0, size);
                data = bigger;
            }
            System.arraycopy(frame, 0, data, size, count);
            size += count;
        }

        float[] copyFrom(int start) {
            int from = Math.max(0, Math.min(start, size));
            int length = size - from;
            if (length <= 0) {
                return new float[0];
            }
            float[] out = new float[length];
            System.arraycopy(data, from, out, 0, length);
            return out;
        }
    }
}
