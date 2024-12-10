package com.dbf.aqhiwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class FaceWidgetReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.samsung.android.intent.action.REQUEST_SERVICEBOX_REMOTEVIEWS".equals(intent.getAction())) {
            //Send broadcast
            Intent newIntent = new Intent("com.samsung.android.intent.action.RESPONSE_SERVICEBOX_REMOTEVIEWS");
            newIntent.setPackage("com.android.systemui");
            newIntent.putExtra("package", context.getPackageName());
            newIntent.putExtra("pageId", "facewidget");//intent.getStringExtra("pageId"));
            newIntent.putExtra("show", true);
            newIntent.putExtra("origin", new RemoteViews(context.getPackageName(),R.layout.face_widget));
            newIntent.putExtra("aod", new RemoteViews(context.getPackageName(),R.layout.face_widget_aoc));
            context.sendBroadcast(newIntent);
        } else {
            Log.e("FaceWidgetReceiver", "unknown Intent: " + intent.getAction());
        }
    }
}
