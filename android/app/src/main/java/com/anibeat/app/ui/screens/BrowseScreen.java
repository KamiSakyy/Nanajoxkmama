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
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.Genres;
import com.anibeat.app.ui.ScreenBase;
import com.anibeat.app.ui.TopBar;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Обзор — порт pages/Browse.tsx (Годы / Новое / OP / ED). */
public class BrowseScreen extends ScreenBase {

    private static final int CURRENT_YEAR = Calendar.getInstance().get(Calendar.YEAR);

    private String type = "years";
    private TopBar topBar;
    private LinearLayout bodyHolder;
    private LinearLayout tabsHolder;

    private List<Models.Track> freshTracks = new ArrayList<>();
    private boolean freshLoading;
    private String freshError;
    private String genre;

    private List<Models.Track> typeTracks = new ArrayList<>();
    private boolean typeLoading;
    private String typeError;
    private int seq;

    public BrowseScreen(MainActivity activity) {
        super(activity);
        loadFresh();
        loadType(true);
    }

    public void setType(String value) {
        if (value == null) return;
        type = value;
        if ("fresh".equals(type)) loadFresh();
        else if (!"years".equals(type)) loadType(true);
        fillBody();
    }

    /* ------------------------------------------------------------------ */
    /* Данные                                                              */
    /* ------------------------------------------------------------------ */

    private void loadFresh() {
        freshLoading = true;
        Api.getFreshTracks(Settings.period, 40, (list, error) -> {
            freshLoading = false;
            if (list != null) {
                freshTracks = list;
                List<Integer> ids = new ArrayList<>();
                for (Models.Track t : list) if (t.anime.malId != null) ids.add(t.anime.malId);
                Meta.warm(ids);
            } else {
                freshError = error;
            }
            fillBody();
        });
    }

    private void loadType(boolean reset) {
        final int current = ++seq;
        typeLoading = true;
        Api.getRandomTracks(24, "OP".equals(type) ? "OP" : "ED", true, (list, error) -> {
            if (current != seq) return;
            typeLoading = false;
            if (list != null) {
                if (reset) typeTracks = new ArrayList<>();
                List<Models.Track> existing = new ArrayList<>(typeTracks);
                existing.addAll(list);
                typeTracks = com.anibeat.app.ui.Format.uniqueBy(existing, t -> t.id);
                List<Integer> ids = new ArrayList<>();
                for (Models.Track t : typeTracks) if (t.anime.malId != null) ids.add(t.anime.malId);
                Meta.warm(ids);
            } else {
                typeError = error;
            }
            fillBody();
        });
    }

    /* ------------------------------------------------------------------ */

    @Override
    protected View build() {
        Context c = ctx();
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Theme.BG);
        topBar = new TopBar(activity, "Обзор", false, true, false);
        root.addView(topBar);

        tabsHolder = Ui.column(c);
        root.addView(tabsHolder);

        bodyHolder = Ui.column(c);
        bodyHolder.setPadding(0, 0, 0, dp(24));
        View scroll = scroller(bodyHolder);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (scroll instanceof LinearLayout) {
            topBar.bindScroll(scrollViewOf((LinearLayout) scroll));
        }
        updateTabs();
        fillBody();
        return root;
    }

    private void updateTabs() {
        if (tabsHolder == null) return;
        tabsHolder.removeAllViews();
        String[] labels = {"Годы", "Новое", "OP", "ED"};
        String[] values = {"years", "fresh", "OP", "ED"};
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(type)) selected = i;
        Ui.Segmented segmented = new Ui.Segmented(ctx(), labels, selected, index -> setType(values[index]));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        sp.leftMargin = dp(16);
        sp.rightMargin = dp(16);
        sp.topMargin = dp(8);
        sp.bottomMargin = dp(6);
        tabsHolder.addView(segmented, sp);
    }

    @Override
    public void rebuild() {
        if (bodyHolder == null) {
            super.rebuild();
            return;
        }
        updateTabs();
        fillBody();
    }

    private void fillBody() {
        if (bodyHolder == null) return;
        Context c = ctx();
        bodyHolder.removeAllViews();
        if ("years".equals(type)) {
            fillYears(c);
        } else if ("fresh".equals(type)) {
            fillFresh(c);
        } else {
            fillType(c);
        }
    }

    private void fillYears(Context c) {
        Map<String, List<Integer>> decades = new LinkedHashMap<>();
        for (int year = CURRENT_YEAR + 1; year >= 1963; year--) {
            String decade = String.valueOf(year / 10 * 10);
            if (!decades.containsKey(decade)) decades.put(decade, new ArrayList<>());
            decades.get(decade).add(year);
        }
        LinearLayout col = Ui.column(c);
        col.setPadding(dp(10), dp(8), dp(10), 0);
        for (Map.Entry<String, List<Integer>> entry : decades.entrySet()) {
            TextView label = Ui.text(c, entry.getKey() + "-е", 15f, Theme.ON_VARIANT, true);
            label.setPadding(dp(6), dp(8), dp(6), dp(8));
            col.addView(label);
            List<View> cells = new ArrayList<>();
            for (Integer year : entry.getValue()) {
                TextView cell = Ui.text(c, String.valueOf(year), 15f, Theme.ON, true);
                cell.setGravity(android.view.Gravity.CENTER);
                cell.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
                cell.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
                final int value = year;
                cell.setOnClickListener(v -> activity.openYear(value));
                Ui.tapScale(cell);
                cells.add(cell);
            }
            int columns = 4;
            LinearLayout rows = Ui.column(c);
            LinearLayout current = null;
            for (int i = 0; i < cells.size(); i++) {
                if (i % columns == 0) {
                    current = Ui.row(c);
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rp.topMargin = dp(6);
                    rows.addView(current, rp);
                }
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cp.leftMargin = dp(4);
                cp.rightMargin = dp(4);
                current.addView(cells.get(i), cp);
            }
            col.addView(rows);
        }
        bodyHolder.addView(col);
    }

    private void fillFresh(Context c) {
        LinearLayout col = Ui.column(c);
        col.setPadding(0, dp(4), 0, 0);
        Ui.Segmented period = new Ui.Segmented(c, new String[]{"Сегодня", "Неделя", "Всё время"},
                "today".equals(Settings.period) ? 0 : "week".equals(Settings.period) ? 1 : 2,
                index -> {
                    Settings.setPeriod(index == 0 ? "today" : index == 1 ? "week" : "all");
                    loadFresh();
                });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        pp.leftMargin = dp(16);
        pp.rightMargin = dp(16);
        pp.bottomMargin = dp(6);
        col.addView(period, pp);

        List<Models.Track> base = Settings.filterMature(freshTracks);
        col.addView(genreChips(base));
        col.addView(playBar(base, "Слушать", () -> loadFresh()));

        List<Models.Track> filtered = Genres.filter(base, genre);
        if (freshLoading && base.isEmpty()) {
            col.addView(Cards.trackRowSkeleton(c, 8));
        } else if (freshError != null && base.isEmpty()) {
            col.addView(Ui.errorState(c, freshError, this::loadFresh));
        } else if (filtered.isEmpty()) {
            col.addView(Ui.emptyState(c, "filter_alt", "Пусто с этим фильтром", "Попробуйте другой жанр или отключите фильтр.", null, null));
        } else {
            col.addView(trackList(filtered));
        }
        bodyHolder.addView(col);
    }

    private void fillType(Context c) {
        LinearLayout col = Ui.column(c);
        col.setPadding(0, dp(4), 0, 0);
        List<Models.Track> base = Settings.filterMature(typeTracks);
        col.addView(genreChips(base));
        col.addView(playBar(base, "Слушать всё", () -> loadType(true)));
        List<Models.Track> filtered = Genres.filter(base, genre);
        if (typeError != null && base.isEmpty()) {
            col.addView(Ui.errorState(c, typeError, () -> loadType(true)));
        } else if (filtered.isEmpty() && !typeLoading) {
            col.addView(Ui.emptyState(c, "filter_alt", "Пусто", "Попробуйте «Все» жанры или обновите.", null, null));
        } else {
            col.addView(trackList(filtered));
        }
        if (typeLoading) col.addView(Cards.trackRowSkeleton(c, base.isEmpty() ? 8 : 3));
        if (!typeLoading && !filtered.isEmpty()) {
            LinearLayout wrap = Ui.row(c);
            wrap.setGravity(android.view.Gravity.CENTER);
            wrap.setPadding(dp(16), dp(20), dp(16), 0);
            wrap.addView(Ui.tintedButton(c, "Показать ещё", () -> loadType(false)));
            col.addView(wrap);
        }
        bodyHolder.addView(col);
    }

    private View genreChips(List<Models.Track> tracks) {
        Context c = ctx();
        LinearLayout row = hrow();
        row.addView(Ui.chip(c, "Все", null, genre == null, () -> {
            genre = null;
            fillBody();
        }));
        for (Models.GenreDef def : Genres.ALL) {
            TextView chipView = Ui.chip(c, def.label, null, def.id.equals(genre), () -> {
                genre = def.id.equals(genre) ? null : def.id;
                fillBody();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.leftMargin = dp(8);
            row.addView(chipView, cp);
        }
        return hscroll(row);
    }

    private View playBar(List<Models.Track> tracks, String label, Runnable refresh) {
        Context c = ctx();
        LinearLayout row = Ui.row(c);
        row.setPadding(dp(16), dp(8), dp(16), dp(6));
        LinearLayout play = Ui.row(c);
        play.setGravity(android.view.Gravity.CENTER);
        play.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
        play.setPadding(0, dp(11), 0, dp(11));
        play.addView(Ui.icon(c, "play_arrow", 18, Theme.PRIMARY));
        TextView playLabel = Ui.text(c, label + (tracks.isEmpty() ? "" : " · " + tracks.size()), 15f, Theme.PRIMARY, true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = dp(6);
        play.addView(playLabel, plp);
        play.setOnClickListener(v -> {
            List<Models.Track> filtered = Genres.filter(Settings.filterMature("years".equals(type) || "fresh".equals(type) ? freshTracks : typeTracks), genre);
            if (filtered.isEmpty()) return;
            Player.playTracks(filtered, 0, false);
            activity.nowPlaying().open();
        });
        Ui.tapScale(play);
        row.addView(play, Ui.lpw(1f));
        FrameLayout refreshBtn = Ui.iconButton(c, "refresh", 20, Theme.ON, refresh::run);
        refreshBtn.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
        LinearLayout.LayoutParams rp = Ui.lp(dp(40), dp(40));
        rp.leftMargin = dp(8);
        refreshBtn.setLayoutParams(rp);
        row.addView(refreshBtn);
        return row;
    }

    private View trackList(List<Models.Track> items) {
        Context c = ctx();
        LinearLayout col = Ui.column(c);
        col.setPadding(dp(16), dp(2), 0, 0);
        for (Models.Track t : items) col.addView(Cards.trackRow(activity, t, items, true, false, false, null, null));
        return col;
    }

    @Override
    public String title() {
        return "Обзор";
    }
}
