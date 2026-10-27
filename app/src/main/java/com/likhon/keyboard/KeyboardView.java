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
 * key pop-up and repeat-delete trivial to implement. Each finger is tracked on
 * its own, so several keys can be held and typed at the same time.
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

    // Appearance knobs, set from the settings screen.
    private float keyRadiusDp = KEY_RADIUS_DP;
    private float gapDp = GAP_DP;
    private float labelScale = 1f;
    private boolean keyBorders = true;

    private static final long LONG_PRESS_MS = 400;
    private static final long REPEAT_DELAY_MS = 330;
    private static final long REPEAT_INTERVAL_MS = 45;

    /** Touch slots; Android reports at most 10 fingers at once. */
    private static final int MAX_POINTERS = 10;

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
    private boolean voiceActive;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;

    private final Pointer[] pointers = new Pointer[MAX_POINTERS];
    private int layoutSerial;

    /**
     * One finger on the keyboard.
     *
     * <p>Every finger gets its own key position, its own long-press timer and
     * its own delete-repeat timer, so two thumbs can type at the same time and
     * a finger resting on shift does not swallow the other hand's letters.
     */
    private final class Pointer {
        int id = -1;
        int row = -1;
        int col = -1;
        int downRow = -1;
        int downCol = -1;
        Key downKey;
        int downSerial;
        boolean longPressFired;
        boolean repeated;

        final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                if (id < 0) {
                    return;
                }
                Key key = keyAt(row, col);
                if (key == null || row != downRow || col != downCol) {
                    return;
                }
                if (key.type != Key.LANGUAGE) {
                    // Only the language key has a long-press action. For every
                    // other key the release still types normally.
                    return;
                }
                longPressFired = true;
                if (listener != null) {
                    listener.onKeyLongPress(key);
                }
            }
        };

        final Runnable repeat = new Runnable() {
            @Override
            public void run() {
                Key key = keyAt(row, col);
                if (id < 0 || key == null || key.type != Key.DELETE || listener == null) {
                    return;
                }
                repeated = true;
                listener.onKey(key);
                handler.postDelayed(this, REPEAT_INTERVAL_MS);
            }
        };

        boolean active() {
            return id >= 0;
        }
    }

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
        for (int i = 0; i < MAX_POINTERS; i++) {
            pointers[i] = new Pointer();
        }
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

    /** Lights the microphone key while voice typing is listening. */
    void setVoiceActive(boolean active) {
        if (this.voiceActive != active) {
            this.voiceActive = active;
            invalidate();
        }
    }

    void setRowHeightDp(float rowHeightDp) {
        rowHeightPx = dp(rowHeightDp);
        requestLayout();
        invalidate();
    }

    /** Current row height, for the drag-to-resize handle. */
    float getRowHeightDp() {
        return rowHeightPx / density;
    }

    /** How many rows the current page has (usually four). */
    int getRowCount() {
        return Math.max(1, layoutRows.size());
    }

    void setKeys(List<Layouts.Row> newRows) {
        layoutRows = newRows == null ? Collections.<Layouts.Row>emptyList() : newRows;
        layoutSerial++;
        // Fingers stay tracked across a layout switch (e.g. a resting finger
        // while the other hand taps shift); each pointer remembers the key it
        // went down on, so the switch cannot turn it into a different character.
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
                + (count - 1) * gapDp * density);
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
        float gap = gapDp * density;

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
        if (!palette.hasGradient()) {
            canvas.drawColor(palette.bg);
        }
        int count = Math.min(layoutRows.size(), keyRects.size());
        for (int r = 0; r < count; r++) {
            Key[] keys = layoutRows.get(r).keys;
            List<RectF> rects = keyRects.get(r);
            for (int c = 0; c < keys.length && c < rects.size(); c++) {
                drawKey(canvas, keys[c], rects.get(c), isPressed(r, c));
            }
        }
        drawPreview(canvas);
    }

    private void drawKey(Canvas canvas, Key key, RectF r, boolean pressed) {
        boolean accent = key.active || (key.type == Key.MIC && voiceActive);
        int bg;
        if (accent) {
            bg = pressed ? palette.accentPressed : palette.accent;
        } else if (key.isSpecial()) {
            bg = pressed ? palette.keySpecialPressed : palette.keySpecial;
        } else {
            bg = pressed ? palette.keyPressed : palette.keyBg;
        }
        float radius = Math.min(dp(keyRadiusDp), r.height() / 2f);
        if (palette.shadow != 0) {
            tmpRect.set(r);
            tmpRect.offset(0, dp(1.4f));
            fillPaint.setColor(palette.shadow);
            canvas.drawRoundRect(tmpRect, radius, radius, fillPaint);
        }
        if (accent && palette.glow != 0) {
            tmpRect.set(r);
            tmpRect.inset(-dp(1.6f), -dp(1.6f));
            fillPaint.setColor(palette.glow);
            canvas.drawRoundRect(tmpRect, radius + dp(1.6f), radius + dp(1.6f), fillPaint);
        }
        fillPaint.setColor(bg);
        canvas.drawRoundRect(r, radius, radius, fillPaint);

        if (keyBorders && !key.active && key.isChar()) {
            strokePaint.setColor(palette.keyStroke);
            strokePaint.setStrokeWidth(dp(1f));
            tmpRect.set(r);
            tmpRect.inset(dp(0.5f), dp(0.5f));
            canvas.drawRoundRect(tmpRect, radius, radius, strokePaint);
        }

        int foreground = accent ? palette.onAccent : palette.text;
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
            case Key.MIC:
                drawMic(canvas, r, foreground);
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
        float size = r.height() * (emoji ? 0.5f : 0.44f) * scale * labelScale;
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

    private void drawMic(Canvas canvas, RectF r, int color) {
        float cx = r.centerX();
        float cy = r.centerY();
        float s = Math.min(r.width(), r.height()) * 0.19f;
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(1.7f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setColor(color);
        // Capsule body.
        tmpRect.set(cx - s * 0.5f, cy - s * 1.45f, cx + s * 0.5f, cy + s * 0.2f);
        canvas.drawRoundRect(tmpRect, s * 0.5f, s * 0.5f, iconPaint);
        // Cradle, stem and base.
        tmpRect.set(cx - s, cy - s * 0.55f, cx + s, cy + s * 0.85f);
        canvas.drawArc(tmpRect, 0f, 180f, false, iconPaint);
        canvas.drawLine(cx, cy + s * 0.85f, cx, cy + s * 1.3f, iconPaint);
        canvas.drawLine(cx - s * 0.65f, cy + s * 1.3f,
                cx + s * 0.65f, cy + s * 1.3f, iconPaint);
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

    private boolean showPreview = true;

    void setKeyGap(float value) {
        if (gapDp != value) {
            gapDp = value;
            if (getWidth() > 0 && getHeight() > 0) {
                computeRects(getWidth(), getHeight());
            }
            requestLayout();
            invalidate();
        }
    }

    void setKeyShape(int shape) {
        float radius = shape == 0 ? 4f : (shape == 2 ? 200f : KEY_RADIUS_DP);
        if (keyRadiusDp != radius) {
            keyRadiusDp = radius;
            invalidate();
        }
    }

    void setLabelScale(float value) {
        if (labelScale != value) {
            labelScale = value;
            invalidate();
        }
    }

    void setKeyBorders(boolean value) {
        if (keyBorders != value) {
            keyBorders = value;
            invalidate();
        }
    }

    void setShowPreview(boolean value) {
        if (showPreview != value) {
            showPreview = value;
            invalidate();
        }
    }

    private void drawPreview(Canvas canvas) {
        if (!showPreview) {
            return;
        }
        // One pop-up at a time: during two-thumb typing it would only flicker.
        Pointer only = singlePointer();
        if (only == null) {
            return;
        }
        Key key = keyAt(only.row, only.col);
        if (key == null || !key.isChar() || key.label.length() > 2) {
            return;
        }
        int codePoint = key.label.codePointAt(0);
        if (codePoint >= 0x1F000) {
            return;
        }
        RectF r = rectAt(only.row, only.col);
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
                Pointer p = obtainPointer(event.getPointerId(0));
                if (p == null) {
                    return true;
                }
                if (!placePointer(p, event.getX(), event.getY())) {
                    releasePointer(p);
                    return false;
                }
                startPointer(p);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int index = event.getActionIndex();
                Pointer p = obtainPointer(event.getPointerId(index));
                if (p == null) {
                    return true;
                }
                // A second finger is a full participant: it gets its own key,
                // its own highlight and its own timers. A finger that lands
                // between keys is still tracked so sliding it onto a key works.
                placePointer(p, event.getX(index), event.getY(index));
                startPointer(p);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                boolean changed = false;
                int count = event.getPointerCount();
                for (int i = 0; i < count; i++) {
                    Pointer p = findPointer(event.getPointerId(i));
                    if (p == null) {
                        continue;
                    }
                    int oldRow = p.row;
                    int oldCol = p.col;
                    placePointer(p, event.getX(i), event.getY(i));
                    if (p.row == oldRow && p.col == oldCol) {
                        continue;
                    }
                    changed = true;
                    Key key = keyAt(p.row, p.col);
                    // Dragging away from delete stops its repeat; dragging off
                    // the pressed key cancels its long-press action.
                    if (key == null || key.type != Key.DELETE) {
                        handler.removeCallbacks(p.repeat);
                    }
                    if (p.row != p.downRow || p.col != p.downCol) {
                        handler.removeCallbacks(p.longPress);
                    }
                }
                if (changed) {
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                // Every finger types when it lifts, even while other fingers
                // are still down: that is what makes fast two-thumb typing work.
                Pointer p = findPointer(event.getPointerId(event.getActionIndex()));
                if (p == null) {
                    return true;
                }
                finishPointer(p);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                releaseAllPointers();
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    private Pointer obtainPointer(int id) {
        for (Pointer p : pointers) {
            if (p.id == id) {
                return p;
            }
        }
        for (Pointer p : pointers) {
            if (!p.active()) {
                resetPointer(p, id);
                return p;
            }
        }
        return null;
    }

    private Pointer findPointer(int id) {
        for (Pointer p : pointers) {
            if (p.id == id) {
                return p;
            }
        }
        return null;
    }

    /** The only finger currently down, or null when there are none or several. */
    private Pointer singlePointer() {
        Pointer only = null;
        for (Pointer p : pointers) {
            if (!p.active()) {
                continue;
            }
            if (only != null) {
                return null;
            }
            only = p;
        }
        return only;
    }

    private void resetPointer(Pointer p, int id) {
        handler.removeCallbacks(p.longPress);
        handler.removeCallbacks(p.repeat);
        p.id = id;
        p.row = -1;
        p.col = -1;
        p.downRow = -1;
        p.downCol = -1;
        p.downKey = null;
        p.downSerial = layoutSerial;
        p.longPressFired = false;
        p.repeated = false;
    }

    private void releasePointer(Pointer p) {
        resetPointer(p, -1);
    }

    private void releaseAllPointers() {
        for (Pointer p : pointers) {
            if (p.active()) {
                releasePointer(p);
            }
        }
    }

    /** True when (x, y) is on a key; false means the finger is on no key. */
    private boolean placePointer(Pointer p, float x, float y) {
        int row = findRow(x, y);
        int col = row < 0 ? -1 : findCol(row, x, y);
        p.row = row;
        p.col = col;
        return row >= 0 && col >= 0;
    }

    private void startPointer(Pointer p) {
        p.downRow = p.row;
        p.downCol = p.col;
        p.downKey = keyAt(p.row, p.col);
        p.downSerial = layoutSerial;
        Key key = p.downKey;
        if (key == null) {
            return;
        }
        feedback(key);
        if (key.type == Key.LANGUAGE) {
            handler.removeCallbacks(p.longPress);
            handler.postDelayed(p.longPress, LONG_PRESS_MS);
        }
        if (key.type == Key.DELETE) {
            handler.removeCallbacks(p.repeat);
            handler.postDelayed(p.repeat, REPEAT_DELAY_MS);
        }
    }

    private void finishPointer(Pointer p) {
        handler.removeCallbacks(p.longPress);
        handler.removeCallbacks(p.repeat);
        Key key = keyAt(p.row, p.col);
        if (p.downSerial != layoutSerial) {
            // The layout changed under this finger (shift, symbols, language,
            // emoji). Type the key it actually went down on instead of whatever
            // now happens to sit at the same coordinates.
            key = (p.row == p.downRow && p.col == p.downCol) ? p.downKey : null;
        }
        boolean emit = key != null && !p.longPressFired && !p.repeated && listener != null;
        // Release first: the listener may rebuild the key grid for the next
        // layout, and the lifting finger must not be re-read afterwards.
        releasePointer(p);
        if (emit) {
            listener.onKey(key);
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
        // Half a gap of slack, so the seams between rows are not dead space.
        float slack = dp(gapDp / 2f);
        for (int r = 0; r < keyRects.size(); r++) {
            List<RectF> rects = keyRects.get(r);
            if (rects.isEmpty()) {
                continue;
            }
            RectF first = rects.get(0);
            RectF last = rects.get(rects.size() - 1);
            if (y >= first.top - slack && y <= first.bottom + slack
                    && x >= first.left - slack && x <= last.right + slack) {
                return r;
            }
        }
        return -1;
    }

    /**
     * Index of the key under the touch, or of the nearest key in the row. The
     * narrow gaps between keys are not dead space: an off-centre tap still
     * types the letter the finger is closest to.
     */
    private int findCol(int row, float x, float y) {
        List<RectF> rects = keyRects.get(row);
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int c = 0; c < rects.size(); c++) {
            RectF r = rects.get(c);
            if (r.contains(x, y)) {
                return c;
            }
            float dx = x < r.left ? r.left - x : (x > r.right ? x - r.right : 0f);
            float dy = y < r.top ? r.top - y : (y > r.bottom ? y - r.bottom : 0f);
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = c;
            }
        }
        return best;
    }

    private Key keyAt(int row, int col) {
        if (row < 0 || row >= layoutRows.size() || col < 0) {
            return null;
        }
        Key[] keys = layoutRows.get(row).keys;
        if (col >= keys.length) {
            return null;
        }
        return keys[col];
    }

    private RectF rectAt(int row, int col) {
        if (row < 0 || row >= keyRects.size() || col < 0) {
            return null;
        }
        List<RectF> rects = keyRects.get(row);
        if (col >= rects.size()) {
            return null;
        }
        return rects.get(col);
    }

    private boolean isPressed(int row, int col) {
        for (Pointer p : pointers) {
            if (p.active() && p.row == row && p.col == col) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAllPointers();
        super.onDetachedFromWindow();
    }

    private float dp(float value) {
        return value * density;
    }
}
