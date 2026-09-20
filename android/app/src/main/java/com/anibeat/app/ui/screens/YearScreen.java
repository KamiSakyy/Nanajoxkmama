package com.anibeat.app.ui.screens;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.ScreenBase;
import com.anibeat.app.ui.TopBar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Год каталога — порт YearPage из pages/Browse.tsx. */
public class YearScreen extends ScreenBase {

    private static final int CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR);

    private final int year;
    private String season;
    private TopBar topBar;
    private LinearLayout bodyHolder;
    private List<Models.AnimeSummary> items = new ArrayList<>();
    private boolean loading = true;
    private boolean busy;
    private int page = 1;
    private int seq;

    public YearScreen(MainActivity activity, int year) {
        super(activity);
        this.year = year;
        load();
    }

    private void load() {
        final int current = ++seq;
        loading = true;
        Api.getSeasonAnime(year, season, 1, (list, error) -> {
            if (current != seq) return;
            loading = false;
            if (list != null) {
                items = list;
                page = 1;
                List<Integer> ids = new ArrayList<>();
                for (Models.AnimeSummary a : list) if (a.malId != null) ids.add(a.malId);
                Meta.warm(ids);
            }
            fillBody();
        });
    }

    private void loadMore() {
        final int next = page + 1;
        final int current = seq;
        Api.getSeasonAnime(year, season, next, (list, error) -> {
            if (current != seq || list == null) return;
            List<Models.AnimeSummary> merged = new ArrayList<>(items);
            merged.addAll(list);
            items = com.anibeat.app.ui.Format.uniqueBy(merged, a -> String.valueOf(a.id));
            page = next;
            fillBody();
        });
    }

    @Override
    protected View build() {
        Context c = ctx();
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Theme.BG);
        topBar = new TopBar(activity, String.valueOf(year), true, false, false);

        FrameLayout prev = Ui.iconButton(c, "expand_less", 20, Theme.ON, () -> {
            if (year > 1963) activity.openYear(year - 1);
        });
        FrameLayout next = Ui.iconButton(c, "expand_more", 20, Theme.ON, () -> {
            if (year < CURRENT_YEAR + 1) activity.openYear(year + 1);
        });
        topBar.addAction(prev);
        topBar.addAction(next);
        root.addView(topBar);

        LinearLayout chipsRow = hrow();
        chipsRow.setPadding(dp(16), dp(6), dp(16), dp(6));
        chipsRow.addView(Ui.chip(c, "Весь год", null, season == null, () -> {
            season = null;
            load();
        }));
        for (int i = 0; i < Api.SEASONS.length; i++) {
            final String value = Api.SEASONS[i];
            TextView chip = Ui.chip(c, Api.SEASONS_RU[i], null, value.equals(season), () -> {
                season = value;
                load();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.leftMargin = dp(8);
            chipsRow.addView(chip, cp);
        }
        root.addView(hscroll(chipsRow));

        bodyHolder = Ui.column(c);
        bodyHolder.setPadding(0, 0, 0, dp(24));
        root.addView(scroller(bodyHolder), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
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

        if (season != null && !items.isEmpty()) {
            LinearLayout wrap = Ui.row(c);
            wrap.setPadding(dp(16), dp(8), dp(16), dp(4));
            LinearLayout shuffle = Ui.row(c);
            shuffle.setGravity(android.view.Gravity.CENTER);
            shuffle.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
            shuffle.setPadding(0, dp(11), 0, dp(11));
            shuffle.addView(Ui.icon(c, "shuffle", 18, Theme.ON));
            TextView label = Ui.text(c, busy ? "Загрузка…" : "Слушать сезон", 15f, Theme.ON, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(6);
            shuffle.addView(label, lp);
            shuffle.setOnClickListener(v -> {
                busy = true;
                fillBody();
                Api.getSeasonTracks(year, season, (tracks, error) -> {
                    busy = false;
                    if (tracks == null || tracks.isEmpty()) {
                        activity.toaster().show("Нет треков");
                        fillBody();
                        return;
                    }
                    Player.playTracks(tracks, 0, true);
                    activity.nowPlaying().open();
                    fillBody();
                });
            });
            Ui.tapScale(shuffle);
            wrap.addView(shuffle, Ui.lpw(1f));
            bodyHolder.addView(wrap);
        }

        if (loading && items.isEmpty()) {
            List<View> skeletons = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                LinearLayout col = Ui.column(c);
                col.addView(Ui.skeleton(c, 0, 0, 12f), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
                col.addView(Ui.skeleton(c, 0, dp(12), 6f), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));
                skeletons.add(col);
            }
            LinearLayout grid = gridRow(skeletons, 3);
            grid.setPadding(dp(10), dp(8), dp(10), 0);
            bodyHolder.addView(grid);
            return;
        }
        if (items.isEmpty()) {
            bodyHolder.addView(Ui.emptyState(c, "calendar", "Ничего не найдено", "Для этого периода пока нет записей.", null, null));
            return;
        }
        List<View> cards = new ArrayList<>();
        for (Models.AnimeSummary a : items) cards.add(Cards.animeCard(activity, a, true));
        LinearLayout grid = gridRow(cards, 3);
        grid.setPadding(dp(10), dp(8), dp(10), 0);
        bodyHolder.addView(grid);

        if (items.size() >= 30) {
            LinearLayout wrap = Ui.row(c);
            wrap.setGravity(android.view.Gravity.CENTER);
            wrap.setPadding(dp(16), dp(16), dp(16), 0);
            wrap.addView(Ui.tintedButton(c, "Показать ещё", this::loadMore));
            bodyHolder.addView(wrap);
        }
    }

    @Override
    public String title() {
        return String.valueOf(year);
    }
}
