package com.anibeat.app.ui.screens;

import android.content.Context;

import com.anibeat.app.R;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.ListScreen;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Sheets;

import java.util.ArrayList;
import java.util.List;

/** Медиатека: избранное, плейлисты, скачанное и история. */
public class LibraryScreen extends ListScreen {

    public static final String TAB_FAVORITES = "favorites";
    public static final String TAB_PLAYLISTS = "playlists";
    public static final String TAB_DOWNLOADS = "downloads";
    public static final String TAB_HISTORY = "history";

    private String tab = TAB_FAVORITES;
    private final Library.Listener libraryListener = () -> Ui.postSafe(this::rebuild);
    private final Downloads.Listener downloadsListener = () -> Ui.postSafe(this::rebuild);
    private boolean watching;
    private boolean metaWatchedOnce;

    public LibraryScreen(Context context, Host host) {
        super(context, host);
    }

    @Override
    protected void load(boolean refresh) {
        rebuild();
    }

    @Override
    protected boolean wantsLoadingScreen() {
        // Медиатека целиком на устройстве: никакой загрузки быть не должно.
        return false;
    }

    @Override
    protected void rebuild() {
        Ui.safe(() -> {
            List<Block> blocks = new ArrayList<>();
            List<Models.Track> trackList = new ArrayList<>();
            blocks.add(Block.header("Медиатека"));
            Block chips = Block.chips("", tabIds(), tabLabels(), tab);
            chips.onChip = (id, label) -> {
                tab = id;
                rebuild();
            };
            blocks.add(chips);

            if (TAB_DOWNLOADS.equals(tab)) {
                List<Models.Track> all = Downloads.offlineTracks();
                int videos = 0;
                for (Models.Track track : all) if (Player.hasOfflineVideo(track)) videos++;
                if (videos > 0) {
                    final Models.Track firstVideo = firstOfflineVideo(all);
                    Block.Row watch = new Block.Row("watch", "Смотреть скачанное видео",
                            Format.plural(videos, "клип", "клипа", "клипов") + " · с устройства, без интернета",
                            R.drawable.ic_videocam);
                    watch.chevron = false;
                    watch.action = () -> {
                        if (firstVideo != null) {
                            Player.setVideoMode(true);
                            host.playTrack(firstVideo, Downloads.offlineTracks(), 0);
                            host.openNowPlaying();
                        }
                    };
                    blocks.add(Block.row(watch));
                }
            }

            if (TAB_FAVORITES.equals(tab)) {
                List<Models.Track> favorites = Library.favorites();
                if (favorites.isEmpty()) {
                    blocks.add(Block.empty("В избранном пусто", "Нажмите ♥ в плеере или удерживайте трек в списке"));
                } else {
                    Block.Row shuffle = new Block.Row("shuffle", "Перемешать избранное", Format.plural(favorites.size(), "трек", "трека", "треков"), R.drawable.ic_shuffle);
                    shuffle.action = () -> host.playTrack(favorites.get(0), com.anibeat.app.ui.Format.shuffled(favorites), 0);
                    blocks.add(Block.row(shuffle));
                    trackList.addAll(favorites);
                    for (int i = 0; i < favorites.size(); i++) {
                        Models.Track track = favorites.get(i);
                        blocks.add(Block.track(track, i, track.id != null && track.id.equals(playingId())));
                    }
                }
            } else if (TAB_PLAYLISTS.equals(tab)) {
                Block.Row create = new Block.Row("create", "Создать плейлист", "Свой список треков", R.drawable.ic_add);
                create.chevron = false;
                create.action = () -> Sheets.createPlaylist(host, this::rebuild);
                blocks.add(Block.row(create));
                List<Models.Playlist> playlists = Library.playlists();
                if (playlists.isEmpty()) {
                    blocks.add(Block.empty("Плейлистов нет", "Создайте первый плейлист и добавляйте туда треки"));
                }
                for (Models.Playlist playlist : playlists) {
                    blocks.add(Block.playlistRow(playlist));
                }
            } else if (TAB_DOWNLOADS.equals(tab)) {
                List<Models.Track> offline = Downloads.offlineTracks();
                Block.Row queue = new Block.Row("queue", "Очередь загрузок", Downloads.activeCount() + " активных", R.drawable.ic_cloud_download);
                queue.chevron = false;
                queue.action = () -> Sheets.downloads(host);
                blocks.add(Block.row(queue));
                blocks.add(Block.text("Занято на устройстве", Downloads.formatBytes(Downloads.offlineTotalSize())));
                if (offline.isEmpty()) {
                    blocks.add(Block.empty("Скачанного нет", "Долгое нажатие на трек → «Скачать аудио». Файл попадёт и в папку «Загрузки»"));
                } else {
                    Block.Row clear = new Block.Row("clear", "Удалить все скачанные", null, R.drawable.ic_delete);
                    clear.chevron = false;
                    clear.action = () -> Sheets.confirm(host.activity(), "Удалить скачанное?",
                            "Файлы будут удалены с устройства.", "Удалить", () -> {
                                Downloads.clearOffline();
                                rebuild();
                            });
                    blocks.add(Block.row(clear));
                    trackList.addAll(offline);
                    for (int i = 0; i < offline.size(); i++) {
                        final Models.Track track = offline.get(i);
                        Block block = Block.track(track, i, track.id != null && track.id.equals(playingId()));
                        // долгое нажатие — что делать со скачанным: смотреть видео, слушать или удалить
                        block.onLongClick = () -> Sheets.offlineMenu(host, track);
                        blocks.add(block);
                    }
                }
            } else {
                List<Models.Track> history = Library.history();
                if (history.isEmpty()) {
                    blocks.add(Block.empty("История пуста", "Включите любой трек — он появится здесь"));
                } else {
                    Block.Row clear = new Block.Row("clear", "Очистить историю", Format.plural(history.size(), "запись", "записи", "записей"), R.drawable.ic_delete);
                    clear.action = () -> {
                        Library.clearHistory();
                        rebuild();
                    };
                    blocks.add(Block.row(clear));
                    trackList.addAll(history);
                    for (int i = 0; i < history.size(); i++) {
                        Models.Track track = history.get(i);
                        blocks.add(Block.track(track, i, track.id != null && track.id.equals(playingId())));
                    }
                }
            }
            render(blocks, trackList);
        });
    }

    private static Models.Track firstOfflineVideo(List<Models.Track> tracks) {
        for (Models.Track track : tracks) {
            if (Player.hasOfflineVideo(track)) return track;
        }
        return null;
    }

    /** Открыть конкретный раздел медиатеки (например, «Скачанное»). */
    public void showTab(String value) {
        if (value == null || value.isEmpty()) return;
        tab = value;
        rebuild();
    }

    @Override
    public void onShow() {
        if (!watching) {
            Library.addListener(libraryListener);
            Downloads.addListener(downloadsListener);
            watching = true;
        }
        if (!metaWatchedOnce) {
            metaWatchedOnce = true;
            super.onShow();
        }
        // Данные локальные — показываем сразу, без единого кадра ожидания.
        rebuild();
    }

    @Override
    public void release() {
        if (watching) {
            Library.removeListener(libraryListener);
            Downloads.removeListener(downloadsListener);
            watching = false;
        }
        super.release();
    }

    private static List<String> tabIds() {
        List<String> ids = new ArrayList<>();
        ids.add(TAB_FAVORITES);
        ids.add(TAB_PLAYLISTS);
        ids.add(TAB_DOWNLOADS);
        ids.add(TAB_HISTORY);
        return ids;
    }

    private static List<String> tabLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("Избранное");
        labels.add("Плейлисты");
        labels.add("Скачанное");
        labels.add("История");
        return labels;
    }
}
