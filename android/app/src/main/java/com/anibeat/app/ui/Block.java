package com.anibeat.app.ui;

import com.anibeat.app.data.Models;

import java.util.ArrayList;
import java.util.List;

/** Кусок экрана: заголовок, строка, плитка, карусель или список треков. */
public final class Block {

    public static final int HEADER = 1;
    public static final int SECTION = 2;
    public static final int TRACK = 3;
    public static final int ROW = 4;
    public static final int ANIME_PAIR = 5;
    public static final int ANIME_ROW = 6;
    public static final int ARTIST_ROW = 7;
    public static final int MIX_ROW = 8;
    public static final int TRACK_ROW = 9;
    public static final int CHIPS = 10;
    public static final int TEXT = 11;
    public static final int SPACE = 12;
    public static final int EMPTY = 13;
    public static final int PLAYLIST_ROW = 14;

    public int kind;
    public String title = "";
    public String subtitle = "";
    public String action = "";
    public String text = "";
    public String id = "";
    public String cover;
    public String badge = "";
    public int heightDp;
    public int index;
    public boolean playing;
    public Models.Track track;
    public Models.AnimeRef anime;
    public Models.ArtistRef artist;
    public List<Models.Track> tracks = new ArrayList<>();
    public List<Models.AnimeSummary> animes = new ArrayList<>();
    public List<Models.ArtistSummary> artists = new ArrayList<>();
    public List<Models.Mix> mixes = new ArrayList<>();
    public List<Models.Playlist> playlists = new ArrayList<>();
    public List<Row> rows = new ArrayList<>();
    public List<String> chips = new ArrayList<>();
    public List<String> chipIds = new ArrayList<>();
    public String selectedChip;
    public Runnable onAction;
    public Runnable onLongClick;
    public ChipListener onChip;

    /** Нажатие на «чипс» (жанр, год, раздел). */
    public interface ChipListener {
        void onChip(String id, String label);
    }

    /** Простая строка меню или списка. */
    public static final class Row {
        public String id = "";
        public String title = "";
        public String subtitle = "";
        public int icon;
        public int color;
        public int badgeColor;
        public boolean chevron = true;
        public boolean extras;
        public boolean playing;
        public Runnable action;

        public Row(String id, String title, String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
        }

        public Row(String id, String title, String subtitle, int icon) {
            this(id, title, subtitle);
            this.icon = icon;
        }
    }

    private Block(int kind) {
        this.kind = kind;
    }

    public static Block header(String title) {
        Block b = new Block(HEADER);
        b.title = title;
        return b;
    }

    public static Block header(String title, String action) {
        Block b = header(title);
        b.action = action;
        return b;
    }

    public static Block section(String title) {
        Block b = new Block(SECTION);
        b.title = title;
        return b;
    }

    public static Block section(String title, String action) {
        Block b = section(title);
        b.action = action;
        return b;
    }

    public static Block track(Models.Track track, int index, boolean playing) {
        Block b = new Block(TRACK);
        b.track = track;
        b.index = index;
        b.playing = playing;
        return b;
    }

    public static Block row(Row row) {
        Block b = new Block(ROW);
        b.rows.add(row);
        return b;
    }

    public static Block rows(List<Row> rows) {
        Block b = new Block(ROW);
        b.rows.addAll(rows);
        return b;
    }

    public static Block playlistRow(Models.Playlist playlist) {
        Block b = new Block(PLAYLIST_ROW);
        b.playlists.add(playlist);
        return b;
    }

    public static Block animePair(List<Models.AnimeSummary> animes) {
        Block b = new Block(ANIME_PAIR);
        b.animes.addAll(animes);
        return b;
    }

    public static Block animeRow(String title, List<Models.AnimeSummary> animes) {
        Block b = new Block(ANIME_ROW);
        b.title = title;
        b.animes.addAll(animes);
        return b;
    }

    public static Block artistRow(String title, List<Models.ArtistSummary> artists) {
        Block b = new Block(ARTIST_ROW);
        b.title = title;
        b.artists.addAll(artists);
        return b;
    }

    public static Block mixRow(String title, List<Models.Mix> mixes) {
        Block b = new Block(MIX_ROW);
        b.title = title;
        b.mixes.addAll(mixes);
        return b;
    }

    public static Block trackRow(String title, List<Models.Track> tracks) {
        Block b = new Block(TRACK_ROW);
        b.title = title;
        b.tracks.addAll(tracks);
        return b;
    }

    public static Block chips(String title, List<String> ids, List<String> labels, String selected) {
        Block b = new Block(CHIPS);
        b.title = title;
        b.chipIds.addAll(ids);
        b.chips.addAll(labels);
        b.selectedChip = selected;
        return b;
    }

    public static Block text(String title, String value) {
        Block b = new Block(TEXT);
        b.title = title;
        b.text = value;
        return b;
    }

    public static Block space(int heightDp) {
        Block b = new Block(SPACE);
        b.heightDp = heightDp;
        return b;
    }

    public static Block empty(String title, String subtitle) {
        Block b = new Block(EMPTY);
        b.title = title;
        b.subtitle = subtitle;
        return b;
    }

    /** Копия блока с заменой одного трека (для списков избранного). */
    public static Block withTrack(Models.Track track, int index, boolean playing) {
        Block b = track(track, index, playing);
        return b;
    }
}
