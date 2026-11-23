package com.moviemax.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies a finished download from the app's private storage to the phone: Movies/Movie max. */
final class Exporter {
    private Exporter() { }

    /** Characters like / : * ? make the phone's storage refuse the file, so they are replaced. */
    static String fileName(String name) {
        String n = name == null || name.trim().isEmpty() ? "Movie max video" : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (n.length() > 120) n = n.substring(0, 120);
        return n.toLowerCase().endsWith(".mp4") ? n : n + ".mp4";
    }

    /** Runs in the background; the Android 9-and-older storage permission is checked by the caller. */
    static void export(Activity a, DownloadStore.Item it) {
        if (it == null || !DownloadStore.DONE.equals(it.status)) {
            Toast.makeText(a, "Finish the download before exporting.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(a, "Exporting to Movies/Movie max…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String msg;
            try {
                String fileName = fileName(it.name);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = a.getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
                    v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                    v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Movie max");
                    v.put(MediaStore.Video.Media.IS_PENDING, 1);
                    Uri uri = cr.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                    if (uri == null) throw new Exception("Storage not available");
                    try (InputStream in = new FileInputStream(it.path); OutputStream out = cr.openOutputStream(uri)) {
                        copy(in, out);
                    } catch (Exception e) {
                        cr.delete(uri, null, null);
                        throw e;
                    }
                    v.clear();
                    v.put(MediaStore.Video.Media.IS_PENDING, 0);
                    cr.update(uri, v, null, null);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Movie max");
                    dir.mkdirs();
                    File dest = new File(dir, fileName);
                    try (InputStream in = new FileInputStream(it.path); OutputStream out = new FileOutputStream(dest)) {
                        copy(in, out);
                    }
                    MediaScannerConnection.scanFile(a, new String[]{dest.getAbsolutePath()}, null, null);
                }
                msg = "Saved to Movies/Movie max";
            } catch (Exception e) {
                msg = "Export failed: " + e.getMessage();
            }
            String m = msg;
            a.runOnUiThread(() -> Toast.makeText(a, m, Toast.LENGTH_LONG).show());
        }).start();
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        if (out == null) throw new Exception("Storage not available");
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
