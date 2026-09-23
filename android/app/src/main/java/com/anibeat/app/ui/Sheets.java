package com.anibeat.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.anibeat.app.AniBeatApp;
import com.anibeat.app.R;
import com.anibeat.app.core.Net;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;

import java.util.List;

/** Нижние панели: меню трека, очередь, скачивания, настройки и служебные окна. */
public final class Sheets {

    private Sheets() {
    }

    /* ----------------------------- каркас -------------------------------- */

    public static BottomSheetDialog open(Context context, String title, View body) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackground(Ui.rounded(context, Theme.SURFACE_2, 20f));
        wrap.setPadding(0, Theme.dp(context, 8), 0, Theme.dp(context, 12));
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(18f);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setTextColor(Theme.ON);
        header.setPadding(Theme.dp(context, 20), Theme.dp(context, 12), Theme.dp(context, 20), Theme.dp(context, 10));
        wrap.addView(header);
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(wrap);
        dialog.setOnShowListener(d -> Ui.safe(() -> {
            BottomSheetDialog sheet = (BottomSheetDialog) d;
            View panel = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (panel != null) {
                panel.setBackgroundColor(Color.TRANSPARENT);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(panel);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                int height = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.9f);
                ViewGroup.LayoutParams params = panel.getLayoutParams();
                params.height = height;
                panel.setLayoutParams(params);
            }
        }));
        dialog.show();
        return dialog;
    }

    public static LinearLayout body(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, Theme.dp(context, 8));
        return column;
    }

    /* --------------------------- элементы -------------------------------- */

    public static View actionRow(Context context, int icon, String title, String subtitle, final Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(context, 20), Theme.dp(context, 13), Theme.dp(context, 20), Theme.dp(context, 13));
        if (icon != 0) {
            ImageView image = new ImageView(context);
            image.setImageResource(icon);
            image.setColorFilter(Theme.ON_VARIANT);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Theme.dp(context, 22), Theme.dp(context, 22));
            params.rightMargin = Theme.dp(context, 16);
            row.addView(image, params);
        }
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(15f);
        label.setTextColor(Theme.ON);
        column.addView(label);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextSize(12.5f);
            sub.setTextColor(Theme.ON_VARIANT);
            column.addView(sub);
        }
        row.setOnClickListener(v -> Ui.safe(action));
        Ui.press(row);
        return row;
    }

    private static View switchRow(Context context, String title, String subtitle, boolean checked, final BooleanSink sink) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(context, 20), Theme.dp(context, 10), Theme.dp(context, 16), Theme.dp(context, 10));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(15f);
        label.setTextColor(Theme.ON);
        column.addView(label);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextSize(12.5f);
            sub.setTextColor(Theme.ON_VARIANT);
            column.addView(sub);
        }
        MaterialSwitch toggle = new MaterialSwitch(context);
        toggle.setChecked(checked);
        toggle.setThumbTintList(ColorStateList.valueOf(checked ? Theme.ACCENT : Theme.ON_DIM));
        toggle.setOnCheckedChangeListener((button, value) -> {
            MaterialSwitch view = (MaterialSwitch) button;
            view.setThumbTintList(ColorStateList.valueOf(value ? Theme.ACCENT : Theme.ON_DIM));
            Ui.safe(() -> sink.onValue(value));
        });
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        return row;
    }

    private static View radioBlock(Context context, String title, String[] values, String[] labels,
                                   String selected, final StringSink sink) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Theme.dp(context, 20), Theme.dp(context, 10), Theme.dp(context, 20), Theme.dp(context, 6));
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(15f);
        header.setTextColor(Theme.ON);
        column.addView(header);
        LinearLayout options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);
        column.addView(options);
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            MaterialRadioButton radio = new MaterialRadioButton(context);
            radio.setText(labels[i]);
            radio.setTextSize(14f);
            radio.setTextColor(Theme.ON_VARIANT);
            radio.setButtonTintList(ColorStateList.valueOf(Theme.ACCENT));
            radio.setChecked(value.equals(selected));
            radio.setOnClickListener(v -> Ui.safe(() -> sink.onValue(value)));
            options.addView(radio);
        }
        return column;
    }

    public static View paragraph(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(13f);
        view.setTextColor(Theme.ON_VARIANT);
        view.setPadding(Theme.dp(context, 20), Theme.dp(context, 6), Theme.dp(context, 20), Theme.dp(context, 12));
        view.setLineSpacing(Theme.dp(context, 3), 1f);
        return view;
    }

    public interface BooleanSink {
        void onValue(boolean value);
    }

    public interface StringSink {
        void onValue(String value);
    }

    /* --------------------------- меню трека ------------------------------ */

    public static void trackMenu(final Host host, final Models.Track track, View anchor) {
        if (track == null) return;
        Context context = host.activity();
        LinearLayout body = body(context);
        body.addView(header(context, track));
        body.addView(actionRow(context, R.drawable.ic_play_arrow, "Играть сейчас", null,
                () -> host.playTrack(track, one(track), 0)));
        if ((track.videoUrl != null && !track.videoUrl.isEmpty()) || Player.hasOfflineVideo(track)) {
            final boolean offlineVideo = Player.hasOfflineVideo(track);
            body.addView(actionRow(context, R.drawable.ic_videocam,
                    offlineVideo ? "Смотреть скачанное видео" : "Смотреть видео",
                    offlineVideo ? "С устройства, без интернета" : "Клип из источника", () -> {
                        Player.setVideoMode(true);
                        host.playTrack(track, one(track), 0);
                        host.openNowPlaying();
                    }));
        }
        if (Player.hasOfflineVideo(track)
                || Downloads.hasOffline(track.id, Downloads.KIND_AUDIO)) {
            body.addView(actionRow(context, R.drawable.ic_download_done, "Скачано на устройство",
                    "Играет без интернета", () -> {
                        Player.setVideoMode(false);
                        host.playTrack(track, one(track), 0);
                        host.openNowPlaying();
                    }));
        }
        body.addView(actionRow(context, R.drawable.ic_playlist_play, "Играть следующим", null,
                () -> {
                    Player.playNext(track);
                    host.toast("Играет следующим");
                }));
        body.addView(actionRow(context, R.drawable.ic_queue_music, "В очередь", null,
                () -> {
                    Player.enqueue(track);
                    host.toast("Добавлено в очередь");
                }));
        boolean favorite = Library.isFavorite(track.id);
        body.addView(actionRow(context, favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border,
                favorite ? "Убрать из избранного" : "В избранное", null, () -> {
                    Library.toggleFavorite(track);
                    host.toast(favorite ? "Убрано из избранного" : "Добавлено в избранное");
                }));
        body.addView(actionRow(context, R.drawable.ic_playlist_add, "В плейлист…", null,
                () -> playlistPicker(host, track)));
        body.addView(actionRow(context, R.drawable.ic_download, "Скачать аудио", offlineLabel(track, Downloads.KIND_AUDIO),
                () -> {
                    Downloads.download(track, Downloads.KIND_AUDIO, true);
                    host.toast("Скачивание аудио началось");
                }));
        if (track.videoUrl != null && !track.videoUrl.isEmpty()) {
            body.addView(actionRow(context, R.drawable.ic_videocam, "Скачать видео", offlineLabel(track, Downloads.KIND_VIDEO),
                    () -> {
                        Downloads.download(track, Downloads.KIND_VIDEO, true);
                        host.toast("Скачивание видео началось");
                    }));
        }
        if (Downloads.hasOffline(track.id, Downloads.KIND_AUDIO) || Downloads.hasOffline(track.id, Downloads.KIND_VIDEO)) {
            body.addView(actionRow(context, R.drawable.ic_delete, "Удалить скачанное",
                    Downloads.formatBytes(Downloads.offlineSizeOf(track.id, Downloads.KIND_AUDIO)
                            + Downloads.offlineSizeOf(track.id, Downloads.KIND_VIDEO)), () -> {
                        Downloads.removeOffline(track.id, Downloads.KIND_AUDIO);
                        Downloads.removeOffline(track.id, Downloads.KIND_VIDEO);
                        host.toast("Файлы удалены с устройства");
                    }));
        }
        body.addView(actionRow(context, R.drawable.ic_share, "Поделиться", null,
                () -> Share.track(context, track)));
        body.addView(actionRow(context, R.drawable.ic_tv, "Открыть аниме", track.anime == null ? "" : track.anime.name,
                () -> host.openAnime(track.anime)));
        if (track.artists != null && !track.artists.isEmpty()) {
            final Models.ArtistRef artist = track.artists.get(0);
            body.addView(actionRow(context, R.drawable.ic_person, "Открыть исполнителя", artist.name,
                    () -> host.openArtist(artist)));
        }
        body.addView(actionRow(context, R.drawable.ic_history, "Добавить в историю", null, () -> {
            Library.addToHistory(track);
            host.toast("Добавлено в историю");
        }));
        open(context, "Действия с треком", body);
    }

    /** Что делать со скачанным треком: смотреть, слушать, удалить файлы. */
    public static void offlineMenu(final Host host, final Models.Track track) {
        if (track == null) return;
        Context context = host.activity();
        LinearLayout body = body(context);
        body.addView(header(context, track));
        final boolean offlineVideo = Player.hasOfflineVideo(track);
        if (offlineVideo) {
            body.addView(actionRow(context, R.drawable.ic_videocam, "Смотреть видео с устройства",
                    "Без интернета", () -> {
                        Player.setVideoMode(true);
                        host.playTrack(track, one(track), 0);
                        host.openNowPlaying();
                    }));
        }
        if (Downloads.hasOffline(track.id, Downloads.KIND_AUDIO)) {
            body.addView(actionRow(context, R.drawable.ic_play_arrow, "Слушать с устройства",
                    "Без интернета", () -> {
                        Player.setVideoMode(false);
                        host.playTrack(track, one(track), 0);
                        host.openNowPlaying();
                    }));
        }
        if (track.videoUrl != null && !track.videoUrl.isEmpty() && !offlineVideo) {
            body.addView(actionRow(context, R.drawable.ic_download, "Скачать видео",
                    "Сохранить клип на устройство", () -> {
                        Downloads.download(track, Downloads.KIND_VIDEO, true);
                        host.toast("Скачивание видео началось");
                    }));
        }
        body.addView(actionRow(context, R.drawable.ic_delete, "Удалить с устройства",
                Downloads.formatBytes(Downloads.offlineSizeOf(track.id, Downloads.KIND_AUDIO)
                        + Downloads.offlineSizeOf(track.id, Downloads.KIND_VIDEO)), () -> {
                    Downloads.removeOffline(track.id, Downloads.KIND_AUDIO);
                    Downloads.removeOffline(track.id, Downloads.KIND_VIDEO);
                    host.toast("Файлы удалены");
                }));
        open(context, "Скачанный трек", body);
    }

    private static View header(Context context, Models.Track track) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(context, 20), Theme.dp(context, 4), Theme.dp(context, 20), Theme.dp(context, 12));
        ImageView cover = new ImageView(context);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(Theme.dp(context, 56), Theme.dp(context, 56));
        coverParams.rightMargin = Theme.dp(context, 14);
        row.addView(cover, coverParams);
        Display display = Display.track(track);
        Img.loadRounded(cover, display.thumb != null ? display.thumb : display.cover, Img.size(context, 56), 12f);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = new TextView(context);
        title.setText(track.title == null || track.title.isEmpty() ? track.themeSlug : track.title);
        title.setTextSize(15.5f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Theme.ON);
        title.setMaxLines(2);
        column.addView(title);
        TextView subtitle = new TextView(context);
        subtitle.setText(TrackRows.subtitleOf(track));
        subtitle.setTextSize(12.5f);
        subtitle.setTextColor(Theme.ON_VARIANT);
        subtitle.setMaxLines(2);
        column.addView(subtitle);
        return row;
    }

    private static String offlineLabel(Models.Track track, String kind) {
        return Downloads.hasOffline(track.id, kind) ? "Уже скачано" : null;
    }

    private static java.util.List<Models.Track> one(Models.Track track) {
        java.util.List<Models.Track> list = new java.util.ArrayList<>();
        list.add(track);
        return list;
    }

    /* ---------------------------- плейлисты ------------------------------ */

    public static void playlistPicker(final Host host, final Models.Track track) {
        Context context = host.activity();
        LinearLayout body = body(context);
        body.addView(actionRow(context, R.drawable.ic_add, "Создать плейлист", null,
                () -> createPlaylist(host, () -> {
                    Library.addToPlaylist(Library.playlists().get(Library.playlists().size() - 1).id, track);
                    host.toast("Добавлено в новый плейлист");
                })));
        List<Models.Playlist> playlists = Library.playlists();
        if (playlists.isEmpty()) {
            body.addView(paragraph(context, "Плейлистов пока нет. Создайте первый — трек сразу попадёт в него."));
        } else {
            for (final Models.Playlist playlist : playlists) {
                body.addView(actionRow(context, R.drawable.ic_library_music, playlist.name,
                        Format.plural(playlist.tracks.size(), "трек", "трека", "треков"),
                        () -> {
                            Library.addToPlaylist(playlist.id, track);
                            host.toast("Добавлено в «" + playlist.name + "»");
                        }));
            }
        }
        open(context, "Добавить в плейлист", body);
    }

    public static void createPlaylist(final Host host, final Runnable done) {
        Context context = host.activity();
        FrameLayout wrap = new FrameLayout(context);
        wrap.setPadding(Theme.dp(context, 24), Theme.dp(context, 8), Theme.dp(context, 24), 0);
        final android.widget.EditText input = new android.widget.EditText(context);
        input.setHint("Название плейлиста");
        input.setTextColor(Theme.ON);
        input.setHintTextColor(Theme.ON_DIM);
        wrap.addView(input);
        new MaterialAlertDialogBuilder(context)
                .setTitle("Новый плейлист")
                .setView(wrap)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) name = "Мой плейлист";
                    Library.createPlaylist(name, new java.util.ArrayList<>());
                    host.toast("Плейлист «" + name + "» создан");
                    if (done != null) Ui.safe(done);
                })
                .show();
    }

    /* ----------------------------- очередь ------------------------------- */

    public static void queue(final Host host) {
        Context context = host.activity();
        final LinearLayout body = body(context);
        final BottomSheetDialog[] holder = new BottomSheetDialog[1];
        Runnable render = () -> {
            body.removeAllViews();
            List<Models.Track> queue = Player.queue();
            body.addView(actionRow(context, R.drawable.ic_shuffle, "Перемешать", null,
                    () -> {
                        Player.setShuffle(!Player.shuffle());
                        host.toast(Player.shuffle() ? "Перемешивание включено" : "Перемешивание выключено");
                    }));
            body.addView(actionRow(context, R.drawable.ic_delete, "Очистить очередь", null, () -> {
                Player.stop();
                if (holder[0] != null) holder[0].dismiss();
            }));
            if (queue.isEmpty()) {
                body.addView(paragraph(context, "Очередь пуста"));
                return;
            }
            body.addView(paragraph(context, "Сейчас в очереди: " + Format.plural(queue.size(), "трек", "трека", "треков")));
            for (int i = 0; i < queue.size(); i++) {
                final int position = i;
                final Models.Track track = queue.get(i);
                boolean playing = i == Player.currentIndex();
                View row = TrackRows.create(context, track, i + 1, playing,
                        v -> {
                            Player.jumpTo(position);
                            if (holder[0] != null) holder[0].dismiss();
                        },
                        v -> {
                            Player.removeAt(position);
                            Ui.safe(() -> {
                                body.removeAllViews();
                                queue(host);
                            });
                        });
                if (playing) row.setBackgroundColor(Theme.SURFACE_2);
                body.addView(row);
            }
        };
        holder[0] = open(context, "Очередь", body);
        Ui.safe(render);
    }

    /* ---------------------------- скачивания ----------------------------- */

    public static void downloads(final Host host) {
        Context context = host.activity();
        final LinearLayout body = body(context);
        final Downloads.Listener[] listener = new Downloads.Listener[1];
        final BottomSheetDialog[] holder = new BottomSheetDialog[1];
        final Runnable[] renderHolder = new Runnable[1];
        renderHolder[0] = () -> Ui.safe(() -> {
            body.removeAllViews();
            List<Downloads.Job> jobs = Downloads.jobs();
            if (jobs.isEmpty()) {
                body.addView(paragraph(context, "Активных загрузок нет."));
            }
            for (final Downloads.Job job : jobs) {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(Theme.dp(context, 20), Theme.dp(context, 10), Theme.dp(context, 20), Theme.dp(context, 10));
                card.addView(header(context, job.track));
                LinearProgressIndicator progress = new LinearProgressIndicator(context);
                progress.setTrackColor(Theme.SURFACE_4);
                progress.setIndicatorColor(job.status == Downloads.Status.ERROR ? Theme.ERROR : Theme.ACCENT);
                int percent = job.total > 0 ? (int) (job.received * 100 / job.total) : 0;
                progress.setProgressCompat(Math.max(percent, job.status == Downloads.Status.QUEUED ? 2 : 0), true);
                card.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(context, 4)));
                TextView state = new TextView(context);
                state.setTextSize(12f);
                state.setTextColor(Theme.ON_VARIANT);
                state.setText(statusLabel(job));
                card.addView(state);
                card.addView(actionRow(context, R.drawable.ic_close, job.status == Downloads.Status.DONE ? "Убрать из списка" : "Отменить", null, () -> {
                    if (job.status == Downloads.Status.DONE || job.status == Downloads.Status.ERROR) Downloads.dismiss(job.key);
                    else Downloads.cancel(job.key);
                    renderHolder[0].run();
                }));
                body.addView(card);
            }
            body.addView(paragraph(context, "Скачано на устройство: " + Downloads.formatBytes(Downloads.offlineTotalSize())));
            List<Models.Track> offline = Downloads.offlineTracks();
            if (offline.isEmpty()) {
                body.addView(paragraph(context, "Пока ничего не скачано. Долгое нажатие на трек или кнопка скачивания в плеере сохранит файл и в папку «Загрузки»."));
            }
            for (final Models.Track track : offline) {
                body.addView(TrackRows.create(context, track, 0, false,
                        v -> host.playTrack(track, offline, offline.indexOf(track)),
                        v -> openTrackOffline(context, track)));
            }
            if (!offline.isEmpty()) {
                body.addView(actionRow(context, R.drawable.ic_delete, "Удалить все скачанные", null, () -> {
                    Downloads.clearOffline();
                    host.toast("Скачанные файлы удалены");
                    renderHolder[0].run();
                }));
            }
            body.addView(actionRow(context, R.drawable.ic_close, "Убрать завершённые", null, () -> {
                Downloads.clearFinished();
                renderHolder[0].run();
            }));
        });
        listener[0] = () -> renderHolder[0].run();
        Downloads.addListener(listener[0]);
        holder[0] = open(context, "Скачивания", body);
        holder[0].setOnDismissListener(d -> Downloads.removeListener(listener[0]));
        renderHolder[0].run();
    }

    private static void openTrackOffline(Context context, Models.Track track) {
        String audio = Downloads.offlineFile(track.id, Downloads.KIND_AUDIO).getAbsolutePath();
        String video = Downloads.offlineFile(track.id, Downloads.KIND_VIDEO).getAbsolutePath();
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("audio/*");
        intent.putExtra(android.content.Intent.EXTRA_SUBJECT, track.title);
        intent.putExtra(android.content.Intent.EXTRA_TEXT, audio + "\n" + video);
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(android.content.Intent.createChooser(intent, "Файл"));
        } catch (Throwable t) {
            Ui.report(t);
            Ui.toast(context, "Файл: " + audio);
        }
    }

    private static String statusLabel(Downloads.Job job) {
        switch (job.status) {
            case DONE:
                return "Готово · " + Downloads.formatBytes(job.total > 0 ? job.total : job.received);
            case ERROR:
                return "Ошибка" + (job.error == null ? "" : ": " + job.error);
            case CANCELLED:
                return "Отменено";
            case DOWNLOADING:
                return "Скачивается · " + Downloads.formatBytes(job.received) + " из " + Downloads.formatBytes(job.total);
            default:
                return "В очереди";
        }
    }

    /* --------------------------- настройки ------------------------------- */

    public static void settings(final Host host) {
        final Context context = host.activity();
        LinearLayout body = body(context);
        body.addView(switchRow(context, "Экономия трафика",
                "Обложки мельче, фоновые обновления выключены, кэш 40 МБ",
                Settings.dataSaver, value -> {
                    Settings.setDataSaver(value);
                    Img.setDataSaver(value);
                }));
        body.addView(switchRow(context, "Русские названия",
                "Названия аниме из Shikimori (как в источнике)",
                Settings.ruTitles, Settings::setRuTitles));
        body.addView(switchRow(context, "Дополнительные источники",
                "Искать темы и в AnisongDB",
                Settings.extraSources, Settings::setExtraSources));
        body.addView(radioBlock(context, "Что скачивать", new String[]{Downloads.KIND_AUDIO, Downloads.KIND_VIDEO},
                new String[]{"Аудио (лёгкий файл)", "Видео (тяжёлый файл)"}, Settings.downloadKind, Settings::setDownloadKind));
        body.addView(radioBlock(context, "Период новинок", new String[]{"all", "year", "month"},
                new String[]{"За всё время", "За год", "За месяц"}, Settings.period, Settings::setPeriod));
        body.addView(radioBlock(context, "Взрослый контент", new String[]{"off", "blur", "show"},
                new String[]{"Скрывать", "Показывать с пометкой", "Показывать"}, Settings.mature, Settings::setMature));
        body.addView(paragraph(context, "Кэш данных: " + Net.cacheSizeLabel()
                + "\nКэш картинок: " + Format.bytes(Img.cacheSize(context))));
        body.addView(actionRow(context, R.drawable.ic_cached, "Очистить кэш данных", "Запросы будут выполнены заново", () -> {
            Net.clearCache();
            host.toast("Кэш данных очищен");
        }));
        body.addView(actionRow(context, R.drawable.ic_storage, "Очистить кэш картинок", null, () -> {
            new Thread(() -> {
                try {
                    com.bumptech.glide.Glide.get(context.getApplicationContext()).clearDiskCache();
                } catch (Throwable t) {
                    Ui.report(t);
                }
            }, "clear-images").start();
            host.toast("Кэш картинок очищается");
        }));
        body.addView(actionRow(context, R.drawable.ic_history, "Очистить историю", null, () -> {
            Library.clearHistory();
            host.toast("История очищена");
        }));
        body.addView(actionRow(context, R.drawable.ic_delete, "Удалить скачанные файлы",
                Downloads.formatBytes(Downloads.offlineTotalSize()), () -> {
                    Downloads.clearOffline();
                    host.toast("Скачанные файлы удалены");
                }));
        body.addView(actionRow(context, R.drawable.ic_info, "О приложении", "AniBeat — музыка аниме", () -> about(host)));
        open(context, "Настройки", body);
    }

    public static void about(final Host host) {
        Context context = host.activity();
        LinearLayout body = body(context);
        body.addView(paragraph(context, "AniBeat — приложение для прослушивания тем из аниме.\n\n"
                + "Источники: AnimeThemes (темы и аудио), Shikimori (русские названия, описания), "
                + "AniList и AniSongDB (дополнительные сведения).\n\n"
                + "Воспроизведение: Media3 ExoPlayer. Обложки: Glide. Интерфейс: Material 3.\n\n"
                + "Экономия трафика включена по умолчанию: картинки грузятся только на видимых плитках, "
                + "аудио и видео не загружаются заранее.\n\n"
                + "Версия: " + versionOf(context)));
        open(context, "О приложении", body);
    }

    public static String versionOf(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "2.0";
        }
    }

    public static void showText(Activity activity, String title, String text) {
        final TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(12f);
        view.setTextColor(Theme.ON_VARIANT);
        view.setTextIsSelectable(true);
        view.setPadding(Theme.dp(activity, 20), Theme.dp(activity, 12), Theme.dp(activity, 20), Theme.dp(activity, 12));
        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Копировать", (dialog, which) -> {
                    try {
                        android.content.ClipboardManager clipboard =
                                (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AniBeat", text));
                        }
                    } catch (Throwable t) {
                        Ui.report(t);
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    public static void confirm(Context context, String title, String message, String positive, final Runnable action) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(positive, (dialog, which) -> Ui.safe(action))
                .show();
    }

    /** Что показать рядом с названием: исполнитель и аниме. */
    public static String plainSubtitle(Models.Track track) {
        String animeName = track.anime == null ? "" : track.anime.name;
        if (TextUtils.isEmpty(animeName)) return track.artistNames();
        return TextUtils.isEmpty(track.artistNames()) ? animeName : track.artistNames() + " · " + animeName;
    }

    /** Метаданные аниме, если они уже загружены. */
    public static Models.AnimeMeta meta(Models.AnimeRef anime) {
        return anime == null ? null : Meta.get(anime.malId);
    }
}
