package com.likhon.keyboard;

import android.content.Context;
import android.os.StatFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * The on-device speech models, and the one-time download that installs them.
 *
 * <p>The engine is sherpa-onnx; the models are the same files the
 * humming-keyboard project uses (NVIDIA Parakeet) plus Whisper for Bangla.
 * Nothing here talks to a server at recognition time: after the download the
 * files live in the app's private storage and are opened with
 * {@code OfflineRecognizer}.
 */
final class VoiceModels {

    static final int KIND_WHISPER = 0;
    static final int KIND_TRANSDUCER = 1;

    static final class Model {
        final String id;
        final String name;
        final String repo;
        final String[] remote;
        final String[] local;
        final long[] sizes;
        final int kind;
        final boolean bangla;
        final int noteRes;
        final boolean recommended;
        final long total;

        Model(String id, String name, String repo, String[] remote, String[] local,
                long[] sizes, int kind, boolean bangla, int noteRes, boolean recommended) {
            this.id = id;
            this.name = name;
            this.repo = repo;
            this.remote = remote;
            this.local = local;
            this.sizes = sizes;
            this.kind = kind;
            this.bangla = bangla;
            this.noteRes = noteRes;
            this.recommended = recommended;
            long sum = 0;
            for (long size : sizes) {
                sum += size;
            }
            this.total = sum;
        }
    }

    /** Whisper models expect these file names in the model directory. */
    private static final List<Model> MODELS = new ArrayList<>();

    static {
        MODELS.add(new Model(
                "whisper-tiny",
                "Whisper Tiny",
                "csukuangfj/sherpa-onnx-whisper-tiny",
                new String[]{"tiny-encoder.int8.onnx", "tiny-decoder.int8.onnx", "tiny-tokens.txt"},
                new String[]{"encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt"},
                new long[]{12937772L, 89855401L, 816730L},
                KIND_WHISPER, true, R.string.vm_note_tiny, false));
        MODELS.add(new Model(
                "whisper-base",
                "Whisper Base",
                "csukuangfj/sherpa-onnx-whisper-base",
                new String[]{"base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"},
                new String[]{"encoder.int8.onnx", "decoder.int8.onnx", "tokens.txt"},
                new long[]{29120534L, 130672026L, 816730L},
                KIND_WHISPER, true, R.string.vm_note_base, true));
        MODELS.add(new Model(
                "parakeet-0.6b",
                "Parakeet 0.6B",
                "csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
                new String[]{"encoder.int8.onnx", "decoder.int8.onnx",
                        "joiner.int8.onnx", "tokens.txt"},
                new String[]{"encoder.int8.onnx", "decoder.int8.onnx",
                        "joiner.int8.onnx", "tokens.txt"},
                new long[]{652184296L, 7257753L, 1739080L, 9384L},
                KIND_TRANSDUCER, false, R.string.vm_note_parakeet, false));
    }

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();

    private VoiceModels() {
    }

    static List<Model> all() {
        return MODELS;
    }

    static Model byId(String id) {
        if (id == null) {
            return null;
        }
        for (Model model : MODELS) {
            if (model.id.equals(id)) {
                return model;
            }
        }
        return null;
    }

    static File dir(Context context, Model model) {
        return new File(new File(context.getFilesDir(), "voice"), model.id);
    }

    static boolean installed(Context context, Model model) {
        File dir = dir(context, model);
        for (int i = 0; i < model.local.length; i++) {
            File file = new File(dir, model.local[i]);
            if (!file.isFile() || file.length() != model.sizes[i]) {
                return false;
            }
        }
        return true;
    }

    /** The chosen model when it is fully downloaded, otherwise the first one that is. */
    static Model selected(Context context) {
        String id = new Prefs(context).voiceModelId();
        Model chosen = byId(id);
        if (chosen != null && installed(context, chosen)) {
            return chosen;
        }
        for (Model model : MODELS) {
            if (installed(context, model)) {
                return model;
            }
        }
        return null;
    }

    static String encoder(Context context, Model model) {
        return new File(dir(context, model), "encoder.int8.onnx").getAbsolutePath();
    }

    static String decoder(Context context, Model model) {
        return new File(dir(context, model), "decoder.int8.onnx").getAbsolutePath();
    }

    static String joiner(Context context, Model model) {
        return new File(dir(context, model), "joiner.int8.onnx").getAbsolutePath();
    }

    static String tokens(Context context, Model model) {
        return new File(dir(context, model), "tokens.txt").getAbsolutePath();
    }

    static long installedBytes(Context context) {
        long total = 0;
        for (Model model : MODELS) {
            if (installed(context, model)) {
                total += model.total;
            }
        }
        return total;
    }

    static void delete(Context context, Model model) {
        deleteTree(dir(context, model));
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ------------------------------------------------------------- downloads

    interface Observer {
        void onVoiceDownloadChanged();
    }

    static final class State {
        String modelId;
        long done;
        long total;
        boolean running;
        boolean cancelled;
        String error;
        int fileIndex;
        int fileCount;
    }

    private static final Object LOCK = new Object();
    private static State current;
    private static volatile Observer observer;

    static void setObserver(Observer value) {
        observer = value;
    }

    private static void notifyObserver() {
        Observer value = observer;
        if (value != null) {
            value.onVoiceDownloadChanged();
        }
    }

    /** A copy of the live download state, or null when nothing is happening. */
    static State state() {
        synchronized (LOCK) {
            if (current == null) {
                return null;
            }
            State copy = new State();
            copy.modelId = current.modelId;
            copy.done = current.done;
            copy.total = current.total;
            copy.running = current.running;
            copy.cancelled = current.cancelled;
            copy.error = current.error;
            copy.fileIndex = current.fileIndex;
            copy.fileCount = current.fileCount;
            return copy;
        }
    }

    static boolean isDownloading(String id) {
        synchronized (LOCK) {
            return current != null && current.running && current.modelId.equals(id);
        }
    }

    /**
     * Starts the one-time download. Returns false when another model is already
     * downloading or there is not enough room; the caller shows the reason.
     */
    static boolean download(Context context, Model model) {
        final Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (current != null && current.running) {
                return false;
            }
            StatFs stat = new StatFs(app.getFilesDir().getAbsolutePath());
            long free = stat.getAvailableBytes();
            if (free < model.total + (40L * 1024 * 1024)) {
                if (current == null) {
                    current = new State();
                }
                current.modelId = model.id;
                current.total = model.total;
                current.running = false;
                current.error = "space";
                notifyObserver();
                return false;
            }
            current = new State();
            current.modelId = model.id;
            current.total = model.total;
            current.running = true;
            current.fileCount = model.local.length;
        }
        notifyObserver();
        Thread thread = new Thread(() -> runDownload(app, model), "voice-model-download");
        thread.setDaemon(true);
        thread.start();
        VoiceLog.add("voice model download: " + model.id);
        return true;
    }

    static void cancel() {
        synchronized (LOCK) {
            if (current != null && current.running) {
                current.cancelled = true;
                current.error = null;
            }
        }
        notifyObserver();
    }

    private static void runDownload(Context context, Model model) {
        File dir = dir(context, model);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try {
            for (int i = 0; i < model.remote.length; i++) {
                synchronized (LOCK) {
                    if (current == null || current.cancelled) {
                        throw new IOException("cancelled");
                    }
                    current.fileIndex = i;
                }
                File target = new File(dir, model.local[i]);
                if (target.isFile() && target.length() == model.sizes[i]) {
                    addDone(model.sizes[i]);
                    continue;
                }
                File part = new File(dir, model.local[i] + ".part");
                downloadFile(model, i, part);
                synchronized (LOCK) {
                    if (current == null || current.cancelled) {
                        throw new IOException("cancelled");
                    }
                }
                if (part.length() != model.sizes[i]) {
                    //noinspection ResultOfMethodCallIgnored
                    part.delete();
                    throw new IOException("size " + part.length() + " != " + model.sizes[i]);
                }
                if (target.exists() && !target.delete()) {
                    throw new IOException("cannot replace " + target);
                }
                if (!part.renameTo(target)) {
                    throw new IOException("cannot rename " + target);
                }
            }
            if (!installed(context, model)) {
                throw new IOException("incomplete");
            }
            Prefs prefs = new Prefs(context);
            Model chosen = byId(prefs.voiceModelId());
            if (chosen == null || !installed(context, chosen)) {
                // First model, or the selected one was removed: use this one.
                prefs.setVoiceModelId(model.id);
            }
            synchronized (LOCK) {
                if (current != null) {
                    current.running = false;
                    current.done = model.total;
                    current.error = null;
                }
            }
            VoiceLog.add("voice model ready: " + model.id);
        } catch (Exception e) {
            synchronized (LOCK) {
                if (current != null) {
                    current.running = false;
                    boolean cancelled = current.cancelled;
                    current.error = cancelled ? null : describe(e);
                }
            }
            VoiceLog.add("voice model failed: " + e);
            // A half-written file would look installed by size but fail to load.
            File[] leftovers = dir.listFiles();
            if (leftovers != null) {
                for (File file : leftovers) {
                    if (file.getName().endsWith(".part")) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
            }
        }
        notifyObserver();
    }

    private static void addDone(long bytes) {
        synchronized (LOCK) {
            if (current != null) {
                current.done += bytes;
            }
        }
        notifyObserver();
    }

    private static void downloadFile(Model model, int index, File part) throws IOException {
        String url = "https://huggingface.co/" + model.repo + "/resolve/main/" + model.remote[index];
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "LikhonKeyboard/2.2")
                .build();
        long lastPublish = 0;
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("empty body");
            }
            long length = body.contentLength();
            if (length > 0 && length != model.sizes[index]) {
                throw new IOException("server size " + length + " != " + model.sizes[index]);
            }
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (LOCK) {
                        if (current == null || current.cancelled) {
                            throw new IOException("cancelled");
                        }
                    }
                    out.write(buffer, 0, read);
                    synchronized (LOCK) {
                        if (current != null) {
                            current.done += read;
                        }
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastPublish > 220) {
                        lastPublish = now;
                        notifyObserver();
                    }
                }
                out.flush();
                out.getFD().sync();
            }
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }

    /** "104 MB" / "1.1 GB", the way a download screen shows it. */
    static String size(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f GB",
                    bytes / (1024.0 * 1024.0 * 1024.0));
        }
        long mb = Math.max(1, Math.round(bytes / (1024.0 * 1024.0)));
        return mb + " MB";
    }
}
