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
import android.view.WindowInsets;
import android.widget.FrameLayout;


import com.anibeat.app.core.Image;
import com.anibeat.app.core.Net;
import com.anibeat.app.core.Prefs;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.ui.ScreenBase;
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
    private final com.anibeat.app.player.Player.Listener playerListener = () -> runOnUiThread(() -> Ui.safe(this::updateBars));
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Ui.attach(this, this);
        // Ни один сбой запуска не должен закрывать приложение.
        Ui.safe(() -> Prefs.init(this));
        Ui.safe(() -> Net.init(this));
        Ui.safe(() -> Image.init(this));
        Ui.safe(() -> Settings.init(this));
        Ui.safe(Library::init);
        Ui.safe(() -> Downloads.init(this));
        Ui.safe(() -> Player.init(this));

        Window window = getWindow();
        Ui.safe(() -> {
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.setSystemBarsAppearance(0,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                }
            } else {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
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

        // Отступы под строку состояния и навигацию — системный слушатель вставок.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
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
        try {
            showTabSafe(index, animate);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void showTabSafe(int index, boolean animate) {
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
        try {
            pushSafe(screen, animate);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void pushSafe(Screen screen, boolean animate) {
        Screen current = currentScreen();
        if (current != null) {
            current.onHide();
            stack.add(current);
        }
        setContent(screen, animate);
    }

    /** Экран вкладки (нужно для проверки интерфейса). */
    public Screen screenAt(int index) {
        return index >= 0 && index < tabs.length ? tabs[index] : null;
    }

    private Screen currentScreen() {
        if (!stack.isEmpty()) return stack.get(stack.size() - 1);
        return tabs[tabIndex];
    }

    private void setContent(Screen screen, boolean animate) {
        try {
            setContentSafe(screen, animate);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void setContentSafe(Screen screen, boolean animate) {
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
        try {
            updateBarsSafe(force);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private void updateBarsSafe(boolean force) {
        boolean mini = Player.current() != null && !nowPlaying.isOpen();
        miniPlayer.getView().setVisibility(mini ? View.VISIBLE : View.GONE);
        if ((mini != miniVisible || force) && currentView != null) {
            miniVisible = mini;
            int bottom = Nav.BAR_HEIGHT_DP + 8 + (mini ? MiniPlayer.HEIGHT_DP + 8 : 0);
            currentView.setPadding(0, 0, 0, Theme.dp(this, bottom));
        }
        if (mini) {
            miniPlayer.resumeTick();
            miniPlayer.refresh();
        } else {
            miniPlayer.pauseTick();
        }
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
    protected void onDestroy() {
        // Закрываем окно: снимаем слушателей и таймеры, чтобы ничего не осталось висеть.
        Player.removeListener(playerListener);
        for (Screen screen : tabs) if (screen != null && screen instanceof ScreenBase) ((ScreenBase) screen).release();
        for (Screen screen : stack) if (screen instanceof ScreenBase) ((ScreenBase) screen).release();
        sheets.release();
        nowPlaying.release();
        miniPlayer.release();
        toaster.release();
        super.onDestroy();
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
