package com.dbf.aqhi.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;

import com.dbf.aqhi.R;

public class AQHIWidgetProviderSmall extends AQHIWidgetProvider {

    protected void updateWidgetUI(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = getRemoteViews(context);
        views.setTextViewText(R.id.txtAQHI, getLatestAQHIString());
        appWidgetManager.updateAppWidget(appWidgetId, views); //Push the update
    }

    @Override
    protected int getLayoutId() {
        return R.layout.widget_layout_small;
    }
}