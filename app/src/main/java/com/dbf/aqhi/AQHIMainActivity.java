package com.dbf.aqhi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.aqhi.service.AQHIBackgroundWorker;

import java.text.DecimalFormat;

public class AQHIMainActivity extends AppCompatActivity {
    private static final String LOG_TAG = "AQHIMainActivity";

    private static final String AQHI_DIGIT_FORMAT = "0.##";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private AQHIBackgroundWorker backgroundWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(LOG_TAG, "AQHI Main Activity created.");

        //Visual initialization
        EdgeToEdge.enable(this);
        setContentView(R.layout.main_activity);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //Initialize a background thread that will periodically refresh the
        //user's location and the latest AQHI data.
        backgroundWorker = new AQHIBackgroundWorker(this, ()->{
            //Must always run on the UI thread
            runOnUiThread(this::updateUI);
        });

        //Don't wait for the data to be fetched to update the UI.
        //We can still show the old values if the data is not too stale.
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(LOG_TAG, "AQHI Main Activity resumed.");
        backgroundWorker.resume();

        //Ask the user to accept the location permission when the app loads
        boolean isStationAuto = backgroundWorker.getAqhiService().isStationAuto();
        if(!isStationAuto || (isStationAuto && requestPermissions())) {
            //User has already granted the permissions, update the location and data right now.
            //Otherwise, the data will only be updated after the user accepts the permission request.
            backgroundWorker.updateNow(); //Updates will be done async.
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "AQHI Main Activity destroyed.");
        backgroundWorker.stop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(LOG_TAG, "AQHI Main Activity paused.");
        backgroundWorker.stop();
    }

    private boolean requestPermissions() {
        if (!PermissionService.checkLocationPermission(this)) {
            Log.i(LOG_TAG, "Requesting location permission for AQHI Main Activity.");
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
                Log.i(LOG_TAG, "Location permission granted.");
                backgroundWorker.updateNow();
            } else {
                Toast.makeText(this, R.string.Location_perm_required, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateUI() {
        Log.i(LOG_TAG, "Updating AQHI Main Activity UI.");
        String recentStation = backgroundWorker.getAqhiService().getStation();
        Double recentAQHI = backgroundWorker.getAqhiService().getLatestAQHI();

        TextView locationText = findViewById(R.id.txtLocation);
        if(null == recentStation || recentStation.isEmpty()) {
            locationText.setText("Location: Unknown");
        } else {
            locationText.setText("Location: " + recentStation);
        }

        TextView aqhiText = findViewById(R.id.txtCurrentAQHI);
        if(null == recentAQHI) {
            aqhiText.setText("Current AQHI: …");
        } else if( recentAQHI < 0.0) {
            aqhiText.setText("Current AQHI: Unknown");
        } else {
            DecimalFormat df = new DecimalFormat(AQHI_DIGIT_FORMAT); // Not thread safe
            aqhiText.setText("Current AQHI: " + df.format(recentAQHI));
        }
    }
}