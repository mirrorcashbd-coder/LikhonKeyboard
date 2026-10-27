package com.likhon.keyboard;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prefix suggestions for the two typing modes.
 *
 * <p>English uses a frequency-ordered list of the 10,000 most common words.
 * Bangla typing is phonetic, so the useful key is the <em>roman</em> form:
 * <pre>
 *   typed "bhal"  →  ভালো, ভালোবাসা, ভালোবাসি …
 * </pre>
 * The index in {@code assets/words_bn_index.txt} stores exactly that
 * (roman, Bangla, rank) and is built by {@code tools/SuggestionIndex.java}.
 *
 * <p>Lists are read once, lazily, on the keyboard's main thread and then kept in
 * memory (about 2 MB in total).
 */
final class Suggestions {

    private static final int MAX_RESULTS = 18;
    private static final int SCAN_LIMIT = 600;

    private final Context context;

    private String[] englishSorted;
    private Map<String, Integer> englishRank;

    private String[] banglaRoman;
    private String[] banglaWords;
    private int[] banglaRank;

    Suggestions(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Loads both lists; call once from a posted task after the keyboard shows. */
    void preload() {
        ensureEnglish();
        ensureBangla();
    }

    List<String> english(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }
        ensureEnglish();
        String key = prefix.toLowerCase(Locale.ROOT);
        int start = lowerBound(englishSorted, key);
        List<Hit> hits = new ArrayList<>();
        for (int i = start; i < englishSorted.length && englishSorted[i].startsWith(key); i++) {
            String word = englishSorted[i];
            if (word.equals(key)) {
                continue; // already typed in full
            }
            Integer rank = englishRank.get(word);
            hits.add(new Hit(word, rank == null ? Integer.MAX_VALUE : rank));
            if (hits.size() >= SCAN_LIMIT) {
                break;
            }
        }
        return top(hits, MAX_RESULTS);
    }

    List<String> bangla(String romanPrefix) {
        if (romanPrefix == null || romanPrefix.length() < 2) {
            return Collections.emptyList();
        }
        ensureBangla();
        String key = romanPrefix.toLowerCase(Locale.ROOT);
        int start = lowerBound(banglaRoman, key);
        List<Hit> hits = new ArrayList<>();
        for (int i = start; i < banglaRoman.length && banglaRoman[i].startsWith(key); i++) {
            if (banglaRoman[i].equals(key)) {
                continue; // the composing region already shows this word
            }
            hits.add(new Hit(banglaWords[i], banglaRank[i]));
            if (hits.size() >= SCAN_LIMIT) {
                break;
            }
        }
        return top(hits, MAX_RESULTS);
    }

    private List<String> top(List<Hit> hits, int max) {
        if (hits.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.sort(hits, new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                return Integer.compare(a.rank, b.rank);
            }
        });
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (Hit hit : hits) {
            unique.add(hit.word);
            if (unique.size() >= max) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private static int lowerBound(String[] values, String key) {
        int lo = 0;
        int hi = values.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (values[mid].compareTo(key) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // ------------------------------------------------------------------ loading

    private void ensureEnglish() {
        if (englishSorted != null) {
            return;
        }
        List<String> words = readLines("words_en.txt");
        englishSorted = words.toArray(new String[0]);
        Arrays.sort(englishSorted);
        Map<String, Integer> ranks = new HashMap<>(words.size() * 2);
        for (int i = 0; i < words.size(); i++) {
            if (!ranks.containsKey(words.get(i))) {
                ranks.put(words.get(i), i);
            }
        }
        englishRank = ranks;
    }

    private void ensureBangla() {
        if (banglaRoman != null) {
            return;
        }
        List<String> lines = readLines("words_bn_index.txt");
        int size = lines.size();
        banglaRoman = new String[size];
        banglaWords = new String[size];
        banglaRank = new int[size];
        for (int i = 0; i < size; i++) {
            String line = lines.get(i);
            int tab1 = line.indexOf('\t');
            int tab2 = tab1 < 0 ? -1 : line.indexOf('\t', tab1 + 1);
            if (tab1 <= 0 || tab2 < 0) {
                banglaRoman[i] = "";
                banglaWords[i] = "";
                banglaRank[i] = Integer.MAX_VALUE;
                continue;
            }
            banglaRoman[i] = line.substring(0, tab1);
            banglaWords[i] = line.substring(tab1 + 1, tab2);
            try {
                banglaRank[i] = Integer.parseInt(line.substring(tab2 + 1));
            } catch (NumberFormatException e) {
                banglaRank[i] = Integer.MAX_VALUE;
            }
        }
    }

    private List<String> readLines(String asset) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = context.getAssets().open(asset);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (!value.isEmpty()) {
                    lines.add(value);
                }
            }
        } catch (IOException e) {
            // A missing asset only means "no suggestions"; typing still works.
            lines.clear();
        }
        return lines;
    }

    private static final class Hit {
        final String word;
        final int rank;

        Hit(String word, int rank) {
            this.word = word;
            this.rank = rank;
        }
    }
}
