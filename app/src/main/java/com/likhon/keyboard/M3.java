package com.likhon.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
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
 * Material 3 building blocks, drawn in code because the project deliberately
 * ships without AndroidX. Every colour is derived from the active
 * {@link Palette}, so all themes and accent colours work unchanged.
 *
 * <p>Depth comes from three things working together: a vertical surface
 * gradient (lighter at the top), a hairline outline and a real elevation
 * shadow. Nothing is a flat rectangle.
 */
final class M3 {

    interface IntAction {
        void run(int value);
    }

    private M3() {
    }

    // ----------------------------------------------------------------- tokens

    /** Top of a raised surface: lit from above. */
    static int surfaceHigh(Palette p) {
        return p.dark ? Palette.mix(p.surface, p.text, 0.07f) : p.surface;
    }

    /** Bottom of a raised surface, a touch darker so the card reads as thick. */
    static int surfaceLow(Palette p) {
        return Palette.mix(p.surface, p.bg, p.dark ? 0.22f : 0.5f);
    }

    /** Deepest container, used for switch tracks and pressed wells. */
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

    /** Darker or lighter accent halo behind the header. */
    private static final class Ambient extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Palette palette;

        Ambient(Context context, Palette palette) {
            super(context);
            this.palette = palette;
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            int alpha = palette.dark ? 0x3A : 0x22;
            int color = (palette.accent & 0x00FFFFFF) | (alpha << 24);
            paint.setShader(new RadialGradient(w * 0.82f, 0f, h,
                    new int[]{color, 0x00000000}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    /** A whole settings screen: gradient backdrop, ambient glow, bar + scroll body. */
    static FrameLayout screen(Context c, Palette p, View bar, LinearLayout body) {
        FrameLayout screen = new FrameLayout(c);
        screen.setBackground(gradient(c, p.bgTop, p.bgBottom, 0, 0));

        screen.addView(new Ambient(c, p), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(c, 340), Gravity.TOP));

        LinearLayout column = Ui.vertical(c);
        column.addView(bar);

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
        body.setPadding(dp(c, 16), dp(c, 4), dp(c, 16), dp(c, 44));
        return body;
    }

    /**
     * Material 3 top app bar. Pass {@code back == null} for a root screen and an
     * app icon is shown instead.
     */
    static LinearLayout topBar(Context c, Palette p, CharSequence title, CharSequence subtitle,
                               Runnable back, boolean showAppIcon) {
        LinearLayout bar = Ui.horizontal(c);
        bar.setPadding(dp(c, 16), dp(c, 12), dp(c, 18), dp(c, 12));

        if (showAppIcon) {
            FrameLayout box = new FrameLayout(c);
            GradientDrawable bg = gradient(c, surfaceHigh(p), surfaceLow(p), 15,
                    outlineVariant(p));
            box.setBackground(bg);
            box.setElevation(dp(c, 3));
            ImageView app = new ImageView(c);
            app.setImageResource(R.mipmap.ic_launcher);
            box.addView(app, new FrameLayout.LayoutParams(dp(c, 46), dp(c, 46), Gravity.CENTER));
            LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(
                    dp(c, 46), dp(c, 46));
            boxParams.rightMargin = dp(c, 14);
            bar.addView(box, boxParams);
        } else if (back != null) {
            FrameLayout btn = new FrameLayout(c);
            GradientDrawable bg = gradient(c, surfaceHigh(p), surfaceLow(p), 14,
                    outlineVariant(p));
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf((p.text & 0x00FFFFFF) | 0x22000000),
                    bg, mask(c, 14));
            btn.setBackground(ripple);
            btn.addView(icon(c, R.drawable.ic_back, p.text),
                    new FrameLayout.LayoutParams(dp(c, 21), dp(c, 21), Gravity.CENTER));
            btn.setClickable(true);
            btn.setFocusable(true);
            btn.setOnClickListener(v -> back.run());
            pressScale(btn);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    dp(c, 42), dp(c, 42));
            btnParams.rightMargin = dp(c, 14);
            bar.addView(btn, btnParams);
        }

        LinearLayout texts = Ui.vertical(c);
        TextView titleView = Ui.text(c, title, 20, p.text, true);
        texts.addView(titleView);
        if (subtitle != null && subtitle.length() > 0) {
            TextView sub = Ui.text(c, subtitle, 11.5f, p.textMuted, false);
            sub.setPadding(0, dp(c, 2), 0, 0);
            texts.addView(sub);
        }
        bar.addView(texts, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    // ------------------------------------------------------------- containers

    /** Filled, elevated card with a 24 dp corner. */
    static LinearLayout card(Context c, Palette p) {
        LinearLayout card = Ui.vertical(c);
        applySurface(c, card, p, 24, 4f);
        int pad = dp(c, 18);
        card.setPadding(pad, pad, pad, pad);
        return card;
    }

    /** Card without padding, for previews that paint their own edges. */
    static LinearLayout bareCard(Context c, Palette p) {
        LinearLayout card = Ui.vertical(c);
        applySurface(c, card, p, 24, 4f);
        return card;
    }

    static void applySurface(Context c, View view, Palette p, float radiusDp,
                             float elevationDp) {
        GradientDrawable bg = gradient(c, surfaceHigh(p), p.dark ? p.surface : surfaceLow(p),
                radiusDp, outlineVariant(p));
        view.setBackground(bg);
        view.setElevation(dp(c, elevationDp));
        if (view instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view).setClipToPadding(false);
        }
    }

    static TextView sectionLabel(Context c, Palette p, CharSequence text) {
        TextView label = Ui.text(c, text, 12, p.textMuted, true);
        label.setLetterSpacing(0.09f);
        label.setAllCaps(false);
        label.setPadding(dp(c, 4), dp(c, 22), 0, dp(c, 8));
        return label;
    }

    static View divider(Context c, Palette p) {
        View view = new View(c);
        view.setBackgroundColor(outlineVariant(p));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        view.setLayoutParams(params);
        return view;
    }

    // ------------------------------------------------------------------ chips

    static TextView chip(Context c, Palette p, CharSequence label, boolean selected,
                         Runnable action) {
        TextView chip = Ui.text(c, label, 13, selected ? onPrimaryContainer(p) : p.textMuted,
                true);
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
        GradientDrawable bg;
        if (selected) {
            bg = gradient(c, primaryContainer(p),
                    Palette.mix(primaryContainer(p), p.accent, 0.12f), 15,
                    Palette.mix(p.accent, p.surface, 0.55f));
        } else {
            bg = Ui.rounded(c, Color.TRANSPARENT, 15);
            bg.setStroke(dp(c, 1), outlineVariant(p));
        }
        chip.setTextColor(selected ? onPrimaryContainer(p) : p.textMuted);
        int ripple = selected ? ((p.accent & 0x00FFFFFF) | 0x30000000)
                : ((p.text & 0x00FFFFFF) | 0x14000000);
        chip.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), bg, mask(c, 15)));
    }

    /** Segmented control: a sunken track with a raised, shadowed selected pill. */
    static LinearLayout segmented(Context c, Palette p, CharSequence[] labels, int selected,
                                  final IntAction action) {
        LinearLayout track = Ui.horizontal(c);
        GradientDrawable bg = Ui.rounded(c, surfaceLow(p), 24);
        bg.setStroke(dp(c, 1), outlineVariant(p));
        track.setBackground(bg);
        track.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final boolean on = i == selected;
            TextView segment = Ui.text(c, labels[i], 13,
                    on ? onPrimaryContainer(p) : p.textMuted, true);
            segment.setGravity(Gravity.CENTER);
            segment.setSingleLine(true);
            segment.setPadding(dp(c, 6), dp(c, 10), dp(c, 6), dp(c, 10));
            if (on) {
                segment.setBackground(gradient(c, surfaceHigh(p),
                        p.dark ? p.surface : surfaceLow(p), 20,
                        Palette.mix(p.accent, p.surface, 0.6f)));
                segment.setElevation(dp(c, 2));
            } else {
                segment.setClickable(true);
                segment.setFocusable(true);
                segment.setBackground(new RippleDrawable(
                        ColorStateList.valueOf((p.text & 0x00FFFFFF) | 0x14000000),
                        Ui.rounded(c, Color.TRANSPARENT, 20), mask(c, 20)));
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
        GradientDrawable bg = gradient(c, p.accent, p.accentPressed, 22, 0);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), bg, mask(c, 22)));
        button.setElevation(dp(c, 2.5f));
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
        GradientDrawable bg = gradient(c, primaryContainer(p),
                Palette.mix(primaryContainer(p), p.surface, 0.35f), 22,
                Palette.mix(p.accent, p.surface, 0.6f));
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf((p.accent & 0x00FFFFFF) | 0x28000000), bg, mask(c, 22)));
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
        TextView titleView = Ui.text(c, title, 14.5f, p.text, true);
        texts.addView(titleView);
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

        row.setBackground(new RippleDrawable(
                ColorStateList.valueOf((p.text & 0x00FFFFFF) | 0x12000000),
                Ui.rounded(c, Color.TRANSPARENT, 14), mask(c, 14)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> toggle.toggle());
        return row;
    }

    /** Large tappable tile for the settings home grid. */
    static View tile(Context c, Palette p, int iconRes, CharSequence title,
                     CharSequence subtitle, Runnable action) {
        FrameLayout tile = new FrameLayout(c);
        applySurface(c, tile, p, 22, 3f);
        tile.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));

        LinearLayout content = Ui.vertical(c);
        FrameLayout badge = new FrameLayout(c);
        GradientDrawable badgeBg = gradient(c, primaryContainer(p),
                Palette.mix(primaryContainer(p), p.bg, 0.2f), 14,
                Palette.mix(p.accent, p.surface, 0.55f));
        badge.setBackground(badgeBg);
        badge.setElevation(dp(c, 1.5f));
        badge.addView(icon(c, iconRes, onPrimaryContainer(p)),
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
        tile.setForeground(rippleMask(c, (p.accent & 0x00FFFFFF) | 0x24000000, 22));
        tile.setOnClickListener(v -> action.run());
        pressScale(tile);
        return tile;
    }

    /** Compact list row with a leading icon and a chevron. */
    static View navRow(Context c, Palette p, int iconRes, CharSequence title,
                       CharSequence subtitle, Runnable action) {
        LinearLayout row = Ui.horizontal(c);
        row.setPadding(dp(c, 14), dp(c, 13), dp(c, 12), dp(c, 13));

        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.rounded(c, primaryContainer(p), 12));
        badge.addView(icon(c, iconRes, onPrimaryContainer(p)),
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
                ColorStateList.valueOf((p.text & 0x00FFFFFF) | 0x14000000),
                Ui.rounded(c, Color.TRANSPARENT, 16), mask(c, 16)));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        return row;
    }

    static int dp(Context c, float value) {
        return Ui.dp(c, value);
    }
}
