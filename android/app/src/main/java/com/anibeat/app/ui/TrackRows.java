package com.anibeat.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Models;

/** Строка трека: номер, обложка, название, исполнитель, метка скачивания и меню. */
public final class TrackRows {

    private TrackRows() {
    }

    public static View create(Context context, Models.Track track, int number, boolean playing,
                              View.OnClickListener onPlay, View.OnClickListener onMenu) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Theme.dp(context, 12);
        row.setPadding(pad, Theme.dp(context, 6), Theme.dp(context, 6), Theme.dp(context, 6));
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 64)));

        FrameLayout marker = new FrameLayout(context);
        LinearLayout.LayoutParams markerParams = new LinearLayout.LayoutParams(Theme.dp(context, 28), Theme.dp(context, 28));
        marker.setLayoutParams(markerParams);
        TextView markerText = new TextView(context);
        markerText.setTextSize(11.5f);
        markerText.setTextColor(playing ? Theme.ACCENT : Theme.ON_DIM);
        markerText.setGravity(Gravity.CENTER);
        markerText.setText(playing ? "▶" : (number > 0 ? String.valueOf(number) : "•"));
        marker.addView(markerText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(marker);

        ImageView cover = new ImageView(context);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(Theme.dp(context, 46), Theme.dp(context, 46));
        coverParams.rightMargin = Theme.dp(context, 12);
        cover.setLayoutParams(coverParams);
        Display coverInfo = Display.track(track);
        Img.loadRounded(cover, coverInfo.thumb != null ? coverInfo.thumb : coverInfo.cover, Img.size(context, 46), 9f);
        row.addView(cover);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        column.setLayoutParams(columnParams);

        TextView title = new TextView(context);
        title.setText(track.title == null || track.title.isEmpty() ? track.themeSlug : track.title);
        title.setTextSize(14.5f);
        title.setTextColor(Theme.ON);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(subtitleOf(track));
        subtitle.setTextSize(11.5f);
        subtitle.setTextColor(Theme.ON_VARIANT);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Theme.dp(context, 2);
        column.addView(subtitle, subtitleParams);
        row.addView(column);

        TextView badge = new TextView(context);
        badge.setText(track.themeSlug == null || track.themeSlug.isEmpty() ? track.type : track.themeSlug.toUpperCase());
        badge.setTextSize(10f);
        badge.setTextColor(Theme.ON_VARIANT);
        badge.setBackground(Ui.rounded(context, Theme.SURFACE_3, 7f));
        badge.setPadding(Theme.dp(context, 7), Theme.dp(context, 3), Theme.dp(context, 7), Theme.dp(context, 3));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = Theme.dp(context, 8);
        row.addView(badge, badgeParams);

        boolean offline = Downloads.hasOffline(track.id, Downloads.KIND_AUDIO)
                || Downloads.hasOffline(track.id, Downloads.KIND_VIDEO);
        if (offline) {
            ImageView done = new ImageView(context);
            done.setImageResource(com.anibeat.app.R.drawable.ic_download_done);
            done.setColorFilter(Theme.TERTIARY);
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(Theme.dp(context, 18), Theme.dp(context, 18));
            doneParams.leftMargin = Theme.dp(context, 8);
            row.addView(done, doneParams);
        }

        ImageView menu = new ImageView(context);
        menu.setImageResource(com.anibeat.app.R.drawable.ic_more_vert);
        menu.setColorFilter(Theme.ON_VARIANT);
        menu.setPadding(Theme.dp(context, 8), Theme.dp(context, 8), Theme.dp(context, 8), Theme.dp(context, 8));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(Theme.dp(context, 36), Theme.dp(context, 36));
        menuParams.leftMargin = Theme.dp(context, 4);
        row.addView(menu, menuParams);
        menu.setOnClickListener(onMenu);
        Ui.ripple(menu);

        row.setOnClickListener(onPlay);
        row.setOnLongClickListener(v -> {
            onMenu.onClick(v);
            return true;
        });
        Ui.ripple(row);
        return row;
    }

    public static String subtitleOf(Models.Track track) {
        StringBuilder sb = new StringBuilder();
        String artists = track.artistNames();
        if (artists != null && !artists.isEmpty()) sb.append(artists);
        String anime = track.anime == null ? "" : track.anime.name;
        Display display = Display.track(track);
        String animeTitle = display != null && display.title != null && !display.title.isEmpty() ? display.title : anime;
        if (animeTitle != null && !animeTitle.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(animeTitle);
        }
        if (track.year != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(track.year);
        }
        return sb.toString();
    }
}
