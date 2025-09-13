package com.dbf.aqhi.api.weather;

import android.util.Log;

import com.dbf.aqhi.api.JsonAPIService;
import com.dbf.aqhi.api.weather.alert.Alert;
import com.dbf.aqhi.api.weather.alert.AlertResponse;
import com.dbf.aqhi.api.weather.location.LocationResponse;
import com.dbf.utils.stacktrace.StackTraceCompactor;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Request;
import okhttp3.Response;

public class WeatherService extends JsonAPIService {

    private static final String LOG_TAG = "WeatherService";

    private static final String BASE_URL = "https://weather.gc.ca/api/app/v3/en/";
    private static final String LOCATION_URL = BASE_URL + "Location/";

    private static final Set<String> EVENT_CODES = new HashSet<String>();

    //Used for Gson conversion
    private static final Type gsonLocationType = new TypeToken<List<LocationResponse>>(){}.getType();

    static {
        EVENT_CODES.add("DSW"); //	Dust Storm Warning
        EVENT_CODES.add("AQW"); //	Air Quality Warning
        EVENT_CODES.add("SAS"); //	Special Air Quality Statement
        EVENT_CODES.add("SAQS"); //	Special Air Quality Statement
    }
    public List<Alert> getAlerts(float lat, float lon) {

        String url = LOCATION_URL + lat + "," + lon;
        Request request = new Request.Builder()
                .url(url)
                .build();

        Log.i(LOG_TAG, "Calling Weather API. URL: " + url);

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if(response.body() != null) {
                    try {
                        List<LocationResponse> locationResponse = gson.fromJson(response.body().string(), gsonLocationType);
                        if(null != locationResponse && !locationResponse.isEmpty()) {
                            AlertResponse alertResponse = locationResponse.get(0).getAlert();
                            if(null != alertResponse) {
                                List<Alert> alerts = alertResponse.getAlerts();
                                if(null != alerts) {
                                    Log.i(LOG_TAG, alerts.size() + " alert(s) were returned from the Weather API. URL: " + url);
                                    //Filter out expired alerts, and non air quality alerts
                                    final Instant now = new Date().toInstant();
                                    return alerts.stream()
                                            .filter(a->Instant.parse(a.getEventEndTime()).isAfter(now))
                                            .filter(a->Instant.parse(a.getExpiryTime()).isAfter(now))
                                            .filter(a->null == a.getStatus() || a.getStatus().toLowerCase().equals("active"))
                                            .filter(a->((null != a.getProgram()   && "air_quality".equals(a.getProgram().toLowerCase()))
                                                     || (null != a.getAlertCode() && EVENT_CODES.contains(a.getAlertCode().toUpperCase()))))
                                            .toList();
                                }
                            }

                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Failed to parse response for Weather API. URL: " + url + ". Response body: " + response.body().string()+ "\n" + StackTraceCompactor.getCompactStackTrace(e));
                    }
                } else {
                    Log.e(LOG_TAG, "all to Weather API failed. URL: " + url + ". Empty response body.");
                }
            } else {
                Log.e(LOG_TAG, "Call to Weather API failed. URL: " + url + ". HTTP Code: " + response.code() + ". Message: " + (response.body() == null ? "null" : response.body().string()));
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to call Weather API. URL: " + url + "\n" + StackTraceCompactor.getCompactStackTrace(e));
        }

        Log.w(LOG_TAG, "No data returned from Weather API. URL: " + url);
        return null;
    }
}
