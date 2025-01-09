package com.dbf.aqhi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dbf.aqhi.geomet.GeoMetService;
import com.dbf.aqhi.geomet.realtime.RealtimeData;
import com.dbf.aqhi.geomet.station.Station;
import com.dbf.aqhi.location.LocationService;
import com.dbf.aqhi.service.AQHIService;
import com.dbf.heatmaps.HeatMap;
import com.dbf.heatmaps.HeatMapGradient;
import com.dbf.heatmaps.HeatMapOptions;
import com.dbf.heatmaps.axis.IntegerAxis;
import com.dbf.heatmaps.data.DataRecord;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AQHIMainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "AQHIMainActivity";

    private static final String AQHI_DIGIT_FORMAT = "0.##";

    private static final long UPDATE_INTERVAL = 300; //In seconds, 5 minutes
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private LocationService locationService;

    private AQHIService aqhiService;
    private ScheduledExecutorService updateScheduler;
    private Runnable updateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Visual initialization
        EdgeToEdge.enable(this);
        setContentView(R.layout.main_activity);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //Location and AQHIService initialization. Must be done onCreate.
        locationService = new LocationService(this);
        aqhiService = new AQHIService(this, locationService);

        updateUI(); //Don't wait for the data to be fetched to update the UI
    }

    @Override
    protected void onResume() {
        super.onResume();
        startUpdateScheduler();
        updateLocation();
        updateAQHI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopUpdateScheduler();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdateScheduler();
    }

    private void startUpdateScheduler(){
        updateScheduler = Executors.newScheduledThreadPool(1);
        updateScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    updateLocation();
                } catch (Throwable t) { //Catch all, protect the scheduler
                    Log.e(LOG_TAG, "Failed to run scheduled AQHI update.", t);
                }
            }
            //Update every 5 minutes, starting 5 minutes from now
        }, UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.SECONDS);
    }

    private void stopUpdateScheduler(){
        if (updateScheduler != null && !updateScheduler.isShutdown()) {
            updateScheduler.shutdown();
        }
    }

    private void updateLocation() {
        try {
            if(checkLocationPermissions()) {
                //User has already granted the permissions, update the location now.
                //Otherwise, the location will only be updated after the user accepts the permission request
                locationService.updateLocation(()->{
                    updateAQHI();
                }); //This is async
            }
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Failed to update the location for the main activity.", t);
        }
    }

    /**
     * Check if the user has granted the location permission to this Activity.
     * @return true if the location permission was already granted, false otherwise.
     */
    private boolean checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateLocation();
            } else {
                Toast.makeText(this, "Location permission is required to determine the nearest AQHI observation station.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void updateAQHI(){
        aqhiService.updateAQHI(()->{
            //Must always run on the UI thread
            runOnUiThread(() -> updateUI());
        });
    }

    private void updateUI() {
        String recentStation = aqhiService.getRecentStation();
        TextView locationText = findViewById(R.id.txtLocation);
        if(null == recentStation || recentStation.isEmpty()) {
            locationText.setText("Location: Unknown");
        } else {
            locationText.setText("Location: " + recentStation);
        }

        Double recentAQHI = aqhiService.getRecentAQHI();
        TextView aqhiText = findViewById(R.id.txtCurrentAQHI);
        if(null == recentAQHI) {
            aqhiText.setText("Current AQHI: ...");
        } else if( recentAQHI < 0.0) {
            aqhiText.setText("Current AQHI: Unknown");
        } else {
            DecimalFormat df = new DecimalFormat(AQHI_DIGIT_FORMAT); // Not thread safe
            aqhiText.setText("Current AQHI: " + df.format(recentAQHI));
        }
    }
}