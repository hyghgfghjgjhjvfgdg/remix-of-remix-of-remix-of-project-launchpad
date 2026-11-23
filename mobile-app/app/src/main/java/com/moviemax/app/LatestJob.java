package com.moviemax.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/** Checks the website for new titles about every hour and posts a phone notification for each new one. */
public class LatestJob extends JobService {
    private static final int JOB_ID = 4711;
    private static final String CHANNEL = "latest";

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        for (JobInfo j : js.getAllPendingJobs()) if (j.getId() == JOB_ID) return;
        js.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(c, LatestJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build());
    }

    /** Also run once when the app opens, so the first list is remembered right away. */
    static void checkNow(Context c) {
        new Thread(() -> check(c.getApplicationContext())).start();
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        new Thread(() -> {
            check(getApplicationContext());
            jobFinished(p, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return true; }

    private static synchronized void check(Context c) {
        try {
            if (!Util.online(c)) return;
            Membership.refresh(c);
            String site = Util.site(c);
            JSONArray items = new JSONObject(Util.get(site + "/api/public/app-latest", 15000)).optJSONArray("items");
            if (items == null || items.length() == 0) return;
            SharedPreferences prefs = Util.prefs(c);
            Set<String> seen = new HashSet<>(prefs.getStringSet("latest_seen", new HashSet<>()));
            boolean first = seen.isEmpty();
            prefs.edit().putString("latest_items", items.toString()).apply();
            LatestWidget.refresh(c);
            Set<String> current = new HashSet<>();
            int posted = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                String id = it.optString("id");
                if (id.isEmpty()) continue;
                current.add(id);
                if (seen.contains(id)) continue;
                // First run: show only the newest title so the user sees alerts work, without a flood.
                if (posted >= (first ? 1 : 3)) continue;
                notify(c, site, it);
                posted++;
            }
            // Remember every title in today's list (so none repeats) plus some recent history.
            Set<String> keep = new HashSet<>(current);
            for (String id : seen) { if (keep.size() >= 400) break; keep.add(id); }
            prefs.edit().putStringSet("latest_seen", keep).apply();
        } catch (Exception ignored) {
        }
    }

    /** "Show me" button on the Downloads page: posts the newest title right away. */
    static void notifyNewest(Context c) {
        new Thread(() -> {
            try {
                JSONArray items = new JSONObject(Util.get(Util.site(c) + "/api/public/app-latest", 15000)).optJSONArray("items");
                if (items != null && items.length() > 0) notify(c, Util.site(c), items.getJSONObject(0));
            } catch (Exception ignored) {
            }
        }).start();
    }

    static void notify(Context c, String site, JSONObject it) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "New movies", NotificationManager.IMPORTANCE_HIGH));
        }
        String title = it.optString("title", "New on Movie max");
        String description = it.optString("description", "");
        String kind = "series".equals(it.optString("type")) ? "New series" : "New movie";
        Intent open = new Intent(c, MainActivity.class)
                .putExtra("path", it.optString("path", "/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int code = it.optString("id").hashCode();
        PendingIntent pi = PendingIntent.getActivity(c, code, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_movie)
                .setContentTitle(kind + " · Movie max")
                .setContentText(title + " · " + description)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setColor(c.getColor(R.color.accent));
        b.setContentTitle(title).setSubText(it.optString("source", "Movie max"));
        boolean subscribed = prefsMember(c);
        if (subscribed) {
            b.addAction(new Notification.Action.Builder(null, "Watch", pi).build());
            b.addAction(new Notification.Action.Builder(null, "Download", action(c, code + 1, it.optString("path", "/"), "download")).build());
        } else b.addAction(new Notification.Action.Builder(null, "Subscribe", action(c, code + 2, it.optString("path", "/"), "subscribe")).build());
        Bitmap poster = image(it.optString("poster", ""));
        if (poster != null) {
            b.setLargeIcon(poster);
            b.setStyle(new Notification.BigPictureStyle().bigPicture(poster).setSummaryText(description));
        }
        nm.notify(code, b.build());
    }

    static boolean prefsMember(Context c) { return DownloadRules.member(Util.prefs(c).getLong("member_until", 0), System.currentTimeMillis()); }
    static PendingIntent action(Context c, int code, String path, String action) {
        String sep = path.contains("?") ? "&" : "?";
        Intent i = new Intent(c, MainActivity.class).putExtra("path", path + sep + "appAction=" + action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    static Bitmap image(String url) {
        if (url == null || !url.startsWith("http")) return null;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);
            try (InputStream in = con.getInputStream()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                return BitmapFactory.decodeStream(in, null, o);
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
