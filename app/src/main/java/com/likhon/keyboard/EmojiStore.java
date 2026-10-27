package com.likhon.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Recently used emoji and favourites, keyed by code point hex string.
 *
 * <p>Kept apart from the sticker store so clearing one never touches the other.
 * Recents are capped; favourites are bounded by the catalogue size.
 */
final class EmojiStore {

    private static final String FILE = "likhon_emoji";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_RECENTS = "recents";
    private static final String SEPARATOR = ",";
    private static final int MAX_RECENT = 48;

    private final SharedPreferences sp;

    EmojiStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean isFavorite(String hex) {
        return favorites().contains(hex);
    }

    Set<String> favorites() {
        return parse(sp.getString(KEY_FAVORITES, ""));
    }

    /** @return true when the emoji is a favourite afterwards. */
    boolean toggleFavorite(String hex) {
        LinkedHashSet<String> ids = parse(sp.getString(KEY_FAVORITES, ""));
        boolean added;
        if (ids.contains(hex)) {
            ids.remove(hex);
            added = false;
        } else {
            ids.add(hex);
            added = true;
        }
        write(KEY_FAVORITES, ids);
        return added;
    }

    List<String> recents() {
        return new ArrayList<>(parse(sp.getString(KEY_RECENTS, "")));
    }

    void recordUse(String hex) {
        LinkedHashSet<String> previous = parse(sp.getString(KEY_RECENTS, ""));
        previous.remove(hex);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(hex);
        ids.addAll(previous);
        while (ids.size() > MAX_RECENT) {
            String last = null;
            for (String value : ids) {
                last = value;
            }
            if (last == null) {
                break;
            }
            ids.remove(last);
        }
        write(KEY_RECENTS, ids);
    }

    private void write(String key, Set<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            if (out.length() > 0) {
                out.append(SEPARATOR);
            }
            out.append(id);
        }
        sp.edit().putString(key, out.toString()).apply();
    }

    private static LinkedHashSet<String> parse(String raw) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }
        Collections.addAll(ids, raw.split(SEPARATOR));
        ids.remove("");
        return ids;
    }
}
