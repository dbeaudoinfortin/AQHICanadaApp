package com.dbf.aqhi;

import com.dbf.aqhi.service.AQHIService;

import java.text.DecimalFormat;

public interface AQHIFeature {

    public static final String AQHI_DIGIT_FORMAT = "0.00";

    public AQHIService getAQHIService();

    public default String getLatestAQHIString() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        Double recentAQHI = getAQHIService().getLatestAQHI(false);
        return formatAQHIValue(recentAQHI);
    }

    public default Double getLatestAQHI() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        Double recentAQHI = getAQHIService().getLatestAQHI(true);
        if(null == recentAQHI || recentAQHI < 0.0) {
            return null;
        }
        return recentAQHI;
    }

    public default String formatAQHIValue(Double recentAQHI){
        if(null == recentAQHI) {
            return "…"; //Still fetching the value
        } else if( recentAQHI < 0.0) {
            return "?"; //Unknown
        } else if(recentAQHI % 1.0 == 0.0) {
            //No fraction
            return recentAQHI.toString();
        }
        //2-digit fractional number
        DecimalFormat df = new DecimalFormat(AQHI_DIGIT_FORMAT); // Not thread safe
        return df.format(recentAQHI);
    }
}
