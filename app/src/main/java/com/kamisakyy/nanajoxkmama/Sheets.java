package com.kamisakyy.nanajoxkmama;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Bottom sheets: track menu, queue editor, playlist picker and settings. */
public final class Sheets {
    private Sheets() { }

    /* ---------------- sheet shell ---------------- */

    private static Dialog sheet(Activity host, String title) {
        Dialog d = new Dialog(host, R.style.SheetDialog);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.rounded(Ui.S2, 22));
        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(10), Ui.dp(6));
        View grab = new View(host);
        grab.setBackground(Ui.rounded(0x4DFFFFFF, 3));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(38), Ui.dp(5));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        LinearLayout grabRow = new LinearLayout(host);
        grabRow.setGravity(Gravity.CENTER_HORIZONTAL);
        grabRow.addView(grab, gp);
        root.addView(grabRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(18)));
        TextView t = Ui.text(host, title == null ? "" : title, 16, Ui.ON, true);
        top.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(Ui.iconBtn(host, "close", 18, Ui.DIM, v -> d.dismiss()));
        root.addView(top);
        ScrollView scroll = new ScrollView(host);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(4), Ui.dp(2), Ui.dp(4), Ui.dp(18));
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
        d.show();
        return d;
    }

    private static LinearLayout body(Dialog d) {
        View decor = d.getWindow().getDecorView();
        View body = findScrollBody(decor);
        return body instanceof LinearLayout ? (LinearLayout) body : new LinearLayout(d.getContext());
    }

    private static View findScrollBody(View v) {
        if (v instanceof ScrollView) {
            return ((ScrollView) v).getChildAt(0);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findScrollBody(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    /* ---------------- track menu ---------------- */

    public static void trackMenu(MainActivity host, Track t, List<Track> context, NowPlayingView player) {
        Dialog d = sheet(host, t.title);
        LinearLayout b = body(d);
        b.addView(Ui.menuItem(host, "play_arrow", "Дальше в очереди", null, v -> {
            addNext(host, t);
            host.toast("Следующим: " + t.title);
            d.dismiss();
        }));
        b.addView(Ui.menuItem(host, "playlist_play", "В конец очереди", null, v -> {
            addQueue(host, t);
            host.toast("Добавлено в очередь");
            d.dismiss();
        }));
        b.addView(Ui.menuItem(host, Store.isFavorite(t.id) ? "favorite" : "favorite_border",
                Store.isFavorite(t.id) ? "Убрать из избранного" : "В избранное", null, v -> {
                    boolean added = Store.toggleFavorite(t);
                    host.toast(added ? "Добавлено в избранное" : "Убрано из избранного");
                    d.dismiss();
                }));
        b.addView(Ui.menuItem(host, "playlist_add", "В плейлист", null, v -> {
            d.dismiss();
            playlistPicker(host, t);
        }));
        b.addView(Ui.menuItem(host, "download", "Скачать аудио", null, v -> {
            Downloader.download(t, false, true);
            host.toast("Скачивание началось");
            d.dismiss();
        }));
        if (t.videoUrl != null && !t.videoUrl.equals(t.audioUrl)) {
            b.addView(Ui.menuItem(host, "videocam", "Скачать видео",
                    t.resolution > 0 ? t.resolution + "p" : null, v -> {
                        Downloader.download(t, true, true);
                        host.toast("Скачивание началось");
                        d.dismiss();
                    }));
        }
        if (Store.isOffline(t)) {
            b.addView(Ui.menuItem(host, "offline_pin", "Удалить из офлайн", null, v -> {
                Store.removeOffline(t.id);
                host.toast("Удалено из офлайн");
                d.dismiss();
            }));
        } else {
            b.addView(Ui.menuItem(host, "cloud_download", "Сохранить офлайн", null, v -> {
                Downloader.download(t, false, false);
                host.toast("Сохраняем офлайн");
                d.dismiss();
            }));
        }
        b.addView(divider(host));
        if (t.animeSlug != null && !t.animeSlug.isEmpty() && !t.animeSlug.startsWith("mal-") && !t.animeSlug.startsWith("ann-")) {
            b.addView(Ui.menuItem(host, "tv", "К аниме", t.animeName, v -> {
                d.dismiss();
                host.openAnime(t.animeSlug, t.animeName);
            }));
        }
        if (t.artistSlug != null && !t.artistSlug.isEmpty()) {
            b.addView(Ui.menuItem(host, "mic", "К исполнителю", t.displayArtist(), v -> {
                d.dismiss();
                host.openArtist(t.artistSlug);
            }));
        }
        b.addView(Ui.menuItem(host, "share", "Поделиться", null, v -> {
            shareTrack(host, t);
            d.dismiss();
        }));
    }

    private static View divider(Activity host) {
        View v = new View(host);
        v.setBackgroundColor(Ui.SEPARATOR);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(1));
        lp.setMargins(Ui.dp(20), Ui.dp(8), Ui.dp(20), Ui.dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    public static void shareTrack(MainActivity host, Track t) {
        String text = Util.shareText(t);
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            host.startActivity(Intent.createChooser(send, "Поделиться"));
        } catch (Exception e) {
            ClipboardManager cm = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("AniBeat", text));
            host.toast("Скопировано");
        }
    }

    /* ---------------- queue helpers ---------------- */

    private static void addNext(MainActivity host, Track t) {
        ArrayList<Track> q = Store.getQueue();
        int at = Math.min(Store.getQueueIndex() + 1, q.size());
        for (int i = q.size() - 1; i >= 0; i--) if (q.get(i).id.equals(t.id)) q.remove(i);
        at = Math.min(at, q.size());
        q.add(at, t.copy());
        Store.saveQueue(q, Store.getQueueIndex());
        Track cur = Store.getCurrentTrack();
        PlaybackService.refresh(host, cur == null ? "" : cur.id);
    }

    private static void addQueue(MainActivity host, Track t) {
        ArrayList<Track> q = Store.getQueue();
        for (int i = q.size() - 1; i >= 0; i--) if (q.get(i).id.equals(t.id)) q.remove(i);
        q.add(t.copy());
        Store.saveQueue(q, Store.getQueueIndex());
        Track cur = Store.getCurrentTrack();
        PlaybackService.refresh(host, cur == null ? "" : cur.id);
    }

    /* ---------------- queue sheet ---------------- */

    public static void queueSheet(MainActivity host, NowPlayingView player) {
        Dialog d = sheet(host, "Очередь");
        LinearLayout b = body(d);
        LinearLayout head = new LinearLayout(host);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(16), Ui.dp(2), Ui.dp(8), Ui.dp(4));
        TextView count = Ui.text(host, "", 13, Ui.VAR, false);
        head.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.iconBtn(host, "delete", 18, Ui.DIM, v -> {
            Track cur = Store.getCurrentTrack();
            Store.saveQueue(cur == null ? new ArrayList<Track>() : new ArrayList<>(java.util.Collections.singletonList(cur)), 0);
            PlaybackService.refresh(host, cur == null ? "" : cur.id);
            d.dismiss();
            host.toast("Очередь очищена");
        }));
        b.addView(head);
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            // rebuild rows
            while (b.getChildCount() > 1) b.removeViewAt(b.getChildCount() - 1);
            ArrayList<Track> q = Store.getQueue();
            count.setText(Util.pluralRu(q.size(), "трек", "трека", "треков"));
            Track cur = Store.getCurrentTrack();
            for (int i = 0; i < q.size(); i++) {
                final int at = i;
                final Track t = q.get(i);
                LinearLayout row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(16), Ui.dp(6), Ui.dp(6), Ui.dp(6));
                row.setBackground(Ui.ripple(Ui.rounded(Color.TRANSPARENT, 10)));
                Ui.CoverView cover = Ui.cover(host, 40, 8);
                cover.load(t.coverSmall.isEmpty() ? t.cover : t.coverSmall, true);
                row.addView(cover);
                LinearLayout mid = new LinearLayout(host);
                mid.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                mp.leftMargin = Ui.dp(10);
                TextView tt = Ui.text(host, t.title, 14f, cur != null && cur.id.equals(t.id) ? Ui.ACCENT : Ui.ON, false);
                TextView ss = Ui.text(host, t.displayArtist(), 11.5f, Ui.VAR, false);
                mid.addView(tt);
                mid.addView(ss);
                row.addView(mid, mp);
                row.addView(Ui.iconBtn(host, "keyboard_arrow_up", 17, Ui.DIM, v -> {
                    ArrayList<Track> qq = Store.getQueue();
                    if (at > 0) {
                        java.util.Collections.swap(qq, at, at - 1);
                        Store.saveQueue(qq, Store.getQueueIndex());
                        PlaybackService.refresh(host, Store.getCurrentTrack() == null ? "" : Store.getCurrentTrack().id);
                        render[0].run();
                    }
                }));
                row.addView(Ui.iconBtn(host, "keyboard_arrow_down", 17, Ui.DIM, v -> {
                    ArrayList<Track> qq = Store.getQueue();
                    if (at < qq.size() - 1) {
                        java.util.Collections.swap(qq, at, at + 1);
                        Store.saveQueue(qq, Store.getQueueIndex());
                        PlaybackService.refresh(host, Store.getCurrentTrack() == null ? "" : Store.getCurrentTrack().id);
                        render[0].run();
                    }
                }));
                row.addView(Ui.iconBtn(host, "close", 15, Ui.DIM, v -> {
                    ArrayList<Track> qq = Store.getQueue();
                    qq.remove(at);
                    Store.saveQueue(qq, Store.getQueueIndex());
                    PlaybackService.refresh(host, Store.getCurrentTrack() == null ? "" : Store.getCurrentTrack().id);
                    render[0].run();
                }));
                row.setOnClickListener(v -> {
                    ArrayList<Track> qq = Store.getQueue();
                    Store.saveQueue(qq, at);
                    host.playAll(qq, at, false);
                    d.dismiss();
                });
                b.addView(row);
            }
        };
        render[0].run();
    }

    /* ---------------- playlist picker ---------------- */

    public static void playlistPicker(MainActivity host, Track t) {
        Dialog d = sheet(host, "В плейлист");
        LinearLayout b = body(d);
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            while (b.getChildCount() > 0) b.removeViewAt(0);
            b.addView(Ui.menuItem(host, "add", "Новый плейлист", null, v -> {
                d.dismiss();
                promptName(host, "Новый плейлист", name -> {
                    Playlist pl = Store.createPlaylist(name, java.util.Collections.singletonList(t));
                    host.toast("Создан «" + pl.name + "»");
                });
            }));
            for (Playlist pl : Store.getPlaylists()) {
                final Playlist p = pl;
                boolean has = false;
                for (Track x : pl.tracks) if (x.id.equals(t.id)) { has = true; break; }
                final boolean hasFinal = has;
                LinearLayout row = new LinearLayout(host);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(14), Ui.dp(8));
                row.setBackground(Ui.ripple(Ui.rounded(Color.TRANSPARENT, 10)));
                FrameLayout mosaic = mosaic(host, pl);
                row.addView(mosaic, new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
                LinearLayout mid = new LinearLayout(host);
                mid.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                mp.leftMargin = Ui.dp(12);
                mid.addView(Ui.text(host, pl.name, 15.5f, Ui.ON, false));
                mid.addView(Ui.text(host, Util.pluralRu(pl.tracks.size(), "трек", "трека", "треков"), 12.5f, Ui.VAR, false));
                row.addView(mid, mp);
                if (hasFinal) row.addView(Ui.icon(host, "check", 18, Ui.ON));
                row.setOnClickListener(v -> {
                    if (hasFinal) {
                        host.toast("Уже в плейлисте");
                        return;
                    }
                    Store.addToPlaylist(p.id, t);
                    host.toast("Добавлено в «" + p.name + "»");
                    d.dismiss();
                });
                b.addView(row);
            }
        };
        render[0].run();
    }

    static FrameLayout mosaic(Activity host, Playlist pl) {
        FrameLayout f = new FrameLayout(host);
        f.setBackground(Ui.rounded(Ui.S3, 9));
        f.setClipToOutline(true);
        if (pl.tracks.isEmpty()) {
            android.widget.ImageView iv = Ui.icon(host, "queue_music", 18, Ui.DIM);
            f.addView(iv, new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18), Gravity.CENTER));
            return f;
        }
        for (int i = 0; i < Math.min(4, pl.tracks.size()); i++) {
            Track t = pl.tracks.get(i);
            Ui.CoverView c = new Ui.CoverView(host);
            c.radius(0);
            int half = Ui.dp(22);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(half, half);
            lp.leftMargin = (i % 2) * half;
            lp.topMargin = (i / 2) * half;
            c.setLayoutParams(lp);
            c.load(t.coverSmall.isEmpty() ? t.cover : t.coverSmall, true);
            f.addView(c);
        }
        return f;
    }

    public interface NameCallback { void accept(String name); }

    public interface ChoiceCallback { void accept(int which); }

    /** Simple menu chooser (playlist rename/delete). */
    public static void promptChoice(Activity host, String title, String[] options, ChoiceCallback cb) {
        Dialog d = sheet(host, title == null ? "" : title);
        LinearLayout b = body(d);
        String[] icons = {"edit", "delete"};
        for (int i = 0; i < options.length; i++) {
            final int which = i;
            b.addView(Ui.menuItem(host, icons.length > i ? icons[i] : "chevron_right", options[i], null, v -> {
                d.dismiss();
                cb.accept(which);
            }));
        }
    }

    static void promptName(Activity host, String initial, NameCallback cb) {
        Dialog d = new Dialog(host, R.style.SheetDialog);
        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.rounded(Ui.S2, 22));
        root.setPadding(Ui.dp(20), Ui.dp(18), Ui.dp(20), Ui.dp(14));
        TextView label = Ui.text(host, "Название", 16, Ui.ON, true);
        root.addView(label);
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextColor(Ui.ON);
        input.setHintTextColor(Ui.DIM);
        input.setBackground(Ui.rounded(Ui.S3, 10));
        input.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        input.setText(initial);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = Ui.dp(12);
        root.addView(input, ip);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.addView(Ui.button(host, "Отмена", null, false, v -> d.dismiss()));
        View ok = Ui.button(host, "Сохранить", null, true, v -> {
            cb.accept(input.getText().toString());
            d.dismiss();
        });
        LinearLayout.LayoutParams op = (LinearLayout.LayoutParams) ok.getLayoutParams();
        op.leftMargin = Ui.dp(8);
        row.addView(ok, op);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = Ui.dp(16);
        root.addView(row, rp);
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
        d.show();
    }

    /* ---------------- settings ---------------- */

    public static void settings(MainActivity host) {
        Dialog d = sheet(host, "Настройки");
        LinearLayout b = body(d);

        LinearLayout seg1 = new LinearLayout(host);
        seg1.setOrientation(LinearLayout.VERTICAL);
        seg1.setPadding(Ui.dp(16), Ui.dp(6), Ui.dp(16), Ui.dp(4));
        seg1.addView(sectionLabel(host, "Период каталога"));
        Ui.Segmented period = new Ui.Segmented(host, new String[]{"today", "week", "all"}, Store.getPeriod(),
                v -> Store.setPeriod(v));
        seg1.addView(period, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34)));
        seg1.addView(sectionLabel2(host, "Контент"));
        Ui.Segmented mature = new Ui.Segmented(host, new String[]{"off", "on"},
                Store.isMatureEnabled() ? "on" : "off",
                v -> {
                    Store.setMatureEnabled("on".equals(v));
                    host.toast("on".equals(v) ? "18+ включён" : "18+ выключен");
                });
        seg1.addView(mature, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34)));
        b.addView(seg1);

        b.addView(groupHeader(host, "Контент и язык"));
        b.addView(switchRow(host, "translate", "Русские названия", null, Store.isRuTitlesEnabled(),
                v -> Store.setRuTitlesEnabled(v)));
        b.addView(switchRow(host, "layers", "Расширенная база", "Больше песен: вставки и редкие темы",
                Store.isExtraSourcesEnabled(), v -> Store.setExtraSourcesEnabled(v)));

        b.addView(groupHeader(host, "Трафик"));
        b.addView(switchRow(host, "data_saver", "Экономия трафика", "Лёгкие обложки, без предзагрузки",
                Store.isDataSaverEnabled(), v -> Store.setDataSaverEnabled(v)));
        b.addView(switchRow(host, "bolt", "Готовить следующий трек", "Переключение без паузы",
                Store.isPreloadNext() && !Store.isDataSaverEnabled(), v -> Store.setPreloadNext(v)));
        b.addView(groupFooter(host, "Видео никогда не загружается само — только когда вы нажмёте «Видео». При сворачивании плеера поток видео останавливается."));

        LinearLayout seg2 = new LinearLayout(host);
        seg2.setOrientation(LinearLayout.VERTICAL);
        seg2.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(4));
        seg2.addView(sectionLabel(host, "Формат скачивания"));
        Ui.Segmented kind = new Ui.Segmented(host, new String[]{"audio", "video"}, Store.getDownloadKind(),
                v -> Store.setDownloadKind(v));
        seg2.addView(kind, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(34)));
        b.addView(seg2);

        b.addView(groupHeader(host, "Хранилище"));
        long offlineBytes = Store.offlineBytes();
        b.addView(valueRow(host, "storage", "Офлайн-треки",
                Store.getOfflineTracks().size() + " · " + Util.formatBytes(offlineBytes), null));
        b.addView(Ui.menuItem(host, "cached", "Очистить кэш", null, v -> {
            host.runIo(() -> {
                HttpCache.clear();
                host.toast("Кэш очищен");
            });
            d.dismiss();
        }));
        b.addView(Ui.menuItem(host, "delete", "Удалить офлайн-треки", null, v -> {
            Store.clearOffline();
            host.toast("Удалено");
            d.dismiss();
        }));
        b.addView(groupFooter(host, "AniBeat · версия 2.0"));
    }

    private static View sectionLabel(Activity host, String s) {
        TextView t = Ui.text(host, s.toUpperCase(), 11.5f, Ui.VAR, true);
        t.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(4), 0, 0, Ui.dp(8));
        t.setLayoutParams(lp);
        return t;
    }

    private static View sectionLabel2(Activity host, String s) {
        View v = sectionLabel(host, s);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.topMargin = Ui.dp(14);
        return v;
    }

    private static View groupHeader(Activity host, String s) {
        TextView t = Ui.text(host, s, 13, Ui.VAR, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(20), Ui.dp(16), 0, Ui.dp(6));
        t.setLayoutParams(lp);
        return t;
    }

    private static View groupFooter(Activity host, String s) {
        TextView t = Ui.text(host, s, 12, Ui.DIM, false);
        t.setSingleLine(false);
        t.setEllipsize(null);
        t.setMaxLines(4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(20), Ui.dp(6), Ui.dp(20), 0);
        t.setLayoutParams(lp);
        return t;
    }

    public interface BoolCallback { void accept(boolean value); }

    static View switchRow(Activity host, String icon, String label, String sub, boolean value, BoolCallback cb) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
        row.setBackground(Ui.ripple(Ui.rounded(Color.TRANSPARENT, 10)));
        row.addView(Ui.icon(host, icon, 20, Ui.ON));
        LinearLayout mid = new LinearLayout(host);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mp.leftMargin = Ui.dp(14);
        mid.addView(Ui.text(host, label, 15f, Ui.ON, false));
        if (sub != null) {
            TextView s = Ui.text(host, sub, 12f, Ui.VAR, false);
            s.setSingleLine(false);
            s.setMaxLines(2);
            mid.addView(s);
        }
        row.addView(mid, mp);
        final boolean[] state = {value};
        FrameLayout track = new FrameLayout(host);
        track.setBackground(Ui.rounded(value ? Ui.ON : Ui.S5, 12));
        View thumb = new View(host);
        thumb.setBackground(Ui.oval(value ? Color.BLACK : Ui.VAR));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18));
        tp.gravity = value ? Gravity.END : Gravity.START;
        tp.setMargins(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        track.addView(thumb, tp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(24));
        row.addView(track, tlp);
        row.setOnClickListener(v -> {
            state[0] = !state[0];
            cb.accept(state[0]);
            track.setBackground(Ui.rounded(state[0] ? Ui.ON : Ui.S5, 12));
            thumb.setBackground(Ui.oval(state[0] ? Color.BLACK : Ui.VAR));
            FrameLayout.LayoutParams ntp = new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18));
            ntp.gravity = state[0] ? Gravity.END : Gravity.START;
            ntp.setMargins(Ui.dp(3), Ui.dp(3), Ui.dp(3), Ui.dp(3));
            thumb.setLayoutParams(ntp);
        });
        return row;
    }

    static View valueRow(MainActivity host, String icon, String label, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12));
        row.setBackground(Ui.ripple(Ui.rounded(Color.TRANSPARENT, 10)));
        row.addView(Ui.icon(host, icon, 20, Ui.ON));
        TextView l = Ui.text(host, label, 15f, Ui.ON, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(14);
        row.addView(l, lp);
        row.addView(Ui.text(host, value, 13.5f, Ui.VAR, false));
        if (click != null) row.setOnClickListener(click);
        return row;
    }
}
