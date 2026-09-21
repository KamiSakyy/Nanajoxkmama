package com.anibeat.app.data;

import com.anibeat.app.core.Image;
import com.anibeat.app.core.Prefs;

import java.util.ArrayList;
import java.util.List;

/** Настройки и состояние каталога (store/settings.tsx + store/general.tsx). */
public final class Settings {

    public interface Listener {
        void onChanged();
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();

    public static boolean dataSaver;
    public static boolean preloadNext = true;
    public static String downloadKind = "audio";
    public static boolean ruTitles = true;
    public static boolean extraSources = true;
    public static String period = "all";
    public static String mature = "off";

    private Settings() {
    }

    public static void init() {
        dataSaver = Prefs.getBool("settings.dataSaver", false);
        preloadNext = Prefs.getBool("settings.preloadNext", true);
        downloadKind = Prefs.getString("settings.downloadKind", "audio");
        ruTitles = Prefs.getBool("settings.ruTitles", true);
        extraSources = Prefs.getBool("settings.extraSources", true);
        period = Prefs.getString("period", "all");
        mature = Prefs.getString("mature", "off");
        Image.setDataSaver(dataSaver);
    }

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void emit() {
        for (Listener l : new ArrayList<>(LISTENERS)) {
            try {
                l.onChanged();
            } catch (Throwable t) {
                com.anibeat.app.core.Ui.report(t);
            }
        }
    }

    public static void setDataSaver(boolean value) {
        dataSaver = value;
        Prefs.put("settings.dataSaver", value);
        Image.setDataSaver(value);
        emit();
    }

    public static void setPreloadNext(boolean value) {
        preloadNext = value;
        Prefs.put("settings.preloadNext", value);
        emit();
    }

    public static void setDownloadKind(String value) {
        downloadKind = value;
        Prefs.put("settings.downloadKind", value);
        emit();
    }

    public static void setRuTitles(boolean value) {
        ruTitles = value;
        Prefs.put("settings.ruTitles", value);
        emit();
    }

    public static void setExtraSources(boolean value) {
        extraSources = value;
        Prefs.put("settings.extraSources", value);
        emit();
    }

    public static void setPeriod(String value) {
        period = value;
        Prefs.put("period", value);
        emit();
    }

    public static void setMature(String value) {
        mature = value;
        Prefs.put("mature", value);
        emit();
    }

    public static List<Models.Track> filterMature(List<Models.Track> tracks) {
        if ("on".equals(mature)) return tracks;
        List<Models.Track> out = new ArrayList<>();
        for (Models.Track t : tracks) if (!t.nsfw) out.add(t);
        return out;
    }
}
