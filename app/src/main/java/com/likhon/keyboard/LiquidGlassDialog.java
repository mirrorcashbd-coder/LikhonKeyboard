package com.likhon.keyboard;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A floating glass dialog with a blurred background where the platform allows it. */
final class LiquidGlassDialog extends Dialog {

    private final LiquidGlassPanel panel;

    private LiquidGlassDialog(Context context, Palette palette, CharSequence title,
                              CharSequence message, Runnable confirm) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        final float density = context.getResources().getDisplayMetrics().density;

        panel = new LiquidGlassPanel(context, palette);
        panel.setRadiusDp(34);
        panel.setBackdrop(GlassBackdrop.wallpaper(context));
        int pad = dp(density, 24);
        panel.setPadding(pad, dp(density, 26), pad, dp(density, 18));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(21f);
        titleView.setTextColor(palette.text);
        titleView.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        column.addView(titleView);

        if (message != null && message.length() > 0) {
            TextView messageView = new TextView(context);
            messageView.setText(message);
            messageView.setTextSize(14f);
            messageView.setTextColor(palette.textMuted);
            messageView.setLineSpacing(dp(density, 3), 1f);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = dp(density, 10);
            column.addView(messageView, messageParams);
        }

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(density, 22);
        column.addView(buttons, buttonsParams);

        TextView cancel = dialogButton(context, palette,
                context.getString(R.string.dialog_cancel), 0,
                (palette.text & 0x00FFFFFF) | 0x1A000000);
        cancel.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelParams.rightMargin = dp(density, 10);
        buttons.addView(cancel, cancelParams);

        TextView ok = dialogButton(context, palette,
                context.getString(R.string.dialog_ok), palette.onAccent,
                0x33FFFFFF);
        ok.setBackground(Glass.control(context, palette, 22, palette.accent, 0x33FFFFFF));
        ok.setOnClickListener(v -> {
            dismiss();
            if (confirm != null) {
                confirm.run();
            }
        });
        buttons.addView(ok, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        panel.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout root = new FrameLayout(context);
        int side = dp(density, 26);
        root.setPadding(side, side, side, side);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(panel, panelParams);
        setContentView(root);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.40f;
            window.setAttributes(params);
            window.setWindowAnimations(0);
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    // Real cross-window blur behind the dialog when the device
                    // supports it; transparent glass elsewhere.
                    window.setBackgroundBlurRadius(24);
                } catch (Throwable ignored) {
                }
            }
        }
        setCanceledOnTouchOutside(true);
    }

    static LiquidGlassDialog show(Activity activity, Palette palette, CharSequence title,
                                  CharSequence message, Runnable confirm) {
        LiquidGlassDialog dialog = new LiquidGlassDialog(activity, palette, title,
                message, confirm);
        dialog.show();
        return dialog;
    }

    private static TextView dialogButton(Context context, Palette palette, String label,
                                         int color, int ripple) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(14.5f);
        view.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        view.setTextColor(color);
        float density = context.getResources().getDisplayMetrics().density;
        view.setPadding(dp(density, 20), dp(density, 12),
                dp(density, 20), dp(density, 12));
        view.setGravity(Gravity.CENTER);
        view.setBackground(Glass.control(context, palette, 22, 0, ripple));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    @Override
    public void show() {
        super.show();
        boolean motionOff = LiquidGlass.motionDisabled(getContext());
        if (motionOff) {
            panel.setAlpha(1f);
            panel.setScaleX(1f);
            panel.setScaleY(1f);
            return;
        }
        panel.setAlpha(0f);
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(24f * getContext().getResources()
                .getDisplayMetrics().density);
        panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(340)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    private static int dp(float density, float value) {
        return Math.round(value * density);
    }

    /** Exposed for the demo screen. */
    View content() {
        return panel;
    }
}
