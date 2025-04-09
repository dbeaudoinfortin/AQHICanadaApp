package com.dbf.aqhi.main;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;

import com.dbf.aqhi.AQHIActivity;
import com.dbf.aqhi.R;
import com.dbf.aqhi.geomet.station.Station;
import com.dbf.aqhi.service.AQHIService;
import com.dbf.utils.stacktrace.StackTraceCompactor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import ovh.plrapps.mapview.MapView;
import ovh.plrapps.mapview.MapViewConfiguration;
import ovh.plrapps.mapview.api.MinimumScaleMode;
import ovh.plrapps.mapview.core.TileStreamProvider;

public class AQHILocationActivity extends AQHIActivity {

    private static final String LOG_TAG = "AQHILocationActivity";

    private AQHIService aqhiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_location_activity);
        initAQHIService(this);
        initUI ();
    }

    private synchronized void initAQHIService(Context context) {
        if(null == aqhiService) {
            aqhiService = new AQHIService(context, ()->{
                Log.i(LOG_TAG, "AQHI Service update complete. Updating the change location UI.");
                    updateUI();
            });
        }
    }

    private void updateUI() {
        //Nothing yet
    }


    protected void initUI () {
        //Close Button Handler
        findViewById(R.id.btnSaveLocation).setOnClickListener(v -> {
            Intent resultValue = new Intent();
            setResult(RESULT_OK, resultValue);
            finish();
        });

        MapView imageView = findViewById(R.id.mapView);
        imageView.configure(getMapViewConfiguration());

        addMapMarkers(imageView);
    }

    private void addMapMarkers(MapView imageView) {
        List<Station> stations =  aqhiService.getGeoMetService().loadStations(false);
        //Create the pin image

        for(Station station : stations) {
            final double lon = station.geometry.coordinates.get(0);
            final double lat = station.geometry.coordinates.get(1);
            ImageView pinView = new ImageView(this);
            pinView.setImageResource(R.drawable.location_pin);
            imageView.getMarkerLayout().addMarker(pinView, 1000, 1000, -0.5f, -1.0f, 0f, 0f, station.id);

        }
    }


    private MapViewConfiguration getMapViewConfiguration() {
        TileStreamProvider tsp = (row, col, zoomLvl) -> {
            try {
                return getAssets().open("map_tiles/" + zoomLvl + "/" + row + "/" + col + ".webp");
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to load map tile row: "+ row + ", col: " + col + ", lvl:"
                        + zoomLvl + ".\n" + StackTraceCompactor.getCompactStackTrace(e));
            }
            return null;
        };
        MapViewConfiguration config = new MapViewConfiguration(9,35000,29699,256,tsp);
        config.setMaxScale(3);
        config.setMinimumScaleMode(MinimumScaleMode.FILL);
        return config;
    }

    @Override
    public AQHIService getAQHIService() {
        return null;
    }
}