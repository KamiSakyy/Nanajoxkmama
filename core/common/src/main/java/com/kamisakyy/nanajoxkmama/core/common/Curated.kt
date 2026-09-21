package com.kamisakyy.nanajoxkmama.core.common

/** Curated content — verified slugs, identical to the website. */
object Curated {

    val LEGEND_SLUGS = listOf(
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
    )

    data class Mix(val id: String, val title: String, val subtitle: String, val slugs: List<String>)

    val MIXES = listOf(
        Mix(
            "shounen", "Сёнэн-энергия", "Naruto · Bleach · JJK · MHA", listOf(
                "naruto_shippuuden", "bleach", "jujutsu_kaisen", "boku_no_hero_academia",
                "kimetsu_no_yaiba", "black_clover", "fairy_tail", "hunter_x_hunter_2011",
            )
        ),
        Mix(
            "epic", "Эпик и драма", "AoT · Death Note · FMA:B", listOf(
                "shingeki_no_kyojin", "death_note", "fullmetal_alchemist_brotherhood",
                "tokyo_ghoul", "vinland_saga", "kingdom", "91_days", "bokurano",
            )
        ),
        Mix(
            "chill", "Чилл и романтика", "K-On! · Clannad · Toradora!", listOf(
                "k_on", "clannad", "toradora", "horimiya", "kaguya_sama_wa_kokurasetai_tensai_tachi_no_renai_zunousen",
                "yamada_kun_to_7_nin_no_majo", "ao_haru_ride", "wotaku_ni_koi_wa_muzukashii",
            )
        ),
        Mix(
            "newwave", "Новая волна", "Frieren · Dandadan · CSM", listOf(
                "sousou_no_frieren", "dandadan", "chainsaw_man", "oshi_no_ko", "kaijuu_8_gou",
                "spy_x_family", "bocchi_the_rock", "shingeki_no_kyojin_final_season",
            )
        ),
        Mix(
            "classic", "Классика", "Evangelion · Bebop · Lain", listOf(
                "neon_genesis_evangelion", "cowboy_bebop", "serial_experiments_lain", "monster",
                "samurai_champloo", "trigun", "great_teacher_onizuka", "flcl",
            )
        ),
    )

    val DECADES = listOf(2020, 2010, 2000, 1990, 1980)

    val SEASONS = listOf("Winter", "Spring", "Summer", "Fall")

    data class Genre(val id: String, val label: String, val needles: List<String>)

    val GENRES = listOf(
        Genre("action", "Экшен", listOf("экшен", "боевик", "action")),
        Genre("fantasy", "Фантастика", listOf("фантастика", "sci-fi", "фэнтези", "fantasy")),
        Genre("romance", "Романтика", listOf("романтика", "romance")),
        Genre("comedy", "Комедия", listOf("комедия", "comedy")),
        Genre("drama", "Драма", listOf("драма", "drama")),
        Genre("adventure", "Приключения", listOf("приключения", "adventure")),
        Genre("sports", "Спорт", listOf("спорт", "sports")),
        Genre("music", "Музыка", listOf("музыка", "music", "идол", "idol")),
        Genre("thriller", "Триллер", listOf("хоррор", "ужасы", "horror", "триллер", "thriller", "мистика", "mystery", "детектив")),
        Genre("shoujo", "Сёдзё", listOf("сёдзё", "shoujo")),
        Genre("seinen", "Сейнен", listOf("сейнен", "seinen")),
        Genre("shounen", "Сёнэн", listOf("сёнэн", "shounen", "shonen")),
        Genre("mecha", "Меха", listOf("меха", "mecha")),
        Genre("slice", "Повседневность", listOf("повседневность", "slice of life")),
        Genre("school", "Школа", listOf("школа", "school")),
        Genre("historical", "Исторический", listOf("исторический", "historical")),
    )
}
