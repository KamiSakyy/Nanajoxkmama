package com.kamisakyy.nanajoxkmama;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Search — port of pages/Search.tsx: debounced multi-source search with tabs. */
public final class SearchScreen implements Screen {
    private static final String[] SUGGESTIONS = {
        "Атака титанов", "Gurenge", "Наруто", "Unravel", "Тетрадь смерти", "Idol",
        "Магическая битва", "Blue Bird", "Kaikai Kitan", "Ван-Пис", "Tank!", "Sparkle"
    };

    private final Ui.Host host;
    private final Activity a;
    private final LinearLayout root;
    private final EditText input;
    private final LinearLayout tabRow;
    private final ScrollView scroll;
    private final LinearLayout results;
    private final LinearLayout idleView;
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private String tab = "all";
    private int generation;
    private String lastQuery = "";
    private List<Track> allTracks = new ArrayList<>();

    public SearchScreen(Ui.Host host) {
        this.host = host;
        this.a = host.activity();
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        // search bar
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(8), Ui.dp(4));
        FrameLayout field = new FrameLayout(a);
        field.setBackground(Ui.rounded(Ui.S3, 10));
        LinearLayout fieldRow = new LinearLayout(a);
        fieldRow.setOrientation(LinearLayout.HORIZONTAL);
        fieldRow.setGravity(Gravity.CENTER_VERTICAL);
        fieldRow.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
        fieldRow.addView(Ui.icon(a, "search", 16, Ui.DIM));
        input = new EditText(a);
        input.setSingleLine(true);
        input.setHint("Аниме, песня, исполнитель");
        input.setHintTextColor(Ui.DIM);
        input.setTextColor(Ui.ON);
        input.setTextSize(16);
        input.setBackground(null);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, Ui.dp(38), 1f);
        ip.leftMargin = Ui.dp(6);
        fieldRow.addView(input, ip);
        field.addView(fieldRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(38)));
        bar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView cancel = Ui.text(a, "Отмена", 15.5f, Ui.ACCENT, true);
        cancel.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(8), Ui.dp(10));
        cancel.setOnClickListener(v -> {
            input.setText("");
            input.clearFocus();
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            showIdle();
        });
        bar.addView(cancel);
        root.addView(bar);

        tabRow = new LinearLayout(a);
        tabRow.setOrientation(LinearLayout.VERTICAL);
        tabRow.setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(2));
        tabRow.setVisibility(View.GONE);
        root.addView(tabRow);

        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        results = new LinearLayout(a);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, Ui.dp(6), 0, Ui.dp(24));
        scroll.addView(results, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setVisibility(View.GONE);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        idleView = new LinearLayout(a);
        idleView.setOrientation(LinearLayout.VERTICAL);
        idleView.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(24));
        root.addView(idleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int after) { }
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                debounce.removeCallbacksAndMessages(null);
                debounce.postDelayed(() -> runSearch(input.getText().toString().trim()), 380);
                if (s.length() == 0) showIdle();
            }
        });
        showIdle();
    }

    public void focusInput() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) a.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    public void setQuery(String q) {
        input.setText(q);
    }

    private void showIdle() {
        idleView.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.GONE);
        tabRow.setVisibility(View.GONE);
        idleView.removeAllViews();
        ArrayList<String> recent = Store.getRecentSearches();
        if (!recent.isEmpty()) {
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(Ui.dp(4), Ui.dp(2), Ui.dp(4), Ui.dp(2));
            head.addView(Ui.text(a, "Недавние", 15, Ui.ON, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView clear = Ui.text(a, "Очистить", 14, Ui.ACCENT, true);
            clear.setOnClickListener(v -> {
                Store.clearRecentSearches();
                showIdle();
            });
            head.addView(clear);
            idleView.addView(head);
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setBackground(Ui.rounded(Ui.S2, 14));
            for (String s : recent) {
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(14), Ui.dp(11), Ui.dp(14), Ui.dp(11));
                row.setBackground(Ui.ripple(Ui.rounded(Color.TRANSPARENT, 10)));
                row.addView(Ui.icon(a, "history", 16, Ui.DIM));
                TextView t = Ui.text(a, s, 15, Ui.ON, false);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tp.leftMargin = Ui.dp(10);
                row.addView(t, tp);
                row.setOnClickListener(v -> setQuery(s));
                list.addView(row);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(4);
            idleView.addView(list, lp);
        }
        TextView label = Ui.text(a, "Попробуйте", 15, Ui.ON, true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(18);
        llp.bottomMargin = Ui.dp(6);
        idleView.addView(label, llp);
        LinearLayout chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView hs = Ui.hscroll(a);
        Ui.hscrollRow(hs).removeAllViews();
        for (String s : SUGGESTIONS) Ui.hscrollRow(hs).addView(Ui.chip(a, s, false, v -> setQuery(s)));
        idleView.addView(hs);
    }

    private void runSearch(String q) {
        if (q.equals(lastQuery) && results.getChildCount() > 0) return;
        lastQuery = q;
        if (q.length() < 2) {
            showIdle();
            return;
        }
        final int gen = ++generation;
        idleView.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        tabRow.setVisibility(View.VISIBLE);
        if (tabRow.getChildCount() == 0) {
            Ui.Segmented seg = new Ui.Segmented(a, new String[]{"all", "tracks", "anime", "artists"},
                    new String[]{"Всё", "Треки", "Аниме", "Артисты"}, tab, v -> {
                tab = v;
                runSearch(lastQuery);
            });
            tabRow.addView(seg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34)));
        }
        results.removeAllViews();
        for (int i = 0; i < 5; i++) results.addView(rowSkeleton());

        final boolean cyrillic = q.toLowerCase(Locale.US).matches(".*[а-яё].*");
        final boolean ruSearch = cyrillic || Store.isRuTitlesEnabled();
        final boolean extra = Store.isExtraSourcesEnabled();

        host.runIo(() -> {
            ApiClient.SearchResult primary = null;
            String primaryError = null;
            try {
                primary = ApiClient.search(q);
            } catch (Exception e) {
                primaryError = e.getMessage();
            }
            ArrayList<Track> extraTracks = new ArrayList<>();
            if (extra) {
                try {
                    extraTracks = ApiClient.anisongSearch(q);
                } catch (Exception ignored) { }
            }
            final ApiClient.SearchResult res = primary;
            final String err = primaryError;
            final ArrayList<Track> extras = extraTracks;
            // RU title search
            if (ruSearch) {
                try {
                    ArrayList<MetaApi.ShikiHit> hits = MetaApi.searchShikimori(q);
                    ArrayList<Integer> ids = new ArrayList<>();
                    for (MetaApi.ShikiHit h : hits) ids.add(h.malId);
                    final ArrayList<AnimeInfo> ruAnime = ids.isEmpty() ? new ArrayList<>() : ApiClient.animeByMalIds(ids);
                    a.runOnUiThread(() -> {
                        if (gen != generation) return;
                        bindResults(res, err, extras, ruAnime, q);
                    });
                } catch (Exception e) {
                    a.runOnUiThread(() -> {
                        if (gen != generation) return;
                        bindResults(res, err, extras, new ArrayList<>(), q);
                    });
                }
            } else {
                a.runOnUiThread(() -> {
                    if (gen != generation) return;
                    bindResults(res, err, extras, new ArrayList<>(), q);
                });
            }
        });
    }

    private List<AnimeInfo> ruAnimeRef = new ArrayList<>();

    private void bindResults(ApiClient.SearchResult res, String err, List<Track> extras, List<AnimeInfo> ruAnime, String q) {
        ruAnimeRef = ruAnime;
        results.removeAllViews();
        ArrayList<AnimeInfo> anime = new ArrayList<>();
        ArrayList<Track> tracks = new ArrayList<>();
        ArrayList<ArtistInfo> artists = new ArrayList<>();
        if (res != null) {
            anime.addAll(ruAnime);
            for (AnimeInfo x : res.anime) {
                boolean dup = false;
                for (AnimeInfo y : anime) if (y.slug.equals(x.slug)) { dup = true; break; }
                if (!dup) anime.add(x);
            }
            tracks.addAll(res.tracks);
            artists.addAll(res.artists);
        }
        Set<String> known = new HashSet<>();
        for (Track t : tracks) known.add((t.malId > 0 ? t.malId : "") + "|" + t.type + t.sequence);
        ArrayList<Track> extraFiltered = new ArrayList<>();
        for (Track t : extras) {
            String key = (t.malId > 0 ? t.malId : "") + "|" + t.type + t.sequence;
            if (t.malId <= 0 || !known.contains(key)) extraFiltered.add(t);
        }
        allTracks = new ArrayList<>(tracks);
        allTracks.addAll(extraFiltered);
        Store.addRecentSearch(q);

        int total = anime.size() + allTracks.size() + artists.size();
        if (res == null && extraFiltered.isEmpty()) {
            results.addView(Ui.errorState(a, err == null ? "" : err, v -> {
                lastQuery = "";
                runSearch(q);
            }));
            return;
        }
        if (total == 0) {
            results.addView(Ui.emptyState(a, "search", "Ничего не найдено",
                    "Попробуйте другое написание — по-русски или латиницей.", null, null));
            return;
        }

        if (("all".equals(tab) || "anime".equals(tab)) && !anime.isEmpty()) {
            results.addView(section("Аниме", null, null));
            HorizontalScrollView hs = Ui.hscroll(a);
            LinearLayout row = Ui.hscrollRow(hs);
            for (AnimeInfo info : anime) row.addView(Ui.animeCard(a, host, info));
            results.addView(hs);
        }
        if ("tracks".equals(tab) && !allTracks.isEmpty()) {
            results.addView(section("Треки", "Слушать", v -> host.playAll(allTracks, 0, false)));
            for (int i = 0; i < allTracks.size(); i++) {
                results.addView(new Ui.TrackRow(a, host, allTracks.get(i), allTracks, true, false, null));
            }
        } else if ("all".equals(tab) && !tracks.isEmpty()) {
            results.addView(section("Треки", "Слушать", v -> {
                if (!allTracks.isEmpty()) host.playAll(allTracks, 0, false);
            }));
            int show = Math.min(10, tracks.size());
            for (int i = 0; i < show; i++) {
                results.addView(new Ui.TrackRow(a, host, tracks.get(i), allTracks, true, false, null));
            }
        }
        if ("all".equals(tab) && !extraFiltered.isEmpty()) {
            results.addView(section("Ещё треки", "Все", v -> {
                tab = "tracks";
                results.removeAllViews();
                bindResults(res, err, extras, ruAnimeRef, q);
            }));
            int show = Math.min(6, extraFiltered.size());
            for (int i = 0; i < show; i++) {
                results.addView(new Ui.TrackRow(a, host, extraFiltered.get(i), allTracks, true, false, null));
            }
        }
        if (("all".equals(tab) || "artists".equals(tab)) && !artists.isEmpty()) {
            results.addView(section("Исполнители", null, null));
            HorizontalScrollView hs = Ui.hscroll(a);
            LinearLayout row = Ui.hscrollRow(hs);
            for (ArtistInfo info : artists) {
                if (info.slug != null && !info.slug.isEmpty()) row.addView(Ui.artistCard(a, host, info));
            }
            results.addView(hs);
        }
    }

    private View section(String title, String action, View.OnClickListener onAction) {
        return Ui.sectionHeader(a, title, action, onAction);
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
}
