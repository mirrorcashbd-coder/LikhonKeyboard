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
}
