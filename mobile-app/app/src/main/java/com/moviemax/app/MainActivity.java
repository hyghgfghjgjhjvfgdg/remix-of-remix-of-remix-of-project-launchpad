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
import android.webkit.JavascriptInterface;
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
    private FrameLayout splash;
    private android.widget.ProgressBar splashBar;
    private TextView splashStatus;
    private long splashStart;
    private boolean splashGone = false;
    private String pendingExport = null;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

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
        updateBar = banner("", getColor(R.color.card));
        FrameLayout.LayoutParams ub = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        ub.setMargins(Util.dp(this,12),0,Util.dp(this,12),Util.dp(this,80));
        updateBar.setPadding(Util.dp(this,16),Util.dp(this,14),Util.dp(this,16),Util.dp(this,14));
        android.graphics.drawable.GradientDrawable updateBg = new android.graphics.drawable.GradientDrawable();
        updateBg.setColor(getColor(R.color.card)); updateBg.setCornerRadius(Util.dp(this,8));
        updateBg.setStroke(Util.dp(this,1),getColor(R.color.accent)); updateBar.setBackground(updateBg);
        stage.addView(updateBar, ub);
        errorView = buildErrorView();
        stage.addView(errorView, new FrameLayout.LayoutParams(-1, -1));
        splash = buildSplash();
        stage.addView(splash, new FrameLayout.LayoutParams(-1, -1));

        // The website draws the app's own top and bottom bars (Home, Music, LIVE, Downloads, Me).
        root.setFitsSystemWindows(true);
        setContentView(root);
        web.addJavascriptInterface(new Bridge(), "MovieMaxApp");

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
        watchNetwork();
        applyNetwork(Util.online(this));
        String start = startUrl(getIntent());
        String firstSite = Util.site(this);
        if (start != null) web.loadUrl(start);
        else if (b != null) { web.restoreState(b); hideSplash(); }
        else {
            // Open instantly from saved pages/images, then refresh when the network answers.
            web.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
            web.loadUrl(firstSite);
            if (Util.online(this)) ui.postDelayed(() -> web.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT), 4000);
        }
        LatestJob.schedule(this);
        LatestJob.checkNow(this);
        // Pick up a domain change; if the website moved, open the new address.
        new Thread(() -> {
            Util.refreshActiveSite(getApplicationContext());
            String now = Util.site(this);
            if (!now.equals(firstSite) && start == null) runOnUiThread(() -> web.loadUrl(now));
        }).start();
        // Never keep the user on the splash, even on a very slow network.
        ui.postDelayed(this::hideSplash, 15000);
    }

    /** Splash shown inside the app until the website has fully loaded underneath it. */
    private FrameLayout buildSplash() {
        splashStart = System.currentTimeMillis();
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(getColor(R.color.bg));
        f.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.splash_logo);
        col.addView(logo, new LinearLayout.LayoutParams(Util.dp(this, 104), Util.dp(this, 104)));
        TextView name = new TextView(this);
        name.setText("MOVIE MAX");
        name.setTextSize(24);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLetterSpacing(0.12f);
        name.setTextColor(0xFFFFFFFF);
        name.post(() -> name.getPaint().setShader(new android.graphics.LinearGradient(0, 0, name.getWidth(), 0,
                0xFF2896FF, 0xFF9B46FF, android.graphics.Shader.TileMode.CLAMP)));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = Util.dp(this, 12);
        col.addView(name, np);
        splashBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        splashBar.setMax(100);
        splashBar.setProgress(5);
        splashBar.setProgressTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Util.dp(this, 180), Util.dp(this, 6));
        bp.topMargin = Util.dp(this, 26);
        col.addView(splashBar, bp);
        splashStatus = new TextView(this);
        splashStatus.setText(Util.online(this) ? "Connecting…" : "Offline · opening saved pages…");
        splashStatus.setTextColor(getColor(R.color.muted));
        splashStatus.setTextSize(12);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = Util.dp(this, 10);
        col.addView(splashStatus, sp);
        logo.setScaleX(0.6f); logo.setScaleY(0.6f); logo.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(600)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
        name.setAlpha(0f); name.setTranslationY(Util.dp(this, 16));
        name.animate().alpha(1f).translationY(0).setStartDelay(300).setDuration(420).start();
        // After the logo, the app shows its own loading skeleton (never the half-drawn website).
        final View sk = buildSkeleton();
        sk.setAlpha(0f);
        f.addView(sk, new FrameLayout.LayoutParams(-1, -1));
        ui.postDelayed(() -> { if (!splashGone) { col.animate().alpha(0f).setDuration(220).start(); sk.animate().alpha(1f).setDuration(260).start(); } }, 1100);
        return f;
    }

    private View block(int w, int h, int r) {
        View v = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xFF1E2230); g.setCornerRadius(Util.dp(this, r)); v.setBackground(g);
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v, "alpha", 1f, 0.45f);
        a.setDuration(800); a.setRepeatMode(android.animation.ValueAnimator.REVERSE); a.setRepeatCount(-1); a.start();
        v.setLayoutParams(new LinearLayout.LayoutParams(w < 0 ? w : Util.dp(this, w), Util.dp(this, h)));
        return v;
    }

    /** Native skeleton matching the app layout: header, tabs, banner, poster rows, bottom bar. */
    private View buildSkeleton() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(getColor(R.color.bg));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = Util.dp(this, 12);
        c.setPadding(pad, pad, pad, pad);
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(block(80, 26, 6));
        View search = block(-1, 34, 17); LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, Util.dp(this, 34), 1f); sl.setMargins(pad, 0, pad, 0); search.setLayoutParams(sl); head.addView(search);
        head.addView(block(32, 32, 16));
        c.addView(head);
        LinearLayout tabs = new LinearLayout(this); tabs.setPadding(0, pad, 0, pad);
        for (int i = 0; i < 6; i++) { View t = block(52, 14, 7); ((LinearLayout.LayoutParams) t.getLayoutParams()).rightMargin = Util.dp(this, 10); tabs.addView(t); }
        c.addView(tabs);
        c.addView(block(-1, 170, 10));
        for (int r = 0; r < 2; r++) {
            View title = block(120, 16, 6); ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(0, pad + pad / 2, 0, pad / 2); c.addView(title);
            LinearLayout row = new LinearLayout(this);
            for (int i = 0; i < 4; i++) { View p = block(0, 150, 8); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Util.dp(this, 150), 1f); lp.rightMargin = i < 3 ? Util.dp(this, 8) : 0; p.setLayoutParams(lp); row.addView(p); }
            c.addView(row);
        }
        wrap.addView(c, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setBackgroundColor(0xFF12141C);
        nav.setPadding(pad, Util.dp(this, 10), pad, Util.dp(this, 10));
        for (int i = 0; i < 5; i++) { View d = block(26, 26, 13); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Util.dp(this, 26), 1f); lp.setMargins(Util.dp(this, 18), 0, Util.dp(this, 18), 0); d.setLayoutParams(lp); nav.addView(d); }
        wrap.addView(nav, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return wrap;
    }

    private void splashProgress(int pct) {
        if (splashGone || splashBar == null) return;
        int p = Math.max(splashBar.getProgress(), Math.min(pct, 96));
        splashBar.setProgress(p);
        splashStatus.setText(p < 30 ? "Connecting…" : p < 80 ? "Loading movies… " + p + "%" : "Almost ready… " + p + "%");
    }

    /** Fades the splash out once the page is ready (at least 1.2 s so the animation is seen). */
    private void hideSplash() {
        if (splashGone) return;
        long wait = Math.max(0, 1800 - (System.currentTimeMillis() - splashStart));
        ui.postDelayed(() -> {
            if (splashGone) return;
            splashGone = true;
            splashBar.setProgress(100);
            splashStatus.setText("Ready");
            splash.animate().alpha(0f).setDuration(280).withEndAction(() -> splash.setVisibility(View.GONE)).start();
        }, wait);
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
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.drawable.offline_icon);
        v.addView(icon, new LinearLayout.LayoutParams(Util.dp(this, 140), Util.dp(this, 140)));
        t.setText("No internet connection");
        t.setTextColor(getColor(R.color.text));
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        v.addView(t);
        TextView s = new TextView(this);
        s.setText("Your saved movies are ready to watch.");
        s.setTextColor(getColor(R.color.muted));
        s.setPadding(0, Util.dp(this, 6), 0, Util.dp(this, 16));
        v.addView(s);
        LinearLayout row = new LinearLayout(this);
        TextView retry = Util.pill(this, "Try again", getColor(R.color.accent));
        retry.setOnClickListener(x -> { mainFailed = false; v.setVisibility(View.GONE); web.reload(); });
        TextView dl = Util.pill(this, "Open downloads", getColor(R.color.card));
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
        // Fit every phone: use the page's own mobile layout, ignore the phone's font-size setting.
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
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

    private void refreshNav() { /* the website's own bottom bar shows Downloads */ }

    /** Page to open from the website's "Get app" link (moviemax://open) or a new-movie notification. */
    private String startUrl(Intent i) {
        if (i == null) return null;
        String site = Util.site(this);
        String path = i.getStringExtra("path");
        String session = null;
        Uri data = i.getData();
        if (data != null && "moviemax".equals(data.getScheme())) {
            path = data.getQueryParameter("path");
            session = data.getQueryParameter("s");
        }
        if (path == null || !path.startsWith("/") || path.startsWith("//")) return null;
        String url = site + path;
        if (session != null && session.matches("[A-Za-z0-9_-]{20,20000}")) url += "#mmx_s=" + session;
        return url;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        String start = startUrl(i);
        if (start != null) web.loadUrl(start);
    }

    /** Called by the website (window.MovieMaxApp) for downloads and the Downloads tab. */
    private class Bridge {
        @JavascriptInterface
        public void download(String url, String name) {
            runOnUiThread(() -> {
                Uri u = Uri.parse(url);
                String host = String.valueOf(u.getHost());
                String siteHost = String.valueOf(Uri.parse(web.getUrl() == null ? Util.site(MainActivity.this) : web.getUrl()).getHost());
                if (!"https".equals(u.getScheme()) && !"http".equals(u.getScheme()) || !host.equals(siteHost)) return;
                String file = name == null || name.trim().isEmpty() ? URLUtil.guessFileName(url, null, "video/mp4") : name;
                DownloadService.send(MainActivity.this, "add", null, url, file);
                Toast.makeText(MainActivity.this, "Added to Downloads", Toast.LENGTH_SHORT).show();
            });
        }

        /** Older website versions call this; the Downloads page now lives inside the website. */
        @JavascriptInterface
        public void openDownloads() {
            runOnUiThread(() -> {
                if (Util.online(MainActivity.this)) web.loadUrl(Util.site(MainActivity.this) + "/downloads");
                else startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
            });
        }

        @JavascriptInterface
        public String downloads() { return DownloadStore.get(MainActivity.this).toJson(); }

        /** pause · resume · cancel · delete for one download. */
        @JavascriptInterface
        public void control(String action, String id) {
            if (!"pause".equals(action) && !"resume".equals(action) && !"cancel".equals(action) && !"delete".equals(action)) return;
            DownloadService.send(MainActivity.this, action, id, null, null);
        }

        @JavascriptInterface
        public void watch(String id) {
            DownloadStore.Item it = DownloadStore.get(MainActivity.this).find(id);
            if (it == null || !DownloadStore.DONE.equals(it.status)) return;
            runOnUiThread(() -> startActivity(new Intent(MainActivity.this, PlayerActivity.class).putExtra("path", it.path)));
        }

        @JavascriptInterface
        public void exportFile(String id) { runOnUiThread(() -> exportDownload(id)); }

        /** Asks the home screen to place the Latest movies widget. Returns false where phones don't support it. */
        @JavascriptInterface
        public boolean addWidget() {
            if (Build.VERSION.SDK_INT < 26) return false;
            android.appwidget.AppWidgetManager m = getSystemService(android.appwidget.AppWidgetManager.class);
            if (m == null || !m.isRequestPinAppWidgetSupported()) return false;
            return m.requestPinAppWidget(new android.content.ComponentName(MainActivity.this, LatestWidget.class), null, null);
        }

        /** True when the phone allows Movie max notifications. */
        @JavascriptInterface
        public boolean notificationsOn() {
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            return nm != null && nm.areNotificationsEnabled();
        }

        /** Turns notifications on (asks, or opens settings) and shows the newest title right away. */
        @JavascriptInterface
        public void enableNotifications() {
            runOnUiThread(() -> {
                if (notificationsOn()) { LatestJob.notifyNewest(MainActivity.this); return; }
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
                    return;
                }
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            });
        }

        @JavascriptInterface
        public void auth(String refreshToken) { Membership.remember(MainActivity.this, refreshToken); }

        @JavascriptInterface
        public void membership(long until) {
            Util.prefs(MainActivity.this).edit().putLong("member_until", until).apply();
        }

        @JavascriptInterface
        public void checkUpdates() { runOnUiThread(() -> checkUpdate(true)); }

        @JavascriptInterface
        public String version() { return BuildConfig.VERSION_NAME; }
    }

    private void exportDownload(String id) {
        DownloadStore.Item it = DownloadStore.get(this).find(id);
        if (it == null) return;
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingExport = id;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        Exporter.export(this, it);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        boolean ok = r.length > 0 && r[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (code == 4 && pendingExport != null && ok) exportDownload(pendingExport);
        if (code == 4) pendingExport = null;
        if (code == 9 && ok) LatestJob.checkNow(this);
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
            // Give the page a moment to draw its movies before the splash fades.
            ui.postDelayed(MainActivity.this::hideSplash, 1200);
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (!r.isForMainFrame()) return;
            mainFailed = true;
            errorView.setVisibility(View.VISIBLE);
            hideSplash();
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView v, int p) { splashProgress(p); }

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
            boolean available = false;
            try {
                String site = Util.site(this);
                JSONObject v = new JSONObject(Util.get(site + "/app-version.json?t=" + now, 8000));
                int code = v.optInt("versionCode", 0);
                if (code <= BuildConfig.VERSION_CODE) return;
                available = true;
                String apk = new URL(new URL(site + "/"), v.getString("apkUrl")).toString();
                String label = v.optString("versionName", String.valueOf(code));
                File dir = getExternalFilesDir("updates");
                File out = new File(dir, "moviemax-" + code + ".apk");
                if (!out.exists()) {
                    showUpdate("Movie max " + label + " · Downloading update…", null);
                    File part = new File(dir, "moviemax-" + code + ".part");
                    fetch(apk, part, label);
                    if (!part.renameTo(out)) throw new Exception("save failed");
                }
                readyUpdate = out;
                showUpdate("Movie max " + label + " is ready · Tap to install", this::install);
            } catch (Exception e) {
                if (available && readyUpdate == null) showUpdate("A new version is available — tap to download", () -> checkUpdate(true));
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
                    showUpdate("Movie max " + label + " · Downloading update… " + (done * 100 / total) + "%", null);
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
