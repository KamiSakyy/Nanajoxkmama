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
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
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

        clickSafely("toaster", () -> activity.toaster().show("проверка"));

        assertTrue("нажатий: " + clicks + ", падений: " + failures.size() + "\n" + String.join("\n", failures), failures.isEmpty());
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
