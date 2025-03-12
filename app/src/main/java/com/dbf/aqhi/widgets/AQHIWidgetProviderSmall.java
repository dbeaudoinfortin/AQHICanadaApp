package com.dbf.aqhi.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;

import com.dbf.aqhi.R;
import com.dbf.aqhi.service.AQHIService;

public class AQHIWidgetProviderSmall extends AQHIWidgetProvider {

    public AQHIWidgetProviderSmall() {
        super();
    }

    public AQHIWidgetProviderSmall(AQHIService aqhiService) {
        super(aqhiService);
    }

    public void updateWidgetUI(Context context, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId) {
        views.setTextViewText(R.id.txtAQHI, getLatestAQHIString());
        appWidgetManager.updateAppWidget(appWidgetId, views); //Push the update
    }

    @Override
    protected int getLayoutId() {
        return R.layout.widget_layout_small;
    }
}