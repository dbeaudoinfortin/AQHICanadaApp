package com.dbf.aqhi.widgets.config;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatDelegate;

import com.dbf.aqhi.R;
import com.dbf.aqhi.widgets.AQHIWidgetProvider;
import com.dbf.aqhi.widgets.AQHIWidgetProviderLarge;
import com.dbf.aqhi.widgets.AQHIWidgetProviderSmall;

public class AQHIWidgetConfigActivity extends Activity  {

    private View widgetPreview;

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

    protected void initUI (int appWidgetId) {

        //Load the Widget configs, if there are any
        WidgetConfig widgetConfig = new WidgetConfig(this, appWidgetId);

        //Determine if the widget is light or dark mode
        //Set the default dark/light mode to automatic
        RadioGroup rgMode = findViewById(R.id.rgMode);
        int mode = widgetConfig.getNightMode();
        updateModeCheckBoxes(rgMode, mode);

        //Add a preview of the widget itself
        widgetPreview = showWidgetPreview(appWidgetId);

        //Set the defaults for transparency
        int defaultAlpha = widgetConfig.getAlpha();
        SeekBar sbTransparency = findViewById(R.id.sbTransparency);
        sbTransparency.setProgress(defaultAlpha);

        TextView lblTransparencyValue = findViewById(R.id.lblTransparencyValue);
        lblTransparencyValue.setText(defaultAlpha + "%");

        //Update the transparency label when the bar value changes
        sbTransparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lblTransparencyValue.setText(progress + "%");
                widgetConfig.setAlpha(progress);

                //Apply layout to the widget preview
                widgetPreview = showWidgetPreview(appWidgetId);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        //Changes to dark/light mode
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            int newMode = determineLightDarkMode(checkedId);
            widgetConfig.setNightMode(newMode);

            //Apply layout to the widget preview
            widgetPreview = showWidgetPreview(appWidgetId);
        });
    }

    private View showWidgetPreview(int appWidgetId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this.getApplicationContext());
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        RemoteViews widgetRemoteViews = new RemoteViews(getPackageName(), info.initialLayout);

        //Size it correctly and place it in its container
        FrameLayout previewContainer = findViewById(R.id.preview_container);

        //Existing previews before adding the new one
        previewContainer.removeAllViews();

        View widgetPreview = widgetRemoteViews.apply(this, previewContainer);
        
        //Set the size of the preview
        FrameLayout previewBG = findViewById(R.id.preview_container_background);
        //The large widget has a 3 to 1 ratio
        //The small widget has a 1.2:1 ratio
        final float scale = info.initialLayout == R.layout.widget_layout_large ? AQHIWidgetProviderLarge.PREVIEW_SCREEN_SCALE: AQHIWidgetProviderSmall.PREVIEW_SCREEN_SCALE;
        final float ratio = info.initialLayout == R.layout.widget_layout_large ? AQHIWidgetProviderLarge.PREVIEW_SCREEN_RATIO: AQHIWidgetProviderSmall.PREVIEW_SCREEN_RATIO;
        final int screenWidth = getResources().getDisplayMetrics().widthPixels;

        widgetPreview.setLayoutParams(new FrameLayout.LayoutParams((int) (screenWidth*scale), (int) (screenWidth*scale*ratio)));

        //Set rounded corners
        widgetPreview.setClipToOutline(true);

        //Add the preview to the container
        previewContainer.addView(widgetPreview);

        //Now update the preview widget UI
        AQHIWidgetProvider provider = (info.initialLayout == R.layout.widget_layout_large) ?
                new AQHIWidgetProviderLarge() : new AQHIWidgetProviderSmall();

        //AQHIService is not initialized automatically since it needs a context
        provider.initAQHIService(this, appWidgetManager, new int[] {appWidgetId});
        provider.refreshWidget(this, widgetRemoteViews, appWidgetManager, appWidgetId);
        widgetRemoteViews.reapply(this, widgetPreview);

        return widgetPreview;
    }

    private int determineLightDarkMode(int checkedId) {
        if (checkedId == R.id.rbAutomatic) {
            return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else if (checkedId == R.id.rbDark) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_NO;
    }

    private void updateModeCheckBoxes(RadioGroup rgMode, int mode) {
        if(mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            rgMode.check(R.id.rbAutomatic);
        } else if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            rgMode.check(R.id.rbDark);
        } else {
            rgMode.check(R.id.rbLight);
        }
    }
}