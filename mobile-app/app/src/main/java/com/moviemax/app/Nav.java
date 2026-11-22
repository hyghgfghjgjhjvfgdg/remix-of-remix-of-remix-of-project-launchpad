package com.moviemax.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Compact bottom navigation shared by the website screen and the Downloads screen. */
final class Nav {
    private Nav() {}

    static LinearLayout bar(Activity a, boolean downloads) {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(a.getColor(R.color.card));
        bar.addView(tab(a, "⌂  Home", !downloads, () -> {
            if (downloads) a.finish();
        }), new LinearLayout.LayoutParams(0, Util.dp(a, 50), 1));
        int active = DownloadStore.get(a).active();
        bar.addView(tab(a, "⬇  Downloads" + (active > 0 ? " (" + active + ")" : ""), downloads, () -> {
            if (!downloads) a.startActivity(new Intent(a, DownloadsActivity.class));
        }), new LinearLayout.LayoutParams(0, Util.dp(a, 50), 1));
        return bar;
    }

    private static TextView tab(Activity a, String label, boolean on, Runnable click) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(13);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextColor(on ? a.getColor(R.color.accent) : a.getColor(R.color.muted));
        t.setOnClickListener(v -> click.run());
        return t;
    }
}
