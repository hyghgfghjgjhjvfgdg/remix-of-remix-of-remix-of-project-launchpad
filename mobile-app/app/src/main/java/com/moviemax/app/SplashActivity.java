package com.moviemax.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Animated splash: logo pops in, name slides up, while the active website is looked up. */
public class SplashActivity extends Activity {
    private boolean siteReady = false;
    private boolean animDone = false;
    private boolean opened = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.splash_logo);
        int s = Util.dp(this, 112);
        col.addView(logo, new LinearLayout.LayoutParams(s, s));

        TextView name = new TextView(this);
        name.setText("MOVIE MAX");
        name.setTextSize(26);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setLetterSpacing(0.12f);
        name.setTextColor(0xFFFFFFFF);
        name.post(() -> name.getPaint().setShader(new LinearGradient(0, 0, name.getWidth(), 0,
                0xFF2896FF, 0xFF9B46FF, Shader.TileMode.CLAMP)));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = Util.dp(this, 14);
        col.addView(name, np);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Util.dp(this, 120), Util.dp(this, 4));
        bp.topMargin = Util.dp(this, 22);
        col.addView(bar, bp);
        setContentView(root);

        logo.setScaleX(0.5f); logo.setScaleY(0.5f); logo.setAlpha(0f);
        name.setAlpha(0f); name.setTranslationY(Util.dp(this, 18));
        bar.setAlpha(0f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(650)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();
        name.animate().alpha(1f).translationY(0).setStartDelay(350).setDuration(450).start();
        bar.animate().alpha(1f).setStartDelay(700).setDuration(300).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> { animDone = true; maybeOpen(); }, 1300);
        new Thread(() -> {
            if (Util.online(this)) Util.refreshActiveSite(getApplicationContext());
            runOnUiThread(() -> { siteReady = true; maybeOpen(); });
        }).start();
        // Never hold the user on the splash for long, even on a very slow network.
        new Handler(Looper.getMainLooper()).postDelayed(() -> { siteReady = true; maybeOpen(); }, 4500);
    }

    private void maybeOpen() {
        if (opened || !siteReady || !animDone) return;
        opened = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
