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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure Black Minimalist Material AniBeat Native Android Application.
 */
public final class MainActivity extends Activity {
    // Pure Pitch Black OLED Durov-grade minimalist palette (100% like AniBeat website)
    public static final int BG = 0xFF000000;
    public static final int SURFACE_1 = 0xFF0B0B0C;
    public static final int SURFACE_2 = 0xFF141416;
    public static final int SURFACE_3 = 0xFF1E1E21;
    public static final int SURFACE_4 = 0xFF28282C;
    public static final int OUTLINE = 0xFF26262A;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFF9A9AA2;
    public static final int DIM = 0xFF63636B;
    public static final int ACCENT = 0xFF8AB4F8;
    public static final int GREEN = 0xFF7EE0C0;
    public static final int HEART = 0xFFFF4F6F;

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
    private int browseYear = 2026;
    private String browseSeason = "";
    private int libraryTab = 0; // 0: Playlists, 1: Favorites, 2: History, 3: Downloads
    private String currentSearchFilter = "ALL"; // ALL, OP, ED, ANIME, ARTIST

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
    private ImageView miniToggleIcon;
    private EqualizerBarsView miniEqualizer;
    private LinearLayout bottomNav;
    private ImageView[] navIcons = new ImageView[4];
    private TextView[] navLabels = new TextView[4];

    // Full Player Dialog controls
    private Dialog playerDialog;
    private ImageView playerCover;
    private TextView playerTitle;
    private TextView playerSubtitle;
    private TextView playerBadge;
    private ImageView playerFav;
    private SeekBar playerSeek;
    private TextView playerElapsed;
    private TextView playerDuration;
    private ImageView playerToggle;
    private ImageView playerShuffle;
    private ImageView playerRepeat;
    private Runnable playerTicker;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getBooleanExtra("error", false)) {
                toast("Не удалось воспроизвести трек");
            }
            refreshMiniPlayer();
            updatePlayerDialog(false);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Store.init(this);

        // Pure black OLED status and nav bar
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(SURFACE_1);
        if (Build.VERSION.SDK_INT >= 26) {
            int flags = w.getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

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
        stopPlayerTicker();
        super.onStop();
    }

    @Override public void onBackPressed() {
        if (playerDialog != null && playerDialog.isShowing()) {
            playerDialog.dismiss();
            return;
        }
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
        shell.addView(miniPlayer, new LinearLayout.LayoutParams(-1, dp(66)));

        bottomNav = buildBottomNav();
        shell.addView(bottomNav, new LinearLayout.LayoutParams(-1, dp(58)));
        setContentView(shell);
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(SURFACE_1);
        nav.setPadding(dp(8), 0, dp(8), 0);

        String[] labels = {"Главная", "Поиск", "Обзор", "Медиатека"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_search, R.drawable.ic_explore, R.drawable.ic_library};

        for (int i = 0; i < labels.length; i++) {
            final int target = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(6), 0, dp(4));

            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(i == 0 ? TEXT : DIM);
            item.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView label = text(labels[i], 11, i == 0 ? TEXT : DIM, false);
            label.setGravity(Gravity.CENTER);
            item.addView(label, margin(0, 3, 0, 0));

            navIcons[i] = icon;
            navLabels[i] = label;

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    page = target == 0 ? Page.HOME : target == 1 ? Page.SEARCH : target == 2 ? Page.BROWSE : Page.LIBRARY;
                    updateNavState(target);
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

    private void updateNavState(int activeIndex) {
        for (int i = 0; i < 4; i++) {
            boolean active = (i == activeIndex);
            if (navIcons[i] != null) navIcons[i].setColorFilter(active ? TEXT : DIM);
            if (navLabels[i] != null) navLabels[i].setTextColor(active ? TEXT : DIM);
        }
    }

    private LinearLayout buildMiniPlayer() {
        LinearLayout container = new LinearLayout(this);
        container.setPadding(dp(8), dp(4), dp(8), dp(4));
        container.setBackgroundColor(BG);

        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(6), dp(10), dp(6));
        card.setBackground(borderedCard(SURFACE_2, OUTLINE, 14));

        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniCover.setBackground(round(SURFACE_3, 8));
        miniCover.setClipToOutline(true);
        card.addView(miniCover, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setPadding(dp(10), 0, dp(6), 0);

        miniTitle = text("AniBeat", 14, TEXT, true);
        miniTitle.setEllipsize(TextUtils.TruncateAt.END);
        miniTitle.setSingleLine(true);
        labels.addView(miniTitle);

        LinearLayout subRow = new LinearLayout(this);
        subRow.setOrientation(LinearLayout.HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);

        miniEqualizer = new EqualizerBarsView(this);
        miniEqualizer.setColor(ACCENT);
        subRow.addView(miniEqualizer, new LinearLayout.LayoutParams(dp(14), dp(12)));

        miniSubtitle = text(" Аниме музыка", 12, MUTED, false);
        miniSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        miniSubtitle.setSingleLine(true);
        subRow.addView(miniSubtitle, new LinearLayout.LayoutParams(-1, -2));
        labels.addView(subRow, margin(0, 2, 0, 0));

        card.addView(labels, new LinearLayout.LayoutParams(0, -1, 1f));

        // Transport controls
        ImageView prevBtn = iconButton(R.drawable.ic_skip_previous, MUTED, dp(22), dp(8), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                PlaybackService.command(MainActivity.this, PlaybackService.ACTION_PREVIOUS);
            }
        });
        card.addView(prevBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        FrameLayout playWrap = new FrameLayout(this);
        playWrap.setBackground(round(TEXT, 999));
        miniToggleIcon = new ImageView(this);
        miniToggleIcon.setImageResource(R.drawable.ic_play_arrow);
        miniToggleIcon.setColorFilter(BG);
        playWrap.addView(miniToggleIcon, centeredParams(dp(18), dp(18)));
        playWrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                PlaybackService.command(MainActivity.this, PlaybackService.ACTION_TOGGLE);
            }
        });
        card.addView(playWrap, marginParams(4, 0, 4, 0, dp(34), dp(34)));

        ImageView nextBtn = iconButton(R.drawable.ic_skip_next, MUTED, dp(22), dp(8), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                PlaybackService.command(MainActivity.this, PlaybackService.ACTION_NEXT);
            }
        });
        card.addView(nextBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPlayerDialog(); }
        });

        container.addView(card, new LinearLayout.LayoutParams(-1, -1));
        return container;
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
        miniSubtitle.setText(" " + current.displayArtist() + " · " + current.animeName);

        boolean playing = Store.isPlaying();
        miniToggleIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        miniEqualizer.setPlaying(playing);
        ImageLoader.load(miniCover, !current.coverSmall.isEmpty() ? current.coverSmall : current.cover, true);
    }

    /* ------------------------------------------------------------------ */
    /* HOME PAGE                                                           */
    /* ------------------------------------------------------------------ */
    private void showHome() {
        page = Page.HOME;
        updateNavState(0);
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);

        body.addView(headerBar("AniBeat", false));

        // Subheader
        TextView greeting = text(greeting(), 13, MUTED, false);
        body.addView(greeting, margin(18, 10, 18, 0));
        TextView heading = text("Опенинги и эндинги", 26, TEXT, true);
        body.addView(heading, margin(18, 2, 18, 0));
        TextView subtitle = text("Музыка из японской анимации в высоком качестве", 13, DIM, false);
        body.addView(subtitle, margin(18, 2, 18, 12));

        // Hero Card ("Трек дня")
        if (!homeTracks.isEmpty()) {
            final Track hero = homeTracks.get(0);
            LinearLayout heroCard = new LinearLayout(this);
            heroCard.setOrientation(LinearLayout.HORIZONTAL);
            heroCard.setGravity(Gravity.CENTER_VERTICAL);
            heroCard.setPadding(dp(14), dp(14), dp(14), dp(14));
            heroCard.setBackground(borderedCard(SURFACE_2, OUTLINE, 18));

            ImageView cover = coverView(dp(96), dp(96), 12);
            ImageLoader.load(cover, hero.cover, false);
            heroCard.addView(cover, new LinearLayout.LayoutParams(dp(96), dp(96)));

            LinearLayout heroText = new LinearLayout(this);
            heroText.setOrientation(LinearLayout.VERTICAL);
            heroText.setGravity(Gravity.CENTER_VERTICAL);
            heroText.setPadding(dp(14), 0, dp(4), 0);

            TextView tag = text("ТРЕК ДНЯ", 11, ACCENT, true);
            tag.setLetterSpacing(0.08f);
            heroText.addView(tag);

            TextView ht = text(hero.title, 18, TEXT, true);
            ht.setMaxLines(2);
            ht.setEllipsize(TextUtils.TruncateAt.END);
            heroText.addView(ht, margin(0, 3, 0, 0));

            TextView ha = text(hero.displayArtist(), 13, MUTED, false);
            ha.setSingleLine(true);
            ha.setEllipsize(TextUtils.TruncateAt.END);
            heroText.addView(ha, margin(0, 2, 0, 0));

            TextView han = text(hero.animeName, 12, DIM, false);
            han.setSingleLine(true);
            han.setEllipsize(TextUtils.TruncateAt.END);
            heroText.addView(han, margin(0, 2, 0, 0));

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            btns.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout playBtn = pillIcon(R.drawable.ic_play_arrow, "Слушать", TEXT, BG);
            btns.addView(playBtn, marginParams(0, 0, 8, 0, -2, dp(36)));
            playBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { playTracks(homeTracks, 0, true); }
            });

            ImageView shufBtn = iconButton(R.drawable.ic_shuffle, TEXT, dp(18), dp(8), round(SURFACE_4, 999), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Store.setShuffle(true);
                    ArrayList<Track> shuf = new ArrayList<>(homeTracks);
                    Collections.shuffle(shuf);
                    playTracks(shuf, 0, true);
                }
            });
            btns.addView(shufBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

            heroText.addView(btns, margin(0, 10, 0, 0));
            heroCard.addView(heroText, new LinearLayout.LayoutParams(0, -1, 1f));
            body.addView(heroCard, margin(18, 0, 18, 16));
        }

        // Categories / Quick filters
        sectionTitle(body, "Категории");
        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(18), 0, dp(18), dp(12));

        chips.addView(filterChip("Опенинги", false, new View.OnClickListener() {
            @Override public void onClick(View v) { browseType = "OP"; page = Page.BROWSE; showBrowse(); }
        }));
        chips.addView(filterChip("Эндинги", false, new View.OnClickListener() {
            @Override public void onClick(View v) { browseType = "ED"; page = Page.BROWSE; showBrowse(); }
        }));
        chips.addView(filterChip("Зима 2026", false, new View.OnClickListener() {
            @Override public void onClick(View v) { browseYear = 2026; browseSeason = "Winter"; page = Page.BROWSE; showBrowse(); }
        }));
        chips.addView(filterChip("Осень 2025", false, new View.OnClickListener() {
            @Override public void onClick(View v) { browseYear = 2025; browseSeason = "Fall"; page = Page.BROWSE; showBrowse(); }
        }));
        chips.addView(filterChip("Случайный микс", false, new View.OnClickListener() {
            @Override public void onClick(View v) { loadRandomMix(); }
        }));
        chipsScroll.addView(chips);
        body.addView(chipsScroll);

        // Fresh Releases Section
        sectionTitle(body, "Новинки и тренды");
        if (homeLoading && homeTracks.isEmpty()) {
            body.addView(progressView(), margin(0, 24, 0, 24));
        } else if (homeTracks.isEmpty()) {
            body.addView(emptyView("Не удалось загрузить каталог", "Нажмите здесь для повторной попытки", new View.OnClickListener() {
                @Override public void onClick(View v) { loadHome(false); }
            }), margin(18, 16, 18, 16));
        } else {
            addTrackList(body, Store.filterMature(homeTracks), 15);
        }

        // Favorites quick section if available
        ArrayList<Track> favs = Store.getFavorites();
        if (!favs.isEmpty()) {
            sectionTitle(body, "Любимые треки");
            addTrackList(body, favs, 4);
        }

        // History quick section if available
        ArrayList<Track> hist = Store.getHistory();
        if (!hist.isEmpty()) {
            sectionTitle(body, "Недавно прослушано");
            addTrackList(body, hist, 4);
        }

        if (homeTracks.isEmpty() && !homeLoading) loadHome(false);
        setPageView(scroll);
    }

    private void loadHome(final boolean random) {
        if (homeLoading) return;
        homeLoading = true;
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ArrayList<Track> list = random ? ApiClient.random(40, "") : ApiClient.latest(40, "");
                    Store.saveHomeCache(list);
                    main.post(new Runnable() {
                        @Override public void run() {
                            homeLoading = false;
                            homeTracks.clear();
                            homeTracks.addAll(list);
                            if (page == Page.HOME) showHome();
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            homeLoading = false;
                            if (page == Page.HOME) showHome();
                        }
                    });
                }
            }
        });
    }

    private void loadRandomMix() {
        toast("Загрузка случайного микса...");
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ArrayList<Track> list = ApiClient.random(30, "");
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (!list.isEmpty()) {
                                playTracks(list, 0, true);
                            } else {
                                toast("Не удалось загрузить треки");
                            }
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { toast("Ошибка сети"); }
                    });
                }
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* SEARCH PAGE                                                         */
    /* ------------------------------------------------------------------ */
    private void showSearch() {
        page = Page.SEARCH;
        updateNavState(1);
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        final LinearLayout body = body(scroll);

        body.addView(headerBar("Поиск", false));

        // Modern search input field with search icon and clear button
        LinearLayout searchBox = new LinearLayout(this);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(dp(14), dp(4), dp(10), dp(4));
        searchBox.setBackground(borderedCard(SURFACE_2, OUTLINE, 14));

        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(DIM);
        searchBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        final EditText input = new EditText(this);
        input.setText(searchQuery);
        input.setHint("Аниме, песня, исполнитель...");
        input.setHintTextColor(DIM);
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.addView(input, marginParams(10, 0, 8, 0, 0, -2, 1f));

        final ImageView clearBtn = new ImageView(this);
        clearBtn.setImageResource(R.drawable.ic_close);
        clearBtn.setColorFilter(DIM);
        clearBtn.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                input.setText("");
                searchQuery = "";
                searchResult = null;
                showSearch();
            }
        });
        searchBox.addView(clearBtn, new LinearLayout.LayoutParams(dp(24), dp(24)));
        body.addView(searchBox, margin(18, 12, 18, 10));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER) {
                    hideKeyboard(input);
                    String q = input.getText().toString().trim();
                    if (!q.isEmpty()) executeSearch(q);
                    return true;
                }
                return false;
            }
        });

        // Search Filter Chips
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setPadding(dp(18), 0, dp(18), dp(12));

        filterRow.addView(filterChip("Все", "ALL".equals(currentSearchFilter), new View.OnClickListener() {
            @Override public void onClick(View v) { currentSearchFilter = "ALL"; showSearch(); }
        }));
        filterRow.addView(filterChip("Опенинги", "OP".equals(currentSearchFilter), new View.OnClickListener() {
            @Override public void onClick(View v) { currentSearchFilter = "OP"; showSearch(); }
        }));
        filterRow.addView(filterChip("Эндинги", "ED".equals(currentSearchFilter), new View.OnClickListener() {
            @Override public void onClick(View v) { currentSearchFilter = "ED"; showSearch(); }
        }));
        filterRow.addView(filterChip("Аниме", "ANIME".equals(currentSearchFilter), new View.OnClickListener() {
            @Override public void onClick(View v) { currentSearchFilter = "ANIME"; showSearch(); }
        }));
        filterRow.addView(filterChip("Исполнители", "ARTIST".equals(currentSearchFilter), new View.OnClickListener() {
            @Override public void onClick(View v) { currentSearchFilter = "ARTIST"; showSearch(); }
        }));
        filterScroll.addView(filterRow);
        body.addView(filterScroll);

        if (searchLoading) {
            body.addView(progressView(), margin(0, 36, 0, 0));
        } else if (searchResult != null) {
            // Anime matches
            if (!searchResult.anime.isEmpty() && (!"OP".equals(currentSearchFilter) && !"ED".equals(currentSearchFilter))) {
                sectionTitle(body, "Аниме (" + searchResult.anime.size() + ")");
                for (final AnimeInfo a : searchResult.anime) {
                    body.addView(buildAnimeCard(a), margin(18, 4, 18, 4));
                }
            }

            // Track matches
            ArrayList<Track> filteredTracks = new ArrayList<>();
            for (Track t : searchResult.tracks) {
                if ("OP".equals(currentSearchFilter) && !"OP".equals(t.type)) continue;
                if ("ED".equals(currentSearchFilter) && !"ED".equals(t.type)) continue;
                filteredTracks.add(t);
            }

            if (!filteredTracks.isEmpty() && !"ANIME".equals(currentSearchFilter) && !"ARTIST".equals(currentSearchFilter)) {
                sectionTitle(body, "Треки (" + filteredTracks.size() + ")");
                addTrackList(body, filteredTracks, 40);
            }

            // Artist matches
            if (!searchResult.artists.isEmpty() && (!"OP".equals(currentSearchFilter) && !"ED".equals(currentSearchFilter) && !"ANIME".equals(currentSearchFilter))) {
                sectionTitle(body, "Исполнители (" + searchResult.artists.size() + ")");
                for (final String artistName : searchResult.artists) {
                    body.addView(buildArtistChip(artistName), margin(18, 3, 18, 3));
                }
            }

            if (searchResult.anime.isEmpty() && searchResult.tracks.isEmpty() && searchResult.artists.isEmpty()) {
                body.addView(emptyView("Ничего не найдено", "Попробуйте изменить поисковый запрос", null), margin(18, 32, 18, 0));
            }
        } else {
            // Suggestion shortcuts
            sectionTitle(body, "Популярные запросы");
            String[] suggestions = {"Attack on Titan", "Naruto", "Jujutsu Kaisen", "Bleach", "Demon Slayer", "LiSA", "YOASOBI", "Aimer"};
            for (final String s : suggestions) {
                body.addView(buildSuggestionRow(s, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        input.setText(s);
                        executeSearch(s);
                    }
                }), margin(18, 3, 18, 3));
            }
        }

        setPageView(scroll);
    }

    private void executeSearch(final String query) {
        searchQuery = query;
        searchLoading = true;
        showSearch();
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ApiClient.SearchResult res = ApiClient.search(query);
                    main.post(new Runnable() {
                        @Override public void run() {
                            searchLoading = false;
                            searchResult = res;
                            if (page == Page.SEARCH) showSearch();
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            searchLoading = false;
                            toast("Ошибка поиска: проверьте подключение");
                            if (page == Page.SEARCH) showSearch();
                        }
                    });
                }
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* BROWSE PAGE                                                         */
    /* ------------------------------------------------------------------ */
    private void showBrowse() {
        page = Page.BROWSE;
        updateNavState(2);
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);

        body.addView(headerBar("Обзор каталога", false));

        // Year Selector Carousel
        sectionTitle(body, "Год выпуска");
        HorizontalScrollView yearScroll = new HorizontalScrollView(this);
        yearScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout yearRow = new LinearLayout(this);
        yearRow.setPadding(dp(18), 0, dp(18), dp(10));

        int[] years = {2026, 2025, 2024, 2023, 2022, 2021, 2020, 2019, 2018, 2017, 2016, 2015, 2014, 2013, 2012, 2011, 2010, 2005, 2000, 1995, 1990};
        for (final int y : years) {
            yearRow.addView(filterChip(String.valueOf(y), browseYear == y, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    browseYear = y;
                    loadBrowseTracks();
                }
            }));
        }
        yearScroll.addView(yearRow);
        body.addView(yearScroll);

        // Season Selector
        sectionTitle(body, "Сезон");
        HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout seasonRow = new LinearLayout(this);
        seasonRow.setPadding(dp(18), 0, dp(18), dp(10));

        String[] seasons = {"Все", "Зима", "Весна", "Лето", "Осень"};
        final String[] seasonKeys = {"", "Winter", "Spring", "Summer", "Fall"};
        for (int i = 0; i < seasons.length; i++) {
            final String k = seasonKeys[i];
            seasonRow.addView(filterChip(seasons[i], browseSeason.equals(k), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    browseSeason = k;
                    loadBrowseTracks();
                }
            }));
        }
        seasonScroll.addView(seasonRow);
        body.addView(seasonScroll);

        // Type filter: All, OP, ED
        sectionTitle(body, "Тип композиции");
        LinearLayout typeRow = new LinearLayout(this);
        typeRow.setPadding(dp(18), 0, dp(18), dp(12));
        typeRow.addView(filterChip("Все типы", browseType.isEmpty(), new View.OnClickListener() {
            @Override public void onClick(View v) { browseType = ""; showBrowse(); }
        }));
        typeRow.addView(filterChip("Только опенинги (OP)", "OP".equals(browseType), new View.OnClickListener() {
            @Override public void onClick(View v) { browseType = "OP"; showBrowse(); }
        }));
        typeRow.addView(filterChip("Только эндинги (ED)", "ED".equals(browseType), new View.OnClickListener() {
            @Override public void onClick(View v) { browseType = "ED"; showBrowse(); }
        }));
        body.addView(typeRow);

        // Track Results
        sectionTitle(body, (browseSeason.isEmpty() ? "" : (seasonNameRu(browseSeason) + " ")) + browseYear + " (" + browseTracks.size() + ")");
        if (browseLoading) {
            body.addView(progressView(), margin(0, 32, 0, 0));
        } else if (browseTracks.isEmpty()) {
            body.addView(emptyView("Нет треков за выбранный период", "Попробуйте выбрать другой год или сезон", new View.OnClickListener() {
                @Override public void onClick(View v) { loadBrowseTracks(); }
            }), margin(18, 24, 18, 0));
        } else {
            ArrayList<Track> filtered = new ArrayList<>();
            for (Track t : browseTracks) {
                if (!browseType.isEmpty() && !browseType.equals(t.type)) continue;
                filtered.add(t);
            }
            addTrackList(body, filtered, 50);
        }

        if (browseTracks.isEmpty() && !browseLoading) loadBrowseTracks();
        setPageView(scroll);
    }

    private void loadBrowseTracks() {
        browseLoading = true;
        showBrowse();
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    // Fetch tracks for the chosen year
                    final ArrayList<Track> list = ApiClient.latest(50, browseType);
                    main.post(new Runnable() {
                        @Override public void run() {
                            browseLoading = false;
                            browseTracks.clear();
                            browseTracks.addAll(list);
                            if (page == Page.BROWSE) showBrowse();
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            browseLoading = false;
                            if (page == Page.BROWSE) showBrowse();
                        }
                    });
                }
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* LIBRARY PAGE                                                        */
    /* ------------------------------------------------------------------ */
    private void showLibrary() {
        page = Page.LIBRARY;
        updateNavState(3);
        bottomNav.setVisibility(View.VISIBLE);
        final ScrollView scroll = pageScroll();
        LinearLayout body = body(scroll);

        body.addView(headerBar("Медиатека", false));

        // Library Tab Selector (Playlists, Favorites, History, Downloads)
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(dp(18), dp(10), dp(18), dp(12));

        String[] tabs = {"Плейлисты", "Любимое", "История", "Загрузки"};
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            tabRow.addView(filterChip(tabs[i], libraryTab == t, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    libraryTab = t;
                    showLibrary();
                }
            }));
        }
        tabScroll.addView(tabRow);
        body.addView(tabScroll);

        if (libraryTab == 0) {
            // Playlists Tab
            LinearLayout createBtn = new LinearLayout(this);
            createBtn.setGravity(Gravity.CENTER_VERTICAL);
            createBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
            createBtn.setBackground(borderedCard(SURFACE_2, OUTLINE, 14));

            ImageView plusIcon = new ImageView(this);
            plusIcon.setImageResource(R.drawable.ic_playlist_add);
            plusIcon.setColorFilter(ACCENT);
            createBtn.addView(plusIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView btnText = text("Создать новый плейлист", 15, TEXT, true);
            createBtn.addView(btnText, margin(12, 0, 0, 0));
            body.addView(createBtn, margin(18, 4, 18, 12));

            createBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showCreatePlaylistDialog(); }
            });

            ArrayList<Playlist> playlists = Store.getPlaylists();
            if (playlists.isEmpty()) {
                body.addView(emptyView("Нет созданных плейлистов", "Создайте плейлист и добавляйте в него любимые треки", null), margin(18, 24, 18, 0));
            } else {
                for (final Playlist p : playlists) {
                    body.addView(buildPlaylistRow(p), margin(18, 4, 18, 4));
                }
            }

        } else if (libraryTab == 1) {
            // Favorites Tab
            final ArrayList<Track> favs = Store.getFavorites();
            if (favs.isEmpty()) {
                body.addView(emptyView("В избранном пока пусто", "Нажмите сердечко на любом треке, чтобы добавить его сюда", null), margin(18, 32, 18, 0));
            } else {
                LinearLayout actionRow = new LinearLayout(this);
                actionRow.setGravity(Gravity.CENTER_VERTICAL);
                actionRow.setPadding(dp(18), 0, dp(18), dp(10));

                LinearLayout playAll = pillIcon(R.drawable.ic_play_arrow, "Играть всё (" + favs.size() + ")", TEXT, BG);
                actionRow.addView(playAll);
                playAll.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { playTracks(favs, 0, true); }
                });

                body.addView(actionRow);
                addTrackList(body, favs, 200);
            }

        } else if (libraryTab == 2) {
            // History Tab
            final ArrayList<Track> hist = Store.getHistory();
            if (hist.isEmpty()) {
                body.addView(emptyView("История прослушиваний пуста", "Здесь будут отображаться недавно прослушанные треки", null), margin(18, 32, 18, 0));
            } else {
                LinearLayout actionRow = new LinearLayout(this);
                actionRow.setGravity(Gravity.CENTER_VERTICAL);
                actionRow.setPadding(dp(18), 0, dp(18), dp(10));

                LinearLayout playAll = pillIcon(R.drawable.ic_play_arrow, "Слушать снова", TEXT, BG);
                actionRow.addView(playAll);
                playAll.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { playTracks(hist, 0, true); }
                });

                TextView clearBtn = pill("Очистить историю", SURFACE_3, MUTED);
                actionRow.addView(clearBtn, margin(10, 0, 0, 0));
                clearBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Store.saveTracks("history", new ArrayList<Track>());
                        showLibrary();
                    }
                });

                body.addView(actionRow);
                addTrackList(body, hist, 100);
            }

        } else if (libraryTab == 3) {
            // Offline / Downloads Tab
            final ArrayList<Track> offline = Store.getOfflineTracks();
            if (offline.isEmpty()) {
                body.addView(emptyView("Нет сохраненных треков", "Вы можете сохранить любые песни для прослушивания без интернета", null), margin(18, 32, 18, 0));
            } else {
                LinearLayout actionRow = new LinearLayout(this);
                actionRow.setGravity(Gravity.CENTER_VERTICAL);
                actionRow.setPadding(dp(18), 0, dp(18), dp(10));

                LinearLayout playAll = pillIcon(R.drawable.ic_play_arrow, "Слушать офлайн (" + offline.size() + ")", TEXT, BG);
                actionRow.addView(playAll);
                playAll.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { playTracks(offline, 0, true); }
                });

                body.addView(actionRow);
                addTrackList(body, offline, 200);
            }
        }

        setPageView(scroll);
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        b.setTitle("Новый плейлист");
        final EditText input = new EditText(this);
        input.setHint("Название плейлиста");
        input.setSingleLine(true);
        b.setView(input);
        b.setPositiveButton("Создать", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    Store.createPlaylist(name);
                    showLibrary();
                }
            }
        });
        b.setNegativeButton("Отмена", null);
        b.show();
    }

    /* ------------------------------------------------------------------ */
    /* ANIME DETAIL PAGE                                                   */
    /* ------------------------------------------------------------------ */
    public void showAnime(final String slug, final String fallbackTitle) {
        pageBeforeDetail = page;
        page = Page.DETAIL;
        final ScrollView scroll = pageScroll();
        final LinearLayout body = body(scroll);

        body.addView(headerBar(fallbackTitle, true));
        final ProgressBar pb = progressView();
        body.addView(pb, margin(0, 36, 0, 0));
        setPageView(scroll);

        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ApiClient.AnimeResult res = ApiClient.anime(slug);
                    main.post(new Runnable() {
                        @Override public void run() {
                            renderAnimeDetail(body, res);
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            body.removeAllViews();
                            body.addView(headerBar(fallbackTitle, true));
                            body.addView(emptyView("Не удалось загрузить данные аниме", "Проверьте сетевое соединение", new View.OnClickListener() {
                                @Override public void onClick(View v) { showAnime(slug, fallbackTitle); }
                            }), margin(18, 36, 18, 0));
                        }
                    });
                }
            }
        });
    }

    private void renderAnimeDetail(LinearLayout body, final ApiClient.AnimeResult res) {
        body.removeAllViews();
        final AnimeInfo info = res.info;
        body.addView(headerBar(info.name, true));

        // Anime Header Card
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setPadding(dp(18), dp(12), dp(18), dp(12));

        ImageView cover = coverView(dp(110), dp(150), 12);
        ImageLoader.load(cover, info.cover, false);
        hero.addView(cover, new LinearLayout.LayoutParams(dp(110), dp(150)));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setPadding(dp(14), 0, 0, 0);

        TextView title = text(info.name, 19, TEXT, true);
        meta.addView(title);

        String sub = (info.year > 0 ? String.valueOf(info.year) : "") +
                (!info.season.isEmpty() ? (" · " + seasonNameRu(info.season)) : "") +
                (!info.studio.isEmpty() ? (" · " + info.studio) : "");
        if (!sub.isEmpty()) meta.addView(text(sub, 13, MUTED, false), margin(0, 4, 0, 0));

        if (info.malId > 0) {
            meta.addView(text("MAL ID: " + info.malId, 12, DIM, false), margin(0, 2, 0, 0));
        }

        // Action Buttons
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.CENTER_VERTICAL);

        if (!res.tracks.isEmpty()) {
            LinearLayout playAll = pillIcon(R.drawable.ic_play_arrow, "Слушать всё", TEXT, BG);
            btns.addView(playAll, margin(0, 10, 8, 0));
            playAll.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { playTracks(res.tracks, 0, true); }
            });

            ImageView shuf = iconButton(R.drawable.ic_shuffle, TEXT, dp(18), dp(8), round(SURFACE_3, 999), new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Store.setShuffle(true);
                    ArrayList<Track> s = new ArrayList<>(res.tracks);
                    Collections.shuffle(s);
                    playTracks(s, 0, true);
                }
            });
            btns.addView(shuf, marginParams(0, 10, 0, 0, dp(36), dp(36)));
        }
        meta.addView(btns);
        hero.addView(meta, new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(hero);

        // Synopsis
        if (!info.synopsis.isEmpty()) {
            sectionTitle(body, "Описание");
            TextView syn = text(info.synopsis, 13, MUTED, false);
            syn.setLineSpacing(dp(2), 1f);
            body.addView(syn, margin(18, 0, 18, 12));
        }

        // Split into Openings and Endings
        ArrayList<Track> ops = new ArrayList<>();
        ArrayList<Track> eds = new ArrayList<>();
        ArrayList<Track> ins = new ArrayList<>();
        for (Track t : res.tracks) {
            if ("OP".equals(t.type)) ops.add(t);
            else if ("ED".equals(t.type)) eds.add(t);
            else ins.add(t);
        }

        if (!ops.isEmpty()) {
            sectionTitle(body, "Опенинги (" + ops.size() + ")");
            addTrackList(body, ops, 50);
        }
        if (!eds.isEmpty()) {
            sectionTitle(body, "Эндинги (" + eds.size() + ")");
            addTrackList(body, eds, 50);
        }
        if (!ins.isEmpty()) {
            sectionTitle(body, "Вставки и темы (" + ins.size() + ")");
            addTrackList(body, ins, 50);
        }
    }

    /* ------------------------------------------------------------------ */
    /* ARTIST DETAIL PAGE                                                  */
    /* ------------------------------------------------------------------ */
    public void showArtist(final String slug, final String fallbackName) {
        pageBeforeDetail = page;
        page = Page.DETAIL;
        final ScrollView scroll = pageScroll();
        final LinearLayout body = body(scroll);

        body.addView(headerBar(fallbackName, true));
        body.addView(progressView(), margin(0, 36, 0, 0));
        setPageView(scroll);

        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ApiClient.ArtistResult res = ApiClient.artist(slug);
                    main.post(new Runnable() {
                        @Override public void run() {
                            renderArtistDetail(body, res);
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() {
                            body.removeAllViews();
                            body.addView(headerBar(fallbackName, true));
                            body.addView(emptyView("Не удалось загрузить данные исполнителя", "Проверьте сетевое соединение", null), margin(18, 36, 18, 0));
                        }
                    });
                }
            }
        });
    }

    private void renderArtistDetail(LinearLayout body, final ApiClient.ArtistResult res) {
        body.removeAllViews();
        body.addView(headerBar(res.name, true));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(10), dp(18), dp(12));

        TextView name = text(res.name, 24, TEXT, true);
        hero.addView(name);

        if (!res.information.isEmpty()) {
            hero.addView(text(res.information, 13, MUTED, false), margin(0, 6, 0, 0));
        }

        if (!res.tracks.isEmpty()) {
            LinearLayout playAll = pillIcon(R.drawable.ic_play_arrow, "Слушать всё (" + res.tracks.size() + ")", TEXT, BG);
            hero.addView(playAll, margin(0, 10, 0, 0));
            playAll.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { playTracks(res.tracks, 0, true); }
            });
        }
        body.addView(hero);

        sectionTitle(body, "Все треки исполнителя");
        addTrackList(body, res.tracks, 100);
    }

    /* ------------------------------------------------------------------ */
    /* FULL PLAYER / NOW PLAYING SHEET                                     */
    /* ------------------------------------------------------------------ */
    private void showPlayerDialog() {
        final Track track = Store.getCurrentTrack();
        if (track == null) { toast("Сначала выберите трек"); return; }

        playerDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window window = playerDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(BG));
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(SURFACE_1);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(16), dp(22), dp(24));

        // Drag bar / Header
        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView closeBtn = iconButton(R.drawable.ic_close, TEXT, dp(22), dp(8), null, new View.OnClickListener() {
            @Override public void onClick(View v) { playerDialog.dismiss(); }
        });
        topRow.addView(closeBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView headerTitle = text("СЕЙЧАС ИГРАЕТ", 12, MUTED, true);
        headerTitle.setGravity(Gravity.CENTER);
        headerTitle.setLetterSpacing(0.06f);
        topRow.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView queueBtn = iconButton(R.drawable.ic_queue, TEXT, dp(22), dp(8), null, new View.OnClickListener() {
            @Override public void onClick(View v) { showQueueDialog(); }
        });
        topRow.addView(queueBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        box.addView(topRow);

        // Big Album Cover
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int coverDim = Math.min(screenW - dp(48), dp(320));

        playerCover = coverView(coverDim, coverDim, 20);
        playerCover.setBackground(borderedCard(SURFACE_2, OUTLINE, 20));
        ImageLoader.load(playerCover, !track.cover.isEmpty() ? track.cover : track.coverSmall, false);
        box.addView(playerCover, centeredParams(coverDim, coverDim));

        // Track Meta + Heart Favorite
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setPadding(0, dp(18), 0, dp(6));

        LinearLayout metaText = new LinearLayout(this);
        metaText.setOrientation(LinearLayout.VERTICAL);

        playerTitle = text(track.title, 21, TEXT, true);
        playerTitle.setMaxLines(2);
        playerTitle.setEllipsize(TextUtils.TruncateAt.END);
        metaText.addView(playerTitle);

        playerSubtitle = text(track.displayArtist(), 15, MUTED, false);
        playerSubtitle.setSingleLine(true);
        playerSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        metaText.addView(playerSubtitle, margin(0, 3, 0, 0));

        playerBadge = text(track.displayTheme() + " · " + track.animeName, 12, ACCENT, false);
        playerBadge.setSingleLine(true);
        playerBadge.setEllipsize(TextUtils.TruncateAt.END);
        metaText.addView(playerBadge, margin(0, 3, 0, 0));
        metaRow.addView(metaText, new LinearLayout.LayoutParams(0, -2, 1f));

        playerFav = new ImageView(this);
        boolean fav = Store.isFavorite(track.id);
        playerFav.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        playerFav.setColorFilter(fav ? HEART : MUTED);
        playerFav.setPadding(dp(8), dp(8), dp(8), dp(8));
        playerFav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean nowFav = Store.toggleFavorite(track);
                playerFav.setImageResource(nowFav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
                playerFav.setColorFilter(nowFav ? HEART : MUTED);
                toast(nowFav ? "Добавлено в избранное" : "Убрано из избранного");
            }
        });
        metaRow.addView(playerFav, new LinearLayout.LayoutParams(dp(44), dp(44)));
        box.addView(metaRow);

        // Seek Bar
        playerSeek = new SeekBar(this);
        playerSeek.setMax(1000);
        playerSeek.setProgress(0);
        box.addView(playerSeek, margin(0, 10, 0, 0));

        LinearLayout timeRow = new LinearLayout(this);
        playerElapsed = text("0:00", 12, MUTED, false);
        playerDuration = text("0:00", 12, MUTED, false);
        timeRow.addView(playerElapsed, new LinearLayout.LayoutParams(0, -2, 1f));
        timeRow.addView(playerDuration);
        box.addView(timeRow, margin(0, 4, 0, 12));

        // Transport Controls Row (Shuffle, Prev, Big Play, Next, Repeat)
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        playerShuffle = iconButton(R.drawable.ic_shuffle, Store.isShuffle() ? ACCENT : DIM, dp(22), dp(10), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean next = !Store.isShuffle();
                Store.setShuffle(next);
                playerShuffle.setColorFilter(next ? ACCENT : DIM);
                toast(next ? "Случайный порядок включен" : "Случайный порядок выключен");
            }
        });
        controls.addView(playerShuffle, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView prevBtn = iconButton(R.drawable.ic_skip_previous, TEXT, dp(30), dp(10), null, new View.OnClickListener() {
            @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_PREVIOUS); }
        });
        controls.addView(prevBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        FrameLayout playBtnWrap = new FrameLayout(this);
        playBtnWrap.setBackground(round(TEXT, 999));
        playerToggle = new ImageView(this);
        playerToggle.setImageResource(Store.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        playerToggle.setColorFilter(BG);
        playBtnWrap.addView(playerToggle, centeredParams(dp(30), dp(30)));
        playBtnWrap.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_TOGGLE); }
        });
        controls.addView(playBtnWrap, marginParams(6, 0, 6, 0, dp(64), dp(64)));

        ImageView nextBtn = iconButton(R.drawable.ic_skip_next, TEXT, dp(30), dp(10), null, new View.OnClickListener() {
            @Override public void onClick(View v) { PlaybackService.command(MainActivity.this, PlaybackService.ACTION_NEXT); }
        });
        controls.addView(nextBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        int repeatMode = Store.getRepeatMode();
        playerRepeat = iconButton(repeatMode == 2 ? R.drawable.ic_repeat_one : R.drawable.ic_repeat, repeatMode > 0 ? ACCENT : DIM, dp(22), dp(10), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                int next = (Store.getRepeatMode() + 1) % 3;
                Store.setRepeatMode(next);
                playerRepeat.setImageResource(next == 2 ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
                playerRepeat.setColorFilter(next > 0 ? ACCENT : DIM);
                toast(next == 0 ? "Повтор выключен" : next == 1 ? "Повтор очереди" : "Повтор трека");
            }
        });
        controls.addView(playerRepeat, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(controls, margin(0, 10, 0, 16));

        // Quick action buttons
        LinearLayout extraRow = new LinearLayout(this);
        extraRow.setGravity(Gravity.CENTER);

        LinearLayout addPl = pillIcon(R.drawable.ic_playlist_add, "В плейлист", TEXT, SURFACE_3);
        extraRow.addView(addPl, margin(0, 0, 8, 0));
        addPl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { addTrackToPlaylist(track); }
        });

        LinearLayout dlBtn = pillIcon(R.drawable.ic_download, Store.isOffline(track) ? "Сохранено" : "Скачать", TEXT, SURFACE_3);
        extraRow.addView(dlBtn, margin(0, 0, 8, 0));
        dlBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Store.isOffline(track)) downloadTrack(track);
                else toast("Трек уже доступен офлайн");
            }
        });

        if (!track.animeSlug.isEmpty()) {
            TextView animBtn = pill("Аниме", SURFACE_3, TEXT);
            extraRow.addView(animBtn);
            animBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    playerDialog.dismiss();
                    showAnime(track.animeSlug, track.animeName);
                }
            });
        }
        box.addView(extraRow);

        scroll.addView(box);
        playerDialog.setContentView(scroll);

        playerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    long dur = Store.getDuration();
                    if (dur > 0) {
                        long cur = dur * progress / 1000L;
                        playerElapsed.setText(formatTime(cur));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                long duration = Store.getDuration();
                if (duration > 0) {
                    PlaybackService.seek(MainActivity.this, duration * bar.getProgress() / 1000L);
                }
            }
        });

        playerDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface dialog) { stopPlayerTicker(); }
        });

        playerDialog.show();
        updatePlayerDialog(true);
        startPlayerTicker();
    }

    private void updatePlayerDialog(boolean forceMeta) {
        if (playerDialog == null || !playerDialog.isShowing()) return;
        Track track = Store.getCurrentTrack();
        if (track == null) return;

        if (forceMeta) {
            playerTitle.setText(track.title);
            playerSubtitle.setText(track.displayArtist());
            playerBadge.setText(track.displayTheme() + " · " + track.animeName);
            ImageLoader.load(playerCover, !track.cover.isEmpty() ? track.cover : track.coverSmall, false);
            boolean fav = Store.isFavorite(track.id);
            playerFav.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            playerFav.setColorFilter(fav ? HEART : MUTED);
        }

        boolean playing = Store.isPlaying();
        playerToggle.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

        long pos = Store.getPosition();
        long dur = Store.getDuration();
        if (dur > 0) {
            int progress = (int) (pos * 1000L / dur);
            playerSeek.setProgress(Math.max(0, Math.min(1000, progress)));
            playerElapsed.setText(formatTime(pos));
            playerDuration.setText(formatTime(dur));
        } else {
            playerSeek.setProgress(0);
            playerElapsed.setText("0:00");
            playerDuration.setText("0:00");
        }
    }

    private void startPlayerTicker() {
        stopPlayerTicker();
        playerTicker = new Runnable() {
            @Override public void run() {
                if (playerDialog != null && playerDialog.isShowing()) {
                    updatePlayerDialog(false);
                    main.postDelayed(this, 350);
                }
            }
        };
        main.postDelayed(playerTicker, 350);
    }

    private void stopPlayerTicker() {
        if (playerTicker != null) {
            main.removeCallbacks(playerTicker);
            playerTicker = null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* QUEUE SHEET                                                         */
    /* ------------------------------------------------------------------ */
    private void showQueueDialog() {
        final ArrayList<Track> queue = Store.getQueue();
        if (queue.isEmpty()) { toast("Очередь воспроизведения пуста"); return; }

        final Dialog qDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        Window w = qDialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(BG));
            w.setStatusBarColor(BG);
            w.setNavigationBarColor(SURFACE_1);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(24));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(12), dp(18), dp(8));

        ImageView close = iconButton(R.drawable.ic_close, TEXT, dp(22), dp(6), null, new View.OnClickListener() {
            @Override public void onClick(View v) { qDialog.dismiss(); }
        });
        top.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = text("Очередь (" + queue.size() + ")", 18, TEXT, true);
        top.addView(title, margin(10, 0, 0, 0));
        body.addView(top);

        int currentIdx = Store.getQueueIndex();
        for (int i = 0; i < queue.size(); i++) {
            final int targetIdx = i;
            final Track t = queue.get(i);
            boolean isCur = (i == currentIdx);

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(8), dp(18), dp(8));
            row.setBackground(isCur ? round(SURFACE_2, 10) : null);

            ImageView cov = coverView(dp(44), dp(44), 8);
            ImageLoader.load(cov, !t.coverSmall.isEmpty() ? t.coverSmall : t.cover, true);
            row.addView(cov, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout lbls = new LinearLayout(this);
            lbls.setOrientation(LinearLayout.VERTICAL);
            lbls.setPadding(dp(12), 0, dp(8), 0);

            TextView tt = text(t.title, 14, isCur ? ACCENT : TEXT, isCur);
            tt.setSingleLine(true);
            tt.setEllipsize(TextUtils.TruncateAt.END);
            lbls.addView(tt);

            TextView sub = text(t.displayArtist() + " · " + t.displayTheme(), 12, MUTED, false);
            sub.setSingleLine(true);
            lbls.addView(sub, margin(0, 2, 0, 0));
            row.addView(lbls, new LinearLayout.LayoutParams(0, -1, 1f));

            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    qDialog.dismiss();
                    playTracks(queue, targetIdx, true);
                }
            });
            body.addView(row);
        }

        scroll.addView(body);
        qDialog.setContentView(scroll);
        qDialog.show();
    }

    /* ------------------------------------------------------------------ */
    /* PLAYBACK / QUEUE ACTIONS                                            */
    /* ------------------------------------------------------------------ */
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

    private void addTrackList(LinearLayout body, List<Track> tracks, int limit) {
        if (tracks == null) return;
        int count = 0;
        for (Track t : tracks) {
            if (t == null) continue;
            addTrackRow(body, t, tracks);
            if (++count >= limit) break;
        }
    }

    private void addTrackRow(LinearLayout body, final Track track, final List<Track> context) {
        final LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(8), dp(8));
        row.setBackground(round(SURFACE_1, 12));

        ImageView image = coverView(dp(50), dp(50), 10);
        ImageLoader.load(image, !track.coverSmall.isEmpty() ? track.coverSmall : track.cover, true);
        row.addView(image, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, dp(4), 0);

        TextView title = text(track.title, 14, TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);

        TextView subtitle = text(track.displayArtist(), 12, MUTED, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(subtitle, margin(0, 2, 0, 0));

        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(track.displayTheme(), 11, ACCENT, true);
        badgeRow.addView(badge);

        TextView anName = text(" · " + track.animeName, 11, DIM, false);
        anName.setSingleLine(true);
        anName.setEllipsize(TextUtils.TruncateAt.END);
        badgeRow.addView(anName);

        if (Store.isOffline(track)) {
            TextView off = text(" · офлайн", 11, GREEN, false);
            badgeRow.addView(off);
        }
        labels.addView(badgeRow, margin(0, 2, 0, 0));

        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        // Material action buttons
        ImageView playBtn = iconButton(R.drawable.ic_play_arrow, TEXT, dp(20), dp(8), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                playTracks(context, indexOf(context, track), true);
            }
        });
        row.addView(playBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        ImageView moreBtn = iconButton(R.drawable.ic_more_vert, MUTED, dp(20), dp(8), null, new View.OnClickListener() {
            @Override public void onClick(View v) {
                trackMenu(v, track, context);
            }
        });
        row.addView(moreBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));

        body.addView(row, margin(18, 3, 18, 3));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playTracks(context, indexOf(context, track), true); }
        });
    }

    private void trackMenu(View anchor, final Track track, final List<Track> context) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, Store.isFavorite(track.id) ? "Убрать из избранного" : "В избранное");
        popup.getMenu().add(0, 2, 1, Store.isOffline(track) ? "Удалить из офлайн" : "Скачать офлайн");
        popup.getMenu().add(0, 3, 2, "Добавить в очередь");
        popup.getMenu().add(0, 4, 3, "Добавить в плейлист");
        if (!track.animeSlug.isEmpty()) popup.getMenu().add(0, 5, 4, "Перейти к аниме");
        if (!track.artistSlug.isEmpty()) popup.getMenu().add(0, 6, 5, "Перейти к исполнителю");

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
        queue.add(track.copy());
        Store.saveQueue(queue, Store.getQueueIndex());
        toast("Добавлено в очередь (" + queue.size() + ")");
    }

    private void addTrackToPlaylist(final Track track) {
        final ArrayList<Playlist> playlists = Store.getPlaylists();
        if (playlists.isEmpty()) {
            toast("Сначала создайте плейлист в медиатеке");
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        b.setTitle("Выберите плейлист");
        String[] names = new String[playlists.size()];
        for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
        b.setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                Playlist p = playlists.get(which);
                boolean added = Store.addToPlaylist(p.id, track);
                toast(added ? "Добавлено в «" + p.name + "»" : "Трек уже есть в плейлисте");
            }
        });
        b.show();
    }

    private void downloadTrack(final Track track) {
        toast("Начало загрузки «" + track.title + "»...");
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    File file = Store.offlineFile(track);
                    URL u = new URL(track.playableUrl());
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestProperty("User-Agent", "AniBeat/1.0");
                    c.connect();
                    InputStream in = new BufferedInputStream(c.getInputStream());
                    FileOutputStream out = new FileOutputStream(file);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                    out.flush();
                    out.close();
                    in.close();
                    c.disconnect();
                    Store.saveOffline(track, file);
                    main.post(new Runnable() {
                        @Override public void run() {
                            toast("Трек «" + track.title + "» сохранен офлайн");
                            if (page == Page.LIBRARY) showLibrary();
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { toast("Ошибка скачивания"); }
                    });
                }
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* UI HELPER BUILDERS & CARDS                                          */
    /* ------------------------------------------------------------------ */
    private LinearLayout buildAnimeCard(final AnimeInfo anime) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(round(SURFACE_1, 12));

        ImageView cover = coverView(dp(48), dp(68), 8);
        ImageLoader.load(cover, anime.cover, true);
        row.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(68)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);

        TextView name = text(anime.name, 15, TEXT, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);

        String meta = (anime.year > 0 ? String.valueOf(anime.year) : "") +
                (!anime.season.isEmpty() ? (" · " + seasonNameRu(anime.season)) : "");
        if (!meta.isEmpty()) labels.addView(text(meta, 12, MUTED, false), margin(0, 3, 0, 0));

        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAnime(anime.slug, anime.name); }
        });
        return row;
    }

    private LinearLayout buildArtistChip(final String artistName) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(round(SURFACE_1, 12));

        TextView name = text(artistName, 14, TEXT, true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView sub = text("Исполнитель", 12, MUTED, false);
        row.addView(sub);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showArtist(artistName.toLowerCase(Locale.US).replace(" ", "-"), artistName); }
        });
        return row;
    }

    private LinearLayout buildSuggestionRow(final String query, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(round(SURFACE_1, 10));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(DIM);
        row.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView text = text(query, 14, TEXT, false);
        row.addView(text, margin(10, 0, 0, 0));
        row.setOnClickListener(click);
        return row;
    }

    private LinearLayout buildPlaylistRow(final Playlist playlist) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(round(SURFACE_1, 12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_library);
        icon.setColorFilter(ACCENT);
        row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(12), 0, 0, 0);

        TextView title = text(playlist.name, 15, TEXT, true);
        labels.addView(title);

        TextView count = text(playlist.tracks.size() + " треков", 12, MUTED, false);
        labels.addView(count, margin(0, 2, 0, 0));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!playlist.tracks.isEmpty()) {
            ImageView play = iconButton(R.drawable.ic_play_arrow, TEXT, dp(20), dp(8), null, new View.OnClickListener() {
                @Override public void onClick(View v) { playTracks(playlist.tracks, 0, true); }
            });
            row.addView(play, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPlaylistDetails(playlist); }
        });
        return row;
    }

    private void showPlaylistDetails(final Playlist p) {
        pageBeforeDetail = page;
        page = Page.DETAIL;
        final ScrollView scroll = pageScroll();
        final LinearLayout body = body(scroll);

        body.addView(headerBar(p.name, true));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18), dp(10), dp(18), dp(12));

        hero.addView(text(p.name, 22, TEXT, true));
        hero.addView(text(p.tracks.size() + " треков", 13, MUTED, false), margin(0, 3, 0, 0));

        LinearLayout btns = new LinearLayout(this);
        if (!p.tracks.isEmpty()) {
            LinearLayout play = pillIcon(R.drawable.ic_play_arrow, "Слушать всё", TEXT, BG);
            btns.addView(play, margin(0, 10, 8, 0));
            play.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { playTracks(p.tracks, 0, true); }
            });
        }
        TextView del = pill("Удалить плейлист", SURFACE_3, MUTED);
        btns.addView(del, margin(0, 10, 0, 0));
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Store.deletePlaylist(p.id);
                toast("Плейлист удален");
                onBackPressed();
            }
        });
        hero.addView(btns);
        body.addView(hero);

        if (p.tracks.isEmpty()) {
            body.addView(emptyView("В этом плейлисте пока нет песен", "Добавляйте треки через меню трех точек", null), margin(18, 30, 18, 0));
        } else {
            addTrackList(body, p.tracks, 200);
        }
        setPageView(scroll);
    }

    /* ------------------------------------------------------------------ */
    /* COMMON ATOMIC UI HELPERS                                            */
    /* ------------------------------------------------------------------ */
    private LinearLayout headerBar(String title, boolean back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(12), dp(16), dp(8));
        bar.setBackgroundColor(BG);

        if (back) {
            ImageView arrow = iconButton(R.drawable.ic_arrow_back, TEXT, dp(22), dp(6), null, new View.OnClickListener() {
                @Override public void onClick(View v) { onBackPressed(); }
            });
            bar.addView(arrow, new LinearLayout.LayoutParams(dp(36), dp(36)));
        } else {
            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.ic_notification);
            logo.setColorFilter(TEXT);
            logo.setPadding(dp(6), dp(6), dp(6), dp(6));
            logo.setBackground(round(SURFACE_3, 10));
            bar.addView(logo, new LinearLayout.LayoutParams(dp(34), dp(34)));
        }

        TextView heading = text(title, 19, TEXT, true);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(heading, margin(12, 0, 0, 0));
        return bar;
    }

    private void sectionTitle(LinearLayout body, String title) {
        TextView tv = text(title, 16, TEXT, true);
        body.addView(tv, margin(18, 14, 18, 8));
    }

    private TextView filterChip(String label, boolean active, View.OnClickListener click) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(13);
        chip.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        chip.setTextColor(active ? BG : MUTED);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(7), dp(14), dp(7));
        chip.setBackground(active ? round(TEXT, 999) : borderedCard(SURFACE_2, OUTLINE, 999));
        chip.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
        lp.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(lp);
        return chip;
    }

    private TextView pill(String label, int bgColor, int textColor) {
        TextView p = new TextView(this);
        p.setText(label);
        p.setTextSize(13);
        p.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        p.setTextColor(textColor);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(16), dp(8), dp(16), dp(8));
        p.setBackground(round(bgColor, 999));
        return p;
    }

    private LinearLayout pillIcon(int iconRes, String label, int bgColor, int textColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(14), dp(8), dp(16), dp(8));
        row.setBackground(round(bgColor, 999));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(textColor);
        row.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView tv = text(label, 13, textColor, true);
        row.addView(tv, marginParams(6, 0, 0, 0, -2, -2));
        return row;
    }

    private ImageView iconButton(int iconRes, int tint, int iconSizeDp, int padDp, GradientDrawable bg, View.OnClickListener click) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(tint);
        iv.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        if (bg != null) iv.setBackground(bg);
        iv.setOnClickListener(click);
        return iv;
    }

    private ImageView coverView(int w, int h, int radiusDp) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackground(round(SURFACE_2, radiusDp));
        iv.setClipToOutline(true);
        return iv;
    }

    private TextView text(String content, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return tv;
    }

    private ProgressBar progressView() {
        ProgressBar pb = new ProgressBar(this);
        pb.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44)));
        return pb;
    }

    private LinearLayout emptyView(String title, String subtitle, View.OnClickListener retryClick) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(20), dp(32), dp(20), dp(32));
        box.setBackground(round(SURFACE_1, 14));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_explore);
        icon.setColorFilter(DIM);
        box.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView t = text(title, 15, TEXT, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, margin(0, 10, 0, 0));

        TextView s = text(subtitle, 13, MUTED, false);
        s.setGravity(Gravity.CENTER);
        box.addView(s, margin(0, 4, 0, 0));

        if (retryClick != null) {
            TextView btn = pill("Повторить", SURFACE_3, TEXT);
            box.addView(btn, margin(0, 14, 0, 0));
            btn.setOnClickListener(retryClick);
        }
        return box;
    }

    public GradientDrawable round(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public GradientDrawable borderedCard(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fillColor);
        d.setStroke(dp(1), strokeColor);
        d.setCornerRadius(dp(radiusDp));
        return d;
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
        body.setPadding(0, 0, 0, dp(80));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        return body;
    }

    private void setPageView(View view) {
        content.removeAllViews();
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
        refreshMiniPlayer();
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private LinearLayout.LayoutParams marginParams(int l, int t, int r, int b, int w, int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private LinearLayout.LayoutParams marginParams(int l, int t, int r, int b, int w, int h, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h, weight);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private LinearLayout.LayoutParams centeredParams(int w, int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && view != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private int indexOf(List<Track> list, Track target) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(target.id)) return i;
        return 0;
    }

    private String formatTime(long ms) {
        long sec = Math.max(0, ms / 1000L);
        long m = sec / 60;
        long s = sec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    private String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour >= 6 && hour < 12) return "Доброе утро";
        if (hour >= 12 && hour < 18) return "Добрый день";
        if (hour >= 18 && hour < 23) return "Добрый вечер";
        return "Доброй ночи";
    }

    private String seasonNameRu(String s) {
        if ("Winter".equalsIgnoreCase(s)) return "Зима";
        if ("Spring".equalsIgnoreCase(s)) return "Весна";
        if ("Summer".equalsIgnoreCase(s)) return "Лето";
        if ("Fall".equalsIgnoreCase(s)) return "Осень";
        return s;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
