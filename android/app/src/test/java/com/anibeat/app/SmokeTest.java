package com.anibeat.app;

import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

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

/** Прогон приложения: вкладки, возвраты на вкладки, экраны и нажатия не должны ронять интерфейс. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SmokeTest {

    private final List<String> failures = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private int clicks;

    @Test
    public void appOpensAndTabsWork() {
        Player.setAutoConnect(false);
        ActivityController<MainActivity> controller;
        try {
            controller = Robolectric.buildActivity(MainActivity.class).setup();
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            assertTrue("запуск: " + describe(t), false);
            return;
        }
        final MainActivity activity = controller.get();
        String[] names = {"Главная", "Поиск", "Обзор", "Медиатека"};

        // первый проход: открыть каждую вкладку
        for (int tab = 0; tab < 4; tab++) {
            final int index = tab;
            clickSafely("вкладка " + names[tab], () -> activity.showTab(index, false));
            layout(activity);
            report.add("вкладка " + names[tab] + ": элементов " + items(activity) + " ← первый заход");
        }

        // второй проход: возврат на вкладки (здесь раньше появлялись чёрные экраны)
        for (int tab = 0; tab < 4; tab++) {
            final int index = tab;
            clickSafely("возврат " + names[tab], () -> activity.showTab(index, true));
            layout(activity);
            int count = items(activity);
            String line = "вкладка " + names[tab] + ": элементов " + count + " ← возврат";
            report.add(line);
            if (count == 0) failures.add("чёрный экран при возврате: " + names[tab]);
        }

        // переход в другой раздел и обратно + открытие экранов поверх вкладки
        clickSafely("вкладка Медиатека", () -> activity.showTab(3, true));
        clickSafely("раздел скачанное", () -> activity.openLibraryTab("downloads"));
        layout(activity);
        report.add("скачанное: элементов " + items(activity));
        if (items(activity) == 0) failures.add("чёрный экран в разделе «Скачанное»");
        clickSafely("вкладка Главная", () -> activity.showTab(0, true));
        if (items(activity) == 0) failures.add("чёрный экран после возврата на Главную");

        clickSafely("открыть аниме", () -> activity.pushScreen(new AnimeScreen(activity, activity, "naruto")));
        layout(activity);
        walk("аниме", activity.getWindow().getDecorView(), 0);
        clickSafely("назад", activity::pop);
        layout(activity);
        if (items(activity) == 0) failures.add("чёрный экран после закрытия экрана аниме");

        clickSafely("открыть исполнителя", () -> activity.pushScreen(new ArtistScreen(activity, activity, "yorushika")));
        layout(activity);
        clickSafely("назад", activity::pop);
        layout(activity);

        clickSafely("открыть год", () -> activity.pushScreen(new YearScreen(activity, activity, 2024)));
        layout(activity);
        clickSafely("назад", activity::pop);
        layout(activity);
        if (items(activity) == 0) failures.add("чёрный экран после закрытия экрана года");

        // плеер и панели
        clickSafely("полноэкранный плеер", () -> activity.nowPlaying().open());
        layout(activity);
        clickSafely("плеер: play/pause", Player::toggle);
        clickSafely("плеер: следующий", () -> Player.next(false));
        clickSafely("плеер: предыдущий", Player::prev);
        clickSafely("плеер: перемотка", () -> Player.seekTo(1000));
        clickSafely("плеер: скорость", () -> Player.setSpeed(1.25f));
        clickSafely("плеер: повтор", () -> Player.setRepeat(Player.REPEAT_ALL));
        clickSafely("плеер: перемешивание", () -> Player.setShuffle(true));
        clickSafely("закрыть плеер", () -> activity.nowPlaying().close());
        layout(activity);

        clickSafely("настройки", () -> com.anibeat.app.ui.Sheets.settings(activity));
        clickSafely("очередь", () -> com.anibeat.app.ui.Sheets.queue(activity));
        clickSafely("скачивания", () -> com.anibeat.app.ui.Sheets.downloads(activity));
        clickSafely("о приложении", () -> com.anibeat.app.ui.Sheets.about(activity));

        for (int tab = 0; tab < 4; tab++) {
            final int index = tab;
            clickSafely("финальный проход " + names[tab], () -> activity.showTab(index, false));
            layout(activity);
            report.add("финал, вкладка " + names[tab] + ": элементов " + items(activity));
        }

        System.out.println("=== ОТЧЁТ ===");
        for (String line : report) System.out.println(line);
        System.out.println("нажатий: " + clicks + ", падений: " + failures.size());
        assertTrue(buildReport(), failures.isEmpty());
    }

    private int items(MainActivity activity) {
        View shown = shownScreen(activity);
        List<Integer> found = new ArrayList<>();
        collect(shown == null ? activity.getWindow().getDecorView() : shown, found, 0);
        int best = -1;
        for (int value : found) best = Math.max(best, value);
        return best < 0 ? 0 : best;
    }

    /** Реально видимый экран: он один и лежит в content. */
    private View shownScreen(MainActivity activity) {
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        View content = findById(root, android.R.id.content);
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.isShown() && child.getAlpha() > 0.99f) return child;
            }
        }
        return null;
    }

    private View findById(View view, int id) {
        if (view == null) return null;
        if (view.getId() == id) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findById(group.getChildAt(i), id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void collect(View view, List<Integer> out, int depth) {
        if (view == null || depth > 12) return;
        if (view instanceof RecyclerView && view.isShown()) {
            RecyclerView.Adapter<?> adapter = ((RecyclerView) view).getAdapter();
            if (adapter != null) out.add(adapter.getItemCount());
            else out.add(-1);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out, depth + 1);
        }
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder("падений: " + failures.size() + "\n");
        for (String line : report) sb.append(line).append('\n');
        for (String fail : failures) sb.append("! ").append(fail).append('\n');
        return sb.toString();
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
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            if (depth > 9 && group.getChildCount() > 60) return;
            for (int i = 0; i < group.getChildCount(); i++) walk(where, group.getChildAt(i), depth + 1);
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
