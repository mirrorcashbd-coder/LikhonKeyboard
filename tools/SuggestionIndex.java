package com.likhon.keyboard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the Bangla suggestion index used by the keyboard at runtime.
 *
 * <p>Bangla typing is phonetic, so the interesting key for a suggestion is the
 * <em>roman</em> form of a word: the user types “bhal” and the keyboard should
 * offer ভালো, ভালোবাসা… To make that a cheap prefix search, every Bangla word
 * in the source list is romanised with {@link Romanize} and stored next to it:
 *
 * <pre>roman \t bangla \t rank</pre>
 *
 * <p>Lines are sorted by roman; {@code rank} keeps the original order so the
 * common words at the top of the source list still win over rare ones.
 *
 * <p>Run from the project root:
 * <pre>java -cp build/tools com.likhon.keyboard.SuggestionIndex</pre>
 * or simply {@code tools/build-suggestions.sh}.
 */
public final class SuggestionIndex {

    private SuggestionIndex() {
    }

    public static void main(String[] args) throws IOException {
        Path in = Paths.get(args.length > 0 ? args[0] : "tools/data/words_bn.txt");
        Path out = Paths.get(args.length > 1 ? args[1]
                : "app/src/main/assets/words_bn_index.txt");
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int rank = 0;
        try (BufferedReader reader = Files.newBufferedReader(in, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.isEmpty()) {
                    continue;
                }
                addRow(rows, seen, Romanize.toLatin(word), word, rank);
                addRow(rows, seen, Romanize.toLatinRules(word), word, rank);
                rank++;
            }
        }
        rows.sort(Comparator.comparing((Row r) -> r.roman).thenComparingInt(r -> r.rank));
        Files.createDirectories(out.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (Row row : rows) {
                writer.write(row.roman);
                writer.write('\t');
                writer.write(row.bangla);
                writer.write('\t');
                writer.write(Integer.toString(row.rank));
                writer.write('\n');
            }
        }
        System.out.println("Wrote " + rows.size() + " suggestion entries to " + out);
    }

    private static void addRow(List<Row> rows, Set<String> seen, String roman,
                               String word, int rank) {
        if (roman.isEmpty() || roman.indexOf(' ') >= 0 || roman.indexOf('\t') >= 0) {
            return;
        }
        if (!seen.add(roman + '\t' + word)) {
            return;
        }
        rows.add(new Row(roman, word, rank));
    }

    private static final class Row {
        final String roman;
        final String bangla;
        final int rank;

        Row(String roman, String bangla, int rank) {
            this.roman = roman;
            this.bangla = bangla;
            this.rank = rank;
        }
    }
}
