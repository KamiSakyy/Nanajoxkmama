package com.anibeat.app;

import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Прогон приложения в тестовой среде: открывает все вкладки, шторки и плеер,
 * нажимает на каждый кликабельный элемент и длинный тап — падения ловятся сразу.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SmokeTest {

    private final List<String> failures = new ArrayList<>();
    private int clicks;

    @Test
    public void everythingIsClickableWithoutCrash() {
        // В тестовой среде нет службы воспроизведения — проверяем интерфейс без неё.
        com.anibeat.app.player.Player.setAutoConnect(false);
        ActivityController<MainActivity> controller;
        try {
            controller = Robolectric.buildActivity(MainActivity.class).setup();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            failures.add("запуск приложения → " + describe(t));
            assertTrue("нажатий: " + clicks + ", падений: " + failures.size() + "\n" + String.join("\n", failures), failures.isEmpty());
            return;
        }
        MainActivity activity = controller.get();

        for (int tab = 0; tab < 4; tab++) {
            final int index = tab;
            clickSafely("showTab(" + tab + ")", () -> activity.showTab(index, false));
            layout(activity);
            walk("tab" + tab, activity.getWindow().getDecorView(), 0);
        }

        clickSafely("nowPlaying.open()", () -> activity.nowPlaying().open());
        layout(activity);
        walk("nowPlaying", activity.getWindow().getDecorView(), 0);
        clickSafely("nowPlaying.close()", () -> activity.nowPlaying().close());
        layout(activity);

        clickSafely("sheets.openSettings()", () -> activity.sheets().openSettings());
        layout(activity);
        walk("settings", activity.getWindow().getDecorView(), 0);
        clickSafely("sheets.close()", () -> activity.sheets().close());
        layout(activity);

        clickSafely("sheets.openQueue()", () -> activity.sheets().openQueue());
        layout(activity);
        walk("queue", activity.getWindow().getDecorView(), 0);
        clickSafely("sheets.close()", () -> activity.sheets().close());
        layout(activity);

        clickSafely("sheets.openDownloadsSheet()", () -> activity.sheets().openDownloadsSheet());
        layout(activity);
        walk("downloads", activity.getWindow().getDecorView(), 0);
        clickSafely("sheets.close()", () -> activity.sheets().close());
        layout(activity);

        // Внутренние экраны: аниме, исполнитель, год, плейлист — открываются и сразу закрываются.
        openScreen("аниме", new com.anibeat.app.ui.screens.AnimeScreen(activity, "naruto"), activity);
        openScreen("исполнитель", new com.anibeat.app.ui.screens.ArtistScreen(activity, "yorushika"), activity);
        openScreen("год", new com.anibeat.app.ui.screens.YearScreen(activity, 2024), activity);
        openScreen("плейлист", new com.anibeat.app.ui.screens.PlaylistScreen(activity, "test"), activity);

        // Все разделы медиатеки.
        for (int tab = 0; tab < 4; tab++) {
            final int index = tab;
            clickSafely("library.openTab(" + tab + ")", () -> {
                activity.showTab(3, false);
                com.anibeat.app.ui.screens.LibraryScreen lib =
                        (com.anibeat.app.ui.screens.LibraryScreen) activity.screenAt(3);
                if (lib != null) lib.openTab(index);
            });
            layout(activity);
            walk("library" + tab, activity.getWindow().getDecorView(), 0);
        }

        // Меню трека: длинный тап, плейлисты, скачивание, очередь.
        com.anibeat.app.data.Models.Track probe = probeTrack();
        clickSafely("меню трека", () -> activity.sheets().openTrackMenu(probe));
        layout(activity);
        walk("меню трека", activity.getWindow().getDecorView(), 0);
        clickSafely("закрыть меню", () -> activity.sheets().close());

        clickSafely("выбор плейлиста", () -> activity.sheets().openPlaylistPicker(probe));
        layout(activity);
        walk("выбор плейлиста", activity.getWindow().getDecorView(), 0);
        clickSafely("закрыть выбор", () -> activity.sheets().close());

        clickSafely("создание плейлиста", () -> activity.sheets().openCreatePlaylist(() -> { }));
        layout(activity);
        walk("создание плейлиста", activity.getWindow().getDecorView(), 0);
        clickSafely("закрыть создание", () -> activity.sheets().close());

        clickSafely("очередь + скачанные", () -> activity.sheets().openQueueDownloads());
        layout(activity);
        walk("очередь + скачанные", activity.getWindow().getDecorView(), 0);
        clickSafely("закрыть", () -> activity.sheets().close());

        // Плеер: открыть, нажать управление, закрыть.
        clickSafely("плеер: открыть", () -> activity.nowPlaying().open());
        layout(activity);
        clickSafely("плеер: play/pause", () -> com.anibeat.app.player.Player.toggle());
        clickSafely("плеер: следующий", () -> com.anibeat.app.player.Player.next(false));
        clickSafely("плеер: предыдущий", () -> com.anibeat.app.player.Player.prev());
        clickSafely("плеер: перемотка", () -> com.anibeat.app.player.Player.seekTo(1000));
        walk("плеер: управление", activity.getWindow().getDecorView(), 0);
        clickSafely("плеер: закрыть", () -> activity.nowPlaying().close());

        clickSafely("уведомление", () -> activity.toaster().show("проверка"));

        assertTrue("нажатий: " + clicks + ", падений: " + failures.size() + "\n" + String.join("\n", failures), failures.isEmpty());
    }

    private void openScreen(String where, MainActivity.Screen screen, MainActivity activity) {
        clickSafely("открыть " + where, () -> activity.push(screen, false));
        layout(activity);
        walk(where, activity.getWindow().getDecorView(), 0);
        clickSafely("закрыть " + where, activity::pop);
        layout(activity);
    }

    /** Трек, собранный вручную — проверяем экраны и меню без сети. */
    private static com.anibeat.app.data.Models.Track probeTrack() {
        com.anibeat.app.data.Models.Track t = new com.anibeat.app.data.Models.Track();
        t.id = "probe";
        t.themeSlug = "op1";
        t.title = "Проверка";
        t.audioUrl = "https://example.com/a.mp3";
        t.cover = "https://example.com/c.jpg";
        t.coverSmall = t.cover;
        t.anime = new com.anibeat.app.data.Models.AnimeRef();
        t.anime.slug = "naruto";
        t.anime.name = "Naruto";
        return t;
    }

    private void clickSafely(String where, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            failures.add(where + " → " + describe(t));
        }
    }

    private void walk(String where, View view, int depth) {
        if (view == null || depth > 14) return;
        if (view.getVisibility() == View.VISIBLE && view.isClickable()) {
            try {
                view.performClick();
                clicks++;
            } catch (Throwable t) {
                failures.add(where + ": клик по " + name(view) + " → " + describe(t));
            }
            try {
                view.performLongClick();
            } catch (Throwable t) {
                failures.add(where + ": долгий тап по " + name(view) + " → " + describe(t));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (depth > 10 && group.getChildCount() > 40) return;
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(where, group.getChildAt(i), depth + 1);
            }
        }
    }

    private static String name(View v) {
        String s = v.getClass().getSimpleName();
        if (v.getContentDescription() != null) s += '"' + String.valueOf(v.getContentDescription()) + '"';
        else if (v instanceof android.widget.TextView) s += "\"" + String.valueOf(((android.widget.TextView) v).getText()) + "\"";
        return s;
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < st.length && i < 8; i++) {
            if (st[i].getClassName().startsWith("com.anibeat")) sb.append("\n      at ").append(st[i]);
        }
        return sb.toString();
    }

    private static void layout(MainActivity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1080, 1920);
    }
}
