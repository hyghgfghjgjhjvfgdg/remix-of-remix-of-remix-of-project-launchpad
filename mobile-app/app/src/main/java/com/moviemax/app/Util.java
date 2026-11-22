package com.moviemax.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Util {
    private Util() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("moviemax", Context.MODE_PRIVATE);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static boolean online(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** Website the app opens: last value read from Firebase, else the built-in default. */
    static String site(Context c) {
        return prefs(c).getString("site", BuildConfig.DEFAULT_SITE);
    }

    static String get(String url, int timeoutMs) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(timeoutMs);
        con.setReadTimeout(timeoutMs);
        con.setUseCaches(false);
        con.setRequestProperty("Cache-Control", "no-cache");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }

    /** Reads app_settings/active_site from Firebase and remembers it for offline starts. */
    static void refreshActiveSite(Context c) {
        try {
            JSONObject doc = new JSONObject(get(BuildConfig.ACTIVE_SITE_DOC, 4000));
            String value = doc.getJSONObject("fields").getJSONObject("value").optString("stringValue", "");
            URL u = new URL(value);
            if ("https".equals(u.getProtocol()) && u.getHost().contains(".")) {
                prefs(c).edit().putString("site", "https://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "")).apply();
            }
        } catch (Exception ignored) {
            // Offline, quota reached or not set yet: keep the saved address.
        }
    }

    static String size(long bytes) {
        if (bytes <= 0) return "0 MB";
        if (bytes >= 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    static TextView pill(Context c, String label, int color) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(12);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 16));
        b.setBackground(g);
        return b;
    }
}
