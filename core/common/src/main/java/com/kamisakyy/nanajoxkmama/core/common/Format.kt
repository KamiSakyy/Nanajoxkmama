package com.kamisakyy.nanajoxkmama.core.common

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS

fun formatTime(ms: Long): String {
    val total = abs(ms) / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(Locale.US, m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes Б"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f КБ", kb)
    val mb = kb / 1024.0
    return if (mb < 1024) String.format(Locale.US, "%.1f МБ", mb)
    else String.format(Locale.US, "%.2f ГБ", mb / 1024.0)
}

fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val n10 = abs(n) % 10
    val n100 = abs(n) % 100
    return when {
        n10 == 1 && n100 != 11 -> one
        n10 in 2..4 && n100 !in 12..14 -> few
        else -> many
    }
}

fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 4..10 -> "Доброе утро"
        in 11..16 -> "Добрый день"
        in 17..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

fun seasonRu(season: String?): String = when (season?.lowercase(Locale.US)) {
    "winter" -> "Зима"
    "spring" -> "Весна"
    "summer" -> "Лето"
    "fall" -> "Осень"
    else -> season ?: ""
}

fun seasonLabel(year: Int?, season: String?): String {
    if (year == null) return seasonRu(season)
    return seasonRu(season) + " " + year
}

fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

fun currentSeason(): String {
    val m = Calendar.getInstance().get(Calendar.MONTH)
    return when {
        m < 2 -> "Winter"
        m < 5 -> "Spring"
        m < 8 -> "Summer"
        m < 11 -> "Fall"
        else -> "Winter"
    }
}

/** Website season model: Winter=Dec-Feb, Spring=Mar-May, Summer=Jun-Aug, Fall=Sep-Nov */
fun currentSeasonSite(): Pair<Int, String> {
    val c = Calendar.getInstance()
    val m = c.get(Calendar.MONTH)
    val season = when {
        m == 11 -> "Winter"
        m < 2 -> "Winter"
        m < 5 -> "Spring"
        m < 8 -> "Summer"
        else -> "Fall"
    }
    val year = if (m == 11) c.get(Calendar.YEAR) + 1 else c.get(Calendar.YEAR)
    return year to season
}

fun prevSeason(year: Int, season: String): Pair<Int, String> {
    val order = listOf("Winter", "Spring", "Summer", "Fall")
    val i = order.indexOf(season)
    return if (i <= 0) (year - 1) to "Fall" else year to order[i - 1]
}

fun themeTypeOrder(type: String): Int = when (type) {
    "OP" -> 0
    "ED" -> 1
    else -> 2
}
