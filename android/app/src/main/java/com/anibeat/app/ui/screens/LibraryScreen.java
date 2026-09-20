package com.anibeat.app.ui.screens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.ScreenBase;
import com.anibeat.app.ui.TopBar;

import java.util.ArrayList;
import java.util.List;

/** Медиатека — порт pages/Library.tsx (Избранное / Скачано / Плейлисты / История). */
public class LibraryScreen extends ScreenBase {

    private int tab; // 0 избранное, 1 скачано, 2 плейлисты, 3 история
    private TopBar topBar;
    private LinearLayout tabsHolder;
    private LinearLayout bodyHolder;

    public LibraryScreen(MainActivity activity) {
        super(activity);
    }

    /** Открыть конкретную вкладку (вызывается из переходов с других экранов). */
    public void openTab(int index) {
        setTab(index);
    }

    private void setTab(int index) {
        tab = index;
        fillBody();
        updateTopActions();
    }

    @Override
    protected View build() {
        Context c = ctx();
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Theme.BG);
        topBar = new TopBar(activity, "Медиатека", false, true, false);
        root.addView(topBar);
        tabsHolder = Ui.column(c);
        root.addView(tabsHolder);
        bodyHolder = Ui.column(c);
        bodyHolder.setPadding(0, 0, 0, dp(24));
        View scroll = scroller(bodyHolder);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (scroll instanceof LinearLayout) topBar.bindScroll(scrollViewOf((LinearLayout) scroll));
        updateTabs();
        fillBody();
        updateTopActions();
        return root;
    }

    private void updateTabs() {
        tabsHolder.removeAllViews();
        Ui.Segmented segmented = new Ui.Segmented(ctx(), new String[]{"Избранное", "Скачано", "Плейлисты", "История"}, tab, this::setTab);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        sp.leftMargin = dp(16);
        sp.rightMargin = dp(16);
        sp.topMargin = dp(8);
        sp.bottomMargin = dp(6);
        tabsHolder.addView(segmented, sp);
    }

    private void updateTopActions() {
        topBar.actions().removeAllViews();
        Context c = ctx();
        if (tab == 3 && !Library.history().isEmpty()) {
            topBar.addAction(Ui.iconButton(c, "delete", 20, Theme.ON, () -> {
                Library.clearHistory();
                activity.toaster().show("История очищена");
                fillBody();
                updateTopActions();
            }));
        } else if (tab == 2) {
            topBar.addAction(Ui.iconButton(c, "add", 22, Theme.ON, () -> {
                activity.sheets().openCreatePlaylist(() -> {
                    tab = 2;
                    fillBody();
                });
            }));
        }
    }

    @Override
    public void rebuild() {
        if (bodyHolder == null) {
            super.rebuild();
            return;
        }
        fillBody();
        updateTopActions();
    }

    private void fillBody() {
        if (bodyHolder == null) return;
        Context c = ctx();
        bodyHolder.removeAllViews();
        switch (tab) {
            case 0:
                fillFavorites(c);
                break;
            case 1:
                fillDownloads(c);
                break;
            case 2:
                fillPlaylists(c);
                break;
            default:
                fillHistory(c);
                break;
        }
    }

    private void fillFavorites(Context c) {
        List<Models.Track> favorites = Library.favorites();
        if (favorites.isEmpty()) {
            bodyHolder.addView(Ui.emptyState(c, "favorite_border", "Пока пусто", "Нажмите сердечко у трека — он появится здесь.", "Найти музыку", () -> activity.showTab(1, true)));
            return;
        }
        bodyHolder.addView(playBar(favorites, false));
        bodyHolder.addView(trackList(favorites, false));
    }

    private void fillHistory(Context c) {
        List<Models.Track> history = Library.history();
        if (history.isEmpty()) {
            bodyHolder.addView(Ui.emptyState(c, "history", "История пуста", "Здесь появятся треки, которые вы слушали.", null, null));
            return;
        }
        bodyHolder.addView(playBar(history, false));
        bodyHolder.addView(trackList(history, false));
    }

    private void fillDownloads(Context c) {
        List<Downloads.Job> jobs = Downloads.jobs();
        List<Models.Track> offlineTracks = Format.uniqueBy(Downloads.offlineTracks(), t -> t.id);

        if (!jobs.isEmpty()) {
            TextView title = Ui.text(c, "Загрузки", 15f, Theme.ON, true);
            TextView clear = Ui.text(c, "Очистить", 14f, Theme.PRIMARY);
            clear.setOnClickListener(v -> {
                Downloads.clearFinished();
                fillBody();
            });
            Ui.tap(clear);
            LinearLayout head = Ui.row(c);
            head.setPadding(dp(16), dp(16), dp(16), dp(4));
            head.addView(title, Ui.lpw(1f));
            head.addView(clear);
            bodyHolder.addView(head);

            LinearLayout group = Ui.listGroup(c, null, null);
            LinearLayout body = Ui.groupBody(group);
            for (Downloads.Job job : jobs) {
                LinearLayout row = Ui.row(c);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                CoverView cover = new CoverView(c);
                cover.setRadiusDp(8f);
                cover.setIconSizeDp(14f);
                cover.setUrl(job.track.coverSmall != null ? job.track.coverSmall : job.track.cover, null);
                row.addView(cover, Ui.lp(dp(40), dp(40)));
                LinearLayout texts = Ui.column(c);
                TextView name = Ui.text(c, job.track.title, 15f, Theme.ON);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                String status = jobStatus(job);
                TextView sub = Ui.text(c, status, 12.5f, Theme.ON_VARIANT);
                texts.addView(name);
                texts.addView(sub);
                LinearLayout.LayoutParams tp = Ui.lpw(1f);
                tp.leftMargin = dp(12);
                row.addView(texts, tp);
                boolean live = job.status == Downloads.Status.QUEUED || job.status == Downloads.Status.DOWNLOADING;
                row.addView(Ui.iconButton(c, live ? "stop" : "close", live ? 14 : 16, Theme.ON_DIM, () -> {
                    if (live) Downloads.cancel(job.key);
                    else Downloads.dismiss(job.key);
                    fillBody();
                }));
                body.addView(row);
                if (live) {
                    FrameLayout bar = Ui.progressBar(c, 4f);
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
                    bp.leftMargin = dp(12);
                    bp.rightMargin = dp(12);
                    bp.bottomMargin = dp(10);
                    body.addView(bar, bp);
                    Ui.setProgress(bar, job.total > 0 ? job.received * 100f / job.total : 0f);
                }
            }
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.topMargin = dp(4);
            bodyHolder.addView(group, gp);
        }

        if (offlineTracks.isEmpty()) {
            bodyHolder.addView(Ui.emptyState(c, "cloud_download", "Нет загрузок", "В меню трека выберите «Сохранить офлайн» — он будет играть без интернета.", "Найти музыку", () -> activity.showTab(1, true)));
            return;
        }
        bodyHolder.addView(playBar(offlineTracks, true));
        TextView stats = Ui.text(c, Format.plural(Downloads.offlineList().size(), "файл", "файла", "файлов") + " · " + Downloads.formatBytes(Downloads.offlineTotalSize()), 12.5f, Theme.ON_VARIANT);
        stats.setPadding(dp(20), dp(6), dp(20), dp(4));
        bodyHolder.addView(stats);

        LinearLayout list = Ui.column(c);
        list.setPadding(dp(16), 0, 0, 0);
        for (Models.Track t : offlineTracks) {
            final Models.Track track = t;
            list.addView(Cards.trackRow(activity, t, offlineTracks, true, false, false, () -> {
                Downloads.removeOffline(track.id, Downloads.KIND_AUDIO);
                fillBody();
            }, null));
        }
        bodyHolder.addView(list);
    }

    private static String jobStatus(Downloads.Job j) {
        switch (j.status) {
            case QUEUED:
                return "В очереди";
            case DOWNLOADING:
                return j.total > 0 ? Math.round(j.received * 100f / j.total) + "% · " + Downloads.formatBytes(j.total) : Downloads.formatBytes(j.received);
            case DONE:
                return "Готово · " + Downloads.formatBytes(j.received);
            case ERROR:
                return j.error != null ? j.error : "Ошибка";
            case CANCELLED:
                return "Отменено";
            default:
                return "";
        }
    }

    private void fillPlaylists(Context c) {
        List<Models.Playlist> playlists = Library.playlists();
        if (playlists.isEmpty()) {
            bodyHolder.addView(Ui.emptyState(c, "queue_music", "Нет плейлистов", "Создайте плейлист и соберите в нём любимые темы.", "Создать", () -> activity.sheets().openCreatePlaylist(() -> {
                tab = 2;
                fillBody();
            })));
            return;
        }
        LinearLayout group = Ui.listGroup(c, null, null);
        LinearLayout body = Ui.groupBody(group);
        for (Models.Playlist pl : playlists) {
            LinearLayout row = Ui.row(c);
            row.setPadding(dp(12), dp(8), dp(14), dp(8));
            FrameLayout mosaic = new FrameLayout(c);
            mosaic.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 9f)));
            int cell = dp(24);
            for (int i = 0; i < Math.min(4, pl.tracks.size()); i++) {
                CoverView cv = new CoverView(c);
                cv.setRadiusDp(0f);
                cv.setIconSizeDp(11f);
                cv.setUrl(pl.tracks.get(i).coverSmall != null ? pl.tracks.get(i).coverSmall : pl.tracks.get(i).cover, null);
                FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(cell, cell);
                cp.leftMargin = (i % 2) * cell;
                cp.topMargin = (i / 2) * cell;
                mosaic.addView(cv, cp);
            }
            if (pl.tracks.isEmpty()) {
                placeholder(mosaic, cell);
            }
            row.addView(mosaic, Ui.lp(dp(48), dp(48)));
            LinearLayout texts = Ui.column(c);
            TextView name = Ui.text(c, pl.name, 16f, Theme.ON);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name);
            texts.addView(Ui.text(c, Format.plural(pl.tracks.size(), "трек", "трека", "треков"), 13f, Theme.ON_VARIANT));
            LinearLayout.LayoutParams tp = Ui.lpw(1f);
            tp.leftMargin = dp(12);
            row.addView(texts, tp);
            row.addView(Ui.icon(c, "chevron_right", 15, Theme.ON_DIM));
            row.setOnClickListener(v -> activity.openPlaylist(pl.id));
            Ui.tap(row);
            body.addView(row);
        }
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(12);
        gp.leftMargin = dp(0);
        bodyHolder.addView(group, gp);
    }

    private void placeholder(FrameLayout mosaic, int cell) {
        FrameLayout holder = new FrameLayout(ctx());
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(20), dp(20));
        hp.gravity = android.view.Gravity.CENTER;
        holder.addView(Ui.icon(ctx(), "queue_music", 20, Theme.ON_DIM), hp);
        mosaic.addView(holder, new FrameLayout.LayoutParams(cell * 2, cell * 2));
    }

    private View playBar(List<Models.Track> tracks, boolean shuffle) {
        Context c = ctx();
        LinearLayout row = Ui.row(c);
        row.setPadding(dp(16), dp(10), dp(16), dp(6));
        LinearLayout play = Ui.row(c);
        play.setGravity(android.view.Gravity.CENTER);
        play.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
        play.setPadding(0, dp(11), 0, dp(11));
        play.addView(Ui.icon(c, "play_arrow", 18, Theme.PRIMARY));
        TextView label = Ui.text(c, "Слушать", 15f, Theme.PRIMARY, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        play.addView(label, lp);
        play.setOnClickListener(v -> {
            if (tracks.isEmpty()) return;
            Player.playTracks(tracks, 0, false);
            activity.nowPlaying().open();
        });
        Ui.tapScale(play);
        row.addView(play, Ui.lpw(1f));

        if (shuffle) {
            LinearLayout mix = Ui.row(c);
            mix.setGravity(android.view.Gravity.CENTER);
            mix.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
            mix.setPadding(0, dp(11), 0, dp(11));
            mix.addView(Ui.icon(c, "shuffle", 18, Theme.PRIMARY));
            TextView mixLabel = Ui.text(c, "Вперемешку", 15f, Theme.PRIMARY, true);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.leftMargin = dp(6);
            mix.addView(mixLabel, mp);
            mix.setOnClickListener(v -> {
                if (tracks.size() < 2) return;
                Player.playTracks(tracks, 0, true);
                activity.nowPlaying().open();
            });
            Ui.tapScale(mix);
            LinearLayout.LayoutParams rp = Ui.lpw(1f);
            rp.leftMargin = dp(8);
            row.addView(mix, rp);
        }
        return row;
    }

    private View trackList(List<Models.Track> tracks, boolean withRemove) {
        Context c = ctx();
        LinearLayout list = Ui.column(c);
        list.setPadding(dp(16), dp(4), 0, 0);
        for (Models.Track t : tracks) {
            list.addView(Cards.trackRow(activity, t, tracks, true, false, false, null, null));
        }
        return list;
    }

    @Override
    public String title() {
        return "Медиатека";
    }
}
