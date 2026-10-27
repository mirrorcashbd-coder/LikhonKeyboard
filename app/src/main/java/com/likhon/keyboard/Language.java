package com.likhon.keyboard;

/**
 * One keyboard language. Latin languages keep the familiar QWERTY order and
 * simply add their own letters; script languages carry three rows of their own
 * alphabet, plus an optional shifted set for matras, signs and extra letters.
 */
final class Language {

    final String code;
    final String nativeName;
    final String english;
    /** Two or three characters on the language key, e.g. EN, বাং, हि. */
    final String shortLabel;
    /** Word printed on the space bar. */
    final String spaceLabel;
    final boolean rtl;

    final String[] row1;
    final String[] row2;
    final String[] row3;
    /** Shifted rows; null means "same letters, upper case" (Latin). */
    final String[] shift1;
    final String[] shift2;
    final String[] shift3;

    Language(String code, String nativeName, String english, String shortLabel,
             String spaceLabel, boolean rtl,
             String[] row1, String[] row2, String[] row3,
             String[] shift1, String[] shift2, String[] shift3) {
        this.code = code;
        this.nativeName = nativeName;
        this.english = english;
        this.shortLabel = shortLabel;
        this.spaceLabel = spaceLabel;
        this.rtl = rtl;
        this.row1 = row1;
        this.row2 = row2;
        this.row3 = row3;
        this.shift1 = shift1;
        this.shift2 = shift2;
        this.shift3 = shift3;
    }

    /** Plain QWERTY: shift just upper-cases the letters. */
    static Language latin(String code, String nativeName, String english,
                          String shortLabel, String spaceLabel) {
        return new Language(code, nativeName, english, shortLabel, spaceLabel, false,
                null, null, null, null, null, null);
    }

    boolean usesOwnLetters() {
        return row1 != null;
    }

    boolean isPhoneticBangla() {
        return "bn".equals(code);
    }

    boolean hasCase() {
        return shift1 == null;
    }

    /** Suggestions only make sense where a dictionary exists. */
    boolean suggests() {
        return "en".equals(code) || "bn".equals(code);
    }

    /** The row to show for the current shift state. */
    String[] row(int index, boolean shifted) {
        if (index == 0) {
            return shifted && shift1 != null ? shift1 : row1;
        }
        if (index == 1) {
            return shifted && shift2 != null ? shift2 : row2;
        }
        return shifted && shift3 != null ? shift3 : row3;
    }
}
