package com.likhon.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Settings building blocks in an iOS 27 liquid-glass language: translucent
 * glass panels, specular rims, floating pills and a soft colour mesh behind
 * everything. All colours still come from {@link Palette}, so the eight themes
 * and every accent keep working.
 */
final class M3 {

    interface IntAction {
        void run(int value);
    }

    private M3() {
    }

    // ----------------------------------------------------------------- tokens

    static int surfaceHigh(Palette p) {
        return p.dark ? Palette.mix(p.surface, p.text, 0.07f) : p.surface;
    }

    static int surfaceLow(Palette p) {
        return Palette.mix(p.surface, p.bg, p.dark ? 0.22f : 0.5f);
    }

    static int surfaceHighest(Palette p) {
        return p.dark ? Palette.mix(p.surface, p.text, 0.13f)
                : Palette.mix(p.surface, p.bg, 0.95f);
    }

    static int outlineVariant(Palette p) {
        return Palette.mix(p.surface, p.text, 0.10f);
    }

    static int outline(Palette p) {
        return Palette.mix(p.surface, p.text, 0.26f);
    }

    static int primaryContainer(Palette p) {
        return Palette.mix(p.surface, p.accent, p.dark ? 0.24f : 0.13f);
    }

    static int onPrimaryContainer(Palette p) {
        return Palette.mix(p.text, p.accent, p.dark ? 0.45f : 0.4f);
    }

    // ------------------------------------------------------------- primitives

    static GradientDrawable gradient(Context c, int top, int bottom, float radiusDp,
                                     int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        drawable.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) {
            drawable.setStroke(dp(c, 1), strokeColor);
        }
        return drawable;
    }

    private static GradientDrawable mask(Context c, float radiusDp) {
        return Ui.rounded(c, 0xFFFFFFFF, radiusDp);
    }

    static RippleDrawable rippleMask(Context c, int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(color), null, mask(c, radiusDp));
    }

    static void pressScale(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.972f).scaleY(0.972f).setDuration(90).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
            return false;
        });
    }

    static ImageView icon(Context c, int res, int tint) {
        ImageView view = new ImageView(c);
        view.setImageResource(res);
        view.setColorFilter(tint);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return view;
    }

    // ----------------------------------------------------------------- screens

    static FrameLayout screen(Context c, Palette p, View bar, LinearLayout body) {
        FrameLayout screen = new FrameLayout(c);
        screen.addView(new Glass.Backdrop(c, p), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout column = Ui.vertical(c);
        LinearLayout barWrap = Ui.vertical(c);
        barWrap.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 6));
        barWrap.addView(bar);
        column.addView(barWrap);

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        screen.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return screen;
    }

    static LinearLayout body(Context c) {
        LinearLayout body = Ui.vertical(c);
        body.setPadding(dp(c, 14), dp(c, 2), dp(c, 14), dp(c, 48));
        return body;
    }

    static LinearLayout topBar(Context c, Palette p, CharSequence title, CharSequence subtitle,
                               Runnable back, boolean showAppIcon) {
        LinearLayout bar = new Glass.Panel(c, p, 26, Glass.BAR, true);
        bar.setPadding(dp(c, 10), dp(c, 9), dp(c, 16), dp(c, 9));
        bar.setElevation(dp(c, 4));

        if (showAppIcon) {
            ImageView app = new ImageView(c);
            app.setImageResource(R.mipmap.ic_launcher);
            LinearLayout.LayoutParams appParams = new LinearLayout.LayoutParams(
                    dp(c, 42), dp(c, 42));
            appParams.rightMargin = dp(c, 12);
            bar.addView(app, appParams);
        } else if (back != null) {
            FrameLayout btn = new FrameLayout(c);
            btn.setBackground(Glass.control(c, p, 21, 0,
                    (p.text & 0x00FFFFFF) | 0x14000000));
            btn.addView(icon(c, R.drawable.ic_back, p.text),
                    new FrameLayout.LayoutParams(dp(c, 20), dp(c, 20), Gravity.CENTER));
            btn.setClickable(true);
            btn.setFocusable(true);
            btn.setOnClickListener(v -> back.run());
            pressScale(btn);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    dp(c, 40), dp(c, 40));
            btnParams.rightMargin = dp(c, 12);
            bar.addView(btn, btnParams);
        }

        LinearLayout texts = Ui.vertical(c);
        texts.addView(Ui.text(c, title, 19, p.text, true));
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = Ui.text(c, subtitle, 11, p.textMuted, false);
            sub.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(sub);
        }
        bar.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    // ------------------------------------------------------------- containers

    static LinearLayout card(Context c, Palette p) {
        LinearLayout card = new Glass.Panel(c, p, 28, Glass.CARD, false);
        card.setElevation(dp(c, 5));
        card.setClipToPadding(false);
        int pad = dp(c, 18);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    static LinearLayout bareCard(Context c, Palette p) {
        LinearLayout card = new Glass.Panel(c, p, 28, Glass.CARD, false);
        card.setElevation(dp(c, 5));
        card.setClipToPadding(false);
        return card;
    }

    static void applySurface(Context c, View view, Palette p, float radiusDp,
                             float elevationDp) {
        view.setBackground(Glass.glass(c, p, radiusDp, 0));
        view.setElevation(dp(c, elevationDp));
    }

    static TextView sectionLabel(Context c, Palette p, CharSequence text) {
        TextView label = Ui.text(c, text, 12, p.textMuted, true);
        label.setLetterSpacing(0.09f);
        label.setAllCaps(false);
        label.setPadding(dp(c, 8), dp(c, 22), 0, dp(c, 8));
        return label;
    }

    static View divider(Context c, Palette p) {
        View view = new View(c);
        view.setBackgroundColor((p.text & 0x00FFFFFF) | 0x14000000);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        view.setLayoutParams(params);
        return view;
    }

    // ------------------------------------------------------------------ chips

    static TextView chip(Context c, Palette p, CharSequence label, boolean selected,
                         Runnable action) {
        TextView chip = Ui.text(c, label, 13, selected ? p.onAccent : p.text, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(c, 12), dp(c, 11), dp(c, 12), dp(c, 11));
        applyChip(chip, c, p, selected);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    static void applyChip(TextView chip, Context c, Palette p, boolean selected) {
        chip.setTextColor(selected ? p.onAccent : p.text);
        int ripple = selected ? 0x33FFFFFF : ((p.text & 0x00FFFFFF) | 0x18000000);
        if (selected) {
            chip.setBackground(Glass.control(c, p, 18, p.accent, ripple));
            chip.setElevation(dp(c, 2));
        } else {
            chip.setBackground(Glass.control(c, p, 18, 0, ripple));
            chip.setElevation(0);
        }
    }

    /** Brighter glass pill used for the selected segment. */
    private static Drawable raisedPill(Context c, Palette p, float radiusDp) {
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                ((p.dark ? 0x4C : 0xF4) << 24) | 0x00FFFFFF,
                ((p.dark ? 0x30 : 0xD8) << 24) | 0x00FFFFFF});
        base.setCornerRadius(dp(c, radiusDp));
        base.setStroke(Math.max(1, dp(c, 1.1f)),
                ((p.dark ? 0x70 : 0xFF) << 24) | 0x00FFFFFF);
        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{(p.dark ? 0x1A : 0x40) << 24 | 0x00FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(dp(c, radiusDp - 1));
        LayerDrawable layers = new LayerDrawable(new Drawable[]{base, sheen});
        layers.setLayerInset(1, dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1));
        return layers;
    }

    static LinearLayout segmented(Context c, Palette p, CharSequence[] labels, int selected,
                                  final IntAction action) {
        LinearLayout track = Ui.horizontal(c);
        track.setBackground(Glass.control(c, p, 24, 0,
                (p.text & 0x00FFFFFF) | 0x08000000));
        track.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final boolean on = i == selected;
            TextView segment = Ui.text(c, labels[i], 13,
                    on ? p.text : p.textMuted, true);
            segment.setGravity(Gravity.CENTER);
            segment.setSingleLine(true);
            segment.setPadding(dp(c, 6), dp(c, 10), dp(c, 6), dp(c, 10));
            if (on) {
                segment.setBackground(raisedPill(c, p, 20));
                segment.setElevation(dp(c, 3));
            } else {
                segment.setClickable(true);
                segment.setFocusable(true);
                segment.setBackground(new RippleDrawable(
                        ColorStateList.valueOf((p.text & 0x00FFFFFF) | 0x14000000),
                        Ui.rounded(c, 0x00000000, 20), mask(c, 20)));
                segment.setOnClickListener(v -> action.run(index));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                params.leftMargin = dp(c, 4);
            }
            track.addView(segment, params);
        }
        return track;
    }

    // ---------------------------------------------------------------- buttons

    static TextView filledButton(Context c, Palette p, CharSequence label, Runnable action) {
        TextView button = Ui.text(c, label, 14, p.onAccent, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        button.setBackground(Glass.control(c, p, 22, p.accent, 0x33FFFFFF));
        button.setElevation(dp(c, 3));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        pressScale(button);
        return button;
    }

    static TextView tonalButton(Context c, Palette p, CharSequence label, Runnable action) {
        TextView button = Ui.text(c, label, 14, onPrimaryContainer(p), true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        button.setBackground(Glass.control(c, p, 22, 0,
                (p.accent & 0x00FFFFFF) | 0x24000000));
        button.setElevation(dp(c, 1));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        pressScale(button);
        return button;
    }

    // ------------------------------------------------------------------- rows

    static View switchRow(Context c, Palette p, CharSequence title, CharSequence description,
                          boolean checked, final M3Switch.Listener listener) {
        LinearLayout row = Ui.horizontal(c);
        row.setPadding(dp(c, 2), dp(c, 10), dp(c, 2), dp(c, 10));

        LinearLayout texts = Ui.vertical(c);
        texts.addView(Ui.text(c, title, 14.5f, p.text, true));
        if (description != null && description.length() > 0) {
            TextView sub = Ui.text(c, description, 11.5f, p.textMuted, false);
            sub.setPadding(0, dp(c, 3), dp(c, 10), 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final M3Switch toggle = new M3Switch(c, p, checked);
        toggle.setListener(listener);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.leftMargin = dp(c, 8);
        row.addView(toggle, toggleParams);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> toggle.toggle());
        return row;
    }

    static View tile(Context c, Palette p, int iconRes, CharSequence title,
                     CharSequence subtitle, Runnable action) {
        Glass.Tile tile = new Glass.Tile(c, p, 24, Glass.CARD);
        tile.setElevation(dp(c, 4));
        tile.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));

        LinearLayout content = Ui.vertical(c);
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Glass.glass(c, p, 13, 0));
        badge.setElevation(dp(c, 1));
        badge.addView(icon(c, iconRes, p.accent),
                new FrameLayout.LayoutParams(dp(c, 21), dp(c, 21), Gravity.CENTER));
        content.addView(badge, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));

        TextView titleView = Ui.text(c, title, 14, p.text, true);
        titleView.setPadding(0, dp(c, 12), 0, 0);
        content.addView(titleView);

        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = Ui.text(c, subtitle, 11, p.textMuted, false);
            sub.setPadding(0, dp(c, 3), 0, 0);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            content.addView(sub);
        }
        tile.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setForeground(rippleMask(c, (p.text & 0x00FFFFFF) | 0x18000000, 24));
        tile.setOnClickListener(v -> action.run());
        pressScale(tile);
        return tile;
    }

    static View navRow(Context c, Palette p, int iconRes, CharSequence title,
                       CharSequence subtitle, Runnable action) {
        LinearLayout row = Ui.horizontal(c);
        row.setPadding(dp(c, 12), dp(c, 12), dp(c, 10), dp(c, 12));

        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Glass.glass(c, p, 11, 0));
        badge.addView(icon(c, iconRes, p.accent),
                new FrameLayout.LayoutParams(dp(c, 19), dp(c, 19), Gravity.CENTER));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                dp(c, 34), dp(c, 34));
        badgeParams.rightMargin = dp(c, 12);
        row.addView(badge, badgeParams);

        LinearLayout texts = Ui.vertical(c);
        texts.addView(Ui.text(c, title, 14, p.text, true));
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = Ui.text(c, subtitle, 11, p.textMuted, false);
            sub.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(icon(c, R.drawable.ic_chevron, p.textMuted),
                new LinearLayout.LayoutParams(dp(c, 17), dp(c, 17)));

        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf((p.text & 0x00FFFFFF) | 0x16000000),
                Ui.rounded(c, 0x00000000, 16), mask(c, 16)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    static int dp(Context c, float value) {
        return Ui.dp(c, value);
    }
}
