package com.anibeat.app.ui.screens;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.AnisongDb;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;
import com.anibeat.app.ui.Cards;
import com.anibeat.app.ui.Format;
import com.anibeat.app.ui.ScreenBase;
import com.anibeat.app.ui.TopBar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Аниме — порт AnimePage из pages/Anime.tsx. */
public class AnimeScreen extends ScreenBase {

    private final String slug;
    private Models.AnimeDetail detail;
    private Meta.ShikiDetails shiki;
    private List<Models.Track> extras = new ArrayList<>();
    private boolean loading = true;
    private String error;
    private String filter = "all";
    private boolean showAllVersions;
    private boolean expanded;
    private boolean extrasLoading;

    private TopBar topBar;
    private LinearLayout bodyHolder;
    private LinearLayout backdropHolder;

    public AnimeScreen(MainActivity activity, String slug) {
        super(activity);
        this.slug = slug;
        load();
    }

    private void load() {
        loading = true;
        Api.getAnime(slug, (detail, error) -> {
            this.loading = false;
            if (detail != null) {
                this.detail = detail;
                List<Integer> ids = new ArrayList<>();
                if (detail.malId != null) ids.add(detail.malId);
                Meta.warm(ids);
                if (detail.malId != null) {
                    Meta.getShikiDetails(detail.malId, (d, e) -> {
                        shiki = d;
                        fillBody();
                        fillBackdrop();
                    });
                    if (Settings.extraSources) loadExtras(detail.malId, detail.name);
                }
            } else {
                this.error = error;
            }
            fillBody();
            fillBackdrop();
        });
    }

    private void loadExtras(Integer malId, String name) {
        extrasLoading = true;
        List<String> names = new ArrayList<>();
        if (name != null && !name.isEmpty()) names.add(name);
        Models.AnimeMeta meta = Meta.get(malId);
        if (meta != null && meta.name != null && !meta.name.isEmpty()) names.add(meta.name);
        AnisongDb.forAnime(malId, names, (list, error) -> {
            extrasLoading = false;
            if (list != null) {
                extras = list;
                fillBody();
            }
        });
    }

    private List<Models.Track> primary() {
        List<Models.Track> out = new ArrayList<>();
        if (detail == null) return out;
        Set<Long> seen = new LinkedHashSet<>();
        for (Models.Track t : detail.tracks) {
            if (seen.contains(t.themeId)) continue;
            seen.add(t.themeId);
            out.add(t);
        }
        return out;
    }

    private List<Models.Track> extraTracks() {
        if (extras.isEmpty() || detail == null) return new ArrayList<>();
        Set<String> known = new LinkedHashSet<>();
        for (Models.Track t : primary()) known.add(t.type + (t.sequence == null ? "" : t.sequence));
        List<Models.Track> out = new ArrayList<>();
        for (Models.Track t : extras) {
            if ("IN".equals(t.type) || !known.contains(t.type + (t.sequence == null ? "" : t.sequence))) {
                Models.Track copy = t;
                copy.cover = detail.cover;
                copy.coverSmall = detail.coverSmall;
                if (copy.anime != null) {
                    copy.anime.slug = detail.slug;
                    copy.anime.name = detail.name;
                    copy.anime.malId = detail.malId;
                }
                out.add(copy);
            }
        }
        return out;
    }

    private List<Models.Track> all() {
        List<Models.Track> out = new ArrayList<>();
        if (detail != null) {
            if (showAllVersions) out.addAll(detail.tracks);
            else out.addAll(primary());
        }
        out.addAll(extraTracks());
        return out;
    }

    private List<Models.Track> visible() {
        List<Models.Track> items = all();
        if ("all".equals(filter)) return items;
        List<Models.Track> out = new ArrayList<>();
        for (Models.Track t : items) if (filter.equals(t.type)) out.add(t);
        return out;
    }

    /* ------------------------------------------------------------------ */

    @Override
    protected View build() {
        Context c = ctx();
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Theme.BG);

        backdropHolder = Ui.column(c);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340));
        root.addView(backdropHolder, bp);

        LinearLayout content = Ui.column(c);
        topBar = new TopBar(activity, null, true, false, true);
        content.addView(topBar);

        bodyHolder = Ui.column(c);
        bodyHolder.setPadding(0, 0, 0, dp(24));
        content.addView(scroller(bodyHolder), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content);
        fillBody();
        fillBackdrop();
        return root;
    }

    @Override
    public void rebuild() {
        if (bodyHolder == null) {
            super.rebuild();
            return;
        }
        fillBody();
    }

    private void fillBackdrop() {
        if (backdropHolder == null) return;
        Context c = ctx();
        backdropHolder.removeAllViews();
        String image = null;
        if (detail != null) {
            Models.AnimeMeta meta = Meta.get(detail.malId);
            image = meta != null && meta.banner != null ? meta.banner : detail.cover;
        }
        if (image == null) return;

        FrameLayout holder = new FrameLayout(c);
        CoverView banner = new CoverView(c);
        banner.setRadiusDp(0f);
        banner.setIconSizeDp(40f);
        banner.setUrl(image, detail == null ? null : detail.cover);
        banner.setAlpha(0.35f);
        holder.addView(banner, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));

        View shade = new View(c);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0x66000000, 0xE6000000, 0xFF000000});
        shade.setBackground(gradient);
        holder.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));
        backdropHolder.addView(holder);
    }

    private void fillBody() {
        if (bodyHolder == null) return;
        Context c = ctx();
        bodyHolder.removeAllViews();

        if (loading && detail == null) {
            LinearLayout skeleton = Ui.column(c);
            skeleton.setGravity(Gravity.CENTER_HORIZONTAL);
            skeleton.addView(Ui.skeleton(c, dp(132), dp(190), 12f), Ui.lp(dp(132), dp(190)));
            skeleton.addView(Ui.skeleton(c, dp(200), dp(22), 8f), Ui.lp(dp(200), dp(22)));
            bodyHolder.addView(skeleton);
            return;
        }
        if (detail == null) {
            bodyHolder.addView(Ui.errorState(c, error == null ? "Не найдено" : error, this::load));
            return;
        }

        Models.AnimeMeta meta = Meta.get(detail.malId);
        String ru = Settings.ruTitles && shiki != null && shiki.ru != null ? shiki.ru : (Settings.ruTitles && meta != null ? meta.ru : null);
        String name = detail.name != null && !detail.name.isEmpty() ? detail.name : (shiki != null ? shiki.name : "");
        String title = ru != null && !ru.isEmpty() ? ru : name;
        String poster = Settings.dataSaver ? (detail.coverSmall != null ? detail.coverSmall : detail.cover)
                : (fix(meta == null ? null : meta.poster) != null ? fix(meta.poster) : (shiki != null && shiki.poster != null ? shiki.poster : (detail.cover != null ? detail.cover : detail.coverSmall)));
        Double score = shiki != null && shiki.score != null ? shiki.score : (meta == null ? null : meta.score);
        String description = Settings.ruTitles && shiki != null && shiki.description != null ? shiki.description
                : (detail.synopsis != null ? Meta.cleanShikiText(detail.synopsis.replace("[Written by MAL Rewrite]", "")) : (shiki == null ? null : shiki.description));

        LinearLayout head = Ui.column(c);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setPadding(dp(16), 0, dp(16), 0);

        CoverView cover = new CoverView(c);
        cover.setRadiusDp(12f);
        cover.setIconSizeDp(44f);
        cover.setUrl(poster, detail.cover);
        head.addView(cover, Ui.lp(dp(132), dp(190)));

        TextView titleView = Ui.heading(c, title, 26f, Theme.ON);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(14);
        head.addView(titleView, tp);
        if (ru != null && !ru.equals(name) && !name.isEmpty()) {
            TextView original = Ui.text(c, name, 14f, Theme.ON_VARIANT);
            original.setGravity(Gravity.CENTER);
            head.addView(original);
        }

        LinearLayout metaRow = Ui.row(c);
        metaRow.setGravity(Gravity.CENTER);
        if (score != null && score > 0) {
            LinearLayout scoreBox = Ui.row(c);
            scoreBox.setGravity(Gravity.CENTER);
            scoreBox.addView(Ui.icon(c, "star_rate", 13, 0xFFFF9F0A));
            TextView scoreText = Ui.text(c, String.format(java.util.Locale.US, "%.2f", score), 13f, Theme.ON, true);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.leftMargin = dp(4);
            scoreBox.addView(scoreText, sp);
            metaRow.addView(scoreBox);
        }
        String kind = shiki != null && shiki.kind != null ? Meta.KIND_RU.getOrDefault(shiki.kind, shiki.kind.toUpperCase()) : detail.mediaFormat;
        if (kind != null && !kind.isEmpty()) metaRow.addView(metaText(c, kind));
        String season = Api.seasonLabel(detail.season, detail.year);
        if (!season.isEmpty()) metaRow.addView(metaText(c, season));
        if (shiki != null && shiki.episodes != null && shiki.episodes > 0) metaRow.addView(metaText(c, Format.plural(shiki.episodes, "эпизод", "эпизода", "эпизодов")));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = dp(6);
        head.addView(metaRow, mp);

        if (shiki != null && !shiki.genres.isEmpty()) {
            TextView genres = Ui.text(c, String.join(" · ", shiki.genres), 12.5f, Theme.ON_DIM);
            genres.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            gp.topMargin = dp(6);
            head.addView(genres, gp);
        }

        List<Models.Track> all = all();
        LinearLayout actions = Ui.row(c);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(16);
        if (all.isEmpty()) actions.setAlpha(0.4f);
        LinearLayout play = actionButton(c, "play_arrow", "Слушать", () -> {
            if (all.isEmpty()) return;
            Player.playTracks(all, 0, false);
            activity.nowPlaying().open();
        }, true);
        LinearLayout shuffle = actionButton(c, "shuffle", "Вперемешку", () -> {
            if (all.isEmpty()) return;
            Player.playTracks(all, 0, true);
            activity.nowPlaying().open();
        }, true);
        actions.addView(play);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.leftMargin = dp(8);
        actions.addView(shuffle, sp2);
        FrameLayout save = Ui.iconButton(c, "cloud_download", 19, Theme.PRIMARY, () -> {
            for (Models.Track t : all) Downloads.download(t, Downloads.KIND_AUDIO, false);
            activity.toaster().show("Сохраняем " + Format.plural(all.size(), "трек", "трека", "треков"));
        });
        save.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
        LinearLayout.LayoutParams savp = Ui.lp(dp(40), dp(40));
        savp.leftMargin = dp(8);
        save.setLayoutParams(savp);
        actions.addView(save);
        head.addView(actions, ap);
        bodyHolder.addView(head);

        if (description != null && !description.isEmpty()) {
            LinearLayout descBox = Ui.column(c);
            descBox.setPadding(dp(16), dp(18), dp(16), 0);
            TextView desc = Ui.text(c, description, 15f, Theme.ON_VARIANT);
            desc.setLineSpacing(dp(4), 1.15f);
            desc.setMaxLines(expanded ? Integer.MAX_VALUE : 3);
            desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            descBox.addView(desc);
            TextView more = Ui.text(c, expanded ? "Свернуть" : "Ещё", 14f, Theme.PRIMARY, true);
            more.setPadding(0, dp(6), 0, 0);
            more.setOnClickListener(v -> {
                expanded = !expanded;
                fillBody();
            });
            Ui.tap(more);
            descBox.addView(more);
            bodyHolder.addView(descBox);
        }

        // Фильтры по типу темы
        LinearLayout filterRow = Ui.row(c);
        filterRow.setPadding(dp(16), dp(18), dp(16), dp(6));
        long countOP = count("OP", all);
        long countED = count("ED", all);
        long countIN = count("IN", all);
        filterRow.addView(Ui.chip(c, "Все", null, "all".equals(filter), () -> {
            filter = "all";
            fillBody();
        }));
        if (countOP > 0) filterRow.addView(spacedChip(Ui.chip(c, "OP · " + countOP, null, "OP".equals(filter), () -> {
            filter = "OP";
            fillBody();
        })));
        if (countED > 0) filterRow.addView(spacedChip(Ui.chip(c, "ED · " + countED, null, "ED".equals(filter), () -> {
            filter = "ED";
            fillBody();
        })));
        if (countIN > 0) filterRow.addView(spacedChip(Ui.chip(c, "Вставки · " + countIN, null, "IN".equals(filter), () -> {
            filter = "IN";
            fillBody();
        })));
        if (detail.tracks.size() > primary().size()) {
            TextView versions = Ui.text(c, showAllVersions ? "Скрыть" : "Версии", 14f, Theme.PRIMARY, true);
            versions.setPadding(dp(10), dp(8), 0, dp(8));
            versions.setOnClickListener(v -> {
                showAllVersions = !showAllVersions;
                fillBody();
            });
            Ui.tap(versions);
            filterRow.addView(versions);
        }
        bodyHolder.addView(hscroll(filterRow));

        List<Models.Track> visible = visible();
        if (visible.isEmpty() && !extrasLoading) {
            bodyHolder.addView(Ui.emptyState(c, "music_note", "Треков пока нет", "Для этого аниме ещё нет записей.", null, null));
        } else {
            LinearLayout list = Ui.column(c);
            list.setPadding(dp(16), 0, 0, 0);
            for (Models.Track t : visible) list.addView(Cards.trackRow(activity, t, visible, false, true, false, null, null));
            bodyHolder.addView(list);
        }
        if (extrasLoading) bodyHolder.addView(Cards.trackRowSkeleton(c, 2));

        if (!detail.studios.isEmpty() || !detail.series.isEmpty()) {
            LinearLayout foot = Ui.column(c);
            foot.setPadding(dp(16), dp(18), dp(16), 0);
            if (!detail.studios.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String[] s : detail.studios) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s[0]);
                }
                foot.addView(Ui.text(c, sb.toString(), 12.5f, Theme.ON_DIM));
            }
            if (!detail.series.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String[] s : detail.series) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(s[0]);
                }
                foot.addView(Ui.text(c, sb.toString(), 12.5f, Theme.ON_DIM));
            }
            bodyHolder.addView(foot);
        }
    }

    private static String fix(String url) {
        return url == null ? null : Meta.fixShikiHost(url);
    }

    private static long count(String type, List<Models.Track> tracks) {
        long n = 0;
        for (Models.Track t : tracks) if (type.equals(t.type)) n++;
        return n;
    }

    private static TextView metaText(Context c, String value) {
        TextView tv = Ui.text(c, value, 13f, Theme.ON_VARIANT);
        tv.setPadding(Theme.dp(c, 8), 0, 0, 0);
        return tv;
    }

    private static LinearLayout actionButton(Context c, String icon, String label, Runnable click, boolean enabled) {
        LinearLayout box = Ui.row(c);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 11f)));
        box.setPadding(Theme.dp(c, 18), Theme.dp(c, 11), Theme.dp(c, 18), Theme.dp(c, 11));
        box.addView(Ui.icon(c, icon, 18, Theme.PRIMARY));
        TextView tv = Ui.text(c, label, 15f, Theme.PRIMARY, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Theme.dp(c, 6);
        box.addView(tv, p);
        box.setOnClickListener(v -> click.run());
        Ui.tapScale(box);
        if (!enabled) box.setAlpha(0.4f);
        return box;
    }

    private LinearLayout spacedChip(TextView chip) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(8);
        chip.setLayoutParams(p);
        LinearLayout holder = Ui.row(ctx());
        holder.addView(chip);
        return holder;
    }

    @Override
    public String title() {
        return detail == null ? "" : detail.name;
    }
}
