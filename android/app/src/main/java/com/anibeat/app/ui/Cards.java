package com.anibeat.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anibeat.app.MainActivity;
import com.anibeat.app.core.BarsView;
import com.anibeat.app.core.CoverView;
import com.anibeat.app.core.Theme;
import com.anibeat.app.core.Spinner;
import com.anibeat.app.core.Ui;
import com.anibeat.app.data.Api;
import com.anibeat.app.data.Downloads;
import com.anibeat.app.data.Library;
import com.anibeat.app.data.Meta;
import com.anibeat.app.data.Models;
import com.anibeat.app.data.Settings;
import com.anibeat.app.player.Player;

import java.util.List;

/** Карточки треков/аниме/исполнителей/миксов — перенос components/cards.tsx. */
public final class Cards {

    /** Живые ссылки на уже построенные строки — обновляем их без перерисовки экрана. */
    private static final class RowRef {
        final Models.Track track;
        final View overlay;
        final TextView title;
        final BarsView bars;

        RowRef(Models.Track track, View overlay, TextView title, BarsView bars) {
            this.track = track;
            this.overlay = overlay;
            this.title = title;
            this.bars = bars;
        }
    }

    private static final java.util.List<RowRef> ROWS = new java.util.ArrayList<>();

    /** Подсветка активного трека без пересборки экрана (быстро и без рывков). */
    public static void refreshPlaybackIndicators() {
        try {
            refreshIndicators();
        } catch (Throwable t) {
            Ui.report(t);
        }
    }

    private static void refreshIndicators() {
        Models.Track current = Player.current();
        boolean playing = Player.isPlaying();
        for (int i = ROWS.size() - 1; i >= 0; i--) {
            RowRef ref = ROWS.get(i);
            if (ref.overlay == null || !ref.overlay.isAttachedToWindow()) {
                ROWS.remove(i);
                continue;
            }
            boolean active = current != null && current.id.equals(ref.track.id);
            ref.overlay.setVisibility(active ? View.VISIBLE : View.GONE);
            if (ref.bars != null) ref.bars.setPaused(!playing);
            if (ref.title != null) ref.title.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    public interface TrackAction {
        void on(Models.Track track);
    }

    private Cards() {
    }

    /* ------------------------------------------------------------------ */
    /* TrackRow                                                            */
    /* ------------------------------------------------------------------ */

    public static View trackRow(MainActivity a, Models.Track track, List<Models.Track> context,
                               boolean showAnime, boolean showVersion, boolean dense,
                               Runnable onRemove, View trailing) {
        Context c = a;
        LinearLayout row = Ui.row(c);
        row.setPadding(Theme.dp(c, dense ? 16 : 16), Theme.dp(c, dense ? 6 : 7), Theme.dp(c, 4), Theme.dp(c, dense ? 6 : 7));
        row.setBackgroundColor(Theme.BG);
        row.setOnClickListener(v -> {
            Player.playTrack(track, context);
            a.updateBars();
        });
        row.setOnLongClickListener(v -> {
            a.sheets().openTrackMenu(track);
            return true;
        });

        int coverSize = Theme.dp(c, dense ? 44 : 50);
        FrameLayout coverBox = new FrameLayout(c);
        CoverView cover = new CoverView(c);
        cover.setRadiusDp(10f);
        cover.setIconSizeDp(20f);
        Display d = Display.track(track);
        cover.setUrl(d.thumb != null ? d.thumb : d.cover, d.cover);
        coverBox.addView(cover, new FrameLayout.LayoutParams(coverSize, coverSize));

        boolean active = Player.current() != null && Player.current().id.equals(track.id);
        FrameLayout overlay = new FrameLayout(c);
        overlay.setBackground(Ui.rounded(0x8C000000, Theme.dpF(c, 10f)));
        BarsView bars = new BarsView(c);
        bars.setPaused(!Player.isPlaying());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Theme.dp(c, 15), Theme.dp(c, 15));
        bp.gravity = Gravity.CENTER;
        overlay.addView(bars, bp);
        overlay.setVisibility(active ? View.VISIBLE : View.GONE);
        coverBox.addView(overlay, new FrameLayout.LayoutParams(coverSize, coverSize));
        row.addView(coverBox, Ui.lp(coverSize, coverSize));

        LinearLayout info = Ui.row(c);
        LinearLayout.LayoutParams ip = Ui.lpw(1f);
        ip.leftMargin = Theme.dp(c, 12);
        row.addView(info, ip);

        LinearLayout texts = Ui.column(c);
        info.addView(texts, Ui.lpw(1f));

        LinearLayout titleRow = Ui.row(c);
        TextView title = Ui.text(c, track.title, 15.5f, Theme.ON, active);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // регистрируем строку, чтобы позже обновлять подсветку без перерисовки экрана
        ROWS.add(new RowRef(track, overlay, title, bars));
        titleRow.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (track.nsfw) {
            TextView tag = Ui.tag(c, "18+", "warn");
            LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Theme.dp(c, 15));
            tp.leftMargin = Theme.dp(c, 8);
            titleRow.addView(tag, tp);
        }
        texts.addView(titleRow);

        LinearLayout meta = Ui.row(c);
        String label = track.themeSlug + (showVersion && track.version != null && track.version > 1 ? " v" + track.version : "");
        TextView themeTag = Ui.tag(c, label, track.type);
        meta.addView(themeTag);
        if (Downloads.hasOffline(track.id, Downloads.KIND_AUDIO)) {
            ImageView pin = Ui.icon(c, "offline_pin", 13, Theme.TERTIARY);
            LinearLayout.LayoutParams pp = Ui.lp(Theme.dp(c, 13), Theme.dp(c, 13));
            pp.leftMargin = Theme.dp(c, 6);
            meta.addView(pin, pp);
        }
        String subtitle = track.artistNames() + (showAnime && d.title != null && !d.title.isEmpty() ? " · " + d.title : "");
        TextView sub = Ui.text(c, subtitle, 13f, Theme.ON_VARIANT);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = Theme.dp(c, 6);
        meta.addView(sub, sp);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Theme.dp(c, 4);
        texts.addView(meta, mp);

        if (Library.isFavorite(track.id)) {
            ImageView fav = Ui.icon(c, "favorite", 14, Theme.ON_VARIANT);
            LinearLayout.LayoutParams fp = Ui.lp(Theme.dp(c, 14), Theme.dp(c, 14));
            fp.leftMargin = Theme.dp(c, 8);
            info.addView(fav, fp);
        }
        if (trailing != null) {
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.leftMargin = Theme.dp(c, 8);
            info.addView(trailing, tp);
        }

        FrameLayout more = Ui.iconButton(c, onRemove != null ? "close" : "more_horiz", onRemove != null ? 17 : 20, Theme.ON_DIM, null);
        more.setOnClickListener(v -> {
            if (onRemove != null) onRemove.run();
            else a.sheets().openTrackMenu(track);
        });
        Ui.tapScale(more);
        info.addView(more);

        coverBox.setOnClickListener(v -> {
            Player.playTrack(track, context);
            a.updateBars();
        });
        return row;
    }

    /** Обновляет индикатор воспроизведения без пересборки списка. */
    public static void refreshTrackRowIndicators(View row, Models.Track track) {
        if (row instanceof ViewGroup && ((ViewGroup) row).getChildCount() > 0 && ((ViewGroup) row).getChildAt(0) instanceof FrameLayout) {
            FrameLayout box = (FrameLayout) ((ViewGroup) row).getChildAt(0);
            if (box.getChildCount() > 1) {
                View overlay = box.getChildAt(1);
                boolean active = Player.current() != null && Player.current().id.equals(track.id);
                overlay.setVisibility(active ? View.VISIBLE : View.GONE);
                if (overlay instanceof FrameLayout) {
                    View bars = ((FrameLayout) overlay).getChildAt(0);
                    if (bars instanceof BarsView) ((BarsView) bars).setPaused(!Player.isPlaying());
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* TrackCard                                                           */
    /* ------------------------------------------------------------------ */

    public static View trackCard(MainActivity a, Models.Track track, List<Models.Track> context) {
        Context c = a;
        int width = Theme.dp(c, 138);
        LinearLayout card = Ui.column(c);
        card.setLayoutParams(Ui.lp(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout art = new FrameLayout(c);
        CoverView cover = new CoverView(c);
        cover.setRadiusDp(14f);
        cover.setIconSizeDp(40f);
        Display d = Display.track(track);
        cover.setUrl(d.cover, d.thumb);
        art.addView(cover, new FrameLayout.LayoutParams(width, width));

        FrameLayout overlay = new FrameLayout(c);
        overlay.setBackground(Ui.rounded(0x73000000, Theme.dpF(c, 14f)));
        boolean active = Player.current() != null && Player.current().id.equals(track.id);
        FrameLayout badge = new FrameLayout(c);
        badge.setBackground(Ui.rounded(Theme.ON, width / 2f));
        BarsView bars = new BarsView(c);
        bars.setColor(0xFF000000);
        bars.setPaused(!Player.isPlaying());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Theme.dp(c, 15), Theme.dp(c, 15));
        bp.gravity = Gravity.CENTER;
        badge.addView(bars, bp);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Theme.dp(c, 48), Theme.dp(c, 48));
        cp.gravity = Gravity.CENTER;
        overlay.addView(badge, cp);
        overlay.setVisibility(active ? View.VISIBLE : View.GONE);
        art.addView(overlay, new FrameLayout.LayoutParams(width, width));
        art.setOnClickListener(v -> {
            Player.playTrack(track, context);
            a.updateBars();
        });
        art.setOnLongClickListener(v -> {
            a.sheets().openTrackMenu(track);
            return true;
        });
        card.addView(art);

        TextView title = Ui.text(c, track.title, 14f, Theme.ON, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // регистрируем строку, чтобы позже обновлять подсветку без перерисовки экрана
        ROWS.add(new RowRef(track, overlay, title, bars));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Theme.dp(c, 8);
        card.addView(title, tp);

        String sub = track.themeSlug + " · " + (d.title == null ? "" : d.title);
        TextView subtitle = Ui.text(c, sub, 12.5f, Theme.ON_VARIANT);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Theme.dp(c, 2);
        card.addView(subtitle, sp);
        return card;
    }

    /* ------------------------------------------------------------------ */
    /* AnimeCard                                                           */
    /* ------------------------------------------------------------------ */

    public static View animeCard(MainActivity a, Models.AnimeSummary anime, boolean wide) {
        Context c = a;
        int width = wide ? ViewGroup.LayoutParams.MATCH_PARENT : Theme.dp(c, 112);
        LinearLayout card = Ui.column(c);
        card.setLayoutParams(Ui.lp(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        Display d = Display.summary(anime);
        FrameLayout art = new FrameLayout(c);
        CoverView cover = new CoverView(c);
        cover.setRadiusDp(12f);
        cover.setIconSizeDp(32f);
        cover.setUrl(d.cover, d.thumb);
        cover.setAspect(2f / 3f);
        art.addView(cover, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (d.score != null && d.score > 0) {
            TextView score = Ui.text(c, String.format(java.util.Locale.US, "%.1f", d.score), 10.5f, Theme.ON, true);
            score.setBackground(Ui.rounded(0xB3000000, Theme.dpF(c, 6f)));
            score.setPadding(Theme.dp(c, 6), Theme.dp(c, 2), Theme.dp(c, 6), Theme.dp(c, 3));
            FrameLayout.LayoutParams scp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            scp.gravity = Gravity.TOP | Gravity.START;
            scp.leftMargin = Theme.dp(c, 6);
            scp.topMargin = Theme.dp(c, 6);
            art.addView(score, scp);
        }
        card.addView(art);

        TextView title = Ui.text(c, d.title == null ? "" : d.title, 13.5f, Theme.ON, true);
        title.setSingleLine(false);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Theme.dp(c, 8);
        card.addView(title, tp);

        if (anime.year != null && anime.year > 0) {
            TextView year = Ui.text(c, String.valueOf(anime.year), 12f, Theme.ON_DIM);
            LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            yp.topMargin = Theme.dp(c, 4);
            card.addView(year, yp);
        }
        card.setOnClickListener(v -> a.openAnime(anime.slug));
        Ui.tapScale(card);
        return card;
    }

    /* ------------------------------------------------------------------ */
    /* ArtistCard                                                          */
    /* ------------------------------------------------------------------ */

    public static View artistCard(MainActivity a, Models.ArtistSummary artist) {
        Context c = a;
        int size = Theme.dp(c, 84);
        LinearLayout card = Ui.column(c);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setLayoutParams(Ui.lp(Theme.dp(c, 92), ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout circle = new FrameLayout(c);
        if ((artist.image != null && !artist.image.isEmpty()) || (artist.imageSmall != null && !artist.imageSmall.isEmpty())) {
            CoverView cover = new CoverView(c);
            cover.setRadiusDp(42f);
            cover.setIconSizeDp(30f);
            cover.setUrl(artist.image != null ? artist.image : artist.imageSmall, artist.imageSmall);
            circle.addView(cover, new FrameLayout.LayoutParams(size, size));
        } else {
            FrameLayout holder = new FrameLayout(c);
            holder.setBackground(Ui.rounded(Theme.SURFACE_2, size / 2f));
            ImageView mic = Ui.icon(c, "mic", 26, Theme.ON_DIM);
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(Theme.dp(c, 26), Theme.dp(c, 26));
            mp.gravity = Gravity.CENTER;
            holder.addView(mic, mp);
            circle.addView(holder, new FrameLayout.LayoutParams(size, size));
        }
        card.addView(circle);

        TextView name = Ui.text(c, artist.name, 13f, Theme.ON, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = Theme.dp(c, 8);
        card.addView(name, np);
        card.setOnClickListener(v -> a.openArtist(artist.slug));
        Ui.tapScale(card);
        return card;
    }

    /* ------------------------------------------------------------------ */
    /* MixCard                                                             */
    /* ------------------------------------------------------------------ */

    public static View mixCard(MainActivity a, Models.Mix mix, Runnable onPlay, boolean loading) {
        Context c = a;
        int w = Theme.dp(c, 196);
        int h = Theme.dp(c, 108);
        FrameLayout card = new FrameLayout(c);
        card.setLayoutParams(Ui.lp(w, h));
        card.setBackground(Ui.rounded(Theme.SURFACE_2, Theme.dpF(c, 16f)));

        ImageView spark = Ui.icon(c, "auto_awesome", 18, Theme.ON_DIM);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Theme.dp(c, 18), Theme.dp(c, 18));
        sp.leftMargin = Theme.dp(c, 16);
        sp.topMargin = Theme.dp(c, 16);
        card.addView(spark, sp);

        LinearLayout texts = Ui.column(c);
        TextView title = Ui.text(c, mix.title, 15f, Theme.ON, true);
        TextView subtitle = Ui.text(c, mix.subtitle, 12f, Theme.ON_VARIANT);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        texts.addView(subtitle);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.BOTTOM;
        tp.leftMargin = Theme.dp(c, 16);
        tp.rightMargin = Theme.dp(c, 48);
        tp.bottomMargin = Theme.dp(c, 14);
        card.addView(texts, tp);

        FrameLayout play = new FrameLayout(c);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Theme.dp(c, 36), Theme.dp(c, 36));
        pp.gravity = Gravity.BOTTOM | Gravity.END;
        pp.rightMargin = Theme.dp(c, 14);
        pp.bottomMargin = Theme.dp(c, 12);
        if (loading) {
            Spinner spinner = new Spinner(c);
            spinner.setColors(0x33FFFFFF, Theme.ON);
            FrameLayout.LayoutParams sclp = new FrameLayout.LayoutParams(Theme.dp(c, 18), Theme.dp(c, 18));
            sclp.gravity = Gravity.CENTER;
            play.addView(spinner, sclp);
            spinner.start();
            card.addView(play, pp);
            return card;
        }
        play.setBackground(Ui.rounded(Theme.ON, Theme.dpF(c, 18f)));
        ImageView playIcon = Ui.icon(c, "play_arrow", 18, 0xFF000000);
        FrameLayout.LayoutParams pip = new FrameLayout.LayoutParams(Theme.dp(c, 18), Theme.dp(c, 18));
        pip.gravity = Gravity.CENTER;
        play.addView(playIcon, pip);
        card.addView(play, pp);

        card.setOnClickListener(v -> onPlay.run());
        Ui.tapScale(card);
        return card;
    }

    /* ------------------------------------------------------------------ */
    /* Скелетоны и секции                                                  */
    /* ------------------------------------------------------------------ */

    public static View trackRowSkeleton(Context c, int count) {
        LinearLayout box = Ui.column(c);
        for (int i = 0; i < count; i++) {
            LinearLayout row = Ui.row(c);
            row.setPadding(Theme.dp(c, 16), Theme.dp(c, 8), Theme.dp(c, 16), Theme.dp(c, 8));
            View cover = Ui.skeleton(c, Theme.dp(c, 52), Theme.dp(c, 52), 8f);
            row.addView(cover);
            LinearLayout col = Ui.column(c);
            col.addView(Ui.skeleton(c, 0, Theme.dp(c, 14), 6f), repeatWidth(c));
            col.addView(Ui.skeleton(c, 0, Theme.dp(c, 12), 6f), repeatWidthHalf(c));
            LinearLayout.LayoutParams cp = Ui.lpw(1f);
            cp.leftMargin = Theme.dp(c, 12);
            row.addView(col, cp);
            box.addView(row);
        }
        return box;
    }

    private static LinearLayout.LayoutParams repeatWidth(Context c) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 14));
    }

    private static LinearLayout.LayoutParams repeatWidthHalf(Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(c, 12));
        p.topMargin = Theme.dp(c, 8);
        return p;
    }

    /** Заголовок секции (SectionHeader). */
    public static LinearLayout sectionHeader(Context c, String title, String action, Ui.Click onAction) {
        return Ui.sectionHeader(c, title, action, onAction);
    }

    static Typeface semiBold() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** Обновить мета-информацию во всех строках после загрузки метаданных. */
    public static void warmTracks(List<Models.Track> tracks) {
        if (tracks == null) return;
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (Models.Track t : tracks) if (t.anime != null && t.anime.malId != null) ids.add(t.anime.malId);
        Meta.warm(ids);
    }

    public static String seasonLabel(Integer year, String season) {
        return Api.seasonLabel(season, year);
    }

    /** Миниатюра обложки для меню трека и списков. */
    public static View cover(Context c, String url, String fallback, int sizeDp, float radiusDp) {
        CoverView cover = new CoverView(c);
        cover.setRadiusDp(radiusDp);
        cover.setIconSizeDp(sizeDp / 3f);
        cover.setUrl(url, fallback);
        cover.setLayoutParams(Ui.lp(Theme.dp(c, sizeDp), Theme.dp(c, sizeDp)));
        return cover;
    }
}
