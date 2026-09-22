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
        private final ImageView markerIcon;
        private final ImageView cover;
        private final ImageView playOverlay;
        private final View playScrim;
        private final TextView title;
        private final TextView subtitle;
        private final TextView badge;
        private final TextView progress;
        private final ImageView done;
        private final ImageView menu;
        private Runnable action;
        private Runnable menuAction;
        private Models.Track currentTrack;
        private String shownCover = "\u0000";
        private String shownProgress = "";
        private String shownTone = "\u0000";

        Holder(View view, TextView marker, ImageView markerIcon, ImageView cover, ImageView playOverlay,
               View playScrim, TextView title, TextView subtitle, TextView badge, TextView progress,
               ImageView done, ImageView menu) {
            this.view = view;
            this.marker = marker;
            this.markerIcon = markerIcon;
            this.cover = cover;
            this.playOverlay = playOverlay;
            this.playScrim = playScrim;
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

        /** Трек, который сейчас показан в этой строке. */
        public Models.Track track() {
            return currentTrack;
        }

        private void bindSafe(Models.Track track, int number, boolean playing) {
            currentTrack = track;
            boolean active = playing;
            boolean isPlaying = active && com.anibeat.app.player.Player.isPlaying();
            marker.setVisibility(active ? View.GONE : View.VISIBLE);
            marker.setTextColor(Theme.ON_DIM);
            marker.setText(number > 0 ? String.valueOf(number) : "");
            if (markerIcon != null) {
                markerIcon.setVisibility(active ? View.VISIBLE : View.GONE);
                markerIcon.setImageResource(isPlaying
                        ? com.anibeat.app.R.drawable.ic_graphic_eq
                        : com.anibeat.app.R.drawable.ic_play_arrow);
                markerIcon.setColorFilter(Theme.ACCENT);
            }
            if (playOverlay != null) {
                playOverlay.setImageResource(isPlaying
                        ? com.anibeat.app.R.drawable.ic_pause
                        : com.anibeat.app.R.drawable.ic_play_arrow);
                playOverlay.setColorFilter(0xFFFFFFFF);
            }
            if (playScrim != null) {
                playScrim.setBackgroundColor(active ? 0xCC000000 : 0x66000000);
            }

            String name = track.title == null || track.title.isEmpty() ? track.themeSlug : track.title;
            if (!name.equals(title.getText().toString())) title.setText(name);

            String sub = subtitleOf(track);
            if (!sub.equals(subtitle.getText().toString())) subtitle.setText(sub);

            String badgeText = track.themeSlug == null || track.themeSlug.isEmpty()
                    ? (track.type == null ? "" : track.type) : track.themeSlug.toUpperCase();
            if (!badgeText.equals(badge.getText().toString())) badge.setText(badgeText);
            badge.setVisibility(badgeText.isEmpty() ? View.GONE : View.VISIBLE);
            String typeKey = track.type == null ? "" : track.type.toUpperCase();
            int tone = "ED".equals(typeKey) ? Theme.SECONDARY : ("IN".equals(typeKey) ? Theme.TERTIARY : Theme.PRIMARY);
            String toneKey = typeKey + "|" + badgeText;
            if (!toneKey.equals(shownTone)) {
                shownTone = toneKey;
                badge.setTextColor(tone);
                badge.setBackground(Ui.rounded(view.getContext(), Theme.mix(Theme.SURFACE_3, tone, 0.18f), 7f));
            }

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
        row.setPadding(pad, Theme.dp(context, 8), Theme.dp(context, 6), Theme.dp(context, 8));
        row.setMinimumHeight(Theme.dp(context, 72));
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout markerBox = new FrameLayout(context);
        markerBox.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(context, 28), Theme.dp(context, 28)));
        TextView marker = new TextView(context);
        marker.setTextSize(11.5f);
        marker.setTextColor(Theme.ON_DIM);
        marker.setIncludeFontPadding(false);
        marker.setGravity(Gravity.CENTER);
        markerBox.addView(marker, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView markerIcon = new ImageView(context);
        markerIcon.setVisibility(View.GONE);
        markerIcon.setColorFilter(Theme.ACCENT);
        FrameLayout.LayoutParams markerIconParams = new FrameLayout.LayoutParams(
                Theme.dp(context, 17), Theme.dp(context, 17));
        markerIconParams.gravity = Gravity.CENTER;
        markerBox.addView(markerIcon, markerIconParams);
        row.addView(markerBox);

        FrameLayout coverBox = new FrameLayout(context);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(
                Theme.dp(context, 50), Theme.dp(context, 50));
        coverParams.rightMargin = Theme.dp(context, 12);
        coverBox.setLayoutParams(coverParams);

        ImageView cover = new ImageView(context);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverBox.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Кнопка «играть/пауза» прямо на обложке — видна всегда, как в карточке песни на сайте.
        FrameLayout overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x66000000);
        ImageView playOverlay = new ImageView(context);
        playOverlay.setImageResource(com.anibeat.app.R.drawable.ic_play_arrow);
        playOverlay.setColorFilter(0xFFFFFFFF);
        int playPad = Theme.dp(context, 13);
        playOverlay.setPadding(playPad, playPad, playPad, playPad);
        overlay.addView(playOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        coverBox.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(coverBox);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setTextSize(14.5f);
        title.setTextColor(Theme.ON);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);
        column.addView(head);

        TextView subtitle = new TextView(context);
        subtitle.setTextSize(11.5f);
        subtitle.setTextColor(Theme.ON_VARIANT);
        subtitle.setSingleLine(true);
        subtitle.setIncludeFontPadding(false);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = Theme.dp(context, 2);
        column.addView(subtitle, subtitleParams);
        row.addView(column);

        TextView badge = new TextView(context);
        badge.setTextSize(10f);
        badge.setTextColor(Theme.ON_VARIANT);
        badge.setSingleLine(true);
        badge.setIncludeFontPadding(false);
        badge.setMaxWidth(Theme.dp(context, 70));
        badge.setEllipsize(TextUtils.TruncateAt.END);
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

        final Holder holder = new Holder(row, marker, markerIcon, cover, playOverlay, overlay, title,
                subtitle, badge, progress, done, menu);
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
        coverBox.setOnClickListener(v -> {
            Models.Track track = holder.track();
            if (track == null) return;
            boolean active = track.id != null && com.anibeat.app.player.Player.current() != null
                    && track.id.equals(com.anibeat.app.player.Player.current().id);
            if (active) com.anibeat.app.player.Player.toggle();
            else if (holder.action != null) holder.action.run();
        });
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
        String day = Dates.relativeDay(track.createdAt);
        if (!day.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(day);
        }
        return sb.toString();
    }
}
