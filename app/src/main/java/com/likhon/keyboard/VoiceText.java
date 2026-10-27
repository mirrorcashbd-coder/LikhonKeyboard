package com.likhon.keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tidies raw speech-recognition output, the way “Rambler”-style voice typing
 * does: hesitation sounds and stutters are dropped, spacing around punctuation
 * is fixed, and the first Latin letter is capitalised.
 *
 * <p>Everything here is deliberately conservative. It only deletes words that
 * are almost never meant (um, uh, উম, ইয়ে…) and only joins a word with the
 * identical word right before it (a stutter), so normal speech is untouched.
 */
final class VoiceText {

    /** Stand-alone hesitation sounds in Bangla. */
    private static final Set<String> BANGLA_FILLERS = new HashSet<>(Arrays.asList(
            "উম", "উমম", "উঁ", "উহুঁ", "আআ", "আআআ", "আহ", "ইয়ে", "ইয়ো",
            "হুম", "হুমম", "এরম", "এ্যাঁ", "মম"));

    /** Stand-alone hesitation sounds in English. */
    private static final Set<String> LATIN_FILLERS = new HashSet<>(Arrays.asList(
            "um", "umm", "ummm", "uh", "uhh", "uhhh", "er", "erm", "hmm",
            "hmmm", "mhm", "mm", "mmm", "ah", "ahh"));

    /** Multi-word fillers removed as a phrase. */
    private static final String[][] LATIN_PHRASES = {
            {"you", "know"},
            {"i", "mean"},
    };

    private VoiceText() {
    }

    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace('\n', ' ').replace('\t', ' ').trim();
        if (text.isEmpty()) {
            return "";
        }
        String[] words = text.split("\\s+");
        List<String> kept = new ArrayList<>(words.length);
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String bare = stripPunctuation(word);
            String lower = bare.toLowerCase(new Locale("en"));
            if (bare.isEmpty()) {
                // A token that is only punctuation belongs to the word before.
                if (!kept.isEmpty()) {
                    kept.set(kept.size() - 1, kept.get(kept.size() - 1) + word);
                }
                continue;
            }
            if (BANGLA_FILLERS.contains(bare) || LATIN_FILLERS.contains(lower)) {
                continue;
            }
            if (i + 1 < words.length && isPhraseStart(lower, words[i + 1])) {
                i++;
                continue;
            }
            if (!kept.isEmpty() && sameWord(kept.get(kept.size() - 1), word)) {
                // Stutter or self-correction: keep only the last saying.
                kept.set(kept.size() - 1, word);
                continue;
            }
            kept.add(word);
        }
        if (kept.isEmpty()) {
            return "";
        }
        String joined = join(kept);
        joined = tidyPunctuation(joined);
        return capitaliseFirst(joined);
    }

    private static boolean isPhraseStart(String lower, String next) {
        String nextBare = stripPunctuation(next).toLowerCase(new Locale("en"));
        for (String[] phrase : LATIN_PHRASES) {
            if (phrase[0].equals(lower) && phrase[1].equals(nextBare)) {
                return true;
            }
        }
        return false;
    }

    private static String stripPunctuation(String word) {
        int start = 0;
        int end = word.length();
        while (start < end && isPunctuation(word.charAt(start))) {
            start++;
        }
        while (end > start && isPunctuation(word.charAt(end - 1))) {
            end--;
        }
        return word.substring(start, end);
    }

    private static boolean isPunctuation(char c) {
        return c == ',' || c == '.' || c == '!' || c == '?' || c == ';'
                || c == ':' || c == '।' || c == '…' || c == '"' || c == '\'';
    }

    private static boolean sameWord(String a, String b) {
        String left = stripPunctuation(a).toLowerCase(new Locale("en"));
        String right = stripPunctuation(b).toLowerCase(new Locale("en"));
        return !left.isEmpty() && left.equals(right);
    }

    private static String join(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(word);
        }
        return sb.toString();
    }

    /** “hello , world !” → “hello, world!” */
    private static String tidyPunctuation(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' && i + 1 < text.length() && isPunctuation(text.charAt(i + 1))) {
                // Drop the space that speech put before a comma or full stop.
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String capitaliseFirst(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                if (c >= 'a' && c <= 'z') {
                    return text.substring(0, i) + Character.toUpperCase(c)
                            + text.substring(i + 1);
                }
                return text;
            }
        }
        return text;
    }

    // --------------------------------------------------------- voice commands

    /** Two-word commands: words, replacement. */
    private static final String[][] COMMAND_PHRASES = {
            {"new", "line", "\n"},
            {"new", "paragraph", "\n"},
            {"full", "stop", "."},
            {"question", "mark", "?"},
            {"exclamation", "mark", "!"},
            {"exclamation", "point", "!"},
            {"\u09a8\u09a4\u09c1\u09a8", "\u09b2\u09be\u0987\u09a8", "\n"},
    };

    /** One-word commands; the same list covers both scripts. */
    private static final String[][] COMMAND_WORDS = {
            {"period", "."},
            {"comma", ","},
            {"colon", ":"},
            {"semicolon", ";"},
            {"\u09a6\u09be\u0981\u09dc\u09bf", "\u0964"},
            {"\u0995\u09ae\u09be", ","},
            {"\u09aa\u09cd\u09b0\u09b6\u09cd\u09a8\u099a\u09cd\u09b9\u09bf\u09a8", "?"},
            {"\u09ac\u09bf\u09b8\u09cd\u09ae\u09df\u099a\u09cd\u09b9\u09bf\u09a8", "!"},
            {"\u0995\u09cb\u09b2\u09a8", ":"},
            {"\u09b8\u09c7\u09ae\u09bf\u0995\u09cb\u09b2\u09a8", ";"},
    };

    /**
     * Turns spoken punctuation into the real thing, the way Gboard's Rambler
     * voice typing does: “hello comma world period” becomes “hello, world.”.
     * Commands are matched on whole words only, so “the period of this” is
     * left alone unless the word really is a lone command token.
     */
    static String commands(String raw, boolean bangla) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace('\n', ' ').trim();
        if (text.isEmpty()) {
            return "";
        }
        String[] words = text.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String replacement = null;
            int consumed = 0;
            String lower = normalise(words[i].toLowerCase(new Locale("en")));
            for (String[] phrase : COMMAND_PHRASES) {
                if (i + 1 < words.length
                        && lower.equals(normalise(phrase[0]))
                        && normalise(words[i + 1].toLowerCase(new Locale("en")))
                                .equals(normalise(phrase[1]))) {
                    replacement = phrase[2];
                    consumed = 2;
                    break;
                }
            }
            if (replacement == null) {
                for (String[] command : COMMAND_WORDS) {
                    if (lower.equals(normalise(command[0]))) {
                        replacement = command[1];
                        consumed = 1;
                        break;
                    }
                }
            }
            if (replacement != null) {
                // “period” and “colon” are common nouns, so they only count at
                // the end of a phrase; the other commands are unambiguous.
                boolean nounLike = "period".equals(lower) || "colon".equals(lower);
                if (nounLike && i != words.length - 1) {
                    replacement = null;
                }
            }
            if (replacement != null) {
                if ("\n".equals(replacement)) {
                    out.append('\n');
                } else {
                    if (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                        out.setLength(out.length() - 1);
                    }
                    if (bangla && ".".equals(replacement)) {
                        replacement = "\u0964";
                    }
                    out.append(replacement);
                }
                i += consumed - 1;
                continue;
            }
            if (out.length() > 0) {
                char last = out.charAt(out.length() - 1);
                if (last != ' ' && last != '\n') {
                    out.append(' ');
                }
            }
            out.append(words[i]);
        }
        int start = 0;
        int end = out.length();
        while (start < end && out.charAt(start) == ' ') {
            start++;
        }
        while (end > start && out.charAt(end - 1) == ' ') {
            end--;
        }
        return out.substring(start, end);
    }

    /** Bangla nukta letters can arrive precomposed or decomposed. */
    private static String normalise(String word) {
        StringBuilder sb = new StringBuilder(word.length() + 2);
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '\u09dc') {
                sb.append('\u09a1').append('\u09bc');
            } else if (c == '\u09dd') {
                sb.append('\u09a2').append('\u09bc');
            } else if (c == '\u09df') {
                sb.append('\u09af').append('\u09bc');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** “delete that”, “মুছে ফেলো” and friends, as a whole phrase. */
    static boolean isDeleteCommand(String raw) {
        if (raw == null) {
            return false;
        }
        String text = normalise(raw.toLowerCase(new Locale("en")).trim());
        text = text.replaceAll("[\\p{Punct}\\u0964\\s]+$", "");
        text = text.replaceAll("\\s+", " ");
        return text.equals("delete")
                || text.equals("delete that")
                || text.equals("scratch that")
                || text.equals("erase that")
                || text.equals("\u09ae\u09c1\u099b\u09c7 \u09ab\u09c7\u09b2\u09cb")
                || text.equals("\u09ae\u09c1\u099b\u09c7 \u09ab\u09c7\u09b2")
                || text.equals("\u0993\u099f\u09be \u09ae\u09c1\u099b\u09c7 \u09ab\u09c7\u09b2\u09cb")
                || text.equals("\u098f\u099f\u09be \u09ae\u09c1\u099b\u09c7 \u09ab\u09c7\u09b2\u09cb")
                || text.equals("\u09b8\u09ac \u09ae\u09c1\u099b\u09c7 \u09ab\u09c7\u09b2\u09cb");
    }

}
