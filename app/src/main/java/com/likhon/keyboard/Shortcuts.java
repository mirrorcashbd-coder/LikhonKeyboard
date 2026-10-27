package com.likhon.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Text shortcuts: a short key that expands into a longer phrase, e.g.
 * {@code mail} → {@code likhon@example.com}. Stored as {@code key<TAB>value}
 * lines in the app's own preferences, so it survives restarts and updates.
 */
final class Shortcuts {

    private static final String FILE = "likhon_shortcuts";
    private static final String KEY = "pairs";

    private final SharedPreferences sp;

    Shortcuts(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Key → expansion, in the order they were added. */
    Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = sp.getString(KEY, "");
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String line : raw.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            String key = line.substring(0, tab).trim();
            String value = line.substring(tab + 1);
            if (!key.isEmpty() && !value.isEmpty()) {
                map.put(key, value);
            }
        }
        return map;
    }

    String get(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return all().get(key);
    }

    void put(String key, String value) {
        String cleanKey = key == null ? "" : key.trim();
        String cleanValue = value == null ? "" : value.trim();
        if (cleanKey.isEmpty() || cleanValue.isEmpty() || cleanKey.contains("\t")
                || cleanKey.contains("\n") || cleanValue.contains("\n")) {
            return;
        }
        Map<String, String> map = all();
        map.put(cleanKey, cleanValue);
        save(map);
    }

    void remove(String key) {
        Map<String, String> map = all();
        if (map.remove(key) != null) {
            save(map);
        }
    }

    int size() {
        return all().size();
    }

    private void save(Map<String, String> map) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            out.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
        }
        sp.edit().putString(KEY, out.toString()).apply();
    }
}
