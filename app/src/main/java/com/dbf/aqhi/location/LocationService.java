package com.dbf.aqhi.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dbf.aqhi.Utils;
import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.utils.stacktrace.StackTraceCompactor;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.ParseException;

public class LocationService {

    private static final String LOG_TAG = "LocationService";
    private static final Object GLOBAL_SYNC_OBJECT = new Object();

    private static final long DATA_VALIDITY_DURATION = 600000; //10 minutes in milliseconds

    //Minimum distance in meters that will trigger an onChange event
    private static final int MIN_CHANGE_DISTANCE = 2000; //meters

    //Shared preferences keys
    private static final String LOCATION_PREF_KEY = "com.dbf.aqhi.location";
    private static final String LOCATION_COORDINATES_KEY = "COORDINATES";
    private static final String LOCATION_COORDINATES_TS_KEY = LOCATION_COORDINATES_KEY + "_TS";

    private final FusedLocationProviderClient locationClient;
    private final CurrentLocationRequest locationRequest;
    private final SharedPreferences locationPref;
    private final Context context;

    public LocationService(Context context) {
        this.context = context;
        this.locationPref =  context.getSharedPreferences(LOCATION_PREF_KEY, Context.MODE_PRIVATE);
        this.locationClient = LocationServices.getFusedLocationProviderClient(context);
        this.locationRequest =
            new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(60000)
                    .setDurationMillis(60000)
                    .build();
    }

    /**
     * Attempts to update to the user's saved location.
     * This method will first check to see if the user has granted the ACCESS_COARSE_LOCATION permission.
     * Only changes that result in approximately at least {@link LocationService#MIN_CHANGE_DISTANCE} meters of distance will result in an update.
     * The location is stored in SharedPreferences and can be retrieved via {@link LocationService#getRecentLocation()}.
     *
     * @param @Nullable callback Optional callback executed when the location has been successfully updated and differs from the previous location.
     */
    @SuppressLint("MissingPermission")
    public void updateLocation(Runnable callback) {
        if (!PermissionService.checkLocationPermission(context)) {
            Log.i(LOG_TAG, "Cannot update the user's current location due to missing permissions.");
            return; //Don't call back here
        }

        Log.i(LOG_TAG, "Attempting to update the user's current location...");
        locationClient.getCurrentLocation(locationRequest, null).addOnSuccessListener(currentLocation -> {
            if(null != currentLocation) {
                Log.i(LOG_TAG, "Location update received. Provider: " + currentLocation.getProvider() + ", Latitude: " + currentLocation.getLatitude()  + ", Longitude: " + currentLocation.getLongitude() + ", Accuracy: " + currentLocation.getAccuracy());

                boolean locationChanged = false;
                synchronized (GLOBAL_SYNC_OBJECT) {
                    //Extract the previous stored location, if present
                    double[] oldLatLong = parseLocationCoordinates(locationPref.getString(LOCATION_COORDINATES_KEY,""));
                    if(null == oldLatLong) {
                        //Location has never been saved, or is corrupt (unparsable). Force an update of the preferences.
                        setRecentLocation(currentLocation.getLatitude(), currentLocation.getLongitude());
                        locationChanged = true;
                    } else if (oldLatLong[0] == currentLocation.getLatitude() && oldLatLong[1] == currentLocation.getLongitude()) {
                        //The location coordinates match exactly. Update only the timestamp.
                        setRecentLocation(null, null);
                    } else {
                        //Determine if the location differs enough to update and to make the callback
                        final double distance = Utils.earthDistanceMeters(currentLocation.getLongitude(), currentLocation.getLatitude(), oldLatLong[1], oldLatLong[0]);
                        if(distance >= MIN_CHANGE_DISTANCE) {
                            //The location is sufficiently different from the last saved location. Update the saved location
                            setRecentLocation(currentLocation.getLatitude(), currentLocation.getLongitude());
                            locationChanged = true;
                        } else {
                            //Update only the timestamp
                            setRecentLocation(null, null);
                        }
                    }
                }

                //Perform the callback now that the saved location has been updated
                if(null != callback && locationChanged) callback.run();
            } else {
                //The location lookup was successful and yet the location object is null.
                //This might happen if the location update is happening in the background and the user hasn't allowed the ACCESS_BACKGROUND_LOCATION permission.
                //No callback is performed in this scenario
                Log.i(LOG_TAG, "Unable to update location, null location object received. Is the background location permission allowed?");
            }
        }).addOnFailureListener(e -> Log.e(LOG_TAG, "Failed to update the current location." + "\n" + StackTraceCompactor.getCompactStackTrace(e))); //No callback on failure
    }

    /**
     * Converts are String representation of latitude and longitude coordinates, separated by a semi-colon ';',
     * into a double array of length 2, in the form of double[latitude, longitude].
     *
     * @param coordinates String of coordinates.
     * @return double array of length 2, in the form of double[latitude, longitude]
     */
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
            Log.e(LOG_TAG, "Failed to parse location coordinates: " + coordinates + "\n" + StackTraceCompactor.getCompactStackTrace(e));
        }
        return null;
    }

    /**
     * Retrieves the most recent saved location coordinates (latitude and longitude) from shared preferences.
     *
     * @param allowStale When true, only location coordinates that have been updated within the last {@link LocationService#DATA_VALIDITY_DURATION} milliseconds are returned.
     * @return double[] An array of length 2 in the form of double[latitude, longitude].
     */
    public double[] getRecentLocation(boolean allowStale){
        synchronized (GLOBAL_SYNC_OBJECT) {
            if (!allowStale) {
                long ts = locationPref.getLong(LOCATION_COORDINATES_TS_KEY, Integer.MIN_VALUE);
                if (System.currentTimeMillis() - ts > DATA_VALIDITY_DURATION) return null;
            }
            return parseLocationCoordinates(locationPref.getString(LOCATION_COORDINATES_KEY, null));
        }
    }

    /**
     * Retrieves the most recent saved location coordinates (latitude and longitude) from shared preferences.
     * A value is only returned if the location coordinates have been updated within the last {@link LocationService#DATA_VALIDITY_DURATION} milliseconds.
     * Otherwise, null is returned.
     *
     * @return double[] An array of length 2 in the form of double[latitude, longitude].
     */
    public double[] getRecentLocation() {
        return getRecentLocation(false);
    }

    /**
     * Saves the most recent location coordinates (latitude and longitude) to the shared preferences.
     * Only updates the timestamp if either the latitude and longitude values are null.
     *
     * @param lat Double Latitude
     * @param lon Double Longitude
     */
    private void setRecentLocation(Double lat, Double lon) {
        SharedPreferences.Editor editor = locationPref.edit();
        if(null != lat && null != lon) {
            editor.putString(LOCATION_COORDINATES_KEY, lat + ";" + lon);
        }
        editor.putLong(LOCATION_COORDINATES_TS_KEY, System.currentTimeMillis());
        editor.apply();
    }

}