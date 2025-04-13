package com.dbf.aqhi.geomet;

import static com.dbf.aqhi.Utils.earthDistanceMagnitude;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.util.Log;

import com.dbf.aqhi.geomet.data.Data;
import com.dbf.aqhi.geomet.data.DataResponse;
import com.dbf.aqhi.geomet.data.forecast.ForecastData;
import com.dbf.aqhi.geomet.data.forecast.ForecastResponse;
import com.dbf.aqhi.geomet.data.realtime.RealtimeData;
import com.dbf.aqhi.geomet.data.realtime.RealtimeResponse;
import com.dbf.aqhi.geomet.station.Station;
import com.dbf.aqhi.geomet.station.StationResponse;
import com.dbf.aqhi.http.RetryInterceptor;
import com.dbf.utils.stacktrace.StackTraceCompactor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GeoMetService {

    private static final long HTTP_TIMEOUT = 60000; //1 minute in Milliseconds
    private static final int  HTTP_TRIES = 3;

    //Some stations will offline briefly, so we want to refresh this at least once per day
    private static final long STATION_CACHE_DURATION = 24 * 60 * 60 * 1000; //1 day in milliseconds
    private static final String STATION_CACHE_FILE_NAME = "stationCache.json";

    private static final String LOG_TAG = "GeoMetService";
    private static final String BASE_URL = "https://api.weather.gc.ca/collections/";

    //Not all stations have data, so use only the stations that provide the latest observations.
    //Don't use "aqhi-stations/items"
    private static final String STATION_URL = BASE_URL + "aqhi-observations-realtime/items?latest=true";
    private static final String REALTIME_URL = BASE_URL + "aqhi-observations-realtime/items?skipGeometry=true";
    private static final String FORECAST_URL = BASE_URL + "aqhi-forecasts-realtime/items?skipGeometry=true";
    private static final String URL_LOCATION_ID = "location_id";
    private static final Gson gson = new Gson();
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new RetryInterceptor(HTTP_TRIES, LOG_TAG))
            .callTimeout(HTTP_TIMEOUT, MILLISECONDS)
            .build();

    private final File stationCacheFile;

    private Map<String, Station> stations; //This is cached

    public GeoMetService(Context context) {
        this.stationCacheFile = new File(context.getCacheDir(), STATION_CACHE_FILE_NAME);
    }

    public Station getNearestStation(double longitude, double latitude, boolean forceStationUpdate){
        Map<String, Station> stations = loadStations(forceStationUpdate, true);
        if (null == stations || stations.isEmpty()) return null; //Well, we tried.

        //TODO: Apply a bit of logic to reduce the amount of heavy math calculations
        Collection<Station> stationList = stations.values();
        Station nearestStation = null;
        double nearestMagnitude = Double.MAX_VALUE;
        for(Station station : stationList) {
            double distanceMagnitude = earthDistanceMagnitude(longitude, latitude, station.geometry.coordinates.get(0), station.geometry.coordinates.get(1));
            if (distanceMagnitude < nearestMagnitude) {
                nearestMagnitude = distanceMagnitude;
                nearestStation = station;
            }
        }
        return nearestStation;
    }

    private <D extends Data, R extends DataResponse<D>> List<D>  getData(String stationID, String baseURL, Class<R> responseClass) {
        if(null == stationID || stationID.isEmpty()) return null;

        Request request = new Request.Builder()
                .url(baseURL + '&' + URL_LOCATION_ID + '=' + stationID)
                .build();

        Log.i(LOG_TAG, "Calling GeoMet. URL: " + baseURL);
        try (Response response = client. newCall(request).execute()) {
            if (response.isSuccessful()) {
                if(response.body() != null) {
                    try {
                        DataResponse dataResponse = gson.fromJson(response.body().string(), responseClass);
                        if(null != dataResponse) {
                            Log.i(LOG_TAG, dataResponse.numberReturned + " Data points were returned from GeoMet. URL: " + baseURL);
                            return dataResponse.getData();
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to parse response for GeoMet. URL: " + baseURL + ". Response body: " + response.body().string()+ "\n" + StackTraceCompactor.getCompactStackTrace(e));
                    }
                } else {
                    Log.e(LOG_TAG, "Call to GeoMet failed. URL: " + baseURL + ". Empty response body.");
                }
            } else {
                Log.e(LOG_TAG, "Call to GeoMet failed. URL: " + baseURL + ". HTTP Code: " + response.code() + ". Message: " + (response.body() == null ? "null" : response.body().string()));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to call GeoMet. URL: " + baseURL + "\n" + StackTraceCompactor.getCompactStackTrace(e));
        }
        return null;
    }

    public List<RealtimeData> getRealtimeData(String stationID) {
        return getData(stationID, REALTIME_URL, RealtimeResponse.class);
    }

    public List<ForecastData> getForecastData(String stationID) {
        List<ForecastData> data = getData(stationID, FORECAST_URL, ForecastResponse.class);
        if(null != data) {
            final Date now = new Date();
            return data.stream().filter(d->d.getProperties().getDate().after(now)).toList();
        }
        return null;
    }

    /**
     *  Returns a Map of all of the possible stations to choose from.
     *
     * @param forceUpdate When true, the data will be forcefully reloaded into memory.
     * @return Map<String, Station> all stations
     */
    public synchronized Map<String, Station> loadStations(boolean forceUpdate, boolean allowRemote) {
        if(null == stations || forceUpdate) {
            //This request can be heavy, so we want to first see if we have this cached
            Map<String, Station> newStations = loadStationsCached(forceUpdate, allowRemote);
            if (null != newStations && !newStations.isEmpty()) {
                //Don't replace the station list if we could not fetch a new list this time
                stations = newStations;
            }
        }
        if(null == stations || stations.isEmpty()) return null;
        return stations;
    }

    private Map<String, Station> loadStationsCached(boolean forceUpdate, boolean allowRemote){
        Map<String, Station> stations = null;

        if(!forceUpdate) {
            //Determine if the station definitions have been previously cached to disk to avoid fetching them from the API again
            stations = fetchStationsFromDisk();
            if(null != stations) return stations;
        }

        //The cache file may not exist, may be corrupt, or may be expired.
        //Attempt to fetch the stations remotely and cache the results.
        if(allowRemote) stations = fetchStationsRemotely();

        if(null == stations) {
            //Remote look up failed.
            //Fallback to the disk cache if the force flag was set, since it hasn't been checked yet.
            if(forceUpdate) {
                stations = fetchStationsFromDisk();
            }
        } else {
            //Remote look up succeeded, update the disk cache file
            writeStationsToDisk(stations);
        }
        return stations;
    }

    private void writeStationsToDisk(Map<String, Station> stations) {
        try (FileWriter writer = new FileWriter(stationCacheFile)) {
            gson.toJson(stations, writer); // Serialize the Map<String, Station> to JSON
            stationCacheFile.setLastModified(System.currentTimeMillis()); // Update the timestamp
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to write station definitions to the cache file " + stationCacheFile.getAbsolutePath() + "\n" + StackTraceCompactor.getCompactStackTrace(e));
        }
    }

    private static final Type stationsType = new TypeToken<Map<String, Station>>() {}.getType();
    private Map<String, Station> fetchStationsFromDisk() {
        if (stationCacheFile.exists() && (System.currentTimeMillis() - stationCacheFile.lastModified()) <= STATION_CACHE_DURATION) {
            Log.i(LOG_TAG, "Loading station list from disk cache.");
            try (FileReader reader = new FileReader(stationCacheFile)) {
                return gson.fromJson(reader, stationsType);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to read station definitions from the cache file " + stationCacheFile.getAbsolutePath() + "\n" + StackTraceCompactor.getCompactStackTrace(e));
            }
        }
        return null;
    }

    private Map<String, Station> fetchStationsRemotely() {
        Request request = new Request.Builder()
                .url(STATION_URL)
                .build();

        //This needs to be a blocking call since we can't move on without a list of stations
        Log.i(LOG_TAG, "Calling GeoMet. URL: " + STATION_URL);
        try (Response response = client. newCall(request).execute()) {
            if (response.isSuccessful()) {
                if(response.body() != null) {
                    try {
                        StationResponse stationResponse = gson.fromJson(response.body().string(), StationResponse.class);
                        if(null != stationResponse) {
                            Log.i(LOG_TAG, stationResponse.numberReturned + " Stations were returned from GeoMet. URL: " + STATION_URL);
                            return validateStations(stationResponse.stations);
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to parse response for GeoMet. URL: " + STATION_URL + ". Response body: " + response.body().string() + "\n" + StackTraceCompactor.getCompactStackTrace(e));
                    }
                } else {
                    Log.e(LOG_TAG, "Call to GeoMet failed. URL: " + STATION_URL + ". Empty response body.");
                }
            } else {
                Log.e(LOG_TAG, "Call to GeoMet failed. URL: " + STATION_URL + ". HTTP Code: " + response.code() + ". Message: " + (response.body() == null ? "null" : response.body().string()));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to call GeoMet. URL: " + STATION_URL + "\n" + StackTraceCompactor.getCompactStackTrace(e));
        }
        return null;
    }

    private Map<String, Station> validateStations(List<Station> stations) {
        //Remove any station that doesn't have valid geometry data
        final Map<String, Station> validStations = new HashMap<String, Station>(stations.size());
        for(Station station : stations) {
            if(null == station.id || station.id.isEmpty()) {
                Log.w(LOG_TAG, "Invalid station ID: " + station.id + ".");
                continue;
            }
            if(null == station.geometry || null == station.geometry.coordinates || station.geometry.coordinates.size() < 2) {
                Log.w(LOG_TAG, "Invalid geometry data for station " + station.id + ".");
                continue;
            }
            if(null == station.properties) {
                Log.w(LOG_TAG, "Invalid properties for station " + station.id + ".");
                continue;
            }
            validStations.put(station.id, station);
        }
        return validStations;
    }
}
