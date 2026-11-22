package com.moviemax.app;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.VideoView;

/** Plays a downloaded movie from the app's own storage, no internet needed. */
public class PlayerActivity extends Activity {
    private VideoView video;
    private int position = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        video = new VideoView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.gravity = android.view.Gravity.CENTER;
        root.addView(video, lp);
        setContentView(root);
        MediaController mc = new MediaController(this);
        mc.setAnchorView(video);
        video.setMediaController(mc);
        video.setVideoURI(Uri.fromFile(new java.io.File(getIntent().getStringExtra("path"))));
        if (b != null) position = b.getInt("pos", 0);
        video.setOnPreparedListener(mp -> {
            if (position > 0) video.seekTo(position);
            video.start();
            mc.show(2500);
        });
    }

    @Override protected void onPause() { super.onPause(); position = video.getCurrentPosition(); video.pause(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("pos", position); }
}
