package com.dbf.aqhi.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;

import androidx.appcompat.app.AppCompatDelegate;

import com.dbf.aqhi.R;
import com.dbf.aqhi.service.AQHIService;

public class AQHIWidgetProviderSmall extends AQHIWidgetProvider {

    public static final float PREVIEW_SCREEN_SCALE = 0.25f;
    public static final float PREVIEW_SCREEN_RATIO = 1.2f;

    public AQHIWidgetProviderSmall() {
        super();
    }

    public AQHIWidgetProviderSmall(AQHIService aqhiService) {
        super(aqhiService);
    }

    public void updateWidgetUI(Context context, int lightDarkMode, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId) {
        //Update text colours
        int colourID = R.color.widget_text_color;
        if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_YES) {
            colourID = R.color.widget_text_dark_color;
        } else if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_NO) {
            colourID  = R.color.widget_text_light_color;
        }
        final int txtColour = context.getResources().getColor(colourID);
        views.setTextColor(R.id.txtAQHI,  txtColour);
        views.setTextViewText(R.id.txtAQHI, getLatestAQHIString());
        appWidgetManager.updateAppWidget(appWidgetId, views); //Push the update
    }

    @Override
    protected int getLayoutId() {
        return R.layout.widget_layout_small;
    }
}