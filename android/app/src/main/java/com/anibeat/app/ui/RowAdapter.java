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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Models;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/** Горизонтальная карусель: аниме, исполнители, подборки, треки или плейлисты. */
public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {

    public static final int ANIME = 1;
    public static final int ARTIST = 2;
    public static final int MIX = 3;
    public static final int TRACK = 4;
    public static final int PLAYLIST = 5;

    private final int kind;
    private final Host host;
    private final List<Object> items = new ArrayList<>();
    private final java.util.Set<String> mixRequested = new java.util.HashSet<>();
    private String playingId = "";

    public RowAdapter(int kind, Host host) {
        this.kind = kind;
        this.host = host;
    }

    public void submit(List<?> list) {
        List<Object> fresh = new ArrayList<>();
        if (list != null) fresh.addAll(list);
        if (sameItems(fresh)) return;
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
    }

    /** Список тот же — перерисовка не нужна. */
    private boolean sameItems(List<Object> fresh) {
        if (fresh.size() != items.size()) return false;
        for (int i = 0; i < fresh.size(); i++) {
            if (!String.valueOf(keyOf(fresh.get(i))).equals(String.valueOf(keyOf(items.get(i))))) return false;
        }
        return true;
    }

    private static String keyOf(Object item) {
        if (item instanceof Models.Track) {
            Models.Track track = (Models.Track) item;
            return track.id + "~" + track.themeSlug + "~" + track.type;
        }
        if (item instanceof Models.AnimeSummary) return ((Models.AnimeSummary) item).slug;
        if (item instanceof Models.ArtistSummary) return ((Models.ArtistSummary) item).slug;
        if (item instanceof Models.Mix) return ((Models.Mix) item).id;
        if (item instanceof Models.Playlist) return ((Models.Playlist) item).id;
        return String.valueOf(item);
    }

    public int kind() {
        return kind;
    }

    public void setPlayingId(String id) {
        String value = id == null ? "" : id;
        if (value.equals(playingId)) return;
        playingId = value;
        if (kind == TRACK) notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title;
        TextView subtitle;
        View badge;
        android.widget.GridLayout mosaic;
        ImageView[] tiles;
        ImageView play;
        FrameLayout tilePlay;
        ImageView tilePlayIcon;

        VH(View view) {
            super(view);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        VH holder;
        switch (kind) {
            case ANIME: {
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                int width = Theme.dp(context, 112);
                column.setLayoutParams(new RecyclerView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
                MaterialCardView card = new MaterialCardView(context);
                card.setCardBackgroundColor(Theme.SURFACE_2);
                card.setRadius(Theme.dpF(context, 12));
                card.setCardElevation(0f);
                FrameLayout frame = new FrameLayout(context);
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                frame.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                TextView score = new TextView(context);
                score.setTextSize(10.5f);
                score.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                score.setTextColor(0xFFFFFFFF);
                score.setBackground(Ui.rounded(context, 0xB3000000, 7f));
                score.setPadding(Theme.dp(context, 6), Theme.dp(context, 2), Theme.dp(context, 6), Theme.dp(context, 2));
                FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                scoreParams.gravity = Gravity.TOP | Gravity.START;
                scoreParams.topMargin = Theme.dp(context, 6);
                scoreParams.leftMargin = Theme.dp(context, 6);
                frame.addView(score, scoreParams);
                card.addView(frame, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 168)));
                column.addView(card);
                TextView title = new TextView(context);
                title.setTextSize(13.5f);
                title.setTextColor(Theme.ON);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                titleParams.topMargin = Theme.dp(context, 8);
                column.addView(title, titleParams);
                TextView year = new TextView(context);
                year.setTextSize(12f);
                year.setTextColor(Theme.ON_VARIANT);
                year.setMaxLines(1);
                LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                yearParams.topMargin = Theme.dp(context, 4);
                column.addView(year, yearParams);
                holder = new VH(column);
                holder.image = image;
                holder.title = title;
                holder.subtitle = year;
                holder.badge = score;
                break;
            }
            case ARTIST: {
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setGravity(Gravity.CENTER_HORIZONTAL);
                column.setLayoutParams(new RecyclerView.LayoutParams(Theme.dp(context, 92), ViewGroup.LayoutParams.WRAP_CONTENT));
                ImageView image = new ImageView(context);
                image.setLayoutParams(new LinearLayout.LayoutParams(Theme.dp(context, 84), Theme.dp(context, 84)));
                column.addView(image);
                TextView title = new TextView(context);
                title.setTextSize(13f);
                title.setTextColor(Theme.ON);
                title.setGravity(Gravity.CENTER);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                titleParams.topMargin = Theme.dp(context, 7);
                column.addView(title, titleParams);
                holder = new VH(column);
                holder.image = image;
                holder.title = title;
                break;
            }
            case MIX:
            case PLAYLIST: {
                FrameLayout frame = new FrameLayout(context);
                frame.setLayoutParams(new RecyclerView.LayoutParams(Theme.dp(context, 196), Theme.dp(context, 108)));
                MaterialCardView card = new MaterialCardView(context);
                card.setCardBackgroundColor(Theme.SURFACE_3);
                card.setRadius(Theme.dpF(context, 16));
                card.setCardElevation(0f);
                card.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                // Превью подборки: мозаика из обложек входящих аниме.
                android.widget.GridLayout mosaic = new android.widget.GridLayout(context);
                mosaic.setColumnCount(2);
                mosaic.setRowCount(2);
                mosaic.setVisibility(View.GONE);
                ImageView[] tiles = new ImageView[4];
                for (int i = 0; i < 4; i++) {
                    ImageView tile = new ImageView(context);
                    tile.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    android.widget.GridLayout.LayoutParams tileParams = new android.widget.GridLayout.LayoutParams(
                            android.widget.GridLayout.spec(i / 2, 1f), android.widget.GridLayout.spec(i % 2, 1f));
                    mosaic.addView(tile, tileParams);
                    tiles[i] = tile;
                }
                card.addView(mosaic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                View scrim = new View(context);
                scrim.setBackground(Ui.gradient(0x33000000, 0xE6000000));
                card.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                frame.addView(card);

                ImageView play = new ImageView(context);
                play.setImageResource(com.anibeat.app.R.drawable.ic_play_arrow);
                play.setColorFilter(0xFF101014);
                play.setBackground(Ui.circle(0xFFFFFFFF));
                play.setPadding(Theme.dp(context, 7), Theme.dp(context, 7), Theme.dp(context, 7), Theme.dp(context, 7));
                FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                        Theme.dp(context, 34), Theme.dp(context, 34));
                playParams.gravity = Gravity.RIGHT | Gravity.BOTTOM;
                playParams.rightMargin = Theme.dp(context, 14);
                playParams.bottomMargin = Theme.dp(context, 12);
                frame.addView(play, playParams);
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setGravity(Gravity.BOTTOM);
                column.setPadding(Theme.dp(context, 16), Theme.dp(context, 12), Theme.dp(context, 48), Theme.dp(context, 14));
                frame.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                TextView title = new TextView(context);
                title.setTextSize(15f);
                title.setTextColor(Theme.ON);
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                column.addView(title);
                TextView subtitle = new TextView(context);
                subtitle.setTextSize(12f);
                subtitle.setTextColor(0xFFD0D0D6);
                subtitle.setMaxLines(1);
                subtitle.setEllipsize(TextUtils.TruncateAt.END);
                column.addView(subtitle);
                holder = new VH(frame);
                holder.image = image;
                holder.title = title;
                holder.subtitle = subtitle;
                holder.mosaic = mosaic;
                holder.tiles = tiles;
                holder.play = play;
                break;
            }
            default: {
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setLayoutParams(new RecyclerView.LayoutParams(Theme.dp(context, 138), ViewGroup.LayoutParams.WRAP_CONTENT));
                MaterialCardView card = new MaterialCardView(context);
                card.setCardBackgroundColor(Theme.SURFACE_2);
                card.setRadius(Theme.dpF(context, 14));
                card.setCardElevation(0f);
                ImageView image = new ImageView(context);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 138)));
                View tileScrim = new View(context);
                tileScrim.setBackgroundColor(0x40000000);
                card.addView(tileScrim, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 138)));
                FrameLayout tilePlay = new FrameLayout(context);
                tilePlay.setBackground(Ui.circle(0xFFFFFFFF));
                ImageView tilePlayIcon = new ImageView(context);
                tilePlayIcon.setImageResource(com.anibeat.app.R.drawable.ic_play_arrow);
                tilePlayIcon.setColorFilter(0xFF101014);
                int tilePad = Theme.dp(context, 13);
                tilePlayIcon.setPadding(tilePad, tilePad, tilePad, tilePad);
                tilePlay.addView(tilePlayIcon, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                FrameLayout.LayoutParams tilePlayParams = new FrameLayout.LayoutParams(
                        Theme.dp(context, 48), Theme.dp(context, 48));
                tilePlayParams.gravity = Gravity.CENTER;
                card.addView(tilePlay, tilePlayParams);
                column.addView(card);
                TextView title = new TextView(context);
                title.setTextSize(13f);
                title.setTextColor(Theme.ON);
                title.setMaxLines(2);
                title.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                titleParams.topMargin = Theme.dp(context, 7);
                column.addView(title, titleParams);
                TextView subtitle = new TextView(context);
                subtitle.setTextSize(11f);
                subtitle.setTextColor(Theme.ON_VARIANT);
                subtitle.setMaxLines(1);
                subtitle.setEllipsize(TextUtils.TruncateAt.END);
                column.addView(subtitle);
                holder = new VH(column);
                holder.image = image;
                holder.title = title;
                holder.subtitle = subtitle;
                holder.tilePlay = tilePlay;
                holder.tilePlayIcon = tilePlayIcon;
                break;
            }
        }
        holder.itemView.setPadding(0, 0, Theme.dp(context, 10), 0);
        Ui.press(holder.itemView);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        try {
            bindSafe(holder, position);
        } catch (Throwable t) {
            com.anibeat.app.core.Ui.report(t);
        }
    }

    private void bindSafe(@NonNull VH holder, int position) {
        Object item = items.get(position);
        Context context = holder.itemView.getContext();
        final int index = position;
        if (kind == ANIME && item instanceof Models.AnimeSummary) {
            Models.AnimeSummary anime = (Models.AnimeSummary) item;
            Display display = Display.summary(anime);
            Img.loadRounded(holder.image, display.thumb != null ? display.thumb : display.cover, Img.size(context, 118), 14f);
            holder.title.setText(display.title);
            if (holder.subtitle != null) {
                holder.subtitle.setText(anime.year != null ? String.valueOf(anime.year) : "");
                holder.subtitle.setVisibility(anime.year != null ? View.VISIBLE : View.GONE);
            }
            TextView score = (TextView) holder.badge;
            if (display.score != null && display.score > 0) {
                score.setVisibility(View.VISIBLE);
                score.setText(String.format(java.util.Locale.US, "%.1f", display.score));
            } else {
                score.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> host.openAnime(anime));
        } else if (kind == ARTIST && item instanceof Models.ArtistSummary) {
            Models.ArtistSummary artist = (Models.ArtistSummary) item;
            Img.loadCircle(holder.image, artist.imageSmall != null ? artist.imageSmall : artist.image, Img.size(context, 84));
            holder.title.setText(artist.name);
            holder.itemView.setOnClickListener(v -> host.openArtist(artist));
        } else if (kind == MIX && item instanceof Models.Mix) {
            final Models.Mix mix = (Models.Mix) item;
            holder.title.setText(mix.title);
            holder.subtitle.setText(mix.subtitle);
            holder.itemView.setOnClickListener(v -> host.openMix(mix));
            bindMixPreview(holder, context, mix, index);
        } else if (kind == PLAYLIST && item instanceof Models.Playlist) {
            Models.Playlist playlist = (Models.Playlist) item;
            Models.Track first = playlist.tracks.isEmpty() ? null : playlist.tracks.get(0);
            Display display = first == null ? null : Display.track(first);
            Img.load(holder.image, display == null ? null : display.cover, Img.size(context, 210));
            holder.title.setText(playlist.name);
            holder.subtitle.setText(com.anibeat.app.ui.Format.plural(playlist.tracks.size(), "трек", "трека", "треков"));
            holder.itemView.setOnClickListener(v -> host.openPlaylist(playlist));
        } else if (item instanceof Models.Track) {
            final Models.Track track = (Models.Track) item;
            Display display = Display.track(track);
            boolean playing = track.id != null && track.id.equals(playingId);
            Img.loadRounded(holder.image, display.thumb != null ? display.thumb : display.cover, Img.size(context, 156), 14f);
            holder.title.setText(track.title == null || track.title.isEmpty() ? track.themeSlug : track.title);
            holder.subtitle.setText(track.artistNames());
            if (playing) holder.title.setTextColor(Theme.ACCENT);
            else holder.title.setTextColor(Theme.ON);
            if (holder.tilePlayIcon != null) {
                boolean isPlaying = playing && com.anibeat.app.player.Player.isPlaying();
                holder.tilePlayIcon.setImageResource(isPlaying
                        ? com.anibeat.app.R.drawable.ic_pause
                        : com.anibeat.app.R.drawable.ic_play_arrow);
            }
            final Runnable tileToggle = () -> {
                Models.Track now = com.anibeat.app.player.Player.current();
                if (playing && now != null && track.id != null && track.id.equals(now.id)) {
                    com.anibeat.app.player.Player.toggle();
                } else {
                    host.playTrack(track, tracks(), index);
                }
            };
            holder.itemView.setOnClickListener(v -> tileToggle.run());
            if (holder.tilePlay != null) holder.tilePlay.setOnClickListener(v -> tileToggle.run());
            holder.itemView.setOnLongClickListener(v -> {
                host.trackMenu(track, v);
                return true;
            });
        }
    }

    private List<Models.Track> tracks() {
        List<Models.Track> list = new ArrayList<>();
        for (Object item : items) if (item instanceof Models.Track) list.add((Models.Track) item);
        return list;
    }

    /** Превью подборки: мозаика обложек; если их ещё нет — просим пачкой и перерисовываем. */
    private void bindMixPreview(VH holder, Context context, final Models.Mix mix, final int index) {
        if (holder.mosaic == null || holder.tiles == null) {
            Img.loadRounded(holder.image, null, Img.size(context, 210), 16f);
            return;
        }
        List<String> covers = MixCovers.previewOf(mix);
        if (covers.isEmpty()) {
            holder.mosaic.setVisibility(View.GONE);
            holder.image.setVisibility(View.VISIBLE);
            holder.image.setScaleType(ImageView.ScaleType.CENTER);
            holder.image.setPadding(Theme.dp(context, 74), Theme.dp(context, 34), Theme.dp(context, 74), Theme.dp(context, 34));
            holder.image.setImageResource(com.anibeat.app.R.drawable.ic_auto_awesome);
            holder.image.setColorFilter(0x66FFFFFF);
            if (mix.id != null && !mixRequested.contains(mix.id)) {
                mixRequested.add(mix.id);
                List<Models.Mix> one = new ArrayList<>();
                one.add(mix);
                MixCovers.ensure(context, one, () -> notifyItemChanged(index));
            }
        } else {
            holder.mosaic.setVisibility(View.VISIBLE);
            holder.image.setVisibility(View.GONE);
            int side = Img.size(context, 108);
            for (int i = 0; i < 4; i++) {
                String url = i < covers.size() ? covers.get(i) : covers.get(covers.size() - 1);
                Img.load(holder.tiles[i], url, side);
            }
        }
        if (holder.play != null) {
            holder.play.setImageResource(com.anibeat.app.R.drawable.ic_play_arrow);
            holder.play.setOnClickListener(v -> playMix(mix));
        }
    }

    /** Кнопка на карточке подборки: играем подборку сразу, не открывая список. */
    private void playMix(final Models.Mix mix) {
        if (mix == null || mix.slugs == null || mix.slugs.length == 0) return;
        com.anibeat.app.data.Api.getTracksForAnimeSlugs(java.util.Arrays.asList(mix.slugs), (tracks, error) -> Ui.postSafe(() -> {
            if (error != null || tracks == null || tracks.isEmpty()) {
                host.toast("Подборка недоступна");
                host.openMix(mix);
                return;
            }
            List<Models.Track> clean = com.anibeat.app.data.Settings.filterMature(tracks);
            if (clean.isEmpty()) clean = tracks;
            host.playTrack(clean.get(0), clean, 0);
            host.openNowPlaying();
        }));
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        Img.clear(holder.image);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
