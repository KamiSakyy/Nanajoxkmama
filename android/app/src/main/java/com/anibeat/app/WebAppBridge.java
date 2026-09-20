package com.anibeat.app;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native API exposed to the page as {@code window.AniBeatNative}.
 *
 * Only bridges what a browser has and WebView does not: media session
 * (lockscreen/notification), share sheet and saving blobs to Downloads.
 */
public class WebAppBridge {

    private static final String TAG = "AniBeatBridge";

    private final MainActivity activity;
    private final WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final FileSaver fileSaver;

    WebAppBridge(MainActivity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        this.fileSaver = new FileSaver(activity);
    }

    /* ------------------------------------------------------------------ */
    /* Diagnostics                                                         */
    /* ------------------------------------------------------------------ */

    @JavascriptInterface
    public void log(String message) {
        Log.d(TAG, message == null ? "" : message);
    }

    @JavascriptInterface
    public void onReady() {
        Log.d(TAG, "web app ready");
        main.post(() -> PlaybackService.syncFromWeb(activity));
    }

    @JavascriptInterface
    public void toast(String message) {
        main.post(() -> Toast.makeText(activity, message == null ? "" : message, Toast.LENGTH_SHORT).show());
    }

    /* ------------------------------------------------------------------ */
    /* Media session / background playback                                 */
    /* ------------------------------------------------------------------ */

    @JavascriptInterface
    public void updateMetadata(String json) {
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            String title = o.optString("title", "");
            String artist = o.optString("artist", "");
            String album = o.optString("album", "");
            String art = "";
            JSONArray artwork = o.optJSONArray("artwork");
            if (artwork != null && artwork.length() > 0) {
                art = artwork.getJSONObject(artwork.length() - 1).optString("src", "");
                if (art.isEmpty()) art = artwork.getJSONObject(0).optString("src", "");
            }
            PlaybackService.updateMetadata(activity, title, artist, album, art);
        } catch (Exception e) {
            Log.w(TAG, "metadata " + e);
        }
    }

    @JavascriptInterface
    public void updatePlaybackState(String json) {
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            boolean playing = o.optBoolean("playing", false);
            long position = (long) (o.optDouble("position", 0) * 1000);
            long duration = (long) (o.optDouble("duration", 0) * 1000);
            activity.setPlaying(playing);
            PlaybackService.updateState(activity, playing, position, duration);
        } catch (Exception e) {
            Log.w(TAG, "state " + e);
        }
    }

    /** Sends a media command coming from the notification / lockscreen back into the page. */
    static void dispatchMediaAction(WebView web, String action, String value) {
        if (web == null) return;
        String js = "window.__AniBeatNativeEvent && window.__AniBeatNativeEvent('mediaAction', JSON.stringify({action:"
                + JSONObject.quote(action) + ", value:" + value + "}))";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    /* ------------------------------------------------------------------ */
    /* Share sheet                                                         */
    /* ------------------------------------------------------------------ */

    @JavascriptInterface
    public String share(String title, String text, String url) {
        main.post(() -> {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                if (title != null && !title.isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title);
                StringBuilder body = new StringBuilder();
                if (text != null) body.append(text);
                if (url != null && !url.isEmpty()) body.append(body.length() > 0 ? "\n" : "").append(url);
                send.putExtra(Intent.EXTRA_TEXT, body.toString());
                Intent chooser = Intent.createChooser(send, title == null || title.isEmpty() ? "Поделиться" : title);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(chooser);
            } catch (Exception e) {
                Log.w(TAG, "share " + e);
            }
        });
        return "ok";
    }

    /* ------------------------------------------------------------------ */
    /* Blob download -> Downloads folder                                   */
    /* ------------------------------------------------------------------ */

    @JavascriptInterface
    public String beginFile(String name, String mime, long total) {
        return fileSaver.begin(name, mime, total);
    }

    @JavascriptInterface
    public String writeChunk(String base64) {
        return fileSaver.write(base64);
    }

    @JavascriptInterface
    public String endFile() {
        String result = fileSaver.end();
        main.post(() -> Toast.makeText(activity, "Сохранено в «Загрузки»" + (result.startsWith("error") ? " — ошибка" : ""), Toast.LENGTH_SHORT).show());
        return result;
    }

    @JavascriptInterface
    public void fileProgress(long written, long total) {
        /* progress is reported through the web UI itself */
    }

    @JavascriptInterface
    public boolean isAndroid() {
        return true;
    }

    @JavascriptInterface
    public String platformInfo() {
        return Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE;
    }
}
