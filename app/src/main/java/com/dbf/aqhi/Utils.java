package com.dbf.aqhi;

import android.content.Context;
import android.graphics.Color;

import androidx.core.content.ContextCompat;


public class Utils {
    public static Color getColor(Context context, String colourResourceName) {
        int colourId = context.getResources().getIdentifier(colourResourceName, "color", context.getPackageName());
        if (colourId != 0) {
            return Color.valueOf(ContextCompat.getColor(context, colourId)); //Fetch color from resources
        } else {
            throw new IllegalArgumentException("Color resource not found: " + colourResourceName);
        }
    }

    /**
     * Calculates the magnitude of the distance between two points latitude and longitude.
     * This value does not represent the actual distance, only a magnitude value that can be used for comparison purposes.
     * This can be used for any sphere of any size.
     *
     * @param longitude1 longitude of the first point
     * @param latitude1 latitude of the first point
     * @param longitude2 longitude of the second point
     * @param latitude2 latitude of the second point
     * @return double magnitude of the distance between two points latitude and longitude
     */
    public static double distanceMagnitude(double longitude1, double latitude1, double longitude2, double latitude2) {
        //Using the Haversine formula approach
        //Convert from degrees to radians
        latitude1  = Math.toRadians(latitude1);
        longitude1 = Math.toRadians(longitude1);
        latitude2  = Math.toRadians(latitude2);
        longitude2 = Math.toRadians(longitude2);

        //Differences in latitude and longitude
        double latitudeDiff  = latitude2 - latitude1;
        double longitudeDiff = longitude2 - longitude1;

        //Calculate the chord length squared
        return Math.pow(Math.sin(latitudeDiff / 2), 2)  + Math.cos(latitude1) * Math.cos(latitude2) * Math.pow(Math.sin(longitudeDiff / 2), 2);
    }

    private static final double EARTH_RADIUS = 6371000.0; //Global average. Close enough!

    /**
     * Calculates the approximate distance between two points latitude and longitude on Earth.
     * This treats the earth as a sphere with an approximated average radius of {@link this#EARTH_RADIUS} meters.
     *
     * @param longitude1 longitude of the first point
     * @param latitude1 latitude of the first point
     * @param longitude2 longitude of the second point
     * @param latitude2 latitude of the second point
     * @return double The distance in meters between two points latitude and longitude on Earth
     */
    public static double distanceMeters(double longitude1, double latitude1, double longitude2, double latitude2) {
        double magSquared = distanceMagnitude(longitude1, latitude1, longitude2, latitude2);
        return EARTH_RADIUS * (2 * Math.atan2(Math.sqrt(magSquared), Math.sqrt(1 - magSquared)));
    }
}
