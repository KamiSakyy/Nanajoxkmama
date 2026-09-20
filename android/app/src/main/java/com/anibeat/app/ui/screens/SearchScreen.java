package com.anibeat.app.ui.screens;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.AnisongDb;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.ScreenBase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Поиск — порт pages/Search.tsx (debounce 380мс, вкладки, расширенная база). */
public class SearchScreen extends ScreenBase {

    private static final String[] SUGGESTIONS = {"Атака титанов", "Gurenge", "Наруто", "Unravel", "Тетрадь смерти", "Idol", "Магическая битва", "Blue Bird", "Kaikai Kitan", "Ван-Пис", "Tank!", "Sparkle"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debounceTask = this::runSearch;

    private String query = "";
    private int tab;
    private EditText input;
    private LinearLayout header;
    private LinearLayout bodyHolder;
    private LinearLayout tabsHolder;
    private ScrollState scrollState;

    private List<Models.AnimeSummary> anime = new ArrayList<>();
    private List<Models.Track> tracks = new ArrayList<>();
    private List<Models.Track> extraTracks = new ArrayList<>();
    private List<Models.ArtistSummary> artists = new ArrayList<>();
    private boolean loading;
    private boolean extraLoading;
    private String error;
    private boolean searched;
    private int requestSeq;

    public SearchScreen(MainActivity activity) {
        super(activity);
    }

    private static final class ScrollState {
    }

    private boolean enabled() {
        return query.trim().length() >= 2;
    }

    private void scheduleSearch() {
        handler.removeCallbacks(debounceTask);
        if (!enabled()) {
            reset();
            fillBody();
            updateTabs();
            return;
        }
        loading = true;
        fillBody();
        updateTabs();
        handler.postDelayed(debounceTask, 380);
    }

    private void reset() {
        anime = new ArrayList<>();
        tracks = new ArrayList<>();
        extraTracks = new ArrayList<>();
        artists = new ArrayList<>();
        searched = false;
        error = null;
        loading = false;
        extraLoading = false;
    }

    private void runSearch() {
        final String q = query.trim();
        if (q.length() < 2) return;
        final int seq = ++requestSeq;
        final boolean extra = Settings.extraSources;
        final boolean needByTitle = Settings.ruTitles || com.anibeat.app.ui.Format.hasCyrillic(q);
        final List<Models.AnimeSummary> byTitle = new ArrayList<>();
        final boolean[] done = {false, !extra, !needByTitle};

        Runnable finish = () -> {
            if (seq != requestSeq) return;
            loading = false;
            extraLoading = false;
            searched = true;
            fillBody();
        };

        Api.searchAll(q, (results, err) -> {
            if (seq != requestSeq) return;
            if (results != null) {
                tracks = results.tracks;
                artists = results.artists;
                List<Models.AnimeSummary> merged = new ArrayList<>(byTitle);
                merged.addAll(results.anime);
                anime = uniqueAnime(merged);
                Meta.warm(metaIds(tracks));
                Library.addRecentSearch(q);
            } else {
                error = err;
            }
            done[0] = true;
            if (done[0] && done[1] && done[2]) finish.run();
        });

        if (extra) {
            extraLoading = true;
            AnisongDb.search(q, (list, err) -> {
                if (seq != requestSeq) return;
                if (list != null) {
                    Set<String> known = new LinkedHashSet<>();
                    for (Models.Track t : tracks) known.add(key(t));
                    List<Models.Track> filtered = new ArrayList<>();
                    for (Models.Track t : list) {
                        if (t.anime == null || t.anime.malId == null || !known.contains(key(t))) filtered.add(t);
                    }
                    extraTracks = filtered;
                    Meta.warm(metaIds(extraTracks));
                }
                done[1] = true;
                if (done[0] && done[1] && done[2]) finish.run();
            });
        }

        if (needByTitle) {
            Meta.searchShikimori(q, (hits, err) -> {
                if (seq != requestSeq) return;
                if (hits == null || hits.isEmpty()) {
                    done[2] = true;
                    if (done[0] && done[1] && done[2]) finish.run();
                    return;
                }
                List<Integer> ids = new ArrayList<>();
                for (Meta.ShikiHit hit : hits) ids.add(hit.malId);
                Api.getAnimeByMalIds(ids, (list, err2) -> {
                    if (seq != requestSeq) return;
                    if (list != null) {
                        List<Models.AnimeSummary> merged = new ArrayList<>(list);
                        merged.addAll(anime);
                        anime = uniqueAnime(merged);
                        byTitle.clear();
                        byTitle.addAll(list);
                    }
                    done[2] = true;
                    if (done[0] && done[1] && done[2]) finish.run();
                });
            });
        }
    }

    private static String key(Models.Track t) {
        Integer mal = t.anime == null ? null : t.anime.malId;
        return (mal == null ? "" : String.valueOf(mal)) + "|" + t.type + (t.sequence == null ? "" : t.sequence);
    }

    private static List<Models.AnimeSummary> uniqueAnime(List<Models.AnimeSummary> list) {
        List<Models.AnimeSummary> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Models.AnimeSummary a : list) {
            if (a == null || a.slug == null || seen.contains(a.slug)) continue;
            seen.add(a.slug);
            out.add(a);
        }
        return out;
    }

    private static List<Integer> metaIds(List<Models.Track> items) {
        List<Integer> ids = new ArrayList<>();
        for (Models.Track t : items) if (t.anime != null && t.anime.malId != null) ids.add(t.anime.malId);
        return ids;
    }

    /* ------------------------------------------------------------------ */

    @Override
    protected View build() {
        Context c = ctx();
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Theme.BG);

        header = Ui.column(c);
        LinearLayout row = Ui.row(c);
        row.setPadding(dp(16), dp(8), dp(12), dp(8));
        LinearLayout field = Ui.row(c);
        field.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 10f)));
        field.setPadding(dp(10), 0, dp(10), 0);
        field.addView(Ui.icon(c, "search", 17, Theme.ON_DIM));
        input = new EditText(c);
        input.setHint("Аниме, песня, исполнитель");
        input.setHintTextColor(Theme.ON_DIM);
        input.setTextColor(Theme.ON);
        input.setTextSize(17f);
        input.setSingleLine(true);
        input.setBackgroundColor(0x00000000);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(query);
        input.setPadding(dp(6), 0, dp(6), 0);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String value = s.toString();
                if (value.equals(query)) return;
                query = value;
                scheduleSearch();
            }
        });
        field.addView(input, Ui.lpw(1f));

        final FrameLayout clearBox = new FrameLayout(c);
        clearBox.setBackground(Ui.rounded(Theme.SURFACE_5, Theme.dpF(c, 10f)));
        ImageView close = Ui.icon(c, "close", 12, 0xFF000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(12), dp(12));
        cp.gravity = Gravity.CENTER;
        clearBox.addView(close, cp);
        clearBox.setOnClickListener(v -> {
            query = "";
            input.setText("");
            reset();
            fillBody();
            updateTabs();
            rebuildClearVisibility();
        });
        clearBox.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        field.addView(clearBox, Ui.lp(dp(20), dp(20)));

        row.addView(field, Ui.lpw(1f));

        final TextView cancel = Ui.text(c, "Отмена", 17f, Theme.PRIMARY);
        cancel.setPadding(dp(10), dp(10), dp(4), dp(10));
        cancel.setOnClickListener(v -> {
            query = "";
            input.setText("");
            input.clearFocus();
            View focus = activity.getCurrentFocus();
            if (focus != null) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
            reset();
            fillBody();
            updateTabs();
            rebuildClearVisibility();
        });
        row.addView(cancel);
        header.addView(row, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        tabsHolder = Ui.column(c);
        header.addView(tabsHolder);
        root.addView(header);

        bodyHolder = Ui.column(c);
        bodyHolder.setPadding(0, 0, 0, dp(24));
        View scroll = scroller(bodyHolder);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        fillBody();
        updateTabs();
        return root;
    }

    private void rebuildClearVisibility() {
        input.post(() -> {
            ViewGroup field = (ViewGroup) input.getParent();
            if (field != null && field.getChildCount() > 2) {
                field.getChildAt(field.getChildCount() - 1).setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void updateTabs() {
        if (tabsHolder == null) return;
        tabsHolder.removeAllViews();
        if (!enabled()) return;
        Ui.Segmented segmented = new Ui.Segmented(ctx(), new String[]{"Всё", "Треки", "Аниме", "Артисты"}, tab, index -> {
            tab = index;
            fillBody();
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        sp.leftMargin = dp(16);
        sp.rightMargin = dp(16);
        sp.bottomMargin = dp(10);
        tabsHolder.addView(segmented, sp);
    }

    @Override
    public void rebuild() {
        if (bodyHolder == null) {
            super.rebuild();
            return;
        }
        fillBody();
        updateTabs();
        rebuildClearVisibility();
    }

    private void fillBody() {
        if (bodyHolder == null) return;
        Context c = ctx();
        View scrollY = bodyHolder.getParent() instanceof android.widget.ScrollView ? (View) bodyHolder.getParent() : null;
        int keepScroll = scrollY instanceof android.widget.ScrollView ? ((android.widget.ScrollView) scrollY).getScrollY() : 0;
        bodyHolder.removeAllViews();

        if (!enabled()) {
            bodyHolder.addView(suggestions());
        } else if (loading && anime.isEmpty() && tracks.isEmpty() && artists.isEmpty()) {
            bodyHolder.addView(Cards.trackRowSkeleton(c, 5));
        } else if (error != null && anime.isEmpty() && extraTracks.isEmpty()) {
            bodyHolder.addView(Ui.errorState(c, error, this::scheduleSearch));
        } else {
            int total = anime.size() + tracks.size() + artists.size() + extraTracks.size();
            if (searched && total == 0 && !extraLoading) {
                bodyHolder.addView(Ui.emptyState(c, "search", "Ничего не найдено", "Попробуйте другое написание — по-русски или латиницей.", null, null));
            } else {
                if ((tab == 0 || tab == 2) && !anime.isEmpty()) {
                    bodyHolder.addView(sectionTitle("Аниме"));
                    List<View> cards = new ArrayList<>();
                    for (Models.AnimeSummary a : anime) cards.add(Cards.animeCard(activity, a, tab == 2));
                    if (tab == 2) {
                        LinearLayout grid = gridRow(cards, 3);
                        grid.setPadding(dp(10), 0, dp(10), 0);
                        bodyHolder.addView(grid);
                    } else {
                        LinearLayout holder = Ui.column(c);
                        addCardRow(holder, cards);
                        bodyHolder.addView(holder);
                    }
                }
                List<Models.Track> all = new ArrayList<>(tracks);
                all.addAll(extraTracks);
                if ((tab == 0 || tab == 1) && !tracks.isEmpty()) {
                    bodyHolder.addView(sectionTitleWithAction("Треки", "Слушать", () -> {
                        Player.playTracks(all, 0, false);
                        activity.nowPlaying().open();
                    }));
                    bodyHolder.addView(trackList(tab == 0 ? tracks.subList(0, Math.min(8, tracks.size())) : tracks, all));
                }
                if ((tab == 0 || tab == 1) && (!extraTracks.isEmpty() || extraLoading)) {
                    bodyHolder.addView(sectionTitle("Ещё треки"));
                    if (extraLoading && extraTracks.isEmpty()) {
                        bodyHolder.addView(Cards.trackRowSkeleton(c, 3));
                    } else {
                        bodyHolder.addView(trackList(tab == 0 ? extraTracks.subList(0, Math.min(6, extraTracks.size())) : extraTracks, all));
                    }
                }
                if ((tab == 0 || tab == 3) && !artists.isEmpty()) {
                    bodyHolder.addView(sectionTitle("Исполнители"));
                    List<View> cards = new ArrayList<>();
                    for (Models.ArtistSummary artist : artists) cards.add(Cards.artistCard(activity, artist));
                    LinearLayout holder = Ui.column(c);
                    addCardRow(holder, cards);
                    bodyHolder.addView(holder);
                }
            }
        }
        if (scrollY instanceof android.widget.ScrollView && keepScroll > 0) {
            final android.widget.ScrollView sv = (android.widget.ScrollView) scrollY;
            sv.post(() -> sv.scrollTo(0, keepScroll));
        }
    }

    private View trackList(List<Models.Track> items, List<Models.Track> context) {
        Context c = ctx();
        LinearLayout col = Ui.column(c);
        col.setPadding(dp(16), 0, 0, 0);
        for (Models.Track t : items) col.addView(Cards.trackRow(activity, t, context, true, false, false, null, null));
        return col;
    }

    private View suggestions() {
        Context c = ctx();
        LinearLayout col = Ui.column(c);
        col.setPadding(dp(16), dp(12), dp(16), 0);
        List<String> recent = Library.recentSearches();
        if (!recent.isEmpty()) {
            LinearLayout head = Ui.row(c);
            TextView title = Ui.text(c, "Недавние", 15f, Theme.ON, true);
            head.addView(title, Ui.lpw(1f));
            TextView clear = Ui.text(c, "Очистить", 14f, Theme.PRIMARY);
            clear.setOnClickListener(v -> {
                Library.clearRecentSearches();
                fillBody();
            });
            Ui.tap(clear);
            head.addView(clear);
            col.addView(head);
            LinearLayout group = Ui.listGroup(c, null, null);
            LinearLayout body = Ui.groupBody(group);
            boolean first = true;
            for (String value : recent) {
                LinearLayout row = Ui.row(c);
                row.setPadding(dp(16), dp(12), dp(14), dp(12));
                row.addView(Ui.icon(c, "history", 17, Theme.ON_DIM));
                TextView label = Ui.text(c, value, 16f, Theme.ON);
                LinearLayout.LayoutParams lp = Ui.lpw(1f);
                lp.leftMargin = dp(12);
                row.addView(label, lp);
                row.setOnClickListener(v -> {
                    query = value;
                    input.setText(value);
                    scheduleSearch();
                });
                Ui.tap(row);
                body.addView(row);
                first = false;
            }
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.bottomMargin = dp(20);
            col.addView(group, gp);
        }
        col.addView(Ui.text(c, "Попробуйте", 15f, Theme.ON, true));
        LinearLayout wrap = Ui.column(c);
        LinearLayout current = null;
        for (int i = 0; i < SUGGESTIONS.length; i++) {
            if (i % 2 == 0) {
                current = Ui.row(c);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(8);
                wrap.addView(current, rp);
            }
            final String value = SUGGESTIONS[i];
            TextView chipView = Ui.chip(c, value, null, false, () -> {
                query = value;
                input.setText(value);
                scheduleSearch();
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(8);
            current.addView(chipView, cp);
        }
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dp(8);
        col.addView(wrap, wp);
        return col;
    }

    private View sectionTitle(String title) {
        return sectionTitleWithAction(title, null, null);
    }

    private View sectionTitleWithAction(String title, String action, Runnable onAction) {
        LinearLayout wrap = Ui.column(ctx());
        LinearLayout headerRow = Cards.sectionHeader(ctx(), title, action, onAction == null ? null : onAction::run);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(20);
        wrap.addView(headerRow, p);
        return wrap;
    }

    @Override
    public void onHide() {
        super.onHide();
        handler.removeCallbacks(debounceTask);
    }

    @Override
    public String title() {
        return "Поиск";
    }
}
