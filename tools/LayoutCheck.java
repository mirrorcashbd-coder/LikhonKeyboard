package com.likhon.keyboard;

import java.util.List;

public final class LayoutCheck {
    public static void main(String[] args) {
        int pages = 0;
        for (Language l : Languages.all()) {
            for (boolean shift : new boolean[]{false, true}) {
                List<Layouts.Row> rows = Layouts.build(Layouts.PAGE_LETTERS, l, shift, false,
                        true, true, "Enter", l.spaceLabel, 0);
                if (rows.size() < 4) {
                    throw new IllegalStateException(l.code + " only " + rows.size() + " rows");
                }
                for (Layouts.Row row : rows) {
                    for (Key k : row.keys) {
                        if (k == null || k.label == null) {
                            throw new IllegalStateException(l.code + " null key");
                        }
                        if (k.isChar() && k.label.trim().isEmpty()) {
                            throw new IllegalStateException(l.code + " empty char key");
                        }
                    }
                }
                pages++;
            }
        }
        // Unicode sanity: every Bangla letter that matters is reachable.
        String bangla = "";
        for (boolean shift : new boolean[]{false, true}) {
            for (Layouts.Row row : Layouts.build(Layouts.PAGE_LETTERS,
                    Languages.byCode("bn_bd"), shift, false, false, false, "Enter", "বাংলা", 0)) {
                for (Key k : row.keys) {
                    if (k.isChar()) {
                        bangla += k.label;
                    }
                }
            }
        }
        for (String want : new String[]{"অ", "আ", "ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ",
                "ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন", "প", "ফ", "ব", "ভ",
                "ম", "য", "র", "ল", "শ", "ষ", "স", "হ", "ড়", "ঢ়", "য়", "ৎ", "ং", "ঃ",
                "ঁ", "্", "া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো", "ৌ", "৳", "।"}) {
            if (!bangla.contains(want)) {
                throw new IllegalStateException("missing Bangla " + want);
            }
        }
        System.out.println("layout check ok: " + pages + " language pages, Bangla complete");
    }
}
