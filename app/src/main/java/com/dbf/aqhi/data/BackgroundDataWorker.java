package com.dbf.aqhi.data;

import android.content.Context;
import android.util.Log;

import com.dbf.aqhi.data.aqhi.AQHIDataService;
import com.dbf.aqhi.data.spatial.SpatialDataService;
import com.dbf.utils.stacktrace.StackTraceCompactor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackgroundDataWorker {

    private static final String LOG_TAG = "BackgroundDataWorker";

    private static final long UPDATE_INTERVAL = 600; //In seconds, 5 minutes

    private final AQHIDataService aqhiDataService;
    private final SpatialDataService spatialDataService;

    private ScheduledExecutorService updateScheduler;

    public BackgroundDataWorker(Context context, Runnable onChange) {
        aqhiDataService = new AQHIDataService(context, onChange, true);
        spatialDataService = new SpatialDataService(context, onChange);
        initScheduler();
    }

    private synchronized void initScheduler(){
        if (updateScheduler == null || updateScheduler.isShutdown()) {
            updateScheduler = Executors.newScheduledThreadPool(1);
            Log.i(LOG_TAG, "Starting background data worker.");
            //Update every 10 minutes, starting 10 minutes from now
            updateScheduler.scheduleWithFixedDelay(() -> {
                try {
                    Log.i(LOG_TAG, "Running automatic AQHI data update in background.");
                    aqhiDataService.update();
                    Log.i(LOG_TAG, "Running automatic spatial data update in background.");
                    spatialDataService.update();
                } catch (Throwable t) { //Catch all, protect the scheduler
                    Log.e(LOG_TAG, "Failed to run scheduled AQHI update:\n" + StackTraceCompactor.getCompactStackTrace(t));
                }
            }, UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.SECONDS);
        }
    }

    private synchronized void stopScheduler() {
        if (updateScheduler != null && !updateScheduler.isShutdown()) {
            Log.i(LOG_TAG, "Stopping background AQHI worker.");
            updateScheduler.shutdown();
        }
    }

    public AQHIDataService getAQHIService() {
        return aqhiDataService;
    }

    public SpatialDataService getSpatialDataService() {
        return spatialDataService;
    }

    /**
     * Forces an update to run immediately.
     */
    public void updateNow() {
        Log.i(LOG_TAG, "Forcing manual AQHI and spatial data update.");
        aqhiDataService.update();
        spatialDataService.update();
    }

    /**
     * Stops the scheduler to stop background data updates.
     */
    public void stop() {
        stopScheduler();
    }

    /**
     * Resumes the scheduler to continue background data updates.
     */
    public void resume() {
        initScheduler();
    }
}
