package com.likhon.keyboard;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A solid, rounded confirmation dialog in the app's own surface language. */
final class AppDialog extends Dialog {

    private final LinearLayout panel;

    private AppDialog(Context context, Palette palette, CharSequence title,
                      CharSequence message, CharSequence positive, Runnable onPositive,
                      CharSequence negative, Runnable onNegative) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        float density = context.getResources().getDisplayMetrics().density;

        panel = Ui.vertical(context);
        panel.setBackground(Surfaces.surface(context, palette, 26));
        int pad = dp(density, 22);
        panel.setPadding(pad, dp(density, 22), pad, dp(density, 14));

        TextView titleView = Ui.text(context, title, 20, palette.text, true);
        panel.addView(titleView);

        if (message != null && message.length() > 0) {
            TextView messageView = Ui.text(context, message, 14, palette.textMuted, false);
            messageView.setLineSpacing(dp(density, 4), 1f);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            messageParams.topMargin = dp(density, 8);
            panel.addView(messageView, messageParams);
        }

        LinearLayout buttons = Ui.horizontal(context);
        buttons.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(density, 20);
        panel.addView(buttons, buttonsParams);

        if (negative != null) {
            TextView cancel = textButton(context, palette, negative, palette.textMuted);
            cancel.setOnClickListener(v -> {
                dismiss();
                if (onNegative != null) {
                    onNegative.run();
                }
            });
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cancelParams.rightMargin = dp(density, 8);
            buttons.addView(cancel, cancelParams);
        }

        TextView ok = filledButton(context, palette, positive);
        ok.setOnClickListener(v -> {
            dismiss();
            if (onPositive != null) {
                onPositive.run();
            }
        });
        buttons.addView(ok, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout root = new FrameLayout(context);
        int side = dp(density, 24);
        root.setPadding(side, side, side, side);
        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.55f;
            window.setAttributes(params);
            window.setWindowAnimations(0);
        }
        setCanceledOnTouchOutside(true);
    }

    private static TextView textButton(Context context, Palette palette, CharSequence label,
                                       int color) {
        TextView view = Ui.text(context, label, 14, color, true);
        float density = context.getResources().getDisplayMetrics().density;
        view.setPadding(dp(density, 16), dp(density, 12),
                dp(density, 16), dp(density, 12));
        view.setGravity(Gravity.CENTER);
        view.setBackground(Surfaces.ripple(context, 20,
                (palette.text & 0x00FFFFFF) | 0x14000000));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private static TextView filledButton(Context context, Palette palette, CharSequence label) {
        TextView view = Ui.text(context, label, 14, palette.onAccent, true);
        float density = context.getResources().getDisplayMetrics().density;
        view.setPadding(dp(density, 18), dp(density, 12),
                dp(density, 18), dp(density, 12));
        view.setGravity(Gravity.CENTER);
        view.setBackground(Surfaces.control(context, palette, 20, palette.accent, 0, 0x33FFFFFF));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    /** A plain notice: one "OK" button and nothing else. */
    static AppDialog show(Activity activity, Palette palette, CharSequence title,
                          CharSequence message) {
        return show(activity, palette, title, message, (Runnable) null);
    }

    /** One action: the positive button is "OK" and there is no cancel. */
    static AppDialog show(Activity activity, Palette palette, CharSequence title,
                          CharSequence message, Runnable confirm) {
        return show(activity, palette, title, message,
                activity.getString(R.string.dialog_ok), confirm, null, null);
    }

    static AppDialog show(Activity activity, Palette palette, CharSequence title,
                          CharSequence message, CharSequence positive, Runnable onPositive,
                          CharSequence negative, Runnable onNegative) {
        AppDialog dialog = new AppDialog(activity, palette, title, message,
                positive, onPositive, negative, onNegative);
        dialog.show();
        return dialog;
    }

    @Override
    public void show() {
        super.show();
        if (motionDisabled(getContext())) {
            panel.setAlpha(1f);
            panel.setScaleX(1f);
            panel.setScaleY(1f);
            return;
        }
        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    private static int dp(float density, float value) {
        return Math.round(value * density);
    }

    /** True when the system asks for no animations (accessibility / scale 0). */
    private static boolean motionDisabled(Context context) {
        try {
            float scale = android.provider.Settings.Global.getFloat(
                    context.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Exposed for callers that want to add extra content. */
    View content() {
        return panel;
    }
}
