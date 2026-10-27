package com.likhon.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-JDK checks for the learned-word list (no Android needed).
 * Run through {@code tools/check-phonetic.sh}.
 */
public final class LearnedCheck {

    private static int failures = 0;

    private LearnedCheck() {
    }

    public static void main(String[] args) {
        english();
        bangla();
        cleaning();
        persistence();
        limits();
        if (failures == 0) {
            System.out.println("All learned-word expectations passed.");
        } else {
            System.out.println(failures + " learned-word expectation(s) failed.");
            System.exit(1);
        }
    }

    private static void english() {
        LearnedIndex index = new LearnedIndex();
        index.add("hello", null);
        check("english prefix", index.suggest("hel", false, 8).contains("hello"));
        check("english recent", index.suggest("", false, 8).contains("hello"));
        check("english other script hidden", index.suggest("hel", true, 8).isEmpty());

        index.add("alpha", null);
        index.add("alpine", null);
        index.add("alpine", null);
        List<String> top = index.suggest("alp", false, 8);
        check("frequent word first", top.size() >= 2 && top.get(0).equals("alpine"));

        index.add("Hello", null);
        index.add("hello", null);
        check("case merge", count(index.suggest("hel", false, 8), "hello") == 1);

        check("remove", index.remove("hello"));
        check("remove gone", !index.suggest("hel", false, 8).contains("hello"));
        check("remove missing", !index.remove("nothing"));
    }

    private static void bangla() {
        LearnedIndex index = new LearnedIndex();
        index.add("আমার", "amar");
        check("bangla roman prefix", index.suggest("ama", true, 8).contains("আমার"));
        check("bangla recent", index.suggest("", true, 8).contains("আমার"));
        check("bangla hidden in english", index.suggest("ama", false, 8).isEmpty());

        index.add("কেমন", null);
        check("bangla derived roman", index.suggest("kem", true, 8).contains("কেমন"));
    }

    private static void cleaning() {
        check("strip punctuation", "hello".equals(LearnedIndex.cleanWord("hello!")));
        check("strip spaces", "hello".equals(LearnedIndex.cleanWord("  hello  ")));
        check("short word", LearnedIndex.cleanWord("a") == null);
        check("digits", LearnedIndex.cleanWord("1234") == null);
        check("emoji", LearnedIndex.cleanWord("😀😀") == null);
        check("mixed script", LearnedIndex.cleanWord("amiআমি") == null);
        check("apostrophe", "don't".equals(LearnedIndex.cleanWord("don't")));
        check("bangla", "আমার".equals(LearnedIndex.cleanWord("আমার।")));
    }

    private static void persistence() {
        LearnedIndex index = new LearnedIndex();
        index.add("hello", null);
        index.add("আমার", "amar");
        index.add("alpine", null);
        List<String> lines = index.toLines();
        check("lines written", lines.size() == 3);

        LearnedIndex restored = new LearnedIndex();
        restored.fromLines(new ArrayList<>(lines));
        check("restore english", restored.suggest("hel", false, 8).contains("hello"));
        check("restore bangla", restored.suggest("ama", true, 8).contains("আমার"));
        check("restore recent", restored.suggest("", false, 8).contains("hello"));
    }

    private static void limits() {
        LearnedIndex index = new LearnedIndex();
        int added = 0;
        outer:
        for (char a = 'a'; a <= 'z'; a++) {
            for (char b = 'a'; b <= 'z'; b++) {
                index.add("" + a + b, null);
                if (++added >= 300) {
                    break outer;
                }
            }
        }
        check("cap at " + LearnedIndex.MAX_ENTRIES,
                index.size() == LearnedIndex.MAX_ENTRIES);
        check("newest kept", index.suggest("", false, 400).size()
                == LearnedIndex.MAX_ENTRIES);
    }

    private static int count(List<String> words, String wanted) {
        int found = 0;
        for (String word : words) {
            if (word.equalsIgnoreCase(wanted)) {
                found++;
            }
        }
        return found;
    }

    private static void check(String label, boolean ok) {
        if (ok) {
            System.out.println("ok   " + label);
        } else {
            System.out.println("FAIL " + label);
            failures++;
        }
    }
}
