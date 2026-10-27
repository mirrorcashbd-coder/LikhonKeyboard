package com.likhon.keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure in-memory logic for {@link LearnedWords}: no Android classes, so the
 * same code can be checked from a plain JDK ({@code tools/LearnedCheck.java}).
 *
 * <p>An entry remembers the word, the letters that produced it (for Bangla the
 * roman typing, for English the word itself lowercased) and how often it was
 * used. The list is kept most-recent-first; {@link #suggest} sorts matches by
 * use count so a frequent word beats a one-off.
 */
final class LearnedIndex {

    static final int MAX_ENTRIES = 240;

    private final List<Entry> entries = new ArrayList<>();

    void add(String word, String keyHint) {
        String clean = cleanWord(word);
        if (clean == null) {
            return;
        }
        boolean bangla = isBangla(clean);
        String key = bangla
                ? normaliseKey(keyHint != null ? keyHint : Romanize.toLatin(clean))
                : clean.toLowerCase(Locale.ROOT);
        if (key.length() < 2) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.word.equalsIgnoreCase(clean)) {
                entry.count++;
                entry.key = key;
                entries.remove(i);
                entries.add(0, entry);
                return;
            }
        }
        entries.add(0, new Entry(clean, key, 1));
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    /** Suggestions for a prefix; an empty prefix gives recently used words. */
    List<String> suggest(String prefix, boolean bangla, int max) {
        List<String> out = new ArrayList<>();
        if (prefix != null && !prefix.isEmpty()) {
            String wanted = prefix.toLowerCase(Locale.ROOT);
            List<Entry> matches = new ArrayList<>();
            for (Entry entry : entries) {
                if (isBangla(entry.word) == bangla && entry.key.startsWith(wanted)) {
                    matches.add(entry);
                }
            }
            Collections.sort(matches, new Comparator<Entry>() {
                @Override
                public int compare(Entry a, Entry b) {
                    return Integer.compare(b.count, a.count);
                }
            });
            for (Entry entry : matches) {
                if (!out.contains(entry.word)) {
                    out.add(entry.word);
                    if (out.size() >= max) {
                        break;
                    }
                }
            }
            return out;
        }
        for (Entry entry : entries) {
            if (isBangla(entry.word) == bangla && !out.contains(entry.word)) {
                out.add(entry.word);
                if (out.size() >= max) {
                    break;
                }
            }
        }
        return out;
    }

    boolean remove(String word) {
        if (word == null) {
            return false;
        }
        boolean removed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).word.equalsIgnoreCase(word)) {
                entries.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    int size() {
        return entries.size();
    }

    /** Forgets everything; used from the settings screen. */
    void clear() {
        entries.clear();
    }

    /** Most recent first: {@code key \t word \t count}. */
    List<String> toLines() {
        List<String> lines = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            lines.add(entry.key + "\t" + entry.word + "\t" + entry.count);
        }
        return lines;
    }

    void fromLines(List<String> lines) {
        entries.clear();
        for (String line : lines) {
            if (entries.size() >= MAX_ENTRIES) {
                break;
            }
            String[] parts = line.split("\t");
            if (parts.length < 3) {
                continue;
            }
            String word = cleanWord(parts[1]);
            String key = normaliseKey(parts[0]);
            if (word == null || key.length() < 2) {
                continue;
            }
            int count;
            try {
                count = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            entries.add(new Entry(word, key, count));
        }
    }

    // ----------------------------------------------------------------- helpers

    static boolean isBangla(String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c >= '\u0980' && c <= '\u09FF') {
                return true;
            }
        }
        return false;
    }

    /** Letters only, 2–28 characters, no digits or mixed scripts; else null. */
    static String cleanWord(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        int start = 0;
        int end = text.length();
        while (start < end && !Character.isLetter(text.charAt(start))) {
            start++;
        }
        while (end > start && !Character.isLetter(text.charAt(end - 1))) {
            end--;
        }
        text = text.substring(start, end);
        if (text.length() < 2 || text.length() > 28) {
            return null;
        }
        boolean bangla = false;
        boolean latin = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0980' && c <= '\u09FF') {
                bangla = true;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '\'') {
                latin = true;
            } else {
                return null;
            }
        }
        if (bangla && latin) {
            return null;
        }
        return text;
    }

    private static String normaliseKey(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        String lower = raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final class Entry {
        final String word;
        String key;
        int count;

        Entry(String word, String key, int count) {
            this.word = word;
            this.key = key;
            this.count = count;
        }
    }
}
