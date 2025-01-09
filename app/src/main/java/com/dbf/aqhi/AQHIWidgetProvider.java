package com.dbf.aqhi;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

public class AQHIWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

        final FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(context);
        final LocationRequest locationRequest  = new LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 0)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(0)
                .setMaxUpdateDelayMillis(0)
                .setMaxUpdates(1)
                .build();

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout_small);

        // Set default AQHI and location
        views.setTextViewText(R.id.txtLocation, "Location: Updating...");
        views.setTextViewText(R.id.txtCurrentAQHI, "Current AQHI: Fetching...");

        // Push the update
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}