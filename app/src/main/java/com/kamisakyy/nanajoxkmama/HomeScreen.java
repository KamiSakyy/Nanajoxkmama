package com.kamisakyy.nanajoxkmama;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home — port of pages/Home.tsx: greeting header, period switch, live rail,
 * "трек дня" hero, quick actions, mixes, season, legends, artists, fresh & latest.
 */
public final class HomeScreen implements Screen {
    private final Ui.Host host;
    private final Activity a;
    private final LinearLayout root;
    private final LinearLayout freshList;
    private final View freshHeader;
    private final TextView freshHeaderTitle;
    private final LinearLayout heroSlot;
    private final LinearLayout quickRow;
    private final LinearLayout randomRow;
    private final LinearLayout seasonRow;
    private final LinearLayout legendRow;
    private final LinearLayout historyRow;
    private final LinearLayout artistRow;
    private final LinearLayout latestList;
    private final TextView liveTitle;
    private final TextView liveSub;
    private final Ui.CoverView liveCover;
    private final View liveDot;
    private final LinearLayout freshFooter;
    private final TextView freshFooterBtn;
    private final LinearLayout offlineSection;
    private final LinearLayout historySection;
    private final LinearLayout seasonSection;

    private ArrayList<Track> randomTracks = new ArrayList<>();
    private ArrayList<Track> freshTracks = new ArrayList<>();
    private ArrayList<Track> latestTracks = new ArrayList<>();
    private ArrayList<Track> history = new ArrayList<>();
    private ArrayList<Track> offline = new ArrayList<>();
    private ArrayList<AnimeInfo> seasonAnime = new ArrayList<>();
    private String seasonTitle = "Сезон";
    private List<Track> livePool = new ArrayList<>();
    private Track liveTrack;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private int randomTick;
    private boolean started;

    public HomeScreen(Ui.Host host) {
        this.host = host;
        this.a = host.activity();
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, Ui.dp(24));
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // header
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Ui.BG);
        header.setPadding(0, Ui.dp(8), 0, Ui.dp(6));
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(16), 0, Ui.dp(8), 0);
        TextView greet = Ui.text(a, Util.greeting(), 17, Ui.ON, true);
        bar.addView(greet, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(Ui.iconBtn(a, "search", 21, Ui.ON, v -> host.switchTab(1)));
        bar.addView(Ui.iconBtn(a, "settings", 21, Ui.ON, v -> host.openSettings()));
        header.addView(bar);
        Ui.Segmented period = new Ui.Segmented(a, new String[]{"today", "week", "all"}, Store.getPeriod(),
                v -> {
                    Store.setPeriod(v);
                    loadFresh();
                });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34));
        pp.setMargins(Ui.dp(16), Ui.dp(6), Ui.dp(16), 0);
        header.addView(period, pp);

        // live rail
        FrameLayout liveCard = new FrameLayout(a);
        liveCard.setBackground(Ui.ripple(Ui.rounded(Ui.S3, 26)));
        LinearLayout liveRow = new LinearLayout(a);
        liveRow.setOrientation(LinearLayout.HORIZONTAL);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        liveRow.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        FrameLayout liveCoverWrap = new FrameLayout(a);
        liveCover = Ui.cover(a, 42, 21).circle();
        liveCoverWrap.addView(liveCover, new FrameLayout.LayoutParams(Ui.dp(42), Ui.dp(42)));
        liveDot = new View(a);
        liveDot.setBackground(Ui.oval(Ui.LIVE));
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(Ui.dp(9), Ui.dp(9));
        dotLp.gravity = Gravity.TOP | Gravity.END;
        liveCoverWrap.addView(liveDot, dotLp);
        liveRow.addView(liveCoverWrap);
        LinearLayout liveText = new LinearLayout(a);
        liveText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(10);
        liveTitle = Ui.text(a, "AniBeat Live", 13.5f, Ui.ON, true);
        liveSub = Ui.text(a, "эфир · случайные нарезки", 12f, Ui.VAR, false);
        liveText.addView(liveTitle);
        liveText.addView(liveSub);
        liveRow.addView(liveText, lp);
        liveRow.addView(Ui.icon(a, "play_arrow", 22, Ui.ON));
        liveCard.addView(liveRow);
        liveCard.setOnClickListener(v -> {
            if (liveTrack == null) return;
            List<Track> ctx = livePool.isEmpty() ? java.util.Collections.singletonList(liveTrack) : livePool;
            host.playAll(ctx, Math.max(0, ctx.indexOf(liveTrack)), false);
            host.openNowPlaying();
        });
        LinearLayout.LayoutParams lcp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(58));
        lcp.setMargins(Ui.dp(16), Ui.dp(8), Ui.dp(16), 0);
        header.addView(liveCard, lcp);
        body.addView(header);

        // hero slot
        heroSlot = new LinearLayout(a);
        heroSlot.setOrientation(LinearLayout.VERTICAL);
        heroSlot.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), 0);
        heroSlot.addView(Ui.skeleton(a, 320, 132, 18));
        body.addView(heroSlot);

        // quick actions
        HorizontalScrollView quickScroll = Ui.hscroll(a);
        quickRow = Ui.hscrollRow(quickScroll);
        body.addView(quickScroll);

        // fresh
        freshHeaderTitle = Ui.text(a, "Новое", 19, Ui.ON, true);
        LinearLayout fh = new LinearLayout(a);
        fh.setOrientation(LinearLayout.HORIZONTAL);
        fh.setGravity(Gravity.CENTER_VERTICAL);
        fh.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), Ui.dp(6));
        fh.addView(freshHeaderTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView refreshBtn = Ui.text(a, "Обновить", 14, Ui.ACCENT, true);
        refreshBtn.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        refreshBtn.setOnClickListener(v -> loadFresh());
        fh.addView(refreshBtn);
        freshHeader = fh;
        body.addView(fh);
        freshList = new LinearLayout(a);
        freshList.setOrientation(LinearLayout.VERTICAL);
        freshList.setPadding(0, Ui.dp(2), 0, 0);
        body.addView(freshList);
        freshFooter = new LinearLayout(a);
        freshFooter.setOrientation(LinearLayout.VERTICAL);
        freshFooter.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), 0);
        freshFooterBtn = Ui.text(a, "", 14, Ui.ACCENT, true);
        TextView wrapped = freshFooterBtn;
        wrapped.setBackground(Ui.ripple(Ui.rounded(0x1AA0A8FF, 12)));
        wrapped.setGravity(Gravity.CENTER);
        wrapped.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
        wrapped.setOnClickListener(v -> host.switchTab(2));
        freshFooter.addView(wrapped, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(42)));
        body.addView(freshFooter);

        // mixes
        body.addView(Ui.sectionHeader(a, "Миксы", null, null));
        HorizontalScrollView mixScroll = Ui.hscroll(a);
        LinearLayout mixRow = Ui.hscrollRow(mixScroll);
        for (Util.Mix mix : Util.MIXES) {
            mixRow.addView(Ui.mixCard(a, host, mix, () -> playMix(mix)));
        }
        body.addView(mixScroll);

        // random
        LinearLayout rh = new LinearLayout(a);
        rh.setOrientation(LinearLayout.HORIZONTAL);
        rh.setGravity(Gravity.CENTER_VERTICAL);
        rh.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), Ui.dp(6));
        rh.addView(Ui.text(a, "Случайные", 19, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView rBtn = Ui.text(a, "Обновить", 14, Ui.ACCENT, true);
        rBtn.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        rBtn.setOnClickListener(v -> loadRandom());
        rh.addView(rBtn);
        body.addView(rh);
        HorizontalScrollView randomScroll = Ui.hscroll(a);
        randomRow = Ui.hscrollRow(randomScroll);
        body.addView(randomScroll);

        // offline
        offlineSection = new LinearLayout(a);
        offlineSection.setOrientation(LinearLayout.VERTICAL);
        body.addView(offlineSection);

        // season
        seasonSection = new LinearLayout(a);
        seasonSection.setOrientation(LinearLayout.VERTICAL);
        body.addView(seasonSection);

        // legends
        body.addView(Ui.sectionHeader(a, "Легенды", null, null));
        HorizontalScrollView legendScroll = Ui.hscroll(a);
        legendRow = Ui.hscrollRow(legendScroll);
        body.addView(legendScroll);

        // history
        historySection = new LinearLayout(a);
        historySection.setOrientation(LinearLayout.VERTICAL);
        body.addView(historySection);

        // artists
        body.addView(Ui.sectionHeader(a, "Исполнители", null, null));
        HorizontalScrollView artistScroll = Ui.hscroll(a);
        artistRow = Ui.hscrollRow(artistScroll);
        body.addView(artistScroll);

        // latest
        LinearLayout lh = new LinearLayout(a);
        lh.setOrientation(LinearLayout.HORIZONTAL);
        lh.setGravity(Gravity.CENTER_VERTICAL);
        lh.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), Ui.dp(6));
        lh.addView(Ui.text(a, "Недавно добавленные", 19, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView lBtn = Ui.text(a, "Слушать", 14, Ui.ACCENT, true);
        lBtn.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        lBtn.setOnClickListener(v -> {
            if (!latestTracks.isEmpty()) host.playAll(latestTracks, 0, false);
        });
        lh.addView(lBtn);
        body.addView(lh);
        latestList = new LinearLayout(a);
        latestList.setOrientation(LinearLayout.VERTICAL);
        body.addView(latestList);

        start();
    }

    private void start() {
        if (started) return;
        started = true;
        buildQuickActions();
        loadRandom();
        loadFresh();
        loadLatest();
        loadSeason();
        loadLegends();
        loadArtists();
        liveHandler.postDelayed(liveTick, 50_000);
    }

    private final Runnable liveTick = new Runnable() {
        @Override public void run() {
            if (livePool.size() >= 2) {
                liveTrack = livePool.get((int) (Math.random() * livePool.size()));
                bindLive();
            }
            liveHandler.postDelayed(this, 50_000);
        }
    };

    private void bindLive() {
        if (liveTrack == null) return;
        liveTitle.setText(liveTrack.title);
        liveSub.setText(liveTrack.themeTag() + " · " + liveTrack.animeName);
        liveCover.load(liveTrack.coverSmall.isEmpty() ? liveTrack.cover : liveTrack.coverSmall, true);
    }

    private void buildQuickActions() {
        quickRow.removeAllViews();
        quickRow.addView(quickChip("radio", "Радио", v -> {
            host.runIo(() -> {
                try {
                    ArrayList<Track> tracks = ApiClient.getRandomTracks(40, null);
                    a.runOnUiThread(() -> {
                        if (tracks.isEmpty()) {
                            host.toast("Микс пуст — попробуйте ещё раз");
                            return;
                        }
                        host.playAll(Util.filterMature(tracks), 0, true);
                        host.openNowPlaying();
                    });
                } catch (Exception e) {
                    a.runOnUiThread(() -> host.toast("Не удалось загрузить"));
                }
            });
        }));
        quickRow.addView(quickChip("whatshot", "Опенинги", v -> BrowseScreen.openType(host, "OP")));
        quickRow.addView(quickChip("schedule", "Эндинги", v -> BrowseScreen.openType(host, "ED")));
        quickRow.addView(quickChip("offline_pin", "Офлайн", v -> host.switchTab(3)));
        quickRow.addView(quickChip("filter_alt", Store.isMatureEnabled() ? "18+ вкл" : "18+ выкл", v -> {
            Store.setMatureEnabled(!Store.isMatureEnabled());
            host.toast(Store.isMatureEnabled() ? "18+ включён" : "18+ выключен");
            buildQuickActions();
            loadFresh();
        }));
    }

    private View quickChip(String icon, String label, View.OnClickListener click) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(Ui.dp(14), 0, Ui.dp(16), 0);
        l.setBackground(Ui.ripple(Ui.rounded(Ui.S2, 20)));
        l.setMinimumHeight(Ui.dp(36));
        l.addView(Ui.icon(a, icon, 15, Ui.VAR));
        TextView t = Ui.text(a, label, 13f, Ui.ON, false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(6);
        l.addView(t, tp);
        l.setOnClickListener(click);
        Ui.tapScale(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(36));
        lp.setMargins(Ui.dp(3), Ui.dp(4), Ui.dp(3), Ui.dp(4));
        l.setLayoutParams(lp);
        return l;
    }

    /* ---------------- loaders ---------------- */

    private void loadRandom() {
        randomRow.removeAllViews();
        for (int i = 0; i < 5; i++) randomRow.addView(Ui.skeleton(a, 138, 138, 14));
        host.runIo(() -> {
            try {
                ArrayList<Track> tracks = ApiClient.getRandomTracks(16, null);
                randomTick++;
                Store.saveLastRandom(tracks);
                MetaApi.warmTracks(tracks);
                a.runOnUiThread(() -> {
                    randomTracks = Util.filterMature(tracks);
                    bindRandom();
                    livePool = new ArrayList<>(randomTracks.subList(0, Math.min(8, randomTracks.size())));
                    if (liveTrack == null && !livePool.isEmpty()) {
                        liveTrack = livePool.get(0);
                        bindLive();
                    }
                    bindHero();
                    offline = Store.getOfflineTracks();
                    bindOffline();
                    history = Store.getHistory();
                    bindHistory();
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    randomRow.removeAllViews();
                    if (randomTracks.isEmpty()) {
                        randomRow.addView(Ui.errorState(a, e.getMessage(), v -> loadRandom()));
                    }
                });
            }
        });
    }

    private void bindHero() {
        heroSlot.removeAllViews();
        if (randomTracks.isEmpty()) return;
        Track hero = randomTracks.get(0);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.rounded(Ui.S2, 18));
        card.setPadding(Ui.dp(12), Ui.dp(12), Ui.dp(12), Ui.dp(12));
        Ui.CoverView cover = Ui.cover(a, 104, 12);
        cover.load(coverUrl(hero));
        LinearLayout.LayoutParams cp = (LinearLayout.LayoutParams) cover.getLayoutParams();
        cover.setOnClickListener(v -> openAnimeSafe(hero));
        card.addView(cover);
        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.leftMargin = Ui.dp(14);
        TextView kicker = Ui.text(a, "ТРЕК ДНЯ", 10.5f, Ui.VAR, true);
        kicker.setLetterSpacing(0.1f);
        mid.addView(kicker);
        TextView title = Ui.text(a, hero.title, 18.5f, Ui.ON, true);
        title.setMaxLines(2);
        title.setSingleLine(false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Ui.dp(3);
        mid.addView(title, tp);
        TextView artist = Ui.text(a, hero.displayArtist(), 13f, Ui.VAR, false);
        mid.addView(artist);
        TextView anime = Ui.text(a, hero.animeName, 12f, Ui.DIM, false);
        mid.addView(anime);
        LinearLayout actions = new LinearLayout(a);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = Ui.dp(10);
        actions.addView(Ui.button(a, "Слушать", "play_arrow", true, v -> {
            host.playAll(randomTracks, 0, false);
            host.openNowPlaying();
        }));
        View shuffle = Ui.iconBtn(a, "shuffle", 19, Ui.ON, v -> {
            host.playAll(randomTracks, 0, true);
            host.openNowPlaying();
        });
        LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) shuffle.getLayoutParams();
        sp.leftMargin = Ui.dp(4);
        shuffle.setBackground(Ui.ripple(Ui.rounded(Ui.S4, 20)));
        actions.addView(shuffle, sp);
        mid.addView(actions, ap);
        card.addView(mid, mp);
        heroSlot.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String coverUrl(Track t) {
        return Store.isDataSaverEnabled() && !t.coverSmall.isEmpty() ? t.coverSmall : t.cover;
    }

    private void openAnimeSafe(Track t) {
        if (t.animeSlug != null && !t.animeSlug.isEmpty() && !t.animeSlug.startsWith("mal-") && !t.animeSlug.startsWith("ann-")) {
            host.openAnime(t.animeSlug, t.animeName);
        }
    }

    private void bindRandom() {
        randomRow.removeAllViews();
        for (int i = 1; i < randomTracks.size(); i++) {
            randomRow.addView(Ui.trackCard(a, host, randomTracks.get(i), randomTracks));
        }
    }

    private void loadFresh() {
        String period = Store.getPeriod();
        freshHeaderTitle.setText("today".equals(period) ? "Сегодня" : "week".equals(period) ? "Эта неделя" : "Новое");
        freshList.removeAllViews();
        for (int i = 0; i < 5; i++) freshList.addView(rowSkeleton());
        host.runIo(() -> {
            try {
                ArrayList<Track> tracks = ApiClient.getFreshTracks(period, 24);
                Store.saveFreshCache(period, tracks);
                a.runOnUiThread(() -> {
                    freshTracks = Util.filterMature(tracks);
                    bindFresh();
                });
            } catch (Exception e) {
                ArrayList<Track> cached = Store.getFreshCache(period);
                a.runOnUiThread(() -> {
                    freshTracks = Util.filterMature(cached);
                    if (freshTracks.isEmpty()) {
                        freshList.removeAllViews();
                        freshList.addView(Ui.errorState(a, e.getMessage(), v -> loadFresh()));
                    } else bindFresh();
                });
            }
        });
    }

    private void bindFresh() {
        freshList.removeAllViews();
        if (freshTracks.isEmpty()) {
            freshList.addView(Ui.emptyState(a, "calendar", "Здесь пока пусто", "Смените период на «Всё время».", null, null));
            freshFooter.setVisibility(View.GONE);
            return;
        }
        int show = Math.min(6, freshTracks.size());
        for (int i = 0; i < show; i++) freshList.addView(trackRow(freshTracks.get(i), freshTracks, true));
        if (freshTracks.size() > show) {
            freshFooter.setVisibility(View.VISIBLE);
            freshFooterBtn.setText("Все новинки · " + freshTracks.size());
        } else {
            freshFooter.setVisibility(View.GONE);
        }
    }

    private void loadLatest() {
        latestList.removeAllViews();
        for (int i = 0; i < 4; i++) latestList.addView(rowSkeleton());
        host.runIo(() -> {
            try {
                ArrayList<Track> tracks = ApiClient.getLatestTracks(12);
                a.runOnUiThread(() -> {
                    latestTracks = tracks;
                    latestList.removeAllViews();
                    for (Track t : latestTracks) latestList.addView(trackRow(t, latestTracks, true));
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    latestList.removeAllViews();
                    latestList.addView(Ui.errorState(a, e.getMessage(), v -> loadLatest()));
                });
            }
        });
    }

    private void loadSeason() {
        seasonSection.removeAllViews();
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), Ui.dp(6));
        TextView title = Ui.text(a, "Сезон", 19, Ui.ON, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = Ui.text(a, "Все", 14, Ui.ACCENT, true);
        all.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        header.addView(all);
        seasonSection.addView(header);
        HorizontalScrollView scroll = Ui.hscroll(a);
        LinearLayout row = Ui.hscrollRow(scroll);
        seasonSection.addView(scroll);
        for (int i = 0; i < 4; i++) row.addView(Ui.skeleton(a, 116, 164, 12));
        int startYear = Util.currentYear();
        String startSeason = Util.currentSeason();
        host.runIo(() -> {
            try {
                ApiClient.PagedAnime p = ApiClient.seasonAnime(startYear, startSeason, 1);
                ArrayList<AnimeInfo> items = p.items;
                String label = Util.seasonRu(startSeason) + " " + startYear;
                int fYear = startYear;
                String fSeason = startSeason;
                if (items.size() < 6) {
                    int[] prev = Util.prevSeason(startYear, startSeason);
                    String prevSeason = Util.SEASONS[prev[1]];
                    ApiClient.PagedAnime p2 = ApiClient.seasonAnime(prev[0], prevSeason, 1);
                    if (p2.items.size() > items.size()) {
                        items = p2.items;
                        fYear = prev[0];
                        fSeason = prevSeason;
                        label = Util.seasonRu(fSeason) + " " + fYear;
                    }
                }
                final ArrayList<AnimeInfo> fItems = items;
                final String fLabel = label;
                final int ffYear = fYear;
                final String ffSeason = fSeason;
                a.runOnUiThread(() -> {
                    seasonAnime = fItems;
                    seasonTitle = fLabel;
                    title.setText(fLabel);
                    all.setOnClickListener(v -> host.openYear(ffYear, ffSeason));
                    row.removeAllViews();
                    for (AnimeInfo info : fItems) row.addView(Ui.animeCard(a, host, info));
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    row.removeAllViews();
                    row.addView(Ui.errorState(a, e.getMessage(), v -> loadSeason()));
                });
            }
        });
    }

    private void loadLegends() {
        legendRow.removeAllViews();
        for (int i = 0; i < 4; i++) legendRow.addView(Ui.skeleton(a, 116, 164, 12));
        host.runIo(() -> {
            try {
                List<String> slugs = new ArrayList<>(java.util.Arrays.asList(Util.LEGEND_SLUGS));
                ArrayList<AnimeInfo> all = ApiClient.animeBySlugs(slugs);
                List<AnimeInfo> pick = new ArrayList<>(Util.shuffleArray(all));
                if (pick.size() > 18) pick = pick.subList(0, 18);
                final List<AnimeInfo> fPick = pick;
                a.runOnUiThread(() -> {
                    legendRow.removeAllViews();
                    for (AnimeInfo info : fPick) legendRow.addView(Ui.animeCard(a, host, info));
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    legendRow.removeAllViews();
                    legendRow.addView(Ui.errorState(a, e.getMessage(), v -> loadLegends()));
                });
            }
        });
    }

    private void loadArtists() {
        artistRow.removeAllViews();
        for (int i = 0; i < 5; i++) {
            Ui.CoverView c = Ui.cover(a, 84, 42).circle();
            LinearLayout wrap = new LinearLayout(a);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.addView(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(92), Ui.dp(110));
            lp.setMargins(Ui.dp(3), Ui.dp(2), Ui.dp(3), Ui.dp(2));
            artistRow.addView(wrap, lp);
        }
        host.runIo(() -> {
            try {
                ArrayList<ArtistInfo> all = ApiClient.artistsBySlugs(java.util.Arrays.asList(Util.ARTIST_SLUGS));
                List<ArtistInfo> pick = new ArrayList<>(Util.shuffleArray(all));
                if (pick.size() > 16) pick = pick.subList(0, 16);
                final List<ArtistInfo> fPick = pick;
                a.runOnUiThread(() -> {
                    artistRow.removeAllViews();
                    for (ArtistInfo info : fPick) artistRow.addView(Ui.artistCard(a, host, info));
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> artistRow.removeAllViews());
            }
        });
    }

    private void bindOffline() {
        offlineSection.removeAllViews();
        if (offline.isEmpty()) return;
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), Ui.dp(6));
        header.addView(Ui.text(a, "Офлайн", 19, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = Ui.text(a, "Все", 14, Ui.ACCENT, true);
        all.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        all.setOnClickListener(v -> host.switchTab(3));
        header.addView(all);
        offlineSection.addView(header);
        HorizontalScrollView scroll = Ui.hscroll(a);
        LinearLayout row = Ui.hscrollRow(scroll);
        for (Track t : offline) row.addView(Ui.trackCard(a, host, t, offline));
        offlineSection.addView(scroll);
    }

    private void bindHistory() {
        historySection.removeAllViews();
        if (history.isEmpty()) return;
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), Ui.dp(6));
        header.addView(Ui.text(a, "Недавние", 19, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = Ui.text(a, "Все", 14, Ui.ACCENT, true);
        all.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        all.setOnClickListener(v -> host.switchTab(3));
        header.addView(all);
        historySection.addView(header);
        HorizontalScrollView scroll = Ui.hscroll(a);
        LinearLayout row = Ui.hscrollRow(scroll);
        for (int i = 0; i < Math.min(12, history.size()); i++) {
            row.addView(Ui.trackCard(a, host, history.get(i), history));
        }
        historySection.addView(scroll);
    }

    private void playMix(Util.Mix mix) {
        host.toast("Загружаем микс…");
        host.runIo(() -> {
            try {
                ArrayList<Track> tracks = ApiClient.tracksForAnimeSlugs(java.util.Arrays.asList(mix.slugs));
                a.runOnUiThread(() -> {
                    ArrayList<Track> filtered = Util.filterMature(tracks);
                    if (filtered.isEmpty()) {
                        host.toast("Микс пуст — попробуйте ещё раз");
                        return;
                    }
                    host.playAll(filtered, 0, true);
                    host.openNowPlaying();
                });
            } catch (Exception e) {
                a.runOnUiThread(() -> host.toast("Не удалось загрузить"));
            }
        });
    }

    /* ---------------- helpers ---------------- */

    private Ui.TrackRow trackRow(Track t, List<Track> ctx, boolean showAnime) {
        return new Ui.TrackRow(a, host, t, ctx, showAnime, false, null);
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
        row.addView(Ui.skeleton(a, 30, 14, 5));
        return row;
    }

    @Override public View view() { return root; }

    @Override public void onShow() {
        offline = Store.getOfflineTracks();
        history = Store.getHistory();
        bindOffline();
        bindHistory();
    }

    @Override public void onPlayerChanged() {
        // rows self-refresh via Ui.refreshPlayingRows
    }
}
