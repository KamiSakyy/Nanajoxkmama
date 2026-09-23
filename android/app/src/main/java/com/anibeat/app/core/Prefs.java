package com.anibeat.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** localStorage-аналог: SharedPreferences + JSON списки. */
public final class Prefs {

    private static SharedPreferences sp;

    private Prefs() {
    }

    public static void init(Context context) {
        if (sp == null) sp = context.getApplicationContext().getSharedPreferences("anibeat", Context.MODE_PRIVATE);
    }

    public static String getString(String key, String def) {
        return sp.getString(key, def);
    }

    public static void put(String key, String value) {
        sp.edit().putString(key, value).apply();
    }

    public static boolean getBool(String key, boolean def) {
        return sp.getBoolean(key, def);
    }

    public static void put(String key, boolean value) {
        sp.edit().putBoolean(key, value).apply();
    }

    public static int getInt(String key, int def) {
        return sp.getInt(key, def);
    }

    public static void put(String key, int value) {
        sp.edit().putInt(key, value).apply();
    }

    public static long getLong(String key, long def) {
        return sp.getLong(key, def);
    }

    public static void put(String key, long value) {
        sp.edit().putLong(key, value).apply();
    }

    public static void remove(String key) {
        sp.edit().remove(key).apply();
    }

    /** JSON-массив строк. */
    public static List<String> getStringList(String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void putStringList(String key, List<String> values) {
        JSONArray arr = new JSONArray();
        for (String v : values) arr.put(v);
        put(key, arr.toString());
    }

    /** JSON-массив объектов. */
    public static List<JSONObject> getObjectList(String key) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void putObjectList(String key, List<JSONObject> values) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : values) arr.put(o);
        put(key, arr.toString());
    }
}
