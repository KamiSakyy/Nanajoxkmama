package com.anibeat.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.anibeat.app.core.Net;
import com.anibeat.app.core.Prefs;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.Img;
import com.anibeat.app.ui.ListScreen;
import com.anibeat.app.ui.MiniPlayerView;
import com.anibeat.app.ui.NowPlayingView;
import com.anibeat.app.ui.Screen;
import com.anibeat.app.ui.Sheets;
import com.anibeat.app.ui.screens.AnimeScreen;
import com.anibeat.app.ui.screens.ArtistScreen;
import com.anibeat.app.ui.screens.BrowseScreen;
import com.anibeat.app.ui.screens.HomeScreen;
import com.anibeat.app.ui.screens.LibraryScreen;
import com.anibeat.app.ui.screens.PlaylistScreen;
import com.anibeat.app.ui.screens.SearchScreen;
import com.anibeat.app.ui.screens.TracksScreen;
import com.anibeat.app.ui.screens.YearScreen;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Единый экран приложения: четыре вкладки, мини-плеер, полноэкранный плеер и панели.
 * Код полностью на Java, интерфейс — Material 3.
 */
public class MainActivity extends AppCompatActivity implements Host {

    private FrameLayout root;
    private FrameLayout content;
    private MiniPlayerView mini;
    private NowPlayingView nowPlaying;
    private BottomNavigationView nav;

    private final List<Screen> stack = new ArrayList<>();
    private final Screen[] tabs = new Screen[4];
    private int tabIndex;
    private int insetTop;
    private int insetBottom;
    private boolean miniVisible;
    private boolean started;

    private String shownTrackId = "";
    private boolean navSync;

    private final Player.Listener playerListener = () -> Ui.postSafe(() -> {
        updateBars(true);
        if (mini != null) mini.refresh();
        if (nowPlaying != null && nowPlaying.isOpen()) nowPlaying.refresh();
        Models.Track now = Player.current();
        String id = now == null ? "" : now.id;
        if (id.equals(shownTrackId)) return;
        shownTrackId = id;
        Screen current = currentScreen();
        if (current instanceof ListScreen) ((ListScreen) current).refreshPlaying();
    });

    private final android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            try {
                if (mini != null && mini.getVisibility() == View.VISIBLE) mini.tick();
                if (nowPlaying != null && nowPlaying.isOpen()) nowPlaying.tick();
            } catch (Throwable t) {
                Ui.report(t);
            }
            ticker.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            boot();
        } catch (Throwable t) {
            Ui.report(t);
            emergency(t);
        }
    }

    private void boot() {
        Ui.attach(this);
        Ui.safe(() -> Prefs.init(this));
        Ui.safe(() -> Net.init(this));
        Ui.safe(() -> Img.init(this));
        Ui.safe(() -> Settings.init(this));
        Ui.safe(() -> Img.setDataSaver(Settings.dataSaver));
        Ui.safe(Library::init);
        Ui.safe(() -> Downloads.init(this));
        Ui.safe(() -> Player.init(this));
        Player.setAutoConnect(true);
        Player.addListener(playerListener);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mini = new MiniPlayerView(this);
        mini.setVisibility(View.GONE);
        FrameLayout.LayoutParams miniParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, MiniPlayerView.HEIGHT_DP));
        miniParams.gravity = Gravity.BOTTOM;
        miniParams.leftMargin = Theme.dp(this, 10);
        miniParams.rightMargin = Theme.dp(this, 10);
        root.addView(mini, miniParams);

        nav = buildNav();
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.gravity = Gravity.BOTTOM;
        root.addView(nav, navParams);

        nowPlaying = new NowPlayingView(this);
        root.addView(nowPlaying, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            insetTop = bars.top;
            insetBottom = bars.bottom;
            content.setPadding(0, insetTop, 0, 0);
            nav.setPadding(0, 0, 0, insetBottom);
            nowPlaying.setPadding(0, insetTop, 0, insetBottom);
            updateBars(true);
            return insets;
        });

        setContentView(root);
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        showTab(0, false);
        askForNotifications();
        started = true;
    }

    private BottomNavigationView buildNav() {
        BottomNavigationView view = new BottomNavigationView(this);
        view.setBackground(Theme.navBackground(this));
        view.setElevation(0f);
        ColorStateList colors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Theme.ACCENT, Theme.ON_VARIANT});
        view.setItemIconTintList(colors);
        view.setItemTextColor(colors);
        try {
            view.setItemActiveIndicatorEnabled(true);
            view.setItemActiveIndicatorColor(ColorStateList.valueOf(Theme.ACCENT_CONTAINER));
            view.setItemActiveIndicatorWidth(Theme.dp(this, 64));
            view.setItemActiveIndicatorHeight(Theme.dp(this, 32));
            view.setItemActiveIndicatorShapeAppearance(
                    com.google.android.material.shape.ShapeAppearanceModel.builder()
                            .setAllCornerSizes(Theme.dpF(this, 16))
                            .build());
        } catch (Throwable t) {
            Ui.report(t);
        }
        view.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        Menu menu = view.getMenu();
        menu.add(0, 1, 0, "Главная").setIcon(R.drawable.ic_home);
        menu.add(0, 2, 1, "Поиск").setIcon(R.drawable.ic_search);
        menu.add(0, 3, 2, "Обзор").setIcon(R.drawable.ic_explore);
        menu.add(0, 4, 3, "Медиатека").setIcon(R.drawable.ic_library_music);
        view.setOnItemSelectedListener(item -> {
            // Синхронизация выделения из кода не должна запускать переключение повторно.
            if (navSync) return true;
            showTab(item.getItemId() - 1, true);
            return true;
        });
        view.setSelectedItemId(1);
        return view;
    }

    private void askForNotifications() {
        Ui.safe(() -> {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 900);
            }
        });
    }

    /** Аварийный экран: приложение открыто и видно, что произошло. */
    private void emergency(Throwable error) {
        try {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setBackgroundColor(Color.BLACK);
            column.setPadding(Theme.dp(this, 22), Theme.dp(this, 90), Theme.dp(this, 22), Theme.dp(this, 22));
            TextView title = new TextView(this);
            title.setText("Не удалось открыть приложение");
            title.setTextColor(Color.WHITE);
            title.setTextSize(19f);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            TextView message = new TextView(this);
            message.setText(AniBeatApp.describe(error));
            message.setTextColor(0xFF9A9AA2);
            message.setTextSize(12f);
            message.setTextIsSelectable(true);
            TextView retry = new TextView(this);
            retry.setText("Открыть заново");
            retry.setTextColor(Color.BLACK);
            retry.setTextSize(15f);
            retry.setGravity(Gravity.CENTER);
            retry.setBackground(Ui.rounded(this, Color.WHITE, 12f));
            retry.setPadding(Theme.dp(this, 18), Theme.dp(this, 12), Theme.dp(this, 18), Theme.dp(this, 12));
            retry.setOnClickListener(v -> recreate());
            column.addView(title);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.topMargin = Theme.dp(this, 12);
            column.addView(message, mp);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = Theme.dp(this, 20);
            column.addView(retry, rp);
            setContentView(column);
        } catch (Throwable ignored) {
        }
    }

    /* ----------------------------- вкладки -------------------------------- */

    public void showTab(int index, boolean animate) {
        try {
            showTabSafe(index, animate);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    public void showTab(int index) {
        showTab(index, true);
    }

    private void showTabSafe(int index, boolean animate) {
        if (index < 0 || index >= 4) return;
        boolean sameTab = index == tabIndex && stack.isEmpty() && tabs[index] != null
                && tabs[index].view().getVisibility() == View.VISIBLE;
        tabIndex = index;
        for (Screen screen : stack) {
            content.removeView(screen.view());
            screen.release();
        }
        stack.clear();
        if (tabs[index] == null) {
            switch (index) {
                case 0:
                    tabs[index] = new HomeScreen(this, this);
                    break;
                case 1:
                    tabs[index] = new SearchScreen(this, this);
                    break;
                case 2:
                    tabs[index] = new BrowseScreen(this, this);
                    break;
                default:
                    tabs[index] = new LibraryScreen(this, this);
                    break;
            }
        }
        configureTopBar(tabs[index], index);
        setContent(tabs[index], animate && !sameTab);
        if (nav != null && nav.getSelectedItemId() != index + 1) {
            navSync = true;
            try {
                nav.setSelectedItemId(index + 1);
            } finally {
                navSync = false;
            }
        }
        updateBars(true);
    }

    /** Верхняя панель вкладки: название и полезные действия. */
    private void configureTopBar(Screen screen, int index) {
        Ui.safe(() -> {
            if (!(screen instanceof ListScreen)) return;
            ListScreen list = (ListScreen) screen;
            String title = screen.title();
            if (title != null && !title.isEmpty()) list.setTopTitle(title);
            list.clearActions();
            if (index == 0) {
                list.addAction(R.drawable.ic_search, "Поиск", () -> showTab(1, true));
                list.addAction(R.drawable.ic_settings, "Настройки", () -> Sheets.settings(this));
            } else if (index == 2) {
                list.addAction(R.drawable.ic_search, "Поиск", () -> showTab(1, true));
                list.addAction(R.drawable.ic_settings, "Настройки", () -> Sheets.settings(this));
            } else if (index == 3) {
                list.addAction(R.drawable.ic_settings, "Настройки", () -> Sheets.settings(this));
            }
        });
    }

    /** Спрятать лишние экраны и вернуть им нормальный вид (иначе остаются прозрачными = чёрный экран). */
    private void hideExcept(View keep) {
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child == keep) continue;
            child.animate().cancel();
            child.setAlpha(1f);
            child.setTranslationX(0f);
            child.setVisibility(View.GONE);
        }
    }

    private void setContent(Screen screen, boolean animate) {
        final View view = screen.view();
        if (view.getParent() == null) content.addView(view);
        hideExcept(view);
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        if (animate) {
            view.setAlpha(0f);
            view.setTranslationX(Theme.dp(this, 18));
            view.animate().alpha(1f).translationX(0f)
                    .setDuration(Theme.DUR_FAST).setInterpolator(Theme.EASE_OUT)
                    .withEndAction(() -> {
                        view.setAlpha(1f);
                        view.setTranslationX(0f);
                    })
                    .start();
        }
        screen.onShow();
        applyPadding(view);
    }

    private void applyPadding(View view) {
        if (view instanceof ListScreen) {
            int bottom = Theme.dp(this, MiniPlayerView.HEIGHT_DP + 84) + insetBottom + (miniVisible ? Theme.dp(this, 8) : 0);
            ((ListScreen) view).setContentPadding(insetTop, bottom);
        }
    }

    public void push(Screen screen, boolean animate) {
        try {
            pushSafe(screen, animate);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void pushSafe(Screen screen, boolean animate) {
        Screen current = currentScreen();
        if (current != null && current != screen) current.onHide();
        // В стеке лежат именно открытые поверх вкладок экраны — возврат работает по порядку.
        if (!stack.contains(screen)) stack.add(screen);
        View view = screen.view();
        if (screen instanceof ListScreen) {
            ((ListScreen) screen).setBackAction(this::pop);
            String title = screen.title();
            if (title != null && !title.isEmpty()) ((ListScreen) screen).setTopTitle(title);
        }
        content.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hideExcept(view);
        view.setVisibility(View.VISIBLE);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        if (animate) {
            view.setTranslationX(Theme.dp(this, 26));
            view.animate().translationX(0f).setDuration(Theme.DUR_SEGMENT).setInterpolator(Theme.EASE_OUT)
                    .withEndAction(() -> view.setTranslationX(0f));
        }
        screen.onShow();
        applyPadding(view);
        updateBars(true);
    }

    public void pop() {
        try {
            if (stack.isEmpty()) return;
            final Screen leaving = stack.remove(stack.size() - 1);
            Screen back = currentScreen();
            if (back == null) return;
            final View leavingView = leaving.view();
            View backView = back.view();
            if (backView.getParent() == null) content.addView(backView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            hideExcept(backView);
            backView.setAlpha(1f);
            backView.setTranslationX(0f);
            backView.setVisibility(View.VISIBLE);
            back.onShow();
            if (leavingView != null) {
                final View gone = leavingView;
                gone.animate().translationX(Theme.dp(this, 26)).alpha(0f)
                        .setDuration(Theme.DUR_FAST)
                        .withEndAction(() -> Ui.safe(() -> {
                            content.removeView(gone);
                            gone.setAlpha(1f);
                            gone.setTranslationX(0f);
                            leaving.release();
                        }))
                        .start();
            }
            applyPadding(backView);
            updateBars(true);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    /** Экран, который сейчас видно: верхний открытый или вкладка. */
    public Screen currentScreen() {
        if (!stack.isEmpty()) return stack.get(stack.size() - 1);
        return tabs[tabIndex];
    }

    /** Сколько экранов открыто поверх вкладки. */
    public int stackDepth() {
        return stack.size();
    }

    public Screen screenAt(int index) {
        return index >= 0 && index < tabs.length ? tabs[index] : null;
    }

    public com.anibeat.app.ui.NowPlayingView nowPlaying() {
        return nowPlaying;
    }

    public MiniPlayerView miniPlayer() {
        return mini;
    }

    /* ------------------------------ полосы ------------------------------- */

    public void updateBars(boolean force) {
        Ui.safe(() -> {
            boolean visible = Player.current() != null;
            if (visible != miniVisible || force) {
                miniVisible = visible;
                mini.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mini.getLayoutParams();
            int bottom = Theme.dp(this, 72) + insetBottom;
            if (params.bottomMargin != bottom) {
                params.bottomMargin = bottom;
                mini.setLayoutParams(params);
            }
            View current = content.getChildCount() > 0 ? content.getChildAt(content.getChildCount() - 1) : null;
            for (int i = 0; i < content.getChildCount(); i++) {
                if (content.getChildAt(i).getVisibility() == View.VISIBLE) current = content.getChildAt(i);
            }
            if (current != null) applyPadding(current);
        });
    }

    /* ------------------------------- цикл -------------------------------- */

    @Override
    protected void onResume() {
        super.onResume();
        ticker.removeCallbacks(tickTask);
        ticker.postDelayed(tickTask, 1000);
        Ui.postSafe(() -> {
            if (mini != null) mini.refresh();
            if (nowPlaying != null && nowPlaying.isOpen()) nowPlaying.refresh();
            updateBars(true);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticker.removeCallbacks(tickTask);
    }

    @Override
    protected void onDestroy() {
        try {
            ticker.removeCallbacks(tickTask);
            Player.removeListener(playerListener);
            for (Screen screen : stack) screen.release();
            stack.clear();
            for (Screen screen : tabs) if (screen != null) screen.release();
        } catch (Throwable t) {
            Ui.report(t);
        }
        super.onDestroy();
    }

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            try {
                if (nowPlaying != null && nowPlaying.isOpen()) {
                    nowPlaying.close();
                    return;
                }
                if (!stack.isEmpty()) {
                    pop();
                    return;
                }
                if (tabIndex != 0) {
                    showTab(0, false);
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            } catch (Throwable t) {
                Ui.report(t);
            }
        }
    };

    /* ------------------------------- Host -------------------------------- */

    @Override
    public android.app.Activity activity() {
        return this;
    }

    @Override
    public void playTrack(Models.Track track, List<Models.Track> list, int index) {
        if (track == null) return;
        try {
            List<Models.Track> queue = list == null || list.isEmpty() ? java.util.Collections.singletonList(track) : list;
            int start = Math.max(0, Math.min(index, queue.size() - 1));
            for (Models.Track item : queue) {
                if (item != null && item.audioUrl != null && !item.audioUrl.isEmpty()) continue;
                com.anibeat.app.data.Api.attachAudio(item);
            }
            Player.play(queue, start);
            updateBars(true);
            if (mini != null) mini.refresh();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    @Override
    public void enqueue(Models.Track track, boolean next) {
        if (next) Player.playNext(track);
        else Player.enqueue(track);
        updateBars(true);
    }

    @Override
    public void trackMenu(Models.Track track, View anchor) {
        Sheets.trackMenu(this, track, anchor);
    }

    @Override
    public void openAnime(Models.AnimeRef anime) {
        if (anime == null) return;
        if (anime.slug == null || anime.slug.isEmpty()) {
            toast("Не удалось определить аниме");
            return;
        }
        push(new AnimeScreen(this, this, anime.slug), true);
    }

    @Override
    public void openArtist(Models.ArtistRef artist) {
        if (artist == null) return;
        if (artist.slug == null || artist.slug.isEmpty()) {
            toast("Не удалось определить исполнителя");
            return;
        }
        push(new ArtistScreen(this, this, artist.slug), true);
    }

    @Override
    public void openMix(final Models.Mix mix) {
        if (mix == null) return;
        push(new TracksScreen(this, this, mix.title, "Подборка: " + mix.subtitle, (refresh, sink) -> {
            List<String> slugs = mix.slugs == null ? new ArrayList<>() : Arrays.asList(mix.slugs);
            com.anibeat.app.data.Api.getTracksForAnimeSlugsProgressive(slugs, 2,
                    (partial, error) -> {
                        if (partial != null && !partial.isEmpty()) sink.tracks(partial, null);
                    },
                    (all, error) -> {
                        if (all != null && !all.isEmpty()) sink.tracks(all, null);
                        else sink.tracks(null, error == null ? "В подборке нет треков" : error);
                    });
        }), true);
    }

    /** Треки подборки без ссылки на звук догружаем по одной теме — иначе кнопка не работает. */
    public static void ensureAudio(final Models.Track track) {
        if (track == null || (track.audioUrl != null && !track.audioUrl.isEmpty())) return;
        com.anibeat.app.data.Api.attachAudio(track);
    }

    @Override
    public void openPlaylist(Models.Playlist playlist) {
        if (playlist == null) return;
        push(new PlaylistScreen(this, this, playlist.id), true);
    }

    @Override
    public void openYear(int year) {
        push(new YearScreen(this, this, year), true);
    }

    @Override
    public void openGenre(final String genreId, final String title) {
        push(new TracksScreen(this, this, title, "Треки жанра", (refresh, sink) ->
                com.anibeat.app.data.Api.getFreshTracks("all", 60, (tracks, error) -> {
                    if (error != null) {
                        sink.tracks(null, error);
                        return;
                    }
                    List<Models.Track> filtered = com.anibeat.app.ui.Genres.filter(tracks, genreId);
                    sink.tracks(filtered.isEmpty() ? tracks : filtered, null);
                })), true);
    }

    @Override
    public void openTab(int index) {
        showTab(index, true);
    }

    @Override
    public void openLibraryTab(String tab) {
        showTab(3, true);
        Screen screen = tabs[3];
        if (screen instanceof LibraryScreen) {
            ((LibraryScreen) screen).showTab(tab);
            ((LibraryScreen) screen).toTop();
        }
    }

    public void pushScreen(Screen screen) {
        push(screen, true);
    }

    @Override
    public void popScreen() {
        pop();
    }

    @Override
    public void openNowPlaying() {
        if (nowPlaying != null) nowPlaying.open();
    }

    @Override
    public void toast(String message) {
        Ui.toast(this, message);
    }

    public boolean isStarted() {
        return started;
    }
}
