package com.likhon.keyboard;

import android.content.Context;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Clipboard history, kept in a plain file inside the app's private directory.
 *
 * <p>Nothing is ever dropped automatically: the list grows for as long as the
 * user keeps copying and only shrinks when an entry (or the whole list) is
 * deleted by hand from the keyboard's clipboard panel. Entries are stored one
 * per line as {@code id<TAB>base64(text)}, so newlines inside a copy survive.
 */
final class ClipStore {

    static final class Entry {
        final long id;
        final String text;

        Entry(long id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    private static final String FILE_NAME = "clipboard.txt";

    private final File file;
    private final List<Entry> entries = new ArrayList<>();
    private boolean loaded;
    private long nextId = 1;

    ClipStore(Context context) {
        file = new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    /** Newest first. */
    synchronized List<Entry> entries() {
        load();
        return new ArrayList<>(entries);
    }

    synchronized int size() {
        load();
        return entries.size();
    }

    /** Stores a copy; identical text is moved to the top instead of duplicated. */
    synchronized boolean add(String text) {
        if (text == null) {
            return false;
        }
        String clean = text.replace("\r\n", "\n").trim();
        if (clean.isEmpty()) {
            return false;
        }
        load();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).text.equals(clean)) {
                entries.remove(i);
                break;
            }
        }
        entries.add(0, new Entry(nextId++, clean));
        save();
        return true;
    }

    synchronized void remove(long id) {
        load();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id == id) {
                entries.remove(i);
                save();
                return;
            }
        }
    }

    synchronized void clear() {
        load();
        entries.clear();
        save();
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                long id;
                try {
                    id = Long.parseLong(line.substring(0, tab));
                } catch (NumberFormatException e) {
                    continue;
                }
                String text = new String(Base64.decode(line.substring(tab + 1),
                        Base64.NO_WRAP), StandardCharsets.UTF_8);
                if (!text.trim().isEmpty()) {
                    entries.add(new Entry(id, text));
                    if (id >= nextId) {
                        nextId = id + 1;
                    }
                }
            }
        } catch (Exception ignored) {
            // A corrupt history must never stop the keyboard from starting.
        }
    }

    private void save() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                writer.write(Long.toString(entry.id));
                writer.write('\t');
                writer.write(Base64.encodeToString(
                        entry.text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                writer.write('\n');
            }
        } catch (Exception ignored) {
            // Same: losing the history is better than crashing the IME.
        }
    }
}
