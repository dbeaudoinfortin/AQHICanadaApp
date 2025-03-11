package com.dbf.aqhi.widgets;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.dbf.aqhi.R;

public class AQHIWidgetConfigActivity extends AppCompatActivity {

    private static final int DEFAULT_TRANSPARENCY = 30;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.widget_config_activity);

        //Retrieve the App Widget ID from the launching Intent
        Bundle extras = getIntent().getExtras();
        int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        //Per doc: If the user backs out of the activity before reaching the end,
        //the system notifies the app widget host that the configuration is canceled and the host doesn't add the widget
        setResult(Activity.RESULT_CANCELED, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));

        // If they gave us an intent without the widget id, just bail.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        initUI (appWidgetId);

        // Handle the Save button click event
        int finalAppWidgetId = appWidgetId;
        findViewById(R.id.btnSave).setOnClickListener(v -> {
            // Prepare the result intent to pass back the widget ID
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, finalAppWidgetId);
            setResult(RESULT_OK, resultValue);

            // Close the activity and return to the home screen
            finish();
        });
    }

    private void initUI (int appWidgetId) {

        //Add a preview of the widget itself
        //Figure out what widget is being previewed
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this.getApplicationContext());
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        RemoteViews widgetRemoteViews = new RemoteViews(getPackageName(), info.initialLayout);

        //Size it correctly and place it in its container
        FrameLayout previewContainer = findViewById(R.id.preview_container);
        View widgetPreview = widgetRemoteViews.apply(this, previewContainer);
        float density = getResources().getDisplayMetrics().density*0.6f;
        widgetPreview.setLayoutParams(new FrameLayout.LayoutParams((int) (info.minWidth*density), (int) (info.minHeight*density)));
        previewContainer.addView(widgetPreview);

        //Now update the preview widget UI
        AQHIWidgetProvider provider = (info.initialLayout == R.layout.widget_layout_large) ?
                new AQHIWidgetProviderLarge() : new AQHIWidgetProviderSmall();
        provider.refreshWidget(this, widgetRemoteViews, appWidgetManager, widgetRemoteViews.getViewId());
        widgetRemoteViews.reapply(this, widgetPreview);

        //Set the defaults for transparency
        SeekBar sbTransparency = findViewById(R.id.sbTransparency);
        sbTransparency.setProgress(DEFAULT_TRANSPARENCY);

        TextView lblTransparencyValue = findViewById(R.id.lblTransparencyValue);
        lblTransparencyValue.setText(DEFAULT_TRANSPARENCY + "%");
        setPreviewBackground(widgetPreview, DEFAULT_TRANSPARENCY);

        //Update the transparency label when the bar value changes
        sbTransparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lblTransparencyValue.setText(progress + "%");
                setPreviewBackground(widgetPreview, progress);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        //Set the default dark/light mode to automatic
        RadioGroup rgMode = findViewById(R.id.rgMode);

        //Changes to dark/light mode
        rgMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int newMode;
                if (checkedId == R.id.rbAutomatic) {
                    newMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                } else if (checkedId == R.id.rbDark) {
                    newMode = AppCompatDelegate.MODE_NIGHT_YES;
                } else {
                    newMode = AppCompatDelegate.MODE_NIGHT_NO;
                }

                //Apply theme to the current activity
                if (newMode != getDelegate().getLocalNightMode()) {
                    getDelegate().setLocalNightMode(newMode);
                    recreate();
                }

            }
        });
    }

    private void setPreviewBackground(View widgetPreview, int percentage) {
        int baseColor = 0;
        if(widgetPreview.getBackground() instanceof ColorDrawable) {
            baseColor = ((ColorDrawable) widgetPreview.getBackground()).getColor();
        } else if (widgetPreview.getBackground() instanceof GradientDrawable) {
            GradientDrawable gradientDrawable = (GradientDrawable) widgetPreview.getBackground();
            baseColor = gradientDrawable.getColor().getDefaultColor();
        }
        int newColor = Color.argb((int) (percentage * 2.55f), Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        widgetPreview.setBackgroundColor(newColor);
    }

    private void updatePreferences(){

    }
}