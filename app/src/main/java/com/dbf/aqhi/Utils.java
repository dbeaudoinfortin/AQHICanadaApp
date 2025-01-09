package com.dbf.aqhi;

public class Utils {
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
    public static double distanceMeters(double longitude1, double latitude1, double longitude2, double latitude2) {
        double magSquared = distanceMagnitude(longitude1, latitude1, longitude2, latitude2);
        return EARTH_RADIUS * (2 * Math.atan2(Math.sqrt(magSquared), Math.sqrt(1 - magSquared)));
    }
}
