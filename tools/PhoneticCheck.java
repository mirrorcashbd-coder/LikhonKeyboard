package com.likhon.keyboard;

import java.text.Normalizer;

/**
 * Tiny offline check for the phonetic engine. Kept out of the APK: compile it
 * together with Phonetic.java using tools/check-phonetic.sh.
 */
public final class PhoneticCheck {

    private static int failed;

    public static void main(String[] args) {
        expect("ami", "আমি");
        expect("tumi", "তুমি");
        expect("kemon", "কেমন");
        expect("acho", "আছো");
        expect("bhalo", "ভালো");
        expect("korbo", "করবো");
        expect("kotha", "কথা");
        expect("bangla", "বাংলা");
        expect("desh", "দেশ");
        expect("kintu", "কিন্তু");
        expect("amar", "আমার");
        expect("tomar", "তোমার");
        expect("kothay", "কোথায়");
        expect("jabo", "যাবো");
        expect("asbo", "আসবো");
        expect("pani", "পানি");
        expect("khabar", "খাবার");
        expect("rasta", "রাস্তা");
        expect("shundor", "সুন্দর");
        expect("tui", "তুই");
        expect("ki", "কি");
        expect("na", "না");
        expect("onek", "অনেক");
        expect("kanna", "কান্না");
        expect("prithibi", "প্রিথিবি");
        expect("kkhoma", "ক্ষমা");
        expect("gyan", "জ্ঞান");
        expect("123", "১২৩");
        expect("k^kh", "ক্খ");
        expect("chad~", "ছাদঁ");

        String[] sample = {
                "valobasa", "bhalobasi", "shob", "choto", "choTo", "dhonnobad",
                "shokh", "bari", "ghor", "meye", "chele", "kolom", "boi", "likhon",
                "keyboard", "phone", "taka", "aja", "kal", "rat", "din"
        };
        for (String word : sample) {
            System.out.println(word + " -> " + Phonetic.convert(word));
        }

        if (failed == 0) {
            System.out.println("\nAll fixed expectations passed.");
        } else {
            System.out.println("\n" + failed + " expectation(s) failed.");
            System.exit(1);
        }
    }

    private static void expect(String roman, String bangla) {
        String actual = Normalizer.normalize(Phonetic.convert(roman), Normalizer.Form.NFC);
        boolean ok = actual.equals(Normalizer.normalize(bangla, Normalizer.Form.NFC));
        if (!ok) {
            failed++;
        }
        System.out.printf("%s %-10s -> %s%s%n", ok ? "ok  " : "FAIL", roman, actual,
                ok ? "" : "   (expected " + bangla + ")");
    }
}
