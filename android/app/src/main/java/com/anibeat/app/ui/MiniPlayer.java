package com.anibeat.app.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Models;
import com.anibeat.app.player.Player;

/** Мини-плеер — плавающая карточка над таб-баром (58dp, как на сайте). */
public class MiniPlayer extends FrameLayout {

    public static final int HEIGHT_DP = 58;

    private final MainActivity activity;
    private final CoverView cover;
    private final TextView title;
    private final TextView subtitle;
    private final FrameLayout playIcon;
    private final View progress;
    private final View card;
    private final View spinner;
    private float touchStartY;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 500);
        }
    };

    public MiniPlayer(MainActivity activity) {
        super(activity);
        this.activity = activity;
        Context c = activity;
        setPadding(Theme.dp(c, 8), 0, Theme.dp(c, 8), 0);
        setVisibility(GONE);

        card = Ui.row(c);
        ((LinearLayout) card).setPadding(Theme.dp(c, 8), 0, Theme.dp(c, 4), 0);
        card.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 14f)));
        card.setElevation(Theme.dpF(c, 10f));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, Theme.dp(c, HEIGHT_DP));
        addView(card, cp);

        FrameLayout coverBox = new FrameLayout(c);
        cover = new CoverView(c);
        cover.setRadiusDp(8f);
        cover.setIconSizeDp(18f);
        coverBox.addView(cover, new FrameLayout.LayoutParams(Theme.dp(c, 42), Theme.dp(c, 42)));
        spinner = new com.anibeat.app.core.Spinner(c);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Theme.dp(c, 18), Theme.dp(c, 18));
        sp.gravity = Gravity.CENTER;
        coverBox.addView(spinner, sp);
        spinner.setVisibility(GONE);
        ((LinearLayout) card).addView(coverBox, Ui.lp(Theme.dp(c, 42), Theme.dp(c, 42)));

        LinearLayout texts = Ui.column(c);
        title = Ui.text(c, "", 14.5f, Theme.ON, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = Ui.text(c, "", 12.5f, Theme.ON_VARIANT);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(subtitle);
        LinearLayout.LayoutParams tp = Ui.lpw(1f);
        tp.leftMargin = Theme.dp(c, 10);
        ((LinearLayout) card).addView(texts, tp);

        playIcon = Ui.iconButton(c, "play_arrow", 26, Theme.ON, Player::toggle);
        ((LinearLayout) card).addView(playIcon);
        FrameLayout next = Ui.iconButton(c, "skip_next", 24, Theme.ON, () -> Player.next(false));
        ((LinearLayout) card).addView(next);

        progress = new View(c);
        progress.setBackground(Ui.rounded(0xB3FFFFFF, Theme.dpF(c, 1f)));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(0, Theme.dp(c, 2));
        pp.gravity = Gravity.BOTTOM | Gravity.START;
        ((LinearLayout) card).addView(progress, pp);

        card.setOnClickListener(v -> open());
    }

    private void open() {
        activity.nowPlaying().open();
    }

    public View getView() {
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    public void refresh() {
        Models.Track track = Player.current();
        if (track == null) return;
        Display d = Display.track(track);
        title.setText(track.title);
        subtitle.setText(track.artistNames());
        cover.setUrl(d.thumb != null ? d.thumb : d.cover, d.cover);
        spinner.setVisibility(Player.isBuffering() ? VISIBLE : GONE);
        int res = getContext().getResources().getIdentifier(Player.isPlaying() ? "ic_pause" : "ic_play_arrow", "drawable", getContext().getPackageName());
        if (playIcon.getChildCount() > 0 && playIcon.getChildAt(0) instanceof ImageView) {
            ImageView iv = (ImageView) playIcon.getChildAt(0);
            if (res != 0) iv.setImageResource(res);
            iv.setColorFilter(Theme.ON);
        }
        long duration = Player.duration();
        long position = Player.position();
        float ratio = duration > 0 ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
        card.post(() -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) progress.getLayoutParams();
            p.width = Math.round(card.getWidth() * ratio);
            progress.setLayoutParams(p);
        });
    }
}
