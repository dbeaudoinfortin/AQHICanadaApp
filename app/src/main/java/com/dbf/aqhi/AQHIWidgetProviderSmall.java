package com.dbf.aqhi;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;

public class AQHIWidgetProviderSmall extends AQHIWidgetProvider {

    protected void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout_small);

        // Set default AQHI and location
        views.setTextViewText(R.id.txtLocation, "Location: Updating...");
        views.setTextViewText(R.id.txtCurrentAQHI, "Current AQHI: Fetching...");

        // Push the update
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}