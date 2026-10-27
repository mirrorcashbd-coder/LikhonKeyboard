package com.likhon.keyboard;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The full Unicode emoji catalogue: every standard emoji, in the OpenMoji
 * artwork, with English and Bengali names and keywords.
 *
 * <p>The catalogue is a tab-separated file built by
 * {@code tools/build-emoji.py}. Loading is done once, lazily, on whichever
 * thread opens the studio first; every later lookup is a memory read. Artwork
 * is decoded on demand and kept in a small bitmap cache, so opening the studio
 * never allocates thousands of bitmaps.
 */
final class EmojiLibrary {

    static final class Item {
        final String hex;
        final String emoji;
        final String group;
        final boolean art;
        final String name;
        final String bnName;
        final String search;
        final int order;

        Item(String hex, String emoji, String group, boolean art, int order,
             String name, String tags, String bnName, String bnTags) {
            this.hex = hex;
            this.emoji = emoji;
            this.group = group;
            this.art = art;
            this.order = order;
            this.name = name;
            this.bnName = bnName;
            this.search = searchBlob(name, tags, bnName, bnTags);
        }

        String label(boolean banglaUi) {
            if (banglaUi && !bnName.isEmpty()) {
                return bnName;
            }
            return name;
        }
    }

    static final class Group {
        final String slug;
        final int labelRes;
        final int accent;

        Group(String slug, int labelRes, int accent) {
            this.slug = slug;
            this.labelRes = labelRes;
            this.accent = accent;
        }
    }

    /** Order matches tools/build-emoji.py and the emoji_group_* strings. */
    static final Group[] GROUPS = {
            new Group("smileys-emotion", R.string.emoji_group_smileys, 0xFFFFB300),
            new Group("people-body", R.string.emoji_group_people, 0xFFEC407A),
            new Group("animals-nature", R.string.emoji_group_animals, 0xFF66BB6A),
            new Group("food-drink", R.string.emoji_group_food, 0xFFFF7043),
            new Group("travel-places", R.string.emoji_group_travel, 0xFF42A5F5),
            new Group("activities", R.string.emoji_group_activities, 0xFFAB47BC),
            new Group("objects", R.string.emoji_group_objects, 0xFF26A69A),
            new Group("symbols", R.string.emoji_group_symbols, 0xFF7E57C2),
            new Group("flags", R.string.emoji_group_flags, 0xFFEF5350),
    };

    private static EmojiLibrary instance;

    final List<Item> all = new ArrayList<>();
    private final Map<String, Item> byHex = new HashMap<>();
    private final Map<String, List<Item>> byGroup = new LinkedHashMap<>();
    private final LruCache<String, Bitmap> bitmaps;

    static synchronized EmojiLibrary get(Context context) {
        if (instance == null) {
            instance = new EmojiLibrary(context.getApplicationContext());
        }
        return instance;
    }

    private EmojiLibrary(Context context) {
        AssetManager assets = context.getAssets();
        int cacheKb = (int) Math.min(12 * 1024,
                Math.max(3 * 1024, Runtime.getRuntime().maxMemory() / 1024 / 48));
        bitmaps = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        try (InputStream in = assets.open("emoji/catalog.tsv");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 8) {
                    continue;
                }
                String group = parts[2];
                if (!byGroup.containsKey(group)) {
                    // Ignore a group the UI does not know about.
                    boolean known = false;
                    for (Group candidate : GROUPS) {
                        if (candidate.slug.equals(group)) {
                            known = true;
                            break;
                        }
                    }
                    if (!known) {
                        continue;
                    }
                    byGroup.put(group, new ArrayList<>());
                }
                Item item = new Item(parts[0], parts[1], group, "1".equals(parts[3]),
                        all.size(), parts[4], parts[5], parts[6], parts[7]);
                all.add(item);
                byHex.put(item.hex, item);
                byGroup.get(group).add(item);
            }
        } catch (IOException ignored) {
            // A broken asset leaves the studio empty rather than crashing the IME.
        }
    }

    List<Item> group(String slug) {
        List<Item> items = byGroup.get(slug);
        return items == null ? Collections.emptyList() : items;
    }

    List<Item> byHexes(List<String> hexes) {
        List<Item> out = new ArrayList<>(hexes.size());
        for (String hex : hexes) {
            Item item = byHex.get(hex);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    Item get(String hex) {
        return byHex.get(hex);
    }

    /**
     * Matches every word in the query against the English and Bengali names and
     * keywords, then puts the closest names first. The full Unicode set is
     * searched, not just the visible group.
     */
    List<Item> search(String query) {
        String needle = normalize(query);
        if (needle.isEmpty()) {
            return Collections.emptyList();
        }
        String[] words = needle.split(" ");
        List<Scored> hits = new ArrayList<>();
        for (Item item : all) {
            int score = score(item, needle, words);
            if (score >= 0) {
                hits.add(new Scored(item, score));
            }
        }
        Collections.sort(hits, (a, b) -> {
            int byScore = Integer.compare(a.score, b.score);
            return byScore != 0 ? byScore : Integer.compare(a.item.order, b.item.order);
        });
        List<Item> out = new ArrayList<>(hits.size());
        for (Scored hit : hits) {
            out.add(hit.item);
        }
        return out;
    }

    private static int score(Item item, String needle, String[] words) {
        String name = normalize(item.name);
        String bnName = normalize(item.bnName);
        int base;
        if (name.equals(needle) || bnName.equals(needle)) {
            base = 0;
        } else if (name.startsWith(needle)) {
            base = 1;
        } else if (bnName.startsWith(needle)) {
            base = 2;
        } else if (name.contains(needle)) {
            base = 3;
        } else if (bnName.contains(needle)) {
            base = 4;
        } else {
            base = 5;
        }
        for (String word : words) {
            if (!item.search.contains(word)) {
                return -1;
            }
        }
        return base;
    }

    private static String searchBlob(String name, String tags, String bnName, String bnTags) {
        StringBuilder out = new StringBuilder(name.length() + tags.length()
                + bnName.length() + bnTags.length() + 8);
        out.append(normalize(name)).append(' ')
                .append(normalize(tags)).append(' ')
                .append(normalize(bnName)).append(' ')
                .append(normalize(bnTags));
        return out.toString();
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean space = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == ',' || c == '|' || c == ';' || c == '/' || Character.isWhitespace(c)) {
                if (!space && out.length() > 0) {
                    out.append(' ');
                    space = true;
                }
            } else {
                out.append(c);
                space = false;
            }
        }
        if (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    /** Decoded OpenMoji artwork, or null when the emoji has no image. */
    Bitmap bitmap(Context context, Item item) {
        if (!item.art) {
            return null;
        }
        Bitmap cached = bitmaps.get(item.hex);
        if (cached != null) {
            return cached;
        }
        try (InputStream in = context.getAssets().open("emoji/" + item.hex + ".png")) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap != null) {
                bitmaps.put(item.hex, bitmap);
            }
            return bitmap;
        } catch (IOException | OutOfMemoryError e) {
            return null;
        }
    }

    private static final class Scored {
        final Item item;
        final int score;

        Scored(Item item, int score) {
            this.item = item;
            this.score = score;
        }
    }
}
