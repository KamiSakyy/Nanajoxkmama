package com.anibeat.app.ui.screens;

import android.content.Context;

import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.ListScreen;
import com.anibeat.app.ui.screens.LibraryScreen;

import java.util.ArrayList;
import java.util.List;

/** Главная: подборки, новинки, сезон, исполнители и жанры. */
public class HomeScreen extends ListScreen {

    private final List<Block> blocks = new ArrayList<>();
    private final List<Models.Track> tracks = new ArrayList<>();
    private int pending;
    private int failed;

    public HomeScreen(Context context, Host host) {
        super(context, host);
    }

    @Override
    protected void load(final boolean refresh) {
        blocks.clear();
        tracks.clear();
        pending = 0;
        failed = 0;
        if (refresh) setRefreshing(true);

        blocks.add(Block.header("AniBeat"));

        Block genres = Block.chips("Жанры", genreIds(), genreNames(), null);
        genres.onChip = (id, label) -> host.openGenre(id, label);
        blocks.add(genres);

        blocks.add(Block.mixRow("Подборки", Api.MIXES));

        // Скачанное и медиатека — всегда под рукой, а не спрятаны в настройках.
        List<Block.Row> shelf = new ArrayList<>();
        int offline = Downloads.offlineTracks().size();
        Block.Row downloaded = new Block.Row("downloads", "Скачанное",
                offline == 0 ? "Пока пусто — скачайте трек из меню «⋮»"
                        : Format.plural(offline, "трек", "трека", "треков") + " · " + Downloads.formatBytes(Downloads.offlineTotalSize()),
                com.anibeat.app.R.drawable.ic_download_done);
        downloaded.action = () -> host.openLibraryTab(LibraryScreen.TAB_DOWNLOADS);
        shelf.add(downloaded);
        Block.Row favorites = new Block.Row("favorites", "Избранное",
                Format.plural(Library.favorites().size(), "трек", "трека", "треков"),
                com.anibeat.app.R.drawable.ic_favorite);
        favorites.action = () -> host.openLibraryTab(LibraryScreen.TAB_FAVORITES);
        shelf.add(favorites);
        Block.Row historyRow = new Block.Row("history", "История",
                Format.plural(Library.history().size(), "запись", "записи", "записей"),
                com.anibeat.app.R.drawable.ic_history);
        historyRow.action = () -> host.openLibraryTab(LibraryScreen.TAB_HISTORY);
        shelf.add(historyRow);
        blocks.add(Block.rows(shelf));

        List<Models.Track> history = Library.history();
        if (!history.isEmpty()) {
            blocks.add(Block.section("Продолжить слушать"));
            int limit = Math.min(4, history.size());
            for (int i = 0; i < limit; i++) {
                Models.Track track = history.get(i);
                tracks.add(track);
                blocks.add(Block.track(track, tracks.size() - 1, track.id != null && track.id.equals(playingId())));
            }
        }
        render(blocks, tracks);

        final int[] season = Api.currentSeason();
        final String seasonName = Api.SEASONS[season[1]];
        final String seasonTitle = "Сезон " + Api.seasonLabel(seasonName, season[0]);

        watch();
        Api.getFreshTracks(Settings.period, 30, (list, error) -> Ui.postSafe(() -> {
            if (error == null && list != null && !list.isEmpty()) {
                Block section = Block.section("Новинки", "Все");
                section.id = "fresh";
                section.onAction = () -> host.pushScreen(new TracksScreen(host.activity(), host, "Новинки",
                        "Свежие темы из аниме", (isRefresh, sink) -> Api.getFreshTracks(Settings.period, 60, (list2, error2) -> {
                            if (error2 != null || list2 == null) sink.tracks(null, error2);
                            else sink.tracks(Settings.filterMature(list2), null);
                        })));
                blocks.add(section);
                List<Models.Track> clean = Settings.filterMature(list);
                int start = tracks.size();
                tracks.addAll(clean);
                for (int i = 0; i < clean.size(); i++) {
                    Models.Track track = clean.get(i);
                    blocks.add(Block.track(track, start + i, track.id != null && track.id.equals(playingId())));
                }
            } else {
                failed++;
            }
            tick();
        }));

        watch();
        Api.getSeasonAnime(season[0], seasonName, 1, (list, error) -> Ui.postSafe(() -> {
            if (error == null && list != null && !list.isEmpty()) {
                blocks.add(Block.animeRow(seasonTitle, list));
            } else {
                failed++;
            }
            tick();
        }));

        watch();
        Api.getArtistsBySlugs(Api.ARTIST_SLUGS.subList(0, Math.min(20, Api.ARTIST_SLUGS.size())),
                (list, error) -> Ui.postSafe(() -> {
                    if (error == null && list != null && !list.isEmpty()) {
                        blocks.add(Block.artistRow("Исполнители", list));
                    } else {
                        failed++;
                    }
                    tick();
                }));

        if (refresh) {
            watch();
            Api.getRandomTracks(18, null, true, (list, error) -> Ui.postSafe(() -> {
                if (error == null && list != null && !list.isEmpty()) {
                    blocks.add(Block.trackRow("Случайные хиты", Settings.filterMature(list)));
                } else {
                    failed++;
                }
                tick();
            }));
        }
    }

    private void watch() {
        pending++;
    }

    private void tick() {
        pending = Math.max(0, pending - 1);
        if (pending > 0) {
            render(blocks, tracks);
            return;
        }
        if (tracks.isEmpty() && blocks.size() <= 5 && failed > 0) {
            fail("Источник не отвечает");
            return;
        }
        render(blocks, tracks);
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
    protected List<Models.Track> baseTracks() {
        return tracks;
    }

    @Override
    protected void rebuild() {
        render(blocks, tracks);
    }
}
