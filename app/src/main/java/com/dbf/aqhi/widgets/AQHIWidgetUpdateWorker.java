package com.dbf.aqhi.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.dbf.aqhi.data.aqhi.AQHIDataService;

import java.util.Arrays;

public class AQHIWidgetUpdateWorker extends Worker {

    private static final String LOG_TAG = "AQHIWidgetUpdateWorker";

    public AQHIWidgetUpdateWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(LOG_TAG, "Starting forced background update of AQHI widgets.");

        //This will update all widget at the same time
        //Get all of our widget IDs
        Context context = getApplicationContext();
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        //Only create one AQHI service so we update only once for all widgets of all types
        AQHIDataService aqhiDataService = new AQHIDataService(context,null);
        //True for widgets because they may not have background location updates enabled
        aqhiDataService.setAllowStaleLocation(true);
        aqhiDataService.setOnUpdate(() -> {
            updateWidgets(context, new AQHIWidgetProviderLarge(aqhiDataService), appWidgetManager, appWidgetManager.getAppWidgetIds(new ComponentName(context, AQHIWidgetProviderLarge.class)));
            updateWidgets(context, new AQHIWidgetProviderSmall(aqhiDataService), appWidgetManager, appWidgetManager.getAppWidgetIds(new ComponentName(context, AQHIWidgetProviderSmall.class)));
            updateWidgets(context, new AQHIWidgetProviderFace(aqhiDataService), appWidgetManager, appWidgetManager.getAppWidgetIds(new ComponentName(context, AQHIWidgetProviderFace.class)));
        });
        aqhiDataService.update();
        return Result.success();
    }

    private void updateWidgets(Context context, AQHIWidgetProvider provider, AppWidgetManager appWidgetManager, int[] widgetIds) {
        if(widgetIds == null || widgetIds.length < 1) return;

        Log.i(LOG_TAG, "Forcefully updating AQHI widget UIs in background. Widget IDs: " + Arrays.toString(widgetIds));
        for (int widgetId : widgetIds) {
            provider.updateWidgetUI(context, provider.createRemoteViews(context), appWidgetManager, widgetId); //Will push update
        }
    }
}
