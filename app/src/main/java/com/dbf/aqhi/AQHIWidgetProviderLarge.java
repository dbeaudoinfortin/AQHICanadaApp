package com.dbf.aqhi;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;

public class AQHIWidgetProviderLarge extends AQHIWidgetProvider {

    protected void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout_large);

        // Set default AQHI and location
        views.setTextViewText(R.id.txtLocation, "Location: Updating...");
        views.setTextViewText(R.id.txtCurrentAQHI, "Current AQHI: Fetching...");

        // Push the update
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}