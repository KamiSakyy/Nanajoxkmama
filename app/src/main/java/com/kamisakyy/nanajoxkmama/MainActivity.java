package com.kamisakyy.nanajoxkmama;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shell: bottom navigation, mini player, toasts, detail stack and the
 * fullscreen Now Playing sheet. Zero dependencies — pure Material black UI.
 */
public final class MainActivity extends Activity implements Ui.Host {
    private FrameLayout shell;
    private FrameLayout content;
    private LinearLayout miniPlayer;
    private Ui.CoverView miniCover;
    private TextView miniTitle;
    private TextView miniSubtitle;
    private ImageView miniToggle;
    private View miniProgressBg;
    private View miniProgress;
    private LinearLayout bottomNav;
    private NowPlayingView nowPlaying;
    private FrameLayout toastContainer;

    private HomeScreen home;
    private SearchScreen search;
    private BrowseScreen browse;
    private LibraryScreen library;
    private int tab = -1;
    private final List<Screen> stack = new ArrayList<>();

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                if (intent != null && intent.getBooleanExtra("error", false)) {
                    toast("Не удалось воспроизвести трек");
                }
                Ui.refreshPlayingRows();
                refreshMiniPlayer();
                if (nowPlaying != null && nowPlaying.isOpen()) nowPlaying.refresh();
            } catch (Throwable ignored) { }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Store.init(this);
        Downloader.init(this);
        HttpCache.init(getCacheDir());
        MetaApi.init(getFilesDir());
        Window w = getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.BG);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildShell();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        }
        if (getIntent() != null && "anibeat.OPEN_PLAYER".equals(getIntent().getAction())) {
            main.postDelayed(this::openNowPlaying, 350);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(PlaybackService.BROADCAST_STATE);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(playbackReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(playbackReceiver, filter);
            receiverRegistered = true;
        }
        refreshMiniPlayer();
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(playbackReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override public void onBackPressed() {
        if (nowPlaying.isOpen()) {
            nowPlaying.close();
            return;
        }
        if (!stack.isEmpty()) {
            popDetail();
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    /* ---------------- shell ---------------- */

    private void buildShell() {
        shell = new FrameLayout(this);
        shell.setBackgroundColor(Ui.BG);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        column.addView(buildMiniPlayer());
        column.addView(buildBottomNav());
        shell.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        nowPlaying = new NowPlayingView(this);
        shell.addView(nowPlaying, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        toastContainer = new FrameLayout(this);
        toastContainer.setClipToPadding(false);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        shell.addView(toastContainer, tlp);

        setContentView(shell);
        switchTab(0);
    }

    private View buildMiniPlayer() {
        miniPlayer = new LinearLayout(this);
        miniPlayer.setOrientation(LinearLayout.VERTICAL);
        miniPlayer.setVisibility(View.GONE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.ripple(Ui.rounded(Ui.S2, 14)));
        card.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(4), Ui.dp(8));
        miniCover = Ui.cover(this, 42, 9);
        card.addView(miniCover);
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.leftMargin = Ui.dp(10);
        miniTitle = Ui.text(this, "", 14f, Ui.ON, true);
        miniSubtitle = Ui.text(this, "", 12f, Ui.VAR, false);
        mid.addView(miniTitle);
        mid.addView(miniSubtitle);
        card.addView(mid, mp);
        miniToggle = Ui.icon(this, "play_arrow", 24, Ui.ON);
        FrameLayout toggleBtn = new FrameLayout(this);
        toggleBtn.setBackground(Ui.ripple(Ui.oval(Color.TRANSPARENT)));
        toggleBtn.addView(miniToggle, new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
        toggleBtn.setOnClickListener(v -> {
            try { PlaybackService.command(this, PlaybackService.ACTION_TOGGLE); } catch (Throwable ignored) { }
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.dp(42), Ui.dp(42));
        card.addView(toggleBtn, tp);
        View nextBtn = Ui.iconBtn(this, "skip_next", 22, Ui.ON,
                v -> PlaybackService.command(this, PlaybackService.ACTION_NEXT));
        card.addView(nextBtn);
        card.setOnClickListener(v -> openNowPlaying());
        Ui.tapScale(card);

        // progress hairline
        FrameLayout progressWrap = new FrameLayout(this);
        miniProgressBg = new View(this);
        miniProgressBg.setBackgroundColor(0x1AFFFFFF);
        miniProgress = new View(this);
        miniProgress.setBackgroundColor(0xB3FFFFFF);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(30), Ui.dp(2), Gravity.BOTTOM | Gravity.START);
        progressWrap.addView(miniProgressBg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(2), Gravity.BOTTOM));
        progressWrap.addView(miniProgress, plp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(4));
        miniPlayer.addView(card, cardLp);

        LinearLayout barWrap = new LinearLayout(this);
        barWrap.setPadding(Ui.dp(16), 0, Ui.dp(16), Ui.dp(6));
        barWrap.addView(progressWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(2)));
        miniPlayer.addView(barWrap);
        miniPlayer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return miniPlayer;
    }

    private View buildBottomNav() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View hairline = new View(this);
        hairline.setBackgroundColor(Ui.SEPARATOR);
        wrap.addView(hairline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(0.5f)));
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor(Ui.BG);
        String[] labels = {"Главная", "Поиск", "Обзор", "Медиатека"};
        String[] icons = {"home_outline", "search", "explore_outline", "library_music_outline"};
        String[] iconsActive = {"home", "search", "explore", "library_music"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
            ImageView icon = Ui.icon(this, icons[i], 24, Ui.DIM);
            TextView label = Ui.text(this, labels[i], 10, Ui.DIM, true);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(3);
            item.addView(icon);
            item.addView(label, lp);
            item.setOnClickListener(v -> switchTab(idx));
            Ui.tapScale(item);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            item.setTag(new Object[]{icons[i], iconsActive[i], icon, label});
            bottomNav.addView(item, ip);
        }
        wrap.addView(bottomNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private void refreshNav() {
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) bottomNav.getChildAt(i);
            Object[] tag = (Object[]) item.getTag();
            String icon = (String) tag[0];
            String iconActive = (String) tag[1];
            ImageView iconView = (ImageView) tag[2];
            TextView label = (TextView) tag[3];
            boolean active = i == tab && stack.isEmpty();
            iconView.setImageResource(Ui.drawableId(this, active ? iconActive : icon));
            iconView.setColorFilter(active ? Ui.ON : Ui.DIM);
            label.setTextColor(active ? Ui.ON : Ui.DIM);
        }
    }

    /* ---------------- navigation ---------------- */

    @Override public void switchTab(int idx) {
        stack.clear();
        tab = idx;
        Screen s;
        if (idx == 0) {
            if (home == null) home = new HomeScreen(this);
            s = home;
        } else if (idx == 1) {
            if (search == null) search = new SearchScreen(this);
            s = search;
        } else if (idx == 2) {
            if (browse == null) browse = new BrowseScreen(this);
            s = browse;
        } else {
            if (library == null) library = new LibraryScreen(this);
            s = library;
        }
        show(s);
        s.onShow();
        refreshNav();
        if (idx == 1) search.focusInput();
    }

    private void show(Screen s) {
        content.removeAllViews();
        content.addView(s.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        s.view().setAlpha(0f);
        s.view().animate().alpha(1f).setDuration(180).start();
    }

    private void pushDetail(Screen s) {
        stack.add(s);
        content.removeAllViews();
        content.addView(s.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View v = s.view();
        v.setTranslationX(Ui.dp(24));
        v.setAlpha(0f);
        v.animate().translationX(0).alpha(1f).setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.3f)).start();
        s.onShow();
        refreshNav();
    }

    private void popDetail() {
        if (stack.isEmpty()) return;
        stack.remove(stack.size() - 1);
        if (stack.isEmpty()) switchTab(tab < 0 ? 0 : tab);
        else {
            Screen s = stack.get(stack.size() - 1);
            show(s);
            s.onShow();
        }
    }

    @Override public void openAnime(String slug, String title) {
        if (slug == null || slug.isEmpty() || slug.startsWith("mal-") || slug.startsWith("ann-")) {
            toast("Страница недоступна для этого трека");
            return;
        }
        pushDetail(new DetailScreens.AnimeScreen(this, slug));
    }

    @Override public void openArtist(String slug) {
        if (slug == null || slug.isEmpty()) return;
        pushDetail(new DetailScreens.ArtistScreen(this, slug));
    }

    @Override public void openYear(int year, String season) {
        pushDetail(new BrowseScreen.YearScreen(this, year, season));
    }

    @Override public void openPlaylist(String id) {
        pushDetail(new LibraryScreen.PlaylistScreen(this, id));
    }

    @Override public void openSearch(String query) {
        switchTab(1);
        search.setQuery(query);
    }

    /* ---------------- player ---------------- */

    @Override public void playAll(List<Track> tracks, int index, boolean shuffle) {
        if (tracks == null || tracks.isEmpty()) {
            toast("Список пуст");
            return;
        }
        ArrayList<Track> queue = new ArrayList<>(tracks);
        int at = Math.max(0, Math.min(index, queue.size() - 1));
        if (shuffle && queue.size() > 1) {
            Track first = queue.remove(at);
            java.util.Collections.shuffle(queue);
            queue.add(0, first);
            at = 0;
        }
        PlaybackService.play(this, queue, at);
        refreshMiniPlayer();
    }

    @Override public void openNowPlaying() {
        nowPlaying.open();
    }

    @Override public void openTrackMenu(Track t, List<Track> context) {
        Sheets.trackMenu(this, t, context, nowPlaying);
    }

    @Override public void openPlaylistPicker(Track t) {
        Sheets.playlistPicker(this, t);
    }

    @Override public void openQueue() {
        Sheets.queueSheet(this, nowPlaying);
    }

    @Override public void openSettings() {
        Sheets.settings(this);
    }

    @Override public void runIo(Runnable r) {
        io.execute(r);
    }

    @Override public Activity activity() {
        return this;
    }

    /* ---------------- mini player ---------------- */

    private void refreshMiniPlayer() {
        Track t = Store.getCurrentTrack();
        if (t == null) {
            miniPlayer.setVisibility(View.GONE);
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);
        miniCover.load(t.coverSmall.isEmpty() ? t.cover : t.coverSmall, true);
        miniTitle.setText(t.title);
        miniSubtitle.setText(t.displayArtist());
        boolean playing = Store.isPlaying();
        miniToggle.setImageResource(Ui.drawableId(this, playing ? "pause" : "play_arrow"));
        miniToggle.setColorFilter(Ui.ON);
        long dur = Store.getDuration();
        long pos = Store.getPosition();
        int width = shell.getWidth() > 0 ? shell.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int barWidth = (int) ((width - Ui.dp(32)) * (dur > 0 ? Math.min(1f, pos / (float) dur) : 0f));
        ViewGroup.LayoutParams lp = miniProgress.getLayoutParams();
        lp.width = Math.max(Ui.dp(2), barWidth);
        miniProgress.setLayoutParams(lp);
    }

    /* ---------------- toasts ---------------- */

    @Override public void toast(String message) {
        main.post(() -> {
            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(Ui.rounded(Ui.S3, 14));
            card.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(12), Ui.dp(12));
            TextView t = Ui.text(MainActivity.this, message, 13.5f, Color.WHITE, true);
            card.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            View close = Ui.iconBtn(MainActivity.this, "close", 14, Ui.DIM, v -> toastContainer.removeView(card));
            card.addView(close);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            lp.setMargins(Ui.dp(16), 0, Ui.dp(16), Ui.dp(nowPlaying.isOpen() ? 24 : 84));
            card.setTranslationY(Ui.dp(24));
            card.setAlpha(0f);
            toastContainer.addView(card, lp);
            card.animate().translationY(0).alpha(1f).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            main.postDelayed(() -> {
                card.animate().translationY(Ui.dp(16)).alpha(0f).setDuration(200)
                        .setListener(new android.animation.AnimatorListenerAdapter() {
                            @Override public void onAnimationEnd(android.animation.Animator animation) {
                                toastContainer.removeView(card);
                            }
                        }).start();
            }, 3000);
        });
    }
}
