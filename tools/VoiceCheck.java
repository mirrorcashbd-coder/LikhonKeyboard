package com.likhon.keyboard;

/**
 * Plain-JDK checks for the two text helpers used by voice typing:
 * {@link Romanize} (Bangla → Banglish) and {@link VoiceText} (speech cleanup).
 *
 * <p>Run with tools/check-phonetic.sh.
 */
public final class VoiceCheck {

    private static int failures;

    public static void main(String[] args) {
        // Bangla script -> Banglish.
        expect("আমি", "ami");
        expect("কথা", "kotha");
        expect("ভালো", "bhalo");
        expect("বাংলা", "bangla");
        expect("বাংলাদেশ", "bangladesh");
        expect("কেমন", "kemon");
        expect("করবো", "korbo");
        expect("দেশ", "desh");
        expect("কিন্তু", "kintu");
        expect("তোমার", "tomar");
        expect("পানি", "pani");
        expect("রাস্তা", "rasta");
        expect("সুন্দর", "sundor");
        expect("ধন্যবাদ", "dhonnobad");
        expect("কোথায়", "kothay");
        expect("আমি ভালো আছি", "ami bhalo achi");
        expect("আমার নাম রফিক", "amar nam rophik");
        expect("দুঃখ", "du:kh");

        // Cleanup: fillers, stutters, punctuation and capitalisation.
        clean("উম আমি ভাত খাই", "আমি ভাত খাই");
        clean("আমি আমি যাব", "আমি যাব");
        clean("ami um bhalo achi", "Ami bhalo achi");
        clean("hello , world !", "Hello, world!");
        clean("umm you know it is fine", "It is fine");
        clean("কথা বলা হয়ে গেছে ।", "কথা বলা হয়ে গেছে।");

        if (failures == 0) {
            System.out.println("All voice expectations passed.");
        } else {
            System.out.println(failures + " voice expectation(s) failed.");
            System.exit(1);
        }
    }

    private static void expect(String bangla, String banglish) {
        String actual = Romanize.toLatin(bangla);
        if (!banglish.equals(actual)) {
            failures++;
            System.out.println("Romanize «" + bangla + "» -> «" + actual
                    + "», expected «" + banglish + "»");
        }
    }

    private static void clean(String raw, String expected) {
        String actual = VoiceText.clean(raw);
        if (!expected.equals(actual)) {
            failures++;
            System.out.println("clean «" + raw + "» -> «" + actual
                    + "», expected «" + expected + "»");
        }
    }
}
