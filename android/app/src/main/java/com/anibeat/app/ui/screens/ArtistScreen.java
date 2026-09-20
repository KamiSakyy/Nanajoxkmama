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
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.ScreenBase;
import com.anibeat.app.ui.TopBar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Исполнитель — порт ArtistPage из pages/Anime.tsx. */
public class ArtistScreen extends ScreenBase {

    private final String slug;
    private Models.ArtistDetail detail;
    private boolean loading = true;
    private String error;
    private String sort = "new";

    private LinearLayout bodyHolder;

    public ArtistScreen(MainActivity activity, String slug) {
        super(activity);
        this.slug = slug;
        load();
    }

    private void load() {
        loading = true;
        Api.getArtist(slug, (artist, error) -> {
            loading = false;
            if (artist != null) {
                detail = artist;
                List<Integer> ids = new ArrayList<>();
                for (Models.Track t : artist.tracks) if (t.anime.malId != null) ids.add(t.anime.malId);
                Meta.warm(ids);
            } else {
                this.error = error;
            }
            fillBody();
        });
    }

    private List<Models.Track> sortedTracks() {
        if (detail == null) return new ArrayList<>();
        List<Models.Track> list = new ArrayList<>(detail.tracks);
        if ("old".equals(sort)) java.util.Collections.reverse(list);
        else if ("anime".equals(sort)) java.util.Collections.sort(list, (a, b) -> a.anime.name.compareToIgnoreCase(b.anime.name));
        return list;
    }

    @Override
    protected View build() {
        Context c = ctx();
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Theme.BG);

        LinearLayout content = Ui.column(c);
        TopBar topBar = new TopBar(activity, null, true, false, true);
        content.addView(topBar);
        bodyHolder = Ui.column(c);
        bodyHolder.setPadding(0, 0, 0, dp(24));
        content.addView(scroller(bodyHolder), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content);
        fillBody();
        return root;
    }

    @Override
    public void rebuild() {
        if (bodyHolder == null) {
            super.rebuild();
            return;
        }
        fillBody();
    }

    private void fillBody() {
        if (bodyHolder == null) return;
        Context c = ctx();
        bodyHolder.removeAllViews();

        if (loading && detail == null) {
            LinearLayout box = Ui.column(c);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(dp(16), dp(16), dp(16), 0);
            box.addView(Ui.skeleton(c, dp(144), dp(144), 72f), Ui.lp(dp(144), dp(144)));
            box.addView(Ui.skeleton(c, dp(180), dp(22), 8f), Ui.lp(dp(180), dp(22)));
            bodyHolder.addView(box);
            return;
        }
        if (detail == null) {
            bodyHolder.addView(Ui.errorState(c, error == null ? "Не найдено" : error, this::load));
            return;
        }

        List<Models.Track> tracks = sortedTracks();
        Set<Long> animeIds = new LinkedHashSet<>();
        for (Models.Track t : tracks) animeIds.add(t.anime.id);

        LinearLayout head = Ui.column(c);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(dp(16), dp(8), dp(16), 0);
        FrameLayout avatarBox = new FrameLayout(c);
        if ((detail.image != null && !detail.image.isEmpty()) || (detail.imageSmall != null && !detail.imageSmall.isEmpty())) {
            CoverView avatar = new CoverView(c);
            avatar.setRadiusDp(72f);
            avatar.setIconSizeDp(44f);
            avatar.setUrl(detail.image != null ? detail.image : detail.imageSmall, detail.imageSmall);
            avatarBox.addView(avatar, new FrameLayout.LayoutParams(dp(144), dp(144)));
        } else {
            FrameLayout holder = new FrameLayout(c);
            holder.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 72f)));
            holder.addView(Ui.icon(c, "mic", 48, Theme.ON_DIM));
            avatarBox.addView(holder, new FrameLayout.LayoutParams(dp(144), dp(144)));
        }
        head.addView(avatarBox, Ui.lp(dp(144), dp(144)));

        TextView name = Ui.heading(c, detail.name, 28f, Theme.ON);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(14);
        head.addView(name, np);

        TextView stats = Ui.text(c, Format.plural(tracks.size(), "трек", "трека", "треков") + " · " + animeIds.size() + " аниме", 13.5f, Theme.ON_VARIANT);
        head.addView(stats);

        LinearLayout actions = Ui.row(c);
        actions.setGravity(Gravity.CENTER);
        LinearLayout play = actionButton(c, "play_arrow", "Слушать", () -> {
            if (tracks.isEmpty()) return;
            Player.playTracks(tracks, 0, false);
            activity.nowPlaying().open();
        });
        LinearLayout shuffle = actionButton(c, "shuffle", "Вперемешку", () -> {
            if (tracks.size() < 2) return;
            Player.playTracks(tracks, 0, true);
            activity.nowPlaying().open();
        });
        actions.addView(play, Ui.lpw(1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(8);
        actions.addView(shuffle, sp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(16);
        head.addView(actions, ap);
        if (tracks.isEmpty()) actions.setAlpha(0.4f);
        bodyHolder.addView(head);

        if (tracks.size() > 1) {
            LinearLayout chips = hrow();
            chips.setPadding(dp(16), dp(18), dp(16), dp(6));
            chips.addView(Ui.chip(c, "Новые", null, "new".equals(sort), () -> {
                sort = "new";
                fillBody();
            }));
            chips.addView(spaced(Ui.chip(c, "Старые", null, "old".equals(sort), () -> {
                sort = "old";
                fillBody();
            })));
            chips.addView(spaced(Ui.chip(c, "По аниме", null, "anime".equals(sort), () -> {
                sort = "anime";
                fillBody();
            })));
            bodyHolder.addView(hscroll(chips));
        }

        if (tracks.isEmpty()) {
            bodyHolder.addView(Ui.emptyState(c, "mic", "Треков пока нет", null, "Искать", () -> {
                activity.showTab(1, true);
            }));
        } else {
            LinearLayout list = Ui.column(c);
            list.setPadding(dp(16), dp(4), 0, 0);
            for (Models.Track t : tracks) list.addView(Cards.trackRow(activity, t, tracks, true, false, false, null, null));
            bodyHolder.addView(list);
        }
    }

    private LinearLayout spaced(TextView chip) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        chip.setLayoutParams(p);
        LinearLayout holder = Ui.row(ctx());
        holder.addView(chip);
        return holder;
    }

    private static LinearLayout actionButton(Context c, String icon, String label, Runnable click) {
        LinearLayout box = Ui.row(c);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
        box.setPadding(Theme.dp(c, 18), Theme.dp(c, 11), Theme.dp(c, 18), Theme.dp(c, 11));
        box.addView(Ui.icon(c, icon, 18, Theme.PRIMARY));
        TextView tv = Ui.text(c, label, 15f, Theme.PRIMARY, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Theme.dp(c, 6);
        box.addView(tv, p);
        box.setOnClickListener(v -> click.run());
        Ui.tapScale(box);
        return box;
    }

    @Override
    public String title() {
        return detail == null ? "" : detail.name;
    }
}
