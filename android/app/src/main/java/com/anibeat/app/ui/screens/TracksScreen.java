package com.anibeat.app.ui.screens;

import android.content.Context;

import com.anibeat.app.data.Models;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.ListScreen;

import java.util.ArrayList;
import java.util.List;

/** Универсальный список треков: подборки, жанры, сезоны. */
public class TracksScreen extends ListScreen {

    public interface Sink {
        void tracks(List<Models.Track> tracks, String error);
    }

    public interface Loader {
        void load(boolean refresh, Sink sink);
    }

    private final String header;
    private final String subtitle;
    private final Loader loader;
    private List<Models.Track> data = new ArrayList<>();

    public TracksScreen(Context context, Host host, String header, String subtitle, Loader loader) {
        super(context, host);
        this.header = header;
        this.subtitle = subtitle;
        this.loader = loader;
    }

    @Override
    protected void load(boolean refresh) {
        if (refresh) setRefreshing(true);
        final java.util.concurrent.atomic.AtomicBoolean answered =
                new java.util.concurrent.atomic.AtomicBoolean();
        // Сторож: если источник молчит дольше 20 секунд, показываем понятную ошибку и «Повторить».
        com.anibeat.app.core.Ui.postDelayed(() -> {
            if (answered.get()) return;
            answered.set(true);
            fail("Источник не ответил. Проверьте соединение");
        }, 20000);
        try {
            loader.load(refresh, (tracks, error) -> {
                answered.set(true);
                if (error != null || tracks == null || tracks.isEmpty()) {
                    if (data == null || data.isEmpty()) fail(error == null ? "Ничего не найдено" : error);
                    else setRefreshing(false);
                    return;
                }
                data = tracks;
                render(blocks(), data);
            });
        } catch (Throwable t) {
            com.anibeat.app.core.Ui.report(t);
            fail(t.getMessage());
        }
    }

    @Override
    protected List<Models.Track> baseTracks() {
        return data;
    }

    private List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.header(header));
        if (subtitle != null && !subtitle.isEmpty()) blocks.add(Block.text("", subtitle));
        blocks.add(Block.space(6));
        String playingId = playingId();
        for (int i = 0; i < data.size(); i++) {
            Models.Track track = data.get(i);
            blocks.add(Block.track(track, i, track.id != null && track.id.equals(playingId)));
        }
        return blocks;
    }

    @Override
    protected void rebuild() {
        render(blocks(), data);
    }
}
