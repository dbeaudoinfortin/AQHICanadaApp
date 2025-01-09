package com.dbf.aqhi.geomet;

import static com.dbf.aqhi.Utils.distanceMagnitude;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.util.Log;

import com.dbf.aqhi.geomet.realtime.RealtimeData;
import com.dbf.aqhi.geomet.realtime.RealtimeResponse;
import com.dbf.aqhi.geomet.station.Station;
import com.dbf.aqhi.geomet.station.StationResponse;
import com.dbf.aqhi.http.RetryInterceptor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GeoMetService {

    private static final long HTTP_TIMEOUT = 60000; //1 minute in Milliseconds
    private static final int  HTTP_TRIES = 3;

    //Some stations will offline briefly, so we want to refresh this at least once per day
    private static final long STATION_CACHE_DURATION = 24 * 60 * 60 * 1000; //1 days in milliseconds
    private static final String STATION_CACHE_FILE_NAME = "stationCache.json";

    private static final String LOG_TAG = "GeoMetService";
    private static final String BASE_URL = "https://api.weather.gc.ca/collections/";

    //Not all stations have data, so use only the stations that provide the latest observations.
    //Don't use "aqhi-stations/items"
    private static final String STATION_URL = BASE_URL + "aqhi-observations-realtime/items?latest=true";
    private static final String REALTIME_URL = BASE_URL + "aqhi-observations-realtime/items";
    private static final String REALTIME_LOCATION_ID = "location_id";
    private static final Gson gson = new Gson();
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new RetryInterceptor(HTTP_TRIES, LOG_TAG))
            .callTimeout(HTTP_TIMEOUT, MILLISECONDS)
            .build();

    private final Context context;
    private final File stationCacheFile;

    private List<Station> stations; //This is cached

    public GeoMetService(Context context) {
        this.context = context;
        this.stationCacheFile = new File(context.getCacheDir(), STATION_CACHE_FILE_NAME);
    }

    public Station getNearestStation(double longitude, double latitude,boolean forceStationUpdate){
        List<Station> stations = loadStations(forceStationUpdate);
        if (null == stations) return null; //Well, we tried.

        //TODO: Apply a bit of logic to reduce the amount of heavy math calculations
        Station nearestStation = stations.get(0);
        double nearestMagnitude = distanceMagnitude(longitude, latitude, nearestStation.geometry.coordinates.get(0), nearestStation.geometry.coordinates.get(1));
        for(int i = 1; i < stations.size(); i++) {
            Station station = stations.get(i);
            double distanceMagnitude = distanceMagnitude(longitude, latitude, station.geometry.coordinates.get(0), station.geometry.coordinates.get(1));
            if (distanceMagnitude < nearestMagnitude) {
                nearestMagnitude = distanceMagnitude;
                nearestStation = station;
            }
        }
        return nearestStation;
    }

    public List<RealtimeData> getRealtimeData(String stationID) {
        if(null == stationID || stationID.isEmpty()) return null;

        String url = REALTIME_URL + '?' + REALTIME_LOCATION_ID + '=' + stationID;

        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client. newCall(request).execute()) {
            if (response.isSuccessful()) {
                try {
                    RealtimeResponse realtimeResponse = gson.fromJson(response.body().string(), RealtimeResponse.class);
                    if(null != realtimeResponse) {
                        Log.i(LOG_TAG, realtimeResponse.numberReturned + " Data points were returned from GeoMet. URL: " + REALTIME_URL);
                        return realtimeResponse.data;
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Failed to parse response for GeoMet. URL: " + REALTIME_URL + ". Response body: " + response.body().string(), e);
                }
            } else {
                Log.e(LOG_TAG, "Call to GeoMet failed. URL: " + REALTIME_URL + ". HTTP Code: " + response.code() + ". Message: " + (response.body() == null ? "null" : response.body().string()));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to call GeoMet. URL: " + REALTIME_URL, e);
        }
        return null;
    }

    private synchronized List<Station> loadStations(boolean forceUpdate) {
        if(null == stations || forceUpdate) {
            //This request can be heavy, so we want to first see if we have this cached
            List<Station> newStations = loadStationsCached(forceUpdate);
            if (null != newStations && !newStations.isEmpty()) {
                //Don't replace the station list if we could not fetch a new list this time
                stations = newStations;
            }
        }
        if(stations.isEmpty()) return null;
        return stations;
    }
    private static final Type stationsType = new TypeToken<List<Station>>() {}.getType();
    private List<Station> loadStationsCached(boolean forceUpdate){
        List<Station> stations = null;

        if(!forceUpdate) {
            //Determine if the station definitions have been previously cached to disk to avoid fetching them from the API again
            stations = fetchStationsFromDisk();
            if(null != stations) return stations;
        }

        //The cache file may not exist, may be corrupt, or may be expired.
        //Attempt to fetch the stations remotely and cache the results.
        stations = fetchStationsRemotely();

        if(null == stations) {
            //Remote look up failed.
            //Fallback to the disk cache if the force flag was set, since it hasn't been checked yet.
            if(forceUpdate) {
                stations = fetchStationsFromDisk();
            }
        } else {
            //Remote look up succeeded, update the disk cache file
            writeStationsFromDisk(stations);
        }
        return stations;
    }

    private void writeStationsFromDisk(List<Station> stations) {
        try (FileWriter writer = new FileWriter(stationCacheFile)) {
            gson.toJson(stations, writer); // Serialize the List<Station> to JSON
            stationCacheFile.setLastModified(System.currentTimeMillis()); // Update the timestamp
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to write station definitions to the cache file " + stationCacheFile.getAbsolutePath(), e);
        }
    }

    private List<Station> fetchStationsFromDisk() {
        if (stationCacheFile.exists() && (System.currentTimeMillis() - stationCacheFile.lastModified()) <= STATION_CACHE_DURATION) {
            try (FileReader reader = new FileReader(stationCacheFile)) {
                return gson.fromJson(reader, stationsType);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to read station definitions from the cache file " + stationCacheFile.getAbsolutePath(), e);
            }
        }
        return null;
    }

    private List<Station> fetchStationsRemotely() {
        Request request = new Request.Builder()
                .url(STATION_URL)
                .build();

        //This needs to be a blocking call since we can't move on without a list of stations
        try (Response response = client. newCall(request).execute()) {
            if (response.isSuccessful()) {
                try {
                    StationResponse stationResponse = gson.fromJson(response.body().string(), StationResponse.class);
                    if(null != stationResponse) {
                        Log.i(LOG_TAG, stationResponse.numberReturned + " Stations were returned from GeoMet. URL: " + STATION_URL);
                        return stationResponse.stations;
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Failed to parse response for GeoMet. URL: " + STATION_URL + ". Response body: " + response.body().string(), e);
                }
            } else {
                Log.e(LOG_TAG, "Call to GeoMet failed. URL: " + STATION_URL + ". HTTP Code: " + response.code() + ". Message: " + (response.body() == null ? "null" : response.body().string()));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to call GeoMet. URL: " + STATION_URL, e);
        }
        return null;
    }
}
