package com.likhon.keyboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** A floating glass bar with an animated selection pill. */
class LiquidGlassBottomNavigation extends LinearLayout {

    interface Listener {
        void onSelect(int index);
    }

    private static final class Item {
        ImageView icon;
        TextView label;
        Runnable action;
    }

    private final LiquidGlass.Painter painter = new LiquidGlass.Painter();
    private final LiquidGlass.Config config = new LiquidGlass.Config();
    private final Paint indicator = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int[] location = new int[2];
    private final float density;
    private final boolean motionOff;
    private final List<Item> items = new ArrayList<>();

    private Palette palette;
    private GlassBackdrop backdrop;
    private Listener listener;
    private int selected = 0;
    private float indicatorLeft;
    private float indicatorWidth;
    private ValueAnimator indicatorAnim;

    LiquidGlassBottomNavigation(Context context, Palette palette) {
        super(context);
        this.palette = palette == null ? Palette.light() : palette;
        density = getResources().getDisplayMetrics().density;
        motionOff = LiquidGlass.motionDisabled(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setWillNotDraw(false);
        setElevation(dp(22));
        setPadding(Math.round(dp(6)), Math.round(dp(6)),
                Math.round(dp(6)), Math.round(dp(6)));

        config.radiusDp = 999f;
        config.tintAlpha = 0.18f;
        config.haloAlpha = 0.30f;
        config.haloWidthDp = 10f;
        config.glossAlpha = 0.30f;

        GradientDrawable outline = new GradientDrawable();
        outline.setColor(0x00000000);
        outline.setCornerRadius(dp(999));
        setBackground(outline);

        indicator.setColor(palette.dark ? 0x30FFFFFF : 0x66FFFFFF);
        indicatorRim.setStyle(Paint.Style.STROKE);
        indicatorRim.setStrokeWidth(dp(1f));
        indicatorRim.setColor(palette.dark ? 0x66FFFFFF : 0xCCFFFFFF);
    }

    void setPalette(Palette palette) {
        this.palette = palette == null ? Palette.light() : palette;
        refreshTints();
        invalidate();
    }

    void setBackdrop(GlassBackdrop backdrop) {
        this.backdrop = backdrop;
        invalidate();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void addItem(int iconRes, CharSequence label, Runnable action) {
        Item item = new Item();
        item.action = action;

        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClickable(true);
        cell.setFocusable(true);
        cell.setBackground(M3.rippleMask(getContext(),
                (palette.accent & 0x00FFFFFF) | 0x22000000, 26f));

        item.icon = new ImageView(getContext());
        item.icon.setImageResource(iconRes);
        item.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        item.icon.setColorFilter(palette.textMuted);
        cell.addView(item.icon, new LinearLayout.LayoutParams(
                Math.round(dp(23)), Math.round(dp(23))));

        item.label = new TextView(getContext());
        item.label.setText(label);
        item.label.setTextSize(10.5f);
        item.label.setGravity(Gravity.CENTER);
        item.label.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        item.label.setTextColor(palette.textMuted);
        item.label.setMaxLines(1);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = Math.round(dp(3));
        cell.addView(item.label, labelParams);

        final int index = items.size();
        cell.setContentDescription(label);
        cell.setOnClickListener(v -> select(index, true));
        items.add(item);

        addView(cell, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        if (items.size() == 1) {
            selected = 0;
        }
        refreshTints();
        post(this::layoutIndicator);
    }

    int getSelected() {
        return selected;
    }

    void select(int index, boolean animate) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        if (index == selected && indicatorWidth > 0) {
            return;
        }
        selected = index;
        refreshTints();
        moveIndicator(animate);
        if (listener != null) {
            listener.onSelect(index);
        }
        Item item = items.get(index);
        if (item.action != null) {
            item.action.run();
        }
    }

    private void refreshTints() {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            boolean on = i == selected;
            item.icon.setColorFilter(on ? palette.accent : palette.textMuted);
            item.label.setTextColor(on ? palette.text : palette.textMuted);
            item.label.setAlpha(on ? 1f : 0.85f);
        }
    }

    private void layoutIndicator() {
        if (items.isEmpty()) {
            return;
        }
        View cell = getChildAt(selected);
        if (cell == null || cell.getWidth() == 0) {
            return;
        }
        indicatorLeft = cell.getLeft() + dp(3);
        indicatorWidth = cell.getWidth() - dp(6);
        invalidate();
    }

    private void moveIndicator(boolean animate) {
        View cell = getChildAt(selected);
        if (cell == null || cell.getWidth() == 0) {
            return;
        }
        float targetLeft = cell.getLeft() + dp(3);
        float targetWidth = cell.getWidth() - dp(6);
        if (!animate || motionOff) {
            indicatorLeft = targetLeft;
            indicatorWidth = targetWidth;
            invalidate();
            return;
        }
        if (indicatorAnim != null) {
            indicatorAnim.cancel();
        }
        final float fromLeft = indicatorLeft;
        final float fromWidth = indicatorWidth;
        indicatorAnim = ValueAnimator.ofFloat(0f, 1f);
        indicatorAnim.setDuration(320);
        indicatorAnim.setInterpolator(new DecelerateInterpolator(2f));
        indicatorAnim.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            indicatorLeft = fromLeft + (targetLeft - fromLeft) * t;
            indicatorWidth = fromWidth + (targetWidth - fromWidth) * t;
            invalidate();
        });
        indicatorAnim.start();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed || indicatorWidth == 0) {
            refreshTints();
            layoutIndicator();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        getLocationInWindow(location);
        painter.configure(palette, config, backdrop, density);
        painter.draw(canvas, w, h, location[0], location[1], 0f);

        if (indicatorWidth > 0) {
            float inset = dp(7);
            rect.set(indicatorLeft, inset, indicatorLeft + indicatorWidth, h - inset);
            float radius = rect.height() / 2f;
            canvas.drawRoundRect(rect, radius, radius, indicator);
            canvas.drawRoundRect(rect, radius, radius, indicatorRim);
        }
    }

    private float dp(float value) {
        return value * density;
    }
}
