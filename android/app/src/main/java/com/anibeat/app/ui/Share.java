package com.anibeat.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;

import com.anibeat.app.data.Models;

/** Поделиться треком (shareTrack из lib/utils.ts). */
public final class Share {

    private Share() {
    }

    public static String track(Context context, Models.Track track) {
        String text = track.title + " — " + track.artistNames() + "\n"
                + (track.anime == null ? "" : track.anime.name + " · ") + track.themeSlug + "\nСлушаю в AniBeat";
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, track.title);
            send.putExtra(Intent.EXTRA_TEXT, text);
            Intent chooser = Intent.createChooser(send, "Поделиться");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return "shared";
        } catch (Exception ignored) {
        }
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("AniBeat", text));
                return "copied";
            }
        } catch (Exception ignored) {
        }
        return "failed";
    }
}
