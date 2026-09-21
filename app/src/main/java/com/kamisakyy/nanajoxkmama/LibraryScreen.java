package com.kamisakyy.nanajoxkmama;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Library — port of pages/Library.tsx: favorites, downloads, playlists, history. */
public final class LibraryScreen implements Screen {
    private final Ui.Host host;
    private final Activity a;
    private final LinearLayout root;
    private final LinearLayout content;
    private String tab = "favorites";
    private final Downloader.Listener dlListener = this::render;

    public LibraryScreen(Ui.Host host) {
        this.host = host;
        this.a = host.activity();
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        TextView large = Ui.text(a, "Медиатека", 28, Ui.ON, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(16), Ui.dp(14), 0, Ui.dp(8));
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(large, lp);
        head.addView(Ui.iconBtn(a, "settings", 20, Ui.ON, v -> host.openSettings()));
        head.setPadding(0, 0, Ui.dp(8), 0);
        root.addView(head);

        Ui.Segmented seg = new Ui.Segmented(a,
                new String[]{"favorites", "downloads", "playlists", "history"},
                new String[]{"Избранное", "Скачано", "Плейлисты", "История"},
                tab, v -> {
                    tab = v;
                    render();
                });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34));
        sp.setMargins(Ui.dp(16), Ui.dp(2), Ui.dp(16), Ui.dp(6));
        root.addView(seg, sp);

        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(28));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        Downloader.addListener(dlListener);
        render();
    }

    public void setTab(String t) {
        tab = t;
        render();
    }

    private void render() {
        content.removeAllViews();
        if ("favorites".equals(tab)) renderFavorites();
        else if ("downloads".equals(tab)) renderDownloads();
        else if ("playlists".equals(tab)) renderPlaylists();
        else renderHistory();
    }

    private void renderFavorites() {
        ArrayList<Track> favs = Store.getFavorites();
        if (favs.isEmpty()) {
            content.addView(Ui.emptyState(a, "favorite_border", "Пока пусто",
                    "Нажмите сердечко у трека — он появится здесь.", "Найти музыку", v -> host.switchTab(1)));
            return;
        }
        content.addView(playBar(favs));
        for (Track t : favs) content.addView(new Ui.TrackRow(a, host, t, favs, true, false, null));
    }

    private void renderDownloads() {
        ArrayList<Downloader.Job> jobs = Downloader.jobs();
        if (!jobs.isEmpty()) {
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(12), Ui.dp(2));
            head.addView(Ui.text(a, "Загрузки", 15, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView clear = Ui.text(a, "Очистить", 14, Ui.ACCENT, true);
            clear.setOnClickListener(v -> Downloader.clearFinished());
            head.addView(clear);
            content.addView(head);
            LinearLayout group = new LinearLayout(a);
            group.setOrientation(LinearLayout.VERTICAL);
            group.setBackground(Ui.rounded(Ui.S2, 14));
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.setMargins(Ui.dp(16), Ui.dp(2), Ui.dp(16), Ui.dp(8));
            content.addView(group, gp);
            for (Downloader.Job j : jobs) {
                group.addView(jobRow(j));
            }
        }
        ArrayList<Track> offline = Store.getOfflineTracks();
        if (offline.isEmpty()) {
            content.addView(Ui.emptyState(a, "cloud_download", "Нет загрузок",
                    "В меню трека выберите «Сохранить офлайн» — он будет играть без интернета.",
                    "Найти музыку", v -> host.switchTab(1)));
            return;
        }
        content.addView(playBar(offline));
        TextView size = Ui.text(a, Util.pluralRu(offline.size(), "файл", "файла", "файлов")
                + " · " + Util.formatBytes(Store.offlineBytes()), 12.5f, Ui.VAR, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(Ui.dp(20), Ui.dp(2), 0, Ui.dp(4));
        content.addView(size, sp);
        for (Track t : offline) {
            content.addView(new Ui.TrackRow(a, host, t, offline, true, false,
                    () -> {
                        Store.removeOffline(t.id);
                        host.toast("Удалено из офлайн");
                        render();
                    }));
        }
    }

    private View jobRow(Downloader.Job j) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(6), Ui.dp(8));
        Ui.CoverView cover = Ui.cover(a, 40, 8);
        cover.load(j.track.coverSmall.isEmpty() ? j.track.cover : j.track.coverSmall, true);
        row.addView(cover);
        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.leftMargin = Ui.dp(10);
        mid.addView(Ui.text(a, j.track.title, 14f, Ui.ON, false));
        TextView status = Ui.text(a, jobStatus(j), 12f, j.status == Downloader.ERROR ? Ui.ERR : j.status == Downloader.DONE ? Ui.GREEN : Ui.VAR, false);
        mid.addView(status);
        if (j.status == Downloader.DOWNLOADING) {
            LinearLayout progressBg = new LinearLayout(a);
            progressBg.setBackground(Ui.rounded(0x29FFFFFF, 2));
            View fill = new View(a);
            fill.setBackground(Ui.rounded(Ui.ON, 2));
            float frac = j.total > 0 ? j.received / (float) j.total : 0.2f;
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams((int) (Ui.dp(200) * frac), Ui.dp(3));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(3));
            blp.topMargin = Ui.dp(5);
            progressBg.addView(fill, flp);
            mid.addView(progressBg, blp);
        }
        row.addView(mid, mp);
        boolean live = j.status == Downloader.QUEUED || j.status == Downloader.DOWNLOADING;
        row.addView(Ui.iconBtn(a, live ? "stop" : "close", 15, Ui.DIM,
                v -> {
                    if (live) Downloader.cancel(j.key);
                    else Downloader.dismiss(j.key);
                    render();
                }));
        return row;
    }

    private String jobStatus(Downloader.Job j) {
        switch (j.status) {
            case Downloader.QUEUED: return "В очереди";
            case Downloader.DOWNLOADING:
                return j.total > 0 ? Math.round(j.received * 100f / j.total) + "% · " + Util.formatBytes(j.total)
                        : Util.formatBytes(j.received);
            case Downloader.DONE: return "Готово · " + Util.formatBytes(j.received);
            case Downloader.ERROR: return j.error == null || j.error.isEmpty() ? "Ошибка" : j.error;
            case Downloader.CANCELLED: return "Отменено";
        }
        return "";
    }

    private void renderPlaylists() {
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(12), Ui.dp(2));
        head.addView(Ui.text(a, "Плейлисты", 15, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView add = Ui.text(a, "+ Новый", 14, Ui.ACCENT, true);
        add.setOnClickListener(v -> Sheets.promptName(host, "Новый плейлист", name -> {
            Store.createPlaylist(name, null);
            host.toast("Плейлист создан");
            render();
        }));
        head.addView(add);
        content.addView(head);
        ArrayList<Playlist> pls = Store.getPlaylists();
        if (pls.isEmpty()) {
            content.addView(Ui.emptyState(a, "queue_music", "Нет плейлистов",
                    "Создайте плейлист и соберите в нём любимые темы.", "Создать", v ->
                            Sheets.promptName(host, "Новый плейлист", name -> {
                                Store.createPlaylist(name, null);
                                host.toast("Плейлист создан");
                                render();
                            })));
            return;
        }
        LinearLayout group = new LinearLayout(a);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(Ui.rounded(Ui.S2, 14));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.setMargins(Ui.dp(16), Ui.dp(2), Ui.dp(16), Ui.dp(8));
        content.addView(group, gp);
        for (Playlist pl : pls) {
            final Playlist p = pl;
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(6), Ui.dp(8));
            row.setBackground(Ui.ripple(Ui.rounded(Color.TRANSPARENT, 10)));
            row.addView(Sheets.mosaic(host, pl), new LinearLayout.LayoutParams(Ui.dp(48), Ui.dp(48)));
            LinearLayout mid = new LinearLayout(a);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mp.leftMargin = Ui.dp(12);
            mid.addView(Ui.text(a, pl.name, 15.5f, Ui.ON, false));
            mid.addView(Ui.text(a, Util.pluralRu(pl.tracks.size(), "трек", "трека", "треков"), 12.5f, Ui.VAR, false));
            row.addView(mid, mp);
            row.addView(Ui.iconBtn(a, "more_horiz", 18, Ui.DIM, v -> playlistMenu(p)));
            row.setOnClickListener(v -> host.openPlaylist(p.id));
            group.addView(row);
        }
    }

    private void playlistMenu(Playlist p) {
        Sheets.promptChoice(host, p.name, new String[]{"Переименовать", "Удалить плейлист"}, which -> {
            if (which == 0) {
                Sheets.promptName(host, p.name, name -> {
                    Store.renamePlaylist(p.id, name);
                    render();
                });
            } else {
                Store.deletePlaylist(p.id);
                host.toast("Плейлист удалён");
                render();
            }
        });
    }

    private void renderHistory() {
        ArrayList<Track> history = Store.getHistory();
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(12), Ui.dp(2));
        head.addView(Ui.text(a, "История", 15, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!history.isEmpty()) {
            TextView clear = Ui.text(a, "Очистить", 14, Ui.ACCENT, true);
            clear.setOnClickListener(v -> {
                Store.clearHistory();
                host.toast("История очищена");
                render();
            });
            head.addView(clear);
        }
        content.addView(head);
        if (history.isEmpty()) {
            content.addView(Ui.emptyState(a, "history", "История пуста",
                    "Здесь появятся треки, которые вы слушали.", null, null));
            return;
        }
        content.addView(playBar(history));
        for (Track t : history) content.addView(new Ui.TrackRow(a, host, t, history, true, false, null));
    }

    private View playBar(List<Track> tracks) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(12), Ui.dp(4));
        View play = Ui.surfaceButton(a, "Слушать", "play_arrow", v -> {
            host.playAll(tracks, 0, false);
            host.openNowPlaying();
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, Ui.dp(40), 1f);
        bar.addView(play, pp);
        if (tracks.size() > 1) {
            View shuffle = Ui.surfaceButton(a, "Вперемешку", "shuffle", v -> {
                host.playAll(tracks, 0, true);
                host.openNowPlaying();
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(40), 1f);
            sp.leftMargin = Ui.dp(8);
            bar.addView(shuffle, sp);
        }
        return bar;
    }

    @Override public View view() { return root; }
    @Override public void onShow() { render(); }
    @Override public void onPlayerChanged() { }

    /* ================= Playlist detail (pushed) ================= */

    public static class PlaylistScreen implements Screen {
        private final Ui.Host host;
        private final Activity a;
        private final LinearLayout root;
        private final LinearLayout content;
        private final String id;

        public PlaylistScreen(Ui.Host host, String id) {
            this.host = host;
            this.a = host.activity();
            this.id = id;
            root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Ui.BG);
            content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            ScrollView scroll = new ScrollView(a);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            render();
        }

        private void render() {
            content.removeAllViews();
            Playlist pl = Store.getPlaylist(id);
            if (pl == null) {
                content.addView(Ui.emptyState(a, "queue_music", "Плейлист не найден", "", null, null));
                return;
            }
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(8), Ui.dp(6));
            FrameLayout mosaic = Sheets.mosaic(host, pl);
            head.addView(mosaic, new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64)));
            LinearLayout mid = new LinearLayout(a);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mp.leftMargin = Ui.dp(14);
            TextView name = Ui.text(a, pl.name, 21, Ui.ON, true);
            mid.addView(name);
            mid.addView(Ui.text(a, Util.pluralRu(pl.tracks.size(), "трек", "трека", "треков"), 13, Ui.VAR, false));
            head.addView(mid, mp);
            head.addView(Ui.iconBtn(a, "more_horiz", 18, Ui.DIM, v -> Sheets.promptChoice(host, pl.name,
                    new String[]{"Переименовать", "Удалить плейлист"}, which -> {
                        if (which == 0) {
                            Sheets.promptName(host, pl.name, n -> {
                                Store.renamePlaylist(pl.id, n);
                                render();
                            });
                        } else {
                            Store.deletePlaylist(pl.id);
                            host.toast("Плейлист удалён");
                        }
                    })));
            content.addView(head);
            if (pl.tracks.isEmpty()) {
                content.addView(Ui.emptyState(a, "music_note", "Пока пусто",
                        "Добавьте треки через меню трека.", null, null));
                return;
            }
            LinearLayout bar = new LinearLayout(a);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(12), Ui.dp(4));
            View play = Ui.surfaceButton(a, "Слушать", "play_arrow", v -> {
                host.playAll(pl.tracks, 0, false);
                host.openNowPlaying();
            });
            bar.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(40), 1f));
            View shuffle = Ui.surfaceButton(a, "Вперемешку", "shuffle", v -> {
                host.playAll(pl.tracks, 0, true);
                host.openNowPlaying();
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(40), 1f);
            sp.leftMargin = Ui.dp(8);
            bar.addView(shuffle, sp);
            content.addView(bar);
            for (Track t : pl.tracks) {
                final Track ft = t;
                content.addView(new Ui.TrackRow(a, host, t, pl.tracks, true, false, () -> {
                    Store.removeFromPlaylist(pl.id, ft.id);
                    host.toast("Убрано из плейлиста");
                    render();
                }));
            }
        }

        @Override public View view() { return root; }
        @Override public void onShow() { render(); }
        @Override public void onPlayerChanged() { }
    }
}
