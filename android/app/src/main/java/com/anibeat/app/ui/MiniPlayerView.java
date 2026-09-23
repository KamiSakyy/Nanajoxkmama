package com.anibeat.app.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.R;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Models;
import com.anibeat.app.player.Player;
import com.google.android.material.card.MaterialCardView;

/** Мини-плеер над нижним меню. */
public class MiniPlayerView extends FrameLayout {

    public static final int HEIGHT_DP = 64;

    private final ImageView cover;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView toggle;
    private final View progress;
    private String shownCover = "";

    public MiniPlayerView(final Host host) {
        super(host.activity());
        Context context = host.activity();
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, HEIGHT_DP)));

        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(Theme.SURFACE_3);
        card.setRadius(Theme.dpF(context, 18));
        card.setCardElevation(0f);
        card.setStrokeWidth(Theme.dp(context, 1));
        card.setStrokeColor(Theme.OUTLINE_VARIANT);
        addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(context, 10), Theme.dp(context, 8), Theme.dp(context, 8), Theme.dp(context, 8));
        card.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cover = new ImageView(context);
        row.addView(cover, new LinearLayout.LayoutParams(Theme.dp(context, 42), Theme.dp(context, 42)));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = Theme.dp(context, 11);
        columnParams.rightMargin = Theme.dp(context, 6);
        row.addView(column, columnParams);

        title = new TextView(context);
        title.setTextSize(13.5f);
        title.setTextColor(Theme.ON);
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(3);
        title.setSelected(true);
        column.addView(title);

        subtitle = new TextView(context);
        subtitle.setTextSize(11.5f);
        subtitle.setTextColor(Theme.ON_VARIANT);
        subtitle.setIncludeFontPadding(false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(subtitle);

        toggle = new ImageView(context);
        toggle.setImageResource(R.drawable.ic_pause);
        toggle.setColorFilter(Theme.ON);
        toggle.setPadding(Theme.dp(context, 9), Theme.dp(context, 9), Theme.dp(context, 9), Theme.dp(context, 9));
        row.addView(toggle, new LinearLayout.LayoutParams(Theme.dp(context, 40), Theme.dp(context, 40)));
        toggle.setOnClickListener(v -> Player.toggle());
        Ui.ripple(toggle);

        ImageView next = new ImageView(context);
        next.setImageResource(R.drawable.ic_skip_next);
        next.setColorFilter(Theme.ON);
        next.setPadding(Theme.dp(context, 9), Theme.dp(context, 9), Theme.dp(context, 9), Theme.dp(context, 9));
        row.addView(next, new LinearLayout.LayoutParams(Theme.dp(context, 40), Theme.dp(context, 40)));
        next.setOnClickListener(v -> Player.next(false));
        Ui.ripple(next);

        progress = new View(context);
        progress.setBackground(Ui.rounded(context, Theme.ACCENT, 2f));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(0, Theme.dp(context, 3));
        progressParams.gravity = Gravity.BOTTOM;
        progressParams.bottomMargin = Theme.dp(context, 6);
        addView(progress, progressParams);

        card.setOnClickListener(v -> host.openNowPlaying());
        Ui.ripple(card);
        refresh();
    }

    /** Обновить данные плеера. */
    public void refresh() {
        Ui.safe(() -> {
            Models.Track track = Player.current();
            if (track == null) {
                setVisibility(GONE);
                return;
            }
            title.setText(track.title == null || track.title.isEmpty() ? track.themeSlug : track.title);
            subtitle.setText(TrackRows.subtitleOf(track));
            toggle.setImageResource(Player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            Display display = Display.track(track);
            String url = display.thumb != null ? display.thumb : display.cover;
            if (url != null && !url.equals(shownCover)) {
                shownCover = url;
                Img.loadRounded(cover, url, Img.size(getContext(), 42), 9f);
            }
            tick();
        });
    }

    /** Подвинуть полоску проигрывания. */
    public void tick() {
        Ui.safe(() -> {
            long duration = Player.duration();
            long position = Player.position();
            float ratio = duration > 0 ? Math.min(1f, position / (float) duration) : 0f;
            int full = getWidth() - getPaddingLeft() - getPaddingRight();
            int width = Math.round(full * ratio);
            ViewGroup.LayoutParams params = progress.getLayoutParams();
            if (params.width != width) {
                params.width = Math.max(0, width);
                progress.setLayoutParams(params);
            }
        });
    }
}
