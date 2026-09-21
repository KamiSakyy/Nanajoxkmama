package com.anibeat.app.ui.screens;

import android.content.Context;

import com.anibeat.app.R;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Models;
import com.anibeat.app.ui.Block;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.Host;
import com.anibeat.app.ui.ListScreen;
import com.anibeat.app.ui.Sheets;

import java.util.ArrayList;
import java.util.List;

/** Экран плейлиста: треки, переименование и удаление. */
public class PlaylistScreen extends ListScreen {

    private final String playlistId;
    private Models.Playlist playlist;

    public PlaylistScreen(Context context, Host host, String playlistId) {
        super(context, host);
        this.playlistId = playlistId;
    }

    @Override
    protected void load(boolean refresh) {
        playlist = Library.playlist(playlistId);
        if (playlist == null) {
            fail("Плейлист не найден");
            return;
        }
        rebuild();
    }

    @Override
    public String title() {
        return playlist == null ? "" : playlist.name;
    }

    @Override
    protected void rebuild() {
        playlist = Library.playlist(playlistId);
        List<Block> blocks = new ArrayList<>();
        List<Models.Track> tracks = new ArrayList<>();
        if (playlist == null) {
            render(blocks, tracks);
            return;
        }
        tracks.addAll(playlist.tracks);
        blocks.add(Block.header(playlist.name));
        blocks.add(Block.text("", Format.plural(tracks.size(), "трек", "трека", "треков")));

        List<Block.Row> actions = new ArrayList<>();
        Block.Row playAll = new Block.Row("play", "Играть всё", null, R.drawable.ic_play_arrow);
        playAll.chevron = false;
        playAll.action = () -> {
            if (!tracks.isEmpty()) host.playTrack(tracks.get(0), tracks, 0);
        };
        actions.add(playAll);
        Block.Row shuffle = new Block.Row("shuffle", "Перемешать", null, R.drawable.ic_shuffle);
        shuffle.chevron = false;
        shuffle.action = () -> {
            List<Models.Track> mixed = Format.shuffled(tracks);
            if (!mixed.isEmpty()) host.playTrack(mixed.get(0), mixed, 0);
        };
        actions.add(shuffle);
        Block.Row rename = new Block.Row("rename", "Переименовать", null, R.drawable.ic_edit);
        rename.chevron = false;
        rename.action = () -> rename();
        actions.add(rename);
        Block.Row remove = new Block.Row("delete", "Удалить плейлист", null, R.drawable.ic_delete);
        remove.chevron = false;
        remove.color = com.anibeat.app.core.Theme.ERROR;
        remove.action = () -> Sheets.confirm(host.activity(), "Удалить плейлист?",
                "«" + playlist.name + "» будет удалён без возможности восстановления.", "Удалить", () -> {
                    Library.deletePlaylist(playlistId);
                    host.toast("Плейлист удалён");
                    host.popScreen();
                });
        actions.add(remove);
        blocks.add(Block.rows(actions));

        if (tracks.isEmpty()) {
            blocks.add(Block.empty("Плейлист пуст", "Удерживайте трек в любом списке → «В плейлист…»"));
        }
        String playingId = playingId();
        for (int i = 0; i < tracks.size(); i++) {
            final Models.Track track = tracks.get(i);
            Block block = Block.track(track, i, track.id != null && track.id.equals(playingId));
            block.onLongClick = () -> Sheets.confirm(host.activity(), "Убрать из плейлиста?",
                    track.title, "Убрать", () -> {
                        Library.removeFromPlaylist(playlistId, track.id);
                        host.toast("Трек убран из плейлиста");
                        rebuild();
                    });
            blocks.add(block);
        }
        render(blocks, tracks);
    }

    private void rename() {
        if (playlist == null) return;
        android.widget.EditText input = new android.widget.EditText(host.activity());
        input.setText(playlist.name);
        input.setTextColor(com.anibeat.app.core.Theme.ON);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(host.activity());
        int pad = com.anibeat.app.core.Theme.dp(host.activity(), 24);
        wrap.setPadding(pad, com.anibeat.app.core.Theme.dp(host.activity(), 8), pad, 0);
        wrap.addView(input);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(host.activity())
                .setTitle("Название плейлиста")
                .setView(wrap)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> Ui.safe(() -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    Library.renamePlaylist(playlistId, name);
                    rebuild();
                }))
                .show();
    }
}
