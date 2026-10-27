package com.likhon.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * A small hand-checked list of everyday words whose spelling cannot be derived
 * from their sound alone (tomar -> তোমার, jodi -> যদি, kothay -> কোথায়).
 *
 * <p>Phonetic rules get most words right; this list covers the frequent
 * exceptions. Only whole words are replaced, so the live conversion while
 * typing still behaves normally. Keys are lowercase: capitalised input is left
 * to the rules, which is the escape hatch for manual spellings.
 */
final class WordDictionary {

    private static final String[] ENTRIES = {
            // pronouns and people
            "ami", "আমি",
            "amake", "আমাকে",
            "amar", "আমার",
            "amader", "আমাদের",
            "apni", "আপনি",
            "apnar", "আপনার",
            "apnader", "আপনাদের",
            "tumi", "তুমি",
            "tomake", "তোমাকে",
            "tomar", "তোমার",
            "tomader", "তোমাদের",
            "tomra", "তোমরা",
            "tora", "তোরা",
            "take", "তাকে",
            "tader", "তাদের",
            "bhai", "ভাই",
            "bon", "বোন",
            "didi", "দিদি",
            "meye", "মেয়ে",
            "meyera", "মেয়েরা",
            "chele", "ছেলে",

            // very common verbs
            "hobe", "হবে",
            "hoy", "হয়",
            "hoye", "হয়ে",
            "hoyeche", "হয়েছে",
            "hoyto", "হয়তো",
            "kori", "করি",
            "koro", "করো",
            "koren", "করেন",
            "korun", "করুন",
            "korte", "করতে",
            "korechi", "করেছি",
            "koreche", "করেছে",
            "kore", "করে",
            "korbo", "করবো",
            "korben", "করবেন",
            "korchhi", "করছি",
            "khabo", "খাবো",
            "kheye", "খেয়ে",
            "achi", "আছি",
            "achen", "আছেন",
            "jante", "জানতে",
            "jantam", "জানতাম",
            "jani", "জানি",
            "jano", "জানো",
            "jodi", "যদি",
            "jabe", "যাবে",
            "jabo", "যাবো",
            "jai", "যাই",
            "jay", "যায়",
            "jao", "যাও",
            "jemon", "যেমন",
            "jokhon", "যখন",
            "jekono", "যেকোনো",
            "joto", "যত",
            "jeno", "যেন",
            "je", "যে",
            "jibon", "জীবন",
            "jibone", "জীবনে",
            "jonno", "জন্য",
            "jonyo", "জন্য",
            "jol", "জল",
            "jomi", "জমি",
            "juta", "জুতা",
            "jinis", "জিনিস",
            "juddho", "যুদ্ধ",
            "bhalobasha", "ভালোবাসা",
            "bhalobasi", "ভালোবাসি",
            "valobasha", "ভালোবাসা",
            "valobasi", "ভালোবাসি",

            // question words and connectors
            "kothay", "কোথায়",
            "kothao", "কোথাও",
            "kono", "কোনো",
            "kothin", "কঠিন",
            "ebong", "এবং",
            "karon", "কারণ",
            "tahole", "তাহলে",
            "kintu", "কিন্তু",
            "kichu", "কিছু",
            "bhalo", "ভালো",
            "kotha", "কথা",
            "kemon", "কেমন",

            // everyday nouns
            "bari", "বাড়ি",
            "barite", "বাড়িতে",
            "gari", "গাড়ি",
            "gaari", "গাড়ি",
            "taka", "টাকা",
            "poisa", "পয়সা",
            "phone", "ফোন",
            "mobile", "মোবাইল",
            "train", "ট্রেন",
            "shomoy", "সময়",
            "shokal", "সকাল",
            "shondha", "সন্ধ্যা",
            "sondha", "সন্ধ্যা",
            "shundor", "সুন্দর",
            "sundor", "সুন্দর",
            "shob", "সব",
            "sob", "সব",
            "shobai", "সবাই",
            "sobai", "সবাই",
            "shuru", "শুরু",
            "shokto", "শক্ত",
            "sokto", "শক্ত",
            "shohoj", "সহজ",
            "choto", "ছোট",
            "boro", "বড়",
            "bangladesh", "বাংলাদেশ",
            "bangali", "বাঙালি",

            // greetings and numbers
            "dhonnobad", "ধন্যবাদ",
            "nomoshkar", "নমস্কার",
            "nomoskar", "নমস্কার",
            "shagotom", "স্বাগতম",
            "panch", "পাঁচ",
            "choy", "ছয়",
            "noy", "নয়",
            "dos", "দশ",
    };

    private static final Map<String, String> WORDS = new HashMap<>();
    private static final Map<String, String> REVERSE = new HashMap<>();

    static {
        for (int i = 0; i + 1 < ENTRIES.length; i += 2) {
            WORDS.put(ENTRIES[i], ENTRIES[i + 1]);
            REVERSE.put(ENTRIES[i + 1], ENTRIES[i]);
        }
    }

    private WordDictionary() {
    }

    static String lookup(String roman) {
        return WORDS.get(roman);
    }

    /** Bangla → the reviewed roman spelling, or null for unknown words. */
    static String reverse(String bangla) {
        return REVERSE.get(bangla);
    }
}
