package com.kamisakyy.nanajoxkmama;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single-activity, SDK-only AniBeat UI. The compact custom layout deliberately avoids
 * AppCompat/Compose so the release APK stays under the 4 MB target.
 */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 11, 18);
    private static final int SURFACE = Color.rgb(24, 21, 31);
    private static final int SURFACE_ALT = Color.rgb(33, 29, 42);
    private static final int ACCENT = Color.rgb(255, 92, 138);
    private static final int TEXT = Color.rgb(247, 242, 250);
    private static final int MUTED = Color.rgb(185, 177, 194);
    private static final int DIM = Color.rgb(128, 119, 139);
    private static final int GREEN = Color.rgb(71, 214, 142);

    private enum Page { HOME, SEARCH, BROWSE, LIBRARY, DETAIL }
    private Page page = Page.HOME;
    private Page pageBeforeDetail = Page.HOME;

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Track> homeTracks = new ArrayList<>();
    private final ArrayList<Track> browseTracks = new ArrayList<>();
    private ApiClient.SearchResult searchResult;
    private String searchQuery = "";
    private String browseType = "";
    private int libraryTab;
    private boolean homeLoading;
    private boolean browseLoading;
    private boolean searchLoading;
    private boolean detailLoading;
    private boolean receiverRegistered;

    private FrameLayout content;
    private LinearLayout miniPlayer;
    private ImageView miniCover;
    private TextView miniTitle;
    private TextView miniSubtitle;
    private TextView miniToggle;
    private LinearLayout bottomNav;
    private Dialog playerDialog;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getBooleanExtra("error", false)) toast("Не удалось воспроизвести трек");
            refreshMiniPlayer();
            if (playerDialog != null && playerDialog.isShowing()) updatePlayerDialog();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Store.init(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        buildShell();
        ArrayList<Track> cache = Store.getHomeCache();
        if (!cache.isEmpty()) homeTracks.addAll(cache);
        showHome();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
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
        if (page == Page.DETAIL) {
            page = pageBeforeDetail;
            if (page == Page.HOME) showHome();
            else if (page == Page.SEARCH) showSearch();
            else if (page == Page.BROWSE) showBrowse();
            else showLibrary();
            return;
        }
        if (page != Page.HOME) {
            page = Page.HOME;
            showHome();
            return;
        }
        super.onBackPressed();
    }

    private void buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);

        content = new FrameLayout(this);
        content.setBackgroundColor(BG);
        shell.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        miniPlayer = buildMiniPlayer();
        miniPlayer.setVisibility(View.GONE);
        shell.addView(miniPlayer, new LinearLayout.LayoutParams(-1, dp(68)));

        bottomNav = buildBottomNav();
        shell.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(58)));
        setContentView(shell);
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(10, 9, 14));
        nav.setPadding(dp(4), 0, dp(4), 0);
        String[] labels = {"Главная", "Поиск", "Обзор", "Медиатека"};
        String[] symbols = {"⌂", "⌕", "◈", "♫"};
        for (int i = 0; i < labels.length; i++) {
            final int target = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(4), 0, 0);
            TextView icon = text(symbols[i], 24, TEXT, false);
            TextView label = text(labels[i], 10, MUTED, false);
            item.addView(icon, new LinearLayout.LayoutParams(-1, dp(29)));
            item.addView(label, new LinearLayout.LayoutParams(-1, dp(19)));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    page = target == 0 ? Page.HOME : target == 1 ? Page.SEARCH : target == 2 ? Page.BROWSE : Page.LIBRARY;
                    if (page == Page.HOME) showHome();
                    else if (page == Page.SEARCH) showSearch();
                    else if (page == Page.BROWSE) showBrowse();
                    else showLibrary();
                }
            });
            nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        return nav;
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(7), dp(8), dp(7));
        bar.setBackgroundColor(SURFACE_ALT);
        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bar.addView(miniCover, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(10), 0, dp(6), 0);
        miniTitle = text("AniBeat", 14, TEXT, true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniTitle.setSingleLine(true);
        miniSubtitle = text("Аниме музыка", 12, MUTED, false);
        miniSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        miniSubtitle.setSingleLine(true);
        labels.addView(miniTitle, new LinearLayout.LayoutParams(-1, dp(23)));
        labels.addView(miniSubtitle, new LinearLayout.LayoutParams(-1, dp(20)));
        bar.addView(labels, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView previous = button("‹", 25, TEXT, false);
        miniToggle = button("▶", 20, TEXT, true);
        TextView next = button("›", 25, TEXT, false);
        bar.addView(previous, new LinearLayout.LayoutParams(dp(38), dp(50)));
        bar.addView(miniToggle, new LinearLayout.LayoutParams(dp(44), dp(50)));
        bar.addView(next, new LinearLayout.LayoutParams(dp(38), dp(50)));
        previous.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_PREVIOUS); } });
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_NEXT); } });
        miniToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_TOGGLE); } });
        bar.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPlayerDialog(); } });
        return bar;
    }

    private void refreshMiniPlayer() {
        if (miniPlayer == null) return;
        final Track current = Store.getCurrentTrack();
        if (current == null) {
            miniPlayer.setVisibility(View.GONE);
            return;
        }
        miniPlayer.setVisibility(View.VISIBLE);
        miniTitle.setText(current.title);
        miniSubtitle.setText(current.displayArtist() + " · " + current.animeName);
        miniToggle.setText(Store.isPlaying() ? "Ⅱ" : "▶");
        ImageLoader.load(miniCover, current.coverSmall.isEmpty() ? current.cover : current.coverSmall, true);
    }

    private void showHome() {
        page = Page.HOME;
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar("AniBeat", false));
        TextView greeting = text(greeting(), 14, MUTED, false);
        body.addView(greeting, margin(20, 14, 20, 0));
        TextView heading = text("Аниме-музыка рядом", 27, TEXT, true);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(heading, margin(20, 3, 20, 0));
        TextView subtitle = text("Опенинги, эндинги и вставки из любимых сериалов", 14, MUTED, false);
        body.addView(subtitle, margin(20, 4, 20, 0));

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(20), dp(15), dp(20), dp(4));
        TextView random = pill("Случайная музыка", ACCENT, Color.WHITE);
        TextView refresh = pill("Обновить", SURFACE_ALT, TEXT);
        actions.addView(random, new LinearLayout.LayoutParams(0, dp(43), 1f));
        actions.addView(refresh, marginParams(10, 0, 0, 0, 0, dp(43)));
        body.addView(actions);
        random.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { loadHome(true); } });
        refresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { loadHome(false); } });

        if (!homeTracks.isEmpty()) {
            Track heroTrack = homeTracks.get(0);
            LinearLayout hero = card();
            hero.setOrientation(LinearLayout.HORIZONTAL);
            ImageView image = coverView(dp(88), dp(88));
            ImageLoader.load(image, Store.isDataSaverEnabled() ? heroTrack.coverSmall : heroTrack.cover, Store.isDataSaverEnabled());
            hero.addView(image, new LinearLayout.LayoutParams(dp(88), dp(88)));
            LinearLayout heroText = new LinearLayout(this);
            heroText.setOrientation(LinearLayout.VERTICAL);
            heroText.setGravity(Gravity.CENTER_VERTICAL);
            heroText.setPadding(dp(13), 0, dp(5), 0);
            heroText.addView(text("ТРЕК ДНЯ", 11, ACCENT, true));
            TextView ht = text(heroTrack.title, 18, TEXT, true);
            ht.setMaxLines(2);
            ht.setEllipsize(TextUtils.TruncateAt.END);
            heroText.addView(ht, margin(0, 4, 0, 0));
            TextView hs = text(heroTrack.displayArtist() + " · " + heroTrack.animeName, 12, MUTED, false);
            hs.setSingleLine(true);
            hs.setEllipsize(TextUtils.TruncateAt.END);
            heroText.addView(hs, margin(0, 3, 0, 0));
            hero.addView(heroText, new LinearLayout.LayoutParams(0, -1, 1f));
            body.addView(hero, margin(20, 14, 20, 0));
            hero.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(homeTracks, 0, true); } });
        }

        sectionHeader(body, "Новое", "Все", new View.OnClickListener() {
            @Override public void onClick(View v) { page = Page.BROWSE; browseType = ""; showBrowse(); }
        });
        if (homeLoading && homeTracks.isEmpty()) body.addView(progress(), margin(0, 28, 0, 20));
        if (!homeLoading && homeTracks.isEmpty()) body.addView(empty("Нет соединения", "Нажмите «Обновить», чтобы повторить запрос."), margin(20, 24, 20, 20));
        addTrackList(body, Store.filterMature(homeTracks), 8);

        sectionHeader(body, "Быстрый обзор", null, null);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(20), 0, dp(20), dp(8));
        TextView op = pill("Опенинги", SURFACE_ALT, TEXT);
        TextView ed = pill("Эндинги", SURFACE_ALT, TEXT);
        TextView offline = pill("Офлайн", SURFACE_ALT, TEXT);
        chips.addView(op, new LinearLayout.LayoutParams(0, dp(40), 1f));
        chips.addView(ed, marginParams(8, 0, 0, 0, 0, dp(40)));
        chips.addView(offline, marginParams(8, 0, 0, 0, 0, dp(40)));
        body.addView(chips);
        op.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { browseType = "OP"; page = Page.BROWSE; showBrowse(); } });
        ed.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { browseType = "ED"; page = Page.BROWSE; showBrowse(); } });
        offline.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { libraryTab = 2; page = Page.LIBRARY; showLibrary(); } });
        if (homeTracks.isEmpty() && !homeLoading) loadHome(false);
        setPageView(scroll);
    }

    private void loadHome(final boolean random) {
        if (homeLoading) return;
        homeLoading = true;
        if (random) homeTracks.clear();
        if (page == Page.HOME) showHome();
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    ArrayList<Track> loaded = random ? ApiClient.random(24, null) : ApiClient.latest(24, null);
                    final ArrayList<Track> filtered = Store.filterMature(loaded);
                    main.post(new Runnable() {
                        @Override public void run() {
                            homeTracks.clear();
                            homeTracks.addAll(filtered);
                            Store.saveHomeCache(homeTracks);
                            homeLoading = false;
                            if (page == Page.HOME) showHome();
                        }
                    });
                } catch (final Exception error) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            homeLoading = false;
                            if (homeTracks.isEmpty()) homeTracks.addAll(Store.getHomeCache());
                            if (page == Page.HOME) showHome();
                            toast("Каталог пока недоступен");
                        }
                    });
                }
            }
        });
    }

    private void showSearch() {
        page = Page.SEARCH;
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar("Поиск", false));
        LinearLayout searchLine = new LinearLayout(this);
        searchLine.setGravity(Gravity.CENTER_VERTICAL);
        searchLine.setPadding(dp(18), dp(16), dp(18), dp(4));
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(searchQuery);
        input.setHint("Аниме, песня или исполнитель");
        input.setTextColor(TEXT);
        input.setHintTextColor(DIM);
        input.setTextSize(15);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(14), 0, dp(10), 0);
        input.setBackground(round(SURFACE_ALT, 12));
        TextView go = pill("Найти", ACCENT, Color.WHITE);
        searchLine.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));
        searchLine.addView(go, marginParams(8, 0, 0, 0, dp(74), dp(48)));
        body.addView(searchLine);
        View.OnClickListener submit = new View.OnClickListener() {
            @Override public void onClick(View v) {
                searchQuery = input.getText().toString().trim();
                performSearch();
            }
        };
        go.setOnClickListener(submit);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchQuery = input.getText().toString().trim(); performSearch(); return true; }
                return false;
            }
        });
        if (searchLoading) body.addView(progress(), margin(0, 28, 0, 20));
        else if (searchResult == null) {
            body.addView(empty("Найдите свою тему", "AnimeThemes и AnisongDB ищут по аниме, названию и исполнителю."), margin(20, 30, 20, 0));
        } else {
            if (!searchResult.anime.isEmpty()) {
                sectionHeader(body, "Аниме", null, null);
                for (AnimeInfo a : searchResult.anime) addAnimeRow(body, a);
            }
            if (!searchResult.tracks.isEmpty()) {
                sectionHeader(body, "Треки", null, null);
                addTrackList(body, Store.filterMature(searchResult.tracks), 60);
            }
            if (!searchResult.artists.isEmpty()) {
                sectionHeader(body, "Исполнители", null, null);
                for (String artist : searchResult.artists) body.addView(text("♫  " + artist, 15, TEXT, false), margin(20, 5, 20, 5));
            }
            if (searchResult.anime.isEmpty() && searchResult.tracks.isEmpty()) body.addView(empty("Ничего не найдено", "Попробуйте другое название или латинское написание."), margin(20, 30, 20, 0));
        }
        setPageView(scroll);
    }

    private void performSearch() {
        if (searchQuery.isEmpty()) { toast("Введите запрос"); return; }
        hideKeyboard();
        searchLoading = true;
        searchResult = null;
        showSearch();
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ApiClient.SearchResult result = ApiClient.search(searchQuery);
                    main.post(new Runnable() {
                        @Override public void run() { searchResult = result; searchLoading = false; showSearch(); }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { searchLoading = false; showSearch(); toast("Не удалось выполнить поиск"); }
                    });
                }
            }
        });
    }

    private void showBrowse() {
        page = Page.BROWSE;
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar("Обзор", false));
        TextView intro = text("Слушайте темы по настроению", 22, TEXT, true);
        body.addView(intro, margin(20, 17, 20, 0));
        TextView caption = text("Новые публикации AnimeThemes с полными аудио", 14, MUTED, false);
        body.addView(caption, margin(20, 4, 20, 9));
        LinearLayout filters = new LinearLayout(this);
        filters.setPadding(dp(20), dp(4), dp(20), dp(6));
        TextView all = filterChip("Всё", browseType.isEmpty());
        TextView op = filterChip("Опенинги", "OP".equals(browseType));
        TextView ed = filterChip("Эндинги", "ED".equals(browseType));
        filters.addView(all, new LinearLayout.LayoutParams(0, dp(38), 1f));
        filters.addView(op, marginParams(7, 0, 0, 0, 0, dp(38)));
        filters.addView(ed, marginParams(7, 0, 0, 0, 0, dp(38)));
        body.addView(filters);
        all.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeBrowseType(""); } });
        op.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeBrowseType("OP"); } });
        ed.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { changeBrowseType("ED"); } });
        if (browseLoading) body.addView(progress(), margin(0, 25, 0, 20));
        else if (browseTracks.isEmpty()) body.addView(empty("Загрузка каталога", "Выберите категорию или обновите экран."), margin(20, 25, 20, 0));
        else addTrackList(body, Store.filterMature(browseTracks), 100);
        if (browseTracks.isEmpty() && !browseLoading) loadBrowse();
        setPageView(scroll);
    }

    private void changeBrowseType(String type) {
        browseType = type;
        browseTracks.clear();
        showBrowse();
    }

    private void loadBrowse() {
        if (browseLoading) return;
        browseLoading = true;
        showBrowse();
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ArrayList<Track> loaded = ApiClient.latest(40, browseType);
                    main.post(new Runnable() {
                        @Override public void run() { browseTracks.clear(); browseTracks.addAll(loaded); browseLoading = false; showBrowse(); }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { browseLoading = false; showBrowse(); toast("Каталог пока недоступен"); }
                    });
                }
            }
        });
    }

    private void showLibrary() {
        page = Page.LIBRARY;
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar("Медиатека", false));
        TextView heading = text("Ваша музыка", 26, TEXT, true);
        body.addView(heading, margin(20, 15, 20, 12));
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(16), 0, dp(16), dp(8));
        String[] labels = {"Избранное", "История", "Офлайн", "Плейлисты"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            TextView item = filterChip(labels[i], libraryTab == i);
            tabs.addView(item, new LinearLayout.LayoutParams(0, dp(39), 1f));
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { libraryTab = tab; showLibrary(); } });
        }
        body.addView(tabs);
        if (libraryTab == 0) {
            ArrayList<Track> tracks = Store.getFavorites();
            addLibraryControls(body, tracks, "Нет избранных треков", "Нажмите сердечко в меню любой темы.");
        } else if (libraryTab == 1) {
            ArrayList<Track> tracks = Store.getHistory();
            addLibraryControls(body, tracks, "История пуста", "Здесь появятся прослушанные темы.");
        } else if (libraryTab == 2) {
            ArrayList<Track> tracks = Store.getOfflineTracks();
            addLibraryControls(body, tracks, "Нет офлайн-треков", "В меню трека выберите «Сохранить офлайн».");
        } else {
            addPlaylistLibrary(body);
        }
        setPageView(scroll);
    }

    private void addLibraryControls(LinearLayout body, ArrayList<Track> tracks, String title, String subtitle) {
        if (tracks.isEmpty()) {
            body.addView(empty(title, subtitle), margin(20, 32, 20, 0));
            return;
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(20), dp(7), dp(20), dp(4));
        TextView play = pill("Слушать всё", ACCENT, Color.WHITE);
        TextView shuffle = pill("Вперемешку", SURFACE_ALT, TEXT);
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(shuffle, marginParams(8, 0, 0, 0, 0, dp(42)));
        body.addView(actions);
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(tracks, 0, true); } });
        shuffle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(shuffle(tracks), 0, true); } });
        addTrackList(body, tracks, tracks.size());
    }

    private void addPlaylistLibrary(LinearLayout body) {
        TextView add = pill("＋  Новый плейлист", ACCENT, Color.WHITE);
        body.addView(add, margin(20, 8, 20, 10));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { createPlaylistDialog(); } });
        ArrayList<Playlist> playlists = Store.getPlaylists();
        if (playlists.isEmpty()) {
            body.addView(empty("Нет плейлистов", "Соберите любимые опенинги в отдельную коллекцию."), margin(20, 22, 20, 0));
            return;
        }
        for (final Playlist playlist : playlists) {
            LinearLayout row = card();
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text("♫", 27, ACCENT, true);
            icon.setGravity(Gravity.CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(dp(12), 0, 0, 0);
            details.addView(text(playlist.name, 16, TEXT, true));
            details.addView(text(playlist.tracks.size() + " треков", 13, MUTED, false), margin(0, 3, 0, 0));
            row.addView(details, new LinearLayout.LayoutParams(0, -1, 1f));
            TextView menu = button("⋮", 23, MUTED, false);
            row.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(60)));
            body.addView(row, margin(20, 5, 20, 5));
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showPlaylist(playlist); } });
            menu.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playlistMenu(playlist); } });
        }
    }

    private void showPlaylist(final Playlist playlist) {
        pageBeforeDetail = Page.LIBRARY;
        page = Page.DETAIL;
        bottomNav.setVisibility(View.GONE);
        ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar(playlist.name, true));
        body.addView(text(playlist.tracks.size() + " треков", 14, MUTED, false), margin(20, 18, 20, 6));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(20), dp(6), dp(20), dp(7));
        TextView play = pill("Слушать", ACCENT, Color.WHITE);
        TextView rename = pill("Переименовать", SURFACE_ALT, TEXT);
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(42), 1f));
        actions.addView(rename, marginParams(8, 0, 0, 0, 0, dp(42)));
        body.addView(actions);
        play.setEnabled(!playlist.tracks.isEmpty());
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(playlist.tracks, 0, true); } });
        rename.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { renamePlaylistDialog(playlist); } });
        if (playlist.tracks.isEmpty()) body.addView(empty("Плейлист пуст", "Добавляйте треки через меню темы."), margin(20, 25, 20, 0));
        else {
            for (final Track track : playlist.tracks) {
                addTrackRow(body, track, playlist.tracks, 0);
            }
        }
        setPageView(scroll);
    }

    private void createPlaylistDialog() {
        final EditText input = new EditText(this);
        input.setHint("Название плейлиста");
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(round(SURFACE_ALT, 10));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Новый плейлист").setView(input).setNegativeButton("Отмена", null).setPositiveButton("Создать", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { Store.createPlaylist(input.getText().toString()); dialog.dismiss(); showLibrary(); }
                });
            }
        });
        dialog.show();
    }

    private void renamePlaylistDialog(final Playlist playlist) {
        final EditText input = new EditText(this);
        input.setText(playlist.name);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(round(SURFACE_ALT, 10));
        new AlertDialog.Builder(this).setTitle("Переименовать").setView(input).setNegativeButton("Отмена", null).setPositiveButton("Готово", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { Store.renamePlaylist(playlist.id, input.getText().toString()); showLibrary(); }
        }).show();
    }

    private void playlistMenu(final Playlist playlist) {
        final String[] items = {"Переименовать", "Удалить плейлист"};
        new AlertDialog.Builder(this).setTitle(playlist.name).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) renamePlaylistDialog(playlist);
                else new AlertDialog.Builder(MainActivity.this).setTitle("Удалить плейлист?").setNegativeButton("Отмена", null).setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { Store.deletePlaylist(playlist.id); showLibrary(); }
                }).show();
            }
        }).show();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(6), dp(2), dp(6), 0);
        CheckBox mature = check("Разрешить 18+ контент", Store.isMatureEnabled());
        CheckBox extra = check("Расширенная база AnisongDB", Store.isExtraSourcesEnabled());
        CheckBox saver = check("Экономия трафика и лёгкие обложки", Store.isDataSaverEnabled());
        box.addView(mature);
        box.addView(extra);
        box.addView(saver);
        TextView note = text("Видео не запускается само. Музыка работает в фоне и может быть сохранена офлайн.", 12, MUTED, false);
        box.addView(note, margin(10, 10, 10, 2));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Настройки AniBeat").setView(box).setNegativeButton("Закрыть", null).setPositiveButton("Сохранить", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Store.setMatureEnabled(mature.isChecked());
                        Store.setExtraSourcesEnabled(extra.isChecked());
                        Store.setDataSaverEnabled(saver.isChecked());
                        dialog.dismiss();
                        if (page == Page.HOME) showHome();
                        toast("Настройки сохранены");
                    }
                });
            }
        });
        dialog.show();
    }

    private void showAnime(final String slug, final String fallbackName) {
        pageBeforeDetail = page;
        page = Page.DETAIL;
        bottomNav.setVisibility(View.GONE);
        detailLoading = true;
        ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar(fallbackName == null || fallbackName.isEmpty() ? "Аниме" : fallbackName, true));
        body.addView(progress(), margin(0, 32, 0, 10));
        setPageView(scroll);
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ApiClient.AnimeResult result = ApiClient.anime(slug);
                    main.post(new Runnable() { @Override public void run() { detailLoading = false; renderAnime(result); } });
                } catch (Exception e) {
                    main.post(new Runnable() { @Override public void run() { detailLoading = false; renderDetailError("Аниме пока недоступно"); } });
                }
            }
        });
    }

    private void showArtist(final String slug, final String fallbackName) {
        pageBeforeDetail = page;
        page = Page.DETAIL;
        bottomNav.setVisibility(View.GONE);
        ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar(fallbackName, true));
        body.addView(progress(), margin(0, 32, 0, 10));
        setPageView(scroll);
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ApiClient.ArtistResult result = ApiClient.artist(slug);
                    main.post(new Runnable() { @Override public void run() { renderArtist(result); } });
                } catch (Exception e) {
                    main.post(new Runnable() { @Override public void run() { renderDetailError("Исполнитель пока недоступен"); } });
                }
            }
        });
    }

    private void renderAnime(ApiClient.AnimeResult result) {
        ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar(result.info.name, true));
        LinearLayout intro = new LinearLayout(this);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(dp(20), dp(16), dp(20), dp(6));
        ImageView image = coverView(dp(124), dp(174));
        ImageLoader.load(image, Store.isDataSaverEnabled() ? result.info.coverSmall : result.info.cover, Store.isDataSaverEnabled());
        intro.addView(image, new LinearLayout.LayoutParams(dp(124), dp(174)));
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setPadding(dp(15), 0, 0, 0);
        textBox.addView(text(result.info.name, 22, TEXT, true));
        String meta = (result.info.year > 0 ? String.valueOf(result.info.year) : "") + (result.info.format.isEmpty() ? "" : " · " + result.info.format);
        textBox.addView(text(meta, 13, MUTED, false), margin(0, 6, 0, 0));
        textBox.addView(text(result.tracks.size() + " музыкальных тем", 13, MUTED, false), margin(0, 3, 0, 0));
        intro.addView(textBox, new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(intro);
        if (!result.info.synopsis.isEmpty()) {
            TextView synopsis = text(result.info.synopsis, 14, MUTED, false);
            synopsis.setMaxLines(5);
            synopsis.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(synopsis, margin(20, 11, 20, 5));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView play = pill("▶  Слушать", ACCENT, Color.WHITE);
        TextView shuffle = pill("Перемешать", SURFACE_ALT, TEXT);
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(43), 1f));
        actions.addView(shuffle, marginParams(8, 0, 0, 0, 0, dp(43)));
        body.addView(actions);
        play.setEnabled(!result.tracks.isEmpty());
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(result.tracks, 0, true); } });
        shuffle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(shuffle(result.tracks), 0, true); } });
        sectionHeader(body, "Темы", null, null);
        if (result.tracks.isEmpty()) body.addView(empty("Нет доступного аудио", "Для этого аниме источник пока не отдал файлы."), margin(20, 20, 20, 0));
        else addTrackList(body, Store.filterMature(result.tracks), result.tracks.size());
        setPageView(scroll);
    }

    private void renderArtist(ApiClient.ArtistResult result) {
        ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar(result.name, true));
        body.addView(text(result.name, 27, TEXT, true), margin(20, 21, 20, 3));
        if (!result.information.isEmpty()) {
            TextView info = text(result.information, 14, MUTED, false);
            info.setMaxLines(4);
            info.setEllipsize(TextUtils.TruncateAt.END);
            body.addView(info, margin(20, 0, 20, 8));
        }
        sectionHeader(body, "Треки исполнителя", null, null);
        addTrackList(body, Store.filterMature(result.tracks), result.tracks.size());
        setPageView(scroll);
    }

    private void renderDetailError(String message) {
        ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);
        body.addView(topBar("Ошибка", true));
        body.addView(empty("Не удалось открыть страницу", message), margin(20, 35, 20, 0));
        setPageView(scroll);
    }

    private void addAnimeRow(LinearLayout body, final AnimeInfo anime) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = coverView(dp(58), dp(78));
        ImageLoader.load(image, Store.isDataSaverEnabled() ? anime.coverSmall : anime.cover, Store.isDataSaverEnabled());
        row.addView(image, new LinearLayout.LayoutParams(dp(58), dp(78)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(13), 0, dp(3), 0);
        TextView name = text(anime.name, 16, TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        String meta = anime.year > 0 ? String.valueOf(anime.year) : "Каталог AnimeThemes";
        labels.addView(text(meta, 13, MUTED, false), margin(0, 4, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1f));
        row.addView(text("›", 25, DIM, false), new LinearLayout.LayoutParams(dp(36), -1));
        body.addView(row, margin(20, 4, 20, 4));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showAnime(anime.slug, anime.name); } });
    }

    private void addTrackList(LinearLayout body, List<Track> tracks, int limit) {
        if (tracks == null) return;
        int count = 0;
        for (Track t : tracks) {
            if (t == null) continue;
            addTrackRow(body, t, tracks, 0);
            if (++count >= limit) break;
        }
    }

    private void addTrackRow(LinearLayout body, final Track track, final List<Track> context, int top) {
        final LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image = coverView(dp(58), dp(58));
        ImageLoader.load(image, Store.isDataSaverEnabled() ? track.coverSmall : track.cover, Store.isDataSaverEnabled());
        row.addView(image, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(11), 0, dp(3), 0);
        TextView title = text(track.title, 15, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);
        TextView subtitle = text(track.displayArtist() + " · " + track.animeName, 12, MUTED, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(subtitle, margin(0, 4, 0, 0));
        TextView badge = text(track.displayTheme() + (Store.isOffline(track) ? " · офлайн" : ""), 11, Store.isOffline(track) ? GREEN : ACCENT, false);
        labels.addView(badge, margin(0, 3, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView play = button("▶", 16, ACCENT, true);
        TextView more = button("⋮", 22, MUTED, false);
        row.addView(play, new LinearLayout.LayoutParams(dp(36), dp(64)));
        row.addView(more, new LinearLayout.LayoutParams(dp(31), dp(64)));
        body.addView(row, margin(20, top == 0 ? 4 : top, 20, 4));
        row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(context, indexOf(context, track), true); } });
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playTracks(context, indexOf(context, track), true); } });
        more.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { trackMenu(v, track, context); } });
    }

    private void trackMenu(View anchor, final Track track, final List<Track> context) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, Store.isFavorite(track.id) ? "Убрать из избранного" : "В избранное");
        popup.getMenu().add(0, 2, 1, Store.isOffline(track) ? "Удалить из офлайн" : "Сохранить офлайн");
        popup.getMenu().add(0, 3, 2, "Добавить в очередь");
        popup.getMenu().add(0, 4, 3, "В плейлист");
        if (track.animeSlug != null && !track.animeSlug.isEmpty()) popup.getMenu().add(0, 5, 4, "Открыть аниме");
        if (track.artistSlug != null && !track.artistSlug.isEmpty()) popup.getMenu().add(0, 6, 5, "Открыть исполнителя");
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        toast(Store.toggleFavorite(track) ? "Добавлено в избранное" : "Убрано из избранного");
                        if (page == Page.LIBRARY) showLibrary();
                        return true;
                    case 2:
                        if (Store.isOffline(track)) {
                            Store.removeOffline(track.id);
                            toast("Удалено из офлайн");
                        } else downloadTrack(track);
                        if (page == Page.LIBRARY) showLibrary();
                        return true;
                    case 3:
                        addToQueue(track);
                        return true;
                    case 4:
                        addTrackToPlaylist(track);
                        return true;
                    case 5:
                        showAnime(track.animeSlug, track.animeName);
                        return true;
                    case 6:
                        showArtist(track.artistSlug, track.displayArtist());
                        return true;
                    default: return false;
                }
            }
        });
        popup.show();
    }

    private void addToQueue(Track track) {
        ArrayList<Track> queue = Store.getQueue();
        if (queue.isEmpty()) queue.add(track.copy());
        else queue.add(track.copy());
        Store.saveQueue(queue, Store.getQueueIndex());
        toast("Добавлено в очередь");
    }

    private void addTrackToPlaylist(final Track track) {
        final ArrayList<Playlist> playlists = Store.getPlaylists();
        if (playlists.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Плейлистов нет").setMessage("Создать новый плейлист для этого трека?").setNegativeButton("Отмена", null).setPositiveButton("Создать", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) { Playlist p = Store.createPlaylist("Любимое аниме"); Store.addToPlaylist(p.id, track); toast("Добавлено в «" + p.name + "»"); }
            }).show();
            return;
        }
        String[] names = new String[playlists.size() + 1];
        for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
        names[playlists.size()] = "＋ Новый плейлист";
        new AlertDialog.Builder(this).setTitle("В плейлист").setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == playlists.size()) { createPlaylistDialog(); return; }
                toast(Store.addToPlaylist(playlists.get(which).id, track) ? "Добавлено в плейлист" : "Трек уже там");
            }
        }).show();
    }

    private void downloadTrack(final Track source) {
        if (Store.isOffline(source)) { toast("Уже доступно офлайн"); return; }
        toast("Скачивание началось");
        io.execute(new Runnable() {
            @Override public void run() {
                File target = Store.offlineFile(source);
                File temp = new File(target.getAbsolutePath() + ".part");
                HttpURLConnection connection = null;
                InputStream input = null;
                FileOutputStream output = null;
                try {
                    connection = (HttpURLConnection) new URL(source.audioUrl).openConnection();
                    connection.setConnectTimeout(12000);
                    connection.setReadTimeout(30000);
                    connection.setRequestProperty("User-Agent", "AniBeat/1.0 Android");
                    if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) throw new IllegalStateException("HTTP");
                    input = new BufferedInputStream(connection.getInputStream());
                    output = new FileOutputStream(temp);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    output.flush();
                    if (target.exists()) target.delete();
                    if (!temp.renameTo(target)) throw new IllegalStateException("Не удалось сохранить файл");
                    Store.saveOffline(source, target);
                    main.post(new Runnable() { @Override public void run() { toast("Доступно офлайн: " + source.title); if (page == Page.LIBRARY) showLibrary(); } });
                } catch (Exception e) {
                    temp.delete();
                    main.post(new Runnable() { @Override public void run() { toast("Не удалось скачать трек"); } });
                } finally {
                    try { if (input != null) input.close(); } catch (Exception ignored) { }
                    try { if (output != null) output.close(); } catch (Exception ignored) { }
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private void playTracks(List<Track> source, int start, boolean openPlayer) {
        ArrayList<Track> queue = new ArrayList<>();
        if (source != null) {
            Map<String, Track> offline = new HashMap<>();
            for (Track t : Store.getOfflineTracks()) offline.put(t.id, t);
            for (Track t : source) {
                if (t == null || (!Store.isMatureEnabled() && t.nsfw)) continue;
                Track copy = t.copy();
                Track local = offline.get(copy.id);
                if (local != null) copy.offlinePath = local.offlinePath;
                queue.add(copy);
            }
        }
        if (queue.isEmpty()) { toast("Нет доступных треков"); return; }
        int safeStart = Math.max(0, Math.min(start, queue.size() - 1));
        Store.saveQueue(queue, safeStart);
        Store.addHistory(queue.get(safeStart));
        Store.setPlayback(queue.get(safeStart), false, 0L, 0L);
        PlaybackService.play(this, queue, safeStart);
        refreshMiniPlayer();
        if (openPlayer) showPlayerDialog();
    }

    private void showPlayerDialog() {
        final Track track = Store.getCurrentTrack();
        if (track == null) { toast("Сначала выберите трек"); return; }
        playerDialog = new Dialog(this);
        playerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), dp(14));
        box.setBackground(round(SURFACE, 20));
        ImageView cover = coverView(dp(238), dp(238));
        cover.setTag("player-cover");
        ImageLoader.load(cover, Store.isDataSaverEnabled() ? track.coverSmall : track.cover, Store.isDataSaverEnabled());
        box.addView(cover, centeredParams(dp(238), dp(238)));
        TextView title = text(track.title, 21, TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(title, margin(0, 16, 0, 0));
        TextView sub = text(track.displayArtist() + " · " + track.animeName, 13, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub, margin(0, 4, 0, 5));
        SeekBar seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setProgress(0);
        box.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        TextView prev = button("‹", 33, TEXT, false);
        TextView toggle = button(Store.isPlaying() ? "Ⅱ" : "▶", 28, TEXT, true);
        TextView next = button("›", 33, TEXT, false);
        controls.addView(prev, new LinearLayout.LayoutParams(dp(62), dp(56)));
        controls.addView(toggle, new LinearLayout.LayoutParams(dp(78), dp(56)));
        controls.addView(next, new LinearLayout.LayoutParams(dp(62), dp(56)));
        box.addView(controls);
        TextView close = pill("Закрыть", SURFACE_ALT, TEXT);
        box.addView(close, margin(0, 5, 0, 0));
        playerDialog.setContentView(box);
        Window window = playerDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(-1, -2);
        }
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_PREVIOUS); } });
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_NEXT); } });
        toggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_TOGGLE); } });
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playerDialog.dismiss(); } });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = Store.getDuration();
                if (duration > 0) PlaybackService.seek(MainActivity.this, duration * bar.getProgress() / 1000L);
            }
        });
        playerDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface dialog) {
                Window w = playerDialog.getWindow();
                if (w != null) { w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f), -2); w.setDimAmount(0.7f); }
            }
        });
        playerDialog.show();
        updatePlayerDialog();
    }

    private void updatePlayerDialog() {
        if (playerDialog == null || !playerDialog.isShowing()) return;
        // The dialog is intentionally light: the next state broadcast refreshes the compact player.
        refreshMiniPlayer();
    }

    private void openAnime(Track track) {
        if (track.animeSlug == null || track.animeSlug.isEmpty()) { toast("У этого трека нет страницы аниме"); return; }
        showAnime(track.animeSlug, track.animeName);
    }

    private int indexOf(List<Track> list, Track target) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(target.id)) return i;
        return 0;
    }

    private ArrayList<Track> shuffle(List<Track> input) {
        ArrayList<Track> out = new ArrayList<>(input);
        java.util.Collections.shuffle(out);
        return out;
    }

    private void setPageView(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
        refreshMiniPlayer();
    }

    private ScrollView pageScroll() {
        ScrollView view = new ScrollView(this);
        view.setFillViewport(true);
        view.setBackgroundColor(BG);
        view.setClipToPadding(false);
        return view;
    }

    private LinearLayout body(ScrollView scroll) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(22));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        return body;
    }

    private LinearLayout topBar(String title, boolean back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(12), dp(4));
        if (back) {
            TextView arrow = button("‹", 31, ACCENT, false);
            bar.addView(arrow, new LinearLayout.LayoutParams(dp(39), dp(44)));
            arrow.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onBackPressed(); } });
        } else {
            TextView logo = button("✦", 22, Color.WHITE, true);
            logo.setGravity(Gravity.CENTER);
            logo.setBackground(round(ACCENT, 14));
            bar.addView(logo, new LinearLayout.LayoutParams(dp(38), dp(38)));
        }
        TextView heading = text(title, 20, TEXT, true);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView settings = button("⚙", 22, MUTED, false);
        bar.addView(settings, new LinearLayout.LayoutParams(dp(42), dp(44)));
        settings.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showSettings(); } });
        return bar;
    }

    private void sectionHeader(LinearLayout body, String title, String action, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(19), dp(20), dp(7));
        TextView name = text(title, 19, TEXT, true);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1f));
        if (action != null) {
            TextView button = text(action, 13, ACCENT, true);
            row.addView(button, new LinearLayout.LayoutParams(dp(54), dp(34)));
            button.setGravity(Gravity.CENTER);
            button.setOnClickListener(listener);
        }
        body.addView(row);
    }

    private TextView filterChip(String value, boolean selected) {
        return pill(value, selected ? ACCENT : SURFACE_ALT, selected ? Color.WHITE : TEXT);
    }

    private TextView pill(String value, int color, int textColor) {
        TextView view = button(value, 13, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(color, 13));
        view.setMinWidth(dp(66));
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView button(String value, float size, int color, boolean bold) {
        TextView view = text(value, size, color, bold);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setPadding(dp(4), 0, dp(4), 0);
        return view;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(dp(9), dp(9), dp(5), dp(9));
        layout.setBackground(round(SURFACE, 14));
        return layout;
    }

    private ImageView coverView(int width, int height) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(round(Color.rgb(43, 36, 53), 10));
        return image;
    }

    private View empty(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18), dp(28), dp(18), dp(28));
        box.setBackground(round(SURFACE, 16));
        TextView icon = text("♫", 32, ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon, new LinearLayout.LayoutParams(-1, dp(43)));
        TextView h = text(title, 17, TEXT, true);
        h.setGravity(Gravity.CENTER);
        box.addView(h, margin(0, 7, 0, 0));
        TextView s = text(subtitle, 13, MUTED, false);
        s.setGravity(Gravity.CENTER);
        s.setMaxLines(3);
        box.addView(s, margin(12, 4, 12, 0));
        return box;
    }

    private ProgressBar progress() {
        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        return bar;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox view = new CheckBox(this);
        view.setText(label);
        view.setTextColor(TEXT);
        view.setTextSize(15);
        view.setChecked(checked);
        view.setPadding(dp(4), dp(5), dp(4), dp(5));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private ImageView.LayoutParams centeredParams(int width, int height) {
        ImageView.LayoutParams params = new ImageView.LayoutParams(width, height);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private LinearLayout.LayoutParams margin(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private LinearLayout.LayoutParams marginParams(int left, int top, int right, int bottom, int width, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width == 0 ? -2 : width, height == 0 ? -2 : height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 5) return "Доброй ночи";
        if (hour < 12) return "Доброе утро";
        if (hour < 18) return "Добрый день";
        return "Добрый вечер";
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
