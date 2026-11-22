package com.moviemax.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/** Runs downloads in the background with real pause / resume (HTTP Range) and cancel. */
public class DownloadService extends Service {
    static final String ACTION = "action", ID = "id", URL_EXTRA = "url", NAME = "name";
    private static final int NOTE_ID = 7;
    private static final int MAX_PARALLEL = 2;
    private final Set<String> working = new HashSet<>();

    static void send(Context c, String action, String id, String url, String name) {
        Intent i = new Intent(c, DownloadService.class).putExtra(ACTION, action);
        if (id != null) i.putExtra(ID, id);
        if (url != null) i.putExtra(URL_EXTRA, url);
        if (name != null) i.putExtra(NAME, name);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        DownloadStore store = DownloadStore.get(this);
        String action = intent == null ? "" : String.valueOf(intent.getStringExtra(ACTION));
        String id = intent == null ? null : intent.getStringExtra(ID);
        synchronized (store) {
            DownloadStore.Item it = id == null ? null : store.find(id);
            switch (action) {
                case "add":
                    store.add(intent.getStringExtra(URL_EXTRA), intent.getStringExtra(NAME));
                    break;
                case "pause":
                    if (it != null && !DownloadStore.DONE.equals(it.status)) it.status = DownloadStore.PAUSED;
                    break;
                case "resume":
                    if (it != null && !DownloadStore.DONE.equals(it.status)) { it.status = DownloadStore.QUEUED; it.error = ""; }
                    break;
                case "cancel":
                case "delete":
                    if (it != null) { it.status = DownloadStore.PAUSED; store.remove(it.id); }
                    break;
                default:
                    break;
            }
            store.save(true);
        }
        pump();
        return START_NOT_STICKY;
    }

    private synchronized void pump() {
        DownloadStore store = DownloadStore.get(this);
        for (DownloadStore.Item it : store.all()) {
            if (working.size() >= MAX_PARALLEL) break;
            if (DownloadStore.QUEUED.equals(it.status) && !working.contains(it.id)) {
                working.add(it.id);
                it.status = DownloadStore.RUNNING;
                new Thread(() -> run(it), "dl-" + it.id).start();
            }
        }
        if (working.isEmpty()) {
            stopForeground(true);
            stopSelf();
        } else {
            updateNote();
        }
    }

    private void run(DownloadStore.Item it) {
        DownloadStore store = DownloadStore.get(this);
        int attempts = 0;
        while (DownloadStore.RUNNING.equals(it.status)) {
            try {
                download(it, store);
                break;
            } catch (Exception e) {
                if (!DownloadStore.RUNNING.equals(it.status)) break;
                attempts++;
                if (attempts > 6) {
                    it.status = DownloadStore.FAILED;
                    it.error = Util.online(this) ? "Download stopped. Tap Resume to continue." : "No internet. Tap Resume when you're back online.";
                    break;
                }
                try { Thread.sleep(3000L * attempts); } catch (InterruptedException ignored) { }
            }
        }
        it.speed = 0;
        store.save(true);
        synchronized (this) { working.remove(it.id); }
        pump();
    }

    private void download(DownloadStore.Item it, DownloadStore store) throws Exception {
        File file = new File(it.path);
        long have = file.exists() ? file.length() : 0;
        HttpURLConnection con = (HttpURLConnection) new URL(it.url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(45000);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MovieMaxApp");
        String cookie = CookieManager.getInstance().getCookie(it.url);
        if (cookie != null) con.setRequestProperty("Cookie", cookie);
        if (have > 0) con.setRequestProperty("Range", "bytes=" + have + "-");
        int code = con.getResponseCode();
        if (code == 416 && it.total > 0 && have >= it.total) {
            it.status = DownloadStore.DONE;
            con.disconnect();
            return;
        }
        if (code != 200 && code != 206) {
            con.disconnect();
            throw new Exception("HTTP " + code);
        }
        boolean append = code == 206 && have > 0;
        if (!append) have = 0;
        String range = con.getHeaderField("Content-Range");
        long len = con.getContentLengthLong();
        if (range != null && range.contains("/")) {
            try { it.total = Long.parseLong(range.substring(range.lastIndexOf('/') + 1).trim()); } catch (Exception ignored) { }
        } else if (len > 0) {
            it.total = have + len;
        }
        it.done = have;
        byte[] buf = new byte[128 * 1024];
        long mark = System.currentTimeMillis(), markBytes = have;
        try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(file, append)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                if (!DownloadStore.RUNNING.equals(it.status)) return; // paused or cancelled
                out.write(buf, 0, n);
                it.done += n;
                long now = System.currentTimeMillis();
                if (now - mark >= 1000) {
                    it.speed = (it.done - markBytes) * 1000 / (now - mark);
                    mark = now;
                    markBytes = it.done;
                    store.save(false);
                    updateNote();
                }
            }
        } finally {
            con.disconnect();
        }
        if (it.total > 0 && it.done < it.total) throw new Exception("Connection ended early");
        it.status = DownloadStore.DONE;
        it.total = it.done;
    }

    private Notification.Builder builder() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, "downloads");
        } else {
            b = new Notification.Builder(this);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, DownloadsActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return b.setSmallIcon(android.R.drawable.stat_sys_download).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);
    }

    private void goForeground() {
        Notification n = builder().setContentTitle("Movie max").setContentText("Preparing downloads…").build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTE_ID, n);
    }

    private void updateNote() {
        DownloadStore.Item first = null;
        for (DownloadStore.Item it : DownloadStore.get(this).all()) {
            if (DownloadStore.RUNNING.equals(it.status)) { first = it; break; }
        }
        if (first == null) return;
        int pct = first.total > 0 ? (int) (first.done * 100 / first.total) : 0;
        Notification n = builder().setContentTitle(first.name)
                .setContentText(pct + "% · " + Util.size(first.done) + (first.total > 0 ? " of " + Util.size(first.total) : ""))
                .setProgress(100, pct, first.total <= 0).build();
        getSystemService(NotificationManager.class).notify(NOTE_ID, n);
    }
}
