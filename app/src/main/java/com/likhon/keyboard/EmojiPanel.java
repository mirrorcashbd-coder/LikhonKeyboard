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
 * Emoji Studio: the whole Unicode emoji set in one searchable, colourful panel.
 *
 * <p>Unlike the old emoji page this is not a keyboard layout. It sits above the
 * keys like the sticker library, so the keyboard stays usable for search and
 * for switching back to typing. Every category has its own colour, the tiles
 * use the OpenMoji artwork, and both English and Bengali names are searchable.
 */
final class EmojiPanel extends LinearLayout {

    interface Listener {
        /** An emoji was picked; the IME types it. */
        void onEmoji(String emoji);

        void onClose();

        /** Search got focus: the IME shows the letters. */
        void onSearchStarted();

        void onSearchEnded();
    }

    private static final int COLUMNS = 5;
    private static final int GRID_HEIGHT_DP = 196;
    private static final int TILE_HEIGHT_DP = 49;

    private final Context context;
    private final Palette palette;
    private final EmojiStore store;
    private final boolean haptic;
    private final EmojiLibrary library;
    private final Listener listener;
    private final boolean banglaUi;

    private final LinearLayout searchBox;
    private final TextView searchText;
    private final View caret;
    private final ImageView clearSearch;
    private final LinearLayout chipRow;
    private final GridView grid;
    private final TextView empty;
    private final TextView hint;
    private final Adapter adapter = new Adapter();

    private final List<EmojiLibrary.Item> shown = new ArrayList<>();

    private boolean searching;
    private String query = "";
    /** null = recents, "#fav" = favourites, otherwise a group slug. */
    private String selected = null;
    private int accent = 0xFFFFB300;
    private AlphaAnimation caretAnimation;

    EmojiPanel(Context context, Palette palette, EmojiStore store, boolean haptic,
               Listener listener) {
        super(context);
        this.context = context;
        this.palette = palette;
        this.store = store;
        this.haptic = haptic;
        this.listener = listener;
        this.library = EmojiLibrary.get(context);
        this.banglaUi = isBanglaUi();

        setOrientation(VERTICAL);
        setPadding(dp(8), dp(4), dp(8), dp(6));

        // ------------------------------------------------------------- header
        LinearLayout header = Ui.horizontal(context);
        View dot = Ui.dot(context, accent, 8);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotParams.rightMargin = dp(7);
        header.addView(dot, dotParams);
        TextView title = Ui.text(context, text(R.string.emoji_title), 14,
                palette.text, true);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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

        searchText = Ui.text(context, text(R.string.emoji_search_hint), 12,
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
        clearSearch.setContentDescription(text(R.string.emoji_clear));
        clearSearch.setBackground(Ui.ripple(context, Color.TRANSPARENT, 11,
                (palette.text & 0x00FFFFFF) | 0x1A000000));
        clearSearch.setOnClickListener(v -> clearQuery());
        searchBox.addView(clearSearch, new LinearLayout.LayoutParams(dp(20), dp(20)));

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                0, dp(30), 1f);
        searchParams.leftMargin = dp(8);
        header.addView(searchBox, searchParams);

        ImageView close = M3.icon(context, R.drawable.ic_close, palette.textMuted);
        close.setPadding(dp(7), dp(7), dp(7), dp(7));
        close.setContentDescription(text(R.string.emoji_close));
        close.setBackground(Ui.ripple(context, Color.TRANSPARENT, 15,
                (palette.text & 0x00FFFFFF) | 0x1A000000));
        close.setOnClickListener(v -> listener.onClose());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        closeParams.leftMargin = dp(4);
        header.addView(close, closeParams);
        addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

        // -------------------------------------------------------------- chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chipRow = Ui.horizontal(context);
        chipRow.setPadding(0, dp(2), dp(4), dp(2));
        chipScroll.addView(chipRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(chipScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

        // --------------------------------------------------------------- grid
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
                    view.animate().scaleX(1.12f).scaleY(1.12f).setDuration(70)
                            .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(150)
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

        hint = Ui.text(context, text(R.string.emoji_hint), 10.5f, palette.textMuted, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(2), 0, 0);
        addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));

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
        addChip(text(R.string.emoji_recent), null, 0);
        addChip(text(R.string.emoji_favorites), "#fav", 0);
        for (EmojiLibrary.Group group : EmojiLibrary.GROUPS) {
            List<EmojiLibrary.Item> items = library.group(group.slug);
            String icon = items.isEmpty() ? "" : items.get(0).emoji;
            addChip(icon + "  " + text(group.labelRes), group.slug, group.accent);
        }
    }

    private void addChip(CharSequence label, String key, int chipAccent) {
        boolean on = keyEquals(selected, key);
        boolean plain = chipAccent == 0;
        int tint = plain ? palette.accent : chipAccent;
        TextView chip = Ui.text(context, label, 11.5f,
                on ? palette.text : Palette.mix(palette.text, palette.textMuted, 0.35f), on);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(11), dp(6), dp(11), dp(6));
        if (on) {
            chip.setBackground(Surfaces.control(context, palette, 14,
                    Palette.mix(palette.surface, tint, palette.dark ? 0.30f : 0.20f),
                    tint, (tint & 0x00FFFFFF) | 0x33000000));
        } else {
            chip.setBackground(Surfaces.control(context, palette, 14, palette.surface,
                    Surfaces.border(palette), (palette.text & 0x00FFFFFF) | 0x14000000));
        }
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            selected = key;
            accent = plain ? palette.accent : tint;
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

    private void applyFilter() {
        shown.clear();
        if (!query.isEmpty()) {
            shown.addAll(library.search(query));
        } else if (selected == null) {
            shown.addAll(library.byHexes(store.recents()));
        } else if ("#fav".equals(selected)) {
            List<String> hexes = new ArrayList<>(store.favorites());
            java.util.Collections.reverse(hexes);
            shown.addAll(library.byHexes(hexes));
        } else {
            shown.addAll(library.group(selected));
        }
        adapter.notifyDataSetChanged();

        boolean none = shown.isEmpty();
        grid.setVisibility(none ? View.GONE : View.VISIBLE);
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) {
            if (!query.isEmpty()) {
                empty.setText(text(R.string.emoji_no_result));
            } else if ("#fav".equals(selected)) {
                empty.setText(text(R.string.emoji_no_favorites));
            } else {
                empty.setText(text(R.string.emoji_no_recent));
            }
        }
        hint.setText(text(searching ? R.string.emoji_hint_search : R.string.emoji_hint));
        if (grid.getVisibility() == View.VISIBLE) {
            grid.setSelection(0);
        }
    }

    private void pick(EmojiLibrary.Item item) {
        store.recordUse(item.hex);
        listener.onEmoji(item.emoji);
        if (selected == null) {
            applyFilter();
        }
    }

    private void toggleFavorite(EmojiLibrary.Item item) {
        boolean nowFavorite = store.toggleFavorite(item.hex);
        Toast.makeText(context, nowFavorite
                ? R.string.emoji_fav_on : R.string.emoji_fav_off, Toast.LENGTH_SHORT).show();
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
                    ? text(R.string.emoji_search_hint) : query);
            searchText.setTextColor(palette.textMuted);
        }
        caret.setVisibility(searching ? View.VISIBLE : View.GONE);
        clearSearch.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
    }

    private String emptySearchLabel() {
        return text(searching ? R.string.emoji_search_tip : R.string.emoji_search_hint);
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

    private final class Adapter extends BaseAdapter {

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
            TextView glyph;
            View dot;
            if (convertView == null) {
                cell = new FrameLayout(context);
                cell.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(TILE_HEIGHT_DP)));
                cell.setPadding(dp(5), dp(4), dp(5), dp(4));

                image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                cell.addView(image, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                glyph = Ui.text(context, "", 22, palette.text, false);
                glyph.setGravity(Gravity.CENTER);
                glyph.setVisibility(View.GONE);
                cell.addView(glyph, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                dot = new View(context);
                dot.setBackground(Ui.rounded(context, palette.accent, 2.5f));
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                        dp(5), dp(5), Gravity.TOP | Gravity.END);
                dotParams.topMargin = dp(2);
                dotParams.rightMargin = dp(2);
                cell.addView(dot, dotParams);

                cell.setTag(new Holder(image, glyph, dot));
            } else {
                cell = (FrameLayout) convertView;
                Holder holder = (Holder) cell.getTag();
                image = holder.image;
                glyph = holder.glyph;
                dot = holder.dot;
            }
            if (position >= 0 && position < shown.size()) {
                EmojiLibrary.Item item = shown.get(position);
                int tint = accentFor(item.group);
                cell.setBackground(Surfaces.control(context, palette, 13,
                        Palette.mix(palette.surface, tint, palette.dark ? 0.16f : 0.09f),
                        (tint & 0x00FFFFFF) | 0x24000000,
                        (tint & 0x00FFFFFF) | 0x14000000));
                android.graphics.Bitmap bitmap = library.bitmap(context, item);
                if (bitmap != null) {
                    image.setVisibility(View.VISIBLE);
                    glyph.setVisibility(View.GONE);
                    image.setImageBitmap(bitmap);
                } else {
                    image.setVisibility(View.GONE);
                    glyph.setVisibility(View.VISIBLE);
                    glyph.setText(item.emoji);
                }
                dot.setVisibility(store.isFavorite(item.hex) ? View.VISIBLE : View.INVISIBLE);
                cell.setContentDescription(item.label(banglaUi));
            }
            return cell;
        }
    }

    private int accentFor(String group) {
        for (EmojiLibrary.Group candidate : EmojiLibrary.GROUPS) {
            if (candidate.slug.equals(group)) {
                return candidate.accent;
            }
        }
        return accent;
    }

    private static final class Holder {
        final ImageView image;
        final TextView glyph;
        final View dot;

        Holder(ImageView image, TextView glyph, View dot) {
            this.image = image;
            this.glyph = glyph;
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
