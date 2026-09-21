package com.anibeat.app.ui.screens;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.Display;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.ScreenBase;

import java.util.ArrayList;
import java.util.List;

/** Главная — порт pages/Home.tsx. */
public class HomeScreen extends ScreenBase {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> periodValues = new ArrayList<>();
    private List<Models.Track> randomTracks = new ArrayList<>();
    private List<Models.Track> freshTracks = new ArrayList<>();
    private List<Models.Track> latestTracks = new ArrayList<>();
    private List<Models.AnimeSummary> legendPick = new ArrayList<>();
    private List<Models.ArtistSummary> artistPick = new ArrayList<>();
    private List<Models.AnimeSummary> seasonItems = new ArrayList<>();
    private String seasonLabel = "Сезон";
    private boolean randomLoading = true;
    private boolean freshLoading = true;
    private boolean seasonLoading = true;
    private String randomError;
    private String freshError;
    private Models.Track liveTrack;
    private String busy;
    private int randomTick;

    private final Runnable liveTick = new Runnable() {
        @Override
        public void run() {
            List<Models.Track> pool = livePool();
            if (pool.size() >= 3) {
                liveTrack = pool.get((int) (Math.random() * pool.size()));
                rebuild();
            }
            handler.postDelayed(this, 50_000);
        }
    };

    public HomeScreen(MainActivity activity) {
        super(activity);
        load();
    }

    /* ------------------------------------------------------------------ */
    /* Данные                                                              */
    /* ------------------------------------------------------------------ */

    private void load() {
        int size = Math.max(1, livePoolSize());
        randomLoading = true;
        Api.getRandomTracks(16, null, randomTick > 0, (tracks, error) -> {
            randomLoading = false;
            if (tracks != null) {
                randomTracks = tracks;
                if (liveTrack == null && !tracks.isEmpty()) liveTrack = tracks.get(0);
                Meta.warm(metaIds(tracks));
            } else {
                randomError = error;
            }
            rebuild();
        });
        loadFresh();
        Api.getAnimeBySlugs(Api.LEGEND_SLUGS, (list, error) -> {
            if (list != null) {
                legendPick = Format.shuffled(list);
                if (legendPick.size() > 18) legendPick = legendPick.subList(0, 18);
            }
            rebuild();
        });
        Api.getArtistsBySlugs(Api.ARTIST_SLUGS, (list, error) -> {
            if (list != null) {
                artistPick = Format.shuffled(list);
                if (artistPick.size() > 16) artistPick = artistPick.subList(0, 16);
            }
            rebuild();
        });
        Api.getLatestTracks(12, (list, error) -> {
            if (list != null) latestTracks = list;
            rebuild();
        });
        loadSeason(Api.currentSeason());
        if (randomTracks.isEmpty()) handler.postDelayed(liveTick, 45_000);
    }

    private void loadFresh() {
        freshLoading = true;
        Api.getFreshTracks(Settings.period, 24, (list, error) -> {
            freshLoading = false;
            if (list != null) {
                freshTracks = list;
                Meta.warm(metaIds(list));
                warmMeta();
            } else {
                freshError = error;
            }
            rebuild();
        });
    }

    private void loadSeason(int[] cur) {
        seasonLoading = true;
        Api.getSeasonAnime(cur[0], Api.SEASONS[cur[1]], 1, (items, error) -> {
            if (items != null && items.size() >= 6) {
                seasonItems = items;
                seasonLabel = Api.seasonLabel(Api.SEASONS[cur[1]], cur[0]);
                seasonLoading = false;
                rebuild();
                return;
            }
            int[] prev = Api.prevSeason(cur[0], cur[1]);
            Api.getSeasonAnime(prev[0], Api.SEASONS[prev[1]], 1, (items2, error2) -> {
                seasonLoading = false;
                if (items2 != null && items2.size() > (items == null ? 0 : items.size())) {
                    seasonItems = items2;
                    seasonLabel = Api.seasonLabel(Api.SEASONS[prev[1]], prev[0]);
                } else if (items != null) {
                    seasonItems = items;
                    seasonLabel = Api.seasonLabel(Api.SEASONS[cur[1]], cur[0]);
                }
                rebuild();
            });
        });
    }

    private int livePoolSize() {
        return 16;
    }

    private List<Integer> metaIds(List<Models.Track> tracks) {
        List<Integer> ids = new ArrayList<>();
        for (Models.Track t : tracks) if (t.anime != null && t.anime.malId != null) ids.add(t.anime.malId);
        return ids;
    }

    private void warmMeta() {
        List<Integer> ids = new ArrayList<>();
        ids.addAll(metaIds(randomTracks));
        ids.addAll(metaIds(freshTracks));
        ids.addAll(metaIds(latestTracks));
        for (Models.AnimeSummary a : legendPick) if (a.malId != null) ids.add(a.malId);
        for (Models.AnimeSummary a : seasonItems) if (a.malId != null) ids.add(a.malId);
        Meta.warm(ids);
    }

    private List<Models.Track> livePool() {
        List<Models.Track> pool = new ArrayList<>();
        for (int i = 1; i < Math.min(9, randomTracks.size()); i++) pool.add(randomTracks.get(i));
        return pool;
    }

    private List<Models.Track> visibleFresh() {
        return Settings.filterMature(freshTracks);
    }

    private List<Models.Track> offlineTracks() {
        return Format.uniqueBy(Downloads.offlineTracks(), t -> t.id);
    }

    /* ------------------------------------------------------------------ */
    /* Вёрстка                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    protected View build() {
        Context c = ctx();
        LinearLayout root = Ui.column(c);

        // Шапка
        LinearLayout header = Ui.column(c);
        header.setBackgroundColor(Theme.BG);
        LinearLayout bar = Ui.row(c);
        bar.setPadding(dp(16), 0, dp(16), 0);
        FrameLayout logoBtn = new FrameLayout(c);
        logoBtn.addView(Ui.logo(c, 26), Ui.lp(dp(26), dp(26)));
        (Ui.flp(logoBtn.getChildAt(0))).gravity = Gravity.CENTER;
        bar.addView(logoBtn, Ui.lp(dp(44), dp(44)));
        bar.addView(Ui.iconButton(c, "search", 21, Theme.ON, () -> activity.showTab(1, true)));
        bar.addView(Ui.iconButton(c, "settings", 21, Theme.ON, () -> activity.sheets().openSettings()));
        bar.addView(new View(c), Ui.lpw(1f));
        header.addView(bar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        String[] periods = {"Сегодня", "Неделя", "Всё время"};
        int selected = "today".equals(Settings.period) ? 0 : "week".equals(Settings.period) ? 1 : 2;
        Ui.Segmented segmented = new Ui.Segmented(c, periods, selected, index -> {
            Settings.setPeriod(index == 0 ? "today" : index == 1 ? "week" : "all");
            loadFresh();
            rebuild();
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        sp.leftMargin = dp(16);
        sp.rightMargin = dp(16);
        sp.bottomMargin = dp(10);
        header.addView(segmented, sp);

        // LiveRail
        View live = liveRail();
        if (live != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(16);
            lp.rightMargin = dp(16);
            lp.bottomMargin = dp(10);
            header.addView(live, lp);
        }
        root.addView(header);

        // Содержимое
        LinearLayout scrollContent = Ui.column(c);
        List<Models.Track> heroList = randomTracks;
        Models.Track hero = heroList.isEmpty() ? null : heroList.get(0);
        scrollContent.setPadding(0, 0, 0, dp(24));

        View heroView = heroBlock(hero, heroList);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.leftMargin = dp(16);
        hp.rightMargin = dp(16);
        hp.topMargin = dp(8);
        scrollContent.addView(heroView, hp);

        scrollContent.addView(quickChips());
        scrollContent.addView(freshSection());
        scrollContent.addView(sectionTitle("Миксы", null, null));
        scrollContent.addView(mixesRow());
        scrollContent.addView(sectionTitle("Случайные", "Обновить", () -> {
            randomTick++;
            randomLoading = true;
            rebuild();
            Api.getRandomTracks(16, null, true, (tracks, error) -> {
                randomLoading = false;
                if (tracks != null) {
                    randomTracks = tracks;
                    liveTrack = tracks.isEmpty() ? null : tracks.get(0);
                    Meta.warm(metaIds(tracks));
                } else {
                    randomError = error;
                }
                rebuild();
            });
        }));
        scrollContent.addView(randomRow());

        List<Models.Track> offline = offlineTracks();
        if (!offline.isEmpty()) {
            scrollContent.addView(sectionTitle("Офлайн", "Все", () -> activity.showLibraryTab(1)));
            List<View> cards = new ArrayList<>();
            for (int i = 0; i < Math.min(12, offline.size()); i++) {
                cards.add(Cards.trackCard(activity, offline.get(i), offline));
            }
            addCardRow(scrollContent, cards);
        }

        scrollContent.addView(sectionTitle(seasonLabel, "Все", () -> activity.openYear(Api.currentSeason()[0])));
        scrollContent.addView(seasonRow());
        scrollContent.addView(sectionTitle("Легенды", null, null));
        scrollContent.addView(animeRow(legendPick));
        List<Models.Track> history = Library.history();
        if (!history.isEmpty()) {
            scrollContent.addView(sectionTitle("Недавние", "Все", () -> activity.showLibraryTab(3)));
            List<View> cards = new ArrayList<>();
            for (int i = 0; i < Math.min(12, history.size()); i++) {
                cards.add(Cards.trackCard(activity, history.get(i), history));
            }
            addCardRow(scrollContent, cards);
        }
        scrollContent.addView(sectionTitle("Исполнители", null, null));
        scrollContent.addView(artistsRow());
        scrollContent.addView(sectionTitle("Недавно добавленные", "Слушать", () -> {
            if (!latestTracks.isEmpty()) {
                Player.playTracks(latestTracks, 0, false);
                activity.nowPlaying().open();
            }
        }));
        LinearLayout latestCol = Ui.column(c);
        latestCol.setPadding(dp(16), 0, dp(16), 0);
        for (Models.Track t : latestTracks) {
            latestCol.addView(Cards.trackRow(activity, t, latestTracks, true, false, false, null, null));
        }
        scrollContent.addView(latestCol);

        View box = scroller(scrollContent);
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private View heroBlock(Models.Track hero, List<Models.Track> list) {
        Context c = ctx();
        if (hero == null) {
            if (randomLoading) {
                LinearLayout box = Ui.column(c);
                box.addView(Ui.skeleton(c, 0, dp(132), 18f), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132));
                box.setLayoutParams(p);
                return box;
            }
            if (randomError != null) return Ui.errorState(c, randomError, () -> {
                randomLoading = true;
                rebuild();
                load();
            });
            return Ui.spacer(c, dp(4));
        }
        Display d = Display.track(hero);
        LinearLayout card = Ui.row(c);
        card.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 18f)));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        CoverView cover = new CoverView(c);
        cover.setRadiusDp(12f);
        cover.setIconSizeDp(30f);
        cover.setUrl(d.cover, d.thumb);
        cover.setOnClickListener(v -> activity.openAnime(hero.anime.slug));
        card.addView(cover, Ui.lp(dp(104), dp(104)));

        LinearLayout info = Ui.column(c);
        TextView kicker = Ui.text(c, "ТРЕК ДНЯ", 11f, 0x80FFFFFF, true);
        kicker.setLetterSpacing(0.1f);
        TextView title = Ui.heading(c, hero.title, 19f, Theme.ON);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist = Ui.text(c, hero.artistNames(), 13.5f, Theme.ON_VARIANT);
        artist.setSingleLine(true);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView anime = Ui.text(c, d.title == null ? "" : d.title, 12.5f, Theme.ON_DIM);
        anime.setSingleLine(true);
        anime.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(kicker);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(4);
        info.addView(title, tp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(2);
        info.addView(artist, ap);
        info.addView(anime, ap);

        LinearLayout buttons = Ui.row(c);
        TextView listen = Ui.primaryButton(c, "Слушать", null);
        listen.setPadding(dp(16), dp(9), dp(16), dp(9));
        listen.setTextSize(14f);
        LinearLayout listenRow = Ui.row(c);
        listenRow.setBackground(Ui.rounded(Theme.ON, Theme.dpF(c, 18f)));
        listenRow.setPadding(dp(16), dp(9), dp(16), dp(9));
        listenRow.addView(Ui.icon(c, "play_arrow", 16, Theme.ON_PRIMARY));
        TextView listenText = Ui.text(c, "Слушать", 14f, Theme.ON_PRIMARY, true);
        LinearLayout.LayoutParams ltp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ltp.leftMargin = dp(6);
        listenRow.addView(listenText, ltp);
        listenRow.setOnClickListener(v -> {
            if (list.isEmpty()) return;
            Player.playTracks(list, 0, false);
            activity.nowPlaying().open();
        });
        Ui.tapScale(listenRow);
        buttons.addView(listenRow);
        FrameLayout shuffle = Ui.iconButton(c, "shuffle", 19, Theme.ON, () -> {
            if (list.isEmpty()) return;
            Player.playTracks(list, 0, true);
            activity.nowPlaying().open();
        });
        shuffle.setBackground(Ui.rounded(Theme.SURFACE_4, Theme.dpF(c, 18f)));
        shuffle.setLayoutParams(Ui.lp(dp(36), dp(36)));
        LinearLayout.LayoutParams shp = Ui.lp(dp(36), dp(36));
        shp.leftMargin = dp(8);
        shuffle.setLayoutParams(shp);
        buttons.addView(shuffle);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(12);
        info.addView(buttons, bp);

        LinearLayout.LayoutParams ip = Ui.lpw(1f);
        ip.leftMargin = dp(14);
        card.addView(info, ip);
        return card;
    }

    private View liveRail() {
        Models.Track track = liveTrack != null ? liveTrack : (randomTracks.size() > 0 ? randomTracks.get(0) : null);
        if (track == null) return null;
        Context c = ctx();
        Display d = Display.track(track);
        LinearLayout pill = Ui.row(c);
        pill.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 26f)));
        pill.setPadding(dp(10), dp(8), dp(16), dp(8));
        pill.setElevation(Theme.dpF(c, 8f));

        FrameLayout coverBox = new FrameLayout(c);
        CoverView cover = new CoverView(c);
        cover.setRadiusDp(21f);
        cover.setIconSizeDp(18f);
        cover.setUrl(d.thumb != null ? d.thumb : d.cover, d.cover);
        coverBox.addView(cover, new FrameLayout.LayoutParams(dp(42), dp(42)));
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.gravity = Gravity.TOP | Gravity.END;
        coverBox.addView(Ui.liveDot(c), lp2);
        pill.addView(coverBox, Ui.lp(dp(42), dp(42)));

        LinearLayout texts = Ui.column(c);
        TextView title = Ui.text(c, track.title, 13.5f, Theme.ON, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sub = Ui.text(c, track.themeSlug + " · " + (d.title == null ? "" : d.title), 12f, Theme.ON_VARIANT);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(sub);
        LinearLayout.LayoutParams tp = Ui.lpw(1f);
        tp.leftMargin = dp(10);
        pill.addView(texts, tp);
        pill.addView(Ui.icon(c, "play_arrow", 22, Theme.ON));
        pill.setOnClickListener(v -> {
            Models.Track t = liveTrack != null ? liveTrack : (randomTracks.isEmpty() ? null : randomTracks.get(0));
            if (t == null) return;
            Player.playTrack(t, randomTracks);
            activity.nowPlaying().open();
        });
        Ui.tapScale(pill);
        return pill;
    }

    private View quickChips() {
        Context c = ctx();
        LinearLayout row = hrow();
        row.setPadding(dp(16), dp(12), dp(16), dp(4));
        row.addView(chip("Радио", "radio", () -> {
            activity.toaster().show("Загружаем радио…");
            Api.getRandomTracks(40, null, true, (tracks, error) -> {
                if (tracks == null || tracks.isEmpty()) {
                    activity.toaster().show("Не удалось загрузить");
                    return;
                }
                Player.playTracks(Settings.filterMature(tracks), 0, false);
                activity.nowPlaying().open();
            });
        }));
        row.addView(chip("Опенинги", "whatshot", () -> activity.showBrowse("OP")));
        row.addView(chip("Эндинги", "schedule", () -> activity.showBrowse("ED")));
        row.addView(chip("Офлайн", "offline_pin", () -> {
            activity.showTab(3, true);
            if (activity.tabIndex() == 3) activity.sheets().openDownloadsSheet();
        }));
        boolean mature = "on".equals(Settings.mature);
        row.addView(chip(mature ? "18+ вкл" : "18+ выкл", "filter_alt", () -> {
            Settings.setMature(mature ? "off" : "on");
            rebuild();
        }));
        return hscroll(row);
    }

    private View chip(String label, String iconName, Runnable click) {
        Context c = ctx();
        LinearLayout pill = Ui.row(c);
        pill.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 18f)));
        pill.setPadding(dp(16), dp(9), dp(16), dp(9));
        ImageView iv = Ui.icon(c, iconName, 16, Theme.ON_VARIANT);
        pill.addView(iv);
        TextView tv = Ui.text(c, label, 13.5f, Theme.ON, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(6);
        pill.addView(tv, p);
        pill.setOnClickListener(v -> click.run());
        Ui.tapScale(pill);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        pill.setLayoutParams(lp);
        return pill;
    }

    private View freshSection() {
        Context c = ctx();
        List<Models.Track> list = visibleFresh();
        LinearLayout section = Ui.column(c);
        String title = "today".equals(Settings.period) ? "Сегодня" : "week".equals(Settings.period) ? "Эта неделя" : "Новое";
        section.addView(sectionTitle(title, "Обновить", () -> {
            freshLoading = true;
            freshError = null;
            rebuild();
            loadFresh();
        }));
        if (freshLoading && list.isEmpty()) {
            section.addView(Cards.trackRowSkeleton(c, 5));
        } else if (freshError != null && list.isEmpty()) {
            section.addView(Ui.errorState(c, freshError, this::loadFresh));
        } else if (list.isEmpty()) {
            section.addView(Ui.emptyState(c, "calendar", "Здесь пока пусто", "Смените период на «Всё время».", null, null));
        } else {
            LinearLayout col = Ui.column(c);
            col.setPadding(dp(16), 0, 0, 0);
            int count = Math.min(6, list.size());
            for (int i = 0; i < count; i++) {
                col.addView(Cards.trackRow(activity, list.get(i), list, true, false, false, null, null));
            }
            section.addView(col);
            if (list.size() > count) {
                LinearLayout wrap = Ui.row(c);
                wrap.setPadding(dp(16), dp(8), dp(16), 0);
                TextView more = Ui.tintedButton(c, "Все новинки · " + list.size(), () -> activity.showBrowse("fresh"));
                wrap.addView(more);
                section.addView(wrap);
            }
        }
        return section;
    }

    private View mixesRow() {
        List<View> cards = new ArrayList<>();
        for (Models.Mix mix : Api.MIXES) {
            cards.add(Cards.mixCard(activity, mix, () -> playMix(mix), mix.id.equals(busy)));
        }
        LinearLayout holder = Ui.column(ctx());
        addCardRow(holder, cards);
        return holder;
    }

    private void playMix(Models.Mix mix) {
        if (busy != null) return;
        busy = mix.id;
        rebuild();
        Api.getTracksForAnimeSlugs(java.util.Arrays.asList(mix.slugs), (tracks, error) -> {
            busy = null;
            if (tracks == null || tracks.isEmpty()) {
                activity.toaster().show("Микс пуст — попробуйте ещё раз");
                rebuild();
                return;
            }
            Player.playTracks(Settings.filterMature(tracks), 0, true);
            activity.nowPlaying().open();
            rebuild();
        });
    }

    private View randomRow() {
        Context c = ctx();
        LinearLayout holder = Ui.column(c);
        if (randomLoading && randomTracks.isEmpty()) {
            holder.addView(Cards.trackRowSkeleton(c, 2));
            return holder;
        }
        List<View> cards = new ArrayList<>();
        for (int i = 1; i < randomTracks.size(); i++) {
            cards.add(Cards.trackCard(activity, randomTracks.get(i), randomTracks));
        }
        addCardRow(holder, cards);
        return holder;
    }

    private View seasonRow() {
        Context c = ctx();
        LinearLayout holder = Ui.column(c);
        if (seasonLoading && seasonItems.isEmpty()) {
            holder.addView(Cards.trackRowSkeleton(c, 2));
            return holder;
        }
        List<View> cards = new ArrayList<>();
        for (Models.AnimeSummary anime : seasonItems) cards.add(Cards.animeCard(activity, anime, false));
        addCardRow(holder, cards);
        return holder;
    }

    private View animeRow(List<Models.AnimeSummary> items) {
        List<View> cards = new ArrayList<>();
        for (Models.AnimeSummary anime : items) cards.add(Cards.animeCard(activity, anime, false));
        LinearLayout holder = Ui.column(ctx());
        addCardRow(holder, cards);
        return holder;
    }

    private View artistsRow() {
        Context c = ctx();
        LinearLayout holder = Ui.column(c);
        if (artistPick.isEmpty()) {
            holder.addView(Cards.trackRowSkeleton(c, 1));
            return holder;
        }
        List<View> cards = new ArrayList<>();
        for (Models.ArtistSummary artist : artistPick) cards.add(Cards.artistCard(activity, artist));
        addCardRow(holder, cards);
        return holder;
    }

    private View sectionTitle(String title, String action, Runnable onAction) {
        LinearLayout header = Cards.sectionHeader(ctx(), title, action, onAction == null ? null : onAction::run);
        LinearLayout wrap = Ui.column(ctx());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(28);
        wrap.addView(header, p);
        return wrap;
    }

    @Override
    public void onHide() {
        super.onHide();
        handler.removeCallbacks(liveTick);
    }

    @Override
    public void onShow() {
        super.onShow();
        handler.removeCallbacks(liveTick);
        handler.postDelayed(liveTick, 50_000);
    }

    @Override
    public String title() {
        return "Главная";
    }
}
