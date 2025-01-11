package com.dbf.aqhi.permissions;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

public class PermissionService {

    /**
     * Check if the user has granted the ACCESS_COARSE_LOCATION permission to this app.
     * @return true if the permission was already granted, false otherwise.
     */
    public static boolean checkLocationPermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if the user has granted the ACCESS_BACKGROUND_LOCATION permission to this app.
     * @return true if the permission was already granted, false otherwise.
     */
    public static boolean checkBackgroundLocationPermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
