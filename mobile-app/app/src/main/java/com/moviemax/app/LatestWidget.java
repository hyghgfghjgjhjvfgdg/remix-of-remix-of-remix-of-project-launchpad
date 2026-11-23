package com.moviemax.app;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
public class LatestWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { LatestJob.schedule(c); LatestJob.checkNow(c); new Thread(() -> refresh(c)).start(); }
    static void refresh(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new ComponentName(c,LatestWidget.class));
        if(ids.length==0) return;
        try {
            JSONArray items=new JSONArray(Util.prefs(c).getString("latest_items","[]"));
            RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.latest_widget);
            int[] posters={R.id.widget_poster0,R.id.widget_poster1,R.id.widget_poster2};
            int[] names={R.id.widget_name0,R.id.widget_name1,R.id.widget_name2};
            int[] boxes={R.id.widget_item0,R.id.widget_item1,R.id.widget_item2};
            for(int i=0;i<3 && i<items.length();i++) {
                JSONObject it=items.getJSONObject(i);
                v.setTextViewText(names[i],it.optString("title"));
                android.graphics.Bitmap image=LatestJob.image(it.optString("poster"));
                if(image!=null) v.setImageViewBitmap(posters[i],android.graphics.Bitmap.createScaledBitmap(image,150,210,true));
                v.setOnClickPendingIntent(boxes[i],LatestJob.action(c,700+i,it.optString("path","/"),"watch"));
            }
            for(int id:ids) m.updateAppWidget(id,v);
        } catch(Exception ignored) { }
    }
}
