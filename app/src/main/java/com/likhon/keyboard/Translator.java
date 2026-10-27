package com.likhon.keyboard;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Small Bangla ↔ English translator for the keyboard.
 *
 * <p>It asks the public Google Translate endpoint that web pages use. Only the
 * text the user chooses to translate is sent, never the rest of the field, and
 * nothing is sent unless the translate button is pressed. Android's built-in
 * {@code org.json} parses the reply, so there is still no third-party library.
 */
final class Translator {

    interface Callback {
        /** Exactly one of {@code translated} and {@code error} is non-null. */
        void onTranslated(String translated, String error);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Translator() {
    }

    static boolean isBangla(CharSequence text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0980 && c <= 0x09FF) {
                return true;
            }
        }
        return false;
    }

    /** Translates off the main thread and calls back on it. */
    static void translate(final String text, final String from, final String to,
                          final Callback callback) {
        if (text == null || text.trim().isEmpty()) {
            callback.onTranslated(null, "empty");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String translated = null;
                String error = null;
                HttpURLConnection connection = null;
                try {
                    String url = "https://translate.googleapis.com/translate_a/single"
                            + "?client=gtx&dt=t&sl=" + URLEncoder.encode(from, "UTF-8")
                            + "&tl=" + URLEncoder.encode(to, "UTF-8")
                            + "&q=" + URLEncoder.encode(text, "UTF-8");
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(9000);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
                    InputStream in = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                    reader.close();
                    JSONArray root = new JSONArray(body.toString());
                    JSONArray segments = root.getJSONArray(0);
                    StringBuilder out = new StringBuilder();
                    for (int i = 0; i < segments.length(); i++) {
                        JSONArray segment = segments.optJSONArray(i);
                        if (segment != null && segment.length() > 0 && !segment.isNull(0)) {
                            out.append(segment.getString(0));
                        }
                    }
                    translated = out.toString().trim();
                    if (translated.isEmpty()) {
                        translated = null;
                        error = "empty";
                    }
                } catch (Exception e) {
                    error = e.getClass().getSimpleName();
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
                final String result = translated;
                final String failure = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onTranslated(result, failure);
                    }
                });
            }
        }, "likhon-translate").start();
    }
}
