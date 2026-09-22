package com.anibeat.app.ui;

import android.content.Context;
import android.content.res.ColorStateList;
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
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Models;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** Вертикальный список блоков: заголовки, строки треков, карусели, чипсы. */
public class BlockAdapter extends RecyclerView.Adapter<BlockAdapter.VH> {

    /** Частичное обновление: меняется только отметка играющего трека. */
    static final Object PAYLOAD_PLAYING = new Object();

    private final Host host;
    private final List<Block> blocks = new ArrayList<>();
    private List<Models.Track> tracks = new ArrayList<>();
    private String playingId = "";

    public BlockAdapter(Host host) {
        this.host = host;
    }

    /**
     * Показать новый набор блоков. Перерисовываются только те элементы, у которых
     * изменилось содержимое, — обложки больше не перезагружаются на каждый чих.
     */
    public void submit(List<Block> data, List<Models.Track> trackList) {
        final List<Block> previous = new ArrayList<>(blocks);
        final String[] oldSignatures = new String[previous.size()];
        for (int i = 0; i < previous.size(); i++) oldSignatures[i] = previous.get(i).signature();

        blocks.clear();
        if (data != null) blocks.addAll(data);
        tracks = trackList == null ? new ArrayList<>() : trackList;

        final String[] newSignatures = new String[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) newSignatures[i] = blocks.get(i).signature();

        boolean same = previous.size() == blocks.size();
        if (same) {
            for (int i = 0; i < oldSignatures.length; i++) {
                if (!oldSignatures[i].equals(newSignatures[i])) {
                    same = false;
                    break;
                }
            }
        }
        if (same) return;

        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return blocks.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                Block a = previous.get(oldPos);
                Block b = blocks.get(newPos);
                return a.kind == b.kind && a.key().equals(b.key());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldSignatures[oldPos].equals(newSignatures[newPos]);
            }

            @Override
            public Object getChangePayload(int oldPos, int newPos) {
                Block a = previous.get(oldPos);
                Block b = blocks.get(newPos);
                if (a.kind == Block.TRACK && a.track != null && b.track != null
                        && a.track.id.equals(b.track.id) && a.playing != b.playing) {
                    return PAYLOAD_PLAYING;
                }
                return null;
            }
        }, true).dispatchUpdatesTo(this);
        blockKnown.clear();
        for (Block block : blocks) blockKnown.add(block.signature());
    }

    /** Обновить только отметку играющего трека. */
    public void setPlayingId(String id) {
        String value = id == null ? "" : id;
        if (value.equals(playingId)) return;
        playingId = value;
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block.kind == Block.TRACK && block.track != null) {
                boolean playing = block.track.id != null && block.track.id.equals(value);
                if (playing != block.playing) {
                    block.playing = playing;
                    block.invalidate();
                    notifyItemChanged(i, PAYLOAD_PLAYING);
                }
            }
        }
    }

    /** Есть ли на экране настоящий контент (а не только заголовок и сообщение). */
    public boolean hasContent() {
        for (Block block : blocks) {
            if (block.kind == Block.TRACK || block.kind == Block.TRACK_ROW || block.kind == Block.ANIME_ROW
                    || block.kind == Block.ANIME_PAIR || block.kind == Block.ARTIST_ROW
                    || block.kind == Block.MIX_ROW || block.kind == Block.PLAYLIST_ROW) {
                return true;
            }
        }
        return false;
    }

    /** Обновить данные (русские названия Shikimori подтянулись) — точечно, без перезагрузки обложек. */
    public void refreshChanged() {
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block.kind == Block.TRACK || block.kind == Block.TRACK_ROW || block.kind == Block.ANIME_ROW
                    || block.kind == Block.ANIME_PAIR || block.kind == Block.ARTIST_ROW || block.kind == Block.TEXT) {
                if (i < blockKnown.size()) blockKnown.set(i, "");
                notifyItemChanged(i);
            }
        }
    }

    private final List<String> blockKnown = new ArrayList<>();

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        TextView subtitle;
        TextView text;
        TextView action;
        ImageView image;
        LinearLayout rows;
        RecyclerView rowView;
        RowAdapter rowAdapter;
        ChipGroup chips;
        FrameLayout pair;
        TrackRows.Holder trackRow;
        Block block;
        String boundSignature = "";

        VH(View view) {
            super(view);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return blocks.get(position).kind;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        switch (viewType) {
            case Block.HEADER: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.HORIZONTAL);
                wrap.setGravity(Gravity.CENTER_VERTICAL);
                wrap.setPadding(Theme.dp(context, 16), Theme.dp(context, 18), Theme.dp(context, 16), Theme.dp(context, 6));
                TextView title = label(context, 22f, Theme.ON, true);
                wrap.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView action = label(context, 13f, Theme.ACCENT, false);
                wrap.addView(action);
                VH holder = new VH(wrap);
                holder.title = title;
                holder.action = action;
                return holder;
            }
            case Block.SECTION: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.HORIZONTAL);
                wrap.setGravity(Gravity.CENTER_VERTICAL);
                wrap.setPadding(Theme.dp(context, 16), Theme.dp(context, 16), Theme.dp(context, 16), Theme.dp(context, 2));
                TextView title = label(context, 16f, Theme.ON, true);
                wrap.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView action = label(context, 12.5f, Theme.ON_VARIANT, false);
                wrap.addView(action);
                VH holder = new VH(wrap);
                holder.title = title;
                holder.action = action;
                return holder;
            }
            case Block.TEXT: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setPadding(Theme.dp(context, 16), Theme.dp(context, 8), Theme.dp(context, 16), Theme.dp(context, 8));
                TextView title = label(context, 13f, Theme.ON_VARIANT, true);
                wrap.addView(title);
                TextView text = label(context, 13.5f, Theme.ON, false);
                text.setLineSpacing(Theme.dp(context, 3), 1f);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textParams.topMargin = Theme.dp(context, 5);
                wrap.addView(text, textParams);
                VH holder = new VH(wrap);
                holder.title = title;
                holder.text = text;
                return holder;
            }
            case Block.TRACK: {
                FrameLayout frame = new FrameLayout(context);
                frame.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                VH holder = new VH(frame);
                final TrackRows.Holder row = TrackRows.holder(context, null, null);
                holder.trackRow = row;
                row.onClick(() -> {
                    Block block = holder.block;
                    if (block == null || block.track == null) return;
                    Models.Track now = com.anibeat.app.player.Player.current();
                    // Тап по играющему треку ставит паузу, а не запускает его заново.
                    if (now != null && block.track.id != null && block.track.id.equals(now.id)) {
                        com.anibeat.app.player.Player.toggle();
                        return;
                    }
                    host.playTrack(block.track, tracks, Math.max(0, block.index));
                });
                row.onMenu(() -> {
                    Block block = holder.block;
                    if (block == null || block.track == null) return;
                    if (block.onLongClick != null) block.onLongClick.run();
                    else host.trackMenu(block.track, row.view);
                });
                frame.addView(row.view);
                return holder;
            }
            case Block.ROW: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                VH holder = new VH(wrap);
                holder.rows = wrap;
                return holder;
            }
            case Block.PLAYLIST_ROW: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                VH holder = new VH(wrap);
                holder.rows = wrap;
                return holder;
            }
            case Block.ANIME_PAIR: {
                LinearLayout pair = new LinearLayout(context);
                pair.setOrientation(LinearLayout.HORIZONTAL);
                pair.setPadding(Theme.dp(context, 12), Theme.dp(context, 8), Theme.dp(context, 12), Theme.dp(context, 4));
                pair.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                VH holder = new VH(pair);
                holder.pair = new FrameLayout(context);
                holder.rows = pair;
                return holder;
            }
            case Block.ANIME_ROW:
            case Block.ARTIST_ROW:
            case Block.MIX_ROW:
            case Block.TRACK_ROW: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setPadding(0, Theme.dp(context, 12), 0, Theme.dp(context, 4));
                TextView title = label(context, 16f, Theme.ON, true);
                title.setPadding(Theme.dp(context, 16), 0, Theme.dp(context, 16), Theme.dp(context, 10));
                wrap.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                RecyclerView list = new RecyclerView(context);
                list.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
                list.setClipToPadding(false);
                list.setHasFixedSize(true);
                list.setItemViewCacheSize(6);
                list.setItemAnimator(null);
                list.setPadding(Theme.dp(context, 16), 0, Theme.dp(context, 6), 0);
                int height = viewType == Block.ARTIST_ROW ? Theme.dp(context, 128)
                        : viewType == Block.MIX_ROW ? Theme.dp(context, 114)
                        : viewType == Block.ANIME_ROW ? Theme.dp(context, 200)
                        : Theme.dp(context, 208);
                wrap.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
                int kind = viewType == Block.ARTIST_ROW ? RowAdapter.ARTIST
                        : viewType == Block.MIX_ROW ? RowAdapter.MIX
                        : viewType == Block.ANIME_ROW ? RowAdapter.ANIME
                        : RowAdapter.TRACK;
                VH holder = new VH(wrap);
                holder.title = title;
                holder.rowView = list;
                holder.rowAdapter = new RowAdapter(kind, host);
                list.setAdapter(holder.rowAdapter);
                return holder;
            }
            case Block.CHIPS: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setPadding(0, Theme.dp(context, 10), 0, Theme.dp(context, 6));
                TextView title = label(context, 13f, Theme.ON_VARIANT, true);
                title.setPadding(Theme.dp(context, 16), 0, Theme.dp(context, 16), Theme.dp(context, 6));
                wrap.addView(title);
                android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
                scroll.setHorizontalScrollBarEnabled(false);
                ChipGroup group = new ChipGroup(context);
                group.setSingleLine(true);
                group.setSingleSelection(true);
                group.setSelectionRequired(false);
                group.setPadding(Theme.dp(context, 12), 0, Theme.dp(context, 12), 0);
                scroll.addView(group, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                wrap.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                VH holder = new VH(wrap);
                holder.title = title;
                holder.chips = group;
                return holder;
            }
            case Block.SPACE: {
                View space = new View(context);
                VH holder = new VH(space);
                return holder;
            }
            default: {
                LinearLayout wrap = new LinearLayout(context);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.setGravity(Gravity.CENTER);
                wrap.setPadding(Theme.dp(context, 24), Theme.dp(context, 48), Theme.dp(context, 24), Theme.dp(context, 48));
                TextView title = label(context, 16f, Theme.ON, true);
                title.setGravity(Gravity.CENTER);
                wrap.addView(title);
                TextView subtitle = label(context, 13f, Theme.ON_VARIANT, false);
                subtitle.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                subParams.topMargin = Theme.dp(context, 6);
                wrap.addView(subtitle, subParams);
                VH holder = new VH(wrap);
                holder.title = title;
                holder.subtitle = subtitle;
                return holder;
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            holder.boundSignature = "";
            onBindViewHolder(holder, position);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Block block = blocks.get(position);
        Context context = holder.itemView.getContext();
        String signature = block.signature();
        if (signature.equals(holder.boundSignature)) {
            return;
        }
        holder.boundSignature = signature;
        switch (block.kind) {
            case Block.HEADER:
            case Block.SECTION:
                holder.title.setText(block.title);
                holder.action.setText(block.action);
                holder.action.setVisibility(block.action == null || block.action.isEmpty() ? View.GONE : View.VISIBLE);
                holder.action.setOnClickListener(block.onAction == null ? null : v -> block.onAction.run());
                break;
            case Block.TEXT:
                holder.title.setText(block.title);
                holder.text.setText(block.text);
                holder.text.setVisibility(block.text == null || block.text.isEmpty() ? View.GONE : View.VISIBLE);
                break;
            case Block.TRACK: {
                FrameLayout frame = (FrameLayout) holder.itemView;
                final TrackRows.Holder row = holder.trackRow;
                holder.block = block;
                row.bind(block.track, block.index + 1, block.playing);
                break;
            }
            case Block.ROW: {
                holder.rows.removeAllViews();
                for (Block.Row row : block.rows) {
                    holder.rows.addView(rowView(context, row));
                }
                break;
            }
            case Block.PLAYLIST_ROW: {
                holder.rows.removeAllViews();
                for (Models.Playlist playlist : block.playlists) {
                    holder.rows.addView(playlistRow(context, playlist));
                }
                break;
            }
            case Block.ANIME_PAIR: {
                LinearLayout pair = holder.rows;
                pair.removeAllViews();
                int half = (int) ((context.getResources().getDisplayMetrics().widthPixels - Theme.dp(context, 24)) / 2f) - Theme.dp(context, 5);
                for (Models.AnimeSummary anime : block.animes) {
                    View card = animeCard(context, anime, half);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    params.leftMargin = Theme.dp(context, 5);
                    params.rightMargin = Theme.dp(context, 5);
                    pair.addView(card, params);
                }
                break;
            }
            case Block.ANIME_ROW:
            case Block.ARTIST_ROW:
            case Block.MIX_ROW:
            case Block.TRACK_ROW: {
                holder.title.setText(block.title);
                holder.title.setVisibility(block.title == null || block.title.isEmpty() ? View.GONE : View.VISIBLE);
                if (holder.rowAdapter.kind() == RowAdapter.TRACK) {
                    holder.rowAdapter.setPlayingId(playingId);
                    holder.rowAdapter.submit(block.tracks);
                } else if (holder.rowAdapter.kind() == RowAdapter.ANIME) {
                    holder.rowAdapter.submit(block.animes);
                } else if (holder.rowAdapter.kind() == RowAdapter.ARTIST) {
                    holder.rowAdapter.submit(block.artists);
                } else {
                    holder.rowAdapter.submit(block.mixes);
                    MixCovers.ensure(context, block.mixes, holder.rowAdapter::notifyDataSetChanged);
                }
                break;
            }
            case Block.CHIPS: {
                holder.title.setText(block.title);
                holder.title.setVisibility(block.title == null || block.title.isEmpty() ? View.GONE : View.VISIBLE);
                holder.chips.removeAllViews();
                for (int i = 0; i < block.chips.size(); i++) {
                    final String id = i < block.chipIds.size() ? block.chipIds.get(i) : block.chips.get(i);
                    final String labelText = block.chips.get(i);
                    boolean selected = block.selectedChip != null && block.selectedChip.equals(id);
                    Chip chip = new Chip(context);
                    chip.setText(labelText);
                    chip.setCheckable(true);
                    chip.setChecked(selected);
                    int fill = selected ? Theme.ACCENT_CONTAINER : Theme.SURFACE_3;
                    chip.setChipBackgroundColor(ColorStateList.valueOf(fill));
                    chip.setChipStrokeColor(ColorStateList.valueOf(fill));
                    chip.setTextColor(selected ? Theme.ACCENT : Theme.ON_VARIANT);
                    chip.setTextSize(12.5f);
                    chip.setEnsureMinTouchTargetSize(false);
                    chip.setOnClickListener(v -> {
                        if (block.onChip != null) block.onChip.onChip(id, labelText);
                    });
                    holder.chips.addView(chip);
                }
                break;
            }
            default:
                holder.title.setText(block.title);
                holder.subtitle.setText(block.subtitle);
                break;
        }
    }

    private View rowView(Context context, final Block.Row row) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Theme.dp(context, 16), Theme.dp(context, 13), Theme.dp(context, 16), Theme.dp(context, 13));
        if (row.icon != 0) {
            ImageView icon = new ImageView(context);
            icon.setImageResource(row.icon);
            icon.setColorFilter(row.color != 0 ? row.color : Theme.ON_VARIANT);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Theme.dp(context, 22), Theme.dp(context, 22));
            iconParams.rightMargin = Theme.dp(context, 14);
            line.addView(icon, iconParams);
        }
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        line.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = label(context, 15f, Theme.ON, false);
        title.setText(row.title);
        column.addView(title);
        if (row.subtitle != null && !row.subtitle.isEmpty()) {
            TextView subtitle = label(context, 12.5f, Theme.ON_VARIANT, false);
            subtitle.setText(row.subtitle);
            column.addView(subtitle);
        }
        if (row.chevron) {
            ImageView chevron = new ImageView(context);
            chevron.setImageResource(com.anibeat.app.R.drawable.ic_chevron_right);
            chevron.setColorFilter(Theme.ON_DIM);
            line.addView(chevron, new LinearLayout.LayoutParams(Theme.dp(context, 20), Theme.dp(context, 20)));
        }
        if (row.action != null) {
            line.setOnClickListener(v -> row.action.run());
            Ui.press(line);
        }
        return line;
    }

    private View playlistRow(Context context, final Models.Playlist playlist) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Theme.dp(context, 16), Theme.dp(context, 10), Theme.dp(context, 16), Theme.dp(context, 10));
        ImageView cover = new ImageView(context);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(Theme.dp(context, 46), Theme.dp(context, 46));
        coverParams.rightMargin = Theme.dp(context, 12);
        line.addView(cover, coverParams);
        Models.Track first = playlist.tracks.isEmpty() ? null : playlist.tracks.get(0);
        Display display = first == null ? null : Display.track(first);
        Img.loadRounded(cover, display == null ? null : display.cover, Img.size(context, 46), 9f);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        line.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(label(context, 15f, Theme.ON, false));
        ((TextView) column.getChildAt(0)).setText(playlist.name);
        TextView count = label(context, 12.5f, Theme.ON_VARIANT, false);
        count.setText(Format.plural(playlist.tracks.size(), "трек", "трека", "треков"));
        column.addView(count);
        ImageView chevron = new ImageView(context);
        chevron.setImageResource(com.anibeat.app.R.drawable.ic_chevron_right);
        chevron.setColorFilter(Theme.ON_DIM);
        line.addView(chevron, new LinearLayout.LayoutParams(Theme.dp(context, 20), Theme.dp(context, 20)));
        line.setOnClickListener(v -> host.openPlaylist(playlist));
        Ui.press(line);
        return line;
    }

    private View animeCard(Context context, final Models.AnimeSummary anime, int width) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        MaterialCardView card = new MaterialCardView(context);
        card.setCardBackgroundColor(Theme.SURFACE_2);
        card.setRadius(Theme.dpF(context, 14));
        card.setCardElevation(0f);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(width * 1.42f)));
        column.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Display display = Display.summary(anime);
        Img.loadRounded(image, display.thumb != null ? display.thumb : display.cover, Img.size(context, width), 14f);
        TextView title = label(context, 13f, Theme.ON, false);
        title.setText(display.title);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Theme.dp(context, 7);
        column.addView(title, params);
        TextView subtitle = label(context, 11.5f, Theme.ON_VARIANT, false);
        String kind = com.anibeat.app.data.Meta.KIND_RU.get(anime.mediaFormat == null ? "" : anime.mediaFormat);
        String year = anime.year == null ? "" : String.valueOf(anime.year);
        String extra = kind != null ? kind : (anime.mediaFormat == null ? "" : anime.mediaFormat);
        subtitle.setText(TextUtils.isEmpty(year) ? extra : (extra.isEmpty() ? year : extra + " · " + year));
        column.addView(subtitle);
        column.setOnClickListener(v -> host.openAnime(anime));
        Ui.press(column);
        return column;
    }

    private static TextView label(Context context, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        holder.boundSignature = "";
        holder.block = null;
        if (holder.image != null) Img.clear(holder.image);
        if (holder.trackRow != null) holder.trackRow.clearCover();
        if (holder.rowAdapter != null) {
            holder.rowAdapter.setPlayingId("");
        }
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }
}
