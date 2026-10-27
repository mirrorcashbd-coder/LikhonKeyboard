package com.likhon.keyboard;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * The last few voice events, kept in memory only.
 *
 * <p>Some phones give the keyboard a microphone that records but never answers,
 * and there is no computer here to read logcat from. The settings screen shows
 * this list instead, so the reason can be read (and reported) on the phone.
 */
final class VoiceLog {

    private static final int MAX = 60;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();

    private VoiceLog() {
    }

    static void add(String line) {
        if (line == null) {
            return;
        }
        String stamp;
        synchronized (TIME) {
            stamp = TIME.format(new Date());
        }
        synchronized (LINES) {
            LINES.addLast(stamp + "  " + line);
            while (LINES.size() > MAX) {
                LINES.removeFirst();
            }
        }
    }

    static String dump() {
        synchronized (LINES) {
            if (LINES.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (String line : LINES) {
                out.append(line).append('\n');
            }
            return out.toString();
        }
    }

    static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }
}
