package com.anibeat.app.ui.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.ScreenBase;
import com.anibeat.app.ui.TopBar;

import java.util.ArrayList;
import java.util.List;

/** Плейлист — порт PlaylistPage из pages/Library.tsx. */
public class PlaylistScreen extends ScreenBase {

    private final String id;
    private Models.Playlist playlist;
    private TopBar topBar;

    public PlaylistScreen(MainActivity activity, String id) {
        super(activity);
        this.id = id;
        this.playlist = Library.playlist(id);
    }

    @Override
    protected View build() {
        Context c = ctx();
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Theme.BG);
        if (playlist == null) {
            topBar = new TopBar(activity, "Плейлист", true, false, false);
            root.addView(topBar);
            root.addView(Ui.emptyState(c, "queue_music", "Плейлист не найден", null, "В медиатеку", () -> {
                activity.pop();
                activity.showTab(3, true);
            }), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
        topBar = new TopBar(activity, playlist.name, true, false, false);
        topBar.addAction(Ui.iconButton(c, "more_horiz", 21, Theme.ON, () -> activity.sheets().openPlaylistMenu(playlist, null)));
        root.addView(topBar);

        LinearLayout body = Ui.column(c);
        body.setPadding(0, 0, 0, dp(24));
        body.addView(headerBlock(c));
        if (!playlist.tracks.isEmpty()) body.addView(playBar());
        if (playlist.tracks.isEmpty()) {
            body.addView(Ui.emptyState(c, "playlist_add", "Плейлист пуст", "Меню трека → «В плейлист».", "Найти музыку", () -> activity.showTab(1, true)));
        } else {
            LinearLayout list = Ui.column(c);
            list.setPadding(dp(16), dp(4), 0, 0);
            final List<Models.Track> tracks = playlist.tracks;
            for (Models.Track t : tracks) {
                final String trackId = t.id;
                list.addView(Cards.trackRow(activity, t, tracks, true, false, false, () -> {
                    Library.removeFromPlaylist(id, trackId);
                    rebuild();
                }, null));
            }
            body.addView(list);
        }
        root.addView(scroller(body), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    @Override
    public void rebuild() {
        playlist = Library.playlist(id);
        super.rebuild();
    }

    private View headerBlock(Context c) {
        LinearLayout head = Ui.column(c);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(dp(16), dp(10), dp(16), 0);
        FrameLayout mosaic = new FrameLayout(c);
        mosaic.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 14f)));
        int cell = dp(75);
        for (int i = 0; i < Math.min(4, playlist.tracks.size()); i++) {
            CoverView cv = new CoverView(c);
            cv.setRadiusDp(0f);
            cv.setIconSizeDp(16f);
            cv.setUrl(playlist.tracks.get(i).cover != null ? playlist.tracks.get(i).cover : playlist.tracks.get(i).coverSmall, null);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(cell, cell);
            cp.leftMargin = (i % 2) * cell;
            cp.topMargin = (i / 2) * cell;
            mosaic.addView(cv, cp);
        }
        if (playlist.tracks.isEmpty()) {
            FrameLayout holder = new FrameLayout(c);
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(40), dp(40));
            hp.gravity = Gravity.CENTER;
            holder.addView(Ui.icon(c, "queue_music", 40, Theme.ON_DIM), hp);
            mosaic.addView(holder, new FrameLayout.LayoutParams(cell * 2, cell * 2));
        }
        head.addView(mosaic, Ui.lp(cell * 2, cell * 2));

        TextView name = Ui.heading(c, playlist.name, 22f, Theme.ON);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(12);
        head.addView(name, np);
        TextView count = Ui.text(c, Format.plural(playlist.tracks.size(), "трек", "трека", "треков"), 13.5f, Theme.ON_VARIANT);
        count.setGravity(Gravity.CENTER);
        head.addView(count);
        return head;
    }

    private View playBar() {
        Context c = ctx();
        List<Models.Track> tracks = new ArrayList<>(playlist.tracks);
        LinearLayout row = Ui.row(c);
        row.setPadding(dp(16), dp(14), dp(16), dp(6));
        LinearLayout play = Ui.row(c);
        play.setGravity(Gravity.CENTER);
        play.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
        play.setPadding(0, dp(11), 0, dp(11));
        play.addView(Ui.icon(c, "play_arrow", 18, Theme.PRIMARY));
        TextView label = Ui.text(c, "Слушать", 15f, Theme.PRIMARY, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        play.addView(label, lp);
        play.setOnClickListener(v -> {
            Player.playTracks(tracks, 0, false);
            activity.nowPlaying().open();
        });
        Ui.tapScale(play);
        row.addView(play, Ui.lpw(1f));

        LinearLayout mix = Ui.row(c);
        mix.setGravity(Gravity.CENTER);
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
        return row;
    }

    @Override
    public String title() {
        return playlist == null ? "Плейлист" : playlist.name;
    }
}
