package com.likhon.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Phonetic (Roman -> Bangla) transliteration.
 *
 * <p>The engine is deliberately small and predictable, in the spirit of the
 * classic Avro-style typing most Bangla speakers already know:
 *
 * <ul>
 *   <li>Consonants follow English sounds: k, kh, g, gh, ch, j, T, Th, D, Dh, t,
 *       th, d, dh, n, p, ph/f, b, bh/v, m, r, l, sh, S, s, h, y, Y, R, ng.</li>
 *   <li>Vowels use a/A, i/I, u/U, e/E, o/O, oi, ou, rri. A vowel at the start
 *       of a word becomes independent (আ, ই, ...); after a consonant it becomes
 *       a vowel sign (া, ি, ...).</li>
 *   <li>{@code o} is the inherent vowel between consonants (kotha -> কথা) and
 *       becomes ো at the end of a word (bhalo -> ভালো).</li>
 *   <li>Doubled letters and common clusters form conjuncts: kk -> ক্ক,
 *       nt -> ন্ত, kkh -> ক্ষ.</li>
 *   <li>Digits become Bangla digits; ^ is an explicit hasanta, ~ is a
 *       chandrabindu.</li>
 * </ul>
 *
 * <p>{@link #convert(String)} always converts a whole word, so the keyboard can
 * keep the raw Latin input in a composing region and re-render it on every
 * keystroke. That is what makes the silent-“o” rule work while typing.
 */
final class Phonetic {

    private static final class Vowel {
        final String independent;
        final String dependent;
        final char family;      // used to collapse doubled vowels (aa, ii, ...)
        final boolean silentMid;

        Vowel(String independent, String dependent, char family, boolean silentMid) {
            this.independent = independent;
            this.dependent = dependent;
            this.family = family;
            this.silentMid = silentMid;
        }
    }

    private static final Map<String, String> CONSONANTS = new HashMap<>();
    private static final Map<String, Vowel> VOWELS = new HashMap<>();

    private static void c(String key, String value) {
        CONSONANTS.put(key, value);
    }

    private static void v(String key, String independent, String dependent, char family, boolean silentMid) {
        VOWELS.put(key, new Vowel(independent, dependent, family, silentMid));
    }

    static {
        // --- special clusters -------------------------------------------------
        c("kkh", "ক্ষ");
        c("gy", "জ্ঞ");
        c("nj", "ঞ্জ");
        c("ny", "ঞ");
        c("chh", "ছ");
        c("shr", "শ্র");
        c("str", "স্ত্র");
        c("thr", "থ্র");
        c("dhr", "ধ্র");
        c("bhr", "ভ্র");
        c("phr", "ফ্র");
        c("khr", "খ্র");
        c("ghr", "ঘ্র");
        c("shn", "শ্ন");
        c("hm", "হ্ম");
        c("hn", "হ্ন");

        // --- two letter consonants -------------------------------------------
        c("kh", "খ");
        c("Kh", "খ");
        c("gh", "ঘ");
        c("Gh", "ঘ");
        c("ch", "ছ");
        c("Ch", "ছ");
        c("jh", "ঝ");
        c("Jh", "ঝ");
        c("Th", "ঠ");
        c("Dh", "ঢ");
        c("th", "থ");
        c("dh", "ধ");
        c("ph", "ফ");
        c("Ph", "ফ");
        c("bh", "ভ");
        c("Bh", "ভ");
        c("sh", "শ");
        c("Sh", "ষ");
        c("ng", "ং");
        c("Ng", "ং");
        c("Rh", "ঢ়");
        c("yy", "য়");

        // --- common conjuncts -------------------------------------------------
        c("nt", "ন্ত");
        c("nd", "ন্দ");
        c("mp", "ম্প");
        c("mb", "ম্ব");
        c("nk", "ঙ্ক");
        c("st", "স্ত");
        c("sk", "স্ক");
        c("sp", "স্প");
        c("sn", "স্ন");
        c("sm", "স্ম");
        c("sl", "স্ল");
        c("sr", "স্র");
        c("pr", "প্র");
        c("br", "ব্র");
        c("kr", "ক্র");
        c("gr", "গ্র");
        c("tr", "ত্র");
        c("dr", "দ্র");
        c("pl", "প্ল");
        c("bl", "ব্ল");
        c("kl", "ক্ল");
        c("gl", "গ্ল");
        c("fl", "ফ্ল");
        c("db", "দ্ব");
        c("dv", "দ্ব");
        c("bd", "ব্দ");
        c("gd", "গ্দ");
        c("tm", "ত্ম");

        c("tt", "ত্ত");
        c("dd", "দ্দ");
        c("nn", "ন্ন");
        c("mm", "ম্ম");
        c("ll", "ল্ল");
        c("pp", "প্প");
        c("bb", "ব্ব");
        c("gg", "গ্গ");
        c("jj", "জ্জ");
        c("cc", "চ্চ");
        c("kk", "ক্ক");
        c("ss", "স্স");
        c("TT", "ট্ট");
        c("DD", "ড্ড");
        c("NN", "ণ্ণ");
        c("MM", "ম্ম");
        c("PP", "প্প");
        c("BB", "ব্ব");
        c("GG", "গ্গ");
        c("JJ", "জ্জ");
        c("CC", "চ্চ");
        c("KK", "ক্ক");
        c("SS", "স্স");

        // --- single consonants ------------------------------------------------
        c("k", "ক");
        c("K", "ক");
        c("q", "ক");
        c("Q", "ক");
        c("g", "গ");
        c("G", "গ");
        c("c", "চ");
        c("C", "চ");
        c("j", "জ");
        c("J", "জ");
        c("z", "জ");
        c("Z", "জ");
        c("T", "ট");
        c("D", "ড");
        c("N", "ণ");
        c("t", "ত");
        c("d", "দ");
        c("n", "ন");
        c("p", "প");
        c("P", "প");
        c("b", "ব");
        c("B", "ব");
        c("m", "ম");
        c("M", "ম");
        c("r", "র");
        c("l", "ল");
        c("L", "ল");
        c("s", "স");
        c("S", "ষ");
        c("h", "হ");
        c("y", "য");
        c("Y", "য়");
        c("R", "ড়");
        c("v", "ভ");
        c("V", "ভ");
        c("f", "ফ");
        c("F", "ফ");
        c("x", "ক্স");
        c("X", "ক্স");

        // --- vowels -----------------------------------------------------------
        v("rri", "ঋ", "ৃ", 'r', false);
        v("oi", "ঐ", "ৈ", (char) 0, false);
        v("ou", "ঔ", "ৌ", (char) 0, false);
        v("a", "আ", "া", 'a', false);
        v("A", "আ", "া", 'a', false);
        v("i", "ই", "ি", 'i', false);
        v("I", "ঈ", "ী", 'i', false);
        v("u", "উ", "ু", 'u', false);
        v("U", "ঊ", "ূ", 'u', false);
        v("e", "এ", "ে", 'e', false);
        v("E", "এ", "ে", 'e', false);
        v("o", "অ", "ো", 'o', true);
        v("O", "ও", "ো", 'o', false);
    }

    private Phonetic() {
    }

    /** True for the letters that feed the transliterator. */
    static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Converts one romanised word. Anything the engine does not know (digits,
     * punctuation, unknown symbols) is copied through unchanged.
     */
    static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        // Whole-word exceptions first; partial words still use the rules, so
        // the composing text keeps updating naturally.
        String known = WordDictionary.lookup(input);
        if (known != null) {
            return known;
        }
        StringBuilder out = new StringBuilder(input.length() * 2);
        int i = 0;
        final int n = input.length();
        boolean prevConsonant = false;
        char prevVowelFamily = 0;

        while (i < n) {
            char ch = input.charAt(i);

            // Bangla digits, so phone numbers stay readable in Bangla mode.
            if (ch >= '0' && ch <= '9') {
                out.append((char) ('০' + (ch - '0')));
                i++;
                prevConsonant = false;
                prevVowelFamily = 0;
                continue;
            }

            // Explicit helpers.
            if (ch == '^') {         // hasanta
                out.append('\u09CD');
                i++;
                prevConsonant = false;
                prevVowelFamily = 0;
                continue;
            }
            if (ch == '~') {         // chandrabindu
                out.append('\u0981');
                i++;
                prevConsonant = false;
                prevVowelFamily = 0;
                continue;
            }
            if (ch == ':') {         // visarga
                out.append('\u0983');
                i++;
                prevConsonant = false;
                prevVowelFamily = 0;
                continue;
            }
            if (ch == 'w' || ch == 'W') {
                out.append('ও');
                i++;
                prevConsonant = false;
                prevVowelFamily = 'o';
                continue;
            }

            // Longest vowel match first (oi, ou, rri before single letters).
            String vowelKey = matchLongest(input, i, VOWELS, 3);
            if (vowelKey != null) {
                Vowel vowel = VOWELS.get(vowelKey);
                if (prevVowelFamily != 0 && vowel.family != 0 && prevVowelFamily == vowel.family) {
                    // "aa", "ii", "uu" ... collapse instead of producing an
                    // invalid double vowel sign.
                    i += vowelKey.length();
                    continue;
                }
                if (!prevConsonant) {
                    out.append(vowel.independent);
                } else if (vowel.silentMid) {
                    if (i + vowelKey.length() >= n) {
                        out.append(vowel.dependent);   // bhalo -> ভালো
                    } else {
                        // inherent vowel, no glyph: kotha -> কথা
                    }
                } else {
                    out.append(vowel.dependent);
                }
                prevConsonant = false;
                prevVowelFamily = vowel.family;
                i += vowelKey.length();
                continue;
            }

            // Word-final "y" is almost always য় (kothay -> কথায়).
            if ((ch == 'y' || ch == 'Y') && i == n - 1) {
                out.append('\u09DF');
                i++;
                prevConsonant = false;
                prevVowelFamily = 0;
                continue;
            }

            // Longest consonant/cluster match (kkh, shr, nt, ... then k).
            String consonantKey = matchLongest(input, i, CONSONANTS, 4);
            if (consonantKey != null) {
                out.append(CONSONANTS.get(consonantKey));
                prevConsonant = true;
                prevVowelFamily = 0;
                i += consonantKey.length();
                continue;
            }

            out.append(ch);
            prevConsonant = false;
            prevVowelFamily = 0;
            i++;
        }

        return out.toString();
    }

    private static String matchLongest(String input, int index, Map<String, ?> map, int maxLength) {
        int available = Math.min(maxLength, input.length() - index);
        for (int length = available; length >= 1; length--) {
            String candidate = input.substring(index, index + length);
            if (map.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
