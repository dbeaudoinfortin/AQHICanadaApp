package com.dbf.aqhi.main;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.dbf.aqhi.map.OverlayTileProvider.MAX_PIXEL_VALUE;

import android.Manifest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.dbf.aqhi.AQHIActivity;
import com.dbf.aqhi.R;
import com.dbf.aqhi.Utils;
import com.dbf.aqhi.api.datamart.Pollutant;
import com.dbf.aqhi.api.weather.alert.Alert;
import com.dbf.aqhi.data.spatial.SpatialData;
import com.dbf.aqhi.data.spatial.SpatialDataService;
import com.dbf.aqhi.map.CompositeTileProvider;
import com.dbf.aqhi.map.MapTransformer;
import com.dbf.aqhi.map.OverlayTileProvider;
import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.aqhi.data.BackgroundDataWorker;
import com.dbf.aqhi.data.aqhi.AQHIDataService;
import com.dbf.heatmaps.android.HeatMap;
import com.dbf.heatmaps.android.HeatMapGradient;
import com.dbf.heatmaps.android.HeatMapOptions;
import com.dbf.heatmaps.axis.IntegerAxis;
import com.dbf.heatmaps.axis.StringAxis;
import com.dbf.heatmaps.data.BasicDataRecord;
import com.dbf.heatmaps.data.DataRecord;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import ovh.plrapps.mapview.MapView;
import ovh.plrapps.mapview.MapViewConfiguration;
import ovh.plrapps.mapview.api.MarkerApiKt;
import ovh.plrapps.mapview.api.MinimumScaleMode;

public class AQHIMainActivity extends AQHIActivity {
    private static final String LOG_TAG = "AQHIMainActivity";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String MAP_STATION_MARKER_TAG = "STATION_MARKER";
    private static final String MAP_TOUCH_MARKER_TAG = "TOUCH_MARKER";

    private BackgroundDataWorker backgroundWorker;
    private boolean showHistoricalGridData = false;
    private boolean showForecastGridData = false;
    private boolean showGaugeNumbers = false;

    //Pollution Map attributes
    private Pollutant selectedMapPollutant;
    private int mapScaleIndex = 1;
    private CompositeTileProvider tileProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(LOG_TAG, "AQHI Main Activity created.");
        setContentView(R.layout.main_activity);

        initUI();

        //Initialize a background thread that will periodically refresh the
        //user's location and the latest AQHI data.
        backgroundWorker = new BackgroundDataWorker(this, ()->{
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

        //The pollutant list cache needs to be refreshed since the data may no longer be valid
        getSpatialDataService().clearLoadedPollutants();

        //Restart the periodic updates
        backgroundWorker.resume();

        //Ask the user to accept the location permission when the app loads
        boolean isStationAuto = getAQHIService().isStationAuto();
        if(!isStationAuto || requestPermissions()) {
            //Either we don't require a location update (non-auto location) or the user has
            //already granted the permissions. Update the data right now.
            //Otherwise, the data will only be updated after the user accepts the permission request.
            backgroundWorker.updateNow(); //Updates will be done async.
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(LOG_TAG, "AQHI Main Activity destroyed.");
        backgroundWorker.stop();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.i(LOG_TAG, "AQHI Main Activity paused.");
        forceWidgetUpdate();
        backgroundWorker.stop();
        super.onPause();
    }

    private boolean requestPermissions() {
        if (!PermissionService.checkLocationPermission(this)) { //Has the location permission already been granted?
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
                Log.i(LOG_TAG, "Location permission was not granted. Disabling location auto-discovery.");
                showNoPermission(this, true);
            }
        }
    }

    protected void initUI() {
        Log.i(LOG_TAG, "Initializing AQHI Main Activity UI.");

        initOverlayMapUI();

        //Add a click listener for the change location text
        findViewById(R.id.txtChangeLocationLink).setOnClickListener(v -> {
            Intent intent = new Intent(AQHIMainActivity.this, AQHILocationActivity.class);
            startActivity(intent);
        });

        //Align the arrow to the correct center position of the gauge
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

        //Add a click listener to show the numbers on the gauge
        ImageView imgGauge = findViewById(R.id.imgAQHIGaugeBackground);
        imgGauge.setOnClickListener(v -> {
            showGaugeNumbers = !showGaugeNumbers;
            if (showGaugeNumbers) {
                imgGauge.setImageResource(R.drawable.gauge_background_numbers);
            } else {
                imgGauge.setImageResource(R.drawable.gauge_background);
            }
        });

        initHeatMapUI();
    }

    private void initHeatMapUI() {
        //Add a click listener to the heatmaps that will render them with data
        ImageView imgHistoricalHeatMap = findViewById(R.id.imgHistoricalHeatMap);
        imgHistoricalHeatMap.setOnClickListener(v -> {
            showHistoricalGridData = !showHistoricalGridData;
            Map<Date, Double> histData = getAQHIService().getHistoricalAQHI();
            renderHistoricalHeatMap(histData);
        });

        ImageView imgForecastHeatMap = findViewById(R.id.imgForecastHeatMap);
        imgForecastHeatMap.setOnClickListener(v -> {
            showForecastGridData = !showForecastGridData;
            Map<Date, Double> forecastData = getAQHIService().getForecastAQHI();
            renderForecastHeatMap(forecastData);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initOverlayMapUI() {
        //Create the map, regardless if we have overlay data or not.
        //Overlays will be applied in the updateUI() method.
        tileProvider = new CompositeTileProvider(getMapTileProvider());
        MapView mapView = findViewById(R.id.mapView);
        MapViewConfiguration config = getMapConfiguration(tileProvider);
        config.setMaxScale(3);
        config.setMinimumScaleMode(MinimumScaleMode.FILL);
        mapView.configure(config);

        final GestureDetector tapDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapUp(MotionEvent e) {
                final float scale = 1/mapView.getScale();
                final int scrollX = mapView.getScrollX();
                final int scrollY = mapView.getScrollY();
                final float sceneX = (e.getX() + scrollX) * scale;
                final float sceneY = (e.getY() + scrollY) * scale;
                updateOverlayTouchMarker(mapView, sceneX, sceneY);
                return true;
            }
        });

        mapView.setOnTouchListener((view, event) -> {
            //Prevent the map scrolling from fighting the main activity scrolling
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_MOVE:
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            //Handle the pollution value lookup
            tapDetector.onTouchEvent(event);
            return false;
        });

        Spinner pollutantList = findViewById(R.id.ddPollutant);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pollutantList.setAdapter(adapter);
        pollutantList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Pollutant newSelection = Pollutant.fromDisplayName(parent.getSelectedItem().toString());
                if(null == selectedMapPollutant || !selectedMapPollutant.equals(newSelection)) {
                    selectedMapPollutant = Pollutant.fromDisplayName(parent.getSelectedItem().toString());
                    updateOverlayMapUI();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if(null != selectedMapPollutant) {
                    selectedMapPollutant = null;
                    updateOverlayMapUI();
                }
            }
        });

        //Add a click listener to change the colour of the overlay
        View imgScale = findViewById(R.id.imgScale);
        imgScale.setOnClickListener(view -> {
            mapScaleIndex++;
            if(mapScaleIndex > 4) mapScaleIndex = 1;

            int mapScaleGradient = getResources().getIdentifier("map_scale_" + mapScaleIndex, "drawable", getPackageName());
            int mapOverlayColour = getResources().getIdentifier("map_overlay_colour_" + mapScaleIndex, "color", getPackageName());

            view.setBackground(getDrawable(mapScaleGradient));
            OverlayTileProvider overlay = tileProvider.getOverlayTileProvider();
            if(null != overlay) {
                tileProvider.setOverlayTileProvider(new OverlayTileProvider(overlay.getOverlay(), getColor(mapOverlayColour)));
                mapView.redrawTiles();
            }
        });
    }

    private void updateUI() {
        Log.i(LOG_TAG, "Updating AQHI Main Activity UI.");
        String recentStation = getAQHIService().getStationName();

        //UPDATE LOCATION TEXT
        TextView locationText = findViewById(R.id.txtLocation);
        if(null == recentStation || recentStation.isEmpty()) {
            locationText.setText(R.string.unknown_Location);
        } else {
            locationText.setText(recentStation);
        }

        //UPDATE AQHI TEXT
        final Double aqhi = getLatestAQHI(false);
        TextView aqhiText = findViewById(R.id.txtAQHIValue);
        aqhiText.setText(getLatestAQHIString(false));

        TextView aqhiRiskText = findViewById(R.id.txtAQHIRisk);
        if(null == aqhi) {
            aqhiRiskText.setVisibility(GONE);
        } else {
            aqhiRiskText.setVisibility(VISIBLE);
            aqhiRiskText.setText(getRiskFactor(aqhi));
        }

        //UPDATE TYPICAL AQHI TEXT
        final String typicalAQHI = getTypicalAQHIString();
        TextView txtTypicalAQHI = findViewById(R.id.txtTypicalAQHI);
        if(null == typicalAQHI) {
            txtTypicalAQHI.setVisibility(GONE);
        } else {
            txtTypicalAQHI.setVisibility(VISIBLE);
            txtTypicalAQHI.setText(getString(R.string.typical) + " " + typicalAQHI);
        }

        //UPDATE GAUGE ANGLE
        ImageView arrowImage = findViewById(R.id.imgAQHIGaugeArrow);

        if(null == aqhi) {
            arrowImage.setVisibility(INVISIBLE);
        } else {
            arrowImage.setPivotX(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,6, getResources().getDisplayMetrics()));
            arrowImage.setPivotY(arrowImage.getHeight() / 2f);
            arrowImage.setRotation(calculateGaugeArrowRotation(aqhi));
            arrowImage.setVisibility(VISIBLE);
        }

        //UPDATE FORECAST
        Map<Date, Double> forecastData = getAQHIService().getForecastAQHI();
        LinearLayout dailyForecastList = findViewById(R.id.daily_forecast_list);
        updateDailyList(forecastData, dailyForecastList, false);
        renderForecastHeatMap(forecastData);

        //UPDATE HISTORICAL
        Map<Date, Double> histData = getAQHIService().getHistoricalAQHI();
        LinearLayout dailyHistoricalList = findViewById(R.id.daily_historical_list);
        updateDailyList(histData, dailyHistoricalList, true);
        renderHistoricalHeatMap(histData);

        //UPDATE ALERTS
        List<Alert> alerts = getAQHIService().getAlerts();
        TextView txtAlerts = findViewById(R.id.lblAlerts);
        View alertsSection = findViewById(R.id.clAlertsSection);
        if(alerts.isEmpty()) {
            txtAlerts.setVisibility(GONE);
            alertsSection.setVisibility(GONE);
        } else {
            txtAlerts.setVisibility(VISIBLE);
            alertsSection.setVisibility(VISIBLE);
            LinearLayout alertList = findViewById(R.id.alert_list);
            updateAlertList(alertList, alerts);
        }

        //UPDATE POLLUTION VALUES
        updatePollutionList();

        //UPDATE POLLUTANT MAP
        updateOverlayMapUI();
    }

    private void updatePollutionList() {
        LinearLayout pollutantList = findViewById(R.id.pollution_list);

        //Clear any old values
        pollutantList.removeAllViews();

        //TODO: Build pollutant list
        //getSpatialDataService().
        
    }

    private void updateOverlayMapUI() {
        View mapLegend = findViewById(R.id.mapLegendContainer);
        MapView mapView = findViewById(R.id.mapView);
        ImageView mapPlaceholder = findViewById(R.id.imgMapPlaceholder);
        View stationMarker = mapView.getMarkerLayout().getMarkerByTag(MAP_STATION_MARKER_TAG);
        View touchMarker   = mapView.getMarkerLayout().getMarkerByTag(MAP_TOUCH_MARKER_TAG);

        //Update the list of available pollutants
        Collection<String> pollutants = getSpatialDataService().getLoadedPollutants(); //Can't be null
        Spinner pollutantList = findViewById(R.id.ddPollutant);
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) pollutantList.getAdapter();
        adapter.clear();
        adapter.addAll(pollutants);

        if(pollutants.isEmpty()) {
            //No spatial data is available, hide the map
            mapPlaceholder.setVisibility(VISIBLE);
            mapView.setVisibility(GONE);
            pollutantList.setVisibility(GONE);
            mapLegend.setVisibility(GONE);

            selectedMapPollutant = null;
            tileProvider.setOverlayTileProvider(null);
            if(null != stationMarker) mapView.getMarkerLayout().removeMarker(stationMarker);
            if(null != touchMarker) mapView.getMarkerLayout().removeMarker(touchMarker);
            return;
        }

        //We have spatial data, show the map
        mapView.setVisibility(VISIBLE);
        mapLegend.setVisibility(VISIBLE);
        pollutantList.setVisibility(VISIBLE);
        mapPlaceholder.setVisibility(GONE);

        //Update the station location stationMarker first, regardless of the selected pollution overlay
        updateOverlayMapMarker(mapView, stationMarker);

        if(null == selectedMapPollutant || !pollutants.contains(selectedMapPollutant.getDisplayName())) {
            //We need to force a change to the selected pollutant
            selectedMapPollutant = Pollutant.fromDisplayName(pollutants.iterator().next());
            //Deactivate the click listener to prevent it from firing another UI update
            AdapterView.OnItemSelectedListener listener = pollutantList.getOnItemSelectedListener();
            pollutantList.setOnItemSelectedListener(null);
            pollutantList.setSelection(0, false);
            pollutantList.setOnItemSelectedListener(listener);
            //Note: The stationMarker stays put even when the data changed since the location doesn't change
        }

        //Don't reload this from disk every time. Use the in-memory data if it is still fresh.
        SpatialData oldSpatialData = (null == tileProvider.getOverlayTileProvider()) ? null : tileProvider.getOverlayTileProvider().getOverlay();

        //If we already have an overlay then get just load the meta data, not the image data.
        SpatialData newSpatialData = (null == oldSpatialData) ? getSpatialDataService().getSpatialData(selectedMapPollutant) : getSpatialDataService().getSpatialMetaData(selectedMapPollutant);

        if (null == newSpatialData) {
            //We have no spatial data, despite the pollutant list not being empty
            //This is a special error condition or a race condition.
            //Keep rendering the map without a pollution overlay and hope no one notices! :-)
            return;
        }

        boolean dataHasChanged = false;
        if(null == oldSpatialData) {
            //The new spatial data object already contains full image data loaded
            dataHasChanged = true;
        } else if(!oldSpatialData.getModel().equals(newSpatialData.getModel())) {
            //The new spatial data object does not contain the full image data.
            //Re-fetch the new spatial meta data, along with the actual image data, since it might have changed in the last few moments
            newSpatialData = getSpatialDataService().getSpatialData(selectedMapPollutant);
            dataHasChanged = true;
        }

        if(dataHasChanged) {
            TextView minTxt = findViewById(R.id.lblMapMin);
            TextView maxTxt = findViewById(R.id.lblMapMax);
            TextView mapTsText = findViewById(R.id.lblTimestamp);

            if(null == newSpatialData) {
                mapTsText.setVisibility(GONE);
                minTxt.setVisibility(GONE);
                maxTxt.setVisibility(GONE);
                tileProvider.setOverlayTileProvider(null);
                if(null != touchMarker) mapView.getMarkerLayout().removeMarker(touchMarker);
            } else {
                mapTsText.setVisibility(VISIBLE);
                minTxt.setVisibility(VISIBLE);
                maxTxt.setVisibility(VISIBLE);
                minTxt.setText(""+ ((int) selectedMapPollutant.getMinVal()));
                maxTxt.setText(((int) selectedMapPollutant.getMaxVal()) + " " + selectedMapPollutant.getUnits());
                mapTsText.setText(this.getResources().getString(R.string.observations_at) + " " + utcDateToFriendly(newSpatialData.getModel().getModelRunDate(), newSpatialData.getModel().getModelRunHour()));
                int mapOverlayColour = getResources().getIdentifier("map_overlay_colour_" + mapScaleIndex, "color", getPackageName());
                tileProvider.setOverlayTileProvider(new OverlayTileProvider(newSpatialData, getColor(mapOverlayColour)));
                if(null != touchMarker) updateOverlayTouchMarker(mapView, null, null); //Do this last
            }

            mapView.redrawTiles();
        }
    }

    private void updateOverlayTouchMarker(MapView mapView, Float sceneX, Float sceneY) {

        if(null == tileProvider) return;
        //I suppose there is a race condition here. The overlay might change while this is executing.
        //Not a big deal since it will only lead to stale data.
        OverlayTileProvider overlay = tileProvider.getOverlayTileProvider();
        if(null == overlay) return;

        //Another little race condition here, could get bad units
        Pollutant pollutant = selectedMapPollutant;
        if(null == pollutant) return;

        View marker = mapView.getMarkerLayout().getMarkerByTag(MAP_TOUCH_MARKER_TAG);

        final int x;
        final int y;
        if(null == sceneX || null == sceneY || sceneX < 0.0 || sceneY < 0.0) {
            //We changed pollutant and we don't actually know what the current mark X,Y coordinates are
            if (null == marker) return; //Scenario doesn't make sense

            //Work-around for the MarkerLayoutParams of the marker not being visible
            Pair<Integer, Integer> coordinates = (Pair<Integer, Integer>) marker.getTag();
            if(null == coordinates) {
                mapView.getMarkerLayout().removeMarker(marker); //something is very wrong
                return;
            }
            x = coordinates.first;
            y = coordinates.second;
        } else {
            x = (int) Math.round(sceneX);
            y = (int) Math.round(sceneY);

            if (marker == null) {
                marker = getLayoutInflater().inflate(R.layout.overlay_map_marker, mapView, false);
                //Center the marker on the centre of the dot.
                final float markerOffsetY = getResources().getDimensionPixelSize(R.dimen.overlay_marker_dot_size)/ 2f;
                mapView.getMarkerLayout().addMarker(marker, x, y, -0.5f, -1.0f, 0f, markerOffsetY , MAP_TOUCH_MARKER_TAG);
            } else {
                mapView.getMarkerLayout().moveMarker(marker, x, y);
            }

            //Work-around for the MarkerLayoutParams of the marker not being visible
            marker.setTag(new Pair<Integer, Integer>(x,y));
        }


        //The overlay value is the alpha transparency of the overlay
        //Convert to the actual unit value
        final int overlayValue = overlay.overlayLookup(x, y);
        TextView value = marker.findViewById(R.id.touch_value);
        if(overlayValue >= MAX_PIXEL_VALUE) {
            value.setText("≥" + pollutant.getMaxVal() + " " + pollutant.getUnits());
        } else if (overlayValue < 0) {
            //Outside the grid
            value.setText("-- " + pollutant.getUnits());
        } else if (overlayValue == 0) {
            value.setText("≤" + pollutant.getMinVal() + " " + pollutant.getUnits());
        } else {
            final float pollutionValue = pollutant.getMinVal() + ((((float)overlayValue) / MAX_PIXEL_VALUE) * (pollutant.getMaxVal() - pollutant.getMinVal()));
            value.setText((new DecimalFormat("0.0")).format(pollutionValue) + " " + pollutant.getUnits());
        }
    }

    private void updateOverlayMapMarker(MapView mapView, View marker) {
        final Pair<Float, Float> latLon = getAQHIService().getStationLatLon(false);
        if(null == latLon || latLon.first < -400 || latLon.second < -400) {
            //There is no selected location yet
            if(null != marker) {
                mapView.getMarkerLayout().removeMarker(marker);
                mapView.setScaleFromCenter(0.02f); //Zoom out
                mapView.scrollTo(1000, 1200);
            }
        } else {
            //There is a valid selected location
            final Pair<Integer, Integer> xyCoordinates = MapTransformer.transformLatLon(latLon.first, latLon.second);
            if(null == marker) {
                //This is the first time and we need to create the marker
                ImageView newMarker = new ImageView(this);
                newMarker.setTag(xyCoordinates);
                newMarker.setImageResource(R.drawable.location_pin_selected);
                mapView.getMarkerLayout().addMarker(newMarker, xyCoordinates.first, xyCoordinates.second, -0.5f, -0.8f, 0f, 0f, MAP_STATION_MARKER_TAG);
                MarkerApiKt.moveToMarker(mapView, newMarker, 0.5f, false);
            } else if(null != marker.getTag() && !xyCoordinates.equals(marker.getTag())) {
                //Only move the marker if the location changed, otherwise you annoy the user
                mapView.getMarkerLayout().moveMarker(marker, xyCoordinates.first, xyCoordinates.second);
                MarkerApiKt.moveToMarker(mapView, marker, 0.5f, false);
            }
        }
    }

    private float calculateGaugeArrowRotation(Double aqhi) {
        aqhi = Math.max(Math.min(aqhi,11d),1d);
        return (float) ((((11-aqhi) / 10) * (-163.636)) -8.182);
    }

    private void renderForecastHeatMap(Map<Date, Double> forecastData) {
        ImageView imgForecastHeatMap = findViewById(R.id.imgForecastHeatMap);
        if(null != forecastData && !forecastData.isEmpty()) {
            imgForecastHeatMap.setImageBitmap(generateHeatMap(forecastData, showForecastGridData));
        } else {
            imgForecastHeatMap.setImageResource(R.drawable.outline_bar_chart_24);
        }
    }

    private void renderHistoricalHeatMap(Map<Date, Double> histData) {
        ImageView imgHistoricalHeatMap = findViewById(R.id.imgHistoricalHeatMap);
        if(null != histData && !histData.isEmpty()) {
            imgHistoricalHeatMap.setImageBitmap(generateHeatMap(histData, showHistoricalGridData));
        } else {
            imgHistoricalHeatMap.setImageResource(R.drawable.outline_bar_chart_24);
        }
    }

    private Bitmap generateHeatMap(Map<Date, Double> data, boolean showGridValues) {
        //TODO: support daylight savings
        //TODO: support timezone changes
        final boolean vertical = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        final int cellSize = vertical ? 65 : 110;
        final float fontScale = this.getResources().getConfiguration().fontScale;
        final SimpleDateFormat formatter = new SimpleDateFormat("MMM d a", Locale.CANADA); // Not thread safe
        return HeatMap.builder()
            .withXAxis(IntegerAxis.instance()
                .withTitle("")
                .addEntry(12,"12")
                .addEntry(1,"1")
                .addEntry(2,"2")
                .addEntry(3,"3")
                .addEntry(4,"4")
                .addEntry(5,"5")
                .addEntry(6,"6")
                .addEntry(7,"7")
                .addEntry(8,"8")
                .addEntry(9,"9")
                .addEntry(10,"10")
                .addEntry(11,"11")
            )
            .withYAxis(new StringAxis("",
                data.keySet()//We need to sort these by date, then convert to string, then remove dupes!
                    .stream()
                    .sorted()
                    .map(formatter::format)
                    .distinct()
                    .toList()))
            .withOptions(HeatMapOptions.builder()
                .withBackgroundColour(getColour("main_activity_background"))
                .withGradient(HeatMapGradient.builder()
                    .withSteps(getHeatMapGradient()).build())
                .withCellWidth(cellSize)
                .withCellHeight(cellSize)
                .withShowGridlines(false)
                .withShowGridValues(showGridValues)
                .withGridValuesFontSize(vertical ? 28f : 36f)
                .withOutsidePadding(0)
                .withShowLegend(false)
                .withShowXAxisLabels(true)
                .withShowYAxisLabels(true)
                .withAxisLabelFontColour(getSystemDefaultTextColor())
                .withAxisLabelFontSize(38f*fontScale)
                .withAxisLabelPadding(15)
                .withAxisLabelFontTypeface(Typeface.create("Roboto", Typeface.NORMAL))
                .withColourScaleLowerBound(1.0)
                .withColourScaleUpperBound(11.0)
                .build())
            .build().render(data.entrySet()
                .stream()
                .map(entry-> (DataRecord) new BasicDataRecord(Utils.to12Hour(entry.getKey().getHours()),
                    formatter.format(entry.getKey()),Math.max(Math.min(entry.getValue(),11d),1d))).toList());
    }

    private Color getSystemDefaultTextColor() {
        //Bit of a hack, steal the colour form a random text box
        final TextView aqhiRiskText = findViewById(R.id.txtLocation);
        return Color.valueOf(aqhiRiskText.getCurrentTextColor());
    }

    private void updateDailyList(Map<Date, Double> data, LinearLayout dailyList, boolean decimals) {
        //Clear any old values
        dailyList.removeAllViews();

        if (null == data || data.isEmpty()) return;

        final String decimalFormat = decimals ? AQHI_DIGIT_FORMAT : AQHI_NO_DIGIT_FORMAT;
        final SimpleDateFormat dateDisplayFormat = new SimpleDateFormat("MMM d", Locale.CANADA);//MAR 3
        final SimpleDateFormat dayDisplayFormat  = new SimpleDateFormat("E", Locale.CANADA);

        data.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                    entry -> dateDisplayFormat.format(entry.getKey()),
                    entry -> Map.entry(entry.getKey(), entry.getValue()),
                    (entry1, entry2) -> entry1.getValue() >= entry2.getValue() ? entry1 : entry2,
                    LinkedHashMap::new
            )).forEach((day, value) -> {
                View itemView = LayoutInflater.from(this)
                        .inflate(R.layout.forecast_layout, dailyList, false);

                final TextView txtDay = itemView.findViewById(R.id.txtDay);
                final TextView txtMonth = itemView.findViewById(R.id.txtMonth);
                final TextView txtForecastRisk = itemView.findViewById(R.id.txtForecastRisk);
                final TextView txtForecastValue = itemView.findViewById(R.id.txtForecastValue);

                txtDay.setText(dayDisplayFormat.format(value.getKey()));    //MON
                txtMonth.setText(dateDisplayFormat.format(value.getKey())); //MAR 3
                txtForecastRisk.setText(getRiskFactor(value.getValue())); //Low Risk
                txtForecastValue.setText(this.formatAQHIValue(value.getValue(), decimalFormat));
                dailyList.addView(itemView);
            });
    }

    private void updateAlertList(LinearLayout alertList, List<Alert> alerts) {
        //Clear any old values
        alertList.removeAllViews();
        if (null == alerts || alerts.isEmpty()) return;

        alerts.forEach(alert -> {
                    //Create the entry based on the layout
                    View itemView = LayoutInflater.from(this).inflate(R.layout.alert_layout, alertList, false);

                    //Set the title of the alert
                    final TextView txtAlert = itemView.findViewById(R.id.txtAlertTitle);
                    txtAlert.setText(alert.getAlertBannerText());

                    //Set the correct icon
                    final ImageView imgAlert = itemView.findViewById(R.id.imgAlertIcon);
                    String level = alert.getType();
                    if("watch".equals(level)) {
                        imgAlert.setImageResource(R.drawable.alert_watch);
                    } else if("warning".equals(level)) {
                        imgAlert.setImageResource(R.drawable.alert_warn);
                    } else {
                        imgAlert.setImageResource(R.drawable.alert_statement);
                    }

                    //Register a click listener to show the details
                    itemView.setOnClickListener(v -> this.showDialog(alert.getAlertBannerText(),null, "<p><b>" + alert.getIssueTimeText() + "</b></p>" + alert.getText().replace("\n","<br>") ,null, null));

                    //Add the entry to the list
                    alertList.addView(itemView);
                });
    }

    private String getRiskFactor(Double aqhi) {
        if(null == aqhi || aqhi.isInfinite() || aqhi.isNaN() || aqhi < 0.0) {
            return "";
        }

        long longAQHI = Math.round(aqhi);
        if (longAQHI <= 3) {
            return "Low Risk";
        } else if (longAQHI <= 6) {
            return "Moderate Risk";
        } else if (longAQHI <= 10) {
            return "High Risk";
        }
        return "Very High Risk";
    }

    public void showTypicalAQHI(View view) {
        String extraContent = null;
        Html.ImageGetter imageGetter = null;
        final Integer napsID = getAQHIService().getStationNAPSID();
        if(null != napsID) {
            Map<Integer, Map<Integer, Float>> dataMap = getAQHIService().getTypicalAQHIMap(napsID);
            if(null != dataMap) {
                final Bitmap heatmap = generateTypicalAQHIHeatMap(dataMap);
                final String stationName = getAQHIService().getStationName(true);
                extraContent = "<p align=\"center\"><b> "+ stationName + " Typical AQHI:</b><br/><img src=\"typical_heatmap\" /></p>";
                imageGetter = source -> {
                    if ("typical_heatmap".equals(source)) {
                        //Create a drawable to old the bitmap
                        BitmapDrawable drawable = new BitmapDrawable(getResources(), heatmap);
                        //Set the bounds
                        WindowManager windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
                        WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
                        final Insets insets = windowMetrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars());
                        final int screenWidth = windowMetrics.getBounds().width() - insets.left - insets.right;
                        final double width = (screenWidth*0.80); //Fixed 80% of the total screen width
                        final double scale = width / drawable.getIntrinsicWidth();
                        drawable.setBounds(0, 0, (int) width, (int) (drawable.getIntrinsicHeight()*scale));
                        return drawable;
                    }
                    return null;
                };
            }
        }
        showDialog("Typical AQHI", R.raw.typical_aqhi, extraContent, imageGetter, null);
    }

    private Bitmap generateTypicalAQHIHeatMap(Map<Integer, Map<Integer, Float>> data) {
        final int cellSize = 35;
        final float fontScale = this.getResources().getConfiguration().fontScale;

        //Determine the background colour of the dialog box
        final TypedValue bgColourValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackgroundFloating, bgColourValue, true);
        Color bgColour = Color.valueOf(bgColourValue.data);

        return HeatMap.builder()
                .withXAxis(IntegerAxis.instance()
                        .withTitle("Hour")
                        .addEntries(1,24)
                )
                .withYAxis(IntegerAxis.instance()
                        .withTitle("Week")
                        .addEntries(1,53)
                )
                .withOptions(HeatMapOptions.builder()
                        .withBackgroundColour(bgColour)
                        .withGradient(HeatMapGradient.builder()
                                .withSteps(getHeatMapGradient()).build())
                        .withCellWidth(cellSize)
                        .withCellHeight(cellSize)
                        .withShowGridlines(false)
                        .withShowGridValues(false)
                        .withOutsidePadding(0)
                        .withShowLegend(false)
                        .withShowXAxisLabels(true)
                        .withShowYAxisLabels(true)
                        .withAxisLabelFontColour(getSystemDefaultTextColor())
                        .withAxisTitleFontSize(44f*fontScale)
                        .withAxisLabelFontSize(38f*fontScale)
                        .withAxisLabelPadding(15)
                        .withAxisLabelFontTypeface(Typeface.create("Roboto", Typeface.NORMAL))
                        .withColourScaleLowerBound(1.0)
                        .withColourScaleUpperBound(11.0)
                        .build())
                .build().render(data.entrySet().stream()
                        .flatMap(hourEntry -> hourEntry.getValue().entrySet().stream()
                                .map(weekEntry -> new BasicDataRecord(hourEntry.getKey(), weekEntry.getKey(), weekEntry.getValue().doubleValue())))
                        .collect(Collectors.toList()));
    }

    private Color[] getHeatMapGradient() {
        //This must be done each time at run time because the colours change
        //based on the theme, such as light and dark.
        return new Color[]{
            getColour("gradient_colour_01"),
            getColour("gradient_colour_02"),
            getColour("gradient_colour_03"),
            getColour("gradient_colour_04"),
            getColour("gradient_colour_05"),
            getColour("gradient_colour_06"),
            getColour("gradient_colour_07"),
            getColour("gradient_colour_08"),
            getColour("gradient_colour_09"),
            getColour("gradient_colour_10"),
            getColour("gradient_colour_11")
        };
    }

    @Override
    public AQHIDataService getAQHIService() {
        return backgroundWorker.getAQHIService();
    }

    public SpatialDataService getSpatialDataService() {
        return backgroundWorker.getSpatialDataService();
    }
}