package com.dbf.aqhi.main;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
import java.util.concurrent.ConcurrentHashMap;

import ovh.plrapps.mapview.MapView;
import ovh.plrapps.mapview.MapViewConfiguration;
import ovh.plrapps.mapview.api.MarkerApiKt;
import ovh.plrapps.mapview.api.MinimumScaleMode;
import ovh.plrapps.mapview.core.TileStreamProvider;
import ovh.plrapps.mapview.markers.MarkerTapListener;

public class AQHILocationActivity extends AQHIActivity {

    private static final String LOG_TAG = "AQHILocationActivity";
    public static final String SELECTED = "SELECTED";

    private AQHIService aqhiService;
    private MapView mapView;
    private Map<String, Station> stationsCache;

    private final Map<String, MarkerView> markers = new ConcurrentHashMap<String, MarkerView>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.change_location_activity);
        initAQHIService(this);
        initUI();
        //This is async on a separate thread, it will call updateUI() when done
        aqhiService.updateAQHI();
    }

    private synchronized void initAQHIService(Context context) {
        if(null == aqhiService) {
            aqhiService = new AQHIService(context, ()-> {
                Log.i(LOG_TAG, "AQHI Service update complete. Updating the change location UI.");
                //Must always run on the UI thread
                runOnUiThread(this::updateUI);
            });
        }
    }

    private synchronized void updateUI() {
        boolean stationAuto = aqhiService.isStationAuto();
        String stationName  = aqhiService.getStationName(true);

        //Auto Toggle Switch
        SwitchMaterial rbAuto = findViewById(R.id.swtAutomatic);
        rbAuto.setChecked(stationAuto);

        //Station drop-down
        AutoCompleteTextView stationDropDown = findViewById(R.id.ddLocation);
        stationDropDown.setText(null == stationName ? "" : stationName);
        stationDropDown.setEnabled(!stationAuto);

        //Populate the dropdown
        Map<String, Station> stations = getStations();
        //Check this every time on updateUI() in case the first time loaded the station list was empty
        if(null != stations && !stations.isEmpty()
                && (stationDropDown.getAdapter() == null || stationDropDown.getAdapter().getCount() < 1)) {
            stationDropDown.setAdapter(new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    stations.values().stream().map(s-> new StationEntry(s.id, s.properties.location_name_en)).sorted(Comparator.comparing(s -> s.label)).toList()
            ));
        }
        updateMapMarkers(stationAuto);
    }

    protected void initUI () {
        //Close Button Handler
        findViewById(R.id.btnSaveLocation).setOnClickListener(v -> {
            Intent resultValue = new Intent();
            //Check to make sure the user has selected a location before leaving
            boolean stationAuto = aqhiService.isStationAuto();
            if(!stationAuto) {
                String stationName  = aqhiService.getStationName(true);
                if(null == stationName || stationName.isEmpty()) {
                    //Create a pop-up warning the user that no location has been selected
                    new AlertDialog.Builder(this)
                        .setTitle("No Location Selected")
                        .setMessage("No location has been selected. Are you sure you want to exit?")
                        .setPositiveButton("Exit Anyway", (dialog, which) -> {
                            setResult(RESULT_CANCELED, resultValue);
                            finish();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            // Dismiss the dialog and let the user select a location.
                            dialog.dismiss();
                        }).show();
                    return;
                }
            }
            setResult(RESULT_OK, resultValue);
            finish();
        });

        //Create the map, do this before updating the UI!
        mapView = findViewById(R.id.mapView);
        mapView.configure(getMapViewConfiguration());
        MarkerTapListener tapper = (view, x, y) -> {
            final String stationID = ((MarkerView) view).getMarkerName();
            if (null == stationID || stationID.isEmpty()) return;
            changeStation(stationID);
        };
        mapView.getMarkerLayout().setMarkerTapListener(tapper);
        //Add a layout listener to ensure a minimum reasonable map size
        mapView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int minHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,300f,getResources().getDisplayMetrics());
                if (mapView.getHeight() < minHeightPx) {
                    ViewGroup.LayoutParams params = mapView.getLayoutParams();
                    params.height = minHeightPx;
                    mapView.setLayoutParams(params);
                }
                //Remove the listener so it doesn't keep firing
                //Will be re-added on rotation
                mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        //Refresh the UI
        //Do this first to avoid triggering onChange events
        updateUI();

        //Create a click listener for selecting a new station
        AutoCompleteTextView stationDropDown = findViewById(R.id.ddLocation);
        stationDropDown.setOnItemClickListener((parent, view, position, id) -> {
            StationEntry selected = (StationEntry) parent.getItemAtPosition(position);
            if (selected != null) changeStation(selected.id);
        });

        //Toggle auto location on and off
        SwitchMaterial rbAuto = findViewById(R.id.swtAutomatic);
        rbAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
            aqhiService.setStationAuto(isChecked);
            aqhiService.clearAllPreferences();
            updateUI();
            if(isChecked) {
                aqhiService.updateAQHI(); //Rediscover a new station
            }
        });
    }

    private Station getStation(String stationID) {
        if(null == stationID) return null;
        Map<String, Station> stations = getStations();
        if(null == stations) return null;
        return stations.get(stationID);
    }

    private synchronized Map<String, Station> getStations() {
        if(null == stationsCache) {
            //Set allowRemote to false to avoid potentially doing a network call on the main thread.
            stationsCache = aqhiService.getGeoMetService().loadStations(false, false);
        }
        if(null == stationsCache) {
            Log.e(LOG_TAG, "Failed to load the station definition list.");
        }
        return stationsCache;
    }

    /**
     * Changes the currently selected location, refreshes the UI and re-fetches fresh AQHI data
     *
     * @param stationID The ID of the station
     * @return boolean, indicating if the selected location was changed
     */
    private boolean changeStation(String stationID) {
        //Note: you can't re-select the selected location
        if (null == stationID || stationID.isEmpty() || stationID.equals(SELECTED)) return false;
        Log.i(LOG_TAG, "Changing the selected station to ID: " + stationID);

        Station newStation = getStation(stationID);
        if(null != newStation) {
            aqhiService.setStation(newStation);
            aqhiService.clearAllData();
            updateUI();
            aqhiService.updateAQHI(); //Wise idea to fetch fresh data now
            return true;
        }

        Log.w(LOG_TAG, "Could not determine the new station ID: " + stationID);
        return false;
    }

    private void updateMapMarkers(boolean stationAuto) {
        Pair<Float, Float> latLon = aqhiService.getStationLatLon(true);
        String stationID = aqhiService.getStationCode(true);

        if(stationAuto) {
            clearMarkers(); //We can safely clear all markers in this case
        } else {
            //Place markers for every single location
            createMarkers();
        }

        //Set the selected station, if there is one
        if(null != stationID) {
            if(latLon.first < -200f || latLon.second < -200f) {
                Log.e(LOG_TAG, "Invalid latitude and longitude coordinates for the current station: " + latLon.first + ", " + latLon.second);
            } else {
                //Place a single marker and zoom the map the the auto-selected location
                //The previous selected marker was automatically removed but a regular marker of the
                //same station ID would not have been removed yet.
                removeMarker(stationID);
                MarkerView pinView = createMarker(latLon.first, latLon.second, true, SELECTED);
                MarkerApiKt.moveToMarker(mapView, pinView, 1.5f, false);
            }
        } else {
            //This could can be valid scenario if the user location was never determined.
            //Zoom out to the whole map
            mapView.setScaleFromCenter(0);
        }
    }

    private void clearMarkers() {
        synchronized (markers) {
            for (MarkerView marker : markers.values()) {
                mapView.getMarkerLayout().removeMarker(marker);
            }
            markers.clear();
        }
    }

    private void removeMarker(String markerName) {
        synchronized (markers) {
            MarkerView marker = markers.remove(markerName);
            if (null != marker) {
                mapView.getMarkerLayout().removeMarker(marker);
            }
        }
    }

    private void createMarkers() {
        Map<String, Station> stations = getStations();
        if(null == stations) return;

        synchronized (markers) {
            //Remove all existing markers that shouldn't exist anymore
            //And any previously selected marker that will need to be re-created
            markers.keySet().stream()
                    .filter(k->!stations.containsKey(k))
                    .toList()
                    .stream() //Avoid concurrent modification
                    .forEach(this::removeMarker);
            //Add new markers
            stations.values().stream()
                    .filter(s->!markers.containsKey(s.id))
                    .forEach(s->createMarker(s.geometry.coordinates.get(1), s.geometry.coordinates.get(0), false, s.id));
        }
    }

    private MarkerView createMarker(float lat, float lon, boolean selected, String markerName) {
        //Create a new pin image each time
        MarkerView pinView = new MarkerView(this, markerName);
        pinView.setImageResource(selected ? R.drawable.location_pin_selected : R.drawable.location_pin);
        final Pair<Integer, Integer> coordinates = MapTransformer.transform(lat,lon);

        synchronized (markers) {
            mapView.getMarkerLayout().addMarker(pinView, coordinates.first, coordinates.second, -0.5f, -0.8f, 0f, 0f, markerName);
            markers.put(markerName, pinView);
        }
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

    private static class StationEntry {
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
    private static class MarkerView extends ImageView {
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