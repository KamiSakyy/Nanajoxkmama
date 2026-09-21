package com.anibeat.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.anibeat.app.core.Image;
import com.anibeat.app.core.Net;
import com.anibeat.app.core.Prefs;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.MiniPlayer;
import com.anibeat.app.ui.Nav;
import com.anibeat.app.ui.NowPlaying;
import com.anibeat.app.ui.Sheets;
import com.anibeat.app.ui.Toaster;
import com.anibeat.app.ui.screens.AnimeScreen;
import com.anibeat.app.ui.screens.ArtistScreen;
import com.anibeat.app.ui.screens.BrowseScreen;
import com.anibeat.app.ui.screens.HomeScreen;
import com.anibeat.app.ui.screens.LibraryScreen;
import com.anibeat.app.ui.screens.PlaylistScreen;
import com.anibeat.app.ui.screens.SearchScreen;
import com.anibeat.app.ui.screens.YearScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * AniBeat — полностью нативное приложение (Java + Android SDK).
 * Ни одной строки веба: экраны, анимации и логика перенесены с сайта
 * на системные View, Media3 (ExoPlayer) и собственный API-слой.
 */
public class MainActivity extends Activity {

    public interface Screen {
        View view();

        default void onShow() {
        }

        default void onHide() {
        }

        default String title() {
            return "";
        }
    }

    private FrameLayout root;
    private FrameLayout content;
    /** Мини-плеер и отступы контента пересчитываются при любом изменении плеера. */
    private final com.anibeat.app.player.Player.Listener playerListener = () -> runOnUiThread(this::updateBars);
    private Nav nav;
    private MiniPlayer miniPlayer;
    private NowPlaying nowPlaying;
    private Sheets sheets;
    private Toaster toaster;

    private final List<Screen> stack = new ArrayList<>();
    private final Screen[] tabs = new Screen[4];
    private int tabIndex;
    private View currentView;
    private boolean miniVisible;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ui.attach(this, this);
        // Ни один сбой запуска не должен закрывать приложение.
        Ui.safe(() -> Prefs.init(this));
        Ui.safe(() -> Net.init(this));
        Ui.safe(() -> Image.init(this));
        Ui.safe(Settings::init);
        Ui.safe(Library::init);
        Ui.safe(() -> Downloads.init(this));
        Ui.safe(() -> Player.init(this));

        Window window = getWindow();
        Ui.safe(() -> {
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
            WindowCompat.setDecorFitsSystemWindows(window, false);
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                controller.setAppearanceLightStatusBars(false);
                controller.setAppearanceLightNavigationBars(false);
            }
        });

        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        nav = new Nav(this);
        toaster = new Toaster(this);
        miniPlayer = new MiniPlayer(this);
        sheets = new Sheets(this);
        nowPlaying = new NowPlaying(this);

        FrameLayout.LayoutParams miniParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        miniParams.gravity = android.view.Gravity.BOTTOM;
        miniParams.bottomMargin = Theme.dp(this, Nav.BAR_HEIGHT_DP + 6);
        root.addView(miniPlayer, miniParams);
        root.addView(nav);
        root.addView(nowPlaying, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(sheets, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(toaster, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            WindowInsetsCompat bars = insets;
            int top = bars.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottom = bars.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        setContentView(root);

        Player.addListener(playerListener);
        showTab(0, false);
        requestNotificationPermissionIfNeeded();
    }

    /* ------------------------------------------------------------------ */
    /* Навигация                                                           */
    /* ------------------------------------------------------------------ */

    public void showTab(int index) {
        showTab(index, true);
    }

    public void showTab(int index, boolean animate) {
        if (index < 0 || index >= 4) return;
        tabIndex = index;
        stack.clear();
        if (tabs[index] == null) {
            switch (index) {
                case 0:
                    tabs[index] = new HomeScreen(this);
                    break;
                case 1:
                    tabs[index] = new SearchScreen(this);
                    break;
                case 2:
                    tabs[index] = new com.anibeat.app.ui.screens.BrowseScreen(this);
                    break;
                default:
                    tabs[index] = new LibraryScreen(this);
                    break;
            }
        }
        setContent(tabs[index], animate);
        nav.setActive(index);
    }

    public void push(Screen screen, boolean animate) {
        Screen current = currentScreen();
        if (current != null) {
            current.onHide();
            stack.add(current);
        }
        setContent(screen, animate);
    }

    private Screen currentScreen() {
        if (!stack.isEmpty()) return stack.get(stack.size() - 1);
        return tabs[tabIndex];
    }

    private void setContent(Screen screen, boolean animate) {
        View view = screen.view();
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        currentView = view;
        miniVisible = false;
        view.setPadding(0, 0, 0, Theme.dp(this, Nav.BAR_HEIGHT_DP + 8));
        if (animate) {
            view.setAlpha(0f);
            view.setTranslationY(Theme.dpF(this, 12f));
            view.animate().alpha(1f).translationY(0f).setDuration(Theme.DUR).setInterpolator(Theme.EASE_OUT).start();
        }
        screen.onShow();
        updateBars();
    }

    public void pop() {
        if (stack.isEmpty()) {
            showTab(0, true);
            return;
        }
        Screen leaving = currentScreen();
        if (leaving != null) leaving.onHide();
        stack.remove(stack.size() - 1);
        Screen screen = currentScreen();
        if (screen != null) setContent(screen, true);
    }

    public void open(Screen screen) {
        push(screen, true);
    }

    /** Медиатека на конкретной вкладке (site: /library?tab=downloads|history). */
    public void showLibraryTab(int tab) {
        showTab(3, true);
        Screen screen = tabs[3];
        if (screen instanceof com.anibeat.app.ui.screens.LibraryScreen) {
            ((com.anibeat.app.ui.screens.LibraryScreen) screen).openTab(tab);
        }
    }

    public void openAnime(String slug) {
        if (slug == null || slug.isEmpty()) return;
        push(new AnimeScreen(this, slug), true);
    }

    public void openArtist(String slug) {
        if (slug == null || slug.isEmpty()) return;
        push(new ArtistScreen(this, slug), true);
    }

    public void openYear(int year) {
        push(new YearScreen(this, year), true);
    }

    public void openPlaylist(String id) {
        push(new PlaylistScreen(this, id), true);
    }

    public void showBrowse(String type) {
        showTab(2, true);
        if (tabs[2] instanceof BrowseScreen) ((BrowseScreen) tabs[2]).setType(type);
    }

    public void updateBars() {
        updateBars(false);
    }

    public void updateBars(boolean force) {
        boolean mini = Player.current() != null && !nowPlaying.isOpen();
        miniPlayer.getView().setVisibility(mini ? View.VISIBLE : View.GONE);
        if ((mini != miniVisible || force) && currentView != null) {
            miniVisible = mini;
            int bottom = Nav.BAR_HEIGHT_DP + 8 + (mini ? MiniPlayer.HEIGHT_DP + 8 : 0);
            currentView.setPadding(0, 0, 0, Theme.dp(this, bottom));
        }
        miniPlayer.refresh();
        toaster.bringToFront();
        sheets.bringToFront();
    }

    public Sheets sheets() {
        return sheets;
    }

    public Toaster toaster() {
        return toaster;
    }

    public NowPlaying nowPlaying() {
        return nowPlaying;
    }

    public Nav nav() {
        return nav;
    }

    public int tabIndex() {
        return tabIndex;
    }

    public boolean hasStack() {
        return !stack.isEmpty();
    }

    /* ------------------------------------------------------------------ */

    @Override
    public void onBackPressed() {
        if (nowPlaying.isOpen()) {
            nowPlaying.close();
            return;
        }
        if (sheets.isOpen()) {
            sheets.close();
            return;
        }
        if (!stack.isEmpty()) {
            pop();
            return;
        }
        if (tabIndex != 0) {
            showTab(0, true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBars();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        try {
            return super.dispatchTouchEvent(event);
        } catch (Throwable t) {
            // Ошибка внутри обработчика касания не должна закрывать приложение.
            Ui.report(t);
            return true;
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        try {
            return super.dispatchKeyEvent(event);
        } catch (Throwable t) {
            Ui.report(t);
            return true;
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
    }
}
