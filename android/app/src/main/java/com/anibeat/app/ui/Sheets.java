package com.anibeat.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.Net;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;

import java.util.List;

/**
 * Нижние шторки (iOS sheets): очередь, меню трека, выбор плейлиста, настройки,
 * создание/переименование плейлиста и список загрузок — порт components/Sheets.tsx.
 */
public class Sheets extends FrameLayout {

    private final MainActivity activity;
    private final View scrim;
    private final FrameLayout container;
    private final LinearLayout sheet;
    private final LinearLayout sheetBody;
    private final TextView sheetTitle;
    private final View scroller;

    private boolean open;
    private float touchStartY;
    private boolean dragging;
    private Runnable onDismiss;

    private Models.Track menuTrack;
    private Models.Track pickerTrack;
    private Models.Playlist menuPlaylist;

    public Sheets(MainActivity activity) {
        super(activity);
        this.activity = activity;
        Context c = activity;

        scrim = new View(c);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setVisibility(GONE);
        scrim.setOnClickListener(v -> close());
        addView(scrim, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        container = new FrameLayout(c);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        cp.gravity = Gravity.BOTTOM;
        addView(container, cp);

        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 16f)));
        sheet.setElevation(Theme.dpF(c, 24f));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sp.gravity = Gravity.BOTTOM;
        sheet.setLayoutParams(sp);
        container.addView(sheet);

        View grabberBox = new FrameLayout(c);
        View grabber = new View(c);
        grabber.setBackground(Ui.rounded(0x40FFFFFF, Theme.dpF(c, 3f)));
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(Theme.dp(c, 36), Theme.dp(c, 5));
        gp.gravity = Gravity.CENTER;
        ((FrameLayout) grabberBox).addView(grabber, gp);
        grabberBox.setPadding(0, Theme.dp(c, 10), 0, Theme.dp(c, 4));
        sheet.addView(grabberBox);

        sheetTitle = Ui.text(c, "", 17f, Theme.ON, true);
        sheetTitle.setPadding(Theme.dp(c, 20), Theme.dp(c, 4), Theme.dp(c, 20), Theme.dp(c, 8));
        sheetTitle.setVisibility(GONE);
        sheet.addView(sheetTitle);

        sheetBody = Ui.column(c);
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(sheetBody, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        sheet.addView(scroll, bodyParams);
        scroller = scroll;

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!open) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = e.getRawY();
                dragging = ((ScrollView) scroller).getScrollY() <= 0;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging && e.getRawY() - touchStartY > Theme.dp(getContext(), 12)) return true;
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float dy = e.getRawY() - touchStartY;
                    if (dy > 0) sheet.setTranslationY(dy);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging && sheet.getTranslationY() > Theme.dp(getContext(), 90)) close();
                else sheet.animate().translationY(0f).setDuration(Theme.DUR_FAST).start();
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Каркас                                                              */
    /* ------------------------------------------------------------------ */

    public boolean isOpen() {
        return open;
    }

    public void show(String title, View content, Runnable onDismiss) {
        sheetBody.removeAllViews();
        if (title != null && !title.isEmpty()) {
            sheetTitle.setText(title);
            sheetTitle.setVisibility(VISIBLE);
        } else {
            sheetTitle.setVisibility(GONE);
        }
        if (content != null) sheetBody.addView(content, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        this.onDismiss = onDismiss;
        open = true;
        setVisibility(VISIBLE);
        scrim.setVisibility(VISIBLE);
        sheet.setTranslationY(getHeight() > 0 ? getHeight() : Theme.dp(getContext(), 600));
        sheet.post(() -> {
            float from = sheet.getHeight();
            sheet.setTranslationY(from);
            scrim.animate().alpha(1f).setDuration(Theme.DUR).start();
            sheet.animate().translationY(0f).setDuration(Theme.DUR_SHEET).setInterpolator(Theme.EASE_SHEET).start();
        });
        bringToFront();
    }

    public void close() {
        if (!open) return;
        open = false;
        Runnable dismiss = onDismiss;
        onDismiss = null;
        scrim.animate().alpha(0f).setDuration(Theme.DUR_FAST).start();
        sheet.animate().translationY(sheet.getHeight()).setDuration(Theme.DUR_SHEET).setInterpolator(Theme.EASE_SHEET)
                .withEndAction(() -> {
                    setVisibility(GONE);
                    scrim.setVisibility(GONE);
                    if (dismiss != null) dismiss.run();
                }).start();
    }

    /* ------------------------------------------------------------------ */
    /* Меню трека                                                          */
    /* ------------------------------------------------------------------ */

    public void openTrackMenu(Models.Track track) {
        menuTrack = track;
        Context c = getContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, 0, 0, Theme.dp(c, 12));

        Display d = Display.track(track);
        LinearLayout head = Ui.row(c);
        head.setPadding(Theme.dp(c, 20), 0, Theme.dp(c, 20), Theme.dp(c, 12));
        head.addView(Cards.cover(c, d.thumb != null ? d.thumb : d.cover, d.cover, 56, 10f));
        LinearLayout texts = Ui.column(c);
        TextView title = Ui.text(c, track.title, 17f, Theme.ON, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist = Ui.text(c, track.artistNames(), 14f, Theme.ON_VARIANT);
        artist.setSingleLine(true);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout metaRow = Ui.row(c);
        metaRow.addView(Ui.tag(c, track.themeSlug, track.type));
        if (Downloads.hasOffline(track.id, Downloads.KIND_AUDIO)) {
            LinearLayout.LayoutParams np = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.leftMargin = Theme.dp(c, 6);
            metaRow.addView(Ui.tag(c, "офлайн", "neutral"), np);
        }
        TextView animeTitle = Ui.text(c, d.title == null ? "" : d.title, 12.5f, Theme.ON_DIM);
        animeTitle.setSingleLine(true);
        animeTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ap.leftMargin = Theme.dp(c, 6);
        metaRow.addView(animeTitle, ap);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Theme.dp(c, 4);
        texts.addView(title);
        texts.addView(artist);
        texts.addView(metaRow, mp);
        LinearLayout.LayoutParams tp = Ui.lpw(1f);
        tp.leftMargin = Theme.dp(c, 14);
        head.addView(texts, tp);
        box.addView(head);
        box.addView(divider(c));

        boolean fav = Library.isFavorite(track.id);
        box.addView(menuItem(c, "playlist_play", "Играть следующим", null, false, () -> {
            Player.playNext(track);
            close();
            activity.toaster().show("Будет следующим");
        }));
        box.addView(menuItem(c, "queue_music", "В очередь", null, false, () -> {
            Player.addToQueue(track);
            close();
            activity.toaster().show("Добавлено в очередь");
        }));
        box.addView(menuItem(c, fav ? "favorite" : "favorite_border", fav ? "Убрать из избранного" : "В избранное", null, false, () -> {
            boolean added = Library.toggleFavorite(track);
            close();
            activity.toaster().show(added ? "Добавлено" : "Удалено");
        }));
        box.addView(menuItem(c, "playlist_add", "В плейлист", null, false, () -> {
            close();
            postDelayed(() -> openPlaylistPicker(track), 220);
        }));
        box.addView(spacedDivider(c));
        box.addView(menuItem(c, "download", "Скачать аудио", null, false, () -> {
            Downloads.download(track, Downloads.KIND_AUDIO, true);
            close();
        }));
        if (!track.videoUrl.equals(track.audioUrl)) {
            box.addView(menuItem(c, "videocam", "Скачать видео", track.resolution != null ? track.resolution + "p" : null, false, () -> {
                Downloads.download(track, Downloads.KIND_VIDEO, true);
                close();
            }));
        }
        if (Downloads.hasOffline(track.id, Downloads.KIND_AUDIO)) {
            box.addView(menuItem(c, "offline_pin", "Удалить из офлайн", null, false, () -> {
                Downloads.removeOffline(track.id, Downloads.KIND_AUDIO);
                close();
            }));
        } else {
            box.addView(menuItem(c, "cloud_download", "Сохранить офлайн", null, false, () -> {
                Downloads.download(track, Downloads.KIND_AUDIO, false);
                close();
            }));
        }
        box.addView(spacedDivider(c));
        box.addView(menuItem(c, "tv", "К аниме", d.title, false, () -> {
            go();
            activity.openAnime(track.anime.slug);
        }));
        for (Models.ArtistRef artistRef : track.artists) {
            if (artistRef.slug == null || artistRef.slug.isEmpty()) continue;
            box.addView(menuItem(c, "mic", "К исполнителю", artistRef.name, false, () -> {
                go();
                activity.openArtist(artistRef.slug);
            }));
        }
        box.addView(menuItem(c, "share", "Поделиться", null, false, () -> {
            String result = Share.track(activity, track);
            close();
            if ("copied".equals(result)) activity.toaster().show("Скопировано");
            else if ("failed".equals(result)) activity.toaster().show("Не удалось");
        }));

        show(null, box, () -> menuTrack = null);
    }

    private void go() {
        close();
        activity.nowPlaying().close();
    }

    /* ------------------------------------------------------------------ */
    /* Очередь                                                             */
    /* ------------------------------------------------------------------ */

    public void openQueue() {
        Context c = getContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, 0, 0, Theme.dp(c, 16));

        List<Models.Track> queue = Player.queue();
        TextView title = Ui.text(c, "Очередь  " + queue.size(), 17f, Theme.ON, true);
        LinearLayout headerRow = Ui.row(c);
        headerRow.setPadding(Theme.dp(c, 20), Theme.dp(c, 4), Theme.dp(c, 12), Theme.dp(c, 8));
        headerRow.addView(title, Ui.lpw(1f));
        FrameLayout shuffleBtn = Ui.iconButton(c, "shuffle", 19, Player.shuffle() ? Theme.ON : Theme.ON_DIM, () -> {
            Player.toggleShuffle();
            openQueue();
        });
        FrameLayout clearBtn = Ui.iconButton(c, "delete", 19, Theme.ON_DIM, queue.size() < 2 ? null : () -> {
            Player.clearQueue();
            openQueue();
            activity.toaster().show("Очередь очищена");
        });
        headerRow.addView(shuffleBtn);
        headerRow.addView(clearBtn);
        box.addView(headerRow);

        for (int i = 0; i < queue.size(); i++) {
            final int index = i;
            Models.Track t = queue.get(i);
            LinearLayout trailingBox = Ui.column(c);
            trailingBox.setGravity(Gravity.CENTER);
            ImageView up = Ui.icon(c, "expand_less", 16, i == 0 ? Theme.alpha(Theme.ON_DIM, 0.3f) : Theme.ON_DIM);
            up.setOnClickListener(v -> {
                Player.moveInQueue(index, index - 1);
                openQueue();
            });
            ImageView down = Ui.icon(c, "expand_more", 16, i == queue.size() - 1 ? Theme.alpha(Theme.ON_DIM, 0.3f) : Theme.ON_DIM);
            down.setOnClickListener(v -> {
                Player.moveInQueue(index, index + 1);
                openQueue();
            });
            trailingBox.addView(up, Ui.lp(Theme.dp(c, 20), Theme.dp(c, 18)));
            trailingBox.addView(down, Ui.lp(Theme.dp(c, 20), Theme.dp(c, 18)));
            View row = Cards.trackRow(activity, t, queue, true, false, true,
                    queue.size() > 1 ? () -> {
                        Player.removeFromQueue(index);
                        openQueue();
                    } : null, trailingBox);
            box.addView(row);
        }
        if (queue.isEmpty()) {
            box.addView(Ui.emptyState(c, "queue_music", "Очередь пуста", "Включите любой трек — он появится здесь.", null, null));
        }
        show(null, box, null);
    }

    /** Список загрузок (клик по плашке «Загрузка»). */
    public void openQueueDownloads() {
        activity.showTab(3, true);
        if (activity.tabIndex() == 3) activity.sheets().openDownloadsSheet();
    }

    public void openDownloadsSheet() {
        Context c = getContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, 0, 0, Theme.dp(c, 16));
        List<Downloads.Job> jobs = Downloads.jobs();
        if (jobs.isEmpty()) {
            box.addView(Ui.emptyState(c, "cloud_download", "Нет загрузок", "В меню трека выберите «Сохранить офлайн».", null, null));
        }
        for (Downloads.Job job : jobs) {
            LinearLayout row = Ui.row(c);
            row.setPadding(Theme.dp(c, 16), Theme.dp(c, 10), Theme.dp(c, 16), Theme.dp(c, 10));
            row.addView(Cards.cover(c, job.track.coverSmall != null ? job.track.coverSmall : job.track.cover, job.track.cover, 40, 8f));
            LinearLayout texts = Ui.column(c);
            TextView name = Ui.text(c, job.track.title, 15f, Theme.ON);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView status = Ui.text(c, jobStatus(job), 12.5f, Theme.ON_VARIANT);
            texts.addView(name);
            texts.addView(status);
            LinearLayout.LayoutParams tp = Ui.lpw(1f);
            tp.leftMargin = Theme.dp(c, 12);
            row.addView(texts, tp);
            boolean live = job.status == Downloads.Status.QUEUED || job.status == Downloads.Status.DOWNLOADING;
            FrameLayout action = Ui.iconButton(c, live ? "stop" : "close", live ? 14 : 16, Theme.ON_DIM, () -> {
                if (live) Downloads.cancel(job.key);
                else Downloads.dismiss(job.key);
                openDownloadsSheet();
            });
            row.addView(action);
            box.addView(row);
            if (live) {
                FrameLayout bar = Ui.progressBar(c, 4f);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 4));
                bp.leftMargin = Theme.dp(c, 16);
                bp.rightMargin = Theme.dp(c, 16);
                bp.bottomMargin = Theme.dp(c, 10);
                box.addView(bar, bp);
                float percent = job.total > 0 ? job.received * 100f / job.total : 0f;
                Ui.setProgress(bar, percent);
            }
        }
        show("Загрузки", box, null);
    }

    private static String jobStatus(Downloads.Job j) {
        switch (j.status) {
            case QUEUED:
                return "В очереди";
            case DOWNLOADING:
                return j.total > 0 ? Math.round(j.received * 100f / j.total) + "% · " + Downloads.formatBytes(j.total) : Downloads.formatBytes(j.received);
            case DONE:
                return "Готово · " + Downloads.formatBytes(j.received);
            case ERROR:
                return j.error != null ? j.error : "Ошибка";
            case CANCELLED:
                return "Отменено";
            default:
                return "";
        }
    }

    /* ------------------------------------------------------------------ */
    /* Выбор плейлиста                                                     */
    /* ------------------------------------------------------------------ */

    public void openPlaylistPicker(Models.Track track) {
        pickerTrack = track;
        Context c = getContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, 0, 0, Theme.dp(c, 16));

        box.addView(menuItem(c, "add", "Новый плейлист", null, false, () -> openCreatePlaylist(track)));

        for (Models.Playlist pl : Library.playlists()) {
            boolean has = false;
            for (Models.Track t : pl.tracks) if (t.id.equals(track.id)) has = true;
            final boolean already = has;
            LinearLayout row = Ui.row(c);
            row.setPadding(Theme.dp(c, 20), Theme.dp(c, 10), Theme.dp(c, 20), Theme.dp(c, 10));
            FrameLayout mosaic = new FrameLayout(c);
            mosaic.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 9f)));
            int cell = Theme.dp(c, 22);
            for (int i = 0; i < Math.min(4, pl.tracks.size()); i++) {
                CoverView cv = new CoverView(c);
                cv.setRadiusDp(0f);
                cv.setIconSizeDp(11f);
                cv.setUrl(pl.tracks.get(i).coverSmall != null ? pl.tracks.get(i).coverSmall : pl.tracks.get(i).cover, null);
                FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(cell, cell);
                cp.leftMargin = (i % 2) * cell;
                cp.topMargin = (i / 2) * cell;
                mosaic.addView(cv, cp);
            }
            row.addView(mosaic, Ui.lp(Theme.dp(c, 44), Theme.dp(c, 44)));
            LinearLayout texts = Ui.column(c);
            texts.addView(Ui.text(c, pl.name, 16f, Theme.ON));
            texts.addView(Ui.text(c, plural(pl.tracks.size(), "трек", "трека", "треков"), 13f, Theme.ON_VARIANT));
            LinearLayout.LayoutParams tp = Ui.lpw(1f);
            tp.leftMargin = Theme.dp(c, 14);
            row.addView(texts, tp);
            if (already) row.addView(Ui.icon(c, "check", 18, Theme.ON));
            row.setOnClickListener(v -> {
                if (already) {
                    activity.toaster().show("Уже в плейлисте");
                    return;
                }
                Library.addToPlaylist(pl.id, track);
                activity.toaster().show("Добавлено в «" + pl.name + "»");
                close();
            });
            Ui.tap(row);
            box.addView(row);
        }
        show("В плейлист", box, () -> pickerTrack = null);
    }

    private void openCreatePlaylist(Models.Track forTrack) {
        Context c = getContext();
        LinearLayout box = Ui.row(c);
        box.setPadding(Theme.dp(c, 20), 0, Theme.dp(c, 20), Theme.dp(c, 24));
        EditText input = new EditText(c);
        input.setHint("Название");
        input.setHintTextColor(Theme.ON_DIM);
        input.setTextColor(Theme.ON);
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 10f)));
        input.setPadding(Theme.dp(c, 14), Theme.dp(c, 12), Theme.dp(c, 14), Theme.dp(c, 12));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        TextView create = Ui.primaryButton(c, "Создать", () -> {
            String name = input.getText().toString().trim();
            Models.Playlist created = Library.createPlaylist(name, forTrack == null ? null : java.util.Collections.singletonList(forTrack));
            activity.toaster().show("Создан «" + created.name + "»");
            close();
        });
        LinearLayout.LayoutParams ip = Ui.lpw(1f);
        box.addView(input, ip);
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Theme.dp(c, 8);
        box.addView(create, cp);
        show("Новый плейлист", box, null);
        input.requestFocus();
    }

    public void openCreatePlaylist(Runnable afterCreate) {
        Context c = getContext();
        LinearLayout box = Ui.row(c);
        box.setPadding(Theme.dp(c, 20), 0, Theme.dp(c, 20), Theme.dp(c, 24));
        EditText input = new EditText(c);
        input.setHint("Название");
        input.setHintTextColor(Theme.ON_DIM);
        input.setTextColor(Theme.ON);
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 10f)));
        input.setPadding(Theme.dp(c, 14), Theme.dp(c, 12), Theme.dp(c, 14), Theme.dp(c, 12));
        TextView create = Ui.primaryButton(c, "Создать", () -> {
            Models.Playlist created = Library.createPlaylist(input.getText().toString(), null);
            activity.toaster().show("Создан «" + created.name + "»");
            close();
            if (afterCreate != null) afterCreate.run();
        });
        box.addView(input, Ui.lpw(1f));
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Theme.dp(c, 8);
        box.addView(create, cp);
        show("Новый плейлист", box, null);
        input.requestFocus();
    }

    /* ------------------------------------------------------------------ */
    /* Меню плейлиста                                                      */
    /* ------------------------------------------------------------------ */

    public void openPlaylistMenu(Models.Playlist playlist, Runnable onOpen) {
        menuPlaylist = playlist;
        Context c = getContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, 0, 0, Theme.dp(c, 12));
        box.addView(menuItem(c, "edit", "Переименовать", null, false, () -> {
            close();
            postDelayed(() -> openRenamePlaylist(playlist), 220);
        }));
        box.addView(menuItem(c, "queue_music", "В очередь", null, false, () -> {
            for (Models.Track t : playlist.tracks) Player.addToQueue(t);
            close();
            activity.toaster().show("Добавлено в очередь");
        }));
        box.addView(menuItem(c, "cloud_download", "Сохранить офлайн", plural(playlist.tracks.size(), "трек", "трека", "треков"), false, () -> {
            for (Models.Track t : playlist.tracks) Downloads.download(t, Downloads.KIND_AUDIO, false);
            close();
        }));
        box.addView(menuItem(c, "delete", "Удалить плейлист", null, true, () -> {
            Library.deletePlaylist(playlist.id);
            close();
            activity.toaster().show("Удалено");
            activity.pop();
        }));
        show(playlist.name, box, () -> menuPlaylist = null);
    }

    private void openRenamePlaylist(Models.Playlist playlist) {
        Context c = getContext();
        LinearLayout box = Ui.row(c);
        box.setPadding(Theme.dp(c, 20), 0, Theme.dp(c, 20), Theme.dp(c, 24));
        EditText input = new EditText(c);
        input.setText(playlist.name);
        input.setTextColor(Theme.ON);
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setBackground(Ui.rounded(Theme.SURFACE_3, Theme.dpF(c, 10f)));
        input.setPadding(Theme.dp(c, 14), Theme.dp(c, 12), Theme.dp(c, 14), Theme.dp(c, 12));
        TextView done = Ui.primaryButton(c, "Готово", () -> {
            Library.renamePlaylist(playlist.id, input.getText().toString());
            close();
        });
        box.addView(input, Ui.lpw(1f));
        LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = Theme.dp(c, 8);
        box.addView(done, cp);
        show("Переименовать", box, null);
        input.requestFocus();
    }

    /* ------------------------------------------------------------------ */
    /* Настройки                                                           */
    /* ------------------------------------------------------------------ */

    public void openSettings() {
        Context c = getContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, Theme.dp(c, 4), 0, Theme.dp(c, 24));

        box.addView(label(c, "Период каталога"));
        Ui.Segmented period = new Ui.Segmented(c, new String[]{"Сегодня", "Неделя", "Всё"}, indexOf(new String[]{"today", "week", "all"}, Settings.period), i -> Settings.setPeriod(new String[]{"today", "week", "all"}[i]));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 32));
        pp.leftMargin = Theme.dp(c, 16);
        pp.rightMargin = Theme.dp(c, 16);
        box.addView(period, pp);

        box.addView(label(c, "Контент"));
        Ui.Segmented mature = new Ui.Segmented(c, new String[]{"Без 18+", "Разрешить 18+"}, "on".equals(Settings.mature) ? 1 : 0, i -> Settings.setMature(i == 1 ? "on" : "off"));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 32));
        mp.leftMargin = Theme.dp(c, 16);
        mp.rightMargin = Theme.dp(c, 16);
        box.addView(mature, mp);

        LinearLayout contentGroup = Ui.listGroup(c, "Контент и язык", null);
        LinearLayout content = Ui.groupBody(contentGroup);
        content.addView(Ui.switchRow(c, "translate", "Русские названия", null, Settings.ruTitles, true, value -> Settings.setRuTitles(value)));
        content.addView(Ui.switchRow(c, "layers", "Расширенная база", "Больше песен: вставки и редкие темы", Settings.extraSources, false, value -> Settings.setExtraSources(value)));
        box.addView(contentGroup);

        LinearLayout trafficGroup = Ui.listGroup(c, "Трафик", "Видео никогда не загружается само — только когда вы нажмёте «Видео». При сворачивании плеера поток видео останавливается.");
        LinearLayout traffic = Ui.groupBody(trafficGroup);
        traffic.addView(Ui.switchRow(c, "data_saver", "Экономия трафика", "Лёгкие обложки, без предзагрузки", Settings.dataSaver, true, value -> Settings.setDataSaver(value)));
        traffic.addView(Ui.switchRow(c, "bolt", "Готовить следующий трек", "Переключение без паузы", Settings.preloadNext && !Settings.dataSaver, false, value -> Settings.setPreloadNext(value)));
        box.addView(trafficGroup);

        box.addView(label(c, "Формат скачивания"));
        Ui.Segmented kind = new Ui.Segmented(c, new String[]{"Аудио", "Видео"}, "video".equals(Settings.downloadKind) ? 1 : 0, i -> Settings.setDownloadKind(i == 1 ? "video" : "audio"));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 32));
        kp.leftMargin = Theme.dp(c, 16);
        kp.rightMargin = Theme.dp(c, 16);
        box.addView(kind, kp);

        LinearLayout storageGroup = Ui.listGroup(c, "Хранилище", null);
        LinearLayout storage = Ui.groupBody(storageGroup);
        storage.addView(Ui.listRow(c, "storage", "Офлайн-треки", null, true, null,
                () -> Downloads.offlineTracks().size() + " · " + Downloads.formatBytes(Downloads.offlineTotalSize())));
        storage.addView(Ui.listRow(c, "cached", "Очистить кэш", null, false, () -> {
            Net.clearCache();
            activity.toaster().show("Кэш очищен");
        }, () -> Net.cacheSizeLabel()));
        storage.addView(Ui.listRow(c, "delete", "Удалить офлайн-треки", null, false, () -> {
            Downloads.clearOffline();
            activity.toaster().show("Удалено");
        }, null, true));
        box.addView(storageGroup);

        TextView footer = Ui.text(c, "AniBeat · нативное приложение", 12f, Theme.ON_DIM);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(Theme.dp(c, 28), Theme.dp(c, 14), Theme.dp(c, 28), 0);
        box.addView(footer);

        show("Настройки", box, null);
    }

    /* ------------------------------------------------------------------ */
    /* Мелкие элементы                                                     */
    /* ------------------------------------------------------------------ */

    private static TextView label(Context c, String text) {
        TextView tv = Ui.text(c, text.toUpperCase(), 13f, Theme.ON_VARIANT, true);
        tv.setPadding(Theme.dp(c, 20), Theme.dp(c, 14), Theme.dp(c, 20), Theme.dp(c, 8));
        tv.setLetterSpacing(0.04f);
        return tv;
    }

    private static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x14FFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(c, 0.5f))));
        return v;
    }

    private static View spacedDivider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Theme.dp(c, 0.5f)));
        p.leftMargin = Theme.dp(c, 16);
        p.rightMargin = Theme.dp(c, 16);
        p.topMargin = Theme.dp(c, 4);
        p.bottomMargin = Theme.dp(c, 4);
        v.setLayoutParams(p);
        return v;
    }

    public static View menuItem(Context c, String icon, String label, String sub, boolean danger, Runnable click) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Theme.dp(c, 20), Theme.dp(c, 12), Theme.dp(c, 20), Theme.dp(c, 12));
        row.addView(Ui.icon(c, icon, 21, danger ? Theme.ERROR : Theme.ON_VARIANT));
        LinearLayout texts = Ui.column(c);
        TextView title = Ui.text(c, label, 16f, danger ? Theme.ERROR : Theme.ON);
        texts.addView(title);
        if (sub != null && !sub.isEmpty()) {
            TextView subtitle = Ui.text(c, sub, 13f, Theme.ON_VARIANT);
            subtitle.setSingleLine(true);
            subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(subtitle);
        }
        LinearLayout.LayoutParams tp = Ui.lpw(1f);
        tp.leftMargin = Theme.dp(c, 14);
        row.addView(texts, tp);
        row.setOnClickListener(v -> click.run());
        Ui.tap(row);
        return row;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }

    public static String plural(int n, String one, String few, String many) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return n + " " + one;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return n + " " + few;
        return n + " " + many;
    }
}
