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

/**
 * Строка трека: номер, обложка, название, исполнитель, метка скачивания и меню.
 * Строка создаётся один раз и потом только обновляется — из-за этого список не мерцает.
 */
public final class TrackRows {

    private TrackRows() {
    }

    /** Многоразовая строка трека: повторная привязка не пересоздаёт вьюхи. */
    public static final class Holder {
        public final View view;
        private final TextView marker;
        private final ImageView cover;
        private final TextView title;
        private final TextView subtitle;
        private final TextView badge;
        private final TextView progress;
        private final ImageView done;
        private final ImageView menu;
        private Runnable action;
        private Runnable menuAction;
        private String shownCover = "\u0000";
        private String shownProgress = "";

        Holder(View view, TextView marker, ImageView cover, TextView title, TextView subtitle,
               TextView badge, TextView progress, ImageView done, ImageView menu) {
            this.view = view;
            this.marker = marker;
            this.cover = cover;
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
            this.progress = progress;
            this.done = done;
            this.menu = menu;
        }

        public void onClick(Runnable runnable) {
            action = runnable;
        }

        public void onMenu(Runnable runnable) {
            menuAction = runnable;
        }

        public void bind(Models.Track track, int number, boolean playing) {
            if (view == null) return;
            try {
                bindSafe(track, number, playing);
            } catch (Throwable t) {
                Ui.report(t);
            }
        }

        private void bindSafe(Models.Track track, int number, boolean playing) {
            marker.setText(playing ? "\u25b6" : (number > 0 ? String.valueOf(number) : "\u2022"));
            marker.setTextColor(playing ? Theme.ACCENT : Theme.ON_DIM);

            String name = track.title == null || track.title.isEmpty() ? track.themeSlug : track.title;
            if (!name.equals(title.getText().toString())) title.setText(name);

            String sub = subtitleOf(track);
            if (!sub.equals(subtitle.getText().toString())) subtitle.setText(sub);

            String badgeText = track.themeSlug == null || track.themeSlug.isEmpty()
                    ? (track.type == null ? "" : track.type) : track.themeSlug.toUpperCase();
            if (!badgeText.equals(badge.getText().toString())) badge.setText(badgeText);
            badge.setVisibility(badgeText.isEmpty() ? View.GONE : View.VISIBLE);

            Display display = Display.track(track);
            String url = display.thumb != null ? display.thumb : display.cover;
            if (url == null) url = "";
            if (!url.equals(shownCover)) {
                shownCover = url;
                Img.loadRounded(cover, url, Img.size(view.getContext(), 46), 9f);
            }

            int percent = Downloads.progressOf(track.id, Downloads.KIND_AUDIO);
            if (percent < 100) percent = Math.max(percent, Downloads.progressOf(track.id, Downloads.KIND_VIDEO));
            boolean downloading = percent >= 0 && percent < 100;
            String progressText = downloading ? (percent <= 0 ? "…" : percent + "%") : "";
            if (!progressText.equals(shownProgress)) {
                shownProgress = progressText;
                progress.setText(progressText);
                progress.setVisibility(downloading ? View.VISIBLE : View.GONE);
                done.setVisibility(!downloading && (Downloads.hasOffline(track.id, Downloads.KIND_AUDIO)
                        || Downloads.hasOffline(track.id, Downloads.KIND_VIDEO)) ? View.VISIBLE : View.GONE);
            }
        }

        public void clearCover() {
            shownCover = "\u0000";
            shownProgress = "";
        }
    }

    /** Создать строку с обработчиками. */
    public static Holder holder(Context context, final View.OnClickListener onPlay,
                               final View.OnClickListener onMenu) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Theme.dp(context, 12);
        row.setPadding(pad, Theme.dp(context, 6), Theme.dp(context, 6), Theme.dp(context, 6));
        row.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 64)));

        FrameLayout markerBox = new FrameLayout(context);
        markerBox.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(context, 28), Theme.dp(context, 28)));
        TextView marker = new TextView(context);
        marker.setTextSize(11.5f);
        marker.setGravity(Gravity.CENTER);
        markerBox.addView(marker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(markerBox);

        ImageView cover = new ImageView(context);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 46), Theme.dp(context, 46));
        coverParams.rightMargin = Theme.dp(context, 12);
        cover.setLayoutParams(coverParams);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(cover);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setTextSize(14.5f);
        title.setTextColor(Theme.ON);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(title);

        TextView subtitle = new TextView(context);
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
        badge.setTextSize(10f);
        badge.setTextColor(Theme.ON_VARIANT);
        badge.setBackground(Ui.rounded(context, Theme.SURFACE_3, 7f));
        badge.setPadding(Theme.dp(context, 7), Theme.dp(context, 3), Theme.dp(context, 7), Theme.dp(context, 3));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = Theme.dp(context, 8);
        row.addView(badge, badgeParams);

        TextView progress = new TextView(context);
        progress.setTextSize(10.5f);
        progress.setTextColor(Theme.ACCENT);
        progress.setBackground(Ui.rounded(context, Theme.mix(Theme.SURFACE_3, Theme.ACCENT, 0.25f), 7f));
        progress.setPadding(Theme.dp(context, 7), Theme.dp(context, 3), Theme.dp(context, 7), Theme.dp(context, 3));
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.leftMargin = Theme.dp(context, 8);
        row.addView(progress, progressParams);

        ImageView done = new ImageView(context);
        done.setImageResource(com.anibeat.app.R.drawable.ic_download_done);
        done.setColorFilter(Theme.TERTIARY);
        done.setVisibility(View.GONE);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 18), Theme.dp(context, 18));
        doneParams.leftMargin = Theme.dp(context, 8);
        row.addView(done, doneParams);

        ImageView menu = new ImageView(context);
        menu.setImageResource(com.anibeat.app.R.drawable.ic_more_vert);
        menu.setColorFilter(Theme.ON_VARIANT);
        menu.setPadding(Theme.dp(context, 8), Theme.dp(context, 8), Theme.dp(context, 8), Theme.dp(context, 8));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 36), Theme.dp(context, 36));
        menuParams.leftMargin = Theme.dp(context, 4);
        row.addView(menu, menuParams);

        final Holder holder = new Holder(row, marker, cover, title, subtitle, badge, progress, done, menu);
        row.setOnClickListener(v -> {
            if (holder.action != null) holder.action.run();
        });
        menu.setOnClickListener(v -> {
            if (holder.menuAction != null) holder.menuAction.run();
        });
        row.setOnLongClickListener(v -> {
            if (holder.menuAction != null) holder.menuAction.run();
            return true;
        });
        Ui.press(row);
        return holder;
    }

    /** Одноразовая строка (панели, диалоговые списки). */
    public static View create(Context context, final Models.Track track, int number, boolean playing,
                              final View.OnClickListener onPlay, final View.OnClickListener onMenu) {
        final Holder holder = holder(context, onPlay, onMenu);
        holder.onClick(() -> {
            if (onPlay != null) onPlay.onClick(holder.view);
        });
        holder.onMenu(() -> {
            if (onMenu != null) onMenu.onClick(holder.view);
        });
        holder.bind(track, number, playing);
        return holder.view;
    }

    public static String subtitleOf(Models.Track track) {
        if (track == null) return "";
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
        if (track.anime != null && track.anime.year != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(track.anime.year);
        }
        return sb.toString();
    }
}
