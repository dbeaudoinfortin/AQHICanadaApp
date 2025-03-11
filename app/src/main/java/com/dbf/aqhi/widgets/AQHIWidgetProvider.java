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
import com.dbf.aqhi.config.WidgetConfig;
import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.aqhi.service.AQHIBackgroundWorker;
import com.dbf.aqhi.service.AQHIService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class AQHIWidgetProvider extends AppWidgetProvider implements AQHIFeature {

    private static final String LOG_TAG = "AQHIWidgetProvider";

    private final Map<Integer, WidgetConfig> widgetConfigs = new HashMap<Integer, WidgetConfig>();

    private AQHIService aqhiService;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget updated.");

        //Open the main activity when you click on the widget
        addClickListeners(context, appWidgetManager, appWidgetIds);

        //Initialize a background thread that will periodically refresh the user's location and the latest AQHI data.
        initAQHIService(context, appWidgetManager, appWidgetIds);

        //Determine if we are following the user's location to find the closest station
        //Or simply using a fixed station
        if(getAQHIService().isStationAuto()) {
            if (!PermissionService.checkLocationPermission(context)) {
                Intent intent = new Intent(context, AQHIMainActivity.class);
                intent.putExtra("request_permission", true);

                //The FLAG_ACTIVITY_NEW_TASK flag in needed to start the activity immediately
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }

            if(!PermissionService.checkBackgroundLocationPermission(context)) {
                //If the user hasn't allowed background location updates then we must take the last saved location, no matter how stale it is.
                getAQHIService().setAllowStaleLocation(true);
            }
        }
        refreshWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        Log.i(LOG_TAG, "AQHI widget options changed.");
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);

        int[] ids = {appWidgetId};
        //Open the main activity when you click on the widget
        //We need to do this every time the widget is updated
        addClickListeners(context, appWidgetManager, ids);
        refreshWidgets(context, appWidgetManager, ids);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "AQHI widget event received: " + intent.getAction());
        super.onReceive(context, intent);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Log.i(LOG_TAG, "AQHI widget package replaced.");
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(context, this.getClass()));
            refreshWidgets(context, appWidgetManager, ids);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget deleted.");
        clearConfigs(appWidgetIds);
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(LOG_TAG, "AQHI widget enabled.");
    }

    @Override
    public void onDisabled(Context context) {
        Log.i(LOG_TAG, "AQHI widget disabled.");
    }

    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget restored.");
        //Note: The UI will be updated in the onUpdate() event, called right after.
        clearConfigs(oldWidgetIds);
    }

    private void addConfigs(int[] appWidgetIds) {

    }

    private void clearConfigs(int[] appWidgetIds) {
        Arrays.stream(appWidgetIds)
                .mapToObj(id->widgetConfigs.get(id))
                .filter(conf->conf != null)
                .forEach(conf->{
                    conf.clearConfigs();
                    widgetConfigs.remove(conf);
                });
    }

    public void refreshWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        initAQHIService(context, appWidgetManager, appWidgetIds);

        //Manually update the widget UIs using stale data
        for (int appWidgetId : appWidgetIds) {
            updateWidgetUI(context, getRemoteViews(context), appWidgetManager, appWidgetId);
        }

        //Asynchronously update the widget UIs using fresh data when it's ready
        getAQHIService().update();
    }

    public void refreshWidget(Context context, RemoteViews view, AppWidgetManager appWidgetManager, int appWidgetId) {
        initAQHIService(context, appWidgetManager, new int[] {appWidgetId});

        //Manually update the widget UI using stale data
        updateWidgetUI(context, view, appWidgetManager, appWidgetId);

        //Asynchronously update the widget UIs using fresh data when it's ready
        getAQHIService().update();
    }

    private synchronized void initAQHIService(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if(null == aqhiService) {
            aqhiService = new AQHIService(context, ()->{
                for (int appWidgetId : appWidgetIds) {
                    updateWidgetUIs(context, appWidgetManager, appWidgetId);
                }
            });
            aqhiService.setAllowStaleLocation(true);
        }
    }

    private void updateWidgetUIs(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.i(LOG_TAG, "Updating AQHI widget UI.");
        updateWidgetUI(context, getRemoteViews(context), appWidgetManager, appWidgetId);
    }

    private void addClickListeners(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            //Set an intent that will open the main activity when clicking on the widget
            Intent clickIntent = new Intent(context, AQHIMainActivity.class);
            clickIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); //Open just once

            //Attach an intent to the remote view (layout)
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            RemoteViews views = getRemoteViews(context);
            views.setOnClickPendingIntent(getWidgetRoot(), pendingIntent);
            appWidgetManager.updateAppWidget(appWidgetId, views); //Push the update
        }
    }

    @Override
    public String getLatestAQHIString() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        Double recentAQHI = getAQHIService().getLatestAQHI(true);
        return formatAQHIValue(recentAQHI);
    }

    @Override
    public Double getLatestAQHI() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        Double recentAQHI = getAQHIService().getLatestAQHI(true);
        if(null == recentAQHI || recentAQHI < 0.0) {
            return null;
        }
        return recentAQHI;
    }

    protected String getCurrentStationName() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        String recentStation = getAQHIService().getStationName(true);
        if(null == recentStation) {
            return "Unknown";
        }
        return recentStation;
    }

    protected RemoteViews getRemoteViews(Context context) {
        return new RemoteViews(context.getPackageName(), getLayoutId());
    }

    @Override
    public synchronized AQHIService getAQHIService() {
        return aqhiService;
    }

    protected int getWidgetRoot() {
        return R.id.widget_root;
    }

    protected abstract void updateWidgetUI(Context context, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId);

    protected abstract int getLayoutId();
}