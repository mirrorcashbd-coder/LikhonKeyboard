package com.likhon.keyboard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The language registry.
 *
 * <p>Latin languages keep the familiar QWERTY order and only add their own
 * letters (ñ, ü, ş, ą …). Script languages carry their own three rows plus a
 * shifted row for matras, signs and extra letters, so every letter of the
 * alphabet can be typed without leaving the keyboard.
 *
 * <p>Chinese, Japanese and Korean character input needs a dictionary engine
 * (pinyin/romaji/hangul conversion) and is deliberately not claimed here: the
 * layouts below type real scripts, not transliterations.
 */
final class Languages {

    private static final List<Language> ALL = new ArrayList<>();

    private static final String DEFAULT_ENABLED = "en,bn";

    static {
        // ------------------------------------------------------------- English
        ALL.add(Language.latin("en", "English", "English", "EN", "English"));

        // --------------------------------------------------- Bangla (phonetic)
        ALL.add(Language.latin("bn", "বাংলা", "Bengali (phonetic)", "বাং", "বাংলা"));

        // ----------------------------------------------- Bangla (direct script)
        ALL.add(new Language("bn_bd", "বাংলা (সরাসরি)", "Bengali (script)", "অ", "বাংলা",
                false,
                c("অ আ ই ঈ উ ঊ ঋ এ ঐ ও"),
                c("ক খ গ ঘ ঙ চ ছ জ ঝ ঞ"),
                c("ট ঠ ড ঢ ণ ত থ দ ধ ন"),
                c("ঔ ৌ া ি ী ু ূ ৃ ে ৈ ো"),
                c("প ফ ব ভ ম য র ল শ ষ"),
                c("স হ ড় ঢ় য় ৎ ং ঃ ঁ ্ ৳")));

        // --------------------------------------------------------------- Hindi
        ALL.add(new Language("hi", "हिन्दी", "Hindi", "हि", "हिन्दी", false,
                c("अ आ इ ई उ ऊ ऋ ए ऐ ओ"),
                c("क ख ग घ ङ च छ ज झ ञ"),
                c("ट ठ ड ढ ण त थ द ध न"),
                c("औ ा ि ी ु ू ृ े ै ो"),
                c("प फ ब भ म य र ल व श"),
                c("ष स ह ् ं ः ँ ौ")));

        // -------------------------------------------------------------- Arabic
        ALL.add(new Language("ar", "العربية", "Arabic", "ع", "العربية", true,
                c("ض ص ث ق ف غ ع ه خ ح"),
                c("ش س ي ب ل ا ت ن م ك"),
                c("ط ج د ذ ر ز و ة ى"),
                c("أ إ آ ؤ ئ ء ً ٌ ٍ َ"),
                c("ُ ِ ّ ْ ـ ؟ ، ؛"),
                c("٠ ١ ٢ ٣ ٤ ٥ ٦ ٧ ٨ ٩")));

        // ---------------------------------------------------------------- Urdu
        ALL.add(new Language("ur", "اردو", "Urdu", "ا", "اردو", true,
                c("ط ص ھ د ٹ پ ت ب ج چ"),
                c("م و ر ن ل ہ ا ک ی ق"),
                c("ف ے ش غ ع ظ ض ذ ژ"),
                c("آ ء ئ ۃ ں ۂ ۓ ڈ ڑ ّ"),
                c("َ ِ ُ ً ٌ ٍ ْ ٰ ۔ ،"),
                c("۱ ۲ ۳ ۴ ۵ ۶ ۷ ۸ ۹ ۰")));

        // -------------------------------------------------------------- Persian
        ALL.add(new Language("fa", "فارسی", "Persian", "فا", "فارسی", true,
                c("ض ص ث ق ف غ ع ه خ ح"),
                c("ش س ی ب ل ا ت ن م ک"),
                c("ظ ط ذ د ز ر و ژ گ پ"),
                c("آ أ ء ة ي ك ى ً ٌ ٍ"),
                c("َ ُ ِ ّ ْ ؛ ، ؟ « »"),
                c("۱ ۲ ۳ ۴ ۵ ۶ ۷ ۸ ۹ ۰")));

        // -------------------------------------------------------------- Russian
        ALL.add(new Language("ru", "Русский", "Russian", "Ру", "Русский", false,
                c("й ц у к е н г ш щ з х ъ"),
                c("ф ы в а п р о л д ж э"),
                c("я ч с м и т ь б ю"),
                c("Й Ц У К Е Н Г Ш Щ З Х Ъ"),
                c("Ф Ы В А П Р О Л Д Ж Э"),
                c("Я Ч С М И Т Ь Б Ю")));

        // ------------------------------------------------------------ Ukrainian
        ALL.add(new Language("uk", "Українська", "Ukrainian", "Ук", "Українська", false,
                c("й ц у к е н г ш щ з х ї"),
                c("ф і в а п р о л д ж є"),
                c("я ч с м и т ь б ю ґ"),
                c("Й Ц У К Е Н Г Ш Щ З Х Ї"),
                c("Ф І В А П Р О Л Д Ж Є"),
                c("Я Ч С М И Т Ь Б Ю Ґ")));

        // --------------------------------------------------------------- Greek
        ALL.add(new Language("el", "Ελληνικά", "Greek", "Ελ", "Ελληνικά", false,
                c("ς ε ρ τ υ θ ι ο π"),
                c("α σ δ φ γ η ξ κ λ"),
                c("ζ χ ψ ω β ν μ"),
                c("Σ Ε Ρ Τ Υ Θ Ι Ο Π"),
                c("Α Σ Δ Φ Γ Η Ξ Κ Λ"),
                c("Ζ Χ Ψ Ω Β Ν Μ")));

        // -------------------------------------------------------------- Hebrew
        ALL.add(new Language("he", "עברית", "Hebrew", "עב", "עברית", true,
                c("ק ר א ט ו ן ם פ"),
                c("ש ד ג כ ע י ח ל ך"),
                c("ז ס ב ה נ מ צ ת ץ"),
                c("ְ ִ ֵ ֶ ַ ָ ֹ ּ ֻ ֿ"),
                c("ף ץ ך ם ן ׳ ״ ־ ׀"),
                c("1 2 3 4 5 6 7 8 9 0")));

        // ---------------------------------------------------------------- Thai
        ALL.add(new Language("th", "ไทย", "Thai", "ไทย", "ไทย", false,
                c("ๆ ไ ำ พ ะ ั ี ร น ย"),
                c("ฟ ห ก ด เ ้ ่ า ส ว"),
                c("ง ผ ป แ อ ิ ื ท ม ใ"),
                c("ๅ / _ ภ ถ ุ ู ค ต จ ข"),
                c("ช ซ . ( ) ฉ ฮ ฺ ์ ๘"),
                c("๑ ๒ ๓ ๔ ๕ ๖ ๗ ๙ ๐ ฿")));

        // --------------------------------------------------------------- Tamil
        ALL.add(new Language("ta", "தமிழ்", "Tamil", "த", "தமிழ்", false,
                c("அ ஆ இ ஈ உ ஊ எ ஏ ஐ ஒ"),
                c("க ங ச ஞ ட ண த ந ப ம"),
                c("ய ர ல வ ழ ள ற ன"),
                c("ஓ ஔ ா ி ீ ு ூ ெ ே ை"),
                c("ொ ோ ௌ ் ஃ ஸ ஷ ஜ ஹ"),
                c("௦ ௧ ௨ ௩ ௪ ௫ ௬ ௭ ௮ ௯")));

        // -------------------------------------------------------------- Telugu
        ALL.add(new Language("te", "తెలుగు", "Telugu", "తె", "తెలుగు", false,
                c("అ ఆ ఇ ఈ ఉ ఊ ఋ ఎ ఏ ఐ"),
                c("క ఖ గ ఘ ఙ చ ఛ జ ఝ ఞ"),
                c("ట ఠ డ ఢ ణ త థ ద ధ న"),
                c("ఒ ఓ ఔ ా ి ీ ు ూ ృ ె"),
                c("ే ై ొ ో ౌ ్ ం ః ప ఫ"),
                c("బ భ మ య ర ల వ శ ష స")));

        // ------------------------------------------------------------- Kannada
        ALL.add(new Language("kn", "ಕನ್ನಡ", "Kannada", "ಕ", "ಕನ್ನಡ", false,
                c("ಅ ಆ ಇ ಈ ಉ ಊ ಋ ಎ ಏ ಐ"),
                c("ಕ ಖ ಗ ಘ ಙ ಚ ಛ ಜ ಝ ಞ"),
                c("ಟ ಠ ಡ ಢ ಣ ತ ಥ ದ ಧ ನ"),
                c("ಒ ಓ ಔ ಾ ಿ ೀ ು ೂ ೃ ೆ"),
                c("ೇ ೈ ೊ ೋ ೌ ್ ಂ ಃ ಪ ಫ"),
                c("ಬ ಭ ಮ ಯ ರ ಲ ವ ಶ ಷ ಸ")));

        // ----------------------------------------------------------- Malayalam
        ALL.add(new Language("ml", "മലയാളം", "Malayalam", "മ", "മലയാളം", false,
                c("അ ആ ഇ ഈ ഉ ഊ ഋ എ ഏ ഐ"),
                c("ക ഖ ഗ ഘ ങ ച ഛ ജ ഝ ഞ"),
                c("ട ഠ ഡ ഢ ണ ത ഥ ദ ധ ന"),
                c("ഒ ഓ ഔ ാ ി ീ ു ൂ ൃ െ"),
                c("േ ൈ ൊ ോ ൌ ് ം ഃ പ ഫ"),
                c("ബ ഭ മ യ ര ല വ ശ ഷ സ")));

        // ------------------------------------------------------------- Gujarati
        ALL.add(new Language("gu", "ગુજરાતી", "Gujarati", "ગુ", "ગુજરાતી", false,
                c("અ આ ઇ ઈ ઉ ઊ ઋ એ ઐ ઓ"),
                c("ક ખ ગ ઘ ઙ ચ છ જ ઝ ઞ"),
                c("ટ ઠ ડ ઢ ણ ત થ દ ધ ન"),
                c("ઔ ા િ ી ુ ૂ ૃ ે ૈ ો"),
                c("પ ફ બ ભ મ ય ર લ વ શ"),
                c("ષ સ હ ્ ં ઃ ઁ ૌ")));

        // -------------------------------------------------------------- Punjabi
        ALL.add(new Language("pa", "ਪੰਜਾਬੀ", "Punjabi", "ਪ", "ਪੰਜਾਬੀ", false,
                c("ਅ ਆ ਇ ਈ ਉ ਊ ਏ ਐ ਓ ਔ"),
                c("ਕ ਖ ਗ ਘ ਙ ਚ ਛ ਜ ਝ ਞ"),
                c("ਟ ਠ ਡ ਢ ਣ ਤ ਥ ਦ ਧ ਨ"),
                c("ੳ ੲ ਾ ਿ ੀ ੁ ੂ ੇ ੈ ੋ"),
                c("ਪ ਫ ਬ ਭ ਮ ਯ ਰ ਲ ਵ ਸ਼"),
                c("ਸ ਹ ੌ ੰ ੱ ੍ ਂ ਃ")));

        // ----------------------------------------------------------------- Odia
        ALL.add(new Language("or", "ଓଡ଼ିଆ", "Odia", "ଓ", "ଓଡ଼ିଆ", false,
                c("ଅ ଆ ଇ ଈ ଉ ଊ ଋ ଏ ଐ ଓ"),
                c("କ ଖ ଗ ଘ ଙ ଚ ଛ ଜ ଝ ଞ"),
                c("ଟ ଠ ଡ ଢ ଣ ତ ଥ ଦ ଧ ନ"),
                c("ଔ ା ି ୀ ୁ ୂ ୃ େ ୈ ୋ"),
                c("ପ ଫ ବ ଭ ମ ଯ ର ଲ ୱ ଶ"),
                c("ଷ ସ ହ ୍ ଂ ଃ ଁ ୌ")));

        // -------------------------------------------------------------- Sinhala
        ALL.add(new Language("si", "සිංහල", "Sinhala", "සි", "සිංහල", false,
                c("අ ආ ඇ ඈ ඉ ඊ උ ඌ එ ඒ"),
                c("ක ඛ ග ඝ ඞ ච ඡ ජ ඣ ඤ"),
                c("ට ඨ ඩ ඪ ණ ත ථ ද ධ න"),
                c("ඓ ඔ ඕ ඖ ා ැ ෑ ි ී ු"),
                c("ූ ෘ ෙ ේ ෛ ො ෝ ෞ ් ං"),
                c("ප ඵ බ භ ම ය ර ල ව ශ")));

        // ---------------------------------------------------- Latin with accents
        ALL.add(roman("es", "Español", "Spanish", "ES", "Español",
                "q w e r t y u i o p", "a s d f g h j k l ñ", "z x c v b n m"));
        ALL.add(roman("fr", "Français", "French", "FR", "Français",
                "a z e r t y u i o p", "q s d f g h j k l m", "w x c v b n é è ç à"));
        ALL.add(roman("de", "Deutsch", "German", "DE", "Deutsch",
                "q w e r t z u i o p ü", "a s d f g h j k l ö ä", "y x c v b n m ß"));
        ALL.add(roman("pt", "Português", "Portuguese", "PT", "Português",
                "q w e r t y u i o p", "a s d f g h j k l ç", "z x c v b n m ã õ á"));
        ALL.add(roman("it", "Italiano", "Italian", "IT", "Italiano",
                "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m à è é ì ò ù"));
        ALL.add(roman("nl", "Nederlands", "Dutch", "NL", "Nederlands",
                "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"));
        ALL.add(roman("tr", "Türkçe", "Turkish", "TR", "Türkçe",
                "q w e r t y u ı o p", "a s d f g h j k l ş", "z x c v b n m ç ö ü ğ"));
        ALL.add(roman("pl", "Polski", "Polish", "PL", "Polski",
                "q w e r t y u i o p", "a s d f g h j k l ł", "z x c v b n m ą ć ę"));
        ALL.add(roman("cs", "Čeština", "Czech", "CS", "Čeština",
                "q w e r t y u i o p", "a s d f g h j k l ů", "z x c v b n m á č ě ř š ž ý"));
        ALL.add(roman("ro", "Română", "Romanian", "RO", "Română",
                "q w e r t y u i o p", "a s d f g h j k l ș", "z x c v b n m ă â î ț"));
        ALL.add(roman("sv", "Svenska", "Swedish", "SV", "Svenska",
                "q w e r t y u i o p", "a s d f g h j k l ö ä", "z x c v b n m å"));
        ALL.add(roman("no", "Norsk", "Norwegian", "NO", "Norsk",
                "q w e r t y u i o p", "a s d f g h j k l ø æ", "z x c v b n m å"));
        ALL.add(roman("da", "Dansk", "Danish", "DA", "Dansk",
                "q w e r t y u i o p", "a s d f g h j k l æ ø", "z x c v b n m å"));
        ALL.add(roman("fi", "Suomi", "Finnish", "FI", "Suomi",
                "q w e r t y u i o p", "a s d f g h j k l ö ä", "z x c v b n m"));
        ALL.add(roman("vi", "Tiếng Việt", "Vietnamese", "VI", "Tiếng Việt",
                "q w e r t y u i o p", "a s d f g h j k l đ", "z x c v b n m ư ơ ă"));
        ALL.add(roman("id", "Indonesia", "Indonesian", "ID", "Indonesia",
                "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"));
        ALL.add(roman("ms", "Melayu", "Malay", "MS", "Melayu",
                "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"));
        ALL.add(roman("tl", "Filipino", "Filipino", "TL", "Filipino",
                "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"));
        ALL.add(roman("sw", "Kiswahili", "Swahili", "SW", "Kiswahili",
                "q w e r t y u i o p", "a s d f g h j k l", "z x c v b n m"));
    }

    private Languages() {
    }

    private static String[] c(String row) {
        return row.trim().split("\\s+");
    }

    private static Language roman(String code, String nativeName, String english,
                                  String shortLabel, String spaceLabel,
                                  String row1, String row2, String row3) {
        return new Language(code, nativeName, english, shortLabel, spaceLabel, false,
                c(row1), c(row2), c(row3), null, null, null);
    }

    static List<Language> all() {
        return ALL;
    }

    static String defaultEnabled() {
        return DEFAULT_ENABLED;
    }

    static Language byCode(String code) {
        if (code != null) {
            for (Language language : ALL) {
                if (language.code.equals(code)) {
                    return language;
                }
            }
        }
        return ALL.get(0);
    }

    /** Enabled languages in registry order; never empty. */
    static List<Language> enabled(String csv) {
        Set<String> wanted = new LinkedHashSet<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                String code = part.trim();
                if (!code.isEmpty()) {
                    wanted.add(code);
                }
            }
        }
        List<Language> list = new ArrayList<>();
        for (Language language : ALL) {
            if (wanted.contains(language.code)) {
                list.add(language);
            }
        }
        if (list.isEmpty()) {
            list.add(byCode("en"));
        }
        return list;
    }

    /** The next enabled language after {@code current}. */
    static Language next(String current, String csv) {
        List<Language> list = enabled(csv);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).code.equals(current)) {
                return list.get((i + 1) % list.size());
            }
        }
        return list.get(0);
    }
}
