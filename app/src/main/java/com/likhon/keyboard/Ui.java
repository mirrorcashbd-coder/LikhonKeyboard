package com.likhon.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building the settings screen in code. */
final class Ui {

    private Ui() {
    }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static float sp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }

    static GradientDrawable rounded(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundedStroke(Context context, int color, float radiusDp,
                                          int strokeColor, float strokeWidthDp) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(dp(context, strokeWidthDp), strokeColor);
        return drawable;
    }

    static RippleDrawable ripple(Context context, int surface, float radiusDp, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),
                rounded(context, surface, radiusDp), null);
    }

    static TextView text(Context context, CharSequence value, float sizeSp, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(context, 3), 1f);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
        return view;
    }

    static LinearLayout card(Context context, Palette palette) {
        return M3.card(context, palette);
    }

    static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static View dot(Context context, int color, int sizeDp) {
        View dot = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(context, sizeDp), dp(context, sizeDp));
        dot.setLayoutParams(params);
        dot.setBackground(rounded(context, color, sizeDp / 2f));
        return dot;
    }

    static TextView button(Context context, Palette palette, String label, boolean primary,
                           Runnable action) {
        return primary ? M3.filledButton(context, palette, label, action)
                : M3.tonalButton(context, palette, label, action);
    }

    /** Text that looks and behaves like a settings row. */
    static LinearLayout settingRow(Context context, Palette palette, String title, String subtitle) {
        LinearLayout row = horizontal(context);
        row.setPadding(0, dp(context, 10), 0, dp(context, 10));
        LinearLayout texts = vertical(context);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView titleView = text(context, title, 15, palette.text, true);
        texts.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subView = text(context, subtitle, 12, palette.textMuted, false);
            subView.setPadding(0, dp(context, 2), 0, 0);
            texts.addView(subView);
        }
        row.addView(texts);
        return row;
    }
}
