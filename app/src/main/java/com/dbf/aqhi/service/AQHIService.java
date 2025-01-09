package com.dbf.aqhi.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dbf.aqhi.geomet.GeoMetService;
import com.dbf.aqhi.geomet.realtime.RealtimeData;
import com.dbf.aqhi.geomet.station.Station;
import com.dbf.aqhi.location.LocationService;

import java.util.List;

public class AQHIService {

    private static final String LOG_TAG = "AQHIService";
    private static final long DATA_VALIDITY_DURATION = 600000; //10 minutes in milliseconds
    private static final String AQHI_PREF_KEY = "com.dbf.aqhi.service";
    private static final String STATION_NAME_KEY = "STATION_NAME";
    private static final String STATION_CODE_KEY = "STATION_CODE";
    private static final String STATION_TS_KEY = "STATION_TS";
    private static final String LATEST_AQHI_VAL_KEY = "AQHI_VAL";
    private static final String LATEST_AQHI_TS_KEY = "AQHI_TS";

    private final GeoMetService geoMetService;
    private final LocationService locationService;

    private final Context context;

    private final SharedPreferences aqhiPref;

    private double[] lastLatLong = null; //Cheap in-memory cache, fall-back to preferences if null

    public AQHIService(Context context, LocationService locationService) {
        this.geoMetService = new GeoMetService(context);
        this.locationService = locationService;
        this.context = context;
        this.aqhiPref = context.getSharedPreferences(AQHI_PREF_KEY, Context.MODE_PRIVATE);
    }
    public String getRecentStation(){
        Long ts = aqhiPref.getLong(STATION_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        return aqhiPref.getString(STATION_NAME_KEY, null);
    }

    public Double getRecentAQHI(){
        Long ts = aqhiPref.getLong(LATEST_AQHI_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        return (double) aqhiPref.getFloat(LATEST_AQHI_VAL_KEY, -1);
    }
    private void updateStationPref(String code, String name) {
        SharedPreferences.Editor editor = aqhiPref.edit();
        if(null != code && !code.isEmpty()) {
            editor.putString(STATION_CODE_KEY, code);
        }
        if(null != name && !name.isEmpty()) {
            editor.putString(STATION_NAME_KEY, name);
        }
        editor.putLong(STATION_TS_KEY, System.currentTimeMillis());
        editor.apply();
    }

    private void updateLatestAQHIPref(double val) {
        aqhiPref.edit()
            .putFloat(LATEST_AQHI_VAL_KEY, (float) val)
            .putLong(LATEST_AQHI_TS_KEY, System.currentTimeMillis())
            .apply();
    }

    private String getNearestStationCode(double[] currentLatLong, boolean force){
        if(force) Log.i(LOG_TAG, "Forcing an update to the station list.");

        //The location has changed sufficiently, we need to determine if the current station is still the closest
        Station station = geoMetService.getNearestStation(currentLatLong[1], currentLatLong[0], force);
        if(null == station) {
            //We can't figure out the location, we can't do anything
            Log.w(LOG_TAG, "Failed to determine the closest station. Latitude: " + currentLatLong[1]  + ", Longitude: " + currentLatLong[0]);
            return null;
        }

        Log.i(LOG_TAG, "Station updated. Code: " + station.properties.location_id  + ", Name: " + station.properties.location_name_en);
        updateStationPref(station.properties.location_id, station.properties.location_name_en);
        return station.properties.location_id;
    }

    private Double findLatestAQHIValue(List<RealtimeData> data) {
        if (null == data || data.isEmpty()) return null;

        //Within all the data points, find the one that is marked as the latest
        for(RealtimeData dataPoint : data) {
            if(null == dataPoint.properties) continue;
            if(dataPoint.properties.latest) {
                return dataPoint.properties.aqhi;
            }
        }
        return null;
    }

    public void updateAQHI(Runnable onChange){
        //We want to do perform these calls not on the main thread
        new Thread(){
            @Override
            public void run() {
                try {
                    final double[] currentLatLong = locationService.getRecentLocation();
                    if(null == currentLatLong) return; //We don't have a recent location, we can't do anything.

                    String stationCode = aqhiPref.getString(STATION_CODE_KEY, null);

                    //Determine is we need to update the station or not
                    if(stationCode == null || stationCode.isEmpty()
                            || lastLatLong == null || lastLatLong[0] != currentLatLong[0] || lastLatLong[1] != currentLatLong[1]) {
                        lastLatLong = currentLatLong;
                        stationCode = getNearestStationCode(currentLatLong, false);
                    } else {
                        //Update just the timestamp to indicate that the station is still valid
                        updateStationPref(null, null);
                    }

                    //Get the latest AQHI reading for the station. This always needs to be fresh data, not cached.
                    Double latestAQHI = findLatestAQHIValue(geoMetService.getRealtimeData(stationCode)); //station may be null
                    if(null == latestAQHI) {
                        //It's possible there is no data available
                        //Some stations don't have data from time-to-time.
                        //In this case, we need to force a station list update and pick another closest station.
                        stationCode = getNearestStationCode(currentLatLong, true);
                        latestAQHI = findLatestAQHIValue(geoMetService.getRealtimeData(stationCode));
                    }

                    if(null == stationCode) {
                        //If the station is still null after forcing an update, then we need wipe the preferences.
                        aqhiPref.edit().remove(STATION_NAME_KEY)
                                .remove(STATION_CODE_KEY)
                                .remove(STATION_TS_KEY)
                                .remove(LATEST_AQHI_VAL_KEY)
                                .remove(LATEST_AQHI_TS_KEY)
                                .apply();
                    } else {
                        //At this point, we may legitimately have no data.
                        //It's possible we were not able to update the station list and the current station has no data
                        //Set the AQHI value to -1, which will be displayed as "Unknown"
                        updateLatestAQHIPref(latestAQHI == null ? -1.0 : latestAQHI);
                    }

                    //TODO: Sort and save the historical AQHI data

                    //Always consider this a change. The latest AQHI number may be the same but the station may have changed, or the historical data may have changed.
                    onChange.run();
                } catch (Throwable t) { //Catch all
                    Log.e(LOG_TAG, "Failed to update AQHI data.", t);
                }
            }
        }.start();
    }
}
