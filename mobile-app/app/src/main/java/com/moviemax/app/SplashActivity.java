package com.moviemax.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Launcher entry: the animated splash and loading bar live in MainActivity, over the loading website. */
public class SplashActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }
}
