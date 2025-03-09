package com.dbf.aqhi.widgets;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.RemoteViews.MARGIN_START;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.dbf.aqhi.R;
import com.dbf.aqhi.service.AQHIBackgroundWorker;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AQHIWidgetProviderLarge extends AQHIWidgetProvider {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    protected void updateWidgetUI(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = getRemoteViews(context);
        views.setTextViewText(R.id.txtAQHI, getLatestAQHIString());
        views.setTextViewText(R.id.lblStation, getCurrentStationName());
        updateArrowPosition(context, views, appWidgetManager, appWidgetId);

        views.setTextViewText(R.id.lblTimestamp, LocalTime.now().format(TIMESTAMP_FORMAT));
        appWidgetManager.updateAppWidget(appWidgetId, views); //Push the update
    }

    private void updateArrowPosition(Context context, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId) {
        Double aqhi = getLatestAQHI();
        if(null == aqhi) {
            views.setViewVisibility(R.id.imgArrow, INVISIBLE);
        } else {
            aqhi = Math.max(Math.min(aqhi,11d),1d);
            //Set the position of the arrow dynamically
            // Retrieve the widget's current options to get the width in dp:
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            float leftOffset = getArrowPositionOffset(context, minWidthDp, aqhi);

            // Now update the arrow's left padding (assuming no additional padding on top/right/bottom)
            views.setViewVisibility(R.id.imgArrow, VISIBLE);
            views.setViewLayoutMargin(R.id.imgArrow,MARGIN_START, leftOffset, TypedValue.COMPLEX_UNIT_PX);
        }
    }

    private static final int ARROW_WIDTH_DP = 30;
    private static final int BAR_PADDING_DP = 5;
    private static float getArrowPositionOffset(Context context, int minWidthDp, Double aqhi) {
        final float density = context.getResources().getDisplayMetrics().density;

        //Determine all the dimensions in pixels
        final int barPaddingPx = (int) ((BAR_PADDING_DP + (minWidthDp*0.0454)) * density);
        final int barWidthPx = (int) ((minWidthDp * density) - (barPaddingPx*2));
        final int arrowWidthPx = (int) (ARROW_WIDTH_DP * density);

        //Calculate the relative position of our arrow
        double fraction = ((aqhi-1) / 10d);
        return (float) (barPaddingPx + ((int) ((barWidthPx * fraction) - (arrowWidthPx / 2.0))));
    }

    @Override
    protected int getLayoutId() {
        return R.layout.widget_layout_large;
    }
}