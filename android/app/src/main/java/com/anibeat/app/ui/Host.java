package com.anibeat.app.ui;

import android.app.Activity;
import android.view.View;

import com.anibeat.app.data.Models;

import java.util.List;

/** Что экраны умеют просить у главного экрана. */
public interface Host {

    Activity activity();

    void playTrack(Models.Track track, List<Models.Track> list, int index);

    void enqueue(Models.Track track, boolean next);

    void trackMenu(Models.Track track, View anchor);

    void openAnime(Models.AnimeRef anime);

    void openArtist(Models.ArtistRef artist);

    void openMix(Models.Mix mix);

    void openPlaylist(Models.Playlist playlist);

    void openYear(int year);

    void openGenre(String genreId, String title);

    void openNowPlaying();

    /** Открыть произвольный экран поверх текущего. */
    /** Открыть медиатеку на нужном разделе: favorites, playlists, downloads, history. */
    void openLibraryTab(String tab);

    /** Перейти на вкладку по номеру: 0 — Главная, 1 — Поиск, 2 — Обзор, 3 — Медиатека. */
    void openTab(int index);

    void pushScreen(Screen screen);

    /** Закрыть текущий экран. */
    void popScreen();

    void toast(String message);
}
