package com.anibeat.app.ui.screens;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.R;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.ListScreen;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Поиск по темам, аниме и исполнителям. */
public class SearchScreen extends ListScreen {

    private final EditText input;
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private final Runnable searchTask = () -> Ui.safe(this::search);
    private Models.SearchResults results;
    private String mode = "all";
    private String query = "";
    private int pending;
    private boolean searching;
    private int barHeight;
    private int generation;

    public SearchScreen(Context context, Host host) {
        super(context, host);

        MaterialCardView bar = new MaterialCardView(context);
        bar.setCardBackgroundColor(Theme.SURFACE_3);
        bar.setRadius(Theme.dpF(context, 16));
        bar.setCardElevation(0f);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(context, 14), Theme.dp(context, 4), Theme.dp(context, 8), Theme.dp(context, 4));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(Theme.ON_VARIANT);
        row.addView(icon, new LinearLayout.LayoutParams(Theme.dp(context, 20), Theme.dp(context, 20)));
        input = new EditText(context);
        input.setHint("Поиск: аниме, тема, исполнитель");
        input.setHintTextColor(Theme.ON_DIM);
        input.setTextColor(Theme.ON);
        input.setTextSize(15f);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        inputParams.leftMargin = Theme.dp(context, 10);
        row.addView(input, inputParams);
        ImageView clear = new ImageView(context);
        clear.setImageResource(R.drawable.ic_close);
        clear.setColorFilter(Theme.ON_VARIANT);
        clear.setPadding(Theme.dp(context, 10), Theme.dp(context, 10), Theme.dp(context, 10), Theme.dp(context, 10));
        row.addView(clear, new LinearLayout.LayoutParams(Theme.dp(context, 40), Theme.dp(context, 40)));
        clear.setOnClickListener(v -> {
            input.setText("");
            results = null;
            query = "";
            showStart();
        });
        bar.addView(row);
        LinearLayout barWrap = new LinearLayout(context);
        barWrap.setOrientation(LinearLayout.VERTICAL);
        barWrap.setPadding(Theme.dp(context, 12), Theme.dp(context, 4), Theme.dp(context, 12), Theme.dp(context, 10));
        barWrap.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addHeader(barWrap);
        this.barHeight = 0;

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim();
                debounce.removeCallbacks(searchTask);
                if (query.length() >= 2) debounce.postDelayed(searchTask, 300);
                else if (query.isEmpty()) {
                    results = null;
                    showStart();
                }
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            debounce.removeCallbacks(searchTask);
            search();
            return true;
        });
        showStart();
    }

    @Override
    protected int extraTop() {
        return 0;
    }

    @Override
    protected void load(boolean refresh) {
        if (query.length() >= 2) search();
        else showStart();
    }

    private void search() {
        if (query.length() < 2) {
            showStart();
            return;
        }
        searching = true;
        final String asked = query;
        final int ticket = ++generation;
        setRefreshing(true);
        Library.addRecentSearch(asked);
        final boolean russian = hasCyrillic(asked);
        results = new Models.SearchResults();
        pending = russian ? 2 : 1;

        Api.searchAll(asked, (found, error) -> Ui.postSafe(() -> {
            if (ticket != generation) return;
            merge(found, error);
        }));

        if (russian) {
            // Русское название ищем отдельно: Shikimori → AniThemes.
            Api.searchRussian(asked, (found, error) -> Ui.postSafe(() -> {
                if (ticket != generation) return;
                merge(found, null);
            }));
        }
    }

    /** Слить ответ источника с тем, что уже найдено (важно для русского поиска). */
    private void merge(Models.SearchResults found, String error) {
        if (found != null) {
            if (results == null) results = new Models.SearchResults();
            if (found.tracks != null) for (Models.Track t : found.tracks) {
                if (t == null || t.id == null) continue;
                boolean has = false;
                for (Models.Track known : results.tracks) if (t.id.equals(known.id)) has = true;
                if (!has) results.tracks.add(t);
            }
            if (found.anime != null) for (Models.AnimeSummary a : found.anime) {
                if (a == null || a.slug == null) continue;
                boolean has = false;
                for (Models.AnimeSummary known : results.anime) if (a.slug.equals(known.slug)) has = true;
                if (!has) results.anime.add(a);
            }
            if (found.artists != null) for (Models.ArtistSummary a : found.artists) {
                if (a == null || a.slug == null) continue;
                boolean has = false;
                for (Models.ArtistSummary known : results.artists) if (a.slug.equals(known.slug)) has = true;
                if (!has) results.artists.add(a);
            }
        }
        if (pending > 0) pending--;
        searching = false;
        setRefreshing(false);
        boolean empty = results == null || (results.tracks.isEmpty() && results.anime.isEmpty() && results.artists.isEmpty());
        if (empty && pending == 0) {
            fail(error == null ? "Ничего не найдено" : error);
            return;
        }
        if (!empty) renderResults();
    }

    private static boolean hasCyrillic(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) return true;
        }
        return false;
    }

    private void showStart() {
        List<Block> blocks = new ArrayList<>();
        List<Block.Row> rows = new ArrayList<>();
        for (final String recent : Library.recentSearches()) {
            Block.Row row = new Block.Row(recent, recent, "Недавний запрос");
            row.icon = R.drawable.ic_history;
            row.chevron = false;
            row.action = () -> {
                input.setText(recent);
                input.setSelection(recent.length());
            };
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            blocks.add(Block.section("Недавние запросы", "Очистить"));
            Block recent = Block.rows(rows);
            recent.onAction = () -> {
                Library.clearRecentSearches();
                showStart();
            };
            blocks.add(recent);
        }
        Block genres = Block.chips("Жанры", genreIds(), genreNames(), null);
        genres.onChip = (id, label) -> host.openGenre(id, label);
        blocks.add(genres);
        blocks.add(Block.mixRow("Подборки", Api.MIXES));
        render(blocks, new ArrayList<>());
    }

    private void renderResults() {
        List<Block> blocks = new ArrayList<>();
        List<Models.Track> trackList = new ArrayList<>();
        blocks.add(Block.header("Найдено: " + query));
        Block chips = Block.chips("", ids(), labels(), mode);
        chips.onChip = (id, label) -> {
            mode = id;
            renderResults();
        };
        blocks.add(chips);

        if (results.tracks != null && !results.tracks.isEmpty() && ("all".equals(mode) || "tracks".equals(mode))) {
            List<Models.Track> found = Settings.filterMature(results.tracks);
            blocks.add(Block.section("Темы"));
            trackList.addAll(found);
            for (int i = 0; i < found.size(); i++) {
                Models.Track track = found.get(i);
                blocks.add(Block.track(track, i, track.id != null && track.id.equals(playingId())));
            }
        }
        if (results.anime != null && !results.anime.isEmpty() && ("all".equals(mode) || "anime".equals(mode))) {
            if (results.anime.size() > 3) {
                blocks.add(Block.animeRow("Аниме", results.anime.subList(0, Math.min(20, results.anime.size()))));
            } else {
                blocks.add(Block.section("Аниме"));
                blocks.add(Block.animePair(results.anime));
            }
        }
        if (results.artists != null && !results.artists.isEmpty() && ("all".equals(mode) || "artists".equals(mode))) {
            blocks.add(Block.artistRow("Исполнители", results.artists));
        }
        if (trackList.isEmpty() && (results.anime == null || results.anime.isEmpty())
                && (results.artists == null || results.artists.isEmpty())) {
            blocks.add(Block.empty("Ничего не найдено", ""));
        }
        render(blocks, trackList);
    }

    @Override
    public void onShow() {
        super.onShow();
        Ui.postSafe(() -> {
            if (query.isEmpty()) showStart();
        });
    }

    @Override
    protected void rebuild() {
        if (results == null) showStart();
        else renderResults();
    }

    private static List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.add("all");
        ids.add("tracks");
        ids.add("anime");
        ids.add("artists");
        return ids;
    }

    private static List<String> labels() {
        List<String> labels = new ArrayList<>();
        labels.add("Всё");
        labels.add("Темы");
        labels.add("Аниме");
        labels.add("Исполнители");
        return labels;
    }

    private static List<String> genreIds() {
        List<String> ids = new ArrayList<>();
        for (Models.GenreDef def : Api.GENRES) ids.add(def.id);
        return ids;
    }

    private static List<String> genreNames() {
        List<String> names = new ArrayList<>();
        for (Models.GenreDef def : Api.GENRES) names.add(def.label);
        return names;
    }

    /** Поле поиска доступно из тестов. */
    public EditText field() {
        return input;
    }

    /** Запустить поиск без клавиатуры. */
    public void searchNow(String text) {
        input.setText(text);
        input.setSelection(input.getText().length());
        debounce.removeCallbacks(searchTask);
        query = text == null ? "" : text.trim();
        search();
    }

    @Override
    public String title() {
        return "Поиск";
    }
}
