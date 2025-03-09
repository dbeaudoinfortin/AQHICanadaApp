package com.dbf.aqhi;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.RemoteViews.MARGIN_START;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

public class AQHIWidgetProviderLarge extends AQHIWidgetProvider {

    protected void updateWidgetUI(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = getRemoteViews(context);
        views.setTextViewText(R.id.txtAQHI, getLatestAQHIString());
        views.setTextViewText(R.id.lblStation, getCurrentStationName());
        updateArrowPosition(context, views, appWidgetManager, appWidgetId);
        appWidgetManager.updateAppWidget(appWidgetId, views); //Push the update
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        //Initialize a background thread that will periodically refresh the user's location and the latest AQHI data.
        int[] ids = {appWidgetId};
        initBackgroundWorker(context, appWidgetManager, ids);
        backgroundWorker.updateNow();

        RemoteViews views = getRemoteViews(context);
        updateArrowPosition(context, views, appWidgetManager, appWidgetId);
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
            float leftOffset = getLeftOffset(context, minWidthDp, aqhi);

            // Now update the arrow's left padding (assuming no additional padding on top/right/bottom)
            views.setViewVisibility(R.id.imgArrow, VISIBLE);
            views.setViewLayoutMargin(R.id.imgArrow,MARGIN_START, leftOffset, TypedValue.COMPLEX_UNIT_PX);
        }
    }

    private static final int ARROW_WIDTH_DP = 30;
    private static final int BAR_PADDING_DP = 5;
    private static float getLeftOffset(Context context, int minWidthDp, Double aqhi) {
        final float density = context.getResources().getDisplayMetrics().density;

        //Determine all the dimensions in pixels
        final int barPaddingPx = (int) ((BAR_PADDING_DP + (minWidthDp*0.0454)) * density);
        final int barWidthPx = (int) ((minWidthDp * density) - (barPaddingPx*2));
        final int arrowWidthPx = (int) (ARROW_WIDTH_DP * density);

        //Calculate the relative position of our arrow
        double fraction = ((aqhi-1) / 10d);
        float leftOffset = barPaddingPx + ((int) (barWidthPx * fraction)) - (arrowWidthPx / 2);
        return leftOffset;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.widget_layout_large;
    }
}