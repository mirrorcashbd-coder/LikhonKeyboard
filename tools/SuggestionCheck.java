package com.likhon.keyboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Plain-JDK check for the suggestion indexes bundled in assets/.
 *
 * <p>It mirrors the search in {@link Suggestions} (binary search for the prefix,
 * keep the best fifteen by frequency rank) so the shipped data can be checked
 * without a phone. Run through {@code tools/check-phonetic.sh}.
 */
public final class SuggestionCheck {

    private static final int MAX = 18;
    private static final int SCAN = 600;

    private SuggestionCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path assets = Paths.get(args.length > 0 ? args[0] : "app/src/main/assets");
        int failures = 0;

        List<String> en = read(assets.resolve("words_en.txt"));
        String[] enSorted = en.toArray(new String[0]);
        Arrays.sort(enSorted);
        Map<String, Integer> enRank = new HashMap<>();
        for (int i = 0; i < en.size(); i++) {
            if (!enRank.containsKey(en.get(i))) {
                enRank.put(en.get(i), i);
            }
        }

        List<String> bnLines = read(assets.resolve("words_bn_index.txt"));
        int n = bnLines.size();
        String[] roman = new String[n];
        String[] word = new String[n];
        int[] rank = new int[n];
        for (int i = 0; i < n; i++) {
            String[] parts = bnLines.get(i).split("\t");
            roman[i] = parts[0];
            word[i] = parts.length > 1 ? parts[1] : "";
            rank[i] = parts.length > 2 ? Integer.parseInt(parts[2]) : Integer.MAX_VALUE;
        }

        failures += expect("english hel", english("hel", enSorted, enRank), "hello", "help");
        failures += expect("english thank", english("thank", enSorted, enRank), "thanks");
        failures += expect("bangla kemo", bangla("kemo", roman, word, rank), "কেমন");
        failures += expect("bangla bhal", bangla("bhal", roman, word, rank), "ভালো",
                "ভালোবাসা");
        failures += expect("bangla kor", bangla("kor", roman, word, rank), "করা", "করি");
        failures += expect("bangla am", bangla("am", roman, word, rank), "আমি");
        failures += expect("bangla bhalobas", bangla("bhalobas", roman, word, rank),
                "ভালোবাসা");
        if (!bangla("zzzz", roman, word, rank).isEmpty()) {
            System.out.println("FAIL bangla zzzz should be empty");
            failures++;
        }
        if (!english("qqqq", enSorted, enRank).isEmpty()) {
            System.out.println("FAIL english qqqq should be empty");
            failures++;
        }

        System.out.println("index: " + en.size() + " english words, " + n
                + " bangla suggestion keys");
        if (failures == 0) {
            System.out.println("All suggestion expectations passed.");
        } else {
            System.out.println(failures + " suggestion expectation(s) failed.");
            System.exit(1);
        }
    }

    private static int expect(String label, List<String> got, String... wanted) {
        int failures = 0;
        for (String want : wanted) {
            if (!got.contains(want)) {
                System.out.println("FAIL " + label + ": missing «" + want + "» in " + got);
                failures++;
            }
        }
        if (failures == 0) {
            System.out.println("ok   " + label + " -> " + got.subList(0, Math.min(6, got.size())));
        }
        return failures;
    }

    private static List<String> english(String prefix, String[] sorted,
                                        Map<String, Integer> ranks) {
        String key = prefix.toLowerCase(Locale.ROOT);
        int start = lowerBound(sorted, key);
        List<Hit> hits = new ArrayList<>();
        for (int i = start; i < sorted.length && sorted[i].startsWith(key); i++) {
            if (!sorted[i].equals(key)) {
                Integer r = ranks.get(sorted[i]);
                hits.add(new Hit(sorted[i], r == null ? Integer.MAX_VALUE : r));
            }
            if (hits.size() >= SCAN) {
                break;
            }
        }
        return top(hits);
    }

    private static List<String> bangla(String prefix, String[] roman, String[] word,
                                       int[] rank) {
        String key = prefix.toLowerCase(Locale.ROOT);
        int start = lowerBound(roman, key);
        List<Hit> hits = new ArrayList<>();
        for (int i = start; i < roman.length && roman[i].startsWith(key); i++) {
            if (!roman[i].equals(key)) {
                hits.add(new Hit(word[i], rank[i]));
            }
            if (hits.size() >= SCAN) {
                break;
            }
        }
        return top(hits);
    }

    private static List<String> top(List<Hit> hits) {
        Collections.sort(hits, new Comparator<Hit>() {
            @Override
            public int compare(Hit a, Hit b) {
                return Integer.compare(a.rank, b.rank);
            }
        });
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (Hit hit : hits) {
            unique.add(hit.word);
            if (unique.size() >= MAX) {
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

    private static List<String> read(Path path) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
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
