package com.anibeat.app.ui.screens;

import android.content.Context;

import com.anibeat.app.R;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Display;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.ListScreen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Экран аниме: описание, метаданные и все темы с прослушиванием. */
public class AnimeScreen extends ListScreen {

    private final String slug;
    private Models.AnimeDetail detail;
    private List<Models.Track> tracks = new ArrayList<>();

    public AnimeScreen(Context context, Host host, String slug) {
        super(context, host);
        setTopTitle("Аниме");
        this.slug = slug;
    }

    @Override
    protected void load(boolean refresh) {
        if (slug == null || slug.isEmpty()) {
            fail("Аниме не найдено");
            return;
        }
        setRefreshing(true);
        Api.getAnime(slug, (found, error) -> Ui.postSafe(() -> {
            try {
                setRefreshing(false);
                if (error != null || found == null) {
                    fail(error == null ? "Аниме не найдено" : error);
                    return;
                }
                detail = found;
                tracks = found.tracks == null ? new ArrayList<>() : found.tracks;
                if (found.malId != null && found.malId > 0) Meta.warmAll(Collections.singletonList(found.malId));
                rebuild();
            } catch (Throwable t) {
                Ui.report(t);
                fail("Не удалось показать аниме");
            }
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
        return detail == null ? "" : Display.summary(detail).title;
    }

    private List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        if (detail == null) return blocks;
        Display display;
        try {
            display = Display.summary(detail);
        } catch (Throwable t) {
            Ui.report(t);
            display = null;
        }
        if (display == null) display = Display.summary(new Models.AnimeSummary());
        if (display.title == null) display.title = detail.name == null ? "" : detail.name;
        blocks.add(Block.header(display.title));

        List<String> meta = new ArrayList<>();
        if (display.original != null && !display.original.isEmpty()) meta.add(display.original);
        String kind = Meta.KIND_RU.get(detail.mediaFormat == null ? "" : detail.mediaFormat);
        meta.add(kind != null ? kind : (detail.mediaFormat == null ? "" : detail.mediaFormat));
        if (detail.year != null) meta.add(String.valueOf(detail.year));
        if (detail.season != null && !detail.season.isEmpty()) meta.add(Api.seasonRu(detail.season));
        if (display.score != null && display.score > 0) meta.add(String.format(java.util.Locale.US, "★ %.1f", display.score));
        StringBuilder line = new StringBuilder();
        for (String part : meta) {
            if (part == null || part.isEmpty()) continue;
            if (line.length() > 0) line.append(" · ");
            line.append(part);
        }
        if (line.length() > 0) blocks.add(Block.text("", line.toString()));

        Models.AnimeMeta info = Meta.peek(detail.malId);
        if (info != null && info.genres != null && !info.genres.isEmpty()) {
            StringBuilder genres = new StringBuilder();
            for (String genre : info.genres) {
                if (genres.length() > 0) genres.append(" · ");
                genres.append(genre);
            }
            blocks.add(Block.text("Жанры", genres.toString()));
        }
        String synopsis = detail.synopsis;
        if (synopsis == null && info != null) synopsis = info.ru;
        if (synopsis != null && !synopsis.isEmpty()) {
            blocks.add(Block.text("Описание", clean(synopsis)));
        }

        List<Block.Row> actions = new ArrayList<>();
        final List<Models.Track> queue = Settings.filterMature(tracks);
        Block.Row playAll = new Block.Row("play", "Слушать все темы", Format.plural(queue.size(), "трек", "трека", "треков"), R.drawable.ic_play_arrow);
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
        Block.Row download = new Block.Row("download", "Скачать все темы",
                "Сохранить " + (Downloads.KIND_VIDEO.equals(Settings.downloadKind) ? "видео" : "аудио"), R.drawable.ic_download);
        download.chevron = false;
        download.action = () -> {
            for (Models.Track track : tracks) {
                Downloads.download(track, Settings.downloadKind, true);
            }
            host.toast("Скачивание запущено: " + tracks.size());
        };
        actions.add(download);
        blocks.add(Block.rows(actions));
        blocks.add(Block.space(4));

        String playingId = playingId();
        for (String type : new String[]{"OP", "ED", "IN"}) {
            List<Models.Track> group = new ArrayList<>();
            for (Models.Track track : queue) {
                if (track == null) continue;
                if (type.equalsIgnoreCase(track.type == null ? "" : track.type)) group.add(track);
            }
            if (group.isEmpty()) continue;
            blocks.add(Block.section(typeLabel(type) + " · " + group.size()));
            for (Models.Track track : group) {
                int index = queue.indexOf(track);
                blocks.add(Block.track(track, Math.max(0, index), track.id != null && track.id.equals(playingId)));
            }
        }
        if (queue.isEmpty()) {
            blocks.add(Block.empty("Тем пока нет", "Для этого аниме темы ещё не добавлены"));
        }
        return blocks;
    }

    private static String typeLabel(String type) {
        if ("OP".equals(type)) return "Открывающие темы";
        if ("ED".equals(type)) return "Закрывающие темы";
        return "Вставные песни";
    }

    private static String clean(String text) {
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }
}
