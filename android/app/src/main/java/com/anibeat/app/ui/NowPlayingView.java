package com.anibeat.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;

import com.anibeat.app.R;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Models;
import com.anibeat.app.player.Player;
import com.google.android.material.slider.Slider;

/** Полноэкранный плеер: обложка или видео, время, управление и очередь. */
public class NowPlayingView extends FrameLayout {

    private Host host;
    private ImageView art;
    private PlayerView video;
    private FrameLayout videoBox;
    private TextView title;
    private TextView artist;
    private TextView anime;
    private TextView position;
    private TextView duration;
    private TextView status;
    private Slider slider;
    private ImageView play;
    private ImageView shuffle;
    private ImageView repeat;
    private ImageView like;
    private ImageView sleep;
    private ImageView videoToggle;
    private boolean open;
    private boolean seeking;
    private String shownCover = "";
    private int shownIndex = -1;

    public NowPlayingView(final Host host) {
        super(host.activity());
        this.host = host;
        Context context = host.activity();
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setBackground(Ui.gradient(Theme.mix(0xFF0A0A0C, Theme.ACCENT, 0.34f), Theme.BG));
        setVisibility(GONE);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        /* полоска-ручка: видно, что панель можно смахнуть вниз */
        FrameLayout handleBox = new FrameLayout(context);
        View handle = new View(context);
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        pill.setColor(0x66FFFFFF);
        pill.setCornerRadius(Theme.dpF(context, 3f));
        handle.setBackground(pill);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(
                Theme.dp(context, 44), Theme.dp(context, 5));
        pillParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        pillParams.topMargin = Theme.dp(context, 8);
        handleBox.addView(handle, pillParams);
        handleBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 20)));
        column.addView(handleBox);

        /* верхняя строка */
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Theme.dp(context, 6), Theme.dp(context, 10), Theme.dp(context, 6), 0);
        ImageView collapse = icon(context, R.drawable.ic_expand_more, Theme.ON);
        top.addView(collapse, new LinearLayout.LayoutParams(Theme.dp(context, 44), Theme.dp(context, 44)));
        collapse.setOnClickListener(v -> close());
        TextView caption = new TextView(context);
        caption.setText("Сейчас играет");
        caption.setTextSize(11.5f);
        caption.setLetterSpacing(0.09f);
        caption.setGravity(Gravity.CENTER);
        caption.setTextColor(Theme.ON_VARIANT);
        top.addView(caption, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView menu = icon(context, R.drawable.ic_more_vert, Theme.ON);
        top.addView(menu, new LinearLayout.LayoutParams(Theme.dp(context, 44), Theme.dp(context, 44)));
        menu.setOnClickListener(v -> {
            Models.Track track = Player.current();
            if (track != null) host.trackMenu(track, v);
        });
        column.addView(top);

        /* сцена: обложка или видео */
        FrameLayout stage = new FrameLayout(context);
        column.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        art = new ImageView(context);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        artParams.setMargins(Theme.dp(context, 34), Theme.dp(context, 10), Theme.dp(context, 34), Theme.dp(context, 10));
        stage.addView(art, artParams);
        videoBox = new FrameLayout(context);
        videoBox.setBackgroundColor(0xFF000000);
        videoBox.setVisibility(GONE);
        video = new PlayerView(context);
        video.setUseController(false);
        video.setShutterBackgroundColor(0xFF000000);
        videoBox.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stage.addView(videoBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        /* подпись трека */
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Theme.dp(context, 24), Theme.dp(context, 12), Theme.dp(context, 24), 0);
        column.addView(info);
        title = new TextView(context);
        title.setTextSize(21f);
        title.setTextColor(Theme.ON);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title);
        artist = new TextView(context);
        artist.setTextSize(14f);
        artist.setTextColor(Theme.ON_VARIANT);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        artistParams.topMargin = Theme.dp(context, 3);
        info.addView(artist, artistParams);
        anime = new TextView(context);
        anime.setTextSize(12.5f);
        anime.setTextColor(Theme.ACCENT);
        anime.setBackground(Ui.rounded(context, Theme.mix(Theme.SURFACE_2, Theme.ACCENT, 0.18f), 10f));
        anime.setPadding(Theme.dp(context, 10), Theme.dp(context, 5), Theme.dp(context, 10), Theme.dp(context, 5));
        LinearLayout.LayoutParams animeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        animeParams.topMargin = Theme.dp(context, 9);
        info.addView(anime, animeParams);
        anime.setOnClickListener(v -> {
            Models.Track track = Player.current();
            if (track != null && track.anime != null) host.openAnime(track.anime);
        });
        status = new TextView(context);
        status.setTextSize(12f);
        status.setTextColor(Theme.TERTIARY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Theme.dp(context, 6);
        info.addView(status, statusParams);

        /* шкала времени */
        slider = new Slider(context);
        slider.setValueFrom(0f);
        slider.setValueTo(1000f);
        slider.setStepSize(0f);
        slider.setTrackActiveTintList(ColorStateList.valueOf(Theme.ACCENT));
        slider.setTrackInactiveTintList(ColorStateList.valueOf(Theme.SURFACE_4));
        slider.setThumbTintList(ColorStateList.valueOf(Theme.ON));
        slider.setHaloTintList(ColorStateList.valueOf(Theme.alpha(Theme.ACCENT, 0.25f)));
        slider.addOnChangeListener((bar, value, fromUser) -> {
            if (fromUser) {
                seeking = true;
                position.setText(Format.time((long) value / 1000f));
            }
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(Slider bar) {
                seeking = false;
                Player.seekTo((long) bar.getValue());
            }
        });
        LinearLayout times = new LinearLayout(context);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setPadding(Theme.dp(context, 20), 0, Theme.dp(context, 20), 0);
        column.addView(times, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        position = new TextView(context);
        position.setTextSize(11.5f);
        position.setTextColor(Theme.ON_VARIANT);
        times.addView(position, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        duration = new TextView(context);
        duration.setTextSize(11.5f);
        duration.setTextColor(Theme.ON_VARIANT);
        duration.setGravity(Gravity.END);
        times.addView(duration, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sliderParams.setMargins(Theme.dp(context, 16), Theme.dp(context, 6), Theme.dp(context, 16), 0);
        column.addView(slider, sliderParams);

        /* управление */
        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(Theme.dp(context, 16), Theme.dp(context, 10), Theme.dp(context, 16), 0);
        column.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shuffle = icon(context, R.drawable.ic_shuffle, Theme.ON_VARIANT);
        controls.addView(shuffle, new LinearLayout.LayoutParams(0, Theme.dp(context, 44), 1f));
        shuffle.setOnClickListener(v -> Player.setShuffle(!Player.shuffle()));

        ImageView prev = icon(context, R.drawable.ic_skip_previous, Theme.ON);
        controls.addView(prev, new LinearLayout.LayoutParams(0, Theme.dp(context, 52), 1f));
        prev.setOnClickListener(v -> Player.prev());

        FrameLayout playBox = new FrameLayout(context);
        playBox.setBackground(Ui.circle(Theme.ON));
        play = new ImageView(context);
        play.setImageResource(R.drawable.ic_pause);
        play.setColorFilter(Theme.ON_PRIMARY);
        play.setPadding(Theme.dp(context, 17), Theme.dp(context, 17), Theme.dp(context, 17), Theme.dp(context, 17));
        playBox.addView(play, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playBox.setOnClickListener(v -> Player.toggle());
        Ui.ripple(playBox);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(Theme.dp(context, 68), Theme.dp(context, 68));
        playParams.leftMargin = Theme.dp(context, 6);
        playParams.rightMargin = Theme.dp(context, 6);
        controls.addView(playBox, playParams);

        ImageView next = icon(context, R.drawable.ic_skip_next, Theme.ON);
        controls.addView(next, new LinearLayout.LayoutParams(0, Theme.dp(context, 52), 1f));
        next.setOnClickListener(v -> Player.next(false));

        repeat = icon(context, R.drawable.ic_repeat, Theme.ON_VARIANT);
        controls.addView(repeat, new LinearLayout.LayoutParams(0, Theme.dp(context, 44), 1f));
        repeat.setOnClickListener(v -> {
            int mode = Player.repeat() == Player.REPEAT_NONE ? Player.REPEAT_ALL
                    : Player.repeat() == Player.REPEAT_ALL ? Player.REPEAT_ONE : Player.REPEAT_NONE;
            Player.setRepeat(mode);
        });

        /* дополнительные действия */
        LinearLayout extras = new LinearLayout(context);
        extras.setOrientation(LinearLayout.HORIZONTAL);
        extras.setGravity(Gravity.CENTER_VERTICAL);
        extras.setPadding(Theme.dp(context, 14), Theme.dp(context, 4), Theme.dp(context, 14), Theme.dp(context, 14));
        column.addView(extras, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        like = extra(context, extras, R.drawable.ic_favorite_border);
        like.setOnClickListener(v -> {
            Models.Track track = Player.current();
            if (track == null) return;
            boolean added = com.anibeat.app.data.Library.toggleFavorite(track);
            host.toast(added ? "Добавлено в избранное" : "Убрано из избранного");
            refresh();
        });
        ImageView download = extra(context, extras, R.drawable.ic_download);
        download.setOnClickListener(v -> {
            Models.Track track = Player.current();
            if (track == null) return;
            com.anibeat.app.data.Downloads.download(track, com.anibeat.app.data.Settings.downloadKind, true);
            host.toast("Скачивание началось");
        });
        ImageView speedBtn = extra(context, extras, R.drawable.ic_speed);
        speedBtn.setOnClickListener(v -> {
            float nextSpeed = Player.speed() >= 1.9f ? 0.75f : Player.speed() + 0.25f;
            Player.setSpeed(nextSpeed);
            host.toast("Скорость: " + String.format(java.util.Locale.US, "%.2f", Player.speed()) + "×");
            refresh();
        });
        sleep = extra(context, extras, R.drawable.ic_schedule);
        sleep.setOnClickListener(v -> {
            long remaining = Player.sleepRemaining();
            Player.setSleepTimer(remaining > 0 ? 0 : 30);
            host.toast(remaining > 0 ? "Таймер сна выключен" : "Таймер сна: 30 минут");
            refresh();
        });
        ImageView queue = extra(context, extras, R.drawable.ic_queue_music);
        queue.setOnClickListener(v -> Sheets.queue(host));
        videoToggle = extra(context, extras, R.drawable.ic_videocam_off);
        videoToggle.setVisibility(View.GONE);
        videoToggle.setOnClickListener(v -> {
            Models.Track track = Player.current();
            if (track == null) return;
            boolean offline = Player.hasOfflineVideo(track);
            boolean remote = track.videoUrl != null && !track.videoUrl.isEmpty();
            if (!offline && !remote) {
                host.toast("У этой темы нет видео");
                return;
            }
            boolean nextState = !Player.videoMode();
            Player.setVideoMode(nextState);
            if (nextState) {
                Player.bindVideo(video);
                host.toast(offline ? "Видео с устройства" : "Включаю видео");
            } else {
                host.toast("Только звук");
            }
            refresh();
        });
        ImageView share = extra(context, extras, R.drawable.ic_share);
        share.setOnClickListener(v -> {
            Models.Track track = Player.current();
            if (track != null) Share.track(getContext(), track);
        });

        OnTouchListener dragListener = new OnTouchListener() {
            private float startY;
            private boolean dragging;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        dragging = false;
                        // true — иначе жест не доходит до обработчика и смахнуть нельзя
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float delta = event.getRawY() - startY;
                        if (delta > Theme.dp(getContext(), 6)) dragging = true;
                        if (dragging) {
                            setTranslationY(Math.max(0f, delta));
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (dragging) {
                            if (getTranslationY() > Theme.dp(getContext(), 120)) close();
                            else animate().translationY(0f).setDuration(Theme.DUR_FAST).start();
                            dragging = false;
                            return true;
                        }
                        return false;
                    default:
                        return false;
                }
            }
        };
        // Смахивать можно за ручку, верхнюю строку и сцену (обложку/видео).
        handleBox.setOnTouchListener(dragListener);
        top.setOnTouchListener(dragListener);
        stage.setOnTouchListener(dragListener);

        refresh();
    }

    private ImageView icon(Context context, int res, int color) {
        ImageView view = new ImageView(context);
        view.setImageResource(res);
        view.setColorFilter(color);
        int pad = Theme.dp(context, 8);
        view.setPadding(pad, pad, pad, pad);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Ui.ripple(view);
        return view;
    }

    private ImageView extra(Context context, LinearLayout parent, int res) {
        ImageView view = icon(context, res, Theme.ON_VARIANT);
        parent.addView(view, new LinearLayout.LayoutParams(0, Theme.dp(context, 42), 1f));
        return view;
    }

    public boolean isOpen() {
        return open;
    }

    public void open() {
        if (open) return;
        open = true;
        setVisibility(VISIBLE);
        setTranslationY(getHeight() > 0 ? getHeight() : Theme.dp(getContext(), 700));
        animate().translationY(0f).setDuration(Theme.DUR_NOWPLAYING).setInterpolator(Theme.EASE_SHEET).start();
        Player.bindVideo(video);
        refresh();
    }

    public void close() {
        if (!open) return;
        open = false;
        Player.unbindVideo(video);
        animate().translationY(getHeight() > 0 ? getHeight() : Theme.dp(getContext(), 700))
                .setDuration(Theme.DUR_FAST)
                .withEndAction(() -> Ui.safe(() -> setVisibility(GONE)))
                .start();
    }

    /** Полное обновление после смены трека или состояния плеера. */
    public void refresh() {
        Ui.safe(() -> {
            Models.Track track = Player.current();
            if (track == null) {
                title.setText("Ничего не играет");
                artist.setText("");
                anime.setVisibility(GONE);
                position.setText("0:00");
                duration.setText("0:00");
                slider.setValue(0f);
                return;
            }
            title.setText(track.title == null || track.title.isEmpty() ? track.themeSlug : track.title);
            artist.setText(track.artistNames());
            Display display = Display.track(track);
            String animeName = display.title == null ? "" : display.title;
            anime.setVisibility(animeName.isEmpty() ? GONE : VISIBLE);
            anime.setText(animeName);
            status.setVisibility(Player.isBuffering() ? VISIBLE : GONE);
            status.setText("Загрузка…");
            boolean hasVideo = Player.hasOfflineVideo(track) || (track.videoUrl != null && !track.videoUrl.isEmpty());
            videoToggle.setVisibility(hasVideo ? VISIBLE : GONE);
            boolean videoOn = Player.videoMode() && hasVideo;
            videoBox.setVisibility(videoOn ? VISIBLE : GONE);
            art.setVisibility(videoOn ? GONE : VISIBLE);
            videoToggle.setImageResource(videoOn ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
            videoToggle.setColorFilter(videoOn ? Theme.ACCENT : Theme.ON_VARIANT);
            if (!videoOn) {
                String url = display.cover;
                String key = track.id + "|" + url;
                if (!key.equals(shownCover)) {
                    shownCover = key;
                    Img.loadRounded(art, url, Img.size(getContext(), 340), 18f);
                }
            }
            art.animate().scaleX(Player.isPlaying() ? 1f : 0.86f).scaleY(Player.isPlaying() ? 1f : 0.86f)
                    .setDuration(Theme.DUR_SHEET).setInterpolator(Theme.EASE_OUT).start();
            play.setImageResource(Player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            shuffle.setColorFilter(Player.shuffle() ? Theme.ACCENT : Theme.ON_VARIANT);
            int repeatMode = Player.repeat();
            repeat.setImageResource(repeatMode == Player.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
            repeat.setColorFilter(repeatMode == Player.REPEAT_NONE ? Theme.ON_VARIANT : Theme.ACCENT);
            boolean favorite = com.anibeat.app.data.Library.isFavorite(track.id);
            like.setImageResource(favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            like.setColorFilter(favorite ? Theme.ERROR : Theme.ON_VARIANT);
            sleep.setColorFilter(Player.sleepRemaining() > 0 ? Theme.ACCENT : Theme.ON_VARIANT);
            shownIndex = Player.currentIndex();
            tick();
        });
    }

    /** Подвинуть шкалу времени. */
    public void tick() {
        Ui.safe(() -> {
            if (seeking) return;
            int indexNow = Player.currentIndex();
            if (indexNow != shownIndex) {
                refresh();
                return;
            }
            long total = Player.duration();
            long now = Player.position();
            if (total > 0) {
                if (slider.getValueTo() < total) slider.setValueTo(Math.max(total, 1000L));
                slider.setValue(Math.min(now, total));
                duration.setText(Format.time(total / 1000f));
            } else {
                duration.setText("--:--");
            }
            position.setText(Format.time(now / 1000f));
        });
    }
}
