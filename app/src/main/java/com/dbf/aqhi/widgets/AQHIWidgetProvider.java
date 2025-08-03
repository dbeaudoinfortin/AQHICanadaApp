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

import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.dbf.aqhi.AQHIFeature;
import com.dbf.aqhi.main.AQHIMainActivity;
import com.dbf.aqhi.R;
import com.dbf.aqhi.widgets.config.WidgetConfig;
import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.aqhi.aqhiservice.AQHIService;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public abstract class AQHIWidgetProvider extends AppWidgetProvider implements AQHIFeature {

    private static final String LOG_TAG = "AQHIWidgetProvider";

    private AQHIService aqhiService;

    public AQHIWidgetProvider() {}

    public AQHIWidgetProvider(AQHIService aqhiService) {
        //AQHI Service will not be created on-the-fly
        this.aqhiService = aqhiService;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        final long startTime = System.currentTimeMillis();
        final String widgetIdsString = Arrays.toString(appWidgetIds);
        Log.i(LOG_TAG, "AQHI widget update started. Widget IDs: " +  widgetIdsString);
        initAQHIService(context, appWidgetManager, appWidgetIds);
        checkPermissions(context); //Requires AQHI Service
        rebuildRemoteViews(context, appWidgetManager, appWidgetIds);
        scheduleForcedUpdates(context);
        Log.i(LOG_TAG, "AQHI widget update complete. Duration: " + (System.currentTimeMillis() - startTime) + "ms, Widget IDs: " +  widgetIdsString);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        Log.i(LOG_TAG, "AQHI widget options changed. Widget ID: " + appWidgetId);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        int[] appWidgetIds = new int[] {appWidgetId};
        initAQHIService(context, appWidgetManager, appWidgetIds);
        rebuildRemoteViews(context, appWidgetManager, appWidgetIds);
        scheduleForcedUpdates(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "AQHI widget event received: " + intent.getAction());
        super.onReceive(context, intent);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, this.getClass()));
            Log.i(LOG_TAG, "AQHI widget package replaced. Widget IDs: " +  Arrays.toString(appWidgetIds));
            initAQHIService(context, appWidgetManager, appWidgetIds);
            rebuildRemoteViews(context, appWidgetManager, appWidgetIds);
            scheduleForcedUpdates(context);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget deleted.");
        clearConfigs(context, appWidgetIds);
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(LOG_TAG, "AQHI widget enabled.");
    }

    @Override
    public void onDisabled(Context context) {
        Log.i(LOG_TAG, "AQHI widget disabled.");
    }

    public synchronized static void scheduleForcedUpdates(Context context) {
        Log.d(LOG_TAG, "Scheduling forced widget background updates");
        //The maximum refresh period of 30 minutes is not enough
        //Enqueue a new task that will for a refresh after 10 and 20 minutes
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueueUniqueWork("widget_update_10", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(AQHIWidgetUpdateWorker.class)
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .build());

        workManager.enqueueUniqueWork("widget_update_20", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(AQHIWidgetUpdateWorker.class)
                    .setInitialDelay(20, TimeUnit.MINUTES)
                    .build());

        workManager.enqueueUniqueWork("widget_update_30", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(AQHIWidgetUpdateWorker.class)
                    .setInitialDelay(30, TimeUnit.MINUTES)
                    .build());
    }

    public void onRestored(Context context, int[] oldWidgetIds, int[] newWidgetIds) {
        Log.i(LOG_TAG, "AQHI widget restored. Old widget IDs: " +  Arrays.toString(oldWidgetIds) + ", new widget IDs: " +  Arrays.toString(newWidgetIds));
        //Note: The UI will be updated in the onUpdate() event, called right after.
        clearConfigs(context, oldWidgetIds);
    }

    private void checkPermissions(Context context) {
        //Determine if we are following the user's location to find the closest station
        //Or simply using a fixed station
        if(getAQHIService().isStationAuto()) {
            Log.i(LOG_TAG, "Location set to auto, checking permissions.");
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
    }

    private void rebuildRemoteViews(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(LOG_TAG, "Immediately Updating UIs with stale data for widget IDs: " +  Arrays.toString(appWidgetIds));
        for (int appWidgetId : appWidgetIds) {
            //Create the new remote view that will replace the existing one
            RemoteViews views = createRemoteViews(context);

            //Manually update the widget UI using stale data
            updateWidgetUI(context, views, appWidgetManager, appWidgetId);
        }
        //Asynchronously update all the widget UIs at once using fresh data when it's ready
        getAQHIService().update();
    }

    private void clearConfigs(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            clearConfig(context, appWidgetId);
        }
    }

    private void clearConfig(Context context, int appWidgetId) {
        WidgetConfig widgetConfig = new WidgetConfig(context, appWidgetId);
        widgetConfig.clearConfigs();
    }

    public void refreshWidget(Context context, RemoteViews view, AppWidgetManager appWidgetManager, int appWidgetId) {
        //Manually update the widget UI using stale data
        updateWidgetUI(context, view, appWidgetManager, appWidgetId);
        //Asynchronously update the widget UIs using fresh data when it's ready
        getAQHIService().update();
    }

    public synchronized void initAQHIService(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if(null == aqhiService) {
            aqhiService = new AQHIService(context, ()->{
                Log.i(LOG_TAG, "AQHI Service update complete. Updating UIs for widget IDs: " +  Arrays.toString(appWidgetIds));
                for (int appWidgetId : appWidgetIds) {
                    //Create the new remote view that will replace the existing one
                    RemoteViews view = createRemoteViews(context);
                    updateWidgetUI(context, view, appWidgetManager, appWidgetId);
                }
            });
            //True for widgets because they may not have background location updates enabled
            aqhiService.setAllowStaleLocation(true);
        }
    }

    private void addClickListeners(Context context, RemoteViews views) {
        //Set an intent that will open the main activity when clicking on the widget
        Intent clickIntent = new Intent(context, AQHIMainActivity.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); //Open just once

        //Attach an intent to the remote view (layout)
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
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

    public RemoteViews createRemoteViews(Context context) {
        return new RemoteViews(context.getPackageName(), getLayoutId());
    }

    @Override
    public synchronized AQHIService getAQHIService() {
        return aqhiService;
    }

    public void updateWidgetUI(Context context, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId) {

        //Attach a Click Listener that will open the main activity when you click on the widget
        addClickListeners(context, views);

        WidgetConfig widgetConfig = new WidgetConfig(context, appWidgetId);
        final int lightDarkMode = widgetConfig.getNightMode();

        //Background colour
        int bgID = R.drawable.widget_background;
        if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_YES) {
            bgID = R.drawable.widget_background_dark;
        } else if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_NO) {
            bgID = R.drawable.widget_background_light;
        }
        views.setImageViewResource(R.id.widget_background, bgID);

        //Background transparency
        views.setInt(R.id.widget_background, "setAlpha", (int) (widgetConfig.getAlpha() * 2.55f));

        //Allow the sub-classes to update their specific UI components
        updateWidgetUI(context, lightDarkMode, views, appWidgetManager, appWidgetId);
    }

    public abstract void updateWidgetUI(Context context, int lightDarkMode, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId);

    protected abstract int getLayoutId();
}