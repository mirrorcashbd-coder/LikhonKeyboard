package com.likhon.keyboard;

import android.os.SystemClock;

import java.util.ArrayDeque;

/**
 * Undo/redo for the current editor.
 *
 * <p>A snapshot is the whole field text plus the selection, taken just before
 * an edit. Restoring one replaces the field's text and puts the selection back.
 * Rapid typing is merged into one snapshot so undo works word by word instead
 * of letter by letter. History is per field and lives in memory only.
 */
final class UndoStack {

    static final class Snap {
        final String text;
        final int start;
        final int end;

        Snap(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private static final int MAX_STEPS = 80;
    private static final int MAX_TEXT = 20000;
    private static final long MERGE_MS = 1400;

    private final ArrayDeque<Snap> undo = new ArrayDeque<>();
    private final ArrayDeque<Snap> redo = new ArrayDeque<>();
    private long lastRecord;

    void reset() {
        undo.clear();
        redo.clear();
        lastRecord = 0;
    }

    boolean canUndo() {
        return !undo.isEmpty();
    }

    boolean canRedo() {
        return !redo.isEmpty();
    }

    /**
     * Records the state before an edit. {@code mergeTyping} folds the call into
     * the previous snapshot when it follows quickly, which is how a typed word
     * becomes a single undo step.
     */
    void record(String text, int start, int end, boolean mergeTyping) {
        if (text == null || text.length() > MAX_TEXT) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        redo.clear();
        if (mergeTyping && !undo.isEmpty() && now - lastRecord < MERGE_MS) {
            lastRecord = now;
            return;
        }
        Snap last = undo.peekLast();
        if (last != null && last.text.equals(text) && last.start == start && last.end == end) {
            lastRecord = now;
            return;
        }
        undo.addLast(new Snap(text, start, end));
        while (undo.size() > MAX_STEPS) {
            undo.removeFirst();
        }
        lastRecord = now;
    }

    /** Pops the previous state; {@code current} becomes the redo step. */
    Snap undo(String current, int start, int end) {
        if (undo.isEmpty()) {
            return null;
        }
        if (current != null && current.length() <= MAX_TEXT) {
            redo.addLast(new Snap(current, start, end));
            while (redo.size() > MAX_STEPS) {
                redo.removeFirst();
            }
        }
        return undo.removeLast();
    }

    /** Pops the next state; {@code current} goes back to the undo side. */
    Snap redo(String current, int start, int end) {
        if (redo.isEmpty()) {
            return null;
        }
        if (current != null && current.length() <= MAX_TEXT) {
            undo.addLast(new Snap(current, start, end));
        }
        return redo.removeLast();
    }
}
