package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Draws the keyboard and turns touches into key events.
 *
 * <p>Keys are painted on a single canvas instead of being one View each: that
 * keeps the keyboard light, lets a finger slide from key to key, and makes the
 * key pop-up and repeat-delete trivial to implement.
 */
public class KeyboardView extends View {

    /** Callbacks into the input method service. */
    public interface Listener {
        void onKey(Key key);

        void onKeyLongPress(Key key);
    }

    private static final float PAD_SIDE_DP = 4f;
    private static final float PAD_TOP_DP = 6f;
    private static final float PAD_BOTTOM_DP = 8f;
    private static final float GAP_DP = 6f;
    private static final float KEY_RADIUS_DP = 9f;

    private static final long LONG_PRESS_MS = 460;
    private static final long REPEAT_DELAY_MS = 380;
    private static final long REPEAT_INTERVAL_MS = 55;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();
    private final RectF tmpRect = new RectF();

    private final List<List<RectF>> keyRects = new ArrayList<>();
    private List<Layouts.Row> layoutRows = Collections.emptyList();

    private Listener listener;
    private Palette palette = Palette.light();
    private float density;
    private float rowHeightPx;
    private boolean haptic = true;
    private boolean sound = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;

    private int pressedRow = -1;
    private int pressedCol = -1;
    private int downRow = -1;
    private int downCol = -1;
    private int activePointer = -1;
    private boolean longPressFired;
    private boolean repeated;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            Key key = pressedKey();
            if (key == null || pressedRow != downRow || pressedCol != downCol) {
                return;
            }
            if (key.type != Key.LANGUAGE) {
                // Only the language key has a long-press action. For every other
                // key the release still types normally.
                return;
            }
            longPressFired = true;
            if (listener != null) {
                listener.onKeyLongPress(key);
            }
        }
    };

    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            Key key = pressedKey();
            if (key == null || key.type != Key.DELETE || listener == null) {
                return;
            }
            repeated = true;
            listener.onKey(key);
            handler.postDelayed(this, REPEAT_INTERVAL_MS);
        }
    };

    public KeyboardView(Context context) {
        this(context, null);
    }

    public KeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        rowHeightPx = dp(52);
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setContentDescription(context.getString(R.string.ime_name));
    }

    // ------------------------------------------------------------------ state

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setPalette(Palette palette) {
        this.palette = palette;
        invalidate();
    }

    void setFeedback(boolean haptic, boolean sound) {
        this.haptic = haptic;
        this.sound = sound;
    }

    void setRowHeightDp(float rowHeightDp) {
        rowHeightPx = dp(rowHeightDp);
        requestLayout();
        invalidate();
    }

    void setKeys(List<Layouts.Row> newRows) {
        layoutRows = newRows == null ? Collections.<Layouts.Row>emptyList() : newRows;
        pressedRow = -1;
        pressedCol = -1;
        // The view size usually stays the same when switching letters <->
        // symbols, so onSizeChanged() would not fire: recompute immediately.
        if (getWidth() > 0 && getHeight() > 0) {
            computeRects(getWidth(), getHeight());
        }
        requestLayout();
        invalidate();
    }

    // ---------------------------------------------------------------- measure

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        int count = Math.max(1, layoutRows.size());
        int wanted = Math.round((PAD_TOP_DP + PAD_BOTTOM_DP) * density
                + count * rowHeightPx
                + (count - 1) * GAP_DP * density);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int height;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(wanted, heightSize);
        } else {
            height = wanted;
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeRects(w, h);
    }

    private void computeRects(int width, int height) {
        keyRects.clear();
        int count = layoutRows.size();
        if (count == 0 || width <= 0 || height <= 0) {
            return;
        }
        float padSide = PAD_SIDE_DP * density;
        float padTop = PAD_TOP_DP * density;
        float padBottom = PAD_BOTTOM_DP * density;
        float gap = GAP_DP * density;

        float availableH = height - padTop - padBottom - gap * (count - 1);
        float rowH = Math.max(dp(28), availableH / count);
        // If the window is too short for the requested height, rows simply get
        // shorter; text is scaled from the rect so nothing overlaps.
        float extra = Math.max(0, (height - padTop - padBottom - gap * (count - 1) - rowH * count));
        float top = padTop + extra / 2f;

        float baseKey = (width - 2 * padSide - 9 * gap) / 10f;

        for (int r = 0; r < count; r++) {
            Layouts.Row row = layoutRows.get(r);
            float inset = Math.max(0, row.inset) * baseKey;
            float startX = padSide + inset;
            float endX = width - padSide - inset;
            Key[] keys = row.keys;
            float usable = Math.max(0, endX - startX - gap * (keys.length - 1));
            float weightSum = 0;
            for (Key key : keys) {
                weightSum += Math.max(0.01f, key.weight);
            }
            List<RectF> rects = new ArrayList<>(keys.length);
            float x = startX;
            for (Key key : keys) {
                float w = usable * Math.max(0.01f, key.weight) / weightSum;
                rects.add(new RectF(x, top, x + w, top + rowH));
                x += w + gap;
            }
            keyRects.add(rects);
            top += rowH + gap;
        }
    }

    // ------------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(palette.bg);
        int count = Math.min(layoutRows.size(), keyRects.size());
        for (int r = 0; r < count; r++) {
            Key[] keys = layoutRows.get(r).keys;
            List<RectF> rects = keyRects.get(r);
            for (int c = 0; c < keys.length && c < rects.size(); c++) {
                drawKey(canvas, keys[c], rects.get(c), r == pressedRow && c == pressedCol);
            }
        }
        drawPreview(canvas);
    }

    private void drawKey(Canvas canvas, Key key, RectF r, boolean pressed) {
        int bg;
        if (key.active) {
            bg = pressed ? palette.accentPressed : palette.accent;
        } else if (key.isSpecial()) {
            bg = pressed ? palette.keySpecialPressed : palette.keySpecial;
        } else {
            bg = pressed ? palette.keyPressed : palette.keyBg;
        }
        float radius = Math.min(dp(KEY_RADIUS_DP), r.height() / 2f);
        fillPaint.setColor(bg);
        canvas.drawRoundRect(r, radius, radius, fillPaint);

        if (!key.active && key.isChar()) {
            strokePaint.setColor(palette.keyStroke);
            strokePaint.setStrokeWidth(dp(1f));
            tmpRect.set(r);
            tmpRect.inset(dp(0.5f), dp(0.5f));
            canvas.drawRoundRect(tmpRect, radius, radius, strokePaint);
        }

        int foreground = key.active ? palette.onAccent : palette.text;
        switch (key.type) {
            case Key.SHIFT:
                drawShift(canvas, r, foreground, "caps".equals(key.label));
                break;
            case Key.DELETE:
                drawBackspace(canvas, r, foreground);
                break;
            case Key.ENTER:
                if (key.label.isEmpty()) {
                    drawEnter(canvas, r, foreground);
                } else {
                    drawLabel(canvas, r, key, foreground, 0.9f);
                }
                break;
            case Key.EMOJI:
                drawEmojiIcon(canvas, r, foreground);
                break;
            case Key.PREV:
                drawChevron(canvas, r, foreground, true);
                break;
            case Key.NEXT:
                drawChevron(canvas, r, foreground, false);
                break;
            case Key.SPACE:
                drawSpaceLabel(canvas, r, key.label);
                break;
            case Key.LANGUAGE:
                drawLabel(canvas, r, key, key.active ? palette.onAccent : palette.accent, 0.62f);
                break;
            case Key.CHAR:
            default:
                drawLabel(canvas, r, key, foreground, 1f);
                drawHint(canvas, r, key);
                break;
        }
    }

    private void drawLabel(Canvas canvas, RectF r, Key key, int color, float scale) {
        String label = key.label;
        if (label == null || label.isEmpty()) {
            return;
        }
        int codePoint = label.codePointAt(0);
        boolean emoji = codePoint >= 0x1F000;
        textPaint.setColor(color);
        float size = r.height() * (emoji ? 0.5f : 0.44f) * scale;
        if (!emoji && label.length() > 1) {
            size *= label.length() >= 4 ? 0.58f : 0.74f;
        }
        textPaint.setTextSize(size);
        // Shrink further only if the text still does not fit.
        float maxWidth = r.width() - dp(6);
        float measured = textPaint.measureText(label);
        if (measured > maxWidth && measured > 0) {
            textPaint.setTextSize(size * maxWidth / measured);
        }
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = r.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, r.centerX(), baseline, textPaint);
    }

    private void drawHint(Canvas canvas, RectF r, Key key) {
        if (key.hint == null || key.hint.isEmpty() || r.height() < dp(34)) {
            return;
        }
        textPaint.setColor(palette.textMuted);
        float size = r.height() * 0.19f;
        textPaint.setTextSize(size);
        float measured = textPaint.measureText(key.hint);
        float x = r.right - dp(5) - measured / 2f;
        canvas.drawText(key.hint, x, r.top + size + dp(3), textPaint);
    }

    private void drawSpaceLabel(Canvas canvas, RectF r, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        textPaint.setColor(palette.textMuted);
        textPaint.setTextSize(r.height() * 0.24f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvas.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
    }

    private void drawShift(Canvas canvas, RectF r, int color, boolean capsLock) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.2f;
        iconPath.reset();
        iconPath.moveTo(cx, cy - s * 1.2f);
        iconPath.lineTo(cx + s * 1.2f, cy + s * 0.15f);
        iconPath.lineTo(cx + s * 0.45f, cy + s * 0.15f);
        iconPath.lineTo(cx + s * 0.45f, cy + s * 1.1f);
        iconPath.lineTo(cx - s * 0.45f, cy + s * 1.1f);
        iconPath.lineTo(cx - s * 0.45f, cy + s * 0.15f);
        iconPath.lineTo(cx - s * 1.2f, cy + s * 0.15f);
        iconPath.close();
        iconPaint.setStyle(Paint.Style.FILL);
        iconPaint.setColor(color);
        canvas.drawPath(iconPath, iconPaint);
        if (capsLock) {
            // Caps lock: a small line under the arrow.
            canvas.drawRect(cx - s * 0.75f, cy + s * 1.45f,
                    cx + s * 0.75f, cy + s * 1.45f + dp(1.6f), iconPaint);
        }
    }

    private void drawBackspace(Canvas canvas, RectF r, int color) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.22f;
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.7f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(color);
        iconPath.reset();
        iconPath.moveTo(cx - s * 1.05f, cy);
        iconPath.lineTo(cx - s * 0.42f, cy - s * 0.62f);
        iconPath.lineTo(cx + s * 0.95f, cy - s * 0.62f);
        iconPath.lineTo(cx + s * 0.95f, cy + s * 0.62f);
        iconPath.lineTo(cx - s * 0.42f, cy + s * 0.62f);
        iconPath.close();
        canvas.drawPath(iconPath, iconPaint);
        iconPath.reset();
        iconPath.moveTo(cx - s * 0.08f, cy - s * 0.24f);
        iconPath.lineTo(cx + s * 0.48f, cy + s * 0.24f);
        iconPath.moveTo(cx + s * 0.48f, cy - s * 0.24f);
        iconPath.lineTo(cx - s * 0.08f, cy + s * 0.24f);
        canvas.drawPath(iconPath, iconPaint);
    }

    private void drawEnter(Canvas canvas, RectF r, int color) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.2f;
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.8f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(color);
        iconPath.reset();
        iconPath.moveTo(cx + s, cy - s);
        iconPath.lineTo(cx + s, cy + s * 0.45f);
        iconPath.lineTo(cx - s * 0.95f, cy + s * 0.45f);
        canvas.drawPath(iconPath, iconPaint);
        iconPath.reset();
        iconPath.moveTo(cx - s * 0.95f, cy + s * 0.45f);
        iconPath.lineTo(cx - s * 0.38f, cy - s * 0.1f);
        iconPath.moveTo(cx - s * 0.95f, cy + s * 0.45f);
        iconPath.lineTo(cx - s * 0.38f, cy + s);
        canvas.drawPath(iconPath, iconPaint);
    }

    private void drawEmojiIcon(Canvas canvas, RectF r, int color) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.24f;
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.5f));
        iconPaint.setColor(color);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawCircle(cx, cy, s, iconPaint);
        iconPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - s * 0.35f, cy - s * 0.28f, s * 0.1f, iconPaint);
        canvas.drawCircle(cx + s * 0.35f, cy - s * 0.28f, s * 0.1f, iconPaint);
        iconPaint.setStyle(Paint.Style.STROKE);
        tmpRect.set(cx - s * 0.55f, cy - s * 0.35f, cx + s * 0.55f, cy + s * 0.5f);
        canvas.drawArc(tmpRect, 25f, 130f, false, iconPaint);
    }

    private void drawChevron(Canvas canvas, RectF r, int color, boolean left) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.16f;
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(2f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(color);
        iconPath.reset();
        if (left) {
            iconPath.moveTo(cx + s * 0.6f, cy - s);
            iconPath.lineTo(cx - s * 0.5f, cy);
            iconPath.lineTo(cx + s * 0.6f, cy + s);
        } else {
            iconPath.moveTo(cx - s * 0.6f, cy - s);
            iconPath.lineTo(cx + s * 0.5f, cy);
            iconPath.lineTo(cx - s * 0.6f, cy + s);
        }
        canvas.drawPath(iconPath, iconPaint);
    }

    private void drawPreview(Canvas canvas) {
        Key key = pressedKey();
        if (key == null || !key.isChar() || key.label.length() > 2) {
            return;
        }
        int codePoint = key.label.codePointAt(0);
        if (codePoint >= 0x1F000) {
            return;
        }
        RectF r = pressedRect();
        if (r == null) {
            return;
        }
        float h = dp(50);
        float w = Math.max(dp(44), Math.min(dp(70), r.width() * 1.25f));
        float cx = Math.max(w / 2 + dp(3), Math.min(getWidth() - w / 2 - dp(3), r.centerX()));
        float top = r.top - h - dp(7);
        if (top < dp(3)) {
            top = r.bottom + dp(7);
        }
        RectF popup = new RectF(cx - w / 2, top, cx + w / 2, top + h);
        fillPaint.setColor(palette.popupBg);
        canvas.drawRoundRect(popup, dp(11), dp(11), fillPaint);
        strokePaint.setColor(palette.accent);
        strokePaint.setStrokeWidth(dp(1.5f));
        canvas.drawRoundRect(popup, dp(11), dp(11), strokePaint);

        textPaint.setColor(palette.text);
        textPaint.setTextSize(h * 0.46f);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        canvas.drawText(key.label, cx, popup.centerY() - (fm.ascent + fm.descent) / 2f, textPaint);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                activePointer = event.getPointerId(0);
                int row = findRow(event.getX(), event.getY());
                if (row < 0) {
                    return false;
                }
                longPressFired = false;
                repeated = false;
                downRow = row;
                downCol = findCol(row, event.getX(), event.getY());
                pressedRow = downRow;
                pressedCol = downCol;
                Key key = pressedKey();
                if (key != null) {
                    feedback(key);
                    handler.removeCallbacks(longPressRunnable);
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                    if (key.type == Key.DELETE) {
                        handler.removeCallbacks(repeatRunnable);
                        handler.postDelayed(repeatRunnable, REPEAT_DELAY_MS);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(activePointer);
                if (index < 0) {
                    return true;
                }
                float x = event.getX(index);
                float y = event.getY(index);
                int row = findRow(x, y);
                if (row < 0) {
                    clearPressed();
                } else {
                    int col = findCol(row, x, y);
                    if (row != pressedRow || col != pressedCol) {
                        setPressed(row, col);
                        // Dragging away from delete stops the repeat; dragging
                        // off the pressed key cancels its long-press action.
                        Key key = pressedKey();
                        if (key == null || key.type != Key.DELETE) {
                            handler.removeCallbacks(repeatRunnable);
                        }
                        if (row != downRow || col != downCol) {
                            handler.removeCallbacks(longPressRunnable);
                        }
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(repeatRunnable);
                Key key = pressedKey();
                if (key != null && !longPressFired && !repeated && listener != null) {
                    listener.onKey(key);
                }
                clearPressed();
                activePointer = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(repeatRunnable);
                clearPressed();
                activePointer = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                // Single-touch keyboard: ignore extra fingers.
                return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int pointerId = event.getPointerId(event.getActionIndex());
                if (pointerId == activePointer) {
                    handler.removeCallbacks(longPressRunnable);
                    handler.removeCallbacks(repeatRunnable);
                    clearPressed();
                    activePointer = -1;
                    invalidate();
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private void feedback(Key key) {
        if (haptic) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (sound && audioManager != null) {
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.7f);
        }
    }

    private int findRow(float x, float y) {
        for (int r = 0; r < keyRects.size(); r++) {
            List<RectF> rects = keyRects.get(r);
            if (rects.isEmpty()) {
                continue;
            }
            RectF first = rects.get(0);
            RectF last = rects.get(rects.size() - 1);
            if (y >= first.top - dp(2) && y <= first.bottom + dp(2)
                    && x >= first.left - dp(GAP_DP) && x <= last.right + dp(GAP_DP)) {
                return r;
            }
        }
        return -1;
    }

    private int findCol(int row, float x, float y) {
        List<RectF> rects = keyRects.get(row);
        for (int c = 0; c < rects.size(); c++) {
            if (rects.get(c).contains(x, y)) {
                return c;
            }
        }
        return -1;
    }

    private void setPressed(int row, int col) {
        pressedRow = row;
        pressedCol = col;
    }

    private void clearPressed() {
        pressedRow = -1;
        pressedCol = -1;
    }

    private Key pressedKey() {
        if (pressedRow < 0 || pressedRow >= layoutRows.size()) {
            return null;
        }
        if (pressedCol < 0) {
            return null;
        }
        Key[] keys = layoutRows.get(pressedRow).keys;
        if (pressedCol >= keys.length) {
            return null;
        }
        return keys[pressedCol];
    }

    private RectF pressedRect() {
        if (pressedRow < 0 || pressedRow >= keyRects.size()) {
            return null;
        }
        List<RectF> rects = keyRects.get(pressedRow);
        if (pressedCol < 0 || pressedCol >= rects.size()) {
            return null;
        }
        return rects.get(pressedCol);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable);
        handler.removeCallbacks(repeatRunnable);
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * density;
    }
}
