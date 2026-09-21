package com.anibeat.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;

/**
 * Базовый экран: содержимое + автоперерисовка при загрузке метаданных,
 * изменениях настроек/медиатеки/очереди — как реактивность React на сайте.
 */
public abstract class ScreenBase extends LinearLayout implements MainActivity.Screen {

    protected final MainActivity activity;
    private View content;
    private boolean visible;
    private boolean scheduled;
    private final Runnable refreshTask = () -> {
        scheduled = false;
        if (visible) rebuild();
    };

    private final Meta.Listener metaListener = this::scheduleRefresh;
    private final Settings.Listener settingsListener = this::scheduleRefresh;
    private final Library.Listener libraryListener = this::scheduleRefresh;
    /** Плеер меняется часто (старт/пауза/переход) — обновляем только подсветку, без пересборки. */
    private final Player.Listener playerListener = () -> {
        if (!visible) return;
        Cards.refreshPlaybackIndicators();
    };
    private final Downloads.Listener downloadsListener = this::scheduleRefresh;

    protected ScreenBase(MainActivity activity) {
        super(activity);
        this.activity = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.BG);
        setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Meta.addListener(metaListener);
        Settings.addListener(settingsListener);
        Library.addListener(libraryListener);
        Player.addListener(playerListener);
        Downloads.addListener(downloadsListener);
    }

    /** Строит содержимое экрана (вызывается один раз). */
    protected abstract View build();

    @Override
    public View view() {
        if (content == null) {
            content = build();
            addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return this;
    }

    protected View content() {
        return content;
    }

    /** Пересборка содержимого при внешних обновлениях — позиция скролла сохраняется. */
    public void rebuild() {
        int scrollY = currentScrollY();
        removeAllViews();
        content = build();
        addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (scrollY > 0) restoreScrollY(scrollY);
    }

    /** Текущая позиция вертикального скролла (0, если прокрутки нет). */
    protected int currentScrollY() {
        android.widget.ScrollView sv = findScrollView(this);
        return sv == null ? 0 : sv.getScrollY();
    }

    protected void restoreScrollY(final int y) {
        post(() -> {
            android.widget.ScrollView sv = findScrollView(ScreenBase.this);
            if (sv != null) sv.scrollTo(0, y);
        });
    }

    private static android.widget.ScrollView findScrollView(View root) {
        if (root instanceof android.widget.ScrollView) return (android.widget.ScrollView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ScrollView found = findScrollView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    protected void scheduleRefresh() {
        if (!visible || scheduled) return;
        scheduled = true;
        postDelayed(refreshTask, 350);
    }

    @Override
    public void onShow() {
        visible = true;
        scheduleRefresh();
    }

    @Override
    public void onHide() {
        visible = false;
    }

    protected int dp(float value) {
        return Theme.dp(getContext(), value);
    }

    protected float dpF(float value) {
        return Theme.dpF(getContext(), value);
    }

    protected Context ctx() {
        return getContext();
    }

    /* ------------------------------------------------------------------ */
    /* Помощники вёрстки                                                   */
    /* ------------------------------------------------------------------ */

    protected LinearLayout scroller(View content) {
        LinearLayout box = Ui.column(ctx());
        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setTag(content);
        return box;
    }

    protected android.widget.ScrollView scrollViewOf(LinearLayout box) {
        return (android.widget.ScrollView) box.getChildAt(0);
    }

    protected View sectionSpacer() {
        return Ui.spacer(ctx(), dp(32));
    }

    protected View hscroll(LinearLayout row) {
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(ctx());
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipToPadding(false);
        hs.addView(row, new android.widget.HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hs.setLayoutParams(p);
        return hs;
    }

    protected LinearLayout hrow() {
        LinearLayout row = Ui.row(ctx());
        row.setPadding(dp(16), 0, dp(16), dp(4));
        return row;
    }

    protected void addCardRow(LinearLayout parent, java.util.List<View> cards) {
        LinearLayout row = hrow();
        for (View card : cards) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(12);
            row.addView(card, p);
        }
        parent.addView(hscroll(row));
    }

    protected LinearLayout paddedColumn() {
        LinearLayout col = Ui.column(ctx());
        col.setPadding(dp(16), 0, dp(16), 0);
        return col;
    }

    protected LinearLayout gridRow(java.util.List<View> cards, int columns) {
        LinearLayout row = Ui.row(ctx());
        row.setPadding(0, 0, 0, 0);
        row.setBaselineAligned(false);
        for (int i = 0; i < columns; i++) {
            LinearLayout col = Ui.column(ctx());
            col.setPadding(dp(6), 0, dp(6), dp(12));
            row.addView(col, Ui.lpw(1f));
        }
        for (int i = 0; i < cards.size(); i++) {
            LinearLayout col = (LinearLayout) row.getChildAt(i % columns);
            col.addView(cards.get(i), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return row;
    }
}
