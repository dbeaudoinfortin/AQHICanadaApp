package com.dbf.aqhi.location;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.dbf.aqhi.Utils;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnFailureListener;

import java.text.ParseException;

public class LocationService {

    private static final String LOG_TAG = "LocationService";

    private static final long DATA_VALIDITY_DURATION = 600000; //10 minutes in milliseconds

    //Minimum distance in meters that will trigger an onChange event
    private static final int MIN_CHANGE_DISTANCE = 2000; //meters
    private static final String LOCATION_PREF_KEY = "com.dbf.aqhi.location";
    private static final String LOCATION_COORDINATES_KEY = "COORDINATES";
    private static final String LOCATION_COORDINATES_TS_KEY = LOCATION_COORDINATES_KEY + "_TS";

    private final FusedLocationProviderClient locationClient;
    private final CurrentLocationRequest locationRequest;

    private final Context context;

    private final SharedPreferences locationPref;

    public LocationService(Context context) {
        this.context = context;
        this.locationPref =  context.getSharedPreferences(LOCATION_PREF_KEY, Context.MODE_PRIVATE);
        this.locationClient = LocationServices.getFusedLocationProviderClient(context);
        this.locationRequest =
            new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(0)
                    .setDurationMillis(60000)
                    .build();
    }

    public double[] getRecentLocation(){
        Long ts = locationPref.getLong(LOCATION_COORDINATES_TS_KEY, Integer.MIN_VALUE);
        if(System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
        return parseLocationCoordinates(locationPref.getString(LOCATION_COORDINATES_KEY, null));
    }

    /**
     * Forces an update to the saved location
     *
     * @param onChange Optional callback if and only if the location has changed
     */
    public void updateLocation(Runnable onChange) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Location permission is required.", Toast.LENGTH_SHORT).show();
            return;
        }
        locationClient.getCurrentLocation(locationRequest, null).addOnSuccessListener(currentLocation -> {
            if(null != currentLocation) {
                Log.i(LOG_TAG, "Location updated received. Provider: " + currentLocation.getProvider() + ", Latitude: " + currentLocation.getLatitude()  + ", Longitude: " + currentLocation.getLongitude() + ", Accuracy: " + currentLocation.getAccuracy());

                //Extract the previous stored location, if present
                String previousLocationCoordinates = locationPref.getString(LOCATION_COORDINATES_KEY,"");

                double[] oldLatLong = parseLocationCoordinates(previousLocationCoordinates);
                if(null == oldLatLong) {
                    //Location has never been saved, or is corrupt (unparsable). Force an update of the preferences.
                    updateLocationPref(currentLocation.getLatitude(), currentLocation.getLongitude());
                    if(null != onChange) onChange.run();
                    return;
                }

                if (oldLatLong[0] == currentLocation.getLatitude() && oldLatLong[1] == currentLocation.getLongitude()) {
                    //Update only the timestamp
                    updateLocationPref(null, null);
                    return;
                }

                //Determine if the location differs enough to update and to make the callback
                final double distance = Utils.distanceMeters(currentLocation.getLongitude(), currentLocation.getLatitude(), oldLatLong[1], oldLatLong[0]);
                if(distance >= MIN_CHANGE_DISTANCE) {
                    //The location is sufficiently different from the last saved location. Update the saved location
                    updateLocationPref(currentLocation.getLatitude(), currentLocation.getLongitude());
                    if(null != onChange) onChange.run();
                } else {
                    //Update only the timestamp
                    updateLocationPref(null, null);
                }
            }
        }).addOnFailureListener(e -> {
            // Handle failure
            Log.e("CurrentLocation", "Failed to get location", e);
        });
    }

    private static double[] parseLocationCoordinates(String coordinates) {
        if(null == coordinates || coordinates.isEmpty()) return null;

        try {
            String[] latLong = coordinates.split(";");
            if (latLong.length != 2) {
                //Might be corrupted
                throw new ParseException("Unparsable location coordinates. Expected 2 entries, got " + latLong.length + " entries: " + coordinates, 0);
            }

            final double latitude  = Double.parseDouble(latLong[0]);
            final double longitude = Double.parseDouble(latLong[1]);

            if(Math.abs(latitude) > 90.0) {
                throw new ParseException("Invalid latitude: "  + latitude, 0);
            }

            if(Math.abs(longitude) > 180.0) {
                throw new ParseException("Invalid longitude: "  + latitude, 0);
            }

            return new double[] {latitude, longitude};
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to parse location coordinates: " + coordinates, e);
        }
        return null;
    }

    private void updateLocationPref(Double lat, Double lon) {
        SharedPreferences.Editor editor = locationPref.edit();
        if(null != lat && null != lon) {
            editor.putString(LOCATION_COORDINATES_KEY, lat + ";" + lon);
        }
        editor.putLong(LOCATION_COORDINATES_TS_KEY, System.currentTimeMillis());
        editor.apply();
    }

}