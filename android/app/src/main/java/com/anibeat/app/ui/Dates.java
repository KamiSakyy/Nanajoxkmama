package com.anibeat.app.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Даты для календаря новинок: «сегодня», «вчера», «12 сен». */
public final class Dates {

    private static final String[] MONTHS_RU = {"янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};
    private static final String[] MONTHS_RU_FULL = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private Dates() {
    }

    private static Date parse(String iso) {
        if (iso == null || iso.length() < 10) return null;
        String[] formats = {"yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String format : formats) {
            try {
                String value = iso.length() > format.length() ? iso.substring(0, format.length()) : iso;
                return new SimpleDateFormat(format, Locale.US).parse(value);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Ключ дня в местном времени: yyyy-MM-dd, либо null. */
    public static String dayKey(String iso) {
        Date date = parse(iso);
        if (date == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private static String key(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date);
    }

    private static Date daysAgo(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -days);
        return calendar.getTime();
    }

    public static boolean isToday(String iso) {
        String day = dayKey(iso);
        return day != null && day.equals(key(new Date()));
    }

    /** «сегодня», «вчера», «12 сен» — как на сайте. */
    public static String relativeDay(String iso) {
        String day = dayKey(iso);
        if (day == null) return "";
        if (day.equals(key(new Date()))) return "сегодня";
        if (day.equals(key(daysAgo(1)))) return "вчера";
        Date date = parse(iso);
        if (date == null) return "";
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_RU[calendar.get(Calendar.MONTH)];
    }

    /** Полная дата: «12 сентября 2024». */
    public static String full(String iso) {
        Date date = parse(iso);
        if (date == null) return "";
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_RU_FULL[calendar.get(Calendar.MONTH)]
                + " " + calendar.get(Calendar.YEAR);
    }
}
