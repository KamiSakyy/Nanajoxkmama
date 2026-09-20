package com.anibeat.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;

/** Панель навигации экрана (TopBar из Nav.tsx): компактный бар 46dp + крупный заголовок. */
public class TopBar extends LinearLayout {

    private final MainActivity activity;
    private final TextView barTitle;
    private final LinearLayout actionsBox;
    private final TextView largeTitle;
    private final View bar;

    public TopBar(MainActivity activity, String title, boolean back, boolean large, boolean transparent) {
        super(activity);
        this.activity = activity;
        Context c = activity;
        setOrientation(VERTICAL);
        setBackgroundColor(transparent ? 0x00000000 : Theme.BG);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar = Ui.row(c);
        bar.setPadding(Theme.dp(c, 6), 0, Theme.dp(c, 6), 0);
        if (!transparent) ((LinearLayout) bar).setBackgroundColor(Theme.BG);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 46)));

        if (back) {
            LinearLayout backBtn = Ui.row(c);
            backBtn.setPadding(Theme.dp(c, 6), 0, Theme.dp(c, 10), 0);
            backBtn.setBackground(Ui.rounded(transparent ? 0x73000000 : 0x00000000, Theme.dpF(c, 20f)));
            ImageView arrow = Ui.icon(c, "arrow_back", 22, Theme.PRIMARY);
            backBtn.addView(arrow, Ui.lp(Theme.dp(c, 22), Theme.dp(c, 22)));
            TextView label = Ui.text(c, "Назад", 17f, Theme.PRIMARY);
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Theme.dp(c, 2);
            backBtn.addView(label, lp);
            backBtn.setOnClickListener(v -> activity.pop());
            Ui.tap(backBtn);
            ((LinearLayout) bar).addView(backBtn, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Theme.dp(c, 40)));
        }

        barTitle = Ui.text(c, title == null ? "" : title, 17f, Theme.ON, true);
        barTitle.setGravity(Gravity.CENTER);
        barTitle.setSingleLine(true);
        barTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = Ui.lpw(1f);
        if (!back) {
            tp.leftMargin = Theme.dp(c, 12);
            barTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        }
        ((LinearLayout) bar).addView(barTitle, tp);
        barTitle.setAlpha(large ? 0f : 1f);

        actionsBox = Ui.row(c);
        actionsBox.setGravity(Gravity.CENTER_VERTICAL);
        ((LinearLayout) bar).addView(actionsBox, Ui.lp(Theme.dp(c, 44), ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(bar);

        if (large) {
            largeTitle = Ui.heading(c, title == null ? "" : title, 34f, Theme.ON);
            largeTitle.setPadding(Theme.dp(c, 16), Theme.dp(c, 4), Theme.dp(c, 16), Theme.dp(c, 6));
            addView(largeTitle);
        } else {
            largeTitle = null;
        }
    }

    public void addAction(View view) {
        actionsBox.addView(view);
    }

    public LinearLayout actions() {
        return actionsBox;
    }

    /** Заголовок в баре проявляется при прокрутке (как sticky large title в iOS). */
    public void bindScroll(ScrollView scroll) {
        if (largeTitle == null) return;
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            float alpha = Math.max(0f, Math.min(1f, (y - Theme.dp(getContext(), 20)) / (float) Theme.dp(getContext(), 26)));
            barTitle.setAlpha(alpha);
            largeTitle.setAlpha(1f - alpha * 0.75f);
        });
    }

    public View barView() {
        return bar;
    }

    public TextView titleView() {
        return largeTitle != null ? largeTitle : barTitle;
    }
}
