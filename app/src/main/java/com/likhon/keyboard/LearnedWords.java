package com.likhon.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Words the person actually types, remembered across restarts.
 *
 * <p>Everything committed as a word (typing a space, punctuation, picking a
 * suggestion, dictating) lands here if it is not already known. Writing is
 * delayed by a second so a burst of words only touches the disk once; the
 * in-memory part lives in {@link LearnedIndex}, which is also tested without
 * Android by {@code tools/LearnedCheck.java}.
 */
final class LearnedWords {

    private static final int SAVE_DELAY_MS = 900;

    private final File file;
    private final LearnedIndex index = new LearnedIndex();
    private final Handler handler;
    private final Runnable saveTask;

    LearnedWords(Context context) {
        file = new File(context.getFilesDir(), "learned_words.txt");
        handler = new Handler(Looper.getMainLooper());
        saveTask = new Runnable() {
            @Override
            public void run() {
                save();
            }
        };
        load();
    }

    /**
     * Remembers a finished word.
     *
     * @param word    the word as it appears in the editor (Bangla or Latin)
     * @param keyHint the roman letters that produced it, when known
     */
    void add(String word, String keyHint) {
        index.add(word, keyHint);
        scheduleSave();
    }

    List<String> suggest(String prefix, boolean bangla, int max) {
        return index.suggest(prefix, bangla, max);
    }

    /** Forgets a word (long-press on its suggestion chip). */
    boolean remove(String word) {
        boolean removed = index.remove(word);
        if (removed) {
            save();
        }
        return removed;
    }

    int size() {
        return index.size();
    }

    /** Forgets every learned word and rewrites the file. */
    void clear() {
        index.clear();
        save();
    }

    /** Writes pending changes now (called when the keyboard closes). */
    void flush() {
        handler.removeCallbacks(saveTask);
        save();
    }

    // ------------------------------------------------------------- persistence

    private void scheduleSave() {
        handler.removeCallbacks(saveTask);
        handler.postDelayed(saveTask, SAVE_DELAY_MS);
    }

    private void save() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (String line : index.toLines()) {
                writer.write(line);
                writer.write('\n');
            }
        } catch (IOException e) {
            // Losing the learned list is harmless; typing still works.
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            return;
        }
        index.fromLines(lines);
    }
}
