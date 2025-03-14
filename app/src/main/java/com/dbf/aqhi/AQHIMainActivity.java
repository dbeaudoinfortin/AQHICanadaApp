package com.dbf.aqhi;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.aqhi.service.AQHIBackgroundWorker;
import com.dbf.aqhi.service.AQHIService;
import com.dbf.heatmaps.android.HeatMap;
import com.dbf.heatmaps.android.HeatMapOptions;
import com.dbf.heatmaps.axis.IntegerAxis;
import com.dbf.heatmaps.data.BasicDataRecord;
import com.dbf.heatmaps.data.DataRecord;

import java.nio.charset.Charset;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;

public class AQHIMainActivity extends AppCompatActivity implements AQHIFeature {
    private static final String LOG_TAG = "AQHIMainActivity";
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

        initUI();

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
        boolean isStationAuto = backgroundWorker.getAQHIService().isStationAuto();
        if(!isStationAuto || requestPermissions()) {
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

        //TODO: Force a widget update so the widgets aren't out of sync.
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
    private void initUI() {
        Log.i(LOG_TAG, "Initializing AQHI Main Activity UI.");

        ImageView arrowImage = findViewById(R.id.imgAQHIGaugeArrow);
        arrowImage.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                arrowImage.getViewTreeObserver().removeOnPreDrawListener(this);
                arrowImage.setPivotX(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,6, getResources().getDisplayMetrics()));
                arrowImage.setPivotY(arrowImage.getHeight() / 2f);
                return true;
            }
        });
    }

    private float calculateGaugeArrowRotation(Double aqhi) {
        aqhi = Math.max(Math.min(aqhi,11d),1d);
        return (float) ((((11-aqhi) / 10) * (-163.636)) -8.182);
    }

    private void updateUI() {
        Log.i(LOG_TAG, "Updating AQHI Main Activity UI.");
        String recentStation = backgroundWorker.getAQHIService().getStationName();

        //UPDATE LOCATION TEXT
        TextView locationText = findViewById(R.id.txtLocation);
        if(null == recentStation || recentStation.isEmpty()) {
            locationText.setText("Unknown");
        } else {
            locationText.setText(recentStation);
        }

        //UPDATE AQHI TEXT
        TextView aqhiText = findViewById(R.id.txtAQHIValue);
        aqhiText.setText(this.getLatestAQHIString());

        //UPDATE GAUGE ANGLE
        ImageView arrowImage = findViewById(R.id.imgAQHIGaugeArrow);
        Double aqhi = this.getLatestAQHI();
        if(null == aqhi) {
            arrowImage.setVisibility(INVISIBLE);
        } else {
            aqhi = Math.max(Math.min(aqhi,11d),1d);
            arrowImage.setPivotX(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,6, getResources().getDisplayMetrics()));
            arrowImage.setPivotY(arrowImage.getHeight() / 2f);
            float angle = (float) ((((11-aqhi) / 10) * (-163.636)) -8.182);
            arrowImage.setRotation(angle);
            arrowImage.setVisibility(VISIBLE);
        }

        ArrayList<DataRecord> records = new ArrayList<DataRecord>(values.length);
        int arrayIndex = 0;
        for (int x = 1; x <= 11; x++ ) {
            records.add(new BasicDataRecord(x,1, values[arrayIndex]));
            arrayIndex++;
        }

        Bitmap b = HeatMap.builder()
                .withTitle("Wacky Bad Air Dude")
                .withXAxis(IntegerAxis.instance()
                        .withTitle("")
                        .addEntries(1, 11))
                .withYAxis(IntegerAxis.instance()
                        .withTitle("")
                        .addEntries(1,1))
                .withOptions(HeatMapOptions.builder()
                        .withCellWidth(100)
                        .withCellHeight(50)
                        .withShowGridlines(false)
                        .withShowGridValues(false)
                        .withHeatMapTitlePadding(0)
                        .withOutsidePadding(0)
                        .withBackgroundColour(Color.valueOf(Color.WHITE))
                        .withShowLegend(false)
                        .withShowGridlines(false)
                        .withShowXAxisLabels(false)
                        .withShowYAxisLabels(false)
                        .withLegendTextFormat("0.#")
                        .withColourScaleLowerBound(0.0)
                        .withColourScaleUpperBound(11.0)
                        .build())
                .build().render(records);

        ImageView imgHistoricalHeatMap = findViewById(R.id.imgHistoricalHeatMap);
        imgHistoricalHeatMap.setImageBitmap(b);
    }

    private static final double[] values = { 1,2,3,4,5,6,7,8,9,10,11 };

    @Override
    public AQHIService getAQHIService() {
        return backgroundWorker.getAQHIService();
    }

    public void showLegalNotices(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Legal Notices");

        // Create a TextView to show the message
        TextView messageTextView = new TextView(this);
        messageTextView.setText(Html.fromHtml(loadNotices(),Html.FROM_HTML_MODE_LEGACY));
        messageTextView.setMovementMethod(LinkMovementMethod.getInstance());
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        messageTextView.setPadding(padding, padding, padding, padding);
        builder.setView(messageTextView);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private String loadNotices(){
        try {
            return IOUtils.toString(getResources().openRawResource(R.raw.legal_notices), Charset.defaultCharset());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to load legal notices.", e);
            return "<p>Failed to load legal notices.</p>";
        }
    }
}