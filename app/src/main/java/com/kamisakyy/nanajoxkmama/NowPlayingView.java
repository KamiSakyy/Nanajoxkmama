package com.kamisakyy.nanajoxkmama;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Surface;
import android.view.TextureView;

import java.util.List;
import java.util.Locale;

/**
 * Fullscreen "Now Playing" — a sliding sheet with cover ⇄ video stage, accent
 * color extracted from the artwork, seek bar and transport controls. Swipe
 * down to dismiss, matching the website's NowPlaying.tsx.
 */
public final class NowPlayingView extends FrameLayout {
    private final MainActivity host;
    private final LinearLayout root;
    private final Ui.CoverView cover;
    private final FrameLayout stage;
    private final TextureView video;
    private MediaPlayer videoPlayer;
    private Surface videoSurface;
    private boolean videoWanted;
    private final TextView title;
    private final TextView subtitle;
    private final TextView tagView;
    private final View artistLink;
    private final TextView posLabel;
    private final TextView negLabel;
    private final Ui.SliderView slider;
    private final ImageView playBtn;
    private final View shuffleBtn;
    private final View repeatBtn;
    private final View favBtn;
    private final View videoBtn;
    private final ImageView videoBtnIcon;
    private final ImageView favIcon;
    private final ImageView repeatIcon;
    private boolean open;
    private boolean videoMode;
    private float startY = -1;
    private float dragY;

    public NowPlayingView(MainActivity host) {
        super(host);
        this.host = host;
        setBackgroundColor(Ui.S1);
        setVisibility(GONE);

        root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // top bar
        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(6), Ui.dp(10), Ui.dp(6), Ui.dp(2));
        top.addView(Ui.iconBtn(host, "expand_more", 24, Color.argb(220, 255, 255, 255), v -> close()));
        View grab = new View(host);
        grab.setBackground(Ui.rounded(0x4DFFFFFF, 3));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(5));
        gp.gravity = Gravity.CENTER;
        LinearLayout grabWrap = new LinearLayout(host);
        grabWrap.addView(grab, gp);
        grabWrap.setGravity(Gravity.CENTER);
        top.addView(grabWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        top.addView(Ui.iconBtn(host, "more_horiz", 22, Color.argb(220, 255, 255, 255), v -> {
            Track t = Store.getCurrentTrack();
            if (t != null) Sheets.trackMenu(host, t, Store.getQueue(), this);
        }));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(host);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(Ui.dp(24), Ui.dp(12), Ui.dp(24), Ui.dp(28));
        scroll.addView(body, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // stage: cover ⇄ video
        stage = new FrameLayout(host);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(300), Ui.dp(300));
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        stage.setLayoutParams(slp);
        cover = new Ui.CoverView(host);
        cover.radius(14);
        stage.addView(cover, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        video = new TextureView(host);
        video.setOpaque(false);
        video.setVisibility(GONE);
        stage.addView(video, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                if (videoSurface != null) videoSurface.release();
                videoSurface = new Surface(st);
                if (videoPlayer != null && videoWanted) {
                    try {
                        videoPlayer.setSurface(videoSurface);
                        videoPlayer.prepareAsync();
                    } catch (Exception ignored) { }
                }
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                if (videoSurface != null) { videoSurface.release(); videoSurface = null; }
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });
        // tap artwork / video area = play-pause, like the site's player stage
        stage.setOnClickListener(v -> {
            if (videoMode) {
                if (videoPlayer != null) {
                    try {
                        if (videoPlayer.isPlaying()) videoPlayer.pause();
                        else videoPlayer.start();
                    } catch (Exception ignored) { }
                }
                refresh();
            } else {
                PlaybackService.command(host, PlaybackService.ACTION_TOGGLE);
            }
        });
        Ui.tapScale(stage);
        body.addView(stage);
        body.addView(spacer(18));

        title = Ui.text(host, "", 23, Ui.ON, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setMaxLines(2);
        title.setSingleLine(false);
        body.addView(title, wide());
        subtitle = Ui.text(host, "", 14.5f, Color.argb(200, 255, 255, 255), false);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams sbp = wide();
        sbp.topMargin = Ui.dp(3);
        body.addView(subtitle, sbp);
        subtitle.setOnClickListener(null); // tap on track info must NEVER close the player

        LinearLayout tagRow = new LinearLayout(host);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setGravity(Gravity.CENTER_HORIZONTAL);
        tagView = (TextView) Ui.tag(host, "", Ui.TAG_OP);
        tagRow.addView(tagView);
        LinearLayout.LayoutParams trp = wide();
        trp.topMargin = Ui.dp(10);
        body.addView(tagRow, trp);

        artistLink = Ui.chip(host, "Открыть артиста", false, v -> {
            Track t = Store.getCurrentTrack();
            if (t != null && t.artistSlug != null && !t.artistSlug.isEmpty()) {
                close();
                host.openArtist(t.artistSlug);
            }
        });
        LinearLayout.LayoutParams alp = wide();
        alp.topMargin = Ui.dp(10);
        LinearLayout artistWrap = new LinearLayout(host);
        artistWrap.setOrientation(LinearLayout.HORIZONTAL);
        artistWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        artistWrap.addView(artistLink);
        body.addView(artistWrap, alp);

        body.addView(spacer(22));

        slider = new Ui.SliderView(host);
        slider.setListener(new Ui.SliderView.OnSeek() {
            long scrubMs = -1;
            @Override public void onScrub(float fraction) {
                Track t = Store.getCurrentTrack();
                long dur = Store.getDuration();
                if (t == null || dur <= 0) return;
                scrubMs = (long) (fraction * dur);
                posLabel.setText(Util.formatTime(scrubMs));
                negLabel.setText("-" + Util.formatTime(Math.max(0, dur - scrubMs)));
            }

            @Override public void onSeek(float fraction) {
                Track t = Store.getCurrentTrack();
                long dur = Store.getDuration();
                if (t == null || dur <= 0) return;
                long ms = (long) (fraction * dur);
                if (videoMode && isVideoPlaying()) { try { videoPlayer.seekTo((int) ms); } catch (Exception ignored) { } }
                else PlaybackService.seek(host, ms);
            }
        });
        body.addView(slider, wide());

        LinearLayout times = new LinearLayout(host);
        times.setOrientation(LinearLayout.HORIZONTAL);
        posLabel = Ui.text(host, "0:00", 11, Color.argb(190, 255, 255, 255), true);
        negLabel = Ui.text(host, "-0:00", 11, Color.argb(190, 255, 255, 255), true);
        negLabel.setGravity(Gravity.END);
        times.addView(posLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(negLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(times, wide());

        body.addView(spacer(16));

        // transport
        LinearLayout transport = new LinearLayout(host);
        transport.setOrientation(LinearLayout.HORIZONTAL);
        transport.setGravity(Gravity.CENTER_VERTICAL);
        transport.addView(Ui.iconBtn(host, "replay_10", 24, Color.argb(220, 255, 255, 255),
                v -> PlaybackService.seekBy(host, -10_000)));
        transport.addView(transportSpace(10));
        transport.addView(Ui.iconBtn(host, "skip_previous", 26, Color.argb(235, 255, 255, 255),
                v -> PlaybackService.command(host, PlaybackService.ACTION_PREVIOUS)));
        transport.addView(transportSpace(14));

        FrameLayout playCircle = new FrameLayout(host);
        playCircle.setBackground(Ui.ripple(Ui.oval(Ui.ON)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(64), Ui.dp(64));
        playCircle.setLayoutParams(plp);
        playBtn = Ui.icon(host, "pause", 28, Color.BLACK);
        playCircle.addView(playBtn, new LayoutParams(Ui.dp(28), Ui.dp(28), Gravity.CENTER));
        playCircle.setOnClickListener(v -> {
            if (videoMode) {
                if (videoPlayer != null) {
                    try {
                        if (videoPlayer.isPlaying()) videoPlayer.pause();
                        else videoPlayer.start();
                    } catch (Exception ignored) { }
                }
                refresh();
            } else PlaybackService.command(host, PlaybackService.ACTION_TOGGLE);
        });
        Ui.tapScale(playCircle);
        transport.addView(playCircle);
        transport.addView(transportSpace(14));
        transport.addView(Ui.iconBtn(host, "skip_next", 26, Color.argb(235, 255, 255, 255),
                v -> PlaybackService.command(host, PlaybackService.ACTION_NEXT)));
        transport.addView(transportSpace(10));
        transport.addView(Ui.iconBtn(host, "forward_10", 24, Color.argb(220, 255, 255, 255),
                v -> PlaybackService.seekBy(host, 10_000)));
        body.addView(transport, wide());

        body.addView(spacer(18));

        // secondary row
        LinearLayout row2 = new LinearLayout(host);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        shuffleBtn = secondaryBtn("shuffle", v -> {
            PlaybackService.command(host, PlaybackService.ACTION_SHUFFLE);
            host.toast(Store.isShuffle() ? "Перемешивание включено" : "Перемешивание выключено");
            refresh();
        });
        FrameLayout repeatFrame = secondaryBtn("repeat", v -> {
            PlaybackService.command(host, PlaybackService.ACTION_REPEAT);
            refresh();
        });
        repeatIcon = (ImageView) repeatFrame.getChildAt(0);
        repeatBtn = repeatFrame;
        FrameLayout favFrame = secondaryBtn("favorite_border", v -> {
            Track t = Store.getCurrentTrack();
            if (t == null) return;
            boolean added = Store.toggleFavorite(t);
            host.toast(added ? "Добавлено в избранное" : "Убрано из избранного");
            refresh();
        });
        favIcon = (ImageView) favFrame.getChildAt(0);
        favBtn = favFrame;
        FrameLayout videoFrame = secondaryBtn("videocam_off", v -> toggleVideo());
        videoBtnIcon = (ImageView) videoFrame.getChildAt(0);
        videoBtn = videoFrame;
        View queueBtn = secondaryBtn("queue_music", v -> Sheets.queueSheet(host, this));
        View dlBtn = secondaryBtn("download", v -> {
            Track t = Store.getCurrentTrack();
            if (t == null) return;
            Downloader.download(t, "video".equals(Store.getDownloadKind()), true);
            host.toast("Скачивание началось");
        });
        View shareBtn = secondaryBtn("share", v -> {
            Track t = Store.getCurrentTrack();
            if (t == null) return;
            Sheets.shareTrack(host, t);
        });
        row2.addView(shuffleBtn);
        row2.addView(favBtn);
        row2.addView(dlBtn);
        row2.addView(queueBtn);
        row2.addView(videoBtn);
        row2.addView(shareBtn);
        row2.addView(repeatBtn);
        body.addView(row2, wide());

        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View spacer(int dp) {
        View v = new View(host);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(1, Ui.dp(dp));
        v.setLayoutParams(lp);
        return v;
    }

    private View transportSpace(int dp) {
        View v = new View(host);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(dp), 1));
        return v;
    }

    private FrameLayout secondaryBtn(String icon, OnClickListener click) {
        return (FrameLayout) Ui.iconBtn(host, icon, 21, Color.argb(170, 255, 255, 255), click);
    }

    /* ---------------- open / close ---------------- */

    public boolean isOpen() { return open; }

    public void open() {
        Track t = Store.getCurrentTrack();
        if (t == null) return;
        open = true;
        setVisibility(VISIBLE);
        setTranslationY(getHeight() > 0 ? getHeight() : host.getResources().getDisplayMetrics().heightPixels);
        animate().translationY(0).setDuration(380)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f)).start();
        refresh();
    }

    public void close() {
        if (!open) return;
        stopVideo(true);
        open = false;
        animate().translationY(getHeight() > 0 ? getHeight() : 2000).setDuration(320)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.1f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        setVisibility(GONE);
                        setTranslationY(0);
                    }
                }).start();
    }

    private void toggleVideo() {
        Track t = Store.getCurrentTrack();
        if (t == null) return;
        if (videoMode) {
            stopVideo(true);
            refresh();
            return;
        }
        String url = t.videoUrl == null ? "" : t.videoUrl;
        boolean hasVideo = !url.isEmpty() && !url.equals(t.audioUrl);
        if (!hasVideo) {
            host.toast("Для этого трека нет видео");
            return;
        }
        // Video is NEVER preloaded — we start fetching it only on this button press.
        videoMode = true;
        videoWanted = true;
        videoBtnIcon.setImageResource(Ui.drawableId(host, "videocam"));
        videoBtnIcon.setColorFilter(Ui.ACCENT);
        cover.setVisibility(GONE);
        video.setVisibility(VISIBLE);
        ViewGroup.LayoutParams lp = stage.getLayoutParams();
        lp.height = Ui.dp(200);
        stage.setLayoutParams(lp);
        long pos = Store.getPosition();
        if (Store.isPlaying()) PlaybackService.command(host, PlaybackService.ACTION_TOGGLE); // pause audio
        releaseVideoPlayer();
        try {
            videoPlayer = new MediaPlayer();
            videoPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            videoPlayer.setOnPreparedListener(mp -> {
                mp.setLooping(false);
                try { mp.seekTo((int) Math.max(0, pos - 400)); } catch (Exception ignored) { }
                mp.start();
                mp.setOnCompletionListener(m -> PlaybackService.command(host, PlaybackService.ACTION_NEXT));
                refresh();
            });
            videoPlayer.setOnErrorListener((mp, what, extra) -> {
                host.toast("Не удалось загрузить видео");
                stopVideo(true);
                return true;
            });
            videoPlayer.setDataSource(url);
            if (videoSurface == null && video.getSurfaceTexture() != null) {
                videoSurface = new Surface(video.getSurfaceTexture());
            }
            if (videoSurface != null) {
                videoPlayer.setSurface(videoSurface);
                videoPlayer.prepareAsync();
            } else {
                // surface arrives via onSurfaceTextureAvailable -> prepareAsync there
                videoWanted = true;
            }
        } catch (Exception e) {
            host.toast("Не удалось загрузить видео");
            stopVideo(true);
            return;
        }
        host.toast("Видео включено");
        refresh();
    }

    private void releaseVideoPlayer() {
        videoWanted = false;
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignored) { }
            try { videoPlayer.release(); } catch (Exception ignored) { }
            videoPlayer = null;
        }
    }

    private void stopVideo(boolean resumeAudio) {
        if (!videoMode && videoPlayer == null) return;
        int pos = 0;
        if (videoPlayer != null) {
            try { pos = videoPlayer.getCurrentPosition(); } catch (Exception ignored) { }
        }
        videoWanted = false;
        releaseVideoPlayer();
        video.setVisibility(GONE);
        cover.setVisibility(VISIBLE);
        ViewGroup.LayoutParams lp = stage.getLayoutParams();
        lp.height = Ui.dp(300);
        stage.setLayoutParams(lp);
        videoMode = false;
        videoBtnIcon.setImageResource(Ui.drawableId(host, "videocam_off"));
        videoBtnIcon.setColorFilter(Color.argb(170, 255, 255, 255));
        if (resumeAudio && pos > 0) {
            PlaybackService.seek(host, pos);
            PlaybackService.command(host, PlaybackService.ACTION_TOGGLE);
        }
    }

    /* ---------------- state ---------------- */

    public void refresh() {
        Track t = Store.getCurrentTrack();
        if (t == null) return;
        title.setText(t.title);
        subtitle.setText(t.displayArtist() + " · " + t.animeName);
        tagView.setText(t.themeTag());
        int tone = Ui.tagColor(t.type);
        tagView.setTextColor(tone);
        tagView.setBackground(Ui.rounded(Color.argb(0x2E, Color.red(tone), Color.green(tone), Color.blue(tone)), 5));
        String url = Store.isDataSaverEnabled() && !t.coverSmall.isEmpty() ? t.coverSmall : t.cover;
        cover.load(url);
        // accent color from artwork
        cover.postDelayed(() -> {
            try {
                ImageView img = null;
                for (int i = 0; i < cover.getChildCount(); i++) {
                    View c = cover.getChildAt(i);
                    if (c instanceof ImageView && c.getVisibility() == VISIBLE) img = (ImageView) c;
                }
                if (img != null && img.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                    Bitmap b = ((android.graphics.drawable.BitmapDrawable) img.getDrawable()).getBitmap();
                    int accent = ImageLoader.extractColor(b, 0);
                    if (accent != 0) setBackgroundColor(ImageLoader.mixSurface(accent, 0.34f));
                }
            } catch (Exception ignored) { }
        }, 650);

        boolean playing = videoMode ? isVideoPlaying() : Store.isPlaying();
        playBtn.setImageResource(Ui.drawableId(host, playing ? "pause" : "play_arrow"));
        playBtn.setColorFilter(Color.BLACK);

        boolean fav = Store.isFavorite(t.id);
        favIcon.setImageResource(Ui.drawableId(host, fav ? "favorite" : "favorite_border"));
        favIcon.setColorFilter(fav ? Color.rgb(255, 105, 180) : Color.argb(170, 255, 255, 255));

        shuffleBtn.setAlpha(Store.isShuffle() ? 1f : 0.45f);
        String repeat = Store.getRepeat();
        repeatIcon.setImageResource(Ui.drawableId(host, "one".equals(repeat) ? "repeat_one" : "repeat"));
        repeatIcon.setColorFilter(!"off".equals(repeat) ? Ui.ACCENT : Color.argb(170, 255, 255, 255));

        long dur = videoMode ? videoDuration() : Store.getDuration();
        long pos = videoMode ? videoPosition() : Store.getPosition();
        if (dur > 0) {
            slider.setProgress(pos / (float) dur);
            posLabel.setText(Util.formatTime(pos));
            negLabel.setText("-" + Util.formatTime(Math.max(0, dur - pos)));
        } else {
            slider.setProgress(0);
            posLabel.setText("0:00");
            negLabel.setText("-0:00");
        }
    }

    private boolean isVideoPlaying() {
        try { return videoPlayer != null && videoPlayer.isPlaying(); } catch (Exception e) { return false; }
    }

    private long videoDuration() {
        try { return videoPlayer == null ? 0 : videoPlayer.getDuration(); } catch (Exception e) { return 0; }
    }

    private long videoPosition() {
        try { return videoPlayer == null ? 0 : videoPlayer.getCurrentPosition(); } catch (Exception e) { return 0; }
    }

    /* ---------------- swipe down ---------------- */

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = event.getRawY();
                dragY = 0;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (startY >= 0) {
                    dragY = Math.max(0, event.getRawY() - startY);
                    setTranslationY(dragY);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragY > 120) close();
                else animate().translationY(0).setDuration(180).start();
                startY = -1;
                dragY = 0;
                return true;
        }
        return super.onTouchEvent(event);
    }
}
