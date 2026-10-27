package com.likhon.keyboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serves a sticker to other apps as a {@code content://} image.
 *
 * <p>Android keyboards cannot put a picture into an editor directly. They hand
 * over a content URI with {@code InputConnection.commitContent} (or the
 * clipboard) and the receiving app reads it through this provider. The file is
 * copied from the APK's assets to the cache on first use, so a sticker can be
 * shared with any app that accepts images, and nothing is uploaded.
 */
public final class StickerProvider extends ContentProvider {

    static final String AUTHORITY = "com.likhon.keyboard.stickers";
    private static final String MIME = "image/webp";

    /** Public URI for a sticker, e.g. {@code content://…/moods/grin}. */
    static Uri uriFor(StickerLibrary.Item item) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(item.pack)
                .appendPath(item.id)
                .build();
    }

    /**
     * Copies the sticker out of the assets if needed and returns the file the
     * provider serves. Safe to call on the UI thread: the files are ~8 KB and
     * are only copied once.
     */
    static File fileFor(Context context, StickerLibrary.Item item) throws IOException {
        File dir = new File(context.getCacheDir(), "stickers");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        File out = new File(dir, item.pack + "-" + item.id + ".webp");
        if (out.length() > 0) {
            return out;
        }
        try (InputStream in = context.getAssets().open("stickers/" + item.file);
             OutputStream stream = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                stream.write(buffer, 0, read);
            }
        }
        return out;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return MIME;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        if (uri == null || uri.getPathSegments().size() < 2) {
            throw new java.io.FileNotFoundException("Unknown sticker " + uri);
        }
        String pack = uri.getPathSegments().get(0);
        String id = uri.getPathSegments().get(1);
        StickerLibrary library = StickerLibrary.get(getContext());
        StickerLibrary.Item item = library.item(pack, id);
        if (item == null) {
            throw new java.io.FileNotFoundException("Unknown sticker " + uri);
        }
        try {
            return ParcelFileDescriptor.open(fileFor(getContext(), item),
                    ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            throw new java.io.FileNotFoundException("Missing sticker " + uri);
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws java.io.FileNotFoundException {
        ParcelFileDescriptor descriptor = openFile(uri, mode);
        return new AssetFileDescriptor(descriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
