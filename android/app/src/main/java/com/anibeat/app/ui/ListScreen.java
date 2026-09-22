package com.anibeat.app.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

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

    private final Meta.Listener metaListener = () -> Ui.postSafe(this::onMetaChanged);
    private boolean metaWatched;
    private boolean loaded;
    private int padTop = -1;
    private int padBottom = -1;

    public ListScreen(Context context, Host host) {
        super(context);
        this.host = host;
        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

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
        addView(swipe);

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
            if (!loaded) {
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
        if (metaWatched) {
            Meta.removeListener(metaListener);
            metaWatched = false;
        }
        Ui.safe(() -> list.setAdapter(null));
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

    /** Сколько сверху занимает собственная панель экрана (например, строка поиска). */
    protected int extraTop() {
        return 0;
    }

    /** Отступы содержимого под системные полосы, мини-плеер и меню. */
    public void setContentPadding(int top, int bottom) {
        final int realTop = top + extraTop();
        if (realTop == padTop && bottom == padBottom) return;
        padTop = realTop;
        padBottom = bottom;
        Ui.safe(() -> {
            list.setPadding(0, realTop, 0, bottom);
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

    protected void render(List<Block> blocks, List<Models.Track> tracks) {
        Ui.safe(() -> {
            boolean wasEmpty = adapter.getItemCount() == 0;
            adapter.submit(blocks, tracks);
            adapter.setPlayingId(playingId());
            swipe.setRefreshing(false);
            // Наверх поднимаем только при первой загрузке: иначе прокрутка сбивается на каждом обновлении.
            if (wasEmpty) list.scrollToPosition(0);
        });
    }

    /** Показать ошибку без падения приложения. */
    protected void fail(String message) {
        Ui.postSafe(() -> {
            swipe.setRefreshing(false);
            List<Block> blocks = new ArrayList<>();
            blocks.add(Block.empty("Не удалось загрузить", message == null ? "Проверьте соединение и потяните вниз" : message));
            Block.Row retry = new Block.Row("retry", "Повторить", "Запросить данные заново", com.anibeat.app.R.drawable.ic_refresh);
            retry.chevron = false;
            retry.action = () -> load(true);
            blocks.add(Block.row(retry));
            adapter.submit(blocks, new ArrayList<>());
        });
    }

    /** Пока данные едут, экран не должен оставаться пустым. */
    protected void showLoading() {
        if (adapter.getItemCount() > 0) return;
        Ui.postSafe(() -> {
            List<Block> blocks = new ArrayList<>();
            blocks.add(Block.empty("Загрузка…", "Получаем данные источника"));
            adapter.submit(blocks, new ArrayList<>());
        });
    }

    protected void setRefreshing(boolean value) {
        swipe.setRefreshing(value);
    }

    protected boolean isRefreshing() {
        return swipe.isRefreshing();
    }
}
