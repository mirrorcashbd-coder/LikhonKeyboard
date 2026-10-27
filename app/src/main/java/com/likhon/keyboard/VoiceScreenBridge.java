package com.likhon.keyboard;

/**
 * Hand-off point between {@link VoiceScreenActivity} and the keyboard.
 *
 * <p>An input method cannot ask for an activity result, so the little activity
 * leaves the recognised text here and wakes the keyboard, which is in the same
 * process. The text is taken exactly once.
 */
final class VoiceScreenBridge {

    private static String pendingText;
    private static String pendingLanguage;
    private static Runnable wake;

    private VoiceScreenBridge() {
    }

    static synchronized void listen(Runnable callback) {
        wake = callback;
    }

    static synchronized void deliver(String text, String language) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        pendingText = text;
        pendingLanguage = language;
        Runnable callback = wake;
        if (callback != null) {
            callback.run();
        }
    }

    static synchronized String takeText() {
        String text = pendingText;
        pendingText = null;
        return text;
    }

    static synchronized String takeLanguage() {
        String language = pendingLanguage;
        pendingLanguage = null;
        return language;
    }
}
