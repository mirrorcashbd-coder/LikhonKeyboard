package com.likhon.keyboard;

/**
 * A single key on the keyboard. Keys are immutable so a whole layout can be
 * rebuilt cheaply when shift, language or page changes.
 */
final class Key {

    static final int CHAR = 0;
    static final int SHIFT = 1;
    static final int DELETE = 2;
    static final int ENTER = 3;
    static final int SPACE = 4;
    static final int SYMBOLS = 5;
    static final int LETTERS = 6;
    static final int LANGUAGE = 7;
    static final int EMOJI = 8;
    static final int PREV = 9;
    static final int NEXT = 10;
    static final int MORE = 11;

    final int type;
    /** What is drawn on the key. Also the text committed for CHAR keys. */
    final String label;
    /** Text committed for CHAR keys. Equal to label unless overridden. */
    final String output;
    /** Small secondary label (Bangla hint in phonetic mode). May be null. */
    final String hint;
    final float weight;
    /** Draws the key in the accent colour (shift on, language selected). */
    final boolean active;

    Key(int type, String label, String output, String hint, float weight, boolean active) {
        this.type = type;
        this.label = label;
        this.output = output;
        this.hint = hint;
        this.weight = weight;
        this.active = active;
    }

    boolean isChar() {
        return type == CHAR;
    }

    boolean isSpecial() {
        return type != CHAR;
    }

    static Key ch(String text, float weight) {
        return new Key(CHAR, text, text, null, weight, false);
    }

    static Key ch(String text, String hint, float weight) {
        return new Key(CHAR, text, text, hint, weight, false);
    }

    static Key special(int type, String label, float weight) {
        return new Key(type, label, "", null, weight, false);
    }

    static Key special(int type, String label, float weight, boolean active) {
        return new Key(type, label, "", null, weight, active);
    }
}
