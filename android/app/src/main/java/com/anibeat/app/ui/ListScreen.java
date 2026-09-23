package com.anibeat.app.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Экран-список: обновление жестом сверху вниз и блоки контента. */
public abstract class ListScreen extends FrameLayout implements Screen {

    protected final Host host;
    protected final BlockAdapter adapter;
    protected final SwipeRefreshLayout swipe;
    protected final RecyclerView list;
    /** Верхняя панель: стрелка назад, название и действия. */
    protected final LinearLayout topBar;
    /** Место под собственные панели экрана (например, строка поиска). */
    protected final LinearLayout headerSlot;
    private final ImageView backButton;
    private final TextView topTitle;
    private final LinearLayout topActions;
    private Runnable backAction;
    private int topInset;
    private int topExtra;

    private final Meta.Listener metaListener = () -> Ui.postSafe(this::onMetaChanged);
    private boolean metaWatched;
    private boolean loaded;
    private int padTop = -1;
    private int padBottom = -1;
    private final android.os.Handler loader = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable loaderTask;

    public ListScreen(Context context, Host host) {
        super(context);
        this.host = host;
        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Theme.BG);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 56)));

        backButton = new ImageView(context);
        backButton.setImageResource(com.anibeat.app.R.drawable.ic_arrow_back);
        backButton.setColorFilter(Theme.ON);
        backButton.setVisibility(View.GONE);
        int backPad = Theme.dp(context, 11);
        backButton.setPadding(backPad, backPad, backPad, backPad);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 46), Theme.dp(context, 46));
        backParams.leftMargin = Theme.dp(context, 6);
        topBar.addView(backButton, backParams);
        Ui.ripple(backButton);

        topTitle = new TextView(context);
        topTitle.setTextSize(19f);
        topTitle.setTextColor(Theme.ON);
        topTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        topTitle.setSingleLine(true);
        topTitle.setIncludeFontPadding(false);
        topTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = Theme.dp(context, 8);
        topBar.addView(topTitle, titleParams);

        topActions = new LinearLayout(context);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        topActions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topBar.addView(topActions);
        column.addView(topBar);

        headerSlot = new LinearLayout(context);
        headerSlot.setOrientation(LinearLayout.VERTICAL);
        column.addView(headerSlot);

        swipe = new SwipeRefreshLayout(context);
        swipe.setColorSchemeColors(Theme.ACCENT, Theme.TERTIARY);
        swipe.setProgressBackgroundColorSchemeColor(Theme.SURFACE_3);
        swipe.setBackgroundColor(Theme.BG);

        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setBackgroundColor(Theme.BG);
        list.setClipToPadding(false);
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(8);
        list.setItemAnimator(null);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new BlockAdapter(host);
        list.setAdapter(adapter);
        swipe.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        column.addView(swipe, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        backButton.setOnClickListener(v -> {
            if (backAction != null) backAction.run();
        });

        swipe.setOnRefreshListener(() -> Ui.safe(() -> load(true)));
    }

    /** Загрузка данных экрана. */
    protected abstract void load(boolean refresh);

    /** Список треков экрана: из него запускается воспроизведение по нажатию. */
    protected List<Models.Track> baseTracks() {
        return new ArrayList<>();
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void onShow() {
        Ui.safe(() -> {
            if (!metaWatched) {
                Meta.addListener(metaListener);
                metaWatched = true;
            }
            refreshTopTitle();
            // Экран мог остаться без содержимого (первый заход не удался) — пробуем снова.
            if (!loaded || adapter.getItemCount() == 0) {
                loaded = true;
                showLoading();
                load(false);
            } else {
                adapter.setPlayingId(playingId());
            }
        });
    }

    @Override
    public void onHide() {
    }

    @Override
    public void release() {
        // Отписываемся от метаданных, но содержимое НЕ уничтожаем:
        // вкладка обязана открыться снова мгновенно, а не остаться чёрной.
        if (metaWatched) {
            Meta.removeListener(metaListener);
            metaWatched = false;
        }
    }

    protected void onMetaChanged() {
        if (!isShown()) return;
        Ui.safe(this::rebuild);
    }

    /** Прокрутить список вверх. */
    public void toTop() {
        Ui.safe(() -> list.scrollToPosition(0));
    }

    /** Перестроить содержимое: перерисовываются только изменившиеся блоки. */
    protected void rebuild() {
        adapter.refreshChanged();
    }

    /** Сколько сверху занимает собственная панель экрана (сохранено для совместимости). */
    protected int extraTop() {
        return 0;
    }

    /** Стрелка возврата: показывается только на открытых поверх вкладок экранах. */
    public void setBackAction(Runnable action) {
        this.backAction = action;
        Ui.safe(() -> backButton.setVisibility(action == null ? View.GONE : View.VISIBLE));
    }

    /** Видна ли стрелка возврата (проверяется в тестах). */
    public boolean hasBackButton() {
        return backAction != null && backButton.getVisibility() == View.VISIBLE;
    }

    /** Место под собственную панель экрана. */
    protected void addHeader(View view) {
        Ui.safe(() -> headerSlot.addView(view));
    }

    public void setTopTitle(String text) {
        Ui.safe(() -> {
            if (text != null && !text.contentEquals(topTitle.getText())) topTitle.setText(text);
        });
    }

    /** Действия в верхней панели (иконки справа). */
    public void clearActions() {
        Ui.safe(() -> topActions.removeAllViews());
    }

    public View addAction(int icon, String description, Runnable action) {
        ImageView button = new ImageView(getContext());
        button.setImageResource(icon);
        button.setColorFilter(Theme.ON);
        button.setContentDescription(description);
        int pad = Theme.dp(getContext(), 10);
        button.setPadding(pad, pad, pad, pad);
        topActions.addView(button, new LinearLayout.LayoutParams(
                Theme.dp(getContext(), 44), Theme.dp(getContext(), 44)));
        button.setOnClickListener(v -> Ui.safe(action));
        Ui.ripple(button);
        return button;
    }

    /** Отступы содержимого под системные полосы, мини-плеер и меню. */
    public void setContentPadding(int top, int bottom) {
        if (top == padTop && bottom == padBottom) return;
        padTop = top;
        padBottom = bottom;
        topInset = top;
        Ui.safe(() -> {
            topBar.setPadding(0, top, 0, 0);
            topBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(getContext(), 56) + top));
            list.setPadding(0, extraTop(), 0, bottom);
            list.setClipToPadding(false);
        });
    }

    /** Обновить подсветку играющего трека. */
    public void refreshPlaying() {
        Ui.safe(() -> adapter.setPlayingId(playingId()));
    }

    protected String playingId() {
        Models.Track now = com.anibeat.app.player.Player.current();
        return now == null ? "" : now.id;
    }

    protected void render(List<Block> blocks) {
        render(blocks, baseTracks());
    }

    /** Собрать id аниме из блоков, чтобы подтянуть русские названия одним запросом. */
    private static void warmMeta(List<Block> blocks, List<Models.Track> tracks) {
        List<Integer> ids = new ArrayList<>();
        if (tracks != null) {
            for (Models.Track track : tracks) {
                if (track.anime != null && track.anime.malId != null) ids.add(track.anime.malId);
            }
        }
        if (blocks != null) {
            for (Block block : blocks) {
                if (block.anime != null && block.anime.malId != null) ids.add(block.anime.malId);
                if (block.track != null && block.track.anime != null && block.track.anime.malId != null) {
                    ids.add(block.track.anime.malId);
                }
                for (Models.AnimeRef anime : block.animes) {
                    if (anime != null && anime.malId != null) ids.add(anime.malId);
                }
                for (Models.Track item : block.tracks) {
                    if (item.anime != null && item.anime.malId != null) ids.add(item.anime.malId);
                }
            }
        }
        if (!ids.isEmpty()) Meta.warmAll(ids);
    }

    protected void render(List<Block> blocks, List<Models.Track> tracks) {
        watchdogToken = new Object();
        hideLoading();
        refreshTopTitle();
        Ui.safe(() -> {
            warmMeta(blocks, tracks);
            boolean wasEmpty = adapter.getItemCount() == 0;
            adapter.submit(blocks, tracks);
            adapter.setPlayingId(playingId());
            swipe.setRefreshing(false);
            // Наверх поднимаем только при первой загрузке: иначе прокрутка сбивается на каждом обновлении.
            if (wasEmpty) list.scrollToPosition(0);
        });
    }

    /** Название в верхней панели берём с экрана. */
    protected void refreshTopTitle() {
        String t = title();
        if (t != null && !t.isEmpty()) setTopTitle(t);
    }

    /** Показать ошибку без падения приложения. */
    protected void fail(String message) {
        watchdogToken = new Object();
        hideLoading();
        Ui.postSafe(() -> {
            if (adapter.getItemCount() > 0 && adapter.hasContent()) {
                // На экране уже что-то есть — не выкидываем содержимое из-за одной ошибки.
                swipe.setRefreshing(false);
                return;
            }
            swipe.setRefreshing(false);
            List<Block> blocks = new ArrayList<>();
            blocks.add(Block.empty("Не удалось загрузить", message == null ? "Нет связи с источником" : message));
            Block.Row retry = new Block.Row("retry", "Повторить", "Запросить данные заново", com.anibeat.app.R.drawable.ic_refresh);
            retry.chevron = false;
            retry.action = () -> load(true);
            blocks.add(Block.row(retry));
            adapter.submit(blocks, new ArrayList<>());
        });
    }

    /** Нужен ли экранам загрузочный экран. Локальные разделы (медиатека) — сразу, без «Загрузки». */
    protected boolean wantsLoadingScreen() {
        return true;
    }

    /** Пока данные едут, экран не остаётся пустым — но и не мигает «Загрузкой» на локальных данных. */
    protected void showLoading() {
        if (adapter.getItemCount() > 0) return;
        if (!wantsLoadingScreen()) return;
        hideLoading();
        loaderTask = () -> Ui.safe(() -> {
            loaderTask = null;
            if (adapter.getItemCount() > 0) return;
            List<Block> blocks = new ArrayList<>();
            blocks.add(Block.empty("Загрузка…", ""));
            adapter.submit(blocks, new ArrayList<>());
        });
        loader.postDelayed(loaderTask, 260);
        armWatchdog();
    }

    /** Сторож: если источник молчит — на экране появится ошибка с «Повторить», а не вечная «Загрузка». */
    private void armWatchdog() {
        final Object token = new Object();
        watchdogToken = token;
        loader.postDelayed(() -> {
            if (watchdogToken != token) return;
            if (adapter.getItemCount() > 0) return;
            fail("Источник не отвечает. Проверьте соединение");
        }, 20000);
    }

    private Object watchdogToken = new Object();

    private void hideLoading() {
        if (loaderTask != null) {
            loader.removeCallbacks(loaderTask);
            loaderTask = null;
        }
    }

    protected void setRefreshing(boolean value) {
        swipe.setRefreshing(value);
    }

    protected boolean isRefreshing() {
        return swipe.isRefreshing();
    }
}
