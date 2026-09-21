package com.anibeat.app;

import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;

import com.anibeat.app.player.Player;
import com.anibeat.app.ui.screens.AnimeScreen;
import com.anibeat.app.ui.screens.ArtistScreen;
import com.anibeat.app.ui.screens.YearScreen;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/** Прогон приложения в тестовой среде: вкладки, экраны и нажатия не должны ронять интерфейс. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SmokeTest {

    private final List<String> failures = new ArrayList<>();
    private int clicks;

    @Test
    public void appOpensAndTabsWork() {
        Player.setAutoConnect(false);
        ActivityController<MainActivity> controller;
        try {
            controller = Robolectric.buildActivity(MainActivity.class).setup();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            failures.add("запуск приложения → " + describe(t));
            assertTrue(report(), failures.isEmpty());
            return;
        }
        final MainActivity activity = controller.get();

        for (int tab = 0; tab < 4; tab++) {
            final int index = tab;
            clickSafely("показать вкладку " + tab, () -> activity.showTab(index, false));
            layout(activity);
            walk("вкладка" + tab, activity.getWindow().getDecorView(), 0);
        }

        clickSafely("открыть аниме", () -> activity.pushScreen(new AnimeScreen(activity, activity, "naruto")));
        layout(activity);
        walk("аниме", activity.getWindow().getDecorView(), 0);
        clickSafely("назад", activity::pop);
        layout(activity);

        clickSafely("открыть исполнителя", () -> activity.pushScreen(new ArtistScreen(activity, activity, "yorushika")));
        layout(activity);
        walk("исполнитель", activity.getWindow().getDecorView(), 0);
        clickSafely("назад", activity::pop);
        layout(activity);

        clickSafely("открыть год", () -> activity.pushScreen(new YearScreen(activity, activity, 2024)));
        layout(activity);
        walk("год", activity.getWindow().getDecorView(), 0);
        clickSafely("назад", activity::pop);
        layout(activity);

        clickSafely("полноэкранный плеер", () -> activity.nowPlaying().open());
        layout(activity);
        clickSafely("плеер: play/pause", Player::toggle);
        clickSafely("плеер: следующий", () -> Player.next(false));
        clickSafely("плеер: предыдущий", Player::prev);
        clickSafely("плеер: перемотка", () -> Player.seekTo(1000));
        clickSafely("плеер: скорость", () -> Player.setSpeed(1.25f));
        clickSafely("плеер: повтор", () -> Player.setRepeat(Player.REPEAT_ALL));
        clickSafely("плеер: перемешивание", () -> Player.setShuffle(true));
        walk("плеер", activity.getWindow().getDecorView(), 0);
        clickSafely("закрыть плеер", () -> activity.nowPlaying().close());
        layout(activity);

        clickSafely("настройки", () -> com.anibeat.app.ui.Sheets.settings(activity));
        clickSafely("очередь", () -> com.anibeat.app.ui.Sheets.queue(activity));
        clickSafely("скачивания", () -> com.anibeat.app.ui.Sheets.downloads(activity));
        clickSafely("о приложении", () -> com.anibeat.app.ui.Sheets.about(activity));

        assertTrue(report(), failures.isEmpty());
    }

    private String report() {
        return "нажатий: " + clicks + ", падений: " + failures.size() + "\n" + String.join("\n", failures);
    }

    private void clickSafely(String where, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            failures.add(where + " → " + describe(t));
        }
    }

    private void walk(String where, View view, int depth) {
        if (view == null || depth > 12) return;
        if (view.getVisibility() == View.VISIBLE && view.isClickable()) {
            try {
                view.performClick();
                clicks++;
            } catch (Throwable t) {
                failures.add(where + ": клик → " + describe(t));
            }
            try {
                view.performLongClick();
            } catch (Throwable t) {
                failures.add(where + ": долгий тап → " + describe(t));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (depth > 9 && group.getChildCount() > 60) return;
            for (int i = 0; i < group.getChildCount(); i++) {
                walk(where, group.getChildAt(i), depth + 1);
            }
        }
    }

    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        StackTraceElement[] stack = t.getStackTrace();
        for (int i = 0; i < stack.length && i < 8; i++) {
            if (stack[i].getClassName().startsWith("com.anibeat")) sb.append("\n      at ").append(stack[i]);
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
