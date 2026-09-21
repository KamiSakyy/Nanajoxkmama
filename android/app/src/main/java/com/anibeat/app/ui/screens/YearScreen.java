package com.anibeat.app.ui.screens;

import android.content.Context;

import com.anibeat.app.R;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.ListScreen;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Экран года: сезоны и аниме выбранного года. */
public class YearScreen extends ListScreen {

    private final int year;
    private int seasonIndex;
    private int page = 1;
    private final List<Models.AnimeSummary> animes = new ArrayList<>();

    public YearScreen(Context context, Host host, int year) {
        super(context, host);
        this.year = year;
    }

    @Override
    protected void load(boolean refresh) {
        int[] current = Api.currentSeason();
        seasonIndex = year == current[0] ? current[1] : 0;
        page = 1;
        animes.clear();
        fetch(refresh);
    }

    private void fetch(boolean refresh) {
        if (refresh) setRefreshing(true);
        final int requestPage = page;
        Api.getSeasonAnime(year, Api.SEASONS[seasonIndex], requestPage, (list, error) -> Ui.postSafe(() -> {
            setRefreshing(false);
            if (error != null || list == null || list.isEmpty()) {
                if (animes.isEmpty()) {
                    fail(error == null ? "В этом сезоне аниме не найдено" : error);
                } else {
                    rebuild();
                }
                return;
            }
            animes.addAll(list);
            rebuild();
        }));
    }

    @Override
    public String title() {
        return String.valueOf(year);
    }

    @Override
    protected void rebuild() {
        render(blocks(), new ArrayList<>());
    }

    private List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(Block.header(String.valueOf(year)));
        Block chips = Block.chips("Сезон", seasonIds(), seasonLabels(), Api.SEASONS[seasonIndex]);
        chips.onChip = (id, label) -> {
            for (int i = 0; i < Api.SEASONS.length; i++) {
                if (Api.SEASONS[i].equals(id)) seasonIndex = i;
            }
            page = 1;
            animes.clear();
            fetch(true);
        };
        blocks.add(chips);

        Block.Row listen = new Block.Row("listen", "Слушать темы сезона",
                Api.seasonLabel(Api.SEASONS[seasonIndex], year), R.drawable.ic_play_arrow);
        listen.chevron = false;
        listen.action = () -> host.pushScreen(new TracksScreen(host.activity(), host,
                "Темы · " + Api.seasonLabel(Api.SEASONS[seasonIndex], year), "Все темы сезона", (r, sink) ->
                Api.getSeasonTracks(year, Api.SEASONS[seasonIndex], (tracks, error) -> {
                    if (error != null || tracks == null) sink.tracks(null, error);
                    else sink.tracks(Settings.filterMature(tracks), null);
                })));
        blocks.add(Block.row(listen));

        if (animes.isEmpty()) {
            blocks.add(Block.empty("Загрузка…", "Тянем аниме из источника"));
            return blocks;
        }
        for (int i = 0; i < animes.size(); i += 2) {
            List<Models.AnimeSummary> pair = new ArrayList<>();
            pair.add(animes.get(i));
            if (i + 1 < animes.size()) pair.add(animes.get(i + 1));
            blocks.add(Block.animePair(pair));
        }
        Block.Row more = new Block.Row("more", "Показать ещё", "Следующая страница", R.drawable.ic_expand_more);
        more.chevron = false;
        more.action = () -> {
            page++;
            fetch(true);
        };
        blocks.add(Block.row(more));
        return blocks;
    }

    private static List<String> seasonIds() {
        List<String> ids = new ArrayList<>();
        for (String season : Api.SEASONS) ids.add(season);
        return ids;
    }

    private static List<String> seasonLabels() {
        List<String> labels = new ArrayList<>();
        for (String season : Api.SEASONS_RU) labels.add(season);
        return labels;
    }

    /** Проверка: год не из будущего. */
    public static int clampYear(int value) {
        int now = Calendar.getInstance().get(Calendar.YEAR);
        return Math.min(now, Math.max(1960, value));
    }
}
