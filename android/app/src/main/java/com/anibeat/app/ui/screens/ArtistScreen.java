package com.anibeat.app.ui.screens;

import android.content.Context;

import com.anibeat.app.R;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.ListScreen;

import java.util.ArrayList;
import java.util.List;

/** Экран исполнителя: описание и все темы. */
public class ArtistScreen extends ListScreen {

    private final String slug;
    private Models.ArtistDetail detail;
    private List<Models.Track> tracks = new ArrayList<>();

    public ArtistScreen(Context context, Host host, String slug) {
        super(context, host);
        setTopTitle("Исполнитель");
        this.slug = slug;
    }

    @Override
    protected void load(boolean refresh) {
        setRefreshing(true);
        Api.getArtist(slug, (found, error) -> Ui.postSafe(() -> {
            setRefreshing(false);
            if (error != null || found == null) {
                fail(error == null ? "Исполнитель не найден" : error);
                return;
            }
            detail = found;
            tracks = found.tracks == null ? new ArrayList<>() : found.tracks;
            rebuild();
        }));
    }

    @Override
    protected List<Models.Track> baseTracks() {
        return tracks;
    }

    @Override
    protected void rebuild() {
        render(blocks(), tracks);
    }

    @Override
    public String title() {
        return detail == null ? "" : detail.name;
    }

    private List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        if (detail == null) return blocks;
        blocks.add(Block.header(detail.name));
        String info = detail.information == null ? "" : detail.information.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        if (!info.isEmpty()) blocks.add(Block.text("Об исполнителе", info));

        final List<Models.Track> queue = Settings.filterMature(tracks);
        List<Block.Row> actions = new ArrayList<>();
        Block.Row playAll = new Block.Row("play", "Слушать всё", Format.plural(queue.size(), "трек", "трека", "треков"), R.drawable.ic_play_arrow);
        playAll.chevron = false;
        playAll.action = () -> {
            if (!queue.isEmpty()) host.playTrack(queue.get(0), queue, 0);
        };
        actions.add(playAll);
        Block.Row shuffle = new Block.Row("shuffle", "Перемешать", "Случайный порядок", R.drawable.ic_shuffle);
        shuffle.chevron = false;
        shuffle.action = () -> {
            List<Models.Track> mixed = Format.shuffled(queue);
            if (!mixed.isEmpty()) host.playTrack(mixed.get(0), mixed, 0);
        };
        actions.add(shuffle);
        blocks.add(Block.rows(actions));
        blocks.add(Block.space(4));

        blocks.add(Block.section("Темы"));
        String playingId = playingId();
        for (int i = 0; i < queue.size(); i++) {
            Models.Track track = queue.get(i);
            blocks.add(Block.track(track, i, track.id != null && track.id.equals(playingId)));
        }
        return blocks;
    }
}
