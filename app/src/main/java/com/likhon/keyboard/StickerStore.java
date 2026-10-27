package com.likhon.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Favourites and the recently sent stickers, kept in a small preferences file.
 *
 * <p>Ids are stable strings such as {@code grin} or {@code red_heart}. The
 * lists never grow without bound: recents keep the last {@link #MAX_RECENT}
 * entries, favourites are only as long as the catalogue.
 */
final class StickerStore {

    private static final String FILE = "likhon_stickers";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_RECENTS = "recents";
    private static final String SEPARATOR = ",";
    private static final int MAX_RECENT = 60;

    private final SharedPreferences sp;

    StickerStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    boolean isFavorite(String id) {
        return favorites().contains(id);
    }

    Set<String> favorites() {
        return parse(sp.getString(KEY_FAVORITES, ""));
    }

    /** @return true when the sticker is a favourite afterwards. */
    boolean toggleFavorite(String id) {
        LinkedHashSet<String> ids = parse(sp.getString(KEY_FAVORITES, ""));
        boolean added;
        if (ids.contains(id)) {
            ids.remove(id);
            added = false;
        } else {
            ids.add(id);
            added = true;
        }
        write(KEY_FAVORITES, ids);
        return added;
    }

    List<String> recents() {
        return new ArrayList<>(parse(sp.getString(KEY_RECENTS, "")));
    }

    /** Moves a sticker to the front of the recents list. */
    void recordUse(String id) {
        LinkedHashSet<String> previous = parse(sp.getString(KEY_RECENTS, ""));
        previous.remove(id);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(id);
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

    void clearRecents() {
        sp.edit().remove(KEY_RECENTS).apply();
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
