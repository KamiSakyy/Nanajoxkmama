package com.anibeat.app.ui;

import java.util.List;

/** Мелкие утилиты форматирования (lib/utils.ts). */
public final class Format {

    private Format() {
    }

    public static String time(long seconds) {
        if (seconds < 0) seconds = 0;
        long m = seconds / 60;
        long s = seconds % 60;
        return m + ":" + (s < 10 ? "0" + s : String.valueOf(s));
    }

    public static String time(float seconds) {
        return time((long) Math.floor(seconds));
    }

    public static String plural(int n, String one, String few, String many) {
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return n + " " + one;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return n + " " + few;
        return n + " " + many;
    }

    public static String bytes(long n) {
        if (n <= 0) return "0 Б";
        if (n < 1024) return n + " Б";
        double kb = n / 1024.0;
        if (kb < 1024) return Math.round(kb) + " КБ";
        double mb = kb / 1024;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f МБ", mb);
        return String.format(java.util.Locale.US, "%.2f ГБ", mb / 1024);
    }

    public static <T> java.util.List<T> uniqueBy(java.util.List<T> items, java.util.function.Function<T, String> key) {
        java.util.LinkedHashMap<String, T> map = new java.util.LinkedHashMap<>();
        for (T item : items) {
            String k = key.apply(item);
            if (!map.containsKey(k)) map.put(k, item);
        }
        return new java.util.ArrayList<>(map.values());
    }

    public static <T> java.util.List<T> shuffled(List<T> items) {
        java.util.List<T> copy = new java.util.ArrayList<>(items);
        java.util.Collections.shuffle(copy);
        return copy;
    }

    public static boolean hasCyrillic(String value) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if (ch >= 'а' && ch <= 'я' || ch == 'ё') return true;
        }
        return false;
    }
}
