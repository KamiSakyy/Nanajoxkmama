package com.anibeat.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Saves a blob coming from the page (audio/video track) into the public
 * «Загрузки» folder, exactly like the browser would with
 * {@code <a download href="blob:...">}.
 */
class FileSaver {

    private static final String TAG = "AniBeatFileSaver";

    private static final class Job {
        String name;
        String mime;
        long total;
        File temp;
        OutputStream out;
        long written;
    }

    private final Context context;
    private Job job;

    FileSaver(Context context) {
        this.context = context.getApplicationContext();
    }

    synchronized String begin(String name, String mime, long total) {
        try {
            closeQuietly();
            Job j = new Job();
            j.name = sanitize(name);
            j.mime = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
            j.total = total;
            File dir = new File(context.getCacheDir(), "saves");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cache dir");
            j.temp = new File(dir, "pending.bin");
            if (j.temp.exists()) j.temp.delete();
            j.out = new FileOutputStream(j.temp);
            job = j;
            Log.d(TAG, "begin " + j.name + " (" + total + " bytes)");
            return "ok";
        } catch (Exception e) {
            job = null;
            return "error:" + e;
        }
    }

    synchronized String write(String base64) {
        if (job == null) return "error:no job";
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            job.out.write(data);
            job.written += data.length;
            return "ok";
        } catch (Exception e) {
            return "error:" + e;
        }
    }

    synchronized String end() {
        if (job == null) return "error:no job";
        try {
            job.out.flush();
            job.out.close();
            job.out = null;
            String result = publish(job.temp, job.name, job.mime);
            job.temp.delete();
            job = null;
            return result;
        } catch (Exception e) {
            Log.w(TAG, "end " + e);
            job = null;
            return "error:" + e;
        }
    }

    private String publish(File source, String name, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri item = context.getContentResolver().insert(collection, values);
            if (item == null) throw new IllegalStateException("insert failed");
            try (OutputStream out = context.getContentResolver().openOutputStream(item)) {
                if (out == null) throw new IllegalStateException("open failed");
                java.io.InputStream in = new java.io.FileInputStream(source);
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                in.close();
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(item, values, null, null);
            return "ok:" + item;
        }

        // Legacy devices: public Downloads when possible, app folder otherwise.
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File target = new File(dir, name);
        try {
            if (dir.exists() || dir.mkdirs()) {
                java.io.InputStream in = new java.io.FileInputStream(source);
                FileOutputStream out = new FileOutputStream(target);
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
                in.close();
                out.close();
                android.media.MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()}, new String[]{mime}, null);
                return "ok:" + target.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.w(TAG, "public downloads failed: " + e);
        }
        File fallbackDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "");
        if (!fallbackDir.exists() && !fallbackDir.mkdirs()) throw new IllegalStateException("no dir");
        File fallback = new File(fallbackDir, name);
        java.io.InputStream in = new java.io.FileInputStream(source);
        FileOutputStream out = new FileOutputStream(fallback);
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        in.close();
        out.close();
        return "ok:" + fallback.getAbsolutePath();
    }

    private void closeQuietly() {
        if (job != null && job.out != null) {
            try {
                job.out.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String sanitize(String name) {
        String n = (name == null || name.trim().isEmpty()) ? "anibeat-file" : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|]+", "").trim();
        if (n.length() > 150) n = n.substring(0, 150);
        return n;
    }
}
