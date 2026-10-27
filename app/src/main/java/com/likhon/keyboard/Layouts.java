package com.likhon.keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * All keyboard layouts. Everything is built in code (no XML inflation) so the
 * layout can react instantly to shift, language and page changes.
 */
final class Layouts {

    static final int PAGE_LETTERS = 0;
    static final int PAGE_SYMBOLS = 1;
    static final int PAGE_MORE = 2;
    static final int PAGE_EMOJI = 3;
    static final int PAGE_NUMERIC = 4;

    private static final String[] ROW1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
    private static final String[] ROW2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
    private static final String[] ROW3 = {"z", "x", "c", "v", "b", "n", "m"};

    /** Bangla letters shown as small hints on the Latin keys. */
    private static final String[] HINTS_LOWER = {
            "ক", "ও", "এ", "র", "ত", "য", "উ", "ই", "অ", "প",
            "আ", "স", "দ", "ফ", "গ", "হ", "জ", "ক", "ল",
            "জ", "ক্স", "চ", "ভ", "ব", "ন", "ম"
    };
    private static final String[] HINTS_UPPER = {
            "ক", "ও", "এ", "ড়", "ট", "য়", "ঊ", "ঈ", "ও", "প",
            "আ", "ষ", "ড", "ফ", "গ", "হ", "ঝ", "খ", "ল",
            "জ", "ক্স", "চ", "ভ", "ব", "ণ", "ম"
    };

    static final class Row {
        final float inset;
        final Key[] keys;

        Row(float inset, Key... keys) {
            this.inset = inset;
            this.keys = keys;
        }
    }

    private Layouts() {
    }

    static List<Row> build(int page, Language lang, boolean shift, boolean caps, boolean hints,
                           boolean numberRow, String enterLabel, String spaceLabel, int emojiPage) {
        switch (page) {
            case PAGE_SYMBOLS:
                return symbols(lang, enterLabel, spaceLabel, false);
            case PAGE_MORE:
                return symbols(lang, enterLabel, spaceLabel, true);
            case PAGE_EMOJI:
                return emoji(emojiPage, spaceLabel, enterLabel);
            case PAGE_NUMERIC:
                return numeric(enterLabel, spaceLabel);
            case PAGE_LETTERS:
            default:
                return letters(lang, shift, caps, hints, numberRow, enterLabel, spaceLabel);
        }
    }

    // ------------------------------------------------------------------ letters

    private static List<Row> letters(Language lang, boolean shift, boolean caps, boolean hints,
                                     boolean numberRow, String enterLabel, String spaceLabel) {
        List<Row> rows = new ArrayList<>(5);
        if (numberRow) {
            rows.add(new Row(0f,
                    Key.ch("1", 1f), Key.ch("2", 1f), Key.ch("3", 1f), Key.ch("4", 1f),
                    Key.ch("5", 1f), Key.ch("6", 1f), Key.ch("7", 1f), Key.ch("8", 1f),
                    Key.ch("9", 1f), Key.ch("0", 1f)));
        }

        boolean banglaPhonetic = lang.isPhoneticBangla();
        if (lang.usesOwnLetters()) {
            rows.add(new Row(0f, scriptRow(lang, 0, shift)));
            rows.add(new Row(0.3f, scriptRow(lang, 1, shift)));

            String[] third = lang.row(2, shift);
            Key[] keys = new Key[third.length + 2];
            keys[0] = Key.special(Key.SHIFT, caps ? "caps" : "shift", 1.3f, shift || caps);
            for (int i = 0; i < third.length; i++) {
                keys[i + 1] = Key.ch(cased(lang, third[i], shift), 1f);
            }
            keys[keys.length - 1] = Key.special(Key.DELETE, "delete", 1.3f, false);
            rows.add(new Row(0f, keys));
        } else {
            rows.add(new Row(0f, letterRow(ROW1, 0, shift, banglaPhonetic, hints)));
            rows.add(new Row(0.5f, letterRow(ROW2, 10, shift, banglaPhonetic, hints)));

            Key[] third = new Key[9];
            third[0] = Key.special(Key.SHIFT, caps ? "caps" : "shift", 1.5f, shift || caps);
            Key[] rest = letterRow(ROW3, 19, shift, banglaPhonetic, hints);
            System.arraycopy(rest, 0, third, 1, rest.length);
            third[8] = Key.special(Key.DELETE, "delete", 1.5f, false);
            rows.add(new Row(0f, third));
        }

        String period = "bn_bd".equals(lang.code) ? "।" : ".";
        rows.add(new Row(0f,
                Key.special(Key.SYMBOLS, "?123", 1.3f, false),
                Key.special(Key.LANGUAGE, lang.shortLabel, 1.3f, !"en".equals(lang.code)),
                Key.ch(",", 0.85f),
                Key.special(Key.SPACE, spaceLabel, 3.3f, false),
                Key.ch(period, 0.85f),
                Key.special(Key.MIC, "mic", 0.9f, false),
                enterKey(enterLabel, 1.3f)));
        return rows;
    }

    /** One row of a script language: every character is its own key. */
    private static Key[] scriptRow(Language lang, int index, boolean shift) {
        String[] letters = lang.row(index, shift);
        Key[] keys = new Key[letters.length];
        for (int i = 0; i < letters.length; i++) {
            keys[i] = Key.ch(cased(lang, letters[i], shift), 1f);
        }
        return keys;
    }

    /** Latin languages have no shifted rows: shift simply upper-cases. */
    private static String cased(Language lang, String text, boolean shift) {
        return shift && lang.hasCase() ? text.toUpperCase() : text;
    }

    private static Key[] letterRow(String[] letters, int hintOffset, boolean shift,
                                   boolean bangla, boolean hints) {
        Key[] keys = new Key[letters.length];
        for (int i = 0; i < letters.length; i++) {
            String lower = letters[i];
            String label = shift ? lower.toUpperCase() : lower;
            String hint = null;
            if (bangla && hints) {
                hint = shift ? HINTS_UPPER[hintOffset + i] : HINTS_LOWER[hintOffset + i];
            }
            keys[i] = Key.ch(label, hint, 1f);
        }
        return keys;
    }

    // ------------------------------------------------------------------ symbols

    private static List<Row> symbols(Language lang, String enterLabel, String spaceLabel, boolean more) {
        boolean bangla = lang.code.startsWith("bn");
        List<Row> rows = new ArrayList<>(4);
        if (!more) {
            rows.add(new Row(0f,
                    Key.ch("1", 1f), Key.ch("2", 1f), Key.ch("3", 1f), Key.ch("4", 1f), Key.ch("5", 1f),
                    Key.ch("6", 1f), Key.ch("7", 1f), Key.ch("8", 1f), Key.ch("9", 1f), Key.ch("0", 1f)));
            rows.add(new Row(0f,
                    Key.ch("@", 1f), Key.ch("#", 1f), Key.ch("$", 1f), Key.ch("%", 1f), Key.ch("&", 1f),
                    Key.ch("*", 1f), Key.ch("-", 1f), Key.ch("+", 1f), Key.ch("(", 1f), Key.ch(")", 1f)));
            rows.add(new Row(0f,
                    Key.special(Key.MORE, "=\\<", 1.5f, false),
                    Key.ch("\"", 1f), Key.ch("'", 1f), Key.ch(":", 1f), Key.ch("!", 1f),
                    Key.ch("?", 1f), Key.ch("/", 1f),
                    bangla ? Key.ch("্", 1f) : Key.ch("=", 1f),
                    Key.special(Key.DELETE, "delete", 1.5f, false)));
        } else {
            rows.add(new Row(0f,
                    Key.ch("~", 1f), Key.ch("`", 1f), Key.ch("|", 1f), Key.ch("•", 1f), Key.ch("√", 1f),
                    Key.ch("π", 1f), Key.ch("÷", 1f), Key.ch("×", 1f), Key.ch("¶", 1f), Key.ch("∆", 1f)));
            rows.add(new Row(0f,
                    Key.ch("£", 1f), Key.ch("¢", 1f), Key.ch("€", 1f), Key.ch("¥", 1f), Key.ch("°", 1f),
                    Key.ch("=", 1f), Key.ch("{", 1f), Key.ch("}", 1f), Key.ch("\\", 1f), Key.ch("_", 1f)));
            rows.add(new Row(0f,
                    Key.special(Key.SYMBOLS, "?123", 1.5f, false),
                    Key.ch("©", 1f), Key.ch("®", 1f), Key.ch("™", 1f), Key.ch("✓", 1f),
                    Key.ch("[", 1f), Key.ch("]", 1f), Key.ch("<", 1f), Key.ch(">", 1f),
                    Key.special(Key.DELETE, "delete", 1.5f, false)));
        }
        rows.add(symbolBottom(lang, enterLabel, spaceLabel));
        return rows;
    }

    private static Row symbolBottom(Language lang, String enterLabel, String spaceLabel) {
        return new Row(0f,
                Key.special(Key.LETTERS, "ABC", 1.4f, false),
                Key.special(Key.LANGUAGE, lang.shortLabel, 1.4f, !"en".equals(lang.code)),
                Key.special(Key.EMOJI, "emoji", 0.9f, false),
                Key.special(Key.SPACE, spaceLabel, 3.3f, false),
                Key.ch(",", 0.9f),
                Key.special(Key.MIC, "mic", 0.9f, false),
                enterKey(enterLabel, 1.4f));
    }

    // -------------------------------------------------------------------- emoji

    private static final String[][] EMOJI_PAGES = {
            {
                    "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
                    "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
                    "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪"
            },
            {
                    "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
                    "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
                    "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕"
            },
            {
                    "🤢", "🤮", "🤧", "🥵", "🥶", "😵", "🤯", "🤠",
                    "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮",
                    "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰"
            },
            {
                    "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓",
                    "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈",
                    "👿", "💀", "💩", "🤡", "👹", "👺", "👻", "👽"
            },
            {
                    "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😽",
                    "🙀", "😿", "😾", "🙈", "🙉", "🙊", "💋", "💌",
                    "💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟"
            }
    };

    static int emojiPageCount() {
        return EMOJI_PAGES.length;
    }

    private static List<Row> emoji(int emojiPage, String spaceLabel, String enterLabel) {
        int page = ((emojiPage % EMOJI_PAGES.length) + EMOJI_PAGES.length) % EMOJI_PAGES.length;
        String[] emoji = EMOJI_PAGES[page];
        List<Row> rows = new ArrayList<>(4);
        for (int row = 0; row < 3; row++) {
            Key[] keys = new Key[8];
            for (int col = 0; col < 8; col++) {
                keys[col] = Key.ch(emoji[row * 8 + col], 1f);
            }
            rows.add(new Row(0f, keys));
        }
        rows.add(new Row(0f,
                Key.special(Key.PREV, "prev", 1.0f, false),
                Key.special(Key.LETTERS, "ABC", 1.3f, false),
                Key.special(Key.SPACE, spaceLabel, 3.4f, false),
                Key.special(Key.DELETE, "delete", 1.3f, false),
                enterKey(enterLabel, 1.3f),
                Key.special(Key.NEXT, "next", 1.0f, false)));
        return rows;
    }

    // ------------------------------------------------------------------ numeric

    private static List<Row> numeric(String enterLabel, String spaceLabel) {
        List<Row> rows = new ArrayList<>(4);
        rows.add(new Row(0f,
                Key.ch("1", 1f), Key.ch("2", 1f), Key.ch("3", 1f),
                Key.special(Key.DELETE, "delete", 1.4f, false)));
        rows.add(new Row(0f,
                Key.ch("4", 1f), Key.ch("5", 1f), Key.ch("6", 1f), Key.ch(".", 1.4f)));
        rows.add(new Row(0f,
                Key.ch("7", 1f), Key.ch("8", 1f), Key.ch("9", 1f), Key.ch(",", 1.4f)));
        rows.add(new Row(0f,
                Key.ch("+", 1f), Key.ch("0", 1f), Key.ch("-", 1f),
                Key.special(Key.SPACE, spaceLabel, 2.6f, false),
                enterKey(enterLabel, 1.4f)));
        return rows;
    }

    private static Key enterKey(String label, float weight) {
        return Key.special(Key.ENTER, label == null ? "" : label, weight, false);
    }
}
