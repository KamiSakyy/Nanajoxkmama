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
import com.anibeat.app.ui.screens.LibraryScreen;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Обзор: разделы, жанры, годы и подборки. */
public class BrowseScreen extends ListScreen {

    public BrowseScreen(Context context, Host host) {
        super(context, host);
    }

    @Override
    protected void load(boolean refresh) {
        List<Block> blocks = new ArrayList<>();

        List<Block.Row> sections = new ArrayList<>();
        Block.Row novelties = new Block.Row("novelties", "Новинки", "Свежие темы за последнее время", R.drawable.ic_whatshot);
        novelties.action = () -> host.pushScreen(new TracksScreen(host.activity(), host, "Новинки", "Свежие темы", (r, sink) ->
                Api.getFreshTracks(Settings.period, 60, (tracks, error) -> {
                    if (error != null || tracks == null) sink.tracks(null, error);
                    else sink.tracks(Settings.filterMature(tracks), null);
                })));
        sections.add(novelties);

        Block.Row random = new Block.Row("random", "Случайные хиты", "Неожиданные находки", R.drawable.ic_casino);
        random.action = () -> host.pushScreen(new TracksScreen(host.activity(), host, "Случайные хиты", "Подборка каждый раз новая", (r, sink) ->
                Api.getRandomTracks(40, null, r, (tracks, error) -> {
                    if (error != null || tracks == null) sink.tracks(null, error);
                    else sink.tracks(Settings.filterMature(tracks), null);
                })));
        sections.add(random);

        Block.Row openings = new Block.Row("op", "Открывающие темы (OP)", "Лучшие опенинги", R.drawable.ic_play_arrow);
        openings.action = () -> host.pushScreen(new TracksScreen(host.activity(), host, "Опенинги", "Только OP", (r, sink) ->
                Api.getRandomTracks(40, "OP", r, (tracks, error) -> {
                    if (error != null || tracks == null) sink.tracks(null, error);
                    else sink.tracks(Settings.filterMature(tracks), null);
                })));
        sections.add(openings);

        Block.Row endings = new Block.Row("ed", "Закрывающие темы (ED)", "Лучшие эндинги", R.drawable.ic_music_note);
        endings.action = () -> host.pushScreen(new TracksScreen(host.activity(), host, "Эндинги", "Только ED", (r, sink) ->
                Api.getRandomTracks(40, "ED", r, (tracks, error) -> {
                    if (error != null || tracks == null) sink.tracks(null, error);
                    else sink.tracks(Settings.filterMature(tracks), null);
                })));
        sections.add(endings);

        final int[] season = Api.currentSeason();
        final String seasonName = Api.SEASONS[season[1]];
        Block.Row current = new Block.Row("season", "Сезон " + Api.seasonLabel(seasonName, season[0]),
                "Все темы текущего сезона", R.drawable.ic_calendar);
        current.action = () -> host.pushScreen(new TracksScreen(host.activity(), host,
                "Сезон " + Api.seasonLabel(seasonName, season[0]), "Темы сезона", (r, sink) ->
                Api.getSeasonTracks(season[0], seasonName, (tracks, error) -> {
                    if (error != null || tracks == null) sink.tracks(null, error);
                    else sink.tracks(Settings.filterMature(tracks), null);
                })));
        sections.add(current);

        blocks.add(Block.section("Разделы"));
        blocks.add(Block.rows(sections));

        Block genres = Block.chips("Жанры", genreIds(), genreNames(), null);
        genres.onChip = (id, label) -> host.openGenre(id, label);
        blocks.add(genres);

        List<String> years = new ArrayList<>();
        List<String> yearLabels = new ArrayList<>();
        int top = Calendar.getInstance().get(Calendar.YEAR);
        for (int year = top; year >= top - 19; year--) {
            years.add(String.valueOf(year));
            yearLabels.add(String.valueOf(year));
        }
        Block yearChips = Block.chips("Годы", years, yearLabels, null);
        yearChips.onChip = (id, label) -> host.openYear(Integer.parseInt(id));
        blocks.add(yearChips);

        blocks.add(Block.mixRow("Подборки", Api.MIXES));

        List<Block.Row> service = new ArrayList<>();
        Block.Row library = new Block.Row("library", "Скачанное и плейлисты", "Всё, что сохранено", R.drawable.ic_download_done);
        library.action = () -> host.openLibraryTab(LibraryScreen.TAB_DOWNLOADS);
        service.add(library);
        Block.Row downloads = new Block.Row("downloads", "Очередь загрузок", "Ход скачивания и отмена", R.drawable.ic_cloud_download);
        downloads.action = () -> com.anibeat.app.ui.Sheets.downloads(host);
        service.add(downloads);
        Block.Row settings = new Block.Row("settings", "Настройки", "Трафик, качество, кэш", R.drawable.ic_settings);
        settings.action = () -> com.anibeat.app.ui.Sheets.settings(host);
        service.add(settings);
        Block.Row about = new Block.Row("about", "О приложении", "Источники и версия", R.drawable.ic_info);
        about.action = () -> com.anibeat.app.ui.Sheets.about(host);
        service.add(about);
        blocks.add(Block.section("Приложение"));
        blocks.add(Block.rows(service));

        render(blocks, new ArrayList<>());

        if (refresh) setRefreshing(true);
        Api.getArtistsBySlugs(Api.ARTIST_SLUGS.subList(0, Math.min(24, Api.ARTIST_SLUGS.size())),
                (list, error) -> Ui.postSafe(() -> {
                    if (error == null && list != null && !list.isEmpty()) {
                        blocks.add(Block.artistRow("Исполнители", list));
                    }
                    render(blocks, new ArrayList<>());
                }));
    }

    @Override
    protected void rebuild() {
        load(false);
    }

    private static List<String> genreIds() {
        List<String> ids = new ArrayList<>();
        for (Models.GenreDef def : Api.GENRES) ids.add(def.id);
        return ids;
    }

    private static List<String> genreNames() {
        List<String> names = new ArrayList<>();
        for (Models.GenreDef def : Api.GENRES) names.add(def.label);
        return names;
    }

    @Override
    public String title() {
        return "Обзор";
    }
}
