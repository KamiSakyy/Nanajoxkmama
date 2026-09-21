package com.kamisakyy.nanajoxkmama;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Browse — port of pages/Browse.tsx: years by decades, "Новое" feed with genre
 * filters, OP/ED feeds with pagination; plus the Year page with seasons.
 */
public final class BrowseScreen implements Screen {
    public static final int CURRENT_YEAR = Util.currentYear();

    private final Ui.Host host;
    private final Activity a;
    private final LinearLayout root;
    private final LinearLayout content;
    private String mode = "years"; // years | fresh | OP | ED
    private String genre = "";
    private final List<Track> typeFeed = new ArrayList<>();
    private boolean typeLoading;

    public BrowseScreen(Ui.Host host) {
        this.host = host;
        this.a = host.activity();
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        TextView large = Ui.text(a, "Обзор", 28, Ui.ON, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(8));
        root.addView(large, lp);

        Ui.Segmented seg = new Ui.Segmented(a, new String[]{"years", "fresh", "OP", "ED"},
                new String[]{"Годы", "Новое", "OP", "ED"}, mode, v -> {
            mode = v;
            genre = "";
            typeFeed.clear();
            render();
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34));
        sp.setMargins(Ui.dp(16), Ui.dp(2), Ui.dp(16), Ui.dp(6));
        root.addView(seg, sp);

        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(24));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        render();
    }

    /** Deep-link from home quick actions. */
    public static void openType(Ui.Host host, String type) {
        host.switchTab(2);
    }

    public void setType(String t) {
        mode = t;
        render();
    }

    private void render() {
        content.removeAllViews();
        if ("years".equals(mode)) renderYears();
        else if ("fresh".equals(mode)) renderFresh();
        else renderTypeFeed();
    }

    /* ---------------- years ---------------- */

    private void renderYears() {
        Map<String, List<Integer>> decades = new LinkedHashMap<>();
        for (int y = CURRENT_YEAR + 1; y >= 1963; y--) {
            String decade = String.valueOf((y / 10) * 10);
            List<Integer> list = decades.get(decade);
            if (list == null) {
                list = new ArrayList<>();
                decades.put(decade, list);
            }
            list.add(y);
        }
        for (Map.Entry<String, List<Integer>> e : decades.entrySet()) {
            TextView label = Ui.text(a, e.getKey() + "-е", 15, Ui.VAR, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(18), Ui.dp(14), Ui.dp(16), Ui.dp(6));
            content.addView(label, lp);
            LinearLayout grid = new LinearLayout(a);
            grid.setOrientation(LinearLayout.HORIZONTAL);
            grid.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
            int col = 0;
            LinearLayout row = newRow();
            for (int year : e.getValue()) {
                if (col == 4) {
                    grid.addView(row);
                    row = newRow();
                    col = 0;
                }
                TextView cell = Ui.text(a, String.valueOf(year), 15, Ui.ON, true);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(Ui.ripple(Ui.rounded(Ui.S2, 11)));
                cell.setOnClickListener(v -> host.openYear(year, null));
                Ui.tapScale(cell);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, Ui.dp(52), 1f);
                cp.setMargins(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
                row.addView(cell, cp);
                col++;
            }
            grid.addView(row);
            content.addView(grid);
        }
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /* ---------------- fresh feed ---------------- */

    private void renderFresh() {
        Ui.Segmented period = new Ui.Segmented(a, new String[]{"today", "week", "all"}, Store.getPeriod(), v -> {
            Store.setPeriod(v);
            renderFreshReload();
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34));
        pp.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), 0);
        content.addView(period, pp);
        content.addView(genreRow(this::renderFreshReload));
        content.addView(playBar(this::renderFreshReload));
        renderFreshReload();
    }

    private void renderFreshReload() {
        // remove old list (last children after fixed rows: genre row + play bar + list)
        while (content.getChildCount() > 3) content.removeViewAt(content.getChildCount() - 1);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < 6; i++) list.addView(rowSkeleton());
        content.addView(list);
        host.runIo(() -> {
            try {
                ArrayList<Track> tracks = ApiClient.getFreshTracks(Store.getPeriod(), 40);
                a.runOnUiThread(() -> {
                    ArrayList<Track> filtered = Util.filterGenre(Util.filterMature(tracks), genre);
                    list.removeAllViews();
                    if (filtered.isEmpty()) {
                        list.addView(Ui.emptyState(a, "filter_alt", "Пусто с этим фильтром",
                                "Попробуйте другой жанр или отключите фильтр.", null, null));
                        return;
                    }
                    for (Track t : filtered) list.addView(new Ui.TrackRow(a, host, t, filtered, true, false, null));
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    list.removeAllViews();
                    list.addView(Ui.errorState(a, e.getMessage(), v -> renderFreshReload()));
                });
            }
        });
    }

    /* ---------------- OP / ED feed ---------------- */

    private void renderTypeFeed() {
        content.addView(genreRow(() -> {
            // just re-filter
            while (content.getChildCount() > 2) content.removeViewAt(content.getChildCount() - 1);
            final LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            content.addView(list);
            bindTypeList(list);
        }));
        content.addView(playBar(() -> loadType(true)));
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        if (typeFeed.isEmpty()) loadType(true);
        else bindTypeList(list);
    }

    private void bindTypeList(LinearLayout list) {
        list.removeAllViews();
        ArrayList<Track> filtered = Util.filterGenre(Util.filterMature(typeFeed), genre);
        if (filtered.isEmpty() && !typeLoading) {
            list.addView(Ui.emptyState(a, "filter_alt", "Пусто", "Попробуйте «Все» жанры или обновите.", null, null));
            return;
        }
        for (Track t : filtered) list.addView(new Ui.TrackRow(a, host, t, filtered, true, false, null));
        if (!typeLoading && !filtered.isEmpty()) {
            LinearLayout more = new LinearLayout(a);
            more.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), 0);
            View btn = Ui.button(a, "Показать ещё", null, false, v -> loadType(false));
            LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) btn.getLayoutParams();
            bp.gravity = Gravity.CENTER_HORIZONTAL;
            more.addView(btn, bp);
            list.addView(more);
        }
    }

    private void loadType(boolean reset) {
        typeLoading = true;
        final LinearLayout list = findList();
        if (list != null && !reset) {
            list.addView(rowSkeleton());
        }
        host.runIo(() -> {
            try {
                ArrayList<Track> more = ApiClient.getRandomTracks(24, mode);
                a.runOnUiThread(() -> {
                    typeLoading = false;
                    if (reset) typeFeed.clear();
                    for (Track t : more) {
                        boolean dup = false;
                        for (Track x : typeFeed) if (x.id.equals(t.id)) { dup = true; break; }
                        if (!dup) typeFeed.add(t);
                    }
                    LinearLayout l = findList();
                    if (l != null) bindTypeList(l);
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    typeLoading = false;
                    LinearLayout l = findList();
                    if (l != null) {
                        l.removeAllViews();
                        l.addView(Ui.errorState(a, e.getMessage(), v -> loadType(true)));
                    }
                });
            }
        });
    }

    private LinearLayout findList() {
        View last = content.getChildCount() > 0 ? content.getChildAt(content.getChildCount() - 1) : null;
        return last instanceof LinearLayout ? (LinearLayout) last : null;
    }

    /* ---------------- shared rows ---------------- */

    private View genreRow(Runnable onChange) {
        HorizontalScrollView hs = Ui.hscroll(a);
        LinearLayout row = Ui.hscrollRow(hs);
        row.addView(Ui.chip(a, "Все", genre.isEmpty(), v -> {
            genre = "";
            onChange.run();
        }));
        for (Util.Genre g : Util.GENRES) {
            row.addView(Ui.chip(a, g.label, genre.equals(g.id), v -> {
                genre = genre.equals(g.id) ? "" : g.id;
                onChange.run();
            }));
        }
        return hs;
    }

    private View playBar(Runnable reload) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(12), Ui.dp(4));
        View play = Ui.surfaceButton(a, "Слушать всё", "play_arrow", v -> {
            ArrayList<Track> filtered = Util.filterGenre(Util.filterMature(typeFeed.isEmpty() ? new ArrayList<Track>() : typeFeed), genre);
            if ("fresh".equals(mode)) {
                host.runIo(() -> {
                    try {
                        ArrayList<Track> tracks = ApiClient.getFreshTracks(Store.getPeriod(), 40);
                        final ArrayList<Track> f = Util.filterGenre(Util.filterMature(tracks), genre);
                        a.runOnUiThread(() -> {
                            if (f.isEmpty()) host.toast("Пусто");
                            else host.playAll(f, 0, false);
                        });
                    } catch (Exception e) {
                        a.runOnUiThread(() -> host.toast("Не удалось загрузить"));
                    }
                });
            } else {
                if (filtered.isEmpty()) host.toast("Пусто");
                else host.playAll(filtered, 0, false);
            }
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, Ui.dp(40), 1f);
        bar.addView(play, pp);
        View refresh = Ui.iconBtn(a, "refresh", 19, Ui.ON, v -> reload.run());
        refresh.setBackground(Ui.ripple(Ui.rounded(Ui.S2, 12)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
        rp.leftMargin = Ui.dp(8);
        bar.addView(refresh, rp);
        return bar;
    }

    private View rowSkeleton() {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        row.addView(Ui.skeleton(a, 46, 46, 9));
        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.leftMargin = Ui.dp(12);
        mid.addView(Ui.skeleton(a, 140, 12, 4));
        Ui.ShimmerView sub = Ui.skeleton(a, 90, 10, 4);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(90), Ui.dp(10));
        sp.topMargin = Ui.dp(6);
        sub.setLayoutParams(sp);
        mid.addView(sub);
        row.addView(mid, mp);
        return row;
    }

    @Override public View view() { return root; }
    @Override public void onShow() { }
    @Override public void onPlayerChanged() { }

    /* ================= Year page (pushed as detail) ================= */

    public static class YearScreen implements Screen {
        private final Ui.Host host;
        private final Activity a;
        private final LinearLayout root;
        private final LinearLayout content;
        private final int year;
        private String season;
        private final ArrayList<AnimeInfo> items = new ArrayList<>();
        private int page = 1;
        private boolean hasMore;
        private boolean loading;

        public YearScreen(Ui.Host host, int year, String season) {
            this.host = host;
            this.a = host.activity();
            this.year = year;
            this.season = season;
            root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Ui.BG);
            TextView large = Ui.text(a, String.valueOf(year), 28, Ui.ON, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(8));
            root.addView(large, lp);
            content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            ScrollView scroll = new ScrollView(a);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            load(true);
        }

        private void load(boolean reset) {
            if (loading) return;
            loading = true;
            if (reset) {
                content.removeAllViews();
                content.addView(seasonRow());
                content.addView(playBar());
                for (int i = 0; i < 3; i++) content.addView(gridSkeleton());
            }
            host.runIo(() -> {
                try {
                    ApiClient.PagedAnime p = ApiClient.seasonAnime(year, season, reset ? 1 : page + 1);
                    a.runOnUiThread(() -> {
                        loading = false;
                        if (reset) items.clear();
                        for (AnimeInfo info : p.items) {
                            boolean dup = false;
                            for (AnimeInfo x : items) if (x.slug.equals(info.slug)) { dup = true; break; }
                            if (!dup) items.add(info);
                        }
                        hasMore = p.hasMore;
                        page = reset ? 1 : page + 1;
                        render(reset);
                    });
                } catch (Exception e) {
                    a.runOnUiThread(() -> {
                        loading = false;
                        render(reset);
                    });
                }
            });
        }

        private void render(boolean reset) {
            if (reset) {
                content.removeAllViews();
                content.addView(seasonRow());
                content.addView(playBar());
            } else {
                while (content.getChildCount() > 2) content.removeViewAt(content.getChildCount() - 1);
            }
            LinearLayout grid = new LinearLayout(a);
            grid.setOrientation(LinearLayout.VERTICAL);
            grid.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), 0);
            int col = 0;
            LinearLayout row = newRow();
            for (AnimeInfo info : items) {
                if (col == 3) {
                    grid.addView(row);
                    row = newRow();
                    col = 0;
                }
                row.addView(Ui.animeCard(a, host, info));
                col++;
            }
            grid.addView(row);
            content.addView(grid);
            if (hasMore) {
                LinearLayout more = new LinearLayout(a);
                more.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), 0);
                View btn = Ui.button(a, "Показать ещё", null, false, v -> load(false));
                LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) btn.getLayoutParams();
                bp.gravity = Gravity.CENTER_HORIZONTAL;
                more.addView(btn, bp);
                content.addView(more);
            }
        }

        private LinearLayout newRow() {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }

        private View seasonRow() {
            HorizontalScrollView hs = Ui.hscroll(a);
            LinearLayout row = Ui.hscrollRow(hs);
            row.addView(Ui.chip(a, "Все", season == null, v -> {
                season = null;
                load(true);
            }));
            String[] keys = {"Winter", "Spring", "Summer", "Fall"};
            String[] labels = {"Зима", "Весна", "Лето", "Осень"};
            for (int i = 0; i < 4; i++) {
                final String key = keys[i];
                row.addView(Ui.chip(a, labels[i], key.equals(season), v -> {
                    season = key.equals(season) ? null : key;
                    load(true);
                }));
            }
            return hs;
        }

        private View playBar() {
            LinearLayout bar = new LinearLayout(a);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(4));
            View play = Ui.surfaceButton(a, "Слушать весь сезон", "play_arrow", v -> {
                String s = season == null ? "Winter" : season;
                host.runIo(() -> {
                    try {
                        ArrayList<Track> tracks = ApiClient.seasonTracks(year, s);
                        final ArrayList<Track> f = Util.filterMature(tracks);
                        a.runOnUiThread(() -> {
                            if (f.isEmpty()) host.toast("Треков не найдено");
                            else {
                                host.playAll(f, 0, false);
                                host.openNowPlaying();
                            }
                        });
                    } catch (Exception e) {
                        a.runOnUiThread(() -> host.toast("Не удалось загрузить"));
                    }
                });
            });
            bar.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(40), 1f));
            return bar;
        }

        private View gridSkeleton() {
            LinearLayout row = newRow();
            row.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));
            for (int i = 0; i < 3; i++) {
                LinearLayout cell = new LinearLayout(a);
                cell.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(110), Ui.dp(170));
                cp.setMargins(Ui.dp(4), 0, Ui.dp(4), 0);
                cell.addView(Ui.skeleton(a, 110, 150, 12));
                row.addView(cell, cp);
            }
            return row;
        }

        @Override public View view() { return root; }
        @Override public void onShow() { }
        @Override public void onPlayerChanged() { }
    }
}
