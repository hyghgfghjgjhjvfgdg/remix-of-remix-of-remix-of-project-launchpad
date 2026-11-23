package com.moviemax.app;
import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
final class Nav {
    static LinearLayout bar(Activity a, boolean downloads) {
        LinearLayout bar = new LinearLayout(a);
        bar.setBackgroundColor(a.getColor(R.color.card));
        String[] labels = {"Home", "Music", "LIVE", "Downloads", "Me"};
        int[] icons={R.drawable.nav_home,R.drawable.nav_music,R.drawable.nav_live,R.drawable.nav_downloads,R.drawable.nav_profile};
        String[] paths = {"/", "/music", "/channels", "", "/profile"};
        for (int i=0;i<labels.length;i++) {
            final int index=i;
            LinearLayout cell=new LinearLayout(a); cell.setOrientation(LinearLayout.VERTICAL); cell.setGravity(Gravity.CENTER);
            android.widget.ImageView icon=new android.widget.ImageView(a); icon.setImageResource(icons[i]);
            icon.setColorFilter(a.getColor(i==3 && downloads ? R.color.accent : R.color.muted));
            cell.addView(icon,new LinearLayout.LayoutParams(Util.dp(a,24),Util.dp(a,24)));
            TextView t = new TextView(a); t.setText(labels[i]); t.setTextSize(11); t.setGravity(Gravity.CENTER);
            cell.addView(t);
            t.setTextColor(a.getColor(i==3 && downloads ? R.color.accent : R.color.muted));
            cell.setOnClickListener(v -> { if(index==3) return; a.startActivity(new Intent(a,MainActivity.class).putExtra("path",paths[index]).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); a.finish(); });
            bar.addView(cell,new LinearLayout.LayoutParams(0,Util.dp(a,64),1));
        }
        return bar;
    }
}
