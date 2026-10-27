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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Clipboard history, kept in a plain file inside the app's private directory.
 *
 * <p>Nothing is ever dropped automatically: the list grows for as long as the
 * user keeps copying and only shrinks when an entry (or the whole list) is
 * deleted by hand from the keyboard's clipboard panel. Entries are stored one
 * per line as {@code id<TAB>base64(text)}, so newlines inside a copy survive;
 * a leading {@code p} marks a pinned entry.
 */
final class ClipStore {

    static final class Entry {
        final long id;
        final String text;
        boolean pinned;

        Entry(long id, String text, boolean pinned) {
            this.id = id;
            this.text = text;
            this.pinned = pinned;
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

    /** Pinned first, then newest first. */
    synchronized List<Entry> entries() {
        load();
        List<Entry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.pinned != b.pinned) {
                    return a.pinned ? -1 : 1;
                }
                return Long.compare(b.id, a.id);
            }
        });
        return sorted;
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
        entries.add(new Entry(nextId++, clean, false));
        save();
        return true;
    }

    synchronized void setPinned(long id, boolean pinned) {
        load();
        for (Entry entry : entries) {
            if (entry.id == id) {
                entry.pinned = pinned;
                save();
                return;
            }
        }
    }

    synchronized void setPinnedByText(String text, boolean pinned) {
        if (text == null) {
            return;
        }
        load();
        for (Entry entry : entries) {
            if (entry.text.equals(text)) {
                entry.pinned = pinned;
                save();
                return;
            }
        }
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
                String head = line.substring(0, tab);
                boolean pinned = head.startsWith("p");
                if (pinned) {
                    head = head.substring(1);
                }
                long id;
                try {
                    id = Long.parseLong(head);
                } catch (NumberFormatException e) {
                    continue;
                }
                String text = new String(Base64.decode(line.substring(tab + 1),
                        Base64.NO_WRAP), StandardCharsets.UTF_8);
                if (!text.trim().isEmpty()) {
                    entries.add(new Entry(id, text, pinned));
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
                if (entry.pinned) {
                    writer.write('p');
                }
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
