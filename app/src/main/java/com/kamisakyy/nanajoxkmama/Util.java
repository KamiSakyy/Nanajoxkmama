package com.kamisakyy.nanajoxkmama;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Catalog constants and pure helpers — a direct port of the website's
 * core/catalog.ts + lib/utils.ts (seasons, genres, curated mixes, formatting).
 */
public final class Util {
    private Util() { }

    public static final String[] SEASONS = {"Winter", "Spring", "Summer", "Fall"};

    public static String seasonRu(String season) {
        if ("Winter".equals(season)) return "Зима";
        if ("Spring".equals(season)) return "Весна";
        if ("Summer".equals(season)) return "Лето";
        if ("Fall".equals(season)) return "Осень";
        return season == null ? "" : season;
    }

    public static String seasonLabel(String season, int year) {
        StringBuilder out = new StringBuilder();
        if (season != null && !season.isEmpty()) out.append(seasonRu(season));
        if (year > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(year);
        }
        return out.toString();
    }

    public static int currentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    public static int currentMonth() {
        return Calendar.getInstance().get(Calendar.MONTH) + 1;
    }

    /** "Winter"|"Spring"|"Summer"|"Fall" for the current date. */
    public static String currentSeason() {
        int m = currentMonth();
        return m < 3 ? "Winter" : m < 6 ? "Spring" : m < 9 ? "Summer" : "Fall";
    }

    public static int[] prevSeason(int year, String season) {
        int i = 0;
        for (int k = 0; k < SEASONS.length; k++) if (SEASONS[k].equals(season)) i = k;
        return i == 0 ? new int[]{year - 1, 3} : new int[]{year, i - 1};
    }

    public static String pluralRu(int n, String one, String few, String many) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return n + " " + one;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return n + " " + few;
        return n + " " + many;
    }

    public static String formatTime(long ms) {
        if (!Double.isFinite(ms) || ms < 0) return "0:00";
        long sec = ms / 1000;
        long m = sec / 60;
        long s = sec % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f КБ", bytes / 1024.0);
        return String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0));
    }

    public static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < 5) return "Доброй ночи";
        if (h < 12) return "Доброе утро";
        if (h < 18) return "Добрый день";
        return "Добрый вечер";
    }

    public static <T> List<T> shuffleArray(List<T> arr) {
        ArrayList<T> a = new ArrayList<>(arr);
        Random r = new Random();
        for (int i = a.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            Collections.swap(a, i, j);
        }
        return a;
    }

    public interface KeyFn<T> { String key(T t); }

    public static <T> ArrayList<T> uniqueBy(List<T> arr, KeyFn<T> key) {
        Set<String> seen = new HashSet<>();
        ArrayList<T> out = new ArrayList<>();
        for (T it : arr) {
            String k = key.key(it);
            if (seen.add(k)) out.add(it);
        }
        return out;
    }

    public static ArrayList<Track> filterMature(List<Track> source) {
        ArrayList<Track> out = new ArrayList<>();
        boolean mature = Store.isMatureEnabled();
        for (Track t : source) if (mature || !t.nsfw) out.add(t);
        return out;
    }

    public static String shareText(Track t) {
        return t.title + " — " + t.displayArtist() + "\n" + t.animeName + " · " + t.themeTag() + "\nСлушаю в AniBeat";
    }

    /* ---------------- genres (meta-based, graceful fallback) ---------------- */
    public static final class Genre {
        public final String id;
        public final String label;
        final String[] needles;
        Genre(String id, String label, String[] needles) {
            this.id = id;
            this.label = label;
            this.needles = needles;
        }
    }

    public static final Genre[] GENRES = {
        new Genre("action", "Экшен", new String[]{"экшен", "боевик", "action"}),
        new Genre("fantasy", "Фантастика", new String[]{"фантастика", "sci-fi", "фэнтези", "fantasy"}),
        new Genre("romance", "Романтика", new String[]{"романтика", "romance"}),
        new Genre("comedy", "Комедия", new String[]{"комедия", "comedy"}),
        new Genre("drama", "Драма", new String[]{"драма", "drama"}),
        new Genre("adventure", "Приключения", new String[]{"приключения", "adventure"}),
        new Genre("sports", "Спорт", new String[]{"спорт", "sports"}),
        new Genre("music", "Музыка", new String[]{"музыка", "music", "идол", "idol"}),
        new Genre("thriller", "Триллер", new String[]{"хоррор", "ужасы", "horror", "триллер", "thriller", "мистика", "mystery", "детектив"}),
        new Genre("shoujo", "Сёдзё", new String[]{"сёдзё", "shoujo"}),
        new Genre("seinen", "Сейнен", new String[]{"сейнен", "seinen"}),
        new Genre("shounen", "Сёнэн", new String[]{"сёнэн", "shounen", "shonen"}),
        new Genre("mecha", "Меха", new String[]{"меха", "mecha"}),
        new Genre("slice", "Повседневность", new String[]{"повседневность", "slice of life"}),
        new Genre("school", "Школа", new String[]{"школа", "school"}),
        new Genre("historical", "Исторический", new String[]{"исторический", "historical"}),
    };

    public static List<String> trackGenreIds(Track t) {
        ArrayList<String> out = new ArrayList<>();
        if (t.malId <= 0) return out;
        String genres = MetaApi.getMeta(t.malId) == null ? "" : MetaApi.getMeta(t.malId).genres;
        if (genres == null || genres.isEmpty()) return out;
        String lower = genres.toLowerCase(Locale.US);
        for (Genre g : GENRES) {
            for (String needle : g.needles) {
                if (lower.contains(needle)) {
                    out.add(g.id);
                    break;
                }
            }
        }
        return out;
    }

    /** Genre filter with graceful fallback (tracks without meta pass through). */
    public static ArrayList<Track> filterGenre(List<Track> tracks, String genre) {
        if (genre == null || genre.isEmpty()) return new ArrayList<>(tracks);
        ArrayList<Track> out = new ArrayList<>();
        for (Track t : tracks) {
            List<String> ids = trackGenreIds(t);
            if (ids.isEmpty() || ids.contains(genre)) out.add(t);
        }
        return out;
    }

    /* ---------------- curated content (slugs verified against the API) ---------------- */
    public static final String[] LEGEND_SLUGS = {
        "shingeki_no_kyojin", "kimetsu_no_yaiba", "jujutsu_kaisen", "fullmetal_alchemist_brotherhood", "death_note",
        "sousou_no_frieren", "chainsaw_man", "spy_x_family", "boku_no_hero_academia", "hunter_x_hunter_2011",
        "cowboy_bebop", "neon_genesis_evangelion", "code_geass_hangyaku_no_lelouch", "tokyo_ghoul", "one_punch_man",
        "mob_psycho_100", "naruto", "naruto_shippuuden", "bleach", "oshi_no_ko", "bocchi_the_rock", "violet_evergarden",
        "made_in_abyss", "sword_art_online", "no_game_no_life", "toradora", "clannad", "angel_beats", "k_on", "haikyuu",
        "dragon_ball_z", "yakusoku_no_neverland", "dr_stone", "vinland_saga", "cyberpunk_edgerunners", "dandadan",
        "one_piece", "tokyo_revengers", "gintama", "fairy_tail", "noragami", "psycho_pass", "samurai_champloo",
        "ore_dake_level_up_na_ken", "kaguya_sama_wa_kokurasetai_tensai_tachi_no_renai_zunousen", "nanatsu_no_taizai",
        "durarara", "soul_eater", "ao_no_exorcist", "akame_ga_kill", "kill_la_kill", "tengen_toppa_gurren_lagann",
        "monster", "berserk", "horimiya", "mushoku_tensei_isekai_ittara_honki_dasu", "tensei_shitara_slime_datta_ken",
        "kaijuu_8_gou", "kuroko_no_basket", "nichijou", "suzumiya_haruhi_no_yuuutsu", "serial_experiments_lain",
    };

    public static final String[] ARTIST_SLUGS = {
        "lisa", "aimer", "yoasobi", "flow", "kana_boon", "radwimps", "kenshi_yonezu", "eve", "ado", "akfg",
        "uverworld", "man_with_a_mission", "official_hige_dandism", "kessoku_band", "reona", "milet", "egoist",
        "supercell", "claris", "asca", "aimyon", "yoko_kanno", "linked_horizon", "yuki_kajiura", "kalafina", "king_gnu",
        "creepy_nuts", "vaundy", "yorushika", "zutomayo", "mrs_green_apple", "spyair", "the_oral_cigarettes",
        "ikimonogakari", "nano", "masayoshi_ooishi", "yui", "burnout_syndromes", "oresama", "fhana", "mili",
        "porno_graffitti", "orange_range", "granrodeo", "ling_tosite_sigure",
    };

    public static final class Mix {
        public final String id, title, subtitle;
        public final String[] slugs;
        Mix(String id, String title, String subtitle, String[] slugs) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.slugs = slugs;
        }
    }

    public static final Mix[] MIXES = {
        new Mix("shounen", "Сёнэн-энергия", "Naruto · Bleach · JJK · MHA", new String[]{
            "naruto", "naruto_shippuuden", "bleach", "jujutsu_kaisen", "boku_no_hero_academia", "kimetsu_no_yaiba",
            "one_piece", "fairy_tail", "dragon_ball_z", "haikyuu"}),
        new Mix("epic", "Эпик и драма", "AoT · FMA · Code Geass", new String[]{
            "shingeki_no_kyojin", "fullmetal_alchemist_brotherhood", "code_geass_hangyaku_no_lelouch", "vinland_saga",
            "death_note", "psycho_pass", "tokyo_ghoul", "monster", "berserk", "tengen_toppa_gurren_lagann"}),
        new Mix("chill", "Чилл и романтика", "Toradora · Clannad · Frieren", new String[]{
            "toradora", "clannad", "horimiya", "sousou_no_frieren", "violet_evergarden",
            "kaguya_sama_wa_kokurasetai_tensai_tachi_no_renai_zunousen", "k_on", "angel_beats", "nichijou",
            "suzumiya_haruhi_no_yuuutsu"}),
        new Mix("modern", "Новая волна", "Chainsaw Man · Oshi no Ko", new String[]{
            "chainsaw_man", "oshi_no_ko", "dandadan", "bocchi_the_rock", "spy_x_family", "cyberpunk_edgerunners",
            "kaijuu_8_gou", "ore_dake_level_up_na_ken", "tokyo_revengers", "mushoku_tensei_isekai_ittara_honki_dasu"}),
        new Mix("classic", "Классика", "Bebop · Evangelion · Champloo", new String[]{
            "cowboy_bebop", "neon_genesis_evangelion", "samurai_champloo", "serial_experiments_lain",
            "hunter_x_hunter_2011", "gintama", "soul_eater", "durarara", "no_game_no_life"}),
    };
}
