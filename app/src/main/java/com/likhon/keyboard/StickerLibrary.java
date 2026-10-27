package com.likhon.keyboard;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The sticker catalogue that ships inside the APK.
 *
 * <p>Artwork lives in {@code assets/stickers/<pack>/<id>.webp} and the names,
 * packs and search tags are described by {@code assets/stickers/stickers.json}.
 * Everything is bundled, so search and browsing work without a network.
 */
final class StickerLibrary {

    /** One sticker. Fields mirror the JSON file. */
    static final class Item {
        String id;
        String file;
        String en;
        String bn;
        List<String> tags;

        /** Filled while loading: the pack this sticker belongs to. */
        String pack;
        /** Lower-case haystack used by {@link #search(String)}. */
        String haystack;

        String name(boolean bangla) {
            return bangla && bn != null && bn.length() > 0 ? bn : en;
        }
    }

    /** A named group of stickers ("Moods", "Food", …). */
    static final class Pack {
        String id;
        String en;
        String bn;
        List<Item> items;

        String name(boolean bangla) {
            return bangla && bn != null && bn.length() > 0 ? bn : en;
        }
    }

    private static final class Catalogue {
        int version;
        List<Pack> packs;
    }

    private static StickerLibrary instance;

    private final List<Pack> packs = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private final LruCache<String, Bitmap> cache = new LruCache<>(28);
    /** Application assets; holding them cannot leak an Activity. */
    private final AssetManager assets;

    static synchronized StickerLibrary get(Context context) {
        if (instance == null) {
            instance = new StickerLibrary(context.getApplicationContext());
        }
        return instance;
    }

    private StickerLibrary(Context context) {
        assets = context.getApplicationContext().getAssets();
        Catalogue catalogue = null;
        try (InputStream in = assets.open("stickers/stickers.json")) {
            catalogue = new Gson().fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), Catalogue.class);
        } catch (IOException | RuntimeException ignored) {
            // A missing catalogue simply means an empty sticker panel.
        }
        if (catalogue == null || catalogue.packs == null) {
            return;
        }
        for (Pack pack : catalogue.packs) {
            if (pack == null || pack.id == null || pack.items == null) {
                continue;
            }
            String packWords = safe(pack.en) + " " + safe(pack.bn) + " "
                    + Romanize.toLatin(safe(pack.bn));
            for (Item item : pack.items) {
                if (item == null || item.id == null || item.file == null) {
                    continue;
                }
                item.pack = pack.id;
                String tags = item.tags == null ? "" : join(item.tags);
                // The romanised Bangla name is searchable too, so someone typing
                // "bhalobasha" finds ভালোবাসা without switching language.
                item.haystack = (safe(item.en) + " " + safe(item.bn) + " "
                        + Romanize.toLatin(safe(item.bn)) + " " + tags + " "
                        + packWords).toLowerCase(Locale.ROOT);
                items.add(item);
            }
            packs.add(pack);
        }
    }

    private static String join(List<String> parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part != null && part.length() > 0) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(part);
            }
        }
        return out.toString();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    List<Pack> packs() {
        return Collections.unmodifiableList(packs);
    }

    List<Item> items() {
        return Collections.unmodifiableList(items);
    }

    Pack pack(String id) {
        for (Pack pack : packs) {
            if (pack.id.equals(id)) {
                return pack;
            }
        }
        return null;
    }

    Item item(String packId, String itemId) {
        Pack pack = pack(packId);
        if (pack == null) {
            return null;
        }
        for (Item item : pack.items) {
            if (item.id.equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    Item itemById(String itemId) {
        for (Item item : items) {
            if (item.id.equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    /** All stickers of one pack, in catalogue order. */
    List<Item> ofPack(String packId) {
        Pack pack = pack(packId);
        if (pack == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(pack.items);
    }

    /**
     * Free-text search over English names, Bangla names, pack names and tags.
     * Every word of the query has to match, so "লাল heart" works too.
     */
    List<Item> search(String query) {
        String trimmed = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return items();
        }
        String[] words = trimmed.split("\\s+");
        List<Item> matches = new ArrayList<>();
        for (Item item : items) {
            boolean all = true;
            for (String word : words) {
                if (!item.haystack.contains(word)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                matches.add(item);
            }
        }
        return matches;
    }

    /** Stickers for a list of ids, skipping unknown ones. */
    List<Item> byIds(List<String> ids) {
        List<Item> result = new ArrayList<>();
        for (String id : ids) {
            Item item = itemById(id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    /** Decoded sticker art, cached in memory. */
    Bitmap bitmap(Item item) {
        return bitmap(item, 0);
    }

    /**
     * Decoded sticker art at a power-of-two sample size (0 = full 192px).
     * Small sizes keep the settings screen light on memory.
     */
    Bitmap bitmap(Item item, int sampleSize) {
        if (item == null || item.file == null) {
            return null;
        }
        String key = item.file + "#" + sampleSize;
        Bitmap cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        AssetManager assets = this.assets;
        if (assets == null) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        if (sampleSize > 1) {
            options.inSampleSize = sampleSize;
        }
        try (InputStream in = assets.open("stickers/" + item.file)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            if (bitmap != null) {
                cache.put(key, bitmap);
            }
            return bitmap;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
