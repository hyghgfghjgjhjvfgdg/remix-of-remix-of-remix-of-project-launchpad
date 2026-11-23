package com.moviemax.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Download list kept in the app's private storage, so it survives restarts. */
final class DownloadStore {
    static final String QUEUED = "queued", RUNNING = "running", PAUSED = "paused", DONE = "done", FAILED = "failed", NETWORK = "network";

    static final class Item {
        String id, url, name, path;
        volatile String status, error = "";
        long total, done, createdAt, speed;
    }

    private static DownloadStore instance;
    private final Context ctx;
    private final List<Item> items = new ArrayList<>();
    private long lastSave = 0;
    private int seq = 0;

    private DownloadStore(Context c) {
        ctx = c.getApplicationContext();
        load();
    }

    static synchronized DownloadStore get(Context c) {
        if (instance == null) instance = new DownloadStore(c);
        return instance;
    }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "downloads");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private void load() {
        try {
            JSONArray arr = new JSONArray(Util.prefs(ctx).getString("downloads", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.getString("id");
                it.url = o.getString("url");
                it.name = o.getString("name");
                it.path = o.getString("path");
                it.status = o.optString("status", PAUSED);
                it.error = o.optString("error", "");
                it.total = o.optLong("total");
                it.createdAt = o.optLong("createdAt");
                File f = new File(it.path);
                it.done = f.exists() ? f.length() : 0;
                // The app was closed mid-download: keep the bytes and let the user resume.
                if (DownloadRules.recoverAfterRestart(it.status)) it.status = QUEUED;
                items.add(it);
            }
        } catch (Exception ignored) {
        }
    }

    synchronized void save(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastSave < 1500) return;
        lastSave = now;
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.id).put("url", it.url).put("name", it.name).put("path", it.path)
                        .put("status", it.status).put("error", it.error).put("total", it.total)
                        .put("createdAt", it.createdAt);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        Util.prefs(ctx).edit().putString("downloads", arr.toString()).apply();
    }

    synchronized List<Item> all() {
        return new ArrayList<>(items);
    }

    synchronized Item find(String id) {
        for (Item it : items) if (it.id.equals(id)) return it;
        return null;
    }

    synchronized Item add(String url, String name) {
        for (Item it : items) {
            if (it.url.equals(url) && !FAILED.equals(it.status)) return it;
        }
        Item it = new Item();
        // Several episodes are added within the same millisecond: ids must still be unique.
        it.id = Long.toString(System.currentTimeMillis(), 36) + "-" + Integer.toString(++seq, 36)
                + Integer.toString((int) (Math.random() * 1296), 36);
        it.url = url;
        it.name = name;
        String safe = name.replaceAll("[^A-Za-z0-9._ ()-]", "_");
        it.path = new File(dir(ctx), it.id + "-" + safe).getAbsolutePath();
        it.status = QUEUED;
        it.createdAt = System.currentTimeMillis();
        items.add(0, it);
        save(true);
        return it;
    }

    synchronized void remove(String id) {
        Item it = find(id);
        if (it == null) return;
        items.remove(it);
        new File(it.path).delete();
        save(true);
    }

    /** Download list for the website's in-app Downloads page. */
    synchronized String toJson() {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : items) {
                arr.put(new JSONObject().put("id", it.id).put("name", it.name).put("status", it.status)
                        .put("error", it.error).put("total", it.total).put("done", it.done)
                        .put("speed", it.speed).put("createdAt", it.createdAt));
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    synchronized int active() {
        int n = 0;
        for (Item it : items) if (DownloadRules.recoverAfterRestart(it.status)) n++;
        return n;
    }
}
