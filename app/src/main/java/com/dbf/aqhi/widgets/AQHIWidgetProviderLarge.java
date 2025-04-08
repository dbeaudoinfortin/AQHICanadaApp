package com.dbf.aqhi.widgets;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.RemoteViews.MARGIN_START;
import static android.widget.RemoteViews.MARGIN_TOP;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import androidx.appcompat.app.AppCompatDelegate;

import com.dbf.aqhi.R;
import com.dbf.aqhi.service.AQHIService;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class AQHIWidgetProviderLarge extends AQHIWidgetProvider {

    public static final float PREVIEW_SCREEN_SCALE = 0.8f;
    public static final float PREVIEW_SCREEN_RATIO = 0.33333f;
    public static final int   MIN_HEIGHT_INFO_BAR = 60;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA);

    public AQHIWidgetProviderLarge() {
        super();
    }

    public AQHIWidgetProviderLarge(AQHIService aqhiService) {
        super(aqhiService);
    }

    public void updateWidgetUI(Context context, int lightDarkMode, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId) {
        //We don't always have enough space to render top bar
        //This happens in landscape mode. Don't do this if we are in preview mode
        if(!(context instanceof Activity)) {
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            if (options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) < MIN_HEIGHT_INFO_BAR) {
                views.setViewVisibility(R.id.top_info, View.GONE);
                views.setViewLayoutMargin(R.id.bottom_info, MARGIN_TOP, 10, TypedValue.COMPLEX_UNIT_DIP);
                views.setViewVisibility(R.id.lblTimestamp, View.GONE);
            } else {
                views.setViewVisibility(R.id.top_info, View.VISIBLE);
                views.setViewLayoutMargin(R.id.bottom_info, MARGIN_TOP, 0, TypedValue.COMPLEX_UNIT_DIP);
                views.setViewVisibility(R.id.lblTimestamp, View.VISIBLE);
            }
        }

        //Update text colours
        int colourId = R.color.widget_text_color;
        if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_YES) {
            colourId = R.color.widget_text_dark_color;
        } else if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_NO) {
            colourId = R.color.widget_text_light_color;
        }
        final int txtColour = context.getResources().getColor(colourId);
        views.setTextColor(R.id.txtAQHI, txtColour);
        views.setTextColor(R.id.lblStation, txtColour);
        views.setTextColor(R.id.lblTimestamp, txtColour);

        //Update Gauge arrow
        int gaID = R.drawable.arrow;
        if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_YES) {
            gaID = R.drawable.arrow_dark;
        } else if (lightDarkMode == AppCompatDelegate.MODE_NIGHT_NO) {
            gaID = R.drawable.arrow_light;
        }
        views.setImageViewResource(R.id.imgArrow, gaID);

        //Update text contents
        views.setTextViewText(R.id.txtAQHI, getLatestAQHIString());
        views.setTextViewText(R.id.lblStation, getCurrentStationName());
        views.setTextViewText(R.id.lblTimestamp, context.getResources().getString(R.string.updated_at) + " " + LocalTime.now().format(TIMESTAMP_FORMAT));

        //Update the position of the gauge arrow
        updateArrowPosition(context, views, appWidgetManager, appWidgetId);

        //Push the update
        appWidgetManager.updateAppWidget(appWidgetId, views);
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

            //We might be rendering inside a preview container during configuration
            if(context instanceof Activity) {
                final float density = context.getResources().getDisplayMetrics().density;
                final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                minWidthDp = (int) ((screenWidth*PREVIEW_SCREEN_SCALE) / density);
            }
            final float leftOffset = getArrowPositionOffset(context, minWidthDp, aqhi) ;

            // Now update the arrow's left padding (assuming no additional padding on top/right/bottom)
            views.setViewVisibility(R.id.imgArrow, VISIBLE);
            views.setViewLayoutMargin(R.id.imgArrow, MARGIN_START, leftOffset, TypedValue.COMPLEX_UNIT_PX);
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