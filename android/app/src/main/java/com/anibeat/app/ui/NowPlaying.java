package com.anibeat.app.ui;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.SliderView;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;

import androidx.media3.ui.PlayerView;

/** Полноэкранный плеер — порт components/NowPlaying.tsx. */
public class NowPlaying extends FrameLayout {

    private final MainActivity activity;
    private final View tint;
    private final FrameLayout stage;
    private final CoverView art;
    private final PlayerView videoView;
    private final FrameLayout buffering;
    private final LinearLayout controls;
    private final View videoTopBar;
    private final View videoCenter;
    private final LinearLayout videoBottom;
    private final TextView title;
    private final LinearLayout artistsRow;
    private final LinearLayout animeRow;
    private final TextView themeLabel;
    private final TextView animeLabel;
    private final FrameLayout favButton;
    private final SliderView seek;
    private final TextView timeLeft;
    private final TextView timeRight;
    private final FrameLayout shuffleBtn;
    private final FrameLayout prevBtn;
    private final FrameLayout playBtn;
    private final FrameLayout nextBtn;
    private final FrameLayout repeatBtn;
    private final LinearLayout videoButton;
    private final LinearLayout fullButton;
    private final LinearLayout downloadButton;
    private final LinearLayout queueButton;
    private final TextView hint;

    private boolean open;
    private boolean fullscreen;
    private float touchStartY;
    private boolean dragging;
    private boolean scrubbing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshTime();
            handler.postDelayed(this, 250);
        }
    };
    private final Runnable hideControls = () -> {
        if (Player.videoMode() && Player.isPlaying()) fadeControls(false);
    };

    public NowPlaying(MainActivity activity) {
        super(activity);
        this.activity = activity;
        Context c = activity;

        tint = new View(c);
        tint.setBackgroundColor(BASE_BG);
        addView(tint, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout root = Ui.column(c);
        addView(root, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Верхняя панель: свернуть / грабер / меню
        LinearLayout bar = Ui.row(c);
        bar.setPadding(Theme.dp(c, 8), Theme.dp(c, 4), Theme.dp(c, 8), Theme.dp(c, 4));
        FrameLayout collapse = Ui.iconButton(c, "expand_more", 24, 0xD9FFFFFF, this::close);
        bar.addView(collapse);
        View grabber = new View(c);
        grabber.setBackground(Ui.rounded(0x4DFFFFFF, Theme.dpF(c, 3f)));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Theme.dp(c, 36), Theme.dp(c, 5));
        gp.gravity = Gravity.CENTER;
        bar.addView(grabber, gp);
        View spacer = new View(c);
        bar.addView(spacer, Ui.lpw(1f));
        FrameLayout menu = Ui.iconButton(c, "more_horiz", 22, 0xD9FFFFFF, () -> {
            Models.Track t = Player.current();
            if (t != null) activity.sheets().openTrackMenu(t);
        });
        bar.addView(menu);
        root.addView(bar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 44)));
        final View dragHandle = bar;

        ScrollView scroll = new ScrollView(c);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = Ui.column(c);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Theme.dp(c, 24), Theme.dp(c, 8), Theme.dp(c, 24), Theme.dp(c, 24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Сцена: обложка ⇄ видео
        stage = new FrameLayout(c);
        int stageSize = Math.min(Theme.dp(c, 380), (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82f));
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(stageSize, stageSize);
        stageParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(stage, stageParams);

        art = new CoverView(c);
        art.setRadiusDp(14f);
        art.setIconSizeDp(56f);
        stage.addView(art, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        videoView = new PlayerView(c);
        videoView.setUseController(false);
        videoView.setShutterBackgroundColor(Color.BLACK);
        videoView.setVisibility(GONE);
        stage.addView(videoView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        buffering = new FrameLayout(c);
        com.anibeat.app.core.Spinner spinner = new com.anibeat.app.core.Spinner(c);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Theme.dp(c, 38), Theme.dp(c, 38));
        sp.gravity = Gravity.CENTER;
        buffering.addView(spinner, sp);
        buffering.setVisibility(GONE);
        stage.addView(buffering, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Оверлей видеоконтролов
        controls = Ui.column(c);
        controls.setVisibility(GONE);
        LinearLayout top = Ui.row(c);
        top.setPadding(Theme.dp(c, 6), Theme.dp(c, 6), Theme.dp(c, 6), 0);
        TextView exitFs = Ui.text(c, "Свернуть", 15f, Theme.ON);
        exitFs.setCompoundDrawablePadding(Theme.dp(c, 4));
        videoTopBar = exitFs;
        top.setGravity(Gravity.END);
        FrameLayout audioOnly = Ui.iconButton(c, "videocam_off", 21, Theme.ON, () -> {
            if (fullscreen) setFullscreen(false);
            Player.setVideoMode(false);
            refresh();
        });
        audioOnly.setBackground(Ui.rounded(0x66000000, Theme.dpF(c, 20f)));
        top.addView(audioOnly);
        controls.addView(top, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 36)));

        FrameLayout center = new FrameLayout(c);
        videoCenter = center;
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        controls.addView(center, cp);
        FrameLayout playBig = new FrameLayout(c);
        playBig.setBackground(Ui.rounded(0x73000000, Theme.dpF(c, 29f)));
        ImageView playIcon = Ui.icon(c, "pause", 30, Theme.ON);
        FrameLayout.LayoutParams pip = new FrameLayout.LayoutParams(Theme.dp(c, 30), Theme.dp(c, 30));
        pip.gravity = Gravity.CENTER;
        playBig.addView(playIcon, pip);
        FrameLayout.LayoutParams pbp = new FrameLayout.LayoutParams(Theme.dp(c, 58), Theme.dp(c, 58));
        pbp.gravity = Gravity.CENTER;
        center.addView(playBig, pbp);
        playBig.setOnClickListener(v -> {
            Player.toggle();
            poke();
        });

        videoBottom = Ui.row(c);
        videoBottom.setPadding(Theme.dp(c, 12), 0, Theme.dp(c, 12), Theme.dp(c, 8));
        SliderView videoSeek = new SliderView(c);
        videoSeek.setTrackHeightDp(4f);
        videoSeek.setShowThumb(false);
        videoSeek.setColors(0x33FFFFFF, 0xFFFFFFFF, Theme.ON);
        videoSeek.setRange(0f, 1000f);
        videoSeek.setOnChange(new SliderView.OnChange() {
            @Override
            public void onScrub(float value) {
                scrubbing = true;
                long duration = Player.duration();
                if (duration > 0) seek.setValue(value / 1000f * duration);
            }

            @Override
            public void onCommit(float value) {
                long duration = Player.duration();
                if (duration > 0) Player.seekTo((long) (value / 1000f * duration));
                scrubbing = false;
            }
        });
        videoBottom.addView(videoSeek, Ui.lpw(1f));
        FrameLayout fullBtn = Ui.iconButton(c, "fullscreen", 22, Theme.ON, () -> {
            setFullscreen(!fullscreen);
            poke();
        });
        videoBottom.addView(fullBtn);
        controls.addView(videoBottom, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 44)));
        stage.addView(controls, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        final SliderView compactSeek = videoSeek;

        // Информация + управление
        LinearLayout info = Ui.column(c);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = Theme.dp(c, 26);
        content.addView(info, ip);

        LinearLayout headRow = Ui.row(c);
        headRow.setGravity(Gravity.TOP);
        LinearLayout headTexts = Ui.column(c);
        title = Ui.heading(c, "", 22f, Theme.ON);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        artistsRow = Ui.row(c);
        animeRow = Ui.row(c);
        animeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeLabel = Ui.text(c, "", 13f, Theme.PRIMARY, true);
        animeLabel = Ui.text(c, "", 13f, 0x73FFFFFF);
        animeLabel.setSingleLine(true);
        animeLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        animeRow.addView(themeLabel);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        alp.leftMargin = Theme.dp(c, 6);
        animeRow.addView(animeLabel, alp);
        animeRow.addView(Ui.icon(c, "chevron_right", 13, 0x73FFFFFF));

        headTexts.addView(title);
        LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        arp.topMargin = Theme.dp(c, 2);
        headTexts.addView(artistsRow, arp);
        LinearLayout.LayoutParams anp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        anp.topMargin = Theme.dp(c, 4);
        headTexts.addView(animeRow, anp);
        headRow.addView(headTexts, Ui.lpw(1f));

        favButton = Ui.iconButton(c, "favorite_border", 22, Theme.ON, () -> {
            Models.Track t = Player.current();
            if (t == null) return;
            Library.toggleFavorite(t);
            refresh();
        });
        favButton.setBackground(Ui.rounded(0x1AFFFFFF, Theme.dpF(c, 18f)));
        LinearLayout.LayoutParams fp = Ui.lp(Theme.dp(c, 36), Theme.dp(c, 36));
        fp.topMargin = Theme.dp(c, 4);
        favButton.setLayoutParams(fp);
        headRow.addView(favButton);
        info.addView(headRow);
        animeRow.setOnClickListener(v -> goAnime());

        // Перемотка
        seek = new SliderView(c);
        seek.setTrackHeightDp(7f);
        seek.setColors(0x1FFFFFFF, Theme.ON, Theme.ON);
        seek.setRange(0f, 1000f);
        seek.setOnChange(new SliderView.OnChange() {
            @Override
            public void onScrub(float value) {
                scrubbing = true;
                long duration = Player.duration();
                if (duration > 0) timeLeft.setText(Format.time(value / 1000f * duration / 1000f));
            }

            @Override
            public void onCommit(float value) {
                long duration = Player.duration();
                if (duration > 0) Player.seekTo((long) (value / 1000f * duration));
                scrubbing = false;
                refreshTime();
            }
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Theme.dp(c, 24));
        seekParams.topMargin = Theme.dp(c, 18);
        info.addView(seek, seekParams);

        LinearLayout timeRow = Ui.row(c);
        timeLeft = Ui.text(c, "0:00", 11f, Theme.ON_VARIANT, true);
        timeRight = Ui.text(c, "-0:00", 11f, Theme.ON_VARIANT, true);
        timeRow.addView(timeLeft, Ui.lpw(1f));
        timeRight.setGravity(Gravity.END);
        timeRow.addView(timeRight);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Theme.dp(c, 2);
        info.addView(timeRow, tp);

        // Транспорт
        LinearLayout transport = Ui.row(c);
        transport.setGravity(Gravity.CENTER);
        transport.setPadding(Theme.dp(c, 8), 0, Theme.dp(c, 8), 0);
        shuffleBtn = Ui.iconButton(c, "shuffle", 22, 0x8CFFFFFF, () -> {
            Player.toggleShuffle();
            refresh();
        });
        prevBtn = Ui.iconButton(c, "skip_previous", 36, Theme.ON, () -> {
            Player.prev();
            refresh();
        });
        playBtn = new FrameLayout(c);
        playBtn.setBackground(Ui.rounded(Theme.ON, Theme.dpF(c, 34f)));
        ImageView playIconMain = Ui.icon(c, "play_arrow", 34, 0xFF000000);
        FrameLayout.LayoutParams mip = new FrameLayout.LayoutParams(Theme.dp(c, 34), Theme.dp(c, 34));
        mip.gravity = Gravity.CENTER;
        playBtn.addView(playIconMain, mip);
        playBtn.setOnClickListener(v -> {
            Player.toggle();
            refresh();
        });
        Ui.tapScale(playBtn);
        nextBtn = Ui.iconButton(c, "skip_next", 36, Theme.ON, () -> {
            Player.next(false);
            refresh();
        });
        repeatBtn = Ui.iconButton(c, "repeat", 22, 0x8CFFFFFF, () -> {
            Player.cycleRepeat();
            refresh();
        });
        LinearLayout.LayoutParams tp2 = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp2.topMargin = Theme.dp(c, 14);
        info.addView(transport, tp2);
        transport.addView(shuffleBtn, Ui.lp(Theme.dp(c, 44), Theme.dp(c, 44)));
        transport.addView(prevBtn, Ui.lp(Theme.dp(c, 56), Theme.dp(c, 56)));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(Theme.dp(c, 68), Theme.dp(c, 68));
        playParams.leftMargin = Theme.dp(c, 12);
        playParams.rightMargin = Theme.dp(c, 12);
        transport.addView(playBtn, playParams);
        transport.addView(nextBtn, Ui.lp(Theme.dp(c, 56), Theme.dp(c, 56)));
        transport.addView(repeatBtn, Ui.lp(Theme.dp(c, 44), Theme.dp(c, 44)));

        // Вторичные действия
        LinearLayout secondary = Ui.row(c);
        secondary.setGravity(Gravity.CENTER);
        videoButton = secondaryButton(c, "videocam_off", "Видео", () -> {
            Player.setVideoMode(!Player.videoMode());
            refresh();
        });
        fullButton = secondaryButton(c, "fullscreen", "Экран", () -> setFullscreen(!fullscreen));
        downloadButton = secondaryButton(c, "download", "Скачать", () -> {
            Models.Track t = Player.current();
            if (t != null) Downloads.download(t, Settings.downloadKind, true);
        });
        queueButton = secondaryButton(c, "queue_music", "Очередь", () -> activity.sheets().openQueue());
        secondary.addView(videoButton);
        secondary.addView(fullButton);
        secondary.addView(downloadButton);
        secondary.addView(queueButton);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.topMargin = Theme.dp(c, 16);
        info.addView(secondary, sp2);

        hint = Ui.text(c, "Видео загружается только по нажатию — звук не прерывается", 11.5f, 0x59FFFFFF);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = Theme.dp(c, 12);
        info.addView(hint, hp);

        // Свайп вниз
        View.OnTouchListener swipe = (v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartY = e.getRawY();
                    dragging = !fullscreen;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        float dy = e.getRawY() - touchStartY;
                        if (dy > 0) setTranslationY(dy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging && getTranslationY() > Theme.dp(getContext(), 110)) close();
                    else animate().translationY(0f).setDuration(Theme.DUR_FAST).start();
                    dragging = false;
                    return true;
                default:
                    return false;
            }
        };
        dragHandle.setOnTouchListener(swipe);
        stage.setOnTouchListener(swipe);

        setVisibility(GONE);
        setTranslationY(Theme.dp(c, 800));
    }

    private LinearLayout secondaryButton(Context c, String icon, String label, Runnable click) {
        LinearLayout box = Ui.column(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Theme.dp(c, 16), Theme.dp(c, 6), Theme.dp(c, 16), Theme.dp(c, 6));
        ImageView iv = Ui.icon(c, icon, 22, 0x8CFFFFFF);
        box.addView(iv);
        TextView tv = Ui.text(c, label, 11f, 0x8CFFFFFF, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Theme.dp(c, 4);
        box.addView(tv, tp);
        box.setOnClickListener(v -> click.run());
        Ui.tap(box);
        return box;
    }

    public boolean isOpen() {
        return open;
    }

    /* ------------------------------------------------------------------ */

    public void open() {
        if (Player.current() == null) return;
        open = true;
        setVisibility(VISIBLE);
        animate().translationY(0f).setDuration(Theme.DUR_NOWPLAYING).setInterpolator(Theme.EASE_SHEET).start();
        activity.updateBars();
        handler.removeCallbacks(tick);
        handler.post(tick);
        refresh();
    }

    public void close() {
        if (!open) return;
        if (fullscreen) setFullscreen(false);
        if (Player.videoMode()) Player.setVideoMode(false);
        videoView.setPlayer(null);
        open = false;
        handler.removeCallbacks(tick);
        animate().translationY(getHeight()).setDuration(Theme.DUR_NOWPLAYING).setInterpolator(Theme.EASE_SHEET)
                .withEndAction(() -> setVisibility(GONE)).start();
        activity.updateBars();
    }

    public void toggle() {
        if (open) close();
        else open();
    }

    /* ------------------------------------------------------------------ */
    /* Обновление                                                          */
    /* ------------------------------------------------------------------ */

    /** Фон: чёрный в режиме видео, иначе производный от обложки цвет (как на сайте). */
    private void applyAccent(Display d) {
        if (Player.videoMode()) {
            tint.setBackgroundColor(Color.BLACK);
            return;
        }
        String metaColor = d != null ? d.color : null;
        String url = d != null ? (d.cover != null ? d.cover : d.thumb) : null;
        if (metaColor != null && metaColor.startsWith("#")) tint.setBackgroundColor(mix(parseHex(metaColor)));
        else tint.setBackgroundColor(BASE_BG);
        if (url == null) return;
        final String key = url;
        Integer cached = ACCENTS.get(key);
        if (cached != null) {
            tint.setBackgroundColor(cached);
            return;
        }
        accentLoading = key;
        Image.load(key, 28, new com.anibeat.app.core.Image.Listener() {
            @Override
            public void onBitmap(android.graphics.Bitmap bitmap) {
                if (bitmap == null || !key.equals(accentLoading)) return;
                int mixed = mix(dominant(bitmap));
                ACCENTS.put(key, mixed);
                if (!Player.videoMode()) tint.setBackgroundColor(mixed);
            }

            @Override
            public void onError() {
            }
        });
    }

    private static final java.util.Map<String, Integer> ACCENTS = new java.util.HashMap<>();
    private static final int BASE_BG = 0xFF141416;
    private String accentLoading;

    /** Средневзвешенный цвет битмапа — порт extractColor() из lib/utils.ts. */
    private static int dominant(android.graphics.Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        double r = 0, g = 0, b = 0, weight = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = bmp.getPixel(x, y);
                int a = Color.alpha(px);
                if (a < 128) continue;
                int R = Color.red(px), G = Color.green(px), B = Color.blue(px);
                int max = Math.max(R, Math.max(G, B));
                int min = Math.min(R, Math.min(G, B));
                double sat = max == 0 ? 0 : (max - min) / (double) max;
                double lum = (max + min) / 2.0 / 255.0;
                double wgt = 0.15 + sat * 2 + (lum > 0.15 && lum < 0.85 ? 0.6 : 0);
                r += R * wgt;
                g += G * wgt;
                b += B * wgt;
                weight += wgt;
            }
        }
        if (weight == 0) return BASE_BG;
        return Color.rgb((int) Math.round(r / weight), (int) Math.round(g / weight), (int) Math.round(b / weight));
    }

    /** color-mix(in srgb, accent 34%, #0a0a0c). */
    private static int mix(int accent) {
        float k = 0.34f;
        int r = Math.round(Color.red(accent) * k + 0x0a * (1 - k));
        int g = Math.round(Color.green(accent) * k + 0x0a * (1 - k));
        int b = Math.round(Color.blue(accent) * k + 0x0c * (1 - k));
        return Color.rgb(r, g, b);
    }

    private static int parseHex(String value) {
        try {
            String v = value.trim();
            if (v.startsWith("#")) v = v.substring(1);
            if (v.length() == 3) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; i++) sb.append(v.charAt(i)).append(v.charAt(i));
                v = sb.toString();
            }
            return (int) Long.parseLong(v, 16) | 0xFF000000;
        } catch (Exception e) {
            return BASE_BG;
        }
    }

    /** Обложка уменьшается на паузе (scale-[0.86], 500мс) — как на сайте. */
    private void applyStageScale() {
        if (Player.videoMode()) {
            stage.animate().scaleX(1f).scaleY(1f).setDuration(500).start();
            return;
        }
        float target = Player.isPlaying() ? 1f : 0.86f;
        stage.animate().scaleX(target).scaleY(target).setDuration(500).setInterpolator(Theme.EASE_SHEET).start();
    }

    public void refresh() {
        Models.Track t = Player.current();
        if (t == null) return;
        Display d = Display.track(t);
        art.setUrl(d.cover, d.thumb);
        art.setVisibility(Player.videoMode() ? GONE : VISIBLE);
        applyAccent(d);
        applyStageScale();

        title.setText(t.title);
        artistsRow.removeAllViews();
        if (!t.artists.isEmpty() && t.artists.get(0).slug != null && !t.artists.get(0).slug.isEmpty()) {
            for (int i = 0; i < t.artists.size(); i++) {
                final Models.ArtistRef a = t.artists.get(i);
                TextView tv = Ui.text(c(), i > 0 ? ", " + a.name : a.name, 19f, 0x99FFFFFF);
                tv.setOnClickListener(v -> {
                    close();
                    activity.openArtist(a.slug);
                });
                Ui.tap(tv);
                artistsRow.addView(tv);
            }
        } else {
            artistsRow.addView(Ui.text(c(), t.artistNames(), 19f, 0x99FFFFFF));
        }
        themeLabel.setText(t.themeSlug);
        themeLabel.setTextColor("OP".equals(t.type) ? Theme.PRIMARY : "ED".equals(t.type) ? Theme.SECONDARY : Theme.TERTIARY);
        animeLabel.setText(d.title == null ? "" : d.title);

        boolean fav = Library.isFavorite(t.id);
        setIcon(favButton, fav ? "favorite" : "favorite_border", fav ? Theme.SECONDARY : Theme.ON);

        setIcon(shuffleBtn, "shuffle", Player.shuffle() ? Theme.PRIMARY : 0x8CFFFFFF);
        setIcon(playBtn, Player.isPlaying() ? "pause" : "play_arrow", 0xFF000000);
        setIcon(nextBtn, "skip_next", Theme.ON);
        setIcon(repeatBtn, "one".equals(Player.repeat()) ? "repeat_one" : "repeat", !"off".equals(Player.repeat()) ? Theme.PRIMARY : 0x8CFFFFFF);

        PlayerView vv = videoView;
        if (Player.videoMode()) {
            art.setVisibility(GONE);
            vv.setVisibility(VISIBLE);
            vv.setPlayer(Player.controller());
            controls.setVisibility(VISIBLE);
            fullButton.setVisibility(VISIBLE);
            hint.setVisibility(GONE);
            if (Player.isPlaying()) poke();
        } else {
            vv.setVisibility(GONE);
            vv.setPlayer(null);
            controls.setVisibility(GONE);
            art.setVisibility(VISIBLE);
            hint.setVisibility(VISIBLE);
            handler.removeCallbacks(hideControls);
        }
        setIcon(videoButton, Player.videoMode() ? "videocam" : "videocam_off", Player.videoMode() ? Theme.PRIMARY : 0x8CFFFFFF);
        setSecondaryLabel(videoButton, "Видео");
        boolean offline = Downloads.hasOffline(t.id, Downloads.KIND_AUDIO);
        setIcon(downloadButton, offline ? "download_done" : "download", offline ? Theme.TERTIARY : 0x8CFFFFFF);

        buffering.setVisibility(Player.isBuffering() ? VISIBLE : GONE);
        refreshTime();
    }

    private void setSecondaryLabel(LinearLayout box, String label) {
        if (box.getChildCount() > 1 && box.getChildAt(1) instanceof TextView) {
            ((TextView) box.getChildAt(1)).setText(label);
        }
    }

    private void refreshTime() {
        if (!open) return;
        Models.Track t = Player.current();
        if (t == null) return;
        long duration = Player.duration();
        long position = Player.position();
        float ratio = duration > 0 ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
        if (!scrubbing) seek.setValue(ratio * 1000f);
        if (videoView.getVisibility() == VISIBLE) {
            for (int i = 0; i < videoBottom.getChildCount(); i++) {
                if (videoBottom.getChildAt(i) instanceof SliderView && !scrubbing) {
                    ((SliderView) videoBottom.getChildAt(i)).setValue(ratio * 1000f);
                }
            }
        }
        timeLeft.setText(Format.time(position / 1000f));
        timeRight.setText("-" + Format.time(Math.max(0, duration - position) / 1000f));
    }

    private void poke() {
        fadeControls(true);
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 2600);
    }

    private void fadeControls(boolean visible) {
        controls.animate().alpha(visible ? 1f : 0f).setDuration(Theme.DUR_FAST).start();
        controls.setClickable(visible);
    }

    private void goAnime() {
        Models.Track t = Player.current();
        if (t == null) return;
        close();
        activity.openAnime(t.anime.slug);
    }

    private void setFullscreen(boolean value) {
        fullscreen = value;
        setIcon(fullButton, value ? "fullscreen_exit" : "fullscreen", 0x8CFFFFFF);
        if (value) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            try {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } catch (Exception ignored) {
            }
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            try {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } catch (Exception ignored) {
            }
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) stage.getLayoutParams();
        if (value) {
            params.width = LayoutParams.MATCH_PARENT;
            params.height = LayoutParams.MATCH_PARENT;
            stage.setBackgroundColor(Color.BLACK);
        } else {
            int stageSize = Math.min(Theme.dp(c(), 380), (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.82f));
            params.width = stageSize;
            params.height = stageSize;
            stage.setBackgroundColor(Color.TRANSPARENT);
        }
        stage.setLayoutParams(params);
    }

    private Context c() {
        return activity;
    }

    private void setIcon(View button, String name, int color) {
        if (!(button instanceof FrameLayout)) return;
        FrameLayout box = (FrameLayout) button;
        if (box.getChildCount() == 0) {
            ImageView iv = Ui.icon(c(), name, 24, color);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Theme.dp(c(), 24), Theme.dp(c(), 24));
            p.gravity = Gravity.CENTER;
            box.addView(iv, p);
            return;
        }
        View child = box.getChildAt(0);
        if (child instanceof ImageView) {
            int size = Math.max(12, Math.round(((ImageView) child).getWidth() / (float) Theme.dp(c(), 1f)));
            int res = c().getResources().getIdentifier("ic_" + name, "drawable", c().getPackageName());
            if (res != 0) ((ImageView) child).setImageResource(res);
            ((ImageView) child).setColorFilter(color);
        }
    }

    /** Формат времени как formatTime на сайте. */
    public static final class Format {
        public static String time(long seconds) {
            if (seconds < 0) seconds = 0;
            long m = seconds / 60;
            long s = seconds % 60;
            return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
        }

        public static String time(float seconds) {
            return time((long) Math.floor(seconds));
        }
    }
}
