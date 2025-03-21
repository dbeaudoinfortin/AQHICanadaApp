package com.dbf.aqhi;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.app.AlertDialog;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import com.dbf.heatmaps.android.HeatMapGradient;
import com.dbf.heatmaps.android.HeatMapOptions;
import com.dbf.heatmaps.axis.IntegerAxis;
import com.dbf.heatmaps.axis.StringAxis;
import com.dbf.heatmaps.data.BasicDataRecord;
import com.dbf.heatmaps.data.DataRecord;

import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

public class AQHIMainActivity extends AppCompatActivity implements AQHIFeature {
    private static final String LOG_TAG = "AQHIMainActivity";

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private AQHIBackgroundWorker backgroundWorker;
    private boolean showHistoricalGridData = false;
    private boolean showForecastGridData = false;

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
            Map<Date, Double> histData = backgroundWorker.getAQHIService().getHistoricalAQHI();
            renderHistoricalHeatMap(histData);
        });

        ImageView imgForecastHeatMap = findViewById(R.id.imgForecastHeatMap);
        imgForecastHeatMap.setOnClickListener(v -> {
            showForecastGridData = !showForecastGridData;
            Map<Date, Double> forecastData = backgroundWorker.getAQHIService().getForecastAQHI();
            renderForecastHeatMap(forecastData);
        });
    }

    private void updateUI() {
        Log.i(LOG_TAG, "Updating AQHI Main Activity UI.");
        String recentStation = backgroundWorker.getAQHIService().getStationName();

        //UPDATE LOCATION TEXT
        TextView locationText = findViewById(R.id.txtLocation);
        if(null == recentStation || recentStation.isEmpty()) {
            locationText.setText(R.string.unknown_Location);
        } else {
            locationText.setText(recentStation);
        }

        Double aqhi = this.getLatestAQHI();

        //UPDATE AQHI TEXT
        TextView aqhiText = findViewById(R.id.txtAQHIValue);
        aqhiText.setText(this.getLatestAQHIString());

        TextView aqhiRiskText = findViewById(R.id.txtAQHIRisk);
        aqhiRiskText.setText(getRiskFactor(aqhi));

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
        Map<Date, Double> forecastData = backgroundWorker.getAQHIService().getForecastAQHI();
        LinearLayout dailyForecastList = findViewById(R.id.daily_forecast_list);
        updateDailyList(forecastData, dailyForecastList, false);
        renderForecastHeatMap(forecastData);

        //UPDATE HISTORICAL
        Map<Date, Double> histData = backgroundWorker.getAQHIService().getHistoricalAQHI();
        LinearLayout dailyHistoricalList = findViewById(R.id.daily_historical_list);
        updateDailyList(histData, dailyHistoricalList, true);
        renderHistoricalHeatMap(histData);
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
                                .withSteps(new Color[]{
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
                                }).build())
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

    private Color getColour(String colourId){
        return Utils.getColor(this, colourId);
    }
}