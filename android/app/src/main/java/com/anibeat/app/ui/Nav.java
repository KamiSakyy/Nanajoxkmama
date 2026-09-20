package com.anibeat.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;

/** Нижняя навигация — 4 вкладки, как BottomNav на сайте (49dp, hairline сверху). */
public class Nav extends LinearLayout {

    public static final int BAR_HEIGHT_DP = 49;

    private static final String[][] TABS = {
            {"Главная", "home_outline", "home"},
            {"Поиск", "search", "search"},
            {"Обзор", "explore_outline", "explore"},
            {"Медиатека", "library_music_outline", "library_music"},
    };

    private final MainActivity activity;
    private final LinearLayout row;

    public Nav(MainActivity activity) {
        super(activity);
        this.activity = activity;
        Context c = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.BG);
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layout.gravity = Gravity.BOTTOM;
        setLayoutParams(layout);

        View hairline = new View(c);
        hairline.setBackgroundColor(Theme.SEPARATOR);
        addView(hairline, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(c, 0.5f))));

        row = Ui.row(c);
        row.setGravity(Gravity.CENTER);
        addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Theme.dp(c, BAR_HEIGHT_DP)));

        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            LinearLayout item = Ui.column(c);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Theme.dp(c, 4), 0, 0);
            ImageView icon = Ui.icon(c, TABS[i][1], 25, Theme.ON_DIM);
            item.addView(icon);
            TextView label = Ui.text(c, TABS[i][0], 10f, Theme.ON_DIM, true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Theme.dp(c, 3);
            item.addView(label, lp);
            item.setTag(new Object[]{icon, label});
            item.setOnClickListener(v -> activity.showTab(index, true));
            Ui.tap(item);
            row.addView(item, Ui.lpw(1f));
        }
    }

    public void setActive(int index) {
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) row.getChildAt(i);
            Object[] tags = (Object[]) item.getTag();
            ImageView icon = (ImageView) tags[0];
            TextView label = (TextView) tags[1];
            boolean active = i == index;
            int res = getContext().getResources().getIdentifier("ic_" + (active ? TABS[i][2] : TABS[i][1]), "drawable", getContext().getPackageName());
            if (res != 0) icon.setImageResource(res);
            icon.setColorFilter(active ? Theme.PRIMARY : Theme.ON_DIM);
            label.setTextColor(active ? Theme.PRIMARY : Theme.ON_DIM);
            label.setTypeface(Typeface.create("sans-serif", active ? Typeface.BOLD : Typeface.NORMAL));
        }
    }
}
