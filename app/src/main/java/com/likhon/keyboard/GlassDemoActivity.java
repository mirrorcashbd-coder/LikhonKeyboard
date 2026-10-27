package com.likhon.keyboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * The reference-inspired showcase: every liquid-glass component on one screen,
 * floating over a blurred photo.
 *
 * <p>Status and navigation bars are transparent and the photo runs edge to
 * edge, exactly like the control centre shot this was modelled on.
 */
public class GlassDemoActivity extends Activity {

    private final ArrayList<GlassSurfaceView> surfaces = new ArrayList<>();

    private Palette palette;
    private Bitmap backdropBitmap;
    private GlassBackdrop backdrop;
    private View backdropView;
    private LiquidGlassFloatingToolbar toolbar;
    private LiquidGlassBottomNavigation navigation;
    private float density;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        Prefs prefs = new Prefs(this);
        palette = Palette.resolve(this, prefs.theme(), prefs.accent());

        styleWindow();
        backdropBitmap = loadBackdrop();

        FrameLayout root = new FrameLayout(this);
        backdropView = new BackdropView(this, backdropBitmap);
        root.addView(backdropView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(buildHeader(), headerParams());
        root.addView(buildTopRow(), topParams(150));
        root.addView(buildFocusPill(), topParams(318));
        root.addView(buildBottomRow(), topParams(452));

        toolbar = buildToolbar();
        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        toolbarParams.bottomMargin = Math.round(dp(114));
        root.addView(toolbar, toolbarParams);

        navigation = buildNavigation();
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                Math.round(dp(72)),
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        navParams.leftMargin = Math.round(dp(16));
        navParams.rightMargin = Math.round(dp(16));
        navParams.bottomMargin = Math.round(dp(30));
        root.addView(navigation, navParams);

        setContentView(root);
        backdropView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) ->
                bindBackdrop());
        root.post(this::bindBackdrop);
        root.postDelayed(() -> {
            toolbar.show();
            navigation.setAlpha(0f);
            navigation.setTranslationY(dp(28));
            navigation.animate().alpha(1f).translationY(0f)
                    .setDuration(460)
                    .start();
        }, 120);
    }

    private void styleWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF101418));
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    // --------------------------------------------------------------- backdrop

    private Bitmap loadBackdrop() {
        Bitmap photo = Wallpaper.blurred(this);
        Bitmap copy = photo == null || photo.isRecycled()
                ? null : photo.copy(Bitmap.Config.ARGB_8888, true);
        photo = copy != null ? copy : artwork();
        Canvas canvas = new Canvas(photo);
        canvas.drawColor(((palette.dark ? 0x30 : 0x40) << 24)
                | (palette.dark ? 0x000000 : 0xFFFFFF));
        canvas.drawColor((0x10 << 24) | (palette.accent & 0x00FFFFFF));
        return photo;
    }

    /** A photo-like scene for phones whose wallpaper cannot be read. */
    private Bitmap artwork() {
        int width = 220;
        int height = 420;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, width, height,
                0xFF3A93DE, 0xFF1B4C99, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        flower(canvas, 26, 40, 62, 0xFFB12A32);
        flower(canvas, 176, 96, 76, 0xFFDC4A34);
        flower(canvas, 40, 190, 58, 0xFF3E8B55);
        flower(canvas, 196, 300, 84, 0xFFC9362E);
        flower(canvas, 88, 350, 70, 0xFF2F6D3E);
        flower(canvas, 120, 120, 44, 0xFFF0EDE6);
        flower(canvas, 150, 236, 52, 0xFFE7DFCF);

        GlassBackdrop.blur(bitmap);
        return bitmap;
    }

    private static void flower(Canvas canvas, float cx, float cy, float radius, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{color, (color & 0x00FFFFFF)}, new float[]{0.35f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private void bindBackdrop() {
        if (backdropBitmap == null || backdropView.getWidth() == 0) {
            return;
        }
        int[] location = new int[2];
        backdropView.getLocationInWindow(location);
        float scale = Math.max(backdropView.getWidth() / (float) backdropBitmap.getWidth(),
                backdropView.getHeight() / (float) backdropBitmap.getHeight());
        float drawW = backdropBitmap.getWidth() * scale;
        float drawH = backdropBitmap.getHeight() * scale;
        float dx = (backdropView.getWidth() - drawW) / 2f;
        float dy = (backdropView.getHeight() - drawH) / 2f;

        backdrop = GlassBackdrop.of(backdropBitmap, drawW, drawH);
        if (backdrop == null) {
            return;
        }
        backdrop.setOrigin(location[0] + dx, location[1] + dy, drawW, drawH);
        for (GlassSurfaceView surface : surfaces) {
            surface.setBackdrop(backdrop);
        }
        if (toolbar != null) {
            toolbar.setBackdrop(backdrop);
        }
        if (navigation != null) {
            navigation.setBackdrop(backdrop);
        }
    }

    /** The backdrop view: the blurred photo, centre-cropped to fill the screen. */
    private static final class BackdropView extends View {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
                | Paint.FILTER_BITMAP_FLAG);
        private android.graphics.Rect src = new android.graphics.Rect();
        private android.graphics.RectF dst = new android.graphics.RectF();

        BackdropView(android.content.Context context, Bitmap bitmap) {
            super(context);
            this.bitmap = bitmap;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bitmap == null) {
                canvas.drawColor(0xFF173B66);
                return;
            }
            float scale = Math.max(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            float left = (getWidth() - w) / 2f;
            float top = (getHeight() - h) / 2f;
            src.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            dst.set(left, top, left + w, top + h);
            canvas.drawBitmap(bitmap, src, dst, paint);
        }
    }

    // -------------------------------------------------------------- component

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LiquidGlassIconButton back = new LiquidGlassIconButton(this);
        back.setButtonSizeDp(46);
        back.setIconSizeDp(21);
        back.setIcon(R.drawable.ic_back);
        back.setIconTint(0xFFFFFFFF);
        back.setGlassElevation(8);
        back.setContentDescription(getString(R.string.action_back));
        back.setOnClickListener(v -> finish());
        surfaces.add(back);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                Math.round(dp(46)), Math.round(dp(46)));
        header.addView(back, backParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(R.string.demo_title);
        title.setTextSize(26f);
        title.setTextColor(0xFFFFFFFF);
        title.setShadowLayer(dp(8), 0, dp(2), 0x99000000);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        texts.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.demo_subtitle);
        subtitle.setTextSize(12.5f);
        subtitle.setTextColor(0xD9FFFFFF);
        subtitle.setShadowLayer(dp(6), 0, dp(1), 0x99000000);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Math.round(dp(3));
        texts.addView(subtitle, subtitleParams);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = Math.round(dp(12));
        header.addView(texts, textParams);
        return header;
    }

    private FrameLayout.LayoutParams headerParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        params.topMargin = Math.round(dp(42));
        params.leftMargin = Math.round(dp(18));
        params.rightMargin = Math.round(dp(18));
        return params;
    }

    private LinearLayout buildTopRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LiquidGlassCircleButton lock = circle(112, 44, R.drawable.ic_lock,
                getString(R.string.demo_lock), 0xFF49C9F2);
        LiquidGlassCircleButton screens = circle(112, 44, R.drawable.ic_screens,
                getString(R.string.demo_screens), 0xFF3B82F6);
        row.addView(lock, circleParams(0));
        LinearLayout.LayoutParams gap = circleParams(22);
        row.addView(screens, gap);
        lock.setOnClickListener(v -> toast(getString(R.string.demo_lock)));
        screens.setOnClickListener(v -> toast(getString(R.string.demo_screens)));
        return row;
    }

    private LinearLayout buildBottomRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LiquidGlassCircleButton bell = circle(112, 42, R.drawable.ic_bell,
                getString(R.string.demo_bell), 0xFFB6A8A0);
        LiquidGlassCircleButton grid = circle(112, 42, R.drawable.ic_grid,
                getString(R.string.demo_grid), 0xFF8ECBFF);
        row.addView(bell, circleParams(0));
        row.addView(grid, circleParams(22));
        bell.setOnClickListener(v -> LiquidGlassDialog.show(this, palette,
                getString(R.string.demo_dialog_title),
                getString(R.string.demo_dialog_message), null));
        grid.setOnClickListener(v -> toast(getString(R.string.demo_grid)));
        return row;
    }

    private LiquidGlassCircleButton circle(float sizeDp, float iconDp, int icon,
                                           CharSequence description, int tint) {
        LiquidGlassCircleButton button = new LiquidGlassCircleButton(this, sizeDp, iconDp);
        button.setIcon(icon);
        button.setIconTint(0xFFFFFFFF);
        button.setGlassTint(tint & 0x00FFFFFF);
        button.setContentDescription(description);
        surfaces.add(button);
        return button;
    }

    private LinearLayout.LayoutParams circleParams(int gapDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(dp(112)), Math.round(dp(112)));
        params.leftMargin = Math.round(dp(gapDp));
        params.rightMargin = Math.round(dp(gapDp));
        return params;
    }

    private LiquidGlassPillButton buildFocusPill() {
        LiquidGlassPillButton pill = new LiquidGlassPillButton(this);
        pill.setLabel(getString(R.string.demo_focus));
        pill.setIcon(R.drawable.ic_moon);
        pill.setIconTint(0xFFFFFFFF);
        pill.setIconSizeDp(36f);
        pill.setHeightDp(104f);
        pill.setBadgeSizeDp(64f);
        pill.setContentDescription(getString(R.string.demo_focus));
        surfaces.add(pill);
        pill.setOnClickListener(v -> LiquidGlassDialog.show(this, palette,
                getString(R.string.demo_focus), getString(R.string.demo_dialog_message),
                null));
        return pill;
    }

    private FrameLayout.LayoutParams topParams(int topDp) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = Math.round(dp(topDp));
        return params;
    }

    private LiquidGlassFloatingToolbar buildToolbar() {
        LiquidGlassFloatingToolbar bar = new LiquidGlassFloatingToolbar(this, palette);
        bar.addAction(R.drawable.ic_undo, getString(R.string.action_undo),
                () -> toast(getString(R.string.action_undo)));
        bar.addAction(R.drawable.ic_clipboard, getString(R.string.action_clipboard),
                () -> toast(getString(R.string.action_clipboard)));
        bar.addAction(R.drawable.ic_translate, getString(R.string.action_translate),
                () -> toast(getString(R.string.action_translate)));
        bar.addAction(R.drawable.ic_settings, getString(R.string.demo_settings),
                () -> startActivity(new Intent(this, SettingsActivity.class)));
        return bar;
    }

    private LiquidGlassBottomNavigation buildNavigation() {
        LiquidGlassBottomNavigation bar = new LiquidGlassBottomNavigation(this, palette);
        bar.addItem(R.drawable.ic_keyboard, getString(R.string.demo_nav_keys),
                () -> toast(getString(R.string.demo_nav_keys)));
        bar.addItem(R.drawable.ic_globe, getString(R.string.demo_nav_language),
                () -> toast(getString(R.string.demo_nav_language)));
        bar.addItem(R.drawable.ic_mic, getString(R.string.demo_nav_voice),
                () -> toast(getString(R.string.demo_nav_voice)));
        bar.addItem(R.drawable.ic_tune, getString(R.string.demo_nav_style),
                () -> toast(getString(R.string.demo_nav_style)));
        bar.addItem(R.drawable.ic_info, getString(R.string.demo_nav_about),
                () -> LiquidGlassDialog.show(this, palette,
                        getString(R.string.demo_title),
                        getString(R.string.demo_dialog_message), null));
        return bar;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private float dp(float value) {
        return value * density;
    }
}
