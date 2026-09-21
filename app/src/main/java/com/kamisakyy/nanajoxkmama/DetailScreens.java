package com.kamisakyy.nanajoxkmama;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detail pages — ports of pages/Anime.tsx (poster, score, genres, description,
 * OP/ED/IN filters, versions) and ArtistPage (sort + tracks).
 */
public final class DetailScreens {
    private DetailScreens() { }

    /* ================= Anime ================= */

    public static class AnimeScreen implements Screen {
        private final Ui.Host host;
        private final Activity a;
        private final LinearLayout root;
        private final LinearLayout content;
        private final String slug;
        private AnimeInfo.Detail data;
        private MetaApi.ShikiDetails shiki;
        private String filter = "all";
        private boolean showAllVersions;
        private boolean expanded;
        private final ArrayList<Track> extras = new ArrayList<>();

        public AnimeScreen(Ui.Host host, String slug) {
            this.host = host;
            this.a = host.activity();
            this.slug = slug;
            root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Ui.BG);
            content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(0, 0, 0, Ui.dp(28));
            ScrollView scroll = new ScrollView(a);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            load();
        }

        private void load() {
            content.removeAllViews();
            content.addView(headerSkeleton());
            for (int i = 0; i < 5; i++) content.addView(rowSkeleton());
            host.runIo(() -> {
                try {
                    AnimeInfo.Detail d = ApiClient.anime(slug);
                    MetaApi.ShikiDetails sd = null;
                    if (d.malId > 0) {
                        try {
                            MetaApi.AnimeMeta meta = MetaApi.getMeta(d.malId);
                            d.applyMeta(meta);
                            sd = MetaApi.getShikiDetails(d.malId);
                            if (sd != null) {
                                if (d.ruName.isEmpty()) d.ruName = sd.ru;
                                if (d.score < 0) d.score = sd.score;
                                if (d.genres.isEmpty()) d.genres = sd.genres;
                                if (d.episodes < 0) d.episodes = sd.episodes;
                                if (!sd.poster.isEmpty() && d.cover.isEmpty()) d.cover = sd.poster;
                            }
                        } catch (Exception ignored) { }
                    }
                    final AnimeInfo.Detail fd = d;
                    final MetaApi.ShikiDetails fs = sd;
                    a.runOnUiThread(() -> {
                        data = fd;
                        shiki = fs;
                        render();
                    });
                    // extended base extras
                    if (Store.isExtraSourcesEnabled()) {
                        try {
                            ArrayList<Track> extra = ApiClient.anisongsForAnime(d.malId, d.name);
                            Set<String> seen = new HashSet<>();
                            for (Track t : d.tracks) seen.add(t.type + ":" + t.sequence);
                            ArrayList<Track> merged = new ArrayList<>();
                            for (Track t : extra) {
                                if ("IN".equals(t.type) || !seen.contains(t.type + ":" + t.sequence)) {
                                    t.animeName = d.name;
                                    t.animeSlug = d.slug;
                                    t.cover = d.cover;
                                    t.coverSmall = d.coverSmall;
                                    t.malId = d.malId;
                                    merged.add(t);
                                }
                            }
                            if (!merged.isEmpty()) {
                                extras.addAll(merged);
                                a.runOnUiThread(() -> {
                                    if (data != null) render();
                                });
                            }
                        } catch (Exception ignored) { }
                    }
                } catch (Exception e) {
                    a.runOnUiThread(() -> {
                        content.removeAllViews();
                        content.addView(Ui.errorState(a, e.getMessage(), v -> load()));
                    });
                }
            });
        }

        private void render() {
            content.removeAllViews();
            if (data == null) return;
            String title = data.displayTitle();
            String original = !data.ruName.isEmpty() && !data.ruName.equals(data.name) ? data.name : "";

            // header: poster + meta
            LinearLayout header = new LinearLayout(a);
            header.setOrientation(LinearLayout.VERTICAL);
            header.setGravity(Gravity.CENTER_HORIZONTAL);
            header.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), 0);
            Ui.CoverView poster = Ui.cover(a, 132, 12);
            poster.load(Store.isDataSaverEnabled() && !data.coverSmall.isEmpty() ? data.coverSmall : data.cover);
            header.addView(poster);
            TextView t1 = Ui.text(a, title, 24, Ui.ON, true);
            t1.setMaxLines(3);
            t1.setSingleLine(false);
            t1.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p1.topMargin = Ui.dp(12);
            header.addView(t1, p1);
            if (!original.isEmpty()) {
                TextView t2 = Ui.text(a, original, 13.5f, Ui.VAR, false);
                t2.setGravity(Gravity.CENTER_HORIZONTAL);
                header.addView(t2);
            }
            // meta line: score · kind · season · episodes
            LinearLayout meta = new LinearLayout(a);
            meta.setOrientation(LinearLayout.HORIZONTAL);
            meta.setGravity(Gravity.CENTER);
            if (data.score > 0) {
                meta.addView(Ui.icon(a, "star_rate", 13, Ui.STAR));
                TextView s = Ui.text(a, String.format(java.util.Locale.US, "%.2f", data.score), 13, Ui.ON, true);
                s.setPadding(Ui.dp(2), 0, Ui.dp(8), 0);
                meta.addView(s);
            }
            String kind = !data.kind.isEmpty()
                    ? (MetaApi.KIND_RU.containsKey(data.kind) ? MetaApi.KIND_RU.get(data.kind) : data.kind.toUpperCase(java.util.Locale.US))
                    : (data.format.isEmpty() ? "" : data.format);
            String season = Util.seasonLabel(data.season, data.year);
            String eps = data.episodes > 0 ? Util.pluralRu(data.episodes, "эпизод", "эпизода", "эпизодов") : "";
            String metaLine = join(" · ", kind, season, eps);
            if (!metaLine.isEmpty()) {
                TextView m = Ui.text(a, metaLine, 13, Ui.VAR, false);
                meta.addView(m);
            }
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mp.topMargin = Ui.dp(6);
            header.addView(meta, mp);
            if (!data.genres.isEmpty()) {
                TextView g = Ui.text(a, data.genres, 12f, Ui.DIM, false);
                g.setSingleLine(true);
                g.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gp.topMargin = Ui.dp(3);
                header.addView(g, gp);
            }
            content.addView(header);

            // actions
            LinearLayout actions = new LinearLayout(a);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), 0);
            View play = Ui.surfaceButton(a, "Слушать", "play_arrow", v -> playAll(false));
            actions.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(40), 1f));
            View shuffle = Ui.surfaceButton(a, "Вперемешку", "shuffle", v -> playAll(true));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(40), 1f);
            sp.leftMargin = Ui.dp(8);
            actions.addView(shuffle, sp);
            View save = Ui.iconBtn(a, "download", 19, Ui.ON, v -> {
                ArrayList<Track> all = visible();
                for (Track t : all) Downloader.download(t, false, false);
                host.toast("Сохраняем " + Util.pluralRu(all.size(), "трек", "трека", "треков"));
            });
            save.setBackground(Ui.ripple(Ui.rounded(Ui.S2, 12)));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40));
            dp.leftMargin = Ui.dp(8);
            actions.addView(save, dp);
            content.addView(actions);

            // description
            String desc = shiki != null && !shiki.description.isEmpty() ? shiki.description
                    : (data.synopsis == null ? "" : data.synopsis);
            if (!desc.isEmpty()) {
                TextView d = Ui.text(a, desc, 14.5f, Ui.VAR, false);
                d.setSingleLine(false);
                d.setEllipsize(null);
                d.setLineSpacing(0, 1.15f);
                d.setMaxLines(expanded ? Integer.MAX_VALUE : 3);
                LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                dp2.setMargins(Ui.dp(18), Ui.dp(16), Ui.dp(18), 0);
                content.addView(d, dp2);
                TextView more = Ui.text(a, expanded ? "Свернуть" : "Ещё", 14, Ui.ACCENT, true);
                more.setPadding(Ui.dp(18), Ui.dp(4), Ui.dp(18), Ui.dp(4));
                more.setOnClickListener(v -> {
                    expanded = !expanded;
                    render();
                });
                content.addView(more);
            }

            // filters
            LinearLayout filterRow = new LinearLayout(a);
            filterRow.setOrientation(LinearLayout.HORIZONTAL);
            HorizontalScrollView hs = Ui.hscroll(a);
            LinearLayout chips = Ui.hscrollRow(hs);
            int op = 0, ed = 0, in = 0;
            for (Track t : data.tracks) {
                if ("OP".equals(t.type)) op++;
                else if ("ED".equals(t.type)) ed++;
                else in++;
            }
            final int fop = op, fed = ed, fin = in;
            chips.addView(Ui.chip(a, "Все", "all".equals(filter), v -> {
                filter = "all";
                render();
            }));
            if (op > 0) chips.addView(Ui.chip(a, "OP · " + op, "OP".equals(filter), v -> {
                filter = "OP";
                render();
            }));
            if (ed > 0) chips.addView(Ui.chip(a, "ED · " + ed, "ED".equals(filter), v -> {
                filter = "ED";
                render();
            }));
            if (in > 0) chips.addView(Ui.chip(a, "Вставки · " + in, "IN".equals(filter), v -> {
                filter = "IN";
                render();
            }));
            filterRow.addView(hs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            boolean hasVersions = false;
            Set<String> base = new HashSet<>();
            for (Track t : data.tracks) {
                if (t.version > 1) hasVersions = true;
            }
            if (hasVersions) {
                TextView ver = Ui.text(a, showAllVersions ? "Скрыть" : "Версии", 14, Ui.ACCENT, true);
                ver.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(14), Ui.dp(8));
                ver.setOnClickListener(v -> {
                    showAllVersions = !showAllVersions;
                    render();
                });
                filterRow.addView(ver);
            }
            content.addView(filterRow);

            // tracks
            ArrayList<Track> visible = visible();
            if (visible.isEmpty() && extras.isEmpty()) {
                content.addView(Ui.emptyState(a, "music_note", "Треков пока нет",
                        "Для этого аниме ещё нет записей.", null, null));
            } else {
                for (Track t : visible) {
                    content.addView(new Ui.TrackRow(a, host, t, visible, false, true, null));
                }
            }
            if (!extras.isEmpty()) {
                content.addView(Ui.sectionHeader(a, "Ещё треки", null, null));
                ArrayList<Track> ctx = new ArrayList<>(visible);
                ctx.addAll(extras);
                for (Track t : extras) {
                    content.addView(new Ui.TrackRow(a, host, t, ctx, false, true, null));
                }
            }
            // footer: studios / series
            String st = join(", ", data.studios.toArray(new String[0]));
            String se = join(", ", data.series.toArray(new String[0]));
            if (!st.isEmpty() || !se.isEmpty()) {
                LinearLayout footer = new LinearLayout(a);
                footer.setOrientation(LinearLayout.VERTICAL);
                footer.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), 0);
                if (!st.isEmpty()) footer.addView(Ui.text(a, st, 12.5f, Ui.DIM, false));
                if (!se.isEmpty()) footer.addView(Ui.text(a, se, 12.5f, Ui.DIM, false));
                content.addView(footer);
            }
        }

        private ArrayList<Track> visible() {
            ArrayList<Track> out = new ArrayList<>();
            if (data == null) return out;
            Set<String> baseSeen = new HashSet<>();
            for (Track t : data.tracks) {
                boolean primaryVersion = t.version <= 1;
                if (primaryVersion) baseSeen.add(t.type + t.sequence);
            }
            for (Track t : data.tracks) {
                if (!"all".equals(filter) && !filter.equals(t.type)) continue;
                if (!showAllVersions && t.version > 1) continue;
                out.add(t);
            }
            return out;
        }

        private void playAll(boolean shuffle) {
            ArrayList<Track> all = visible();
            if (!extras.isEmpty()) all.addAll(extras);
            if (all.isEmpty()) {
                host.toast("Треков пока нет");
                return;
            }
            host.playAll(all, 0, shuffle);
            host.openNowPlaying();
        }

        private View headerSkeleton() {
            LinearLayout l = new LinearLayout(a);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setGravity(Gravity.CENTER_HORIZONTAL);
            l.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
            l.addView(Ui.skeleton(a, 132, 190, 12));
            Ui.ShimmerView t = Ui.skeleton(a, 180, 18, 6);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.dp(180), Ui.dp(18));
            tp.topMargin = Ui.dp(12);
            t.setLayoutParams(tp);
            l.addView(t);
            return l;
        }

        private View rowSkeleton() {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
            row.addView(Ui.skeleton(a, 46, 46, 9));
            LinearLayout mid = new LinearLayout(a);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mp.leftMargin = Ui.dp(12);
            mid.addView(Ui.skeleton(a, 140, 12, 4));
            row.addView(mid, mp);
            return row;
        }

        private static String join(String sep, String... items) {
            StringBuilder out = new StringBuilder();
            for (String s : items) {
                if (s == null || s.isEmpty()) continue;
                if (out.length() > 0) out.append(sep);
                out.append(s);
            }
            return out.toString();
        }

        @Override public View view() { return root; }
        @Override public void onShow() { }
        @Override public void onPlayerChanged() { }
    }

    /* ================= Artist ================= */

    public static class ArtistScreen implements Screen {
        private final Ui.Host host;
        private final Activity a;
        private final LinearLayout root;
        private final LinearLayout content;
        private final String slug;
        private ArtistInfo data;
        private String sort = "new";

        public ArtistScreen(Ui.Host host, String slug) {
            this.host = host;
            this.a = host.activity();
            this.slug = slug;
            root = new LinearLayout(a);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Ui.BG);
            content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(0, 0, 0, Ui.dp(28));
            ScrollView scroll = new ScrollView(a);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            load();
        }

        private void load() {
            content.removeAllViews();
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.VERTICAL);
            head.setGravity(Gravity.CENTER_HORIZONTAL);
            head.setPadding(Ui.dp(16), Ui.dp(10), Ui.dp(16), Ui.dp(10));
            head.addView(Ui.skeleton(a, 132, 132, 66));
            content.addView(head);
            for (int i = 0; i < 5; i++) content.addView(rowSkeleton());
            host.runIo(() -> {
                try {
                    ArtistInfo info = ApiClient.artist(slug);
                    a.runOnUiThread(() -> {
                        data = info;
                        render();
                    });
                } catch (Exception e) {
                    a.runOnUiThread(() -> {
                        content.removeAllViews();
                        content.addView(Ui.errorState(a, e.getMessage(), v -> load()));
                    });
                }
            });
        }

        private void render() {
            content.removeAllViews();
            if (data == null) return;
            LinearLayout header = new LinearLayout(a);
            header.setOrientation(LinearLayout.VERTICAL);
            header.setGravity(Gravity.CENTER_HORIZONTAL);
            header.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), 0);
            Ui.CoverView avatar = Ui.cover(a, 132, 66).circle();
            String img = !data.imageSmall.isEmpty() ? data.imageSmall : data.image;
            if (img.isEmpty()) {
                avatar.addView(Ui.icon(a, "mic", 44, Ui.DIM),
                        new android.widget.FrameLayout.LayoutParams(Ui.dp(44), Ui.dp(44), Gravity.CENTER));
            } else {
                avatar.load(img);
            }
            header.addView(avatar);
            TextView name = Ui.text(a, data.name, 26, Ui.ON, true);
            name.setMaxLines(2);
            name.setSingleLine(false);
            name.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            np.topMargin = Ui.dp(12);
            header.addView(name, np);
            Set<Integer> animeIds = new HashSet<>();
            for (Track t : data.tracks) animeIds.add(t.themeId);
            TextView sub = Ui.text(a, Util.pluralRu(data.tracks.size(), "трек", "трека", "треков")
                    + " · " + animeIds.size() + " аниме", 13, Ui.VAR, false);
            header.addView(sub);
            content.addView(header);

            LinearLayout actions = new LinearLayout(a);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            actions.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(12), 0);
            View play = Ui.surfaceButton(a, "Слушать", "play_arrow", v -> {
                ArrayList<Track> tracks = sorted();
                if (tracks.isEmpty()) return;
                host.playAll(tracks, 0, false);
                host.openNowPlaying();
            });
            actions.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(40), 1f));
            View shuffle = Ui.surfaceButton(a, "Вперемешку", "shuffle", v -> {
                ArrayList<Track> tracks = sorted();
                if (tracks.size() < 2) return;
                host.playAll(tracks, 0, true);
                host.openNowPlaying();
            });
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, Ui.dp(40), 1f);
            sp.leftMargin = Ui.dp(8);
            actions.addView(shuffle, sp);
            content.addView(actions);

            if (data.tracks.size() > 1) {
                HorizontalScrollView hs = Ui.hscroll(a);
                LinearLayout chips = Ui.hscrollRow(hs);
                chips.addView(Ui.chip(a, "Новые", "new".equals(sort), v -> {
                    sort = "new";
                    render();
                }));
                chips.addView(Ui.chip(a, "Старые", "old".equals(sort), v -> {
                    sort = "old";
                    render();
                }));
                chips.addView(Ui.chip(a, "По аниме", "anime".equals(sort), v -> {
                    sort = "anime";
                    render();
                }));
                LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                hp.topMargin = Ui.dp(8);
                content.addView(hs, hp);
            }
            ArrayList<Track> tracks = sorted();
            if (tracks.isEmpty()) {
                content.addView(Ui.emptyState(a, "mic", "Треков пока нет", "", "Искать", v ->
                        host.openSearch(data.name)));
            } else {
                for (Track t : tracks) content.addView(new Ui.TrackRow(a, host, t, tracks, true, false, null));
            }
        }

        private ArrayList<Track> sorted() {
            ArrayList<Track> list = new ArrayList<>(data == null ? new ArrayList<Track>() : data.tracks);
            if ("old".equals(sort)) java.util.Collections.reverse(list);
            if ("anime".equals(sort)) java.util.Collections.sort(list, (x, y) -> x.animeName.compareToIgnoreCase(y.animeName));
            return list;
        }

        private View rowSkeleton() {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
            row.addView(Ui.skeleton(a, 46, 46, 9));
            LinearLayout mid = new LinearLayout(a);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mp.leftMargin = Ui.dp(12);
            mid.addView(Ui.skeleton(a, 140, 12, 4));
            row.addView(mid, mp);
            return row;
        }

        @Override public View view() { return root; }
        @Override public void onShow() { }
        @Override public void onPlayerChanged() { }
    }
}
