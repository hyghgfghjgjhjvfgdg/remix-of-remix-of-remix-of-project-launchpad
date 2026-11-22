package com.moviemax.app;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** The website inside the app, plus offline handling, downloads and app updates. */
public class MainActivity extends Activity {
    private WebView web;
    private FrameLayout stage;
    private LinearLayout root;
    private TextView offlineBar, updateBar;
    private LinearLayout errorView;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private ValueCallback<Uri[]> fileCallback;
    private boolean mainFailed = false;
    private long lastUpdateCheck = 0;
    private File readyUpdate = null;
    private ConnectivityManager.NetworkCallback netCallback;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        web = new WebView(this);
        configure(web);
        stage.addView(web, new FrameLayout.LayoutParams(-1, -1));

        offlineBar = banner("No internet connection — showing saved content", 0xFFB3261E);
        stage.addView(offlineBar, top());
        updateBar = banner("", getColor(R.color.accent));
        FrameLayout.LayoutParams ub = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        stage.addView(updateBar, ub);
        errorView = buildErrorView();
        stage.addView(errorView, new FrameLayout.LayoutParams(-1, -1));

        root.addView(Nav.bar(this, false));
        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        watchNetwork();
        applyNetwork(Util.online(this));
        if (b != null) web.restoreState(b); else web.loadUrl(Util.site(this));
        // Pick up a domain change made while the app is open.
        new Thread(() -> Util.refreshActiveSite(getApplicationContext())).start();
    }

    private FrameLayout.LayoutParams top() { return new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); }

    private TextView banner(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(color);
        t.setPadding(Util.dp(this, 10), Util.dp(this, 8), Util.dp(this, 10), Util.dp(this, 8));
        t.setVisibility(View.GONE);
        return t;
    }

    private LinearLayout buildErrorView() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(getColor(R.color.bg));
        TextView t = new TextView(this);
        t.setText("No internet connection");
        t.setTextColor(getColor(R.color.text));
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        v.addView(t);
        TextView s = new TextView(this);
        s.setText("Your downloads still work offline.");
        s.setTextColor(getColor(R.color.muted));
        s.setPadding(0, Util.dp(this, 6), 0, Util.dp(this, 16));
        v.addView(s);
        LinearLayout row = new LinearLayout(this);
        TextView retry = Util.pill(this, "Try again", getColor(R.color.accent));
        retry.setOnClickListener(x -> { mainFailed = false; v.setVisibility(View.GONE); web.reload(); });
        TextView dl = Util.pill(this, "Open downloads", 0xFF34353C);
        dl.setOnClickListener(x -> startActivity(new Intent(this, DownloadsActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Util.dp(this, 8);
        row.addView(retry, lp);
        row.addView(dl);
        v.addView(row);
        v.setVisibility(View.GONE);
        return v;
    }

    private void configure(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // Google sign-in refuses pages that announce themselves as an in-app WebView.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "") + " MovieMaxApp/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setBackgroundColor(getColor(R.color.bg));
        w.setWebViewClient(new Client());
        w.setWebChromeClient(new Chrome());
        w.setDownloadListener((url, ua, disposition, mime, length) -> {
            String name = URLUtil.guessFileName(url, disposition, mime);
            Uri u = Uri.parse(url);
            String dl = u.getQueryParameter("dl");
            if (dl != null && !dl.isEmpty()) name = dl;
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this, "This file can't be downloaded in the app.", Toast.LENGTH_SHORT).show();
                return;
            }
            DownloadService.send(this, "add", null, url, name);
            Toast.makeText(this, "Added to Downloads", Toast.LENGTH_SHORT).show();
            refreshNav();
        });
    }

    private void refreshNav() {
        root.removeViewAt(1);
        root.addView(Nav.bar(this, false));
    }

    private boolean openOutside(Uri uri) {
        String scheme = String.valueOf(uri.getScheme());
        String host = String.valueOf(uri.getHost());
        boolean external = !scheme.equals("http") && !scheme.equals("https")
                || host.endsWith("wa.me") || host.endsWith("whatsapp.com") || host.equals("play.google.com")
                || host.endsWith("t.me");
        if (!external) return false;
        try {
            Intent i = scheme.equals("intent") ? Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, uri);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (ActivityNotFoundException | java.net.URISyntaxException e) {
            Toast.makeText(this, "No app on this phone can open that link.", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            return openOutside(r.getUrl());
        }

        @Override
        public void onPageStarted(WebView v, String url, Bitmap fav) {
            mainFailed = false;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            if (!mainFailed) errorView.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (!r.isForMainFrame()) return;
            mainFailed = true;
            errorView.setVisibility(View.VISIBLE);
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback cb) {
            customView = view;
            customCallback = cb;
            ((ViewGroup) getWindow().getDecorView()).addView(view, new FrameLayout.LayoutParams(-1, -1));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        @Override
        public void onHideCustomView() {
            if (customView == null) return;
            ((ViewGroup) getWindow().getDecorView()).removeView(customView);
            customView = null;
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            if (customCallback != null) customCallback.onCustomViewHidden();
        }

        /** Sign-in and payment pop-ups (Google, Whop, PayPal) open in a window on top. */
        @Override
        public boolean onCreateWindow(WebView view, boolean dialog, boolean user, Message msg) {
            WebView popup = new WebView(MainActivity.this);
            configure(popup);
            Dialog d = new Dialog(MainActivity.this, android.R.style.Theme_Material_NoActionBar);
            FrameLayout box = new FrameLayout(MainActivity.this);
            box.addView(popup, new FrameLayout.LayoutParams(-1, -1));
            TextView close = Util.pill(MainActivity.this, "✕ Close", 0xCC000000);
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            cp.setMargins(0, Util.dp(MainActivity.this, 10), Util.dp(MainActivity.this, 10), 0);
            box.addView(close, cp);
            close.setOnClickListener(x -> d.dismiss());
            d.setContentView(box);
            popup.setWebChromeClient(new WebChromeClient() {
                @Override public void onCloseWindow(WebView w) { d.dismiss(); }
            });
            d.setOnDismissListener(x -> popup.destroy());
            d.show();
            ((WebView.WebViewTransport) msg.obj).setWebView(popup);
            msg.sendToTarget();
            return true;
        }

        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            fileCallback = cb;
            try {
                startActivityForResult(p.createIntent(), 21);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == 21 && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }

    private void watchNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { runOnUiThread(() -> applyNetwork(true)); }
            @Override public void onLost(Network n) { runOnUiThread(() -> applyNetwork(Util.online(MainActivity.this))); }
        };
        cm.registerDefaultNetworkCallback(netCallback);
    }

    /** Offline: show saved pages from cache and a banner. Online again: refresh what failed. */
    private void applyNetwork(boolean online) {
        web.getSettings().setCacheMode(online ? WebSettings.LOAD_DEFAULT : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        offlineBar.setVisibility(online ? View.GONE : View.VISIBLE);
        if (online) {
            if (mainFailed) { mainFailed = false; errorView.setVisibility(View.GONE); web.reload(); }
            checkUpdate(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNav();
        checkUpdate(false);
    }

    // ---------- App updates (website /app-version.json) ----------

    private void checkUpdate(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastUpdateCheck < 30 * 60 * 1000) return;
        lastUpdateCheck = now;
        new Thread(() -> {
            try {
                String site = Util.site(this);
                JSONObject v = new JSONObject(Util.get(site + "/app-version.json?t=" + now, 8000));
                int code = v.optInt("versionCode", 0);
                if (code <= BuildConfig.VERSION_CODE) return;
                String apk = new URL(new URL(site + "/"), v.getString("apkUrl")).toString();
                String label = v.optString("versionName", String.valueOf(code));
                File dir = getExternalFilesDir("updates");
                File out = new File(dir, "moviemax-" + code + ".apk");
                if (!out.exists()) {
                    showUpdate("Downloading update " + label + "…", null);
                    File part = new File(dir, "moviemax-" + code + ".part");
                    fetch(apk, part, label);
                    if (!part.renameTo(out)) throw new Exception("save failed");
                }
                readyUpdate = out;
                showUpdate("Update " + label + " is ready — tap to install", this::install);
            } catch (Exception e) {
                if (readyUpdate == null) showUpdate("A new version is available — tap to download", () -> checkUpdate(true));
            }
        }).start();
    }

    private void fetch(String url, File dest, String label) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(20000);
        con.setReadTimeout(45000);
        long total = con.getContentLengthLong();
        try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0, last = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0 && System.currentTimeMillis() - last > 700) {
                    last = System.currentTimeMillis();
                    showUpdate("Downloading update " + label + "… " + (done * 100 / total) + "%", null);
                }
            }
        } finally {
            con.disconnect();
        }
    }

    private void showUpdate(String text, Runnable click) {
        runOnUiThread(() -> {
            updateBar.setText(text);
            updateBar.setVisibility(View.VISIBLE);
            updateBar.setOnClickListener(click == null ? null : v -> click.run());
        });
    }

    private void install() {
        if (readyUpdate == null) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow Movie max to install updates, then tap the update bar again.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ApkProvider.uriFor(readyUpdate), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---------- Navigation ----------

    @Override
    public void onBackPressed() {
        if (customView != null) { new Chrome().onHideCustomView(); return; }
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (netCallback != null) getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
        web.destroy();
        super.onDestroy();
    }
}
