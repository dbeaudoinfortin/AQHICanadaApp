package com.dbf.aqhi.main;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.Manifest;

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
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.dbf.aqhi.AQHIActivity;
import com.dbf.aqhi.R;
import com.dbf.aqhi.Utils;
import com.dbf.aqhi.api.weather.alert.Alert;
import com.dbf.aqhi.permissions.PermissionService;
import com.dbf.aqhi.service.AQHIBackgroundWorker;
import com.dbf.aqhi.service.AQHIService;
import com.dbf.aqhi.widgets.AQHIWidgetUpdateWorker;
import com.dbf.heatmaps.android.HeatMap;
import com.dbf.heatmaps.android.HeatMapGradient;
import com.dbf.heatmaps.android.HeatMapOptions;
import com.dbf.heatmaps.axis.IntegerAxis;
import com.dbf.heatmaps.axis.StringAxis;
import com.dbf.heatmaps.data.BasicDataRecord;
import com.dbf.heatmaps.data.DataRecord;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AQHIMainActivity extends AQHIActivity {
    private static final String LOG_TAG = "AQHIMainActivity";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private AQHIBackgroundWorker backgroundWorker;
    private boolean showHistoricalGridData = false;
    private boolean showForecastGridData = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(LOG_TAG, "AQHI Main Activity created.");
        setContentView(R.layout.main_activity);

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

    @Override
    public void onUserLeaveHint() {
        //Forcefully update the widgets, this will handle any change in location
        //and also make sure that they get the same data the user is seeing on the
        //main activity.
        WorkManager workManager = WorkManager.getInstance(this);
        workManager.enqueueUniqueWork("widget_update_now", ExistingWorkPolicy.APPEND,
            new OneTimeWorkRequest.Builder(AQHIWidgetUpdateWorker.class)
                .setInitialDelay(0, TimeUnit.MINUTES)
                .build());
        super.onUserLeaveHint();
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
                showNoPermission(this);
            }
        }
    }

    protected void initUI() {
        Log.i(LOG_TAG, "Initializing AQHI Main Activity UI.");

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

        final DecimalFormat decimalFormatter = new DecimalFormat(decimals ? AQHI_DIGIT_FORMAT : AQHI_NO_DIGIT_FORMAT);
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
                txtForecastValue.setText(decimalFormatter.format(value.getValue())); //3.00 or 3

                dailyList.addView(itemView);
            });
    }

    private void updateAlertList(LinearLayout alertList, List<Alert> alerts) {
        //Clear any old values
        alertList.removeAllViews();
        if (null == alerts || alerts.isEmpty()) return;

        alerts.stream()
                .forEach(alert -> {
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
                    itemView.setOnClickListener(v -> {
                       this.showDialog(alert.getAlertBannerText(),null, "<p><b>" + alert.getIssueTimeText() + "</b></p>" + alert.getText().replace("\n","<br>") ,null, null);

                    });

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
    public AQHIService getAQHIService() {
        return backgroundWorker.getAQHIService();
    }
}