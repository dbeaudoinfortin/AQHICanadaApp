package com.dbf.aqhi;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.dbf.utils.stacktrace.StackTraceCompactor;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class Utils {

    private static final String LOG_TAG = "Utils";

    public static String loadCompressedResource(Context context, int resourceID) {
        try (InputStream is = context.getResources().openRawResource(resourceID);
             BufferedInputStream bis = new BufferedInputStream(is);
             ZipInputStream zis = new ZipInputStream(bis)){
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (!ze.isDirectory()) {
                    return IOUtils.toString(zis, Charset.defaultCharset());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to decompress resource file " + resourceID + ". Exception:\n" + StackTraceCompactor.getCompactStackTrace(e));
        }
        return null;
    }

    /**
     * Converts a time in 24-hour format (1-24) into 12-hour format (12-11)
     *
     * @param hour_24  Time in 24-hour format
     * @return int Time in 12-hour format
     */
    public static int to12Hour(int hour_24) {
        return (hour_24 % 12 == 0) ? 12 : hour_24 % 12;
    }

    /**
     * Loads a Color from a resource bundle.
     *
     * @param context
     * @param colourResourceName
     * @return Color
     */
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
