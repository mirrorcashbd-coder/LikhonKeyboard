package com.likhon.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings, text shortcuts and clipboard history in one JSON file, so they can
 * move to a new phone.
 *
 * <p>Gson (installed in {@code libs/}) does the JSON work; every value is
 * written with a small type prefix so booleans, numbers and strings come back
 * exactly as they went in. A backup never contains typed text that was not in
 * the clipboard history, and nothing is uploaded anywhere: the file is written
 * to the place the user picks with the system file dialog.
 */
final class Backup {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SET_SEPARATOR = "\u0001";

    static final class Clip {
        String text;
        boolean pinned;
    }

    static final class Snapshot {
        int version = 1;
        String app = "Likhon Keyboard";
        long exportedAt;
        Map<String, String> settings = new LinkedHashMap<>();
        Map<String, String> shortcuts = new LinkedHashMap<>();
        List<Clip> clipboard = new ArrayList<>();
    }

    static final class Restored {
        int settings;
        int shortcuts;
        int clips;
    }

    private Backup() {
    }

    static String toJson(Context context) {
        Prefs prefs = new Prefs(context);
        Snapshot snapshot = new Snapshot();
        snapshot.exportedAt = System.currentTimeMillis() / 1000L;

        for (Map.Entry<String, ?> entry : prefs.raw().getAll().entrySet()) {
            String encoded = encode(entry.getValue());
            if (encoded != null) {
                snapshot.settings.put(entry.getKey(), encoded);
            }
        }
        snapshot.shortcuts.putAll(new Shortcuts(context).all());

        List<ClipStore.Entry> entries = new ClipStore(context).entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            ClipStore.Entry entry = entries.get(i);
            Clip clip = new Clip();
            clip.text = entry.text;
            clip.pinned = entry.pinned;
            snapshot.clipboard.add(clip);
        }
        return GSON.toJson(snapshot);
    }

    static Restored restore(Context context, String json) {
        Snapshot snapshot = GSON.fromJson(json, Snapshot.class);
        if (snapshot == null || snapshot.version < 1) {
            throw new IllegalArgumentException("not a Likhon backup");
        }
        Restored result = new Restored();
        Prefs prefs = new Prefs(context);
        SharedPreferences.Editor editor = prefs.raw().edit().clear();
        if (snapshot.settings != null) {
            for (Map.Entry<String, String> entry : snapshot.settings.entrySet()) {
                if (decode(editor, entry.getKey(), entry.getValue())) {
                    result.settings++;
                }
            }
        }
        editor.apply();

        Shortcuts shortcuts = new Shortcuts(context);
        shortcuts.clear();
        if (snapshot.shortcuts != null) {
            for (Map.Entry<String, String> entry : snapshot.shortcuts.entrySet()) {
                shortcuts.put(entry.getKey(), entry.getValue());
                result.shortcuts++;
            }
        }

        ClipStore store = new ClipStore(context);
        store.clear();
        if (snapshot.clipboard != null) {
            for (Clip clip : snapshot.clipboard) {
                if (clip == null || clip.text == null) {
                    continue;
                }
                if (store.add(clip.text)) {
                    result.clips++;
                }
                if (clip.pinned) {
                    store.setPinnedByText(clip.text, true);
                }
            }
        }
        return result;
    }

    private static String encode(Object value) {
        if (value instanceof Boolean) {
            return "b:" + value;
        }
        if (value instanceof Integer) {
            return "i:" + value;
        }
        if (value instanceof Long) {
            return "l:" + value;
        }
        if (value instanceof String) {
            return "s:" + value;
        }
        if (value instanceof Set) {
            StringBuilder out = new StringBuilder("t:");
            boolean first = true;
            for (Object item : (Set<?>) value) {
                if (!first) {
                    out.append(SET_SEPARATOR);
                }
                out.append(item);
                first = false;
            }
            return out.toString();
        }
        return null;
    }

    private static boolean decode(SharedPreferences.Editor editor, String key, String encoded) {
        if (key == null || encoded == null || encoded.length() < 2) {
            return false;
        }
        String value = encoded.substring(2);
        try {
            switch (encoded.charAt(0)) {
                case 'b':
                    editor.putBoolean(key, "true".equals(value));
                    return true;
                case 'i':
                    editor.putInt(key, Integer.parseInt(value));
                    return true;
                case 'l':
                    editor.putLong(key, Long.parseLong(value));
                    return true;
                case 's':
                    editor.putString(key, value);
                    return true;
                case 't':
                    Set<String> set = new java.util.LinkedHashSet<>();
                    if (!value.isEmpty()) {
                        for (String item : value.split(SET_SEPARATOR)) {
                            set.add(item);
                        }
                    }
                    editor.putStringSet(key, set);
                    return true;
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }
}
