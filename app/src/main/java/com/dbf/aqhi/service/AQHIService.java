package com.dbf.aqhi.service;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.util.Pair;

import com.dbf.aqhi.R;
import com.dbf.aqhi.Utils;
import com.dbf.aqhi.api.geomet.GeoMetService;
import com.dbf.aqhi.api.geomet.data.Data;
import com.dbf.aqhi.api.geomet.data.forecast.ForecastData;
import com.dbf.aqhi.api.geomet.data.realtime.RealtimeData;
import com.dbf.aqhi.api.geomet.station.Station;
import com.dbf.aqhi.api.weather.WeatherService;
import com.dbf.aqhi.api.weather.alert.Alert;
import com.dbf.aqhi.location.LocationService;
import com.dbf.utils.stacktrace.StackTraceCompactor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

public class AQHIService {

    private static final String LOG_TAG = "AQHIService";
    private static final Object GLOBAL_SYNC_OBJECT = new Object();

    //Used for Gson conversion
    private static final Type gsonAQHIType = new TypeToken<Map<Date, Double>>(){}.getType();
    private static final Type gsonAlertType = new TypeToken<List<Alert>>(){}.getType();

    //How long we can display the data on screen before we throw it out because its too old
    private static final long DATA_VALIDITY_DURATION = 30 * 60 * 1000; //30 minutes in milliseconds

    //How long we need to wait before loading fresh data, so we don't make to many API calls
    private static final long DATA_REFRESH_MIN_DURATION = 5 * 60 * 1000; //5 minutes in milliseconds

    //Shared preferences keys
    private static final String AQHI_PREF_KEY = "com.dbf.aqhi.service";
    private static final String STATION_NAME_KEY = "STATION_NAME";
    private static final String STATION_NAPS_ID_KEY = "STATION_NAPS_ID";
    private static final String STATION_TZ_KEY = "STATION_TZ";
    private static final String STATION_LAT_KEY = "STATION_LAT";
    private static final String STATION_LON_KEY = "STATION_LON";
    private static final String STATION_CODE_KEY = "STATION_CODE";
    private static final String STATION_AUTO_KEY = "STATION_AUTO";
    private static final String STATION_TS_KEY = "STATION_TS";
    private static final String TYPICAL_AQHI_VAL_KEY = "TYPICAL_AQHI_VAL";
    private static final String TYPICAL_AQHI_TS_KEY = "TYPICAL_AQHI_TS";
    private static final String LATEST_AQHI_VAL_KEY = "LATEST_AQHI_VAL";
    private static final String LATEST_AQHI_TS_KEY = "LATEST_AQHI_TS";
    private static final String HISTORICAL_AQHI_VAL_KEY = "AQHI_HIST";
    private static final String HISTORICAL_AQHI_TS_KEY = "AQHI_HIST_TS";
    private static final String FORECAST_AQHI_VAL_KEY = "AQHI_FORE";
    private static final String FORECAST_AQHI_TS_KEY = "AQHI_FORE_TS";
    private static final String ALERTS_VAL_KEY = "ALERTS_VAL";
    private static final String ALERTS_TS_KEY = "ALERTS_TS";

    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX") // ISO 8601 format
            .enableComplexMapKeySerialization() //Wow! https://github.com/google/gson/issues/1328
            .create();

    private final GeoMetService geoMetService;
    private final WeatherService weatherService;
    private final LocationService locationService;
    private final SharedPreferences aqhiPref;

    //Callback to make when the data changes.
    private Runnable onChange;

    //Cheap in-memory cache, fall-back to preferences if null
    private double[] lastLatLong = null;

    //Cheap in-memory cache, fall-back to the file system if null
    private static final Map<Integer, double[]> NAPS_STATIONS = new HashMap<>();

    //Cache the typical AQHI data in memory so we don't need to load it from the filesystem each time
    //The map of data will be wiped clean if the NAPS ID for the station changes.
    //In the form of Map<Hour of day, Map<Week of year, AQHI value>>
    private static final Type TYPICAL_AQHI_Type = new TypeToken<Map<Integer, Map<Integer, Float>>>(){}.getType();
    private Map<Integer, Map<Integer, Float>> typicalAQHIMap;
    private Integer typicalAQHINAPSID;

    //Indicates that the typical AQHI values will be loaded
    //This is not used for widgets.
    private final boolean loadTypicalAQHI;

    //This is mainly needed for widgets that run in the background.
    //May also be needed is the background location permission is not set
    private boolean allowStaleLocation = true;

    private final Context context;

    public AQHIService(Context context, Runnable onChange) {
        this(context, onChange, false);
    }

    public AQHIService(Context context, Runnable onChange, boolean loadTypicalAQHI) {
        Log.i(LOG_TAG, "Initializing AQHI service.");
        locationService = new LocationService(context);
        this.geoMetService = new GeoMetService(context);
        this.weatherService = new WeatherService();
        this.onChange = onChange;
        this.aqhiPref = context.getSharedPreferences(AQHI_PREF_KEY, Context.MODE_PRIVATE);
        this.context = context;
        this.loadTypicalAQHI = loadTypicalAQHI;
    }

    public void update() {
        Log.i(LOG_TAG, "Updating AQHI data.");
        if(this.isStationAuto()) {
            //Asynchronously update the AQHI data only once the location update has finished.
            locationService.updateLocation(this::updateAQHI);
        } else {
            //No need for a location update, update the AQHI data now.
            updateAQHI();
        }
    }

    /**
     * Updates AQHI data asynchronously by executing {@link AQHIService#updateAQHISync} in a new thread.
     * This may take several minutes to complete depending on the internet connection quality.
     * The optional callback onChange will be executed after the update is complete.
     */
    public void updateAQHI(){
        //We want to perform these calls not on the main thread
        new Thread(() -> {
            try {
                updateAQHISync();
                //Always consider this a change in data.
                //The latest AQHI number may be the same but the station may have changed, or the historical data may have changed.
                if(null != onChange) onChange.run();
            } catch (Throwable t) { //Catch all
                Log.e(LOG_TAG, "Failed to update AQHI data.", t);
            }
        }).start();
    }

    /**
     * Updates both the latest, historical and forecast AQHI readings based on the user's current location. The user's closest station will be first updated if necessary.
     * These updates are done via external API calls to the GeoMet service. The data is stored in SharedPreferences and can be retrieved
     * via {@link AQHIService#getLatestAQHI}, {@link AQHIService#getStationName}, {@link AQHIService#getHistoricalAQHI}, and
     * The update is executed synchronously and may take several minutes to complete depending on the internet connection quality.
     *
     */
    private void updateAQHISync() {
        if(!isInternetAvailable()) {
            Log.i(LOG_TAG, "Network is down. Skipping update.");
            return;
        }
        Log.i(LOG_TAG, "Attempting to update the AQHI data for the current location.");

        //This code is stateful, we don't want to run multiple updates at the same time
        synchronized (GLOBAL_SYNC_OBJECT) {
            final boolean stationAuto = isStationAuto();
            final String previousStationCode = aqhiPref.getString(STATION_CODE_KEY, null);
            String currentStationCode;
            if (stationAuto) {
                //Station is determined automatically based on the user's location
                currentStationCode = determineCurrentLocation(previousStationCode, false);
            } else {
                //Station is set manually by the user
                currentStationCode = this.getStationCode(true);
            }

            if (null == currentStationCode || !currentStationCode.equals(previousStationCode)) {
                //We have changed stations, all of our current data is invalid
                Log.i(LOG_TAG, "Station has changed. Old station: " + previousStationCode + " New station: " + currentStationCode);
                clearAllData();
            }

            //Get the latest AQHI reading for the station.
            if (null != currentStationCode && fetchLatestAQHIData(currentStationCode)) {
                Log.i(LOG_TAG, "Fresh data found for station " + currentStationCode);
            } else if (stationAuto) {
                //It's possible there is no data available for the station
                //Some stations don't have data from time-to-time.
                //In this case, we need to force a station list update and pick another closest station.
                Log.i(LOG_TAG, "Could not fetch fresh data for station " + currentStationCode);
                currentStationCode = determineCurrentLocation(previousStationCode, true);
                if (currentStationCode == null) {
                    //If the station is still null after forcing an update, then either the API is broken or the device is offline
                    Log.w(LOG_TAG, "Cannot determine the closest station. Device may be offline.");
                    return;
                }

                //We have a new, valid, station. Update the AQHI once more
                if (fetchLatestAQHIData(currentStationCode)) {
                    Log.i(LOG_TAG, "Fresh data found for station " + currentStationCode);
                } else {
                    //At this point, we may legitimately have no data.
                    //For example, it's possible we were not able to update the station list and the current station has no data
                    Log.e(LOG_TAG, "Could not fetch fresh data for station " + currentStationCode);
                }
            } else {
                Log.i(LOG_TAG, "Could not fetch fresh data for manually set station: " + currentStationCode);
            }

            //Get the latest alerts
            if (null != currentStationCode) fetchLatestAlertData();
        }
    }

    /**
     * Deletes all stored AQHI data.
     */
    public void clearAllData() {
        Log.i(LOG_TAG, "Clearing all AQHI data.");
        aqhiPref.edit()
                .remove(TYPICAL_AQHI_VAL_KEY)
                .remove(TYPICAL_AQHI_TS_KEY)
                .remove(LATEST_AQHI_VAL_KEY)
                .remove(LATEST_AQHI_TS_KEY)
                .remove(HISTORICAL_AQHI_VAL_KEY)
                .remove(HISTORICAL_AQHI_TS_KEY)
                .remove(FORECAST_AQHI_VAL_KEY)
                .remove(FORECAST_AQHI_TS_KEY)
                .remove(ALERTS_VAL_KEY)
                .remove(ALERTS_TS_KEY)
                .apply();
    }

    /**
     * Deletes all stored preferences, including both AQHI data and station data.
     */
    public void clearAllPreferences() {
        clearAllData();
        aqhiPref.edit()
                .remove(STATION_NAME_KEY)
                .remove(STATION_NAPS_ID_KEY)
                .remove(STATION_CODE_KEY)
                .remove(STATION_TZ_KEY)
                .remove(STATION_LAT_KEY)
                .remove(STATION_LON_KEY)
                .remove(STATION_TS_KEY)
                .apply();

    }

    private String determineCurrentLocation(String previousStationCode, boolean forceStationUpdate) {
        Log.d(LOG_TAG, "Determining the current location.");
        final double[] currentLatLong = locationService.getRecentLocation(allowStaleLocation && !forceStationUpdate);
        if(null == currentLatLong) {
            Log.w(LOG_TAG, "Cannot determine the current location. Cannot update AQHI data.");
            return null; //We don't have a recent location, we can't do anything.
        }

        //Determine if we need to update the station or not
        if(previousStationCode == null || previousStationCode.isEmpty()
                || lastLatLong == null || lastLatLong[0] != currentLatLong[0] || lastLatLong[1] != currentLatLong[1]) {
            //Our previous location was not cached, or our geographic coordinates have changed
            lastLatLong = currentLatLong;
            return findNearestStationCode(currentLatLong, forceStationUpdate);
        }

        //Update just the timestamp to indicate that the station is still valid
        setStation(null, null);
        return previousStationCode;
    }

    private String findNearestStationCode(double[] currentLatLong, boolean force){
        if(force) Log.i(LOG_TAG, "Forcing an update to the station list.");

        //The location has changed sufficiently, we need to determine if the current station is still the closest
        Station station = geoMetService.getNearestStation(currentLatLong[1], currentLatLong[0], force);
        if(null == station) {
            //We can't figure out the location, we can't do anything
            Log.w(LOG_TAG, "Failed to determine the closest station. Latitude: " + currentLatLong[1]  + ", Longitude: " + currentLatLong[0]);
            return null;
        }

        Log.i(LOG_TAG, "Station updated. Code: " + station.properties.location_id  + ", Name: " + station.properties.location_name_en);
        setStation(station);
        return station.properties.location_id;
    }

    /**
     * Sets the user's station
     * @param station
     */
    public void setStation(Station station) {
        Pair<Integer, Double> napsStation = station == null ? null : determineNAPSSite(station.geometry.coordinates.get(1), station.geometry.coordinates.get(0));
        setStation(station, napsStation);
    }

    /**
     * Determines the matching NAPS site ID and time zone based latitude and longitude coordinate.
     * This performs a fuzzy match that tries to get close enough.
     *
     * The NAPS station definition file will be loaded and cached in memory if it wasn't already.
     *
     * @param latitude
     * @param longitude
     * @return A Pair<Integer, Double> representing the NAPS site ID and time zone offset.
     *
     */
    private Pair<Integer, Double> determineNAPSSite(double latitude, double longitude) {
        synchronized (NAPS_STATIONS) {
            if (NAPS_STATIONS.isEmpty()) {
                Log.i(LOG_TAG, "Loading NAPS site definition file.");
                String rawJson = Utils.loadCompressedResource(context, R.raw.sites);
                if(null != rawJson) {
                    try {
                        JSONArray jsonArray = new JSONArray(rawJson);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject locationObj = jsonArray.getJSONObject(i);
                            NAPS_STATIONS.put(locationObj.getInt("id"), new double[]{locationObj.getDouble("lat"), locationObj.getDouble("lon"), locationObj.getDouble("tz")});
                        }
                    } catch (JSONException e) {
                        Log.e(LOG_TAG, "Failed to parse the NAPS site definition file. Exception:\n" + StackTraceCompactor.getCompactStackTrace(e));
                        return null;
                    }
                }

                if(NAPS_STATIONS.isEmpty()) {
                    //We should not still be empty after loading.
                    Log.w(LOG_TAG, "Failed to load NAPS site definition file, no data.");
                    return null;
                }
            }
        }

        Log.i(LOG_TAG, "Determining the matching NAPS site.");
        //Determine the closest matching location
        double closestDist = Double.MAX_VALUE;
        Integer closestNAPSID = null;
        Double closestTZ = null;
        for(Map.Entry<Integer, double[]> entry : NAPS_STATIONS.entrySet()) {
            final double[] latLonTZ = entry.getValue();
            final double latDiff = Math.abs(latitude - latLonTZ[0]);
            final double lonDiff = Math.abs(longitude - latLonTZ[1]);

            //Since we are dealing with very close distances, we don't need to worry
            //about map projections this time.
            if(latDiff <= 0.1 && lonDiff <= 0.1) {
                final double distanceMag = Math.pow(latDiff, 2) + Math.pow(lonDiff, 2);
                if(distanceMag < closestDist) {
                    closestDist = distanceMag;
                    closestNAPSID = entry.getKey();
                    closestTZ = latLonTZ[2];
                }
            }
        }

        if(null == closestNAPSID) {
            Log.i(LOG_TAG, "No matching NAPS site was found.");
        } else {
            Log.i(LOG_TAG, "The closest NAPS site is " + closestNAPSID + ".");
        }
        return new Pair<Integer, Double>(closestNAPSID, closestTZ);
    }

    /**
     * Retrieves a list of public alerts from the remote weather API service for the selected station.
     * Fresh data will only be fetch if there is no cached data or the cache is older
     * than DATA_REFRESH_MIN_DURATION milliseconds.
     *
     */
    private void fetchLatestAlertData() {
        //First determine if the data we have now is new enough to use as-is
        //This avoids excessive calls to the API.
        long ts = aqhiPref.getLong(ALERTS_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts <= DATA_REFRESH_MIN_DURATION) {
            return; //We have valid data already
        }

        //We need to fetch fresh data.
        List<Alert> alerts = null;

        Pair<Float, Float> latLon = getStationLatLon(true);
        if(null != latLon) {
            alerts = weatherService.getAlerts(latLon.first, latLon.second);
        }

        setAlerts(alerts); //Null is ok, will remove data and set the timestamp
    }

    /**
     * Retrieves data from the remote GEO Met API service for the provided station.
     * Fresh data will only be fetch if there is no cached data or the cache is older
     * than DATA_REFRESH_MIN_DURATION milliseconds.
     *
     * @param stationCode
     * @return true if the station has valid fresh data, false otherwise.
     */
    private boolean fetchLatestAQHIData(String stationCode) {
        //First determine if the data we have now is new enough to use as-is
        //This avoids excessive calls to the API.
        long ts = aqhiPref.getLong(LATEST_AQHI_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts <= DATA_REFRESH_MIN_DURATION) {
            float currentAQHIValue = aqhiPref.getFloat(LATEST_AQHI_VAL_KEY, -1f);
            if (currentAQHIValue>=0) return true; //We have valid data already
        }

        //We need to fetch fresh data.
        //Get the latest AQHI reading for the station.
        List<RealtimeData> realtimeData = geoMetService.getRealtimeData(stationCode); //station may be null at this time
        Double latestAQHI = extractLatestAQHIValue(realtimeData);

        if(null != latestAQHI) {
            //We have a legitimate updated AQHI value, save it.
            setLatestAQHI(latestAQHI);

            //We know we have at least one data point at this time, so we can update the historical data
            setHistoricalAQHI(extractPerDateAQHIValues(realtimeData));

            //Determine the typical AQHI for this time of day and time of year, if we have the data for it
            if(loadTypicalAQHI) determineTypicalAQHI();

            //Try to fetch the forecast as well
            List<ForecastData> forecastData = geoMetService.getForecastData(stationCode);
            if(null != forecastData) {
                setForecastAQHI(extractPerDateAQHIValues(forecastData));
            }
            return true;
        }
        return false; //We could not get any valid data
    }

    public synchronized Map<Integer, Map<Integer, Float>> getTypicalAQHIMap(Integer napsID) {
        if(null == napsID) return null;

        //We cache the typicalAQHIMap map in memory to prevent loading it multiple times.
        //We keep track of when the NAPS station changes with typicalAQHINAPSID
        if(null == typicalAQHINAPSID || !typicalAQHINAPSID.equals(napsID)) {
            //Set this first so we don't keep trying to reload the map if it fails,
            //for example from a JSON parsing error.
            typicalAQHINAPSID = napsID;
            final String fileName = "p50_aqhi_" + napsID;
            @SuppressLint("DiscouragedApi")
            int resId = context.getResources().getIdentifier(fileName, "raw", context.getPackageName());
            if (resId == 0) {
                Log.w(LOG_TAG, "No P50 AQHI data file found for NAPS ID " + napsID + " with file name " + fileName + ".");
                return null;
            }

            String json = Utils.loadCompressedResource(context, resId);
            typicalAQHIMap = gson.fromJson(json, TYPICAL_AQHI_Type);
            if(null == typicalAQHIMap || typicalAQHIMap.isEmpty()) {
                Log.w(LOG_TAG, "Could not parse P50 AQHI data from file found for NAPS ID " + fileName + ".");
                return null;
            }
        }

        return typicalAQHIMap;
    }


    private synchronized void determineTypicalAQHI() {
        Integer napsID = getStationNAPSID();
        if (null == napsID) {
            setTypicalAQHI(null);
            return;
        }

        //Get the current time in GMT and convert it into the timezone of the naps station.
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        Float timeZone = getStationTZ();
        if (null != timeZone) {
            //Note: Newfoundland's offset is 3 1/2 hours, so we use minutes
            final int timezoneMinutes = (int) (timeZone * 60.0);
            calendar.add(Calendar.MINUTE, timezoneMinutes);
        } else {
            Log.w(LOG_TAG, "No time zone is available for NAPS ID " + napsID + ".");
        }
        final int weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR);

        //We need to adjust the time forward by 1 hour because the typical AQHI data is from 1 to 24
        final int hour = calendar.get(Calendar.HOUR_OF_DAY) + 1;
        final Pair<Map<Integer, Float>, Map<Integer, Float>> hourMaps = getTypicalAQHIHourMaps(hour, getTypicalAQHIMap(napsID));
        if(null == hourMaps) {
            Log.w(LOG_TAG, "No typical AQHI data is available for NAPS ID " + napsID + ".");
            setTypicalAQHI(null);
        } else {
            //Save the determined value
            setTypicalAQHI(getAvgTypicalAQHI(getTypicalAQHI(weekOfYear, hourMaps.first), getTypicalAQHI(weekOfYear, hourMaps.second)));
        }
    }

    private Float getAvgTypicalAQHI(Float valueOne, Float valueTwo) {
        if(null != valueOne && null != valueTwo) {
            //We return the average between both values
            return (valueOne + valueTwo) / 2f;
        } else if (null != valueOne) {
            return valueOne;
        } else if (null != valueTwo) {
            return valueTwo;
        }

        //Couldn't find any value at all
        return null;
    }

    private Float getTypicalAQHI(int weekOfYear, Map<Integer, Float> hourMap) {
        if(null == hourMap) return null;

        //First see if we have a direct match
        Float valueOne = hourMap.get(weekOfYear);
        if (null != valueOne) return valueOne;

        //Try returning both the previous and next week's maps
        Float valueTwo = null;

        //Sometimes we have a week 53, sometimes we don't :)
        if(weekOfYear == 1) {
            valueOne = hourMap.get(53);
            if(null == valueOne) valueOne = hourMap.get(52);
        } else {
            valueOne = hourMap.get(weekOfYear-1);
        }

        if(weekOfYear == 52) {
            valueTwo = hourMap.get(53);
            if (null == valueTwo) valueTwo = hourMap.get(1);
        } else if (weekOfYear == 53) {
            valueTwo = hourMap.get(1);
        } else {
            valueTwo = hourMap.get(weekOfYear+1);
        }
        return getAvgTypicalAQHI(valueOne, valueTwo);
    }

    private Pair<Map<Integer, Float>, Map<Integer, Float>> getTypicalAQHIHourMaps(int hour, Map<Integer, Map<Integer, Float>> typicalAQHIMap) {
        if(null == typicalAQHIMap) return null;
        //First see if we have a direct match
        final Map<Integer, Float> hourMap = typicalAQHIMap.get(hour);
        if(null != hourMap) return new Pair<Map<Integer, Float>, Map<Integer, Float>>(hourMap, null);

        //Try returning both the previous and next hours' maps
        return new Pair<Map<Integer, Float>, Map<Integer, Float>>(typicalAQHIMap.get(hour==1 ? 24 : hour-1), typicalAQHIMap.get(hour == 24 ? 1 : hour+1));
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

    private static <D extends Data> Map<Date, Double> extractPerDateAQHIValues(List<D> data) {
        if (null == data || data.isEmpty()) return Collections.EMPTY_MAP;

        Map<Date, Double> sortedMap = new TreeMap<Date, Double>();
        for(Data dataPoint : data) {
            if(null == dataPoint.getProperties()) continue;
            sortedMap.put(dataPoint.getProperties().getDate(), dataPoint.getProperties().aqhi);
        }
        return sortedMap;
    }

    /**
     * Retrieves the matching NAPS site ID for the station either selected by the user or closest to the most recently updated user location.
     *
     * @return Integer, NAPS ID, may be null.
     */
    public Integer getStationNAPSID() {
        long rawID = aqhiPref.getLong(STATION_NAPS_ID_KEY, -1L);
        if(rawID < 0) return null;
        return (int) rawID;
    }

    /**
     * Retrieves the time zone offset of the matching NAPS site for the station either selected by the user or closest to the most recently updated user location.
     *
     * @return Float, time zone offset, may be null
     */
    public Float getStationTZ() {
        float rawTZ = aqhiPref.getFloat(STATION_TZ_KEY, -100L);
        if(rawTZ < -50) return null;
        return rawTZ;
    }

    /**
     * Retrieves the station name of the station either selected by the user or closest to the most recently updated user location.
     *
     * @param allowStale boolean, if false only return the Station value if it has been validated during the
     *                   last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Only applies if the station is set to automatic.
     *
     * @return String, station name.
     */
    public String getStationName(boolean allowStale){
        if(!allowStale && isStationAuto()) {
            long ts = aqhiPref.getLong(STATION_TS_KEY, Integer.MIN_VALUE);
            if (System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        }
        return aqhiPref.getString(STATION_NAME_KEY, null);
    }

    /**
     * Retrieves the station name of the station either selected by the user or closest to the most recently updated user location.
     * @return String, station name, if it has been validated during the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Otherwise returns null.
     */
    public String getStationName(){
        return getStationName(false);
    }

    /**
     * Retrieves the station code of the station either selected by the user or closest to the most recently updated user location.
     *
     * @param allowStale boolean, if false only return the Station value if it has been validated during the
     *                   last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Only applies if the station is set to automatic.
     *
     * @return String, station code.
     */
    public String getStationCode(boolean allowStale){
        if(!allowStale && isStationAuto()) {
            long ts = aqhiPref.getLong(STATION_TS_KEY, Integer.MIN_VALUE);
            if (System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        }
        return aqhiPref.getString(STATION_CODE_KEY, null);
    }

    /**
     * Retrieves the latitude & longitude coordinates of the station either selected by the user or closest to the most recently updated user location.
     *
     * @param allowStale boolean, if false only return the coordinates if they have been validated during the
     *                   last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Only applies if the station is set to automatic.
     *
     * @return Pair<Float, Float> station Latitude and Longitude coordinates.
     */
    public Pair<Float, Float> getStationLatLon(boolean allowStale){
        if(!allowStale && isStationAuto()) {
            long ts = aqhiPref.getLong(STATION_TS_KEY, Integer.MIN_VALUE);
            if (System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        }
        return new Pair<Float, Float>(aqhiPref.getFloat(STATION_LAT_KEY, -500f), aqhiPref.getFloat(STATION_LON_KEY, -500f));
    }

    /**
     * Retrieves the automatic station selection preference.
     *
     * @return boolean, true if the station is automatically selected based on the user's location, false otherwise.
     */
    public boolean isStationAuto(){
        return aqhiPref.getBoolean(STATION_AUTO_KEY, true);
    }

    /**
     * Saves the automatic station selection preference.
     *
     * @param val boolean, true if the station should be automatically selected based on the user's location, false otherwise.
     */
    public void setStationAuto(boolean val) {
        aqhiPref.edit().putBoolean(STATION_AUTO_KEY, val).apply();
    }

    /**
     * Retrieves the last saved typical AQHI value for the current station.
     *
     * @return Double, AQHI value, or returns -1 if stale or not present.
     */
    public Double getTypicalAQHI(){
        long ts = aqhiPref.getLong(TYPICAL_AQHI_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) {
            //If the last update was handled by a widget then this value is likely stale
            determineTypicalAQHI();
        }

        return (double) aqhiPref.getFloat(TYPICAL_AQHI_VAL_KEY, -1f);
    }

    /**
     * Retrieves the last saved AQHI value for the current station.
     *
     * @param allowStale boolean, if false only return the AQHI value if it has been updated within the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds.
     * @return Double, AQHI value, or returns -1 if stale or not present.
     */
    public Double getLatestAQHI(boolean allowStale){
        if(!allowStale) {
            long ts = aqhiPref.getLong(LATEST_AQHI_TS_KEY, Integer.MIN_VALUE);
            if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return -1d;
        }
        return (double) aqhiPref.getFloat(LATEST_AQHI_VAL_KEY, -1f);
    }

    /**
     * Retrieves the most recent AQHI value for the current station.
     *
     * @return Double AQHI value if it has been updated within the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Otherwise returns -1.
     */
    public Double getLatestAQHI(){
        return getLatestAQHI(false);
    }

    /**
     * Retrieves the most recent AQHI historical values for the current station.
     * @return Map<Date, Double> of historical AQHI values if they have been updated within the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Otherwise returns null.
     */
    public Map<Date, Double> getHistoricalAQHI(){
        final long ts = aqhiPref.getLong(HISTORICAL_AQHI_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        String historicalAQHI = aqhiPref.getString(HISTORICAL_AQHI_VAL_KEY, null);
        if (null == historicalAQHI || historicalAQHI.isEmpty()) return Collections.EMPTY_MAP;
        try {
            return gson.fromJson(historicalAQHI, gsonAQHIType);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to read the saved historical AQHI data: " + historicalAQHI, e);
            //No point continuing to save this data
            aqhiPref.edit()
                    .remove(HISTORICAL_AQHI_TS_KEY)
                    .remove(HISTORICAL_AQHI_VAL_KEY)
                    .apply();
            return null;
        }
    }

    /**
     * Retrieves the most recent AQHI forecast values for the current station.
     * @return Map<Date, Double> of forecast AQHI values if they have been updated within the last {@link AQHIService#DATA_VALIDITY_DURATION} milliseconds. Otherwise returns null.
     */
    public Map<Date, Double> getForecastAQHI(){
        final long ts = aqhiPref.getLong(FORECAST_AQHI_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        String forecastAQHI = aqhiPref.getString(FORECAST_AQHI_VAL_KEY, null);
        if (null == forecastAQHI || forecastAQHI.isEmpty()) return Collections.EMPTY_MAP;
        try {
            return gson.fromJson(forecastAQHI, gsonAQHIType);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to read the saved forecast AQHI data: " + forecastAQHI, e);
            //No point continuing to save this data
            aqhiPref.edit()
                    .remove(FORECAST_AQHI_TS_KEY)
                    .remove(FORECAST_AQHI_VAL_KEY)
                    .apply();
            return null;
        }
    }

    /**
     * Retrieves the most recent alerts for the current station.
     * @return List<Alert> List of Alerts. Returns an empty list when no data is available.
     */
    public List<Alert> getAlerts(){
        String alertsString = aqhiPref.getString(ALERTS_VAL_KEY, null);
        if (null == alertsString || alertsString.isEmpty()) return Collections.emptyList();
        try {
            List<Alert> alerts = gson.fromJson(alertsString, gsonAlertType);
            //Filter out expired alerts
            final Instant now = new Date().toInstant();
            return alerts.stream()
                    .filter(a->Instant.parse(a.getEventEndTime()).isAfter(now))
                    .filter(a->Instant.parse(a.getExpiryTime()).isAfter(now))
                    .toList();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to read the saved alert data: " + alertsString, e);
            //No point continuing to save this data
            aqhiPref.edit()
                    .remove(ALERTS_TS_KEY)
                    .remove(ALERTS_VAL_KEY)
                    .apply();
            return Collections.emptyList();
        }
    }

    /**
     * Saves the historical AQHI data to the shared preferences.
     *
     * @param val Map<Date, Double> historical AQHI data
     */
    private void setHistoricalAQHI(Map<Date, Double> val) {
        aqhiPref.edit()
                .putString(HISTORICAL_AQHI_VAL_KEY, null == val ? null : gson.toJson(val, gsonAQHIType))
                .putLong(HISTORICAL_AQHI_TS_KEY, System.currentTimeMillis())
                .apply();
    }

    /**
     * Saves the forecast AQHI data to the shared preferences.
     *
     * @param val Map<Date, Double> forecast AQHI data
     */
    private void setForecastAQHI(Map<Date, Double> val) {
        aqhiPref.edit()
                .putString(FORECAST_AQHI_VAL_KEY, null == val ? null : gson.toJson(val, gsonAQHIType))
                .putLong(FORECAST_AQHI_TS_KEY, System.currentTimeMillis())
                .apply();
    }

    /**
     * Saves the current alert data to the shared preferences.
     *
     * @param val List<Alert> Alert data
     */
    private void setAlerts(List<Alert> val) {
        aqhiPref.edit()
                .putString(ALERTS_VAL_KEY, null == val ? null : gson.toJson(val, gsonAlertType))
                .putLong(ALERTS_TS_KEY, System.currentTimeMillis())
                .apply();
    }

    /**
     * Saves the current station name, code, and NAPS ID to the shared preference.
     * Will only updates the timestamp when all values are null.
     *
     * @param station Station
     * @param napsStation A Pair<Integer, Double> representing the NAPS site ID and time zone offset.
     */
    private void setStation(Station station, Pair<Integer, Double> napsStation) {
        SharedPreferences.Editor editor = aqhiPref.edit();
        editor.putLong(STATION_TS_KEY, System.currentTimeMillis());
        //When everything is null we update just the timestamp (refresh)
        if (null != station || null != napsStation) {
            //Not every station has a corresponding NAPS site.
            //So, NAPS is allowed to be null and is wiped when it is null.
            //However, when the station is null, we keep everything as is.
            //It's meaningless for the station to be null and NAPS to be not null.
            if(null != station) {
                //These need to always be in sync
                //Null checks were already preformed when fetching the stations
                editor.putString(STATION_CODE_KEY, station.properties.location_id);
                editor.putString(STATION_NAME_KEY, station.properties.location_name_en);
                editor.putFloat(STATION_LAT_KEY, station.geometry.coordinates.get(1));
                editor.putFloat(STATION_LON_KEY, station.geometry.coordinates.get(0));

                if (null == napsStation || null == napsStation.first) {
                    editor.remove(STATION_NAPS_ID_KEY);
                } else {
                    editor.putLong(STATION_NAPS_ID_KEY, napsStation.first);
                }

                if (null == napsStation || null == napsStation.second) {
                    editor.remove(STATION_TZ_KEY);
                } else {
                    editor.putFloat(STATION_TZ_KEY, napsStation.second.floatValue());
                }
            }
        }
        editor.apply();
    }

    /**
     * Saves the latest AQHI data to the shared preferences.
     * Only updates the timestamp when the value is null.
     *
     * @param val Double the latest AQHI reading for the user's current station.
     */
    private void setLatestAQHI(Double val) {
        SharedPreferences.Editor editor = aqhiPref.edit();
        if(null != val) {
            editor.putFloat(LATEST_AQHI_VAL_KEY, val.floatValue());
            editor.putLong(LATEST_AQHI_TS_KEY, System.currentTimeMillis());
        } else {
            editor.remove(LATEST_AQHI_VAL_KEY);
            editor.remove(LATEST_AQHI_TS_KEY);
        }

        editor.apply();
    }

    /**
     * Saves the typical AQHI value to the shared preferences.
     * Removes the shared preferences if the value is null
     *
     * @param val Float the typical AQHI value
     */
    private void setTypicalAQHI(Float val) {
        SharedPreferences.Editor editor = aqhiPref.edit();
        if(null != val) {
            editor.putFloat(TYPICAL_AQHI_VAL_KEY, val);
            editor.putLong(TYPICAL_AQHI_TS_KEY, System.currentTimeMillis());
        } else {
            editor.remove(TYPICAL_AQHI_VAL_KEY);
            editor.remove(TYPICAL_AQHI_TS_KEY);
        }
        editor.apply();
    }

    private boolean isInternetAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null)
            return false;

        Network network = connectivityManager.getActiveNetwork();
        if (network == null)
            return false;

        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
        if (networkCapabilities == null)
            return false;

        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    public boolean isAllowStaleLocation() {
        return allowStaleLocation;
    }

    public void setAllowStaleLocation(boolean allowStaleLocation) {
        this.allowStaleLocation = allowStaleLocation;
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public GeoMetService getGeoMetService() {
        return geoMetService;
    }
}