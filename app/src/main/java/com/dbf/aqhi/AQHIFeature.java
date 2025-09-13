package com.dbf.aqhi;

import android.content.Context;
import android.widget.Toast;

import com.dbf.aqhi.data.aqhi.AQHIDataService;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public interface AQHIFeature {

    //Globals
    public static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA);
    public static final DateTimeFormatter TIMESTAMP_FORMAT_FULL = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a z", Locale.CANADA);
    public static final DateTimeFormatter UTC_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHX");


    public static final String AQHI_DIGIT_FORMAT = "0.00";
    public static final String AQHI_NO_DIGIT_FORMAT = "0";

    public static final int MAP_TILE_SIZE = 256;
    public static final int MAP_HEIGHT = 29699;
    public static final int MAP_WIDTH = 35000;
    public static final int MAP_LEVEL_COUNT = 9;

    public AQHIDataService getAQHIService();

    public default String getLatestAQHIString() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        return getLatestAQHIString(true);
    }

    public default String getLatestAQHIString(boolean allowStale) {
        Double recentAQHI = getAQHIService().getLatestAQHI(allowStale);
        return formatAQHIValue(recentAQHI);
    }

    public default Double getLatestAQHI() {
        //For widgets, we want to allow stale values since the update are only guaranteed to happen once per 30 minutes
        return getLatestAQHI(true);
    }

    public default Double getLatestAQHI(boolean allowStale) {
        Double recentAQHI = getAQHIService().getLatestAQHI(allowStale);
        if(null == recentAQHI || recentAQHI < 0.0) {
            return null;
        }
        return recentAQHI;
    }

    public default String getTypicalAQHIString() {
        Double typicalAQHI = getAQHIService().getTypicalAQHI();
        if(null == typicalAQHI || typicalAQHI < 0.0) {
            return null;
        }
        return formatAQHIValue(typicalAQHI);
    }

    public default void showNoPermission(Context context, boolean wipeData) {
        Toast.makeText(context, R.string.Location_perm_required, Toast.LENGTH_LONG).show();
        if(wipeData) {
            getAQHIService().setStationAuto(false);
            //AQHI data and station information are no longer valid
            getAQHIService().clearAllPreferences();
            //No point trying to update the AQHI data now, we don't have a station.
        }
    }
    public default String formatAQHIValue(Double aqhi){
        return formatAQHIValue(aqhi, AQHI_DIGIT_FORMAT);
    }

    public default String formatAQHIValue(Double aqhi, String format){
        if(null == aqhi) {
            return "…"; //Still fetching the value
        } else if(aqhi < 0.0) {
            return "?"; //Unknown
        } else if(aqhi <= 1.0) {
            return "1"; //AQHI has a minimum lower bound of 1 by definition
        } else if(aqhi >= 11.0) {
            return "11+";
        } else if(aqhi % 1.0 == 0.0) {
            //No fraction
            return "" + aqhi.intValue();
        }
        //2-digit fractional number
        DecimalFormat df = new DecimalFormat(format); // Not thread safe
        return df.format(aqhi);
    }

    public default String utcDateToFriendly(String date, String hour) {
        String dateString = date + "T" + hour + "Z";
        ZonedDateTime utcDateTime = ZonedDateTime.parse(dateString, UTC_TIMESTAMP_FORMAT.withZone(ZoneId.of("UTC")));
        return utcDateTime.withZoneSameInstant(ZoneId.systemDefault()).format(TIMESTAMP_FORMAT_FULL);
    }
}
