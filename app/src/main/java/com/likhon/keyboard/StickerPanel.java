package com.likhon.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The sticker panel that opens above the keys.
 *
 * <p>It has three parts: a search field, a row of pack chips and a six-column
 * grid. Tapping the search field lets the user type with the keyboard itself —
 * the IME routes key presses into {@link #typeToSearch(String)} instead of the
 * editor — so searching never needs a second input window. Long-press a
 * sticker to keep it in Favourites.
 */
final class StickerPanel extends LinearLayout {

    interface Listener {
        /** A sticker was picked; the IME sends it to the app. */
        void onSticker(StickerLibrary.Item item);

        /** The ✕ in the header, or the panel should close. */
        void onClose();

        /** Search field got focus: the IME shows the letters and hides suggestions. */
        void onSearchStarted();

        /** Search field lost focus. */
        void onSearchEnded();
    }

    private static final int COLUMNS = 6;
    private static final int GRID_HEIGHT_DP = 144;
    private static final int TILE_HEIGHT_DP = 46;

    private final Context context;
    private final Palette palette;
    private final StickerStore store;
    private final boolean haptic;
    private final StickerLibrary library;
    private final Listener listener;
    private final boolean banglaUi;

    private final LinearLayout header;
    private final LinearLayout searchBox;
    private final TextView searchText;
    private final View caret;
    private final ImageView clearSearch;
    private final LinearLayout chipRow;
    private final GridView grid;
    private final TextView empty;
    private final StickerAdapter adapter = new StickerAdapter();

    private final List<StickerLibrary.Item> shown = new ArrayList<>();

    private boolean searching;
    private String query = "";
    /** null = recents, "#fav" = favourites, otherwise a pack id. */
    private String selected;
    private AlphaAnimation caretAnimation;

    StickerPanel(Context context, Palette palette, StickerStore store, boolean haptic,
                 Listener listener) {
        super(context);
        this.context = context;
        this.palette = palette;
        this.store = store;
        this.haptic = haptic;
        this.listener = listener;
        this.library = StickerLibrary.get(context);
        this.banglaUi = isBanglaUi();
        this.selected = library.packs().isEmpty() ? null : library.packs().get(0).id;

        setOrientation(VERTICAL);
        setPadding(dp(8), dp(4), dp(8), dp(6));

        // ------------------------------------------------------------ header
        header = Ui.horizontal(context);
        TextView title = Ui.text(context, text(R.string.sticker_title), 14, palette.text, true);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(4), 0, dp(10), 0);
        header.addView(title);

        searchBox = Ui.horizontal(context);
        searchBox.setPadding(dp(9), 0, dp(6), 0);
        searchBox.setMinimumHeight(dp(30));
        searchBox.setClickable(true);
        searchBox.setFocusable(true);
        searchBox.setOnClickListener(v -> beginSearch());

        ImageView searchIcon = M3.icon(context, R.drawable.ic_search, palette.textMuted);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        iconParams.rightMargin = dp(6);
        searchBox.addView(searchIcon, iconParams);

        searchText = Ui.text(context, text(R.string.sticker_search_hint), 12,
                palette.textMuted, false);
        searchText.setSingleLine(true);
        searchText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        searchBox.addView(searchText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        caret = new View(context);
        caret.setBackground(Ui.rounded(context, palette.accent, 1));
        LinearLayout.LayoutParams caretParams = new LinearLayout.LayoutParams(dp(2), dp(14));
        caretParams.leftMargin = dp(1);
        caretParams.rightMargin = dp(2);
        searchBox.addView(caret, caretParams);

        clearSearch = M3.icon(context, R.drawable.ic_close, palette.textMuted);
        clearSearch.setPadding(dp(3), dp(3), dp(3), dp(3));
        clearSearch.setContentDescription(text(R.string.sticker_clear));
        clearSearch.setBackground(Ui.ripple(context, Color.TRANSPARENT, 11,
                (palette.text & 0x00FFFFFF) | 0x1A000000));
        clearSearch.setOnClickListener(v -> clearQuery());
        searchBox.addView(clearSearch, new LinearLayout.LayoutParams(dp(20), dp(20)));

        header.addView(searchBox, new LinearLayout.LayoutParams(
                0, dp(30), 1f));

        ImageView close = M3.icon(context, R.drawable.ic_close, palette.textMuted);
        close.setPadding(dp(7), dp(7), dp(7), dp(7));
        close.setContentDescription(text(R.string.sticker_close));
        close.setBackground(Ui.ripple(context, Color.TRANSPARENT, 15,
                (palette.text & 0x00FFFFFF) | 0x1A000000));
        close.setOnClickListener(v -> listener.onClose());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        closeParams.leftMargin = dp(4);
        header.addView(close, closeParams);
        addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        // ------------------------------------------------------------- chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipRow = Ui.horizontal(context);
        chipRow.setPadding(0, dp(2), dp(4), dp(2));
        chipScroll.addView(chipRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(chipScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        // -------------------------------------------------------------- grid
        FrameLayout gridFrame = new FrameLayout(context);
        grid = new GridView(context);
        grid.setNumColumns(COLUMNS);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(4));
        grid.setVerticalSpacing(dp(3));
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(2), 0, 0);
        grid.setSelector(new ColorDrawable(0x00000000));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shown.size()) {
                if (haptic) {
                    view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
                if (!motionDisabled()) {
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(80)
                            .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(160)
                                    .setInterpolator(
                                            new android.view.animation.OvershootInterpolator(2f))
                                    .start())
                            .start();
                }
                pick(shown.get(position));
            }
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shown.size()) {
                toggleFavorite(shown.get(position));
                return true;
            }
            return false;
        });
        gridFrame.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(GRID_HEIGHT_DP)));

        empty = Ui.text(context, "", 12, palette.textMuted, false);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        gridFrame.addView(empty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(GRID_HEIGHT_DP)));
        addView(gridFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(GRID_HEIGHT_DP)));

        applySearchStyle();
        buildChips();
        applyFilter();

        if (!motionDisabled()) {
            setAlpha(0f);
            setTranslationY(dp(10));
            post(() -> animate().alpha(1f).translationY(0f).setDuration(160).start());
        }
    }

    // ------------------------------------------------------------------ data

    private void buildChips() {
        chipRow.removeAllViews();
        addChip(text(R.string.sticker_recent), null);
        addChip(text(R.string.sticker_favorites), "#fav");
        for (StickerLibrary.Pack pack : library.packs()) {
            addChip(pack.name(banglaUi), pack.id);
        }
    }

    private void addChip(CharSequence label, String key) {
        boolean on = keyEquals(selected, key);
        TextView chip = Ui.text(context, label, 11.5f, on ? palette.onAccent : palette.text, true);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(11), dp(6), dp(11), dp(6));
        if (on) {
            chip.setBackground(Surfaces.rounded(context, palette.accent, 14, 0));
        } else {
            chip.setBackground(Surfaces.control(context, palette, 14, palette.surface,
                    Surfaces.border(palette), (palette.text & 0x00FFFFFF) | 0x14000000));
        }
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            selected = key;
            buildChips();
            if (searching) {
                stopSearch();
            }
            applyFilter();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(5);
        chipRow.addView(chip, params);
    }

    private static boolean keyEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** Rebuilds the visible list from the current chip and query. */
    private void applyFilter() {
        shown.clear();
        if (!query.isEmpty()) {
            shown.addAll(library.search(query));
        } else if (selected == null) {
            shown.addAll(library.byIds(store.recents()));
        } else if ("#fav".equals(selected)) {
            List<String> ids = new ArrayList<>(store.favorites());
            // Newest favourites first.
            java.util.Collections.reverse(ids);
            shown.addAll(library.byIds(ids));
        } else {
            shown.addAll(library.ofPack(selected));
        }
        adapter.notifyDataSetChanged();

        boolean none = shown.isEmpty();
        grid.setVisibility(none ? View.GONE : View.VISIBLE);
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) {
            if (!query.isEmpty()) {
                empty.setText(text(R.string.sticker_no_result));
            } else if ("#fav".equals(selected)) {
                empty.setText(text(R.string.sticker_no_favorites));
            } else if (selected == null) {
                empty.setText(text(R.string.sticker_no_recent));
            } else {
                empty.setText(text(R.string.sticker_no_result));
            }
        }
        if (grid.getVisibility() == View.VISIBLE) {
            grid.setSelection(0);
        }
    }

    private void pick(StickerLibrary.Item item) {
        store.recordUse(item.id);
        listener.onSticker(item);
        if (selected == null) {
            applyFilter();
        }
    }

    private void toggleFavorite(StickerLibrary.Item item) {
        boolean nowFavorite = store.toggleFavorite(item.id);
        Toast.makeText(context, nowFavorite
                ? R.string.sticker_fav_on : R.string.sticker_fav_off, Toast.LENGTH_SHORT).show();
        adapter.notifyDataSetChanged();
        if ("#fav".equals(selected)) {
            applyFilter();
        }
    }

    // ---------------------------------------------------------------- search

    boolean isSearching() {
        return searching;
    }

    private void beginSearch() {
        if (!searching) {
            searching = true;
            applySearchStyle();
            startCaret();
            listener.onSearchStarted();
        }
    }

    void typeToSearch(String text) {
        if (!searching || text == null || text.isEmpty()) {
            return;
        }
        if (text.length() == 1 && (text.charAt(0) == ' ' || text.charAt(0) == '\n')
                && query.isEmpty()) {
            return;
        }
        query = query + text;
        afterQueryChange();
    }

    void backspaceSearch() {
        if (!searching) {
            return;
        }
        if (query.isEmpty()) {
            stopSearch();
            return;
        }
        int cut = query.offsetByCodePoints(query.length(), -1);
        query = query.substring(0, cut);
        afterQueryChange();
    }

    void clearQuery() {
        query = "";
        afterQueryChange();
    }

    void stopSearch() {
        if (!searching) {
            return;
        }
        searching = false;
        query = "";
        applySearchStyle();
        stopCaret();
        applyFilter();
        listener.onSearchEnded();
    }

    private void afterQueryChange() {
        searchText.setText(query.isEmpty() ? emptySearchLabel() : query);
        searchText.setTextColor(query.isEmpty() ? palette.textMuted : palette.text);
        clearSearch.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        applyFilter();
        if (grid.getVisibility() == View.VISIBLE) {
            grid.smoothScrollToPosition(0);
        }
    }

    private void applySearchStyle() {
        if (searching) {
            searchBox.setBackground(Surfaces.control(context, palette, 15,
                    Surfaces.container(palette), palette.accent, 0));
            searchText.setText(query.isEmpty() ? emptySearchLabel() : query);
            searchText.setTextColor(query.isEmpty() ? palette.textMuted : palette.text);
        } else {
            searchBox.setBackground(Surfaces.control(context, palette, 15,
                    Surfaces.muted(palette), Surfaces.border(palette),
                    (palette.text & 0x00FFFFFF) | 0x14000000));
            searchText.setText(query.isEmpty()
                    ? text(R.string.sticker_search_hint) : query);
            searchText.setTextColor(palette.textMuted);
        }
        caret.setVisibility(searching ? View.VISIBLE : View.GONE);
        clearSearch.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    private String emptySearchLabel() {
        return text(searching ? R.string.sticker_search_tip : R.string.sticker_search_hint);
    }

    private void startCaret() {
        stopCaret();
        caretAnimation = new AlphaAnimation(1f, 0f);
        caretAnimation.setDuration(520);
        caretAnimation.setStartOffset(360);
        caretAnimation.setRepeatMode(AlphaAnimation.REVERSE);
        caretAnimation.setRepeatCount(AlphaAnimation.INFINITE);
        caret.startAnimation(caretAnimation);
    }

    private void stopCaret() {
        caret.clearAnimation();
        if (caretAnimation != null) {
            caretAnimation.cancel();
            caretAnimation = null;
        }
    }

    // --------------------------------------------------------------- adapter

    private final class StickerAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FrameLayout cell;
            ImageView image;
            View dot;
            if (convertView == null) {
                cell = new FrameLayout(context);
                cell.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(TILE_HEIGHT_DP)));
                cell.setPadding(dp(5), dp(5), dp(5), dp(5));
                cell.setBackground(Surfaces.control(context, palette, 13,
                        Palette.mix(palette.surface, palette.bg, palette.dark ? 0.3f : 0.45f),
                        0, (palette.text & 0x00FFFFFF) | 0x16000000));

                image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.addView(image, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                dot = new View(context);
                dot.setBackground(Ui.rounded(context, palette.accent, 2.5f));
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                        dp(5), dp(5), Gravity.TOP | Gravity.END);
                dotParams.topMargin = dp(3);
                dotParams.rightMargin = dp(3);
                cell.addView(dot, dotParams);

                cell.setTag(new Holder(image, dot));
            } else {
                cell = (FrameLayout) convertView;
                Holder holder = (Holder) cell.getTag();
                image = holder.image;
                dot = holder.dot;
            }
            if (position >= 0 && position < shown.size()) {
                StickerLibrary.Item item = shown.get(position);
                image.setImageBitmap(library.bitmap(item));
                dot.setVisibility(store.isFavorite(item.id) ? View.VISIBLE : View.INVISIBLE);
                cell.setContentDescription(item.name(banglaUi));
            }
            return cell;
        }
    }

    private static final class Holder {
        final ImageView image;
        final View dot;

        Holder(ImageView image, View dot) {
            this.image = image;
            this.dot = dot;
        }
    }

    // --------------------------------------------------------------- helpers

    private boolean isBanglaUi() {
        Locale locale = Locale.getDefault();
        return locale != null && "bn".equals(locale.getLanguage());
    }

    private String text(int id) {
        return context.getString(id);
    }

    private boolean motionDisabled() {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
