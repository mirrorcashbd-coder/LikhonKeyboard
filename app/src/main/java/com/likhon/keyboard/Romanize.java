package com.likhon.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Bangla script → <em>Banglish</em> (Bangla written with Latin letters).
 *
 * <p>This is the reverse of {@link Phonetic}: voice typing uses it when the
 * recogniser returns Bangla text but the person wants to send Banglish. The
 * output follows the same habits as phonetic typing, so “kotha” and “bhalo”
 * look the way most people already write them.
 *
 * <p>It is a transliteration, not a translation: it keeps the Bangla words and
 * only changes the script. Words in {@link WordDictionary} use their reviewed
 * roman spelling, because Bangla’s inherent vowels are not predictable from the
 * letters alone (করে → kore, but করবো → korbo).
 */
final class Romanize {

    private static final Map<Character, String> CONSONANT = new HashMap<>();
    private static final Map<Character, String> INDEPENDENT = new HashMap<>();
    private static final Map<Character, String> SIGN = new HashMap<>();

    private static final char HASANTA = '\u09CD';

    /** Conjuncts whose letter-by-letter romanisation would read badly. */
    private static final String[][] CLUSTERS = {
            {"ক্ষ", "kkh"},
            {"জ্ঞ", "gy"},
            {"চ্ছ", "chh"},
            {"ন্য", "nn"},
            {"স্ব", "sh"},
            {"ঞ্চ", "nch"},
            {"ঞ্জ", "nj"},
            // Decomposed ড় / ঢ় / য় (base + nukta).
            {"\u09A1\u09BC", "r"},
            {"\u09A2\u09BC", "rh"},
            {"\u09AF\u09BC", "y"},
    };

    private static void c(char letter, String latin) {
        CONSONANT.put(letter, latin);
    }

    private static void v(char letter, String latin) {
        INDEPENDENT.put(letter, latin);
    }

    private static void s(char letter, String latin) {
        SIGN.put(letter, latin);
    }

    static {
        c('ক', "k");
        c('খ', "kh");
        c('গ', "g");
        c('ঘ', "gh");
        c('ঙ', "ng");
        c('চ', "ch");
        c('ছ', "chh");
        c('জ', "j");
        c('ঝ', "jh");
        c('ঞ', "n");
        // Retroflex letters share Latin letters with the dental ones, the way
        // Banglish is normally written (both ট and ত are "t").
        c('ট', "t");
        c('ঠ', "th");
        c('ড', "d");
        c('ঢ', "dh");
        c('ণ', "n");
        c('ত', "t");
        c('থ', "th");
        c('দ', "d");
        c('ধ', "dh");
        c('ন', "n");
        c('প', "p");
        c('ফ', "ph");
        c('ব', "b");
        c('ভ', "bh");
        c('ম', "m");
        c('য', "j");
        c('র', "r");
        c('ল', "l");
        c('শ', "sh");
        c('ষ', "sh");
        c('স', "s");
        c('হ', "h");
        // Precomposed forms (U+09DC, U+09DD, U+09DF); the decomposed
        // "base + nukta" spellings are handled as clusters above.
        c('\u09DC', "r");
        c('\u09DD', "rh");
        c('\u09DF', "y");
        c('ৎ', "t");

        v('অ', "o");
        v('আ', "a");
        v('ই', "i");
        v('ঈ', "i");
        v('উ', "u");
        v('ঊ', "u");
        v('ঋ', "ri");
        v('এ', "e");
        v('ঐ', "oi");
        v('ও', "o");
        v('ঔ', "ou");

        s('া', "a");
        s('ি', "i");
        s('ী', "i");
        s('ু', "u");
        s('ূ', "u");
        s('ৃ', "ri");
        s('ে', "e");
        s('ৈ', "oi");
        s('ো', "o");
        s('ৌ', "ou");
    }

    private Romanize() {
    }

    /** Latin (Banglish) form of a Bangla string; other scripts pass through. */
    static String toLatin(String text) {
        return toLatin(text, true);
    }

    /**
     * Same as {@link #toLatin(String)} but ignores the reviewed spellings, so
     * the raw rules decide. Used to index alternative spellings for search.
     */
    static String toLatinRules(String text) {
        return toLatin(text, false);
    }

    private static String toLatin(String text, boolean useDictionary) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() * 2);
        int i = 0;
        int n = text.length();
        while (i < n) {
            if (!isBangla(text.charAt(i))) {
                out.append(text.charAt(i));
                i++;
                continue;
            }
            int end = i;
            while (end < n && isBangla(text.charAt(end))) {
                end++;
            }
            String word = text.substring(i, end);
            String known = useDictionary ? WordDictionary.reverse(word) : null;
            if (known != null) {
                out.append(known);
            } else {
                appendWord(text, i, end, out);
            }
            i = end;
        }
        return out.toString();
    }

    private static void appendWord(String text, int start, int end, StringBuilder out) {
        int i = start;
        while (i < end) {
            String consonant = null;
            for (String[] pair : CLUSTERS) {
                if (text.startsWith(pair[0], i)) {
                    consonant = pair[1];
                    i += pair[0].length();
                    break;
                }
            }
            if (consonant == null) {
                consonant = CONSONANT.get(text.charAt(i));
                if (consonant != null) {
                    i++;
                }
            }
            if (consonant != null) {
                out.append(consonant);
                if (i >= end) {
                    continue;
                }
                char next = text.charAt(i);
                String sign = SIGN.get(next);
                if (sign != null) {
                    out.append(sign);
                    i++;
                    continue;
                }
                if (next == HASANTA) {
                    // Conjunct: the next consonant follows without a vowel.
                    i++;
                    continue;
                }
                if (isLetter(next)) {
                    // Inherent vowel, as in kotha (কথা) or kolom (কলম).
                    out.append('o');
                }
                continue;
            }

            char c = text.charAt(i);
            String independent = INDEPENDENT.get(c);
            if (independent != null) {
                out.append(independent);
                i++;
                continue;
            }
            String sign = SIGN.get(c);
            if (sign != null) {
                out.append(sign);
                i++;
                continue;
            }
            switch (c) {
                case 'ং':
                    out.append("ng");
                    break;
                case 'ৎ':
                    out.append('t');
                    break;
                case 'ঁ':
                    // Nasalisation is normally dropped in Banglish.
                    break;
                case 'ঃ':
                    out.append(':');
                    break;
                case '।':
                    out.append('.');
                    break;
                case '৳':
                    out.append("Tk");
                    break;
                case HASANTA:
                    break;
                default:
                    if (c >= '০' && c <= '৯') {
                        out.append((char) ('0' + (c - '০')));
                    } else {
                        out.append(c);
                    }
            }
            i++;
        }
    }

    private static boolean isBangla(char c) {
        return c >= '\u0980' && c <= '\u09FF';
    }

    /** True for Bangla letters and signs that keep a word going. */
    private static boolean isLetter(char c) {
        return CONSONANT.containsKey(c) || INDEPENDENT.containsKey(c)
                || c == 'ং' || c == 'ঁ' || c == 'ঃ' || c == 'ৎ';
    }
}
