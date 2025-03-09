package com.dbf.aqhi;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import com.dbf.aqhi.service.AQHIBackgroundWorker;

import java.text.DecimalFormat;

public interface AQHIFeature {

    public static final String AQHI_DIGIT_FORMAT = "0.00";

    public  AQHIBackgroundWorker getBackgroundWorker();

    public default String getLatestAQHIString() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        Double recentAQHI = getBackgroundWorker().getAqhiService().getLatestAQHI(false);
        return formatAQHIValue(recentAQHI);
    }

    public default String formatAQHIValue(Double recentAQHI){
        if(null == recentAQHI) {
            return "…"; //Still fetching the value
        } else if( recentAQHI < 0.0) {
            return "?"; //Unknown
        } else if(recentAQHI % 1 == 0) {
            //No fraction
            return recentAQHI.toString();
        }
        //2-digit fractional number
        DecimalFormat df = new DecimalFormat(AQHI_DIGIT_FORMAT); // Not thread safe
        return df.format(recentAQHI);
    }
}
