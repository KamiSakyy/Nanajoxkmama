package com.anibeat.app.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.player.Player;

/** Всплывающие сообщения и плашка загрузок (Toasts на сайте). */
public class Toaster extends FrameLayout {

    private final MainActivity activity;
    private final LinearLayout stack;
    private final View downloadBar;
    private final TextView downloadLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public Toaster(MainActivity activity) {
        super(activity);
        this.activity = activity;
        Context c = activity;
        setClickable(false);
        stack = Ui.column(c);
        stack.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.BOTTOM;
        sp.bottomMargin = Theme.dp(c, 120);
        sp.leftMargin = Theme.dp(c, 16);
        sp.rightMargin = Theme.dp(c, 16);
        addView(stack, sp);

        downloadBar = Ui.row(c);
        downloadBar.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 14)));
        downloadBar.setPadding(Theme.dp(c, 16), Theme.dp(c, 10), Theme.dp(c, 16), Theme.dp(c, 10));
        downloadLabel = Ui.text(c, "Загрузка", 14f, Theme.ON);
        ((LinearLayout) downloadBar).addView(Ui.icon(c, "download", 18, Theme.ON));
        LinearLayout.LayoutParams lp = Ui.lpw(1f);
        lp.leftMargin = Theme.dp(c, 10);
        ((LinearLayout) downloadBar).addView(downloadLabel, lp);
        downloadBar.setVisibility(GONE);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        dp.gravity = Gravity.BOTTOM;
        dp.bottomMargin = Theme.dp(c, 120);
        dp.leftMargin = Theme.dp(c, 16);
        dp.rightMargin = Theme.dp(c, 16);
        addView(downloadBar, dp);

        Downloads.addListener(downloadsListener);
    }

    /** Слушатель загрузок и таймеры снимаются при закрытии окна. */
    private final Downloads.Listener downloadsListener = this::refreshDownloads;

    public void release() {
        Downloads.removeListener(downloadsListener);
        handler.removeCallbacksAndMessages(null);
    }

    public void show(String message) {
        post(() -> {
            Context c = getContext();
            TextView tv = Ui.text(c, message, 14f, Theme.ON, true);
            tv.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 14)));
            tv.setPadding(Theme.dp(c, 16), Theme.dp(c, 12), Theme.dp(c, 16), Theme.dp(c, 12));
            tv.setElevation(Theme.dpF(c, 8f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = Theme.dp(c, 8);
            tv.setAlpha(0f);
            tv.setTranslationY(Theme.dpF(c, 12f));
            stack.addView(tv, lp);
            tv.animate().alpha(1f).translationY(0f).setDuration(Theme.DUR).setInterpolator(Theme.EASE_OUT).start();
            handler.postDelayed(() -> {
                tv.animate().alpha(0f).translationY(Theme.dpF(c, 8f)).setDuration(Theme.DUR_FAST).withEndAction(() -> stack.removeView(tv)).start();
            }, 2600);
            while (stack.getChildCount() > 3) stack.removeViewAt(0);
        });
    }

    public void refreshDownloads() {
        post(() -> {
            int active = Downloads.activeCount();
            if (active == 0) {
                downloadBar.setVisibility(GONE);
                return;
            }
            downloadBar.setVisibility(VISIBLE);
            float progress = 0f;
            int known = 0;
            for (Downloads.Job job : Downloads.jobs()) {
                if (job.status == Downloads.Status.DOWNLOADING && job.total > 0) {
                    progress += job.received / (float) job.total;
                    known++;
                } else if (job.status == Downloads.Status.DOWNLOADING) {
                    known++;
                }
            }
            String suffix = known > 0 && progress > 0 ? " · " + Math.round(progress / known * 100) + "%" : "";
            downloadLabel.setText("Загрузка" + (active > 1 ? " · " + active : "") + suffix);
            downloadBar.setOnClickListener(v -> {
                activity.showTab(3, true);
                if (activity.tabIndex() == 3) activity.sheets().openQueueDownloads();
            });
        });
    }
}
