package com.dbf.aqhi.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.dbf.aqhi.AQHIFeature;
import com.dbf.aqhi.AQHIMainActivity;
import com.dbf.aqhi.R;
import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.aqhi.service.AQHIBackgroundWorker;

import java.text.DecimalFormat;

public abstract class AQHIWidgetProvider extends AppWidgetProvider implements AQHIFeature {

    private static final String LOG_TAG = "AQHIWidgetProvider";

    protected AQHIBackgroundWorker backgroundWorker;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget updated.");

        //Open the main activity when you click on the widget
        addClickListeners(context, appWidgetManager, appWidgetIds);

        //Initialize a background thread that will periodically refresh the user's location and the latest AQHI data.
        initBackgroundWorker(context, appWidgetManager, appWidgetIds);

        //Determine if we are following the user's location to find the closest station
        //Or simply using a fixed station
        if(backgroundWorker.getAqhiService().isStationAuto()) {
            if (!PermissionService.checkLocationPermission(context)) {
                Intent intent = new Intent(context, AQHIMainActivity.class);
                intent.putExtra("request_permission", true);

                //The FLAG_ACTIVITY_NEW_TASK flag in needed to start the activity immediately
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }

            if(!PermissionService.checkBackgroundLocationPermission(context)) {
                //If the user hasn't allowed background location updates then we must take the last saved location, no matter how stale it is.
                backgroundWorker.getAqhiService().setAllowStaleLocation(true);
            }
        }

        //Force an update now, since the first time we don't have any data
        backgroundWorker.updateNow();

        //Don't wait for the data to be fetched to update the UI.
        //We can still show the old values if the data is not too stale.
        updateWidgetUIs(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        Log.i(LOG_TAG, "AQHI widget options changed.");
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        //Initialize a background thread that will periodically refresh the user's location and the latest AQHI data.
        int[] ids = {appWidgetId};
        initBackgroundWorker(context, appWidgetManager, ids);
        backgroundWorker.updateNow();
        updateWidgetUIs(context, appWidgetManager, ids);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "AQHI widget event received: " + intent.getAction());
        super.onReceive(context, intent);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Log.i(LOG_TAG, "AQHI widget package replaced.");
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName thisWidget = new ComponentName(context, this.getClass());
            int[] ids = appWidgetManager.getAppWidgetIds(thisWidget);
            initBackgroundWorker(context, appWidgetManager, ids);
            backgroundWorker.updateNow();
            updateWidgetUIs(context, appWidgetManager, ids);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget deleted.");
        killBackgroundWorker();
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(LOG_TAG, "AQHI widget enabled.");
    }

    @Override
    public void onDisabled(Context context) {
        Log.i(LOG_TAG, "AQHI widget disabled.");
        killBackgroundWorker();
    }

    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget restored.");
        //Note: backgroundWorker will be created in the onUpdate() event, called right after.
    }

    protected synchronized void initBackgroundWorker(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        //Initialize a background thread that will periodically refresh the user's location and the latest AQHI data.
        if(null == backgroundWorker) {
            backgroundWorker = new AQHIBackgroundWorker(context, ()->{
                updateWidgetUIs(context, appWidgetManager, appWidgetIds);
            });
        }
    }

    private synchronized void killBackgroundWorker() {
        if(null != backgroundWorker) {
            backgroundWorker.stop();
            backgroundWorker = null;
        }
    }

    private void addClickListeners(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            //Set an intent that will open the main activity when clicking on the widget
            Intent clickIntent = new Intent(context, AQHIMainActivity.class);

            //Attach an intent to the remote view (layout)
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            RemoteViews views = getRemoteViews(context);
            views.setOnClickPendingIntent(getWidgetRoot(), pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views); //Push the update
        }
    }

    private void updateWidgetUIs(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(LOG_TAG, "Updating AQHI widget UIs.");
        for (int appWidgetId : appWidgetIds) {
            updateWidgetUI(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public String getLatestAQHIString() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        Double recentAQHI = backgroundWorker.getAqhiService().getLatestAQHI(true);
        return formatAQHIValue(recentAQHI);
    }

    @Override
    public Double getLatestAQHI() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        Double recentAQHI = backgroundWorker.getAqhiService().getLatestAQHI(true);
        if(null == recentAQHI || recentAQHI < 0.0) {
            return null;
        }
        return recentAQHI;
    }

    protected String getCurrentStationName() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        String recentStation = backgroundWorker.getAqhiService().getStationName(true);
        if(null == recentStation) {
            return "Unknown";
        }
        return recentStation;
    }

    protected RemoteViews getRemoteViews(Context context) {
        return new RemoteViews(context.getPackageName(), getLayoutId());
    }

    @Override
    public AQHIBackgroundWorker getBackgroundWorker() {
        return backgroundWorker;
    }

    protected int getWidgetRoot() {
        return R.id.widget_root;
    }

    protected abstract void updateWidgetUI(Context context, AppWidgetManager appWidgetManager, int appWidgetId);

    protected abstract int getLayoutId();
}