package com.moviemax.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Downloads screen: progress, pause, resume, cancel, watch, export to "Movie max". */
public class DownloadsActivity extends Activity {
    private LinearLayout list;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String pendingExport = null;
    private String filter = "all";
    private final Runnable tick = new Runnable() {
        @Override public void run() { render(); ui.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        TextView title = new TextView(this);
        title.setText("Downloads");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.text));
        title.setPadding(Util.dp(this, 16), Util.dp(this, 14), Util.dp(this, 16), Util.dp(this, 6));
        root.addView(title);
        root.setFitsSystemWindows(true);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(Util.dp(this,12),Util.dp(this,8),Util.dp(this,12),Util.dp(this,8));
        for (String key : new String[]{"all","active","completed"}) {
            TextView tab = Util.pill(this,key.equals("all") ? "All" : key.equals("active") ? "Downloading" : "Completed",getColor(R.color.card));
            tab.setOnClickListener(v -> { filter=key; render(); });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,-2,1); tl.setMargins(0,0,Util.dp(this,6),0); tabs.addView(tab,tl);
        }
        root.addView(tabs);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Util.dp(this, 12), 0, Util.dp(this, 12), Util.dp(this, 12));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(Nav.bar(this, true));
        setContentView(root);
        String exportId = getIntent().getStringExtra("export");
        DownloadStore.Item exportItem = exportId == null ? null : DownloadStore.get(this).find(exportId);
        if (exportItem != null && DownloadStore.DONE.equals(exportItem.status)) export(exportItem);
    }

    @Override protected void onResume() { super.onResume(); ui.post(tick); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }

    private void render() {
        list.removeAllViews();
        List<DownloadStore.Item> items = DownloadStore.get(this).all();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No downloads yet");
            empty.setTextColor(getColor(R.color.muted));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Util.dp(this, 80), 0, 0);
            list.addView(empty);
            return;
        }
        for (DownloadStore.Item it : items) {
            boolean done = DownloadStore.DONE.equals(it.status);
            if (filter.equals("active") && done || filter.equals("completed") && !done) continue;
            list.addView(card(it));
        }
    }

    private LinearLayout card(DownloadStore.Item it) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Util.dp(this, 12), Util.dp(this, 10), Util.dp(this, 12), Util.dp(this, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.card));
        bg.setCornerRadius(Util.dp(this, 8));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Util.dp(this, 8);
        c.setLayoutParams(lp);

        TextView name = new TextView(this);
        name.setText(it.name);
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(2);
        c.addView(name);

        int pct = it.total > 0 ? (int) (it.done * 100 / it.total) : 0;
        String line;
        switch (it.status) {
            case DownloadStore.DONE: line = "Downloaded · " + Util.size(it.total > 0 ? it.total : it.done); break;
            case DownloadStore.RUNNING: line = pct + "% · " + Util.size(it.done) + (it.total > 0 ? " of " + Util.size(it.total) : "")
                    + (it.speed > 0 ? " · " + Util.size(it.speed) + "/s" : ""); break;
            case DownloadStore.QUEUED: line = "Queued"; break;
            case DownloadStore.NETWORK: line = "Waiting for internet · auto-resume · " + Util.size(it.done) + " / " + Util.size(it.total); break;
            case DownloadStore.FAILED: line = it.error.isEmpty() ? "Stopped" : it.error; break;
            default: line = "Paused · " + pct + "% · " + Util.size(it.done) + (it.total > 0 ? " of " + Util.size(it.total) : "");
        }
        TextView status = new TextView(this);
        status.setText(line);
        status.setTextColor(getColor(R.color.muted));
        status.setTextSize(12);
        c.addView(status);

        if (!DownloadStore.DONE.equals(it.status)) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(pct);
            bar.setIndeterminate(it.total <= 0 && DownloadStore.RUNNING.equals(it.status));
            c.addView(bar, new LinearLayout.LayoutParams(-1, Util.dp(this, 8)));
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Util.dp(this, 8), 0, 0);
        int accent = getColor(R.color.accent), grey = getColor(R.color.bar), red = getColor(R.color.danger);
        if (DownloadStore.DONE.equals(it.status)) {
            add(row, Util.pill(this, "▶ Watch", accent), () ->
                    startActivity(new Intent(this, PlayerActivity.class).putExtra("path", it.path)));
            add(row, Util.pill(this, "Export", grey), () -> export(it));
            add(row, Util.pill(this, "Delete", red), () -> confirm("Delete this download from the app?", () ->
                    DownloadService.send(this, "delete", it.id, null, null)));
        } else {
            if (DownloadStore.RUNNING.equals(it.status) || DownloadStore.QUEUED.equals(it.status)) {
                add(row, Util.pill(this, "Pause", grey), () -> DownloadService.send(this, "pause", it.id, null, null));
            } else {
                add(row, Util.pill(this, "Resume", accent), () -> DownloadService.send(this, "resume", it.id, null, null));
            }
            add(row, Util.pill(this, "Cancel", red), () -> confirm("Cancel this download?", () ->
                    DownloadService.send(this, "cancel", it.id, null, null)));
        }
        c.addView(row);
        return c;
    }

    private void add(LinearLayout row, TextView b, Runnable r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Util.dp(this, 8);
        b.setOnClickListener(v -> { r.run(); ui.postDelayed(this::render, 300); });
        row.addView(b, lp);
    }

    private void confirm(String msg, Runnable yes) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Yes", (d, w) -> yes.run())
                .setNegativeButton("No", null).show();
    }

    private void export(DownloadStore.Item it) {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingExport = it.id;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        Exporter.export(this, it);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 4 && pendingExport != null && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            DownloadStore.Item it = DownloadStore.get(this).find(pendingExport);
            if (it != null) export(it);
        }
        pendingExport = null;
    }
}
