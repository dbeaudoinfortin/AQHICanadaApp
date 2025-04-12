package com.dbf.aqhi.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;

import com.dbf.aqhi.AQHIActivity;
import com.dbf.aqhi.R;
import com.dbf.aqhi.geomet.station.Station;
import com.dbf.aqhi.location.MapTransformer;
import com.dbf.aqhi.service.AQHIService;
import com.dbf.utils.stacktrace.StackTraceCompactor;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;

import ovh.plrapps.mapview.MapView;
import ovh.plrapps.mapview.MapViewConfiguration;
import ovh.plrapps.mapview.api.MarkerApiKt;
import ovh.plrapps.mapview.api.MinimumScaleMode;
import ovh.plrapps.mapview.core.TileStreamProvider;
import ovh.plrapps.mapview.markers.MarkerTapListener;

public class AQHILocationActivity extends AQHIActivity {

    private static final String LOG_TAG = "AQHILocationActivity";

    private AQHIService aqhiService;
    private MapView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_location_activity);
        initAQHIService(this);
        initUI();
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
        //Set the auto-select dropdown
        boolean stationAuto = aqhiService.isStationAuto();
        Map<String, Station> stations =  aqhiService.getGeoMetService().loadStations(false); //TODO: This risks doing a network call on the main thread. need to add a flag to avoid it
        addMapMarkers(stationAuto, stations, imageView);
    }


    protected void initUI () {
        //Close Button Handler
        findViewById(R.id.btnSaveLocation).setOnClickListener(v -> {
            Intent resultValue = new Intent();
            setResult(RESULT_OK, resultValue);
            finish();
        });

        //Set initial states
        boolean stationAuto = aqhiService.isStationAuto();

        SwitchMaterial rbAuto = findViewById(R.id.swtAutomatic);
        rbAuto.setChecked(stationAuto);

        AutoCompleteTextView stationDropDown = findViewById(R.id.ddLocation);

        String stationName = aqhiService.getStationName(true);
        stationDropDown.setText(null == stationName ? "" : stationName);
        stationDropDown.setEnabled(!stationAuto);

        //Populate the dropdown
        Map<String, Station> stations =  aqhiService.getGeoMetService().loadStations(false);

        stationDropDown.setAdapter(new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            stations.values().stream().map(s-> new StationEntry(s.id,s.properties.location_name_en)).sorted(Comparator.comparing(s -> s.label)).toList()
        ));
        stationDropDown.setOnItemClickListener((parent, view, position, id) -> {
            StationEntry selected = (StationEntry) parent.getItemAtPosition(position);
            if (selected != null) stations.get(selected.id);
        });

        //Toggle auto location on and off
        rbAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            aqhiService.setStationAuto(isChecked);
            stationDropDown.setEnabled(!isChecked);
            if (isChecked) {
                Log.i(LOG_TAG, "Station selection set to auto.");
                stationDropDown.setText(""); // Clear selection
            } else {
                Log.i(LOG_TAG, "Station selection set to manual.");
            }
            updateUI();
        });

        //Create the map
        imageView = findViewById(R.id.mapView);
        imageView.configure(getMapViewConfiguration());
        MarkerTapListener tapper = (view, x, y) -> {
            final String stationID = ((MarkerView) view).getMarkerName();
            if (null == stationID || "".equals(stationID)) return;
            changeStation((MarkerView) view, stations.get(stationID));
        };
        imageView.getMarkerLayout().setMarkerTapListener(tapper);

        //Refresh the UI
        updateUI();
    }

    private void changeStation(MarkerView view, Station station) {
        if (null == station) return;
        Log.i(LOG_TAG, "Changing the selected station to ID: " + station.id);

        //Update the saved preferences
        //TODO: do it

        //Refresh the UI
        updateUI();
    }

    private void addMapMarkers(boolean stationAuto, Map<String, Station> stations, MapView imageView) {

        if(stationAuto) {
            //Place a single marker and zoom the map the the auto-selected location
            Pair<Float, Float> latLon = aqhiService.getStationLatLon(true);
            if(latLon.first < -200f || latLon.second < -200f) {
                Log.e(LOG_TAG, "Invalid latitude and longitude coordinates for the current station: " + latLon.first + ", " + latLon.second);
            } else {
                MarkerView pinView = placePinOnMap(imageView, latLon.first, latLon.second, "");
                //Zoom the map to the selected location
                MarkerApiKt.moveToMarker(imageView, pinView, 1.5f, false);
            }
        } else {
            //Place markers for every single location
            //TODO: intelligently replace markers, don't delete and re-add everything
            //TODO: The currently selected station (if any) should be a different colour pin
            //TODO: zoom the the selected pin, if there is one, or the whole map otherwise
            if(null != stations) {
                for(Station station : stations.values()) {
                    placePinOnMap(imageView, station.geometry.coordinates.get(1), station.geometry.coordinates.get(0), station.id);
                }
            }
        }
    }

    private MarkerView placePinOnMap(MapView imageView, float lat, float lon, String pinName) {
        //Create a new pin image each time
        MarkerView pinView = new MarkerView(this, pinName);
        pinView.setImageResource(R.drawable.location_pin);
        final Pair<Integer, Integer> coordinates = MapTransformer.transform(lat,lon);
        imageView.getMarkerLayout().addMarker(pinView, coordinates.first, coordinates.second, -0.5f, -0.8f, 0f, 0f, pinName);
        return pinView;
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

    private class StationEntry {
        public final String id;
        public final String label;

        public StationEntry(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @SuppressLint("AppCompatCustomView")
    private class MarkerView extends ImageView {
        private final String markerName;

        public MarkerView(Context context, String stationID) {
            super(context);
            this.markerName = stationID;
        }

        public String getMarkerName() {
            return markerName;
        }
    }
}