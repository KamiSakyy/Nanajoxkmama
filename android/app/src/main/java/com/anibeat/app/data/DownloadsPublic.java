package com.anibeat.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import com.anibeat.app.core.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Сохранение файла в общую папку «Загрузки» на Android 10 и новее.
 * Отдельный класс: на старых версиях он вообще не загружается.
 */
final class DownloadsPublic {

    private DownloadsPublic() {
    }

    static void save(Context context, File source, String name, String mime) {
        try {
            if (context == null) return;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return;
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            if (out == null) return;
            try (InputStream in = new FileInputStream(source)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            } finally {
                out.close();
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
        } catch (Throwable t) {
            Ui.report(t);
        }
    }
}
