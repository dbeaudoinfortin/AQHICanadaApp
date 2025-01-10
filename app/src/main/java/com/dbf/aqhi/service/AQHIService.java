package com.dbf.aqhi.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dbf.aqhi.geomet.GeoMetService;
import com.dbf.aqhi.geomet.realtime.RealtimeData;
import com.dbf.aqhi.geomet.station.Station;
import com.dbf.aqhi.location.LocationService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class AQHIService {

    private static final String LOG_TAG = "AQHIService";
    private static final long DATA_VALIDITY_DURATION = 600000; //10 minutes in milliseconds
    private static final String AQHI_PREF_KEY = "com.dbf.aqhi.service";
    private static final String STATION_NAME_KEY = "STATION_NAME";
    private static final String STATION_CODE_KEY = "STATION_CODE";
    private static final String STATION_TS_KEY = "STATION_TS";
    private static final String LATEST_AQHI_VAL_KEY = "AQHI_VAL";
    private static final String LATEST_AQHI_TS_KEY = "AQHI_TS";
    private static final String HISTORICAL_AQHI_VAL_KEY = "AQHI_HIST";
    private static final String HISTORICAL_AQHI_TS_KEY = "AQHI_HIST_TS";

    private static final Gson gson = new Gson();

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

    /**
     * Retrieves the station code of the station closest to the most recently updated user location.
     * @return Station code if it has been validated during the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Otherwise returns null.
     */
    public String getRecentStation(){
        Long ts = aqhiPref.getLong(STATION_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        return aqhiPref.getString(STATION_NAME_KEY, null);
    }

    /**
     * Retrieves the most recent AQHI value for the current station.
     * @return Double AQHI value if it has been updated within the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Otherwise returns null.
     */
    public Double getRecentAQHI(){
        Long ts = aqhiPref.getLong(LATEST_AQHI_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        return (double) aqhiPref.getFloat(LATEST_AQHI_VAL_KEY, -1f);
    }

    private static final Type historicalAQHType = new TypeToken<Map<Date, Double>>() {}.getType();
    /**
     * Retrieves the most recent AQHI historical values for the current station.
     * @return Map<Date, Double> of historical AQHI values if they have been updated within the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Otherwise returns null.
     */
    public Map<Date, Double> getHistoricalAQHI(){
        Long ts = aqhiPref.getLong(HISTORICAL_AQHI_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        String historicalAQHI = aqhiPref.getString(HISTORICAL_AQHI_VAL_KEY, null);
        if (null == historicalAQHI || historicalAQHI.isEmpty()) return Collections.EMPTY_MAP;
        try {
            return gson.fromJson(historicalAQHI, historicalAQHType);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to read saved historical AQHI data: " + historicalAQHI, e);
            return null;
        }
    }

    private void updateHistoricalAQHIPref(Map<Date, Double> val) {
        aqhiPref.edit()
                .putString(HISTORICAL_AQHI_VAL_KEY, gson.toJson(val))
                .putLong(HISTORICAL_AQHI_TS_KEY, System.currentTimeMillis())
                .apply();
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

    private void updateLatestAQHIPref(Double val) {
        SharedPreferences.Editor editor = aqhiPref.edit();
        if(null != val) {
            editor.putFloat(LATEST_AQHI_VAL_KEY, val.floatValue());
        }
        editor.putLong(LATEST_AQHI_TS_KEY, System.currentTimeMillis());
        editor.apply();
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

    private static Double extractLatestAQHIValue(List<RealtimeData> data) {
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

    private static Map<Date, Double> extractHistoricalAQHIValues(List<RealtimeData> data) {
        if (null == data || data.isEmpty()) return Collections.EMPTY_MAP;

        Map<Date, Double> sortedMap = new TreeMap<Date, Double>();
        for(RealtimeData dataPoint : data) {
            if(null == dataPoint.properties) continue;
            sortedMap.put(dataPoint.properties.getDate(), dataPoint.properties.aqhi);
        }
        return sortedMap;
    }

    /**
     * Updates both the latest and historical AQHI readings based on the user's current location. The user's closest station will be first updated if necessary.
     * These updates are done via external API calls to the GeoMet service. The data is stored in SharedPreferences and can be retrieved
     * via {@link AQHIService#getRecentAQHI}, {@link AQHIService#getRecentStation}, and {@link AQHIService#getHistoricalAQHI}.
     * The update is executed asynchronously in a new thread and may take several minutes to complete depending on the internet connection quality.
     *
     * @param @Nullable onChange An optional callback to be executed if the update results in potential data changes.
     */
    public void updateAQHI(Runnable onChange){
        //We want to do perform these calls not on the main thread
        new Thread(){
            @Override
            public void run() {
                try {
                    final double[] currentLatLong = locationService.getRecentLocation();
                    if(null == currentLatLong) return; //We don't have a recent location, we can't do anything.

                    String previousStationCode = aqhiPref.getString(STATION_CODE_KEY, null);
                    String currentStationCode;
                    //Determine if we need to update the station or not
                    if(previousStationCode == null || previousStationCode.isEmpty()
                            || lastLatLong == null || lastLatLong[0] != currentLatLong[0] || lastLatLong[1] != currentLatLong[1]) {
                        lastLatLong = currentLatLong;
                        currentStationCode = getNearestStationCode(currentLatLong, false);
                    } else {
                        //Update just the timestamp to indicate that the station is still valid
                        updateStationPref(null, null);
                        currentStationCode = previousStationCode;
                    }

                    //Get the latest AQHI reading for the station. This always needs to be fresh data, not cached.
                    List<RealtimeData> dataPoints = geoMetService.getRealtimeData(currentStationCode); //station may be null at this time
                    Double latestAQHI = extractLatestAQHIValue(dataPoints);
                    if(null == latestAQHI) {
                        //It's possible there is no data available
                        //Some stations don't have data from time-to-time.
                        //In this case, we need to force a station list update and pick another closest station.
                        currentStationCode = getNearestStationCode(currentLatLong, true);
                        if(currentStationCode == null) {
                            //If the station is still null after forcing an update, then we need wipe the preferences.
                            aqhiPref.edit().remove(STATION_NAME_KEY)
                                    .remove(STATION_CODE_KEY)
                                    .remove(STATION_TS_KEY)
                                    .remove(LATEST_AQHI_VAL_KEY)
                                    .remove(LATEST_AQHI_TS_KEY)
                                    .apply();
                            if(null != onChange) onChange.run();
                            return;
                        } else {
                            //We have a new, valid, station. Update the AQHI once more
                            dataPoints = geoMetService.getRealtimeData(currentStationCode); //station will not be null at this time
                            latestAQHI = extractLatestAQHIValue(dataPoints);
                        }
                    }

                    if(null == latestAQHI) {
                        //At this point, we may legitimately have no data. For example,
                        //it's possible we were not able to update the station list and the current station has no data
                        //If the station has not changed then we can keep the previous value (null parameter).
                        //Otherwise, we must set the AQHI value to -1, which will be displayed as "Unknown"
                        updateLatestAQHIPref(Objects.equals(currentStationCode, previousStationCode) ? null: -1.0);
                    } else {
                        //We have a legitimate updated AQHI value, save it.
                        updateLatestAQHIPref(latestAQHI);
                        updateHistoricalAQHIPref(extractHistoricalAQHIValues(dataPoints)); //We know we have at least one data point at this time
                    }

                    //Always consider this a change in data. The latest AQHI number may be the same but the station may have changed, or the historical data may have changed.
                    if(null != onChange) onChange.run();
                } catch (Throwable t) { //Catch all
                    Log.e(LOG_TAG, "Failed to update AQHI data.", t);
                }
            }
        }.start();
    }
}